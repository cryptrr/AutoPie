#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TERMUX_SOURCE_DIR="${TERMUX_SOURCE_DIR:-$ROOT_DIR/termux-app}"
TERMUX_APP_DIR="$TERMUX_SOURCE_DIR/app"
ASSETS_DIR="${AUTOPIE_ASSETS_DIR:-$ROOT_DIR/app/src/main/assets}"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT_DIR/.gradle}"
FS_REWRITER="${AUTOPIE_FS_REWRITER:-$ROOT_DIR/scripts/bootstrap/fs-rewriter.py}"
DPKG_WRAPPER="${AUTOPIE_DPKG_WRAPPER:-$ROOT_DIR/scripts/bootstrap/dpkg.py}"
AUTOPIE_SECRET_WRAPPER="${AUTOPIE_SECRET_WRAPPER:-$ROOT_DIR/scripts/bootstrap/autopie-secret}"
BOOTSTRAP_EXTENDER="${AUTOPIE_BOOTSTRAP_EXTENDER:-$ROOT_DIR/scripts/bootstrap/extend-bootstrap.py}"
EXTRA_BOOTSTRAP_PACKAGES="${AUTOPIE_BOOTSTRAP_PACKAGES:-python,python-pip,binutils,openssh,sshpass}"
ARCH="${TERMUX_BOOTSTRAP_ARCH:-aarch64}"
OLD_PACKAGE="${TERMUX_BOOTSTRAP_OLD_PACKAGE:-com.termux}"
NEW_PACKAGE="${TERMUX_BOOTSTRAP_NEW_PACKAGE:-com.autopi}"
NEW_ROOT_DIR="${TERMUX_BOOTSTRAP_NEW_ROOT_DIR:-}"
PACKAGE_VARIANT="${TERMUX_PACKAGE_VARIANT:-apt-android-7}"
GRADLE_TASK="${TERMUX_BOOTSTRAP_GRADLE_TASK:-:app:downloadAutoPieBootstrap}"

SOURCE_ZIP="$TERMUX_APP_DIR/src/main/cpp/bootstrap-$ARCH.zip"
DEST_ZIP="$ASSETS_DIR/bootstrap-$ARCH.zip"

