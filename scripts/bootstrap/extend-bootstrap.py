#!/usr/bin/env python3
from __future__ import annotations

import argparse
import bz2
import gzip
import hashlib
import io
import lzma
import os
import re
import shutil
import tarfile
import tempfile
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urljoin, urlparse, urlunparse


DEFAULT_PACKAGES = ("python", "python-pip", "binutils", "openssh", "sshpass")
DEFAULT_OLD_PACKAGE = "com.termux"
USER_AGENT = "AutoPie bootstrap extender"
BINUTILS_ALIASES = {
    "addr2line": "gaddr2line",
    "ar": "gar",
    "as": "gas",
    "nm": "gnm",
    "objcopy": "gobjcopy",
    "objdump": "gobjdump",
    "ranlib": "granlib",
    "readelf": "greadelf",
    "size": "gsize",
    "strings": "gstrings",
    "strip": "gstrip",
}

AR_GLOBAL_HEADER = b"!<arch>\n"
AR_FILE_HEADER_SIZE = 60
AR_FILE_MAGIC = b"`\n"


@dataclass(frozen=True)
class RepoSource:
    base_url: str
    distribution: str
    component: str


@dataclass
class PackageRecord:
    name: str
    fields: dict[str, str]

    @property
    def version(self) -> str:
        return self.fields["Version"]

    @property
    def filename(self) -> str:
        return self.fields["Filename"]

    @property
    def sha256(self) -> str:
        return self.fields["SHA256"].lower()

    @property
    def depends(self) -> list[str]:
        raw = []
        for key in ("Pre-Depends", "Depends"):
            if key in self.fields:
                raw.extend(split_depends(self.fields[key]))
        return raw

    @property
    def provides(self) -> list[str]:
        if "Provides" not in self.fields:
            return []
        return [normalize_dependency(part) for part in split_depends(self.fields["Provides"])]


@dataclass
class ArMember:
    name: str
    mtime: str
    uid: str
    gid: str
    mode: str
    data: bytes


@dataclass(frozen=True)
class ReleaseIndex:
    path: str
    size: int
    sha256: str


@dataclass(frozen=True)
class DependencyAlternative:
    name: str
    operator: str | None = None
    version: str | None = None


@dataclass(frozen=True)
class DependencyChoice:
    name: str
    force: bool = False


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Inject pinned Termux repo packages into an extracted bootstrap."
    )
    parser.add_argument("root", type=Path, help="Extracted bootstrap root")
    parser.add_argument(
        "--arch",
        default="aarch64",
        help="Termux repository architecture, for example aarch64",
    )
    parser.add_argument(
        "--package",
        action="append",
        dest="packages",
        help="Package to inject. May be passed multiple times.",
    )
    parser.add_argument(
        "--old-package",
        default=DEFAULT_OLD_PACKAGE,
        help="Original Termux package id used inside upstream .deb paths.",
    )
    parser.add_argument(
        "--repo-url",
        help="Override repo base URL. Defaults to first deb line in etc/apt/sources.list.",
    )
    parser.add_argument(
        "--keep-downloads",
        action="store_true",
        help="Keep downloaded .deb files under the temporary work directory.",
    )
    return parser.parse_args()


def read_sources_list(root: Path) -> RepoSource:
    sources = root / "etc/apt/sources.list"
    for line in sources.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split()
        if len(parts) >= 4 and parts[0] == "deb":
            return RepoSource(
                base_url=parts[1].rstrip("/") + "/",
                distribution=parts[2],
                component=parts[3],
            )
    raise SystemExit(f"No usable deb source found in {sources}")


def fetch_bytes(url: str) -> bytes:
    print(f"Downloading {url}")
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def candidate_sources(source: RepoSource) -> list[RepoSource]:
    sources = [source]
    parsed = urlparse(source.base_url)
    if parsed.netloc == "packages-cf.termux.dev":
        mirror = urlunparse(parsed._replace(netloc="packages.termux.dev"))
        sources.append(RepoSource(mirror, source.distribution, source.component))
    return sources


