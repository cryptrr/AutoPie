#!/usr/bin/env bash

set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_FILE="${AUTOPIE_FDROID_BUILD_LOG:-/tmp/build_from_source.log}"
LOG_LINES="${AUTOPIE_FDROID_BUILD_LOG_LINES:-900}"
status=0

bash "$ROOT_DIR/build_from_source.sh" > "$LOG_FILE" 2>&1 || status=$?

echo "==== build_from_source.sh last $LOG_LINES lines ===="
tail -n "$LOG_LINES" "$LOG_FILE" || true
echo "==== end build_from_source.sh log ===="

if ((status != 0)); then
    echo "build_from_source.sh failed with exit code $status" >&2
fi
exit "$status"
