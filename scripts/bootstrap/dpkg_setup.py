#!/usr/bin/env python3
"""Install/repair AutoPie's dpkg wrapper and patched am without executing them.

Used both for cross-architecture bootstraps and for existing Android prefixes.
The standard dpkg diversions database is updated under dpkg's POSIX locks, so
this also works with older dpkg versions and broken/unrelocated dpkg-divert.
"""
import argparse
import contextlib
import errno
import fcntl
import io
import os
from pathlib import Path
import stat
import sys
import tempfile
import zipfile


AM_LAUNCHER = "bin/am"
AM_APK = "libexec/termux-am/am.apk"


class SetupError(RuntimeError):
    pass


class PackageManagerBusy(SetupError):
    pass


def canonical_prefix(prefix):
    prefix = str(prefix).rstrip("/")
    if not prefix.startswith("/") or not prefix.endswith("/files/usr"):
        raise SetupError("Prefix must be an absolute app files/usr directory")
    if any(character in prefix for character in ("\n", "\r", "\0")):
        raise SetupError("Invalid characters in prefix")
    if prefix.startswith("/data/user/0/"):
        prefix = "/data/data/" + prefix[len("/data/user/0/"):]
    return prefix


def prefix_aliases(prefix):
    prefixes = [canonical_prefix(prefix)]
    if prefixes[0].startswith("/data/data/"):
        # dpkg matches diversion paths by name, not inode.
        prefixes.append("/data/user/0/" + prefixes[0][len("/data/data/"):])
    return prefixes


def diversion_pairs(prefix, source="bin/dpkg", target="bin/dpkg.real"):
    return [(p + "/" + source, p + "/" + target, ":") for p in prefix_aliases(prefix)]


def read_diversions(path):
    if not path.exists():
        return []
    lines = path.read_text(encoding="utf-8").splitlines()
    if len(lines) % 3:
        raise SetupError("Malformed diversions database: " + str(path))
    records = [tuple(lines[i:i + 3]) for i in range(0, len(lines), 3)]
    seen = set()
    for source, target, owner in records:
        if not source.startswith("/") or not target.startswith("/") or not owner:
            raise SetupError("Invalid diversion in " + str(path))
        if source in seen or target in seen or source == target:
            raise SetupError("Conflicting diversions in " + str(path))
        seen.update((source, target))
    return records


def merged_diversions(existing, required):
    result = list(existing)
    for record in required:
        if record in result:
            continue
        source, target, _ = record
        if any(source in (s, t) or target in (s, t) for s, t, _ in result):
            raise SetupError("A different diversion already owns " + source)
        result.append(record)
    return result


