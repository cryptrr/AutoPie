#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

"$ROOT_DIR/scripts/prepare-termux-app.sh"

if [[ "$#" -eq 0 ]]; then
    set -- :app:assembleDebug
fi

exec "$ROOT_DIR/gradlew" "$@"
