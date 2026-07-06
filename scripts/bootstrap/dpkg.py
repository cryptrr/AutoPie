#!/data/data/com.autopi/files/usr/bin/env python
"""
dpkg wrapper for Termux-like prefix rewriting.

Behavior:
- For normal dpkg commands, transparently forwards to the real dpkg.
- For installation / unpack commands, rewrites Termux package contents from:
    /data/data/com.termux/files/usr
  to:
    /data/data/com.autopi/files/usr
  before invoking the real dpkg.

What it rewrites in .deb packages:
- ar archive members are preserved in order
- control.tar.* and data.tar.* are rewritten
- absolute path strings in package member names and symlink targets are rewritten
- likely text files inside the package are byte-replaced

Notes:
- This is designed to be a practical wrapper and package binary rewriter.
- Packages embedding the old prefix inside opaque binary blobs may still need custom handling.
- apt dependency resolution still works normally because apt does that before dpkg runs.
"""

from __future__ import annotations

import gzip
import io
import os
import shutil
import subprocess
import sys
import tarfile
import tempfile
from pathlib import Path
from typing import Iterable, List, Optional
import lzma
import codecs
import copy

OLD_PREFIX = os.environ.get(
    "TERMUX_PREFIX",
    "/data/data/com.termux/files/usr",
)
NEW_PREFIX = os.environ.get(
    "PIE_PREFIX",
    "/data/data/com.autopi/files/usr",
)

#Convert prefix to /data/data/ instead of /data/user/0 for just the first user.
if(NEW_PREFIX.startswith("/data/user/0/")):
    NEW_PREFIX = NEW_PREFIX.replace("/data/user/0/", "/data/data/")

OLD_ROOT_DIR = os.environ.get(
    "TERMUX_ROOT_DIR",
    "/data/data/com.termux",
)
NEW_ROOT_DIR = os.environ.get(
    "PIE_ROOT_DIR",
    "/data/data/com.autopi",
)

REAL_DPKG_ENV_VARS = ("REAL_DPKG", "DPKG_REAL", "TERMUX_REAL_DPKG")

INSTALL_FLAGS = {"-i", "--install", "--unpack"}

TEXT_EXTS = {
    ".sh", ".bash", ".zsh", ".csh", ".ksh",
    ".py", ".pl", ".rb", ".lua", ".awk", ".sed",
    ".txt", ".md", ".rst", ".ini", ".conf", ".cfg",
    ".json", ".yml", ".yaml", ".xml", ".toml",
    ".desktop", ".service", ".socket", ".target", ".timer",
    ".cmake", ".pc", ".la", ".mk", ".in",
    ".profile", ".rc", ".env", ".sh.in",
}


def eprint(*args: object) -> None:
    print(*args, file=sys.stderr)


def which_real_dpkg() -> str:
    """
    Locate the actual dpkg binary.

    Best practice: set REAL_DPKG=/path/to/real/dpkg and name this wrapper 'dpkg'.
    """
    for var in REAL_DPKG_ENV_VARS:
        candidate = os.environ.get(var)
        if candidate and os.path.isfile(candidate) and os.access(candidate, os.X_OK):
            return candidate

    wrapper_real = os.path.realpath(sys.argv[0])

    # Common locations first.
    candidates = [
        f"{NEW_PREFIX}/bin/dpkg.real",
        f"{NEW_PREFIX}/bin/dpkg.orig",
        f"{NEW_PREFIX}/bin/dpkg.bin",
        f"{NEW_PREFIX}/bin/dpkg",
        "/usr/bin/dpkg",
        "/bin/dpkg",
        "/system/bin/dpkg",
    ]
    for candidate in candidates:
        if os.path.isfile(candidate) and os.access(candidate, os.X_OK):
            if os.path.realpath(candidate) != wrapper_real:
                return candidate

    # Fall back to PATH lookup, skipping ourselves.
    for path_dir in os.environ.get("PATH", "").split(os.pathsep):
        if not path_dir:
            continue
        candidate = os.path.join(path_dir, "dpkg")
        if os.path.isfile(candidate) and os.access(candidate, os.X_OK):
            if os.path.realpath(candidate) != wrapper_real:
                return candidate

    raise FileNotFoundError(
        "Could not locate the real dpkg. Set REAL_DPKG=/path/to/real/dpkg"
    )


