#!/usr/bin/env python3
"""Apply a Reciv release version to build metadata for the current build."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


def apply_version(version: str) -> None:
    build_config = Path("buildSrc/src/main/kotlin/BuildConfig.kt")
    unciv_game = Path("core/src/com/unciv/UncivGame.kt")

    build_text = build_config.read_text(encoding="utf-8")
    build_text = re.sub(r'appVersion = ".*"', f'appVersion = "{version}"', build_text)
    build_config.write_text(build_text, encoding="utf-8")

    game_text = unciv_game.read_text(encoding="utf-8")
    game_text = re.sub(r'Version\(".*", (\d+)\)', f'Version("{version}", \\1)', game_text, count=1)
    unciv_game.write_text(game_text, encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("version")
    args = parser.parse_args()
    if not re.match(r"^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$", args.version):
        parser.error("version must be SemVer, for example 0.1.0")
    apply_version(args.version)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
