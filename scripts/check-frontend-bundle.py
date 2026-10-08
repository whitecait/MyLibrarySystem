#!/usr/bin/env python3
"""Validate the JavaScript entry served by the Compose frontend."""
import argparse
import re
from pathlib import Path


def check_bundle(path: Path) -> None:
    content = path.read_text(encoding="utf-8")
    mounts_app = re.search(r"""\.mount\(\s*(["'])#app\1\s*\)""", content)
    if content.lstrip().startswith("<") or not mounts_app:
        raise ValueError("Frontend entry bundle must contain a JavaScript mount for #app.")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=Path)
    args = parser.parse_args()
    try:
        check_bundle(args.bundle)
    except (OSError, ValueError) as error:
        parser.error(str(error))


if __name__ == "__main__":
    main()