def parse_release_indexes(text: str, arch: str, component: str) -> list[ReleaseIndex]:
    indexes: list[ReleaseIndex] = []
    wanted_prefix = f"{component}/binary-{arch}/Packages"
    in_sha256 = False

    for line in text.splitlines():
        if line == "SHA256:":
            in_sha256 = True
            continue
        if in_sha256 and line and not line.startswith(" "):
            break
        if not in_sha256:
            continue

        parts = line.split()
        if len(parts) != 3:
            continue
        checksum, size_text, path = parts
        if path == wanted_prefix or path.startswith(wanted_prefix + "."):
            indexes.append(ReleaseIndex(path=path, size=int(size_text), sha256=checksum.lower()))

    return indexes


def decompress_packages(path: str, data: bytes) -> str:
    if path.endswith(".xz"):
        data = lzma.decompress(data)
    elif path.endswith(".gz"):
        data = gzip.decompress(data)
    elif path.endswith(".bz2"):
        data = bz2.decompress(data)
    elif not path.endswith("Packages"):
        raise RuntimeError(f"Unsupported Packages index format: {path}")
    return data.decode("utf-8")


def fetch_release_packages_index(source: RepoSource, arch: str) -> str:
    release_url = urljoin(source.base_url, f"dists/{source.distribution}/Release")
    release_text = fetch_bytes(release_url).decode("utf-8")
    indexes = parse_release_indexes(release_text, arch, source.component)
    if not indexes:
        raise RuntimeError(f"No Packages index listed in {release_url}")

    preferred = sorted(
        indexes,
        key=lambda item: (
            0 if item.path.endswith(".gz") else 1 if item.path.endswith(".bz2") else 2,
            item.size,
        ),
    )
    for index in preferred:
        url = urljoin(source.base_url, f"dists/{source.distribution}/{index.path}")
        data = fetch_bytes(url)
        actual = hashlib.sha256(data).hexdigest().lower()
        if actual != index.sha256:
            raise RuntimeError(
                f"SHA256 mismatch for {index.path}: expected {index.sha256}, got {actual}"
            )
        if len(data) != index.size:
            raise RuntimeError(
                f"Size mismatch for {index.path}: expected {index.size}, got {len(data)}"
            )
        return decompress_packages(index.path, data)

    raise RuntimeError("Could not download a usable Packages index")


def fetch_fallback_packages_index(source: RepoSource, arch: str) -> str:
    index_path = f"dists/{source.distribution}/{source.component}/binary-{arch}/Packages"
    for suffix in (".gz", ".bz2", ".xz", ""):
        url = urljoin(source.base_url, index_path + suffix)
        try:
            data = fetch_bytes(url)
            return decompress_packages(index_path + suffix, data)
        except Exception as exc:
            print(f"Could not use {url}: {exc}")
    raise SystemExit("Could not download Termux Packages index")


def fetch_packages_index(source: RepoSource, arch: str) -> str:
    last_error: Exception | None = None
    for candidate in candidate_sources(source):
        try:
            return fetch_release_packages_index(candidate, arch)
        except Exception as exc:
            print(f"Could not use Release metadata from {candidate.base_url}: {exc}")
            last_error = exc
            try:
                return fetch_fallback_packages_index(candidate, arch)
            except Exception as fallback_exc:
                print(f"Could not use fallback package index from {candidate.base_url}: {fallback_exc}")
                last_error = fallback_exc

    raise SystemExit(f"Could not download Termux Packages index: {last_error}")


def parse_stanzas(text: str) -> list[dict[str, str]]:
    stanzas: list[dict[str, str]] = []
    current: dict[str, str] = {}
    current_key: str | None = None

    for line in text.splitlines():
        if not line:
            if current:
                stanzas.append(current)
                current = {}
                current_key = None
            continue

        if line.startswith((" ", "\t")) and current_key:
            current[current_key] += "\n" + line
            continue

        key, sep, value = line.partition(":")
        if not sep:
            continue
        current[key] = value.strip()
        current_key = key

    if current:
        stanzas.append(current)

    return stanzas