def is_install_like(argv: List[str]) -> bool:
    """
    Heuristically decide whether this dpkg invocation is meant to install/unpack packages.
    """
    if not argv:
        return False

    # dpkg -i file.deb
    # dpkg --install file.deb
    # dpkg --unpack file.deb
    # dpkg -i --status-fd=... file.deb
    for arg in argv:
        if arg in INSTALL_FLAGS:
            return True

    # If a .deb file is present, dpkg is very likely doing install/unpack.
    if any(arg.endswith(".deb") for arg in argv):
        return True

    return False

def get_install_mode(argv):
    if "--recursive" in argv:
        return "recursive"

    if any(arg.endswith(".deb") for arg in argv):
        return "deb"

    return None


def list_deb_paths(argv: List[str]) -> List[str]:
    return [arg for arg in argv if arg.endswith(".deb") and os.path.exists(arg)]


def run_passthrough(real_dpkg: str, argv: List[str]) -> int:
    """
    Forward everything to the real dpkg, preserving file descriptors.
    """
    proc = subprocess.run(
        [real_dpkg, *argv],
        close_fds=False,
    )
    return proc.returncode


def is_probably_text(member_name: str, data: bytes) -> bool:
    if b"\x00" in data:
        return False

    lower = member_name.lower()
    if lower.endswith(tuple(TEXT_EXTS)):
        return True

    if data.startswith(b"#!"):
        return True

    if lower.startswith(("etc/", "usr/share/", "share/", "lib/pkgconfig/", "usr/lib/pkgconfig/")):
        return True

    # UTF-8 sanity check.
    try:
        data.decode("utf-8")
        return True
    except UnicodeDecodeError:
        return False


def rewrite_bytes(member_name: str, data: bytes) -> bytes:
    if OLD_PREFIX.encode() not in data:
        return data

    if not is_probably_text(member_name, data):
        return data

    return data.replace(OLD_PREFIX.encode(), NEW_PREFIX.encode())


def rewrite_string(value: Optional[str]) -> Optional[str]:
    if value is None:
        return None
    return value.replace(OLD_PREFIX, NEW_PREFIX)




def looks_like_text(data: bytes) -> bool:
    if not data:
        return True

    if data.startswith(b"#!"):
        return True

    if data.startswith(codecs.BOM_UTF8) or data.startswith(codecs.BOM_UTF16_LE) or data.startswith(codecs.BOM_UTF16_BE):
        return True

    if data.startswith(b"\x7fELF"):
        return False

    if data.startswith(b"!<arch>\n"):
        return False

    if data.startswith(b"PK\x03\x04"):
        return False

    if data.startswith(b"SQLite format 3"):
        return False

    if b"\x00" in data:
        return False

    try:
        data.decode("utf-8")
        return True
    except UnicodeDecodeError:
        pass

    # Fallback heuristic: mostly printable ASCII / whitespace
    sample = data[:4096]
    printable = sum(
        1 for b in sample
        if 32 <= b <= 126 or b in (9, 10, 13)
    )
    return printable / max(1, len(sample)) > 0.85


def rewrite_text_bytes(data: bytes) -> bytes:
    CONTROL_TEXT_REWRITES = [
        (OLD_PREFIX.encode(), NEW_PREFIX.encode()),
        (OLD_ROOT_DIR.encode(), NEW_ROOT_DIR.encode()),
        (f".{OLD_ROOT_DIR}/files/usr".encode(), f".{NEW_ROOT_DIR}/files/usr".encode()),
        (f".{OLD_ROOT_DIR}".encode(), f".{NEW_ROOT_DIR}".encode()),
    ]
    for old, new in CONTROL_TEXT_REWRITES:
        data = data.replace(old, new)
    return data

def rewrite_binary_bytes(data: bytes) -> bytes:
    REWRITES = [
        (OLD_PREFIX.encode(), NEW_PREFIX.encode()),
        (OLD_ROOT_DIR.encode(), NEW_ROOT_DIR.encode()),
        (f".{OLD_ROOT_DIR}/files/usr".encode(), f".{NEW_ROOT_DIR}/files/usr".encode()),
        (f".{OLD_ROOT_DIR}".encode(), f".{NEW_ROOT_DIR}".encode()),
    ]

    buf = bytearray(data)

    for old, new in REWRITES:
        start = 0
        while True:
            idx = buf.find(old, start)
            if idx == -1:
                break

            # Safe for embedded C strings / fixed buffers:
            # - exact fit: replace directly
            # - shorter: replace and pad with NULs
            # - longer: skip, because it would shift binary layout
            if len(new) <= len(old):
                replacement = new.ljust(len(old), b"\x00")
                buf[idx : idx + len(old)] = replacement
                start = idx + len(old)
            else:
                # Can't expand in-place without breaking offsets/layout
                start = idx + len(old)

    return bytes(buf)


