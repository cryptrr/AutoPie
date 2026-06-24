#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

remove_path() {
    local path="$1"

    if [[ -e "$path" || -L "$path" ]]; then
        echo "Removing ${path#$ROOT_DIR/}"
        rm -rf "$path"
    fi
}

remove_glob() {
    local pattern="$1"
    local path

    shopt -s nullglob
    for path in $pattern; do
        remove_path "$path"
    done
    shopt -u nullglob
}

remove_path "$ROOT_DIR/build"
remove_path "$ROOT_DIR/app/build"
remove_path "$ROOT_DIR/termux-app"

remove_glob "$ROOT_DIR/.termux-app.*"
remove_glob "$ROOT_DIR/.termux-bootstrap.*"

# Legacy generated outputs from the old non-Termux build scripts.
remove_path "$ROOT_DIR/busybox"
remove_path "$ROOT_DIR/python3-android"