def parse_package_index(text: str) -> tuple[dict[str, PackageRecord], dict[str, list[str]]]:
    records: dict[str, PackageRecord] = {}
    provides: dict[str, list[str]] = {}

    for fields in parse_stanzas(text):
        name = fields.get("Package")
        if not name or "Filename" not in fields or "SHA256" not in fields:
            continue
        record = PackageRecord(name=name, fields=fields)
        records[name] = record
        for provided in record.provides:
            provides.setdefault(provided, []).append(name)

    return records, provides


def parse_installed_status(root: Path) -> tuple[dict[str, str], set[str]]:
    status_path = root / "var/lib/dpkg/status"
    installed: dict[str, str] = {}
    provided: set[str] = set()
    if not status_path.exists():
        return installed, provided

    for fields in parse_stanzas(status_path.read_text(encoding="utf-8", errors="replace")):
        name = fields.get("Package")
        version = fields.get("Version", "")
        status = fields.get("Status", "")
        if name and "install ok installed" in status:
            installed[name] = version
            if "Provides" in fields:
                for dep in split_depends(fields["Provides"]):
                    provided.add(normalize_dependency(dep))

    return installed, provided


def split_depends(value: str) -> list[str]:
    deps: list[str] = []
    current: list[str] = []
    depth = 0
    for char in value:
        if char == "," and depth == 0:
            part = "".join(current).strip()
            if part:
                deps.append(part)
            current = []
            continue
        if char == "(":
            depth += 1
        elif char == ")" and depth:
            depth -= 1
        current.append(char)
    part = "".join(current).strip()
    if part:
        deps.append(part)
    return deps


def normalize_dependency(dep: str) -> str:
    return parse_dependency_alternative(dep.split("|", 1)[0]).name


def parse_dependency_alternative(dep: str) -> DependencyAlternative:
    dep = dep.strip()
    match = re.match(r"^([A-Za-z0-9.+-]+)(?::[A-Za-z0-9-]+)?(?:\s*\((<<|<=|=|>=|>>)\s*([^)]+)\))?$", dep)
    if not match:
        return DependencyAlternative(re.sub(r"\s*\([^)]*\)", "", dep).split(":", 1)[0].strip())
    return DependencyAlternative(
        name=match.group(1),
        operator=match.group(2),
        version=match.group(3),
    )


def installed_satisfies_dependency(installed_version: str, alternative: DependencyAlternative) -> bool:
    if alternative.operator is None or alternative.version is None:
        return True
    if alternative.operator == "=":
        return installed_version == alternative.version

    # Versioned non-equality constraints are rare in this bootstrap path. If a
    # package is already installed but constrained, inject the repo version so
    # apt sees a coherent current dependency set instead of a stale bootstrap mix.
    return False


def choose_dependency(
    dep: str,
    records: dict[str, PackageRecord],
    provides: dict[str, list[str]],
    installed: dict[str, str],
    installed_provides: set[str],
) -> DependencyChoice | None:
    alternatives = [parse_dependency_alternative(part) for part in dep.split("|")]
    for alt in alternatives:
        installed_version = installed.get(alt.name)
        if installed_version is not None and installed_satisfies_dependency(installed_version, alt):
            return None
        if alt.name in installed_provides and alt.operator is None:
            return None
    for alt in alternatives:
        if alt.name in records:
            return DependencyChoice(name=alt.name, force=alt.name in installed)
    for alt in alternatives:
        providers = provides.get(alt.name, [])
        if providers:
            return DependencyChoice(name=providers[0])
    raise SystemExit(f"Could not resolve dependency: {dep}")


def resolve_packages(
    requested: list[str],
    records: dict[str, PackageRecord],
    provides: dict[str, list[str]],
    installed: dict[str, str],
    installed_provides: set[str],
) -> list[str]:
    resolved: list[str] = []
    seen: set[str] = set()
    visiting: set[str] = set()

    def visit(name: str, force: bool = False) -> None:
        if (name in installed and not force) or name in seen:
            return
        if name in visiting:
            return
        if name not in records:
            raise SystemExit(f"Package not found in repo index: {name}")

        visiting.add(name)
        record = records[name]
        for dep in record.depends:
            chosen = choose_dependency(dep, records, provides, installed, installed_provides)
            if chosen:
                visit(chosen.name, force=chosen.force)
        visiting.remove(name)
        seen.add(name)
        resolved.append(name)

    for package in requested:
        visit(package, force=package in installed)

    return resolved