def rewrite_member_name(name: str, old_prefix: str, new_prefix: str) -> str:
    if name.startswith(old_prefix):
        return new_prefix + name[len(old_prefix):]
    return name


def rewrite_linkname(linkname: str, old_prefix: str, new_prefix: str) -> str:
    if linkname.startswith(old_prefix):
        return new_prefix + linkname[len(old_prefix):]
    return linkname

def rewrite_path(path: str) -> str:
    # Most specific first.
    if path == "./data/data/com.termux":
        return f".{NEW_ROOT_DIR}"

    if path.startswith("./data/data/com.termux/"):
        return f".{NEW_ROOT_DIR}" + path[len("./data/data/com.termux"):]

    data_user_dir = os.path.dirname(NEW_ROOT_DIR)

    if path == "./data/data":
        return f".{data_user_dir}"

    if path.startswith("./data/data/"):
        return f".{data_user_dir}" + path[len("./data/data"):]

    return path


def rewrite_tar_paths_xz(input_xz_path, output_xz_path, old_prefix, new_prefix):
    
    old_bytes = old_prefix.encode()
    new_bytes = new_prefix.encode()
    
    if input_xz_path.endswith(".xz"):
        print(f"Detected .xz archive: {input_xz_path}", file=sys.stderr)
        out_mode = "w:xz"
    elif input_xz_path.endswith(".gz"):
        print(f"Detected .gz archive: {input_xz_path}", file=sys.stderr)
        out_mode = "w:gz"
    elif input_xz_path.endswith(".bz2"):
        print(f"Detected .bz2 archive: {input_xz_path}", file=sys.stderr)
        out_mode = "w:bz2"
    else:
        print(f"Detected uncompressed archive: {input_xz_path}", file=sys.stderr)
        out_mode = "w:"


    with open_compressed(input_xz_path, "rb") as f_in:
        with tarfile.open(fileobj=f_in, mode="r:") as tar_in:
            with open_compressed(output_xz_path, "wb") as f_out:
                with tarfile.open(
                    fileobj=f_out,
                    mode="w:",
                    format=tarfile.USTAR_FORMAT,
                ) as tar_out:
                    for member in tar_in:
                        # Clone TarInfo so we do not mutate the source object in place.
                        
                        ti = copy.copy(member)

                        # Rewrite the archive path itself.
                        #ti.name = rewrite_member_name(ti.name, old_prefix, new_prefix)
                        ti.name = rewrite_path(ti.name)
                        
                        print(
                            f"rewrite: {member.name} -> {ti.name}",
                            file=sys.stderr,
                        )

                        # Rewrite symlink / hardlink targets.
                        if ti.issym() or ti.islnk():
                            print(
                                f"rewriting link target for {ti.name}: {ti.linkname}",
                                file=sys.stderr,
                            )
                            #ti.linkname = rewrite_linkname(ti.linkname, old_prefix, new_prefix)
                            ti.linkname = rewrite_path(ti.linkname)

                        # Rewrite PAX headers too, if present.
                        if ti.pax_headers:
                            print(
                                f"rewriting PAX headers for {ti.name}",
                                file=sys.stderr
                            )
                            new_pax = {}
                            for k, v in ti.pax_headers.items():
                                if isinstance(v, str):
                                    new_pax[k] = v.replace(old_prefix, new_prefix)
                                else:
                                    new_pax[k] = v
                            ti.pax_headers = new_pax

                        # Directories, symlinks, devices, etc. do not have file payloads.
                        if ti.isfile():
                            #print( f"rewriting file payload for {ti.name}", file=sys.stderr, )
                            extracted = tar_in.extractfile(member)
                            data = extracted.read() if extracted is not None else b""

                            if looks_like_text(data):
                                #print( f"rewriting text file {ti.name} with size {len(data)}", file=sys.stderr, )
                                new_data = rewrite_text_bytes(data)
                                if new_data != data:
                                    print(f"rewriting text file {ti.name}", file=sys.stderr)
                                    data = new_data
                            else:
                                new_data = rewrite_binary_bytes(data)
                                if new_data != data:
                                    print(f"rewriting binary file {ti.name}", file=sys.stderr)
                                    data = new_data

                            ti.size = len(data)
                            tar_out.addfile(ti, io.BytesIO(data))
                        else:
                            # Preserve directory/symlink metadata.
                            print(
                                f"Preserve directory/symlink metadata:adding non-file member {ti.name} without payload",
                                file=sys.stderr,
                            )
                            tar_out.addfile(ti)
                        
