#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT_DIR/.gradle}"

SKIP_PREPARE=false
SKIP_BOOTSTRAP=false
NEW_ROOT_DIR="${TERMUX_BOOTSTRAP_NEW_ROOT_DIR:-}"
GRADLE_ARGS=()

while [[ "$#" -gt 0 ]]; do
    case "$1" in
        --skip-prepare)
            SKIP_PREPARE=true
            shift
            ;;
        --skip-bootstrap)
            SKIP_BOOTSTRAP=true
            shift
            ;;
        --new-root-dir|--new-root)
            if [[ "$#" -lt 2 ]]; then
                echo "Error: $1 requires a path argument." >&2
                exit 1
            fi
            NEW_ROOT_DIR="$2"
            shift 2
            ;;
        *)
            GRADLE_ARGS+=("$1")
            shift
            ;;
    esac
done

ROOT_ARGS=()
if [[ -n "$NEW_ROOT_DIR" ]]; then
    ROOT_ARGS=(--new-root-dir "$NEW_ROOT_DIR")
fi

if [[ "$SKIP_PREPARE" != true ]]; then
    "$ROOT_DIR/scripts/prepare-termux-app.sh" "${ROOT_ARGS[@]}"
fi

if [[ "$SKIP_BOOTSTRAP" != true ]]; then
    "$ROOT_DIR/scripts/prepare-termux-bootstrap.sh" "${ROOT_ARGS[@]}"
fi

if [[ "${#GRADLE_ARGS[@]}" -eq 0 ]]; then
    set -- :app:assembleDebug
else
    set -- "${GRADLE_ARGS[@]}"
fi

exec "$ROOT_DIR/gradlew" "$@"