def atomic_write(path, data, mode):
    if path.is_symlink():
        raise SetupError("Refusing to replace symlink: " + str(path))
    if path.is_file() and path.read_bytes() == data:
        if stat.S_IMODE(path.stat().st_mode) != mode:
            path.chmod(mode)
        return
    fd, temporary = tempfile.mkstemp(prefix="." + path.name + "-", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as out:
            out.write(data)
            out.flush()
            os.fsync(out.fileno())
            os.fchmod(out.fileno(), mode)
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


@contextlib.contextmanager
def package_locks(admindir):
    # lockf uses the same POSIX record locks as apt/dpkg. flock is insufficient.
    with contextlib.ExitStack() as stack:
        for name in ("lock-frontend", "lock"):
            lock = stack.enter_context((admindir / name).open("a+b"))
            try:
                fcntl.lockf(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except OSError as error:
                if error.errno not in (errno.EAGAIN, errno.EACCES):
                    raise
                raise PackageManagerBusy("Package manager is busy; retry after apt/dpkg finishes") from error
        yield


def executable_kind(path):
    if path.is_symlink():
        raise SetupError("Unexpected executable symlink: " + str(path))
    if not path.exists():
        return "missing"
    if not path.is_file():
        raise SetupError("Not a regular executable: " + str(path))
    data = path.read_bytes()
    if data.startswith(b"\x7fELF") and os.access(path, os.X_OK):
        return "native"
    if (data.startswith(b"#!") and b"def which_real_dpkg(" in data
            and b"def rewrite_deb_inplace(" in data):
        return "wrapper"
    raise SetupError("Unrecognized dpkg executable; leaving it untouched: " + str(path))


def render_wrapper(source, prefix):
    text = source.read_text(encoding="utf-8")
    if not text.startswith("#!") or "def which_real_dpkg(" not in text:
        raise SetupError("Invalid AutoPie dpkg wrapper source")
    prefix = canonical_prefix(prefix)
    root = prefix[:-len("/files/usr")]
    text = text.replace("/data/data/com.autopi/files/usr", prefix)
    text = text.replace("/data/data/com.autopi", root)
    return ("#!" + prefix + "/bin/env python\n" + text.split("\n", 1)[1]).encode()


def package_file_list_updates(admindir, prefix, package):
    """Align the protected package's ownership records with future archive paths.

    Otherwise dpkg may unlink a freshly installed diverted file when removing
    an obsolete alias entry for the same file.
    """
    aliases = prefix_aliases(prefix)
    if len(aliases) == 1:
        return []
    canonical_root, legacy_root = (path[:-len("/files/usr")] for path in aliases)
    info = admindir / "info"
    updates = []
    for path in [info / (package + ".list"), *sorted(info.glob(package + ":*.list"))]:
        if path.is_symlink() or path.with_name(path.name + ".autopie-backup").is_symlink():
            raise SetupError("Unexpected package file-list symlink: " + str(path))
        if not path.is_file():
            continue
        original = path.read_bytes()
        lines = []
        for line in original.decode("utf-8").splitlines():
            if line == legacy_root or line.startswith(legacy_root + "/"):
                line = canonical_root + line[len(legacy_root):]
            elif line.rstrip("/") == "/data/user/0":
                line = "/data/data/" if line.endswith("/") else "/data/data"
            lines.append(line)
        updated = ("\n".join(dict.fromkeys(lines)) + "\n").encode()
        if updated != original:
            updates.append((path, original, updated))
    return updates


def read_patched_am(launcher, apk, prefix):
    launcher_data = launcher.read_bytes()
    lines = launcher_data.splitlines()
    if not launcher_data.startswith(b"#!") or not any(
        ('AM_APK_PATH="' + alias + "/" + AM_APK + '"').encode() in lines for alias in prefix_aliases(prefix)
    ):
        raise SetupError("Bundled am launcher does not match " + prefix)

    # DEX strings store their UTF-16 length as ULEB128 before the bytes.
    package = prefix[:-len("/files/usr")].rsplit("/", 1)[-1].encode("ascii")
    length = len(package)
    encoded_length = bytes([length]) if length < 128 else bytes([128 | (length % 128), length // 128])
    caller = encoded_length + package + b"\0"
    apk_data = apk.read_bytes()
    with zipfile.ZipFile(io.BytesIO(apk_data)) as archive:
        if not any(caller in archive.read(name) for name in archive.namelist() if name.endswith(".dex")):
            raise SetupError("Bundled am APK does not contain the caller package " + package.decode())
    return launcher_data, apk_data


def repair_wake_scripts(root, prefix):
    """Repair prefixes retained across APK updates, preserving script options."""
    package = prefix[:-len("/files/usr")].rsplit("/", 1)[-1].encode()
    service = b"/com.termux.app.TermuxService"
    for name in ("termux-wake-lock", "termux-wake-unlock"):
        path = root / "bin" / name
        if path.is_symlink() or not path.is_file():
            continue
        original = path.read_bytes()
        if not original.startswith(b"#!"):
            continue
        updated = original.replace(package + b"/" + package + b".app.TermuxService", package + service)
        updated = updated.replace(b"com.termux" + service, package + service)
        for action in (b"service_wake_lock", b"service_wake_unlock"):
            updated = updated.replace(b"com.termux." + action, package + b"." + action)
        if updated != original:
            atomic_write(path, updated, stat.S_IMODE(path.stat().st_mode))


def install_wrapper(root, prefix, source, am_sources=None):
    """root contains bin/, var/, etc.; prefix is the installed Android path."""
    root, source = Path(root), Path(source)
    prefix = canonical_prefix(prefix)
    admindir = root / "var/lib/dpkg"
    if not (admindir / "status").is_file():
        raise SetupError("No installed dpkg database in " + str(admindir))
    rendered = render_wrapper(source, prefix)
    with package_locks(admindir):
        dpkg, real = root / "bin/dpkg", root / "bin/dpkg.real"
        kind = executable_kind(dpkg)
        if kind == "native":
            # This may be newer than dpkg.real after the wrapper was overwritten.
            native = dpkg
        elif kind in ("wrapper", "missing"):
            native = None
            for candidate in (real, root / "bin/dpkg.orig", root / "bin/dpkg.bin"):
                if executable_kind(candidate) == "native":
                    native = candidate
                    break
            if native is None:
                raise SetupError("No native dpkg backend found; refusing to install a recursive wrapper")
        else:
            raise SetupError("Unsupported dpkg state")

        database = admindir / "diversions"
        existing = read_diversions(database)
        required = diversion_pairs(prefix)
        file_lists = package_file_list_updates(admindir, prefix, "dpkg")
        am_files = []
        if am_sources is not None:
            launcher, apk = read_patched_am(*am_sources, prefix)
            for relative, data, mode in ((AM_APK, apk, 0o400), (AM_LAUNCHER, launcher, 0o700)):
                active = root / relative
                diverted = root / (relative + ".distrib")
                for path in (active, diverted):
                    if path.is_symlink() or (path.exists() and not path.is_file()):
                        raise SetupError("Unexpected am file: " + str(path))
                required.extend(diversion_pairs(prefix, relative, relative + ".distrib"))
                am_files.append((active, diverted, data, mode))
            file_lists.extend(package_file_list_updates(admindir, prefix, "termux-am"))
        updated = merged_diversions(existing, required)
        # Validate everything before replacing any executables or database files.
        for path in (real, database, admindir / "diversions-old"):
            if path.is_symlink():
                raise SetupError("Unexpected symlink: " + str(path))

        if native != real:
            # Copy, never move: an interrupted repair must leave a native dpkg.
            # Keep the original backend once for recovery, including legacy .real.
            backup = root / "bin/dpkg.real.autopie-backup"
            if real.is_file() and not backup.exists():
                atomic_write(backup, real.read_bytes(), stat.S_IMODE(real.stat().st_mode))
            atomic_write(real, native.read_bytes(), 0o700)

        for path, original, contents in file_lists:
            backup = path.with_name(path.name + ".autopie-backup")
            if not backup.exists():
                atomic_write(backup, original, 0o600)
            atomic_write(path, contents, 0o600)

        for active, diverted, data, _ in am_files:
            if active.is_file() and not diverted.exists():
                original = active.read_bytes()
                if original != data:
                    atomic_write(diverted, original, stat.S_IMODE(active.stat().st_mode))

        if updated != existing:
            if database.exists():
                atomic_write(admindir / "diversions-old", database.read_bytes(), 0o600)
            payload = "".join("\n".join(record) + "\n" for record in updated).encode()
            atomic_write(database, payload, 0o600)
        atomic_write(dpkg, rendered, 0o700)
        repair_wake_scripts(root, prefix)
        for active, _, data, mode in am_files:
            active.parent.mkdir(parents=True, exist_ok=True)
            atomic_write(active, data, mode)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--prefix", required=True, help="Installed app files/usr path")
    parser.add_argument("--wrapper", type=Path, required=True, help="AutoPie dpkg.py source")
    parser.add_argument("--bootstrap-root", type=Path, help="Extracted usr directory for cross-architecture builds")
    parser.add_argument("--am", nargs=2, type=Path, metavar=("LAUNCHER", "APK"),
                        help="Patched termux-am files for this app package")
    args = parser.parse_args()
    try:
        install_wrapper(args.bootstrap_root or Path(args.prefix), args.prefix, args.wrapper, args.am)
    except PackageManagerBusy as error:
        print("AutoPie dpkg setup: " + str(error), file=sys.stderr)
        return 75
    except (SetupError, OSError, UnicodeError, zipfile.BadZipFile) as error:
        print("AutoPie dpkg setup: " + str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