#Addition

def open_compressed(path, mode):
    path = str(path)

    if path.endswith(".xz"):
        return lzma.open(path, mode)

    if path.endswith(".gz"):
        return gzip.open(path, mode)

    if path.endswith(".tar"):
        return open(path, mode)

    raise RuntimeError(f"Unsupported archive format: {path}")

def find_control_archive(tempdir: str) -> str:
    matches = [
        p for p in Path(tempdir).iterdir()
        if p.name.startswith("control.tar")
    ]

    if len(matches) != 1:
        raise RuntimeError(
            f"Expected exactly one control.tar.*, found: {[p.name for p in matches]}"
        )

    return str(matches[0])


def find_data_archive(tempdir: str) -> str:
    matches = [
        p for p in Path(tempdir).iterdir()
        if p.name.startswith("data.tar")
    ]

    if len(matches) != 1:
        raise RuntimeError(
            f"Expected exactly one data.tar.*, found: {[p.name for p in matches]}"
        )

    return str(matches[0])

def rewrite_deb_inplace(input_deb, output_deb, old_prefix, new_prefix):
    tempdir = tempfile.mkdtemp()
    try:
        
        input_deb_path = os.path.abspath(input_deb)

        # Extract ar members without decompressing data.tar.xz
        subprocess.run(["gar", "x", input_deb_path], cwd=tempdir, check=True)

        data_tar_xz = find_data_archive(tempdir)
        new_data_tar_xz = os.path.join(
            tempdir,
            "new-" + os.path.basename(data_tar_xz)
        )
        
        # Detect control archive type
        #control_file = next(f for f in os.listdir(tempdir) if f.startswith("control.tar"))
        #control_file = os.path.join(tempdir, "control.tar.xz")
        control_file = find_control_archive(tempdir)
        print(f"Detected control archive: {control_file}", file=sys.stderr)
        new_control_file = os.path.join(
            tempdir,
            "new-" + os.path.basename(control_file)
        )
        
        print(
            f"control archive: {control_file}",
            file=sys.stderr,
        )
        
        print(
            f"data archive: {data_tar_xz}",
            file=sys.stderr,
        )

        

        # Rewrite paths directly inside data.tar.xz stream
        rewrite_tar_paths_xz(data_tar_xz, new_data_tar_xz, old_prefix, new_prefix)
        # Rewrite paths and contents directly inside control.tar.xz stream
        rewrite_tar_paths_xz(control_file, new_control_file, old_prefix, new_prefix)

        # Replace the tar.xz in deb
        os.remove(data_tar_xz)
        os.rename(new_data_tar_xz, data_tar_xz)
        os.remove(control_file)
        os.rename(new_control_file, control_file)

        
        # Repack .deb with same order
        # subprocess.run(
        #     ["gar", "r", output_deb, "debian-binary", control_file, data_tar_xz],
        #     cwd=tempdir, check=True
        # )
        subprocess.run(
            ["gar", "cr", output_deb, "debian-binary", os.path.basename(control_file), os.path.basename(data_tar_xz),],
            cwd=tempdir, check=True
        )
        

    finally:
        print(f"Cleaning up temporary directory 12: {tempdir}", file=sys.stderr)
        shutil.rmtree(tempdir)


def get_modified_deb(input_deb, old_prefix, new_prefix):
    output_deb = tempfile.mktemp(prefix="dpkgwrap-out-", suffix=".deb")
    rewrite_deb_inplace(input_deb, output_deb, old_prefix, new_prefix)
    return Path(output_deb)

def rewrite_recursive_dir(src_dir: Path, old_prefix: str, new_prefix: str) -> Path:
    out_dir = Path(tempfile.mkdtemp(prefix="dpkgwrap-rec-"))

    for root, dirs, files in os.walk(src_dir):
        root_p = Path(root)
        rel = root_p.relative_to(src_dir)
        (out_dir / rel).mkdir(parents=True, exist_ok=True)

        for name in files:
            src = root_p / name
            dst = out_dir / rel / name

            if name.endswith(".deb"):
                rewrite_deb_inplace(src, dst, old_prefix, new_prefix)   # your existing deb rewriter
            else:
                shutil.copy2(src, dst)

    return out_dir

