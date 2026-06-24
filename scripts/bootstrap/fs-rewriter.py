#!/usr/bin/env python3
from __future__ import annotations

import argparse
import codecs
import os
import shutil
import tempfile
from pathlib import Path

OLD_PACKAGE = "com.termux"
NEW_PACKAGE = "com.autopi"


def looks_like_text(data: bytes) -> bool:
    if not data:
        return True

    if data.startswith(b"#!"):
        return True

    if (
        data.startswith(codecs.BOM_UTF8)
        or data.startswith(codecs.BOM_UTF16_LE)
        or data.startswith(codecs.BOM_UTF16_BE)
    ):
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

    sample = data[:4096]
    printable = sum(1 for b in sample if 32 <= b <= 126 or b in (9, 10, 13))
    return printable / max(1, len(sample)) > 0.85


def build_rewrites(old_package: str, new_package: str) -> list[tuple[bytes, bytes]]:
    old_prefix = f"/data/data/{old_package}/files/usr"
    new_prefix = f"/data/data/{new_package}/files/usr"
    old_root_dir = f"/data/data/{old_package}"
    new_root_dir = f"/data/data/{new_package}"

    rewrites = [
        (old_prefix, new_prefix),
        (old_root_dir, new_root_dir),
        (f".{old_prefix}", f".{new_prefix}"),
        (f".{old_root_dir}", f".{new_root_dir}"),
        (f"/data/user/0/{old_package}/files/usr", new_prefix),
        (f"/data/user/0/{old_package}", new_root_dir),
        (f"./data/user/0/{old_package}/files/usr", f".{new_prefix}"),
        (f"./data/user/0/{old_package}", f".{new_root_dir}"),
        (f"/data/user_de/0/{old_package}", f"/data/user_de/0/{new_package}"),
        (
            f"/storage/emulated/0/Android/data/{old_package}",
            f"/storage/emulated/0/Android/data/{new_package}",
        ),
        (
            f"/sdcard/Android/data/{old_package}",
            f"/sdcard/Android/data/{new_package}",
        ),
        (old_package, new_package),
    ]

    unique_rewrites = {}
    for old, new in rewrites:
        unique_rewrites[old.encode()] = new.encode()

    return sorted(
        unique_rewrites.items(),
        key=lambda pair: len(pair[0]),
        reverse=True,
    )


def rewrite_text_bytes(data: bytes, rewrites: list[tuple[bytes, bytes]]) -> bytes:
    for old, new in rewrites:
        data = data.replace(old, new)
    return data


def rewrite_binary_bytes(data: bytes, rewrites: list[tuple[bytes, bytes]]) -> bytes:
    """
    Fixed-size patching for binaries.

    Safe only when len(new) <= len(old), because we preserve file size by
    padding with NUL bytes.
    """
    for old, new in rewrites:
        if old not in data:
            continue
        if len(new) > len(old):
            # Too long to patch in place safely.
            print(
                f"[SKIP] Cannot rewrite {old!r} to {new!r} "
                "in binary file (too long)"
            )
            continue

        padded = new + b"\x00" * (len(old) - len(new))
        data = data.replace(old, padded)

    return data


def rewrite_file(
    path: Path,
    rewrites: list[tuple[bytes, bytes]],
    dry_run: bool = False,
    backup: bool = False,
) -> bool:
    try:
        original = path.read_bytes()
    except (OSError, PermissionError):
        return False

    is_text = looks_like_text(original)

    if is_text:
        updated = rewrite_text_bytes(original, rewrites)
    else:
        updated = rewrite_binary_bytes(original, rewrites)

    if updated == original:
        return False

    if dry_run:
        kind = "text" if is_text else "binary"
        print(f"[DRY:{kind}] {path}")
        return True

    if backup:
        backup_path = path.with_name(path.name + ".bak")
        if not backup_path.exists():
            shutil.copy2(path, backup_path)

    st = path.stat()
    fd, tmp_name = tempfile.mkstemp(prefix=path.name + ".", dir=str(path.parent))
    try:
        with os.fdopen(fd, "wb") as f:
            f.write(updated)

        os.chmod(tmp_name, st.st_mode)
        try:
            os.utime(tmp_name, ns=(st.st_atime_ns, st.st_mtime_ns))
        except OSError:
            pass

        os.replace(tmp_name, path)
    finally:
        try:
            if os.path.exists(tmp_name):
                os.unlink(tmp_name)
        except OSError:
            pass

    print(f"[OK] {path}")
    return True


def walk_and_rewrite(
    root: Path,
    rewrites: list[tuple[bytes, bytes]],
    dry_run: bool = False,
    backup: bool = False,
) -> int:
    changed = 0
    for dirpath, dirnames, filenames in os.walk(root):
        # Skip symlinked directories to avoid escaping the tree.
        dirnames[:] = [d for d in dirnames if not Path(dirpath, d).is_symlink()]

        for name in filenames:
            path = Path(dirpath) / name

            # Skip symlinks; only rewrite real files in place.
            if path.is_symlink():
                continue

            if rewrite_file(path, rewrites, dry_run=dry_run, backup=backup):
                changed += 1

    return changed


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Recursively rewrite text-like files in a folder."
    )
    parser.add_argument(
        "root",
        type=Path,
        help="Root folder to process (for example: extracted Termux usr directory)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Show what would change without modifying files",
    )
    parser.add_argument(
        "--backup",
        action="store_true",
        help="Create a .bak copy before rewriting each file",
    )
    parser.add_argument(
        "--old-package",
        default=OLD_PACKAGE,
        help=f"Package name to replace (default: {OLD_PACKAGE})",
    )
    parser.add_argument(
        "--new-package",
        default=NEW_PACKAGE,
        help=f"Package name to write (default: {NEW_PACKAGE})",
    )
    args = parser.parse_args()

    root = args.root.resolve()
    if not root.exists() or not root.is_dir():
        raise SystemExit(f"Not a directory: {root}")

    rewrites = build_rewrites(args.old_package, args.new_package)
    count = walk_and_rewrite(
        root,
        rewrites,
        dry_run=args.dry_run,
        backup=args.backup,
    )
    print(f"Changed {count} file(s).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