usage() {
    cat <<USAGE
Usage: $0 [--new-root-dir PATH]

Options:
  --new-root-dir PATH  Target app root. Defaults to /data/data/$NEW_PACKAGE.
                       For secondary user 10, pass /data/user/10 or
                       /data/user/10/$NEW_PACKAGE.

Environment:
  TERMUX_BOOTSTRAP_NEW_ROOT_DIR  Same as --new-root-dir.
USAGE
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --new-root-dir|--new-root)
            if [[ $# -lt 2 || -z "$2" ]]; then
                echo "$1 requires a path argument" >&2
                exit 1
            fi
            NEW_ROOT_DIR="$2"
            shift 2
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            usage >&2
            exit 1
            ;;
    esac
done

normalize_root_dir() {
    local root="$1"

    if [[ -z "$root" ]]; then
        printf '/data/data/%s\n' "$NEW_PACKAGE"
        return
    fi

    root="${root%/}"
    if [[ "$root" != /* ]]; then
        echo "Custom root must be an absolute Android data path: $root" >&2
        exit 1
    fi

    if [[ "$root" != */"$NEW_PACKAGE" ]]; then
        root="$root/$NEW_PACKAGE"
    fi

    printf '%s\n' "$root"
}

TARGET_ROOT_DIR="$(normalize_root_dir "$NEW_ROOT_DIR")"
TARGET_PREFIX="$TARGET_ROOT_DIR/files/usr"
WORK_DIR="$(mktemp -d "$ROOT_DIR/.termux-bootstrap.XXXXXX")"
EXTRACTED_DIR="$WORK_DIR/extracted"
PATCHED_ZIP="$WORK_DIR/bootstrap-$ARCH.zip"
PATCHED_DPKG_WRAPPER="$WORK_DIR/dpkg"

cleanup() {
    rm -rf "$WORK_DIR"
}
trap cleanup EXIT

sha256_file() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    else
        shasum -a 256 "$1" | awk '{print $1}'
    fi
}

if [[ ! -f "$TERMUX_SOURCE_DIR/gradlew" ]]; then
    echo "Termux source is not prepared at $TERMUX_SOURCE_DIR" >&2
    echo "Run scripts/prepare-termux-app.sh first." >&2
    exit 1
fi

if [[ ! -f "$FS_REWRITER" ]]; then
    echo "Missing fs-rewriter.py at $FS_REWRITER" >&2
    exit 1
fi

if [[ ! -f "$DPKG_WRAPPER" ]]; then
    echo "Missing dpkg wrapper at $DPKG_WRAPPER" >&2
    exit 1
fi

if [[ ! -f "$AUTOPIE_SECRET_WRAPPER" ]]; then
    echo "Missing autopie-secret wrapper at $AUTOPIE_SECRET_WRAPPER" >&2
    exit 1
fi

if [[ ! -f "$BOOTSTRAP_EXTENDER" ]]; then
    echo "Missing bootstrap package extender at $BOOTSTRAP_EXTENDER" >&2
    exit 1
fi

echo "Downloading Termux bootstrap with Gradle"
echo "  source:  $TERMUX_SOURCE_DIR"
echo "  variant: $PACKAGE_VARIANT"
echo "  arch:    $ARCH"
echo "  root:    $TARGET_ROOT_DIR"

(
    cd "$TERMUX_SOURCE_DIR"
    export GRADLE_USER_HOME
    TERMUX_PACKAGE_VARIANT="$PACKAGE_VARIANT" \
    AUTOPIE_BOOTSTRAP_ARCH="$ARCH" \
        bash ./gradlew --no-daemon "$GRADLE_TASK"
)

if [[ ! -f "$SOURCE_ZIP" ]]; then
    echo "Gradle task completed, but did not create $SOURCE_ZIP" >&2
    exit 1
fi

mkdir -p "$EXTRACTED_DIR"
unzip -q "$SOURCE_ZIP" -d "$EXTRACTED_DIR"

echo "Injecting Termux packages into bootstrap"
IFS=',' read -r -a EXTRA_PACKAGE_NAMES <<< "$EXTRA_BOOTSTRAP_PACKAGES"
EXTRA_PACKAGE_ARGS=()
for package_name in "${EXTRA_PACKAGE_NAMES[@]}"; do
    package_name="${package_name//[[:space:]]/}"
    if [[ -n "$package_name" ]]; then
        EXTRA_PACKAGE_ARGS+=(--package "$package_name")
    fi
done

if [[ "${#EXTRA_PACKAGE_ARGS[@]}" -gt 0 ]]; then
    python3 "$BOOTSTRAP_EXTENDER" "$EXTRACTED_DIR" \
        --arch "$ARCH" \
        --old-package "$OLD_PACKAGE" \
        "${EXTRA_PACKAGE_ARGS[@]}"
fi

echo "Patching bootstrap strings"
python3 "$FS_REWRITER" "$EXTRACTED_DIR" \
    --old-package "$OLD_PACKAGE" \
    --new-package "$NEW_PACKAGE" \
    --new-root-dir "$TARGET_ROOT_DIR"

echo "Installing dpkg wrapper"
if [[ ! -f "$EXTRACTED_DIR/bin/dpkg" ]]; then
    echo "Missing dpkg binary in extracted bootstrap: $EXTRACTED_DIR/bin/dpkg" >&2
    exit 1
fi
if [[ -e "$EXTRACTED_DIR/bin/dpkg.real" ]]; then
    echo "Unexpected existing dpkg.real in extracted bootstrap: $EXTRACTED_DIR/bin/dpkg.real" >&2
    exit 1
fi
mv "$EXTRACTED_DIR/bin/dpkg" "$EXTRACTED_DIR/bin/dpkg.real"
python3 - "$DPKG_WRAPPER" "$PATCHED_DPKG_WRAPPER" "$TARGET_ROOT_DIR" "$TARGET_PREFIX" <<'PY'
from pathlib import Path
import sys

source = Path(sys.argv[1])
dest = Path(sys.argv[2])
target_root = sys.argv[3].rstrip("/")
target_prefix = sys.argv[4].rstrip("/")

text = source.read_text(encoding="utf-8")
lines = text.splitlines(keepends=True)
if not lines or not lines[0].startswith("#!"):
    raise SystemExit(f"Missing shebang in dpkg wrapper: {source}")

line_ending = "\n" if lines[0].endswith("\n") else ""
lines[0] = f"#!{target_prefix}/bin/env python{line_ending}"
text = "".join(lines)
text = text.replace("/data/data/com.autopi/files/usr", target_prefix)
text = text.replace("/data/data/com.autopi", target_root)

dest.write_text(text, encoding="utf-8")
PY
install -m 0700 "$PATCHED_DPKG_WRAPPER" "$EXTRACTED_DIR/bin/dpkg"

echo "Installing autopie-secret wrapper"
install -m 0700 "$AUTOPIE_SECRET_WRAPPER" "$EXTRACTED_DIR/bin/autopie-secret"

echo "Repacking patched bootstrap"
if command -v 7z >/dev/null 2>&1; then
    (
        cd "$EXTRACTED_DIR"
        7z a -tzip -mx=9 -mm=Deflate -mfb=258 -mpass=15 "$PATCHED_ZIP" . >/dev/null
    )
else
    echo "7z not found; falling back to Python zipfile compression"
    python3 - "$EXTRACTED_DIR" "$PATCHED_ZIP" <<'PY'
from __future__ import annotations

import sys
import time
import zipfile
from pathlib import Path

root = Path(sys.argv[1])
out = Path(sys.argv[2])

with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
    for path in sorted(root.rglob("*"), key=lambda p: p.relative_to(root).as_posix()):
        rel = path.relative_to(root).as_posix()
        st = path.stat()

        if path.is_dir():
            rel = rel.rstrip("/") + "/"

        info = zipfile.ZipInfo(rel)
        info.date_time = time.localtime(st.st_mtime)[:6]
        info.external_attr = (st.st_mode & 0xFFFF) << 16

        if path.is_dir():
            zf.writestr(info, b"", compress_type=zipfile.ZIP_STORED)
        else:
            with path.open("rb") as f:
                zf.writestr(info, f.read(), compress_type=zipfile.ZIP_DEFLATED)
PY
fi

mkdir -p "$ASSETS_DIR"
install -m 0644 "$PATCHED_ZIP" "$DEST_ZIP"

echo "Updated $DEST_ZIP"
echo "SHA-256: $(sha256_file "$DEST_ZIP")"
