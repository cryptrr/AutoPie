#!/usr/bin/env python3
"""Reject debug signing configuration in the Android release build type."""

from __future__ import annotations

import re
import sys
from pathlib import Path


DEBUG_SIGNING = re.compile(
    r'\bsigningConfig\s*=\s*signingConfigs\s*\.\s*getByName\s*\(\s*"debug"\s*\)'
)
BUILD_TYPES = re.compile(r"\bbuildTypes\s*\{")
RELEASE = re.compile(
    r'(?:\brelease\s*|\b(?:getByName|named)\s*\(\s*"release"\s*\)\s*)\{'
)


def mask_comments(source: str) -> str:
    """Replace comments with spaces while preserving strings and line numbers."""
    result = list(source)
    index = 0
    state = "code"

    while index < len(source):
        char = source[index]
        following = source[index + 1] if index + 1 < len(source) else ""

        if state == "code":
            if char == '"':
                state = "string"
            elif char == "'":
                state = "char"
            elif char == "/" and following == "/":
                result[index] = result[index + 1] = " "
                index += 1
                state = "line_comment"
            elif char == "/" and following == "*":
                result[index] = result[index + 1] = " "
                index += 1
                state = "block_comment"
        elif state == "string":
            if char == "\\":
                index += 1
            elif char == '"':
                state = "code"
        elif state == "char":
            if char == "\\":
                index += 1
            elif char == "'":
                state = "code"
        elif state == "line_comment":
            if char == "\n":
                state = "code"
            else:
                result[index] = " "
        elif state == "block_comment":
            if char == "*" and following == "/":
                result[index] = result[index + 1] = " "
                index += 1
                state = "code"
            elif char != "\n":
                result[index] = " "

        index += 1

    return "".join(result)


def closing_brace(source: str, opening_brace: int) -> int | None:
    depth = 0
    index = opening_brace
    state = "code"
    while index < len(source):
        char = source[index]
        if state == "string":
            if char == "\\":
                index += 1
            elif char == '"':
                state = "code"
        elif state == "char":
            if char == "\\":
                index += 1
            elif char == "'":
                state = "code"
        elif char == '"':
            state = "string"
        elif char == "'":
            state = "char"
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return index
        index += 1
    return None


def debug_signing_lines(source: str) -> list[int]:
    masked = mask_comments(source)
    lines: list[int] = []

    for build_match in BUILD_TYPES.finditer(masked):
        build_open = masked.find("{", build_match.start(), build_match.end())
        build_close = closing_brace(masked, build_open)
        if build_close is None:
            continue

        build_body = masked[build_open + 1 : build_close]
        for release_match in RELEASE.finditer(build_body):
            release_open = build_open + 1 + release_match.end() - 1
            release_close = closing_brace(masked, release_open)
            if release_close is None or release_close > build_close:
                continue

            release_body = masked[release_open + 1 : release_close]
            for signing_match in DEBUG_SIGNING.finditer(release_body):
                absolute_position = release_open + 1 + signing_match.start()
                lines.append(source.count("\n", 0, absolute_position) + 1)

    return sorted(set(lines))


def main() -> int:
    project_root = Path(__file__).resolve().parent.parent
    gradle_file = project_root / "app" / "build.gradle.kts"

    if not gradle_file.is_file():
        print(f"pre-push: cannot find {gradle_file}", file=sys.stderr)
        return 1

    lines = debug_signing_lines(gradle_file.read_text(encoding="utf-8"))
    if not lines:
        return 0

    locations = ", ".join(f"line {line}" for line in lines)
    print("Push canceled: the release build uses the debug signing config.", file=sys.stderr)
    print(f"  {gradle_file.relative_to(project_root)}: {locations}", file=sys.stderr)
    print(
        'Remove or comment out `signingConfig = signingConfigs.getByName("debug")` '
        "inside `buildTypes.release` before pushing.",
        file=sys.stderr,
    )
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