def _decode_ar_name(raw_name: bytes) -> str:
    name = raw_name.decode("utf-8").strip()
    if name.endswith("/"):
        name = name[:-1]
    return name


def read_ar_archive(path: Path) -> list[ArMember]:
    with path.open("rb") as archive:
        if archive.read(len(AR_GLOBAL_HEADER)) != AR_GLOBAL_HEADER:
            raise RuntimeError(f"Not an ar archive: {path}")

        members: list[ArMember] = []
        while True:
            header = archive.read(AR_FILE_HEADER_SIZE)
            if not header:
                break
            if len(header) != AR_FILE_HEADER_SIZE:
                raise RuntimeError(f"Truncated ar header in {path}")
            if header[58:60] != AR_FILE_MAGIC:
                raise RuntimeError(f"Invalid ar member header in {path}")

            size = int(header[48:58].decode("ascii").strip())
            data = archive.read(size)
            if len(data) != size:
                raise RuntimeError(f"Truncated ar member data in {path}")
            if size % 2 == 1:
                archive.read(1)

            members.append(
                ArMember(
                    name=_decode_ar_name(header[0:16]),
                    mtime=header[16:28].decode("ascii").strip() or "0",
                    uid=header[28:34].decode("ascii").strip() or "0",
                    gid=header[34:40].decode("ascii").strip() or "0",
                    mode=header[40:48].decode("ascii").strip() or "100644",
                    data=data,
                )
            )

        return members


def open_tar_bytes(name: str, data: bytes) -> tarfile.TarFile:
    if name.endswith(".xz"):
        stream = io.BytesIO(lzma.decompress(data))
    elif name.endswith(".gz"):
        stream = io.BytesIO(gzip.decompress(data))
    elif name.endswith(".bz2"):
        stream = io.BytesIO(bz2.decompress(data))
    elif name.endswith(".tar"):
        stream = io.BytesIO(data)
    else:
        raise RuntimeError(f"Unsupported tar archive format: {name}")
    return tarfile.open(fileobj=stream, mode="r:")


def extract_control(members: list[ArMember]) -> tuple[dict[str, bytes], dict[str, str]]:
    control_member = next((m for m in members if m.name.startswith("control.tar")), None)
    if control_member is None:
        raise RuntimeError("No control.tar.* member found")

    files: dict[str, bytes] = {}
    with open_tar_bytes(control_member.name, control_member.data) as tar:
        for member in tar:
            if not member.isfile():
                continue
            extracted = tar.extractfile(member)
            if extracted is None:
                continue
            files[member.name.lstrip("./")] = extracted.read()

    control = files.get("control")
    if control is None:
        raise RuntimeError("No control file found in control archive")
    fields = parse_stanzas(control.decode("utf-8", errors="replace"))[0]
    return files, fields


def data_member(members: list[ArMember]) -> ArMember:
    member = next((m for m in members if m.name.startswith("data.tar")), None)
    if member is None:
        raise RuntimeError("No data.tar.* member found")
    return member


def full_prefix(package_name: str) -> str:
    return f"/data/data/{package_name}/files/usr"


def map_tar_path(name: str, old_package: str) -> str | None:
    path = name.lstrip("./")
    prefix = full_prefix(old_package).lstrip("/")
    user_prefix = f"data/user/0/{old_package}/files/usr"

    for candidate in (prefix, user_prefix):
        if candidate.startswith(path.rstrip("/") + "/"):
            return None
        if path == candidate:
            return None
        if path.startswith(candidate + "/"):
            return path[len(candidate) + 1 :]

    return path


