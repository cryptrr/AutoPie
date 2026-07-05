#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ASSETS_DIR="${AUTOPIE_ASSETS_DIR:-$ROOT_DIR/app/src/main/assets}"
GENERATOR_REPOSITORY="${TERMUX_GENERATOR_REPOSITORY:-https://github.com/cryptrr/termux-generator.git}"
# This revision contains the native, Docker-free bootstrap builder used here.
GENERATOR_REF="${TERMUX_GENERATOR_REF:-aaf17ce7b363609e19954ef2cff34f1443de9e5a}"

WORK_DIR="$(mktemp -d "$ROOT_DIR/.termux-generator.XXXXXX")"
GENERATOR_DIR="$WORK_DIR/termux-generator"
OUTPUT_DIR="$WORK_DIR/output"
CONVERSION_DIR="$WORK_DIR/bootstrap-aarch64"
DESTINATION="$ASSETS_DIR/bootstrap-aarch64.zip"
TEMP_DESTINATION="$DESTINATION.tmp"

cleanup() {
    rm -f "$TEMP_DESTINATION"
    rm -rf "$WORK_DIR"
}
trap cleanup EXIT

for command in 7z find git mktemp mv readlink rm tar; do
    if ! command -v "$command" >/dev/null 2>&1; then
        echo "Missing required command: $command" >&2
        exit 1
    fi
done

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
(
    cd "$CONVERSION_DIR"
    : > SYMLINKS.txt
    while read -r -d '' link; do
        echo "$(readlink "$link")←${link}" >> SYMLINKS.txt
        rm -f "$link"
    done < <(find . -type l -print0)
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
