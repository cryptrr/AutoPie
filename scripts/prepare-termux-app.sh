#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TERMUX_REPO="${TERMUX_REPO:-https://github.com/termux/termux-app.git}"
TERMUX_REF="${TERMUX_REF:-master}"
NEW_PACKAGE="${TERMUX_BOOTSTRAP_NEW_PACKAGE:-com.autopi}"
NEW_ROOT_DIR="${TERMUX_BOOTSTRAP_NEW_ROOT_DIR:-}"
PATCH_DIR="$ROOT_DIR/patches/termux-app"
SOURCE_DIR="$ROOT_DIR/termux-app"

usage() {
    cat <<USAGE
Usage: $0 [--new-root-dir PATH]

Options:
  --new-root-dir PATH  Override TermuxConstants app data root. If omitted,
                       the patched Termux source is left unchanged.

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

normalize_root_prefix() {
    local root="$1"

    root="${root%/}"
    if [[ "$root" != /* ]]; then
        echo "Custom root must be an absolute Android data path: $root" >&2
        exit 1
    fi

    if [[ "$root" == */"$NEW_PACKAGE" ]]; then
        root="${root%/"$NEW_PACKAGE"}"
        root="${root%/}"
    fi

    printf '%s/\n' "$root"
}

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

if [[ -n "$NEW_ROOT_DIR" ]]; then
    ROOT_PREFIX="$(normalize_root_prefix "$NEW_ROOT_DIR")"
    TERMUX_CONSTANTS="$CHECKOUT_DIR/termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java"
    if [[ ! -f "$TERMUX_CONSTANTS" ]]; then
        echo "Missing TermuxConstants.java at $TERMUX_CONSTANTS" >&2
        exit 1
    fi

    echo "Setting Termux app data root to $ROOT_PREFIX"
    python3 - "$TERMUX_CONSTANTS" "$ROOT_PREFIX" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
root_prefix = sys.argv[2]
old = 'public static final String TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH = "/data/data/" + TERMUX_PACKAGE_NAME;'
new = f'public static final String TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH = "{root_prefix}" + TERMUX_PACKAGE_NAME;'

text = path.read_text(encoding="utf-8")
if old not in text:
    raise SystemExit(f"Could not find expected TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH assignment in {path}")

path.write_text(text.replace(old, new, 1), encoding="utf-8")
PY
fi

# Only replace the previous generated checkout after cloning and patching succeeds.
rm -rf "$SOURCE_DIR"
mv "$CHECKOUT_DIR" "$SOURCE_DIR"

echo "Prepared patched Termux source at $SOURCE_DIR"
echo "Upstream commit: $UPSTREAM_COMMIT"