def metadata_path(rel_path: str, old_package: str) -> str:
    return f"{full_prefix(old_package)}/{rel_path}".rstrip("/")


def ensure_parent_dirs(list_entries: set[str], rel_path: str, old_package: str) -> None:
    parts = Path(rel_path).parts
    for index in range(1, len(parts)):
        list_entries.add(metadata_path("/".join(parts[:index]), old_package) + "/")


def append_symlink(root: Path, target: str, rel_path: str) -> None:
    symlinks = root / "SYMLINKS.txt"
    with symlinks.open("a", encoding="utf-8") as out:
        out.write(f"{target}\u2190./{rel_path}\n")


def symlink_entry_exists(root: Path, rel_path: str) -> bool:
    symlinks = root / "SYMLINKS.txt"
    if not symlinks.exists():
        return False

    suffix = f"\u2190./{rel_path}"
    for line in symlinks.read_text(encoding="utf-8", errors="replace").splitlines():
        if line.endswith(suffix):
            return True
    return False


def parse_symlink_line(line: str) -> tuple[str, str] | None:
    target, sep, rel_path = line.partition("\u2190")
    if sep != "\u2190" or not target or not rel_path:
        return None
    return target, rel_path


def normalize_symlinks(root: Path) -> None:
    symlinks = root / "SYMLINKS.txt"
    if not symlinks.exists():
        return

    # TermuxInstaller creates links in a plain loop, so duplicate destinations
    # crash with EEXIST. Keep the last entry for each destination so injected
    # packages can replace links that came from the base bootstrap.
    by_destination: dict[str, str] = {}
    order: list[str] = []
    for line in symlinks.read_text(encoding="utf-8", errors="replace").splitlines():
        parsed = parse_symlink_line(line)
        if parsed is None:
            continue
        target, rel_path = parsed
        destination = rel_path.removeprefix("./")
        if (root / destination).exists():
            continue
        if rel_path not in by_destination:
            order.append(rel_path)
        by_destination[rel_path] = target

    symlinks.write_text(
        "".join(f"{by_destination[rel_path]}\u2190{rel_path}\n" for rel_path in order if rel_path in by_destination),
        encoding="utf-8",
    )


def ensure_symlink(root: Path, target: str, rel_path: str) -> None:
    if (root / rel_path).exists() or symlink_entry_exists(root, rel_path):
        return
    append_symlink(root, target, rel_path)


def ensure_binutils_aliases(root: Path) -> None:
    for alias, target in BINUTILS_ALIASES.items():
        if (root / "bin" / target).exists():
            ensure_symlink(root, target, f"bin/{alias}")


def install_data_archive(
    root: Path,
    member: ArMember,
    old_package: str,
) -> tuple[list[str], list[str]]:
    list_entries: set[str] = set()
    md5_entries: list[str] = []

    with open_tar_bytes(member.name, member.data) as tar:
        for tar_member in tar:
            rel_path = map_tar_path(tar_member.name, old_package)
            if rel_path is None or rel_path == "":
                continue
            dest = root / rel_path
            ensure_parent_dirs(list_entries, rel_path, old_package)
            list_entries.add(metadata_path(rel_path, old_package) + ("/" if tar_member.isdir() else ""))

            if tar_member.isdir():
                dest.mkdir(parents=True, exist_ok=True)
                continue

            if tar_member.issym():
                dest.parent.mkdir(parents=True, exist_ok=True)
                append_symlink(root, tar_member.linkname, rel_path)
                continue

            if tar_member.islnk():
                linked_rel = map_tar_path(tar_member.linkname, old_package)
                if linked_rel:
                    linked = root / linked_rel
                    if linked.exists() and linked.is_file():
                        dest.parent.mkdir(parents=True, exist_ok=True)
                        shutil.copy2(linked, dest)
                continue

            if not tar_member.isfile():
                continue

            extracted = tar.extractfile(tar_member)
            if extracted is None:
                continue
            data = extracted.read()
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(data)
            mode = tar_member.mode & 0o7777
            if mode:
                os.chmod(dest, mode)
            try:
                os.utime(dest, (tar_member.mtime, tar_member.mtime))
            except OSError:
                pass

            md5 = hashlib.md5(data).hexdigest()
            md5_entries.append(f"{md5}  {metadata_path(rel_path, old_package).lstrip('/')}")

    return sorted(list_entries), md5_entries


