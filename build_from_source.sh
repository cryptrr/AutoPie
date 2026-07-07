#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ASSETS_DIR="${AUTOPIE_ASSETS_DIR:-$ROOT_DIR/app/src/main/assets}"
GENERATOR_REPOSITORY="${TERMUX_GENERATOR_REPOSITORY:-https://github.com/cryptrr/termux-generator.git}"
# This revision contains the native, Docker-free bootstrap builder used here.
GENERATOR_REF="${TERMUX_GENERATOR_REF:-996dd6f77fc93fa7cd8dd9c433016c34f3c8a069}"
DPKG_WRAPPER="${AUTOPIE_DPKG_WRAPPER:-$ROOT_DIR/scripts/bootstrap/dpkg.py}"
TARGET_ROOT_DIR="/data/data/com.autopi"
TARGET_PREFIX="$TARGET_ROOT_DIR/files/usr"

WORK_DIR="$(mktemp -d "$ROOT_DIR/.termux-generator.XXXXXX")"
GENERATOR_DIR="$WORK_DIR/termux-generator"
OUTPUT_DIR="$WORK_DIR/output"
CONVERSION_DIR="$WORK_DIR/bootstrap-aarch64"
PATCHED_DPKG_WRAPPER="$WORK_DIR/dpkg"
DESTINATION="$ASSETS_DIR/bootstrap-aarch64.zip"
TEMP_DESTINATION="$DESTINATION.tmp"

cleanup() {
    rm -f "$TEMP_DESTINATION"
    rm -rf "$WORK_DIR"
}
trap cleanup EXIT

for command in 7z find git install mktemp mv python3 readlink rm sort tar; do
    if ! command -v "$command" >/dev/null 2>&1; then
        echo "Missing required command: $command" >&2
        exit 1
    fi
done

if [[ ! -f "$DPKG_WRAPPER" ]]; then
    echo "Missing dpkg wrapper: $DPKG_WRAPPER" >&2
    exit 1
fi

echo "Preparing patched Termux Android modules"
"$ROOT_DIR/scripts/prepare-termux-app.sh"

echo "Fetching termux-generator revision $GENERATOR_REF"
git init --quiet "$GENERATOR_DIR"
git -C "$GENERATOR_DIR" remote add origin "$GENERATOR_REPOSITORY"
git -C "$GENERATOR_DIR" fetch --quiet --depth 1 origin "$GENERATOR_REF"
git -C "$GENERATOR_DIR" checkout --quiet --detach FETCH_HEAD

echo "Building AutoPie aarch64 bootstrap from source"
TERMUX_NATIVE_OUTPUT_DIR="$OUTPUT_DIR" \
    "$GENERATOR_DIR/build-bootstraps-native.sh" \
        --name com.autopi \
        --architectures aarch64 \
        --add python-pip,openssh,sshpass,binutils

SOURCE_ARTIFACT="$OUTPUT_DIR/bootstrap-aarch64.tar.xz"
if [[ ! -s "$SOURCE_ARTIFACT" ]]; then
    echo "Bootstrap build did not produce $SOURCE_ARTIFACT" >&2
    exit 1
fi

mkdir -p "$ASSETS_DIR"
mkdir -p "$CONVERSION_DIR"
tar -xJf "$SOURCE_ARTIFACT" -C "$CONVERSION_DIR"

echo "Installing dpkg wrapper"
if [[ ! -f "$CONVERSION_DIR/bin/dpkg" ]]; then
    echo "Missing dpkg binary in source-built bootstrap: $CONVERSION_DIR/bin/dpkg" >&2
    exit 1
fi
if [[ -e "$CONVERSION_DIR/bin/dpkg.real" ]]; then
    echo "Unexpected existing dpkg.real in source-built bootstrap: $CONVERSION_DIR/bin/dpkg.real" >&2
    exit 1
fi
mv "$CONVERSION_DIR/bin/dpkg" "$CONVERSION_DIR/bin/dpkg.real"
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
    raise SystemExit(f"dpkg wrapper has no shebang: {source}")

line_ending = "\n" if lines[0].endswith("\n") else ""
lines[0] = f"#!{target_prefix}/bin/env python{line_ending}"
text = "".join(lines)
text = text.replace("/data/data/com.autopi/files/usr", target_prefix)
text = text.replace("/data/data/com.autopi", target_root)

dest.write_text(text, encoding="utf-8")
PY
install -m 0700 "$PATCHED_DPKG_WRAPPER" "$CONVERSION_DIR/bin/dpkg"

(
    cd "$CONVERSION_DIR"
    : > SYMLINKS.txt
    while read -r -d '' link; do
        echo "$(readlink "$link")←${link}" >> SYMLINKS.txt
        rm -f "$link"
    done < <(find . -type l -print0 | LC_ALL=C sort -z)
    7z a bootstrap-aarch64.zip ./* -mfb=258 -mpass=15
    mv bootstrap-aarch64.zip "$TEMP_DESTINATION"
)
if [[ ! -s "$TEMP_DESTINATION" ]]; then
    echo "Failed to convert $SOURCE_ARTIFACT to a bootstrap ZIP" >&2
    exit 1
fi
chmod 0644 "$TEMP_DESTINATION"
mv -f "$TEMP_DESTINATION" "$DESTINATION"

echo "Installed bootstrap asset at $DESTINATION"
