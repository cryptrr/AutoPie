#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ASSETS_DIR="${AUTOPIE_ASSETS_DIR:-$ROOT_DIR/app/src/main/assets}"
GENERATOR_REPOSITORY="${TERMUX_GENERATOR_REPOSITORY:-https://github.com/cryptrr/termux-generator.git}"
# This revision contains the native, Docker-free bootstrap builder used here.
GENERATOR_REF="${TERMUX_GENERATOR_REF:-9889170c7398055b0836e84c7720ebf50fe0af7e}"

WORK_DIR="$(mktemp -d "$ROOT_DIR/.termux-generator.XXXXXX")"
GENERATOR_DIR="$WORK_DIR/termux-generator"
OUTPUT_DIR="$WORK_DIR/output"
DESTINATION="$ASSETS_DIR/bootstrap-aarch64.zip"
TEMP_DESTINATION="$DESTINATION.tmp"

cleanup() {
    rm -f "$TEMP_DESTINATION"
    rm -rf "$WORK_DIR"
}
trap cleanup EXIT

for command in git install mktemp; do
    if ! command -v "$command" >/dev/null 2>&1; then
        echo "Missing required command: $command" >&2
        exit 1
    fi
done

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

SOURCE_ARTIFACT="$OUTPUT_DIR/bootstrap-aarch64.zip"
if [[ ! -s "$SOURCE_ARTIFACT" ]]; then
    echo "Bootstrap build did not produce $SOURCE_ARTIFACT" >&2
    exit 1
fi

mkdir -p "$ASSETS_DIR"
install -m 0644 "$SOURCE_ARTIFACT" "$TEMP_DESTINATION"
mv -f "$TEMP_DESTINATION" "$DESTINATION"

echo "Installed bootstrap asset at $DESTINATION"
