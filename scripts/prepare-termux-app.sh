#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TERMUX_REPO="${TERMUX_REPO:-https://github.com/termux/termux-app.git}"
TERMUX_REF="${TERMUX_REF:-master}"
PATCH_DIR="$ROOT_DIR/patches/termux-app"
SOURCE_DIR="$ROOT_DIR/termux-app"
WORK_DIR="$(mktemp -d "$ROOT_DIR/.termux-app.XXXXXX")"
CHECKOUT_DIR="$WORK_DIR/source"

cleanup() {
    rm -rf "$WORK_DIR"
}
trap cleanup EXIT

patches=("$PATCH_DIR"/*.patch)
if [[ ! -e "${patches[0]}" ]]; then
    echo "No Termux patches found in $PATCH_DIR" >&2
    exit 1
fi

echo "Cloning $TERMUX_REPO"
git clone --quiet "$TERMUX_REPO" "$CHECKOUT_DIR"

echo "Checking out Termux ref $TERMUX_REF"
if ! git -C "$CHECKOUT_DIR" checkout --quiet --detach "$TERMUX_REF"; then
    git -C "$CHECKOUT_DIR" fetch --quiet origin "$TERMUX_REF"
    git -C "$CHECKOUT_DIR" checkout --quiet --detach FETCH_HEAD
fi

UPSTREAM_COMMIT="$(git -C "$CHECKOUT_DIR" rev-parse HEAD)"
echo "Applying ${#patches[@]} AutoPie patches to $UPSTREAM_COMMIT"
git -C "$CHECKOUT_DIR" am --quiet --3way "${patches[@]}"

# Only replace the previous generated checkout after cloning and patching succeeds.
rm -rf "$SOURCE_DIR"
mv "$CHECKOUT_DIR" "$SOURCE_DIR"

echo "Prepared patched Termux source at $SOURCE_DIR"
echo "Upstream commit: $UPSTREAM_COMMIT"