def install_debs(argv,real_dpkg, old_prefix, new_prefix) -> int:
    debs = list_deb_paths(argv)
    
    temp_outputs: List[Path] = []
    try:
        rewritten_map = {}
        for deb in debs:
            rewritten = get_modified_deb(deb, old_prefix, new_prefix)
            temp_outputs.append(rewritten)
            rewritten_map[deb] = str(rewritten)

        new_argv = [rewritten_map.get(arg, arg) for arg in argv]
        proc = subprocess.run(
            [real_dpkg, *new_argv],
            close_fds=False,
        )
        return proc.returncode
    finally:
        for p in temp_outputs:
            try:
                if p.exists():
                    p.unlink()
                if p.parent.exists():
                    p.parent.rmdir()
            except Exception:
                pass
            
# def install_recursive(argv, real_dpkg, old_prefix, new_prefix) -> int:
#     recursive_temp_dirs: List[Path] = []

#     try:
#         #
#         # Handle:
#         # dpkg --recursive <dir>
#         #
#         if "--recursive" in argv:
#             idx = argv.index("--recursive")

#             if idx + 1 < len(argv):
#                 recursive_dir = Path(argv[idx + 1])

#                 if recursive_dir.is_dir():
#                     rewritten_dir = rewrite_recursive_dir(
#                         recursive_dir,
#                         old_prefix,
#                         new_prefix,
#                     )

#                     recursive_temp_dirs.append(rewritten_dir)

#                     argv[idx + 1] = str(rewritten_dir)

# def main() -> int:
#     real_dpkg = which_real_dpkg()
#     argv = sys.argv[1:]
#     print(sys.argv, file=sys.stderr)
    
#     old_prefix = "./data/data/com.termux/files/usr"
#     new_prefix = "./data/data/com.autopi/files/usr"

#     if not argv:
#         return run_passthrough(real_dpkg, argv)

#     # # Keep all non-install operations completely transparent.
#     # if not is_install_like(argv):
#     #     return run_passthrough(real_dpkg, argv)
    
#     mode = get_install_mode(argv)

#     if mode is None:
#         return run_passthrough(real_dpkg, argv)

#     elif mode == "deb":
#         install_debs(argv, real_dpkg, old_prefix, new_prefix)

#     elif mode == "recursive":
#         rewrite directory contents

def main() -> int:
    real_dpkg = which_real_dpkg()
    argv = sys.argv[1:]

    print(sys.argv, file=sys.stderr)

    old_prefix = f".{OLD_PREFIX}"
    new_prefix = f".{NEW_PREFIX}"

    if not argv:
        return run_passthrough(real_dpkg, argv)

    if not is_install_like(argv):
        os.execv(real_dpkg, [real_dpkg, *argv])

    temp_outputs: List[Path] = []
    recursive_temp_dirs: List[Path] = []

    try:
        #
        # Handle:
        # dpkg --recursive <dir>
        #
        if "--recursive" in argv:
            idx = argv.index("--recursive")

            if idx + 1 < len(argv):
                recursive_dir = Path(argv[idx + 1])

                if recursive_dir.is_dir():
                    rewritten_dir = rewrite_recursive_dir(
                        recursive_dir,
                        old_prefix,
                        new_prefix,
                    )

                    recursive_temp_dirs.append(rewritten_dir)

                    argv[idx + 1] = str(rewritten_dir)

        #
        # Handle direct .deb arguments
        #
        debs = list_deb_paths(argv)

        rewritten_map = {}

        for deb in debs:
            rewritten = get_modified_deb(
                deb,
                old_prefix,
                new_prefix,
            )

            temp_outputs.append(rewritten)
            rewritten_map[deb] = str(rewritten)

        new_argv = [
            rewritten_map.get(arg, arg)
            for arg in argv
        ]

        print(
            f"Executing: {real_dpkg} {' '.join(new_argv)}",
            file=sys.stderr,
        )

        proc = subprocess.run(
            [real_dpkg, *new_argv],
            close_fds=False,
        )

        return proc.returncode

    finally:
        for p in temp_outputs:
            try:
                print(f"Cleaning up temporary file: {p}", file=sys.stderr)
                if p.exists():
                    p.unlink()

                # if p.parent.exists():
                #     p.parent.rmdir()
            except Exception:
                pass

        for d in recursive_temp_dirs:
            try:
                print(f"Cleaning up temporary directory 11: {d}", file=sys.stderr)
                #shutil.rmtree(d)
            except Exception:
                pass

    


if __name__ == "__main__":
    raise SystemExit(main())
