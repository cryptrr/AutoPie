#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT_DIR/.gradle}"

SKIP_PREPARE=false
SKIP_BOOTSTRAP=false
GRADLE_ARGS=()

for arg in "$@"; do
    case "$arg" in
        --skip-prepare)
            SKIP_PREPARE=true
            ;;
        --skip-bootstrap)
            SKIP_BOOTSTRAP=true
            ;;
        *)
            GRADLE_ARGS+=("$arg")
            ;;
    esac
done

if [[ "$SKIP_PREPARE" != true ]]; then
    "$ROOT_DIR/scripts/prepare-termux-app.sh"
fi

if [[ "$SKIP_BOOTSTRAP" != true ]]; then
    "$ROOT_DIR/scripts/prepare-termux-bootstrap.sh"
fi

if [[ "${#GRADLE_ARGS[@]}" -eq 0 ]]; then
    set -- :app:assembleDebug
else
    set -- "${GRADLE_ARGS[@]}"
fi

exec "$ROOT_DIR/gradlew" "$@"