def write_package_metadata(
    root: Path,
    package: str,
    control_files: dict[str, bytes],
    control_fields: dict[str, str],
    list_entries: list[str],
    md5_entries: list[str],
) -> None:
    info_dir = root / "var/lib/dpkg/info"
    info_dir.mkdir(parents=True, exist_ok=True)

    (info_dir / f"{package}.list").write_text("\n".join(list_entries) + "\n", encoding="utf-8")
    (info_dir / f"{package}.md5sums").write_text("\n".join(md5_entries) + "\n", encoding="utf-8")

    for name in ("conffiles", "preinst", "postinst", "prerm", "postrm"):
        if name in control_files:
            path = info_dir / f"{package}.{name}"
            path.write_bytes(control_files[name])
            if name != "conffiles":
                os.chmod(path, 0o700)

    status_path = root / "var/lib/dpkg/status"
    status_text = status_path.read_text(encoding="utf-8", errors="replace") if status_path.exists() else ""
    stanzas = parse_stanzas(status_text)
    stanzas = [fields for fields in stanzas if fields.get("Package") != package]

    package_status = dict(control_fields)
    package_status["Status"] = "install ok installed"
    stanzas.append(package_status)

    rendered: list[str] = []
    for fields in stanzas:
        for key, value in fields.items():
            rendered.append(f"{key}: {value}")
        rendered.append("")
    status_path.write_text("\n".join(rendered).rstrip() + "\n\n", encoding="utf-8")


def download_deb(source: RepoSource, record: PackageRecord, download_dir: Path) -> Path:
    url = urljoin(source.base_url, record.filename)
    dest = download_dir / Path(record.filename).name
    data = fetch_bytes(url)
    actual = hashlib.sha256(data).hexdigest().lower()
    if actual != record.sha256:
        raise SystemExit(f"SHA256 mismatch for {record.name}: expected {record.sha256}, got {actual}")
    dest.write_bytes(data)
    return dest


def inject_package(root: Path, old_package: str, package: str, deb_path: Path) -> None:
    print(f"Injecting {package} from {deb_path.name}")
    members = read_ar_archive(deb_path)
    control_files, control_fields = extract_control(members)
    data = data_member(members)
    list_entries, md5_entries = install_data_archive(root, data, old_package)
    write_package_metadata(root, package, control_files, control_fields, list_entries, md5_entries)


def main() -> int:
    args = parse_args()
    root = args.root.resolve()
    if not root.is_dir():
        raise SystemExit(f"Not a directory: {root}")

    source = read_sources_list(root)
    if args.repo_url:
        source = RepoSource(args.repo_url.rstrip("/") + "/", source.distribution, source.component)

    requested = args.packages or list(DEFAULT_PACKAGES)
    packages_text = fetch_packages_index(source, args.arch)
    records, provides = parse_package_index(packages_text)
    installed, installed_provides = parse_installed_status(root)
    resolved = resolve_packages(requested, records, provides, installed, installed_provides)

    print("Resolved package injection order:")
    for package in resolved:
        print(f"  {package}")

    with tempfile.TemporaryDirectory(prefix="autopie-termux-debs.") as tmp:
        download_dir = Path(tmp)
        for package in resolved:
            deb_path = download_deb(source, records[package], download_dir)
            inject_package(root, args.old_package, package, deb_path)
        if "binutils" in resolved:
            ensure_binutils_aliases(root)
        normalize_symlinks(root)
        if args.keep_downloads:
            keep_dir = root.parent / "downloaded-debs"
            keep_dir.mkdir(exist_ok=True)
            for deb in download_dir.glob("*.deb"):
                shutil.copy2(deb, keep_dir / deb.name)
            print(f"Kept downloaded debs in {keep_dir}")

    print(f"Injected {len(resolved)} package(s).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
