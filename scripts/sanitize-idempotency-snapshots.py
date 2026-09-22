#!/usr/bin/env python3
"""Structure-aware sanitization for PostgreSQL COPY backup evidence.

Only the ``response_snapshot`` column in COPY blocks for the exact
``idempotency_key`` relation is replaced.  The decision is made from the COPY
header and field position, never from a token-shaped regular expression.  A
sanitized SQL file is evidence only; the original dump must remain the
temporary restore input and must not be used as the sanitized output.

Usage:
    python3 scripts/sanitize-idempotency-snapshots.py INPUT.sql OUTPUT.sql
        [--metadata METADATA.json]

The command refuses an existing output or metadata file and writes both files
atomically after the complete input has been validated.  On malformed COPY
input it exits non-zero without creating an output file.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import pathlib
import re
import tempfile
from typing import Iterable


SCHEMA = "idempotency-response-snapshot-sanitization/1"
PLACEHOLDER = b'{"redacted":"idempotency_key.response_snapshot"}'
COPY_CANDIDATE_RE = re.compile(rb"^\s*COPY(?:\s|$)", re.IGNORECASE)
IDENTIFIER_RE = re.compile(rb"[A-Za-z_][A-Za-z0-9_$]*")


class SanitizationError(ValueError):
    """Input is not a complete, structurally valid COPY dump."""


def _line_body(line: bytes) -> bytes:
    return line[:-2] if line.endswith(b"\r\n") else line[:-1] if line.endswith(b"\n") else line


def _skip_spaces(value: bytes, pos: int) -> int:
    while pos < len(value) and value[pos] in b" \t":
        pos += 1
    return pos


def _parse_identifier(value: bytes, pos: int) -> tuple[bytes, int]:
    """Parse one PostgreSQL identifier, including escaped quoted names."""

    if pos >= len(value):
        raise SanitizationError("COPY header ended while reading an identifier")
    if value[pos] == ord('"'):
        pos += 1
        chars: list[bytes] = []
        while pos < len(value):
            if value[pos] != ord('"'):
                chars.append(bytes((value[pos],)))
                pos += 1
                continue
            if pos + 1 < len(value) and value[pos + 1] == ord('"'):
                chars.append(b'"')
                pos += 2
                continue
            result = b"".join(chars)
            if not result:
                raise SanitizationError("empty quoted identifier in COPY header")
            return result, pos + 1
        raise SanitizationError("unterminated quoted identifier in COPY header")
    match = IDENTIFIER_RE.match(value, pos)
    if match is None:
        raise SanitizationError("malformed identifier in COPY header")
    # Unquoted PostgreSQL identifiers are folded to lower case.
    return match.group(0).lower(), match.end()


def _parse_qualified_relation(value: bytes, pos: int) -> tuple[list[bytes], int]:
    components: list[bytes] = []
    identifier, pos = _parse_identifier(value, pos)
    components.append(identifier)
    while True:
        pos = _skip_spaces(value, pos)
        if pos >= len(value) or value[pos] != ord('.'):
            return components, pos
        pos = _skip_spaces(value, pos + 1)
        identifier, pos = _parse_identifier(value, pos)
        components.append(identifier)


def _find_closing_paren(value: bytes, pos: int) -> int:
    """Find ')' outside quoted identifiers; quoted ')' is column-name text."""

    if pos >= len(value) or value[pos] != ord('('):
        raise SanitizationError("COPY column list does not start with '('")
    pos += 1
    while pos < len(value):
        if value[pos] == ord('"'):
            _, pos = _parse_identifier(value, pos)
            continue
        if value[pos] == ord(')'):
            return pos
        pos += 1
    raise SanitizationError("unterminated COPY column list")


def _parse_column_list(value: bytes) -> list[bytes]:
    """Parse comma-separated identifiers without splitting quoted commas."""

    pos = 0
    columns: list[bytes] = []
    pos = _skip_spaces(value, pos)
    if pos >= len(value):
        raise SanitizationError("COPY column list is empty")
    while True:
        identifier, pos = _parse_identifier(value, pos)
        columns.append(identifier)
        pos = _skip_spaces(value, pos)
        if pos >= len(value):
            return columns
        if value[pos] != ord(','):
            raise SanitizationError("malformed COPY column list separator")
        pos = _skip_spaces(value, pos + 1)
        if pos >= len(value):
            raise SanitizationError("COPY column list ends after a comma")


def _is_target_relation(components: list[bytes]) -> bool:
    return bool(components) and components[-1] == b"idempotency_key"


def _parse_header(body: bytes) -> tuple[bool, int | None, int]:
    """Return (is_copy_header, response_snapshot_index, column_count).

    COPY-looking lines are fail-closed.  We intentionally accept only the
    canonical pg_dump spelling (``COPY ... FROM stdin;`` with no indentation),
    while recognizing case/whitespace variants so they cannot pass as ordinary
    SQL after a valid block.
    """

    if COPY_CANDIDATE_RE.match(body) is None:
        return False, None, 0
    if body[:1] in b" \t\r\n":
        raise SanitizationError("COPY header has leading whitespace")
    if not body.startswith(b"COPY "):
        raise SanitizationError("COPY header keyword spelling is not canonical")

    pos = len(b"COPY ")
    components, pos = _parse_qualified_relation(body, pos)
    is_target = _is_target_relation(components)
    pos = _skip_spaces(body, pos)
    columns: list[bytes] | None = None
    if pos < len(body) and body[pos] == ord('('):
        close = _find_closing_paren(body, pos)
        columns = _parse_column_list(body[pos + 1 : close])
        pos = close + 1
    pos = _skip_spaces(body, pos)
    if body[pos:] != b"FROM stdin;":
        raise SanitizationError("COPY header must end with canonical FROM stdin;")
    if not is_target:
        return True, None, len(columns or [])
    if columns is None:
        raise SanitizationError("idempotency_key COPY header has no column list")
    matches = [index for index, name in enumerate(columns) if name == b"response_snapshot"]
    if not matches:
        raise SanitizationError(
            "idempotency_key COPY header has no response_snapshot column"
        )
    if len(matches) != 1:
        raise SanitizationError(
            "idempotency_key COPY header has duplicate response_snapshot columns"
        )
    return True, matches[0], len(columns)


def _replace_target_rows(
    body: bytes,
    response_index: int,
    column_count: int,
) -> tuple[bytes, bool]:
    fields = body.split(b"\t")
    if len(fields) != column_count:
        raise SanitizationError(
            "idempotency_key COPY row has "
            f"{len(fields)} fields; expected {column_count}"
        )
    value = fields[response_index]
    if value == b"\\N":
        return body, False
    fields[response_index] = PLACEHOLDER
    return b"\t".join(fields), True


def sanitize(raw: bytes) -> tuple[bytes, dict[str, int | str]]:
    """Sanitize raw SQL and return output bytes plus safe structural counts."""

    output: list[bytes] = []
    in_copy = False
    target_index: int | None = None
    target_column_count = 0
    target_blocks = 0
    target_rows = 0
    target_nulls = 0
    replaced = 0

    for line in raw.splitlines(keepends=True):
        body = _line_body(line)
        if not in_copy:
            is_header, response_index, column_count = _parse_header(body)
            if is_header:
                in_copy = True
                target_index = response_index
                if response_index is not None:
                    target_column_count = column_count
                    target_blocks += 1
            output.append(line)
            continue

        if body == b"\\.":
            output.append(line)
            in_copy = False
            target_index = None
            target_column_count = 0
            continue

        if target_index is None:
            output.append(line)
            continue

        sanitized_body, did_replace = _replace_target_rows(
            body, target_index, target_column_count
        )
        target_rows += 1
        if not did_replace:
            target_nulls += 1
        else:
            replaced += 1
        newline = line[len(body) :]
        output.append(sanitized_body + newline)

    if in_copy:
        raise SanitizationError("COPY input ended before a \\. terminator")
    if target_blocks == 0:
        raise SanitizationError("no idempotency_key COPY block was found")

    result = b"".join(output)
    return result, {
        "targetCopyBlocks": target_blocks,
        "targetRows": target_rows,
        "responseSnapshotNulls": target_nulls,
        "responseSnapshotFieldsRedacted": replaced,
    }


def _write_new(path: pathlib.Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp_name = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    published = False
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        # A hard-link publish is atomic and refuses an existing destination;
        # exists()+replace() would leave a TOCTOU overwrite window.
        try:
            os.link(tmp_name, path)
            published = True
        except FileExistsError as exc:
            raise SanitizationError(f"refusing to overwrite existing output: {path}") from exc
        os.unlink(tmp_name)
        tmp_name = ""
    except Exception:
        # If publication succeeded but unlinking the temporary inode failed,
        # roll back the final hard link before retrying temporary cleanup.  The
        # caller must never receive a failure alongside an orphan SQL output.
        if published:
            try:
                os.unlink(path)
            except OSError:
                pass
        try:
            if tmp_name:
                os.unlink(tmp_name)
        except FileNotFoundError:
            pass
        raise


def _metadata_bytes(metadata: dict[str, object]) -> bytes:
    return (json.dumps(metadata, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=pathlib.Path)
    parser.add_argument("output", type=pathlib.Path)
    parser.add_argument("--metadata", type=pathlib.Path)
    args = parser.parse_args(argv)

    try:
        raw = args.input.read_bytes()
        if args.input.resolve() == args.output.resolve():
            raise SanitizationError("input and output must be different files")
        sanitized, counts = sanitize(raw)
        metadata = {
            "schema": SCHEMA,
            "inputBytes": len(raw),
            "inputSha256": hashlib.sha256(raw).hexdigest(),
            "outputBytes": len(sanitized),
            "outputSha256": hashlib.sha256(sanitized).hexdigest(),
            "rawRetained": False,
            "placeholder": PLACEHOLDER.decode("ascii"),
            **counts,
        }
        _write_new(args.output, sanitized)
        if args.metadata is not None:
            try:
                _write_new(args.metadata, _metadata_bytes(metadata))
            except Exception:
                # A sanitized SQL file without its integrity metadata is not a
                # valid evidence result; do not leave a misleading partial
                # artifact behind.
                args.output.unlink(missing_ok=True)
                raise
    except (OSError, SanitizationError) as exc:
        print(f"sanitization failed: {exc}", flush=True)
        return 1

    print(json.dumps(metadata, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
