#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT_DIR/.gradle}"

SKIP_PREPARE=false
SKIP_BOOTSTRAP=false
NEW_ROOT_DIR="${TERMUX_BOOTSTRAP_NEW_ROOT_DIR:-}"
DEFAULT_PACKAGE="com.autopi"
NEW_PACKAGE="$DEFAULT_PACKAGE"
CUSTOM_PACKAGE_SET=false
GRADLE_ARGS=()

if [[ -n "${TERMUX_BOOTSTRAP_NEW_PACKAGE:-}" ]]; then
    NEW_PACKAGE="$TERMUX_BOOTSTRAP_NEW_PACKAGE"
    CUSTOM_PACKAGE_SET=true
fi

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
          --new-package)
                      if [[ "$#" -lt 2 || -z "$2" ]]; then
                          echo "Error: $1 requires a package name." >&2
                          exit 1
                      fi
                      NEW_PACKAGE="$2"
                      CUSTOM_PACKAGE_SET=true
                      shift 2
                      ;;
        *)
            GRADLE_ARGS+=("$1")
            shift
            ;;
    esac
done

if [[ "$CUSTOM_PACKAGE_SET" == true ]]; then
    if [[ ! "$NEW_PACKAGE" =~ ^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$ ]]; then
        echo "Error: Invalid Android package name: $NEW_PACKAGE" >&2
        exit 1
    fi

    export TERMUX_BOOTSTRAP_NEW_PACKAGE="$NEW_PACKAGE"

    if [[ "$NEW_PACKAGE" != "$DEFAULT_PACKAGE" ]]; then
        echo "Replacing $DEFAULT_PACKAGE with $NEW_PACKAGE in the app project"
        while IFS= read -r -d '' file; do
            if sed --version >/dev/null 2>&1; then
                sed -i "s/com\\.autopi/$NEW_PACKAGE/g" "$file"
            else
                sed -i '' "s/com\\.autopi/$NEW_PACKAGE/g" "$file"
            fi
        done < <(rg -l -0 --fixed-strings "$DEFAULT_PACKAGE" "$ROOT_DIR/app")
    fi
fi

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
