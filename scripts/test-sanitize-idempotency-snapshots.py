#!/usr/bin/env python3
"""Tests for structured COPY sanitization and the drill backup boundary."""

from __future__ import annotations

import hashlib
import importlib.util
import inspect
import json
import os
import pathlib
import shutil
import stat
import subprocess
import sys
import tempfile


ROOT = pathlib.Path(__file__).resolve().parents[1]
TOOL = ROOT / "scripts" / "sanitize-idempotency-snapshots.py"
DRILL = ROOT / "scripts" / "drill-backup-restore.sh"


def load_tool():
    spec = importlib.util.spec_from_file_location("sanitize_tool", TOOL)
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def assert_failure(module, payload: bytes, label: str) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        root = pathlib.Path(tmp)
        source = root / "input.sql"
        output = root / "output.sql"
        source.write_bytes(payload)
        proc = subprocess.run(
            [sys.executable, str(TOOL), str(source), str(output)],
            text=True,
            capture_output=True,
            check=False,
        )
        assert proc.returncode != 0, label
        assert not output.exists(), label


def test_copy_structure(module) -> None:
    # Contains three target COPY blocks, three non-NULL snapshots, one NULL,
    # backslash escapes, a quoted identifier containing a comma, and a
    # non-target COPY row that must remain byte exact.
    token_a = b"DYNAMIC-TOKEN-A"
    token_b = b"DYNAMIC-TOKEN-B"
    payload = (
        b"COPY public.other_table (id, value) FROM stdin;\n"
        b"1\tnon-target\n\\.\n"
        b"COPY public.idempotency_key (id, response_snapshot, note) FROM stdin;\n"
        + b"1\t{\"token\":\"" + token_a + b"\",\"escaped\":\"x\\t\\n\\\\\"}\tnote-1\n"
        + b"2\t\\N\tnote-2\n"
        b"\\.\n"
        b"COPY idempotency_key (id, note, response_snapshot) FROM stdin;\n"
        + b"3\tnote-3\t{\"accessToken\":\"" + token_b + b"\"}\n"
        b"\\.\n"
        b'COPY "public"."idempotency_key" ("id,part", "response_snapshot", "weird""name") FROM stdin;\n'
        b"4\tpart-4\t{\"snapshot\":true}\n"
        b"\\.\n"
    )
    sanitized, counts = module.sanitize(payload)
    assert token_a not in sanitized and token_b not in sanitized
    assert b"1\tnon-target\n" in sanitized
    assert sanitized.count(module.PLACEHOLDER) == 3
    assert counts == {
        "targetCopyBlocks": 3,
        "targetRows": 4,
        "responseSnapshotNulls": 1,
        "responseSnapshotFieldsRedacted": 3,
    }


def test_copy_header_variants_fail_closed(module) -> None:
    valid = (
        b"COPY public.idempotency_key (id, response_snapshot) FROM stdin;\n"
        b"1\t{\"safe\":true}\n\\.\n"
    )
    variants = {
        "uppercase FROM STDIN": b"COPY public.idempotency_key (id, response_snapshot) FROM STDIN;\n1\tx\n\\.\n",
        "leading whitespace": b"  COPY public.idempotency_key (id, response_snapshot) FROM stdin;\n1\tx\n\\.\n",
        "lowercase COPY": b"copy public.idempotency_key (id, response_snapshot) FROM stdin;\n1\tx\n\\.\n",
        "missing column list": b"COPY public.idempotency_key FROM stdin;\n1\tx\n\\.\n",
        "malformed target header": b"COPY public.idempotency_key (id, \"response_snapshot) FROM stdin;\n1\tx\n\\.\n",
        "duplicate response_snapshot": b"COPY public.idempotency_key (id, response_snapshot, \"response_snapshot\") FROM stdin;\n1\tx\ty\n\\.\n",
    }
    for label, variant in variants.items():
        assert_failure(module, valid + variant, label)


def test_non_target_copy_is_byte_exact(module) -> None:
    non_target = (
        b"COPY public.other_table (\"column,with,commas\", value) FROM stdin;\n"
        b"1\tkeep\\tthis\\nvalue\n\\.\n"
    )
    target = (
        b"COPY public.idempotency_key (id, response_snapshot) FROM stdin;\n"
        b"1\t{\"token\":\"DYNAMIC-TOKEN-C\"}\n\\.\n"
    )
    sanitized, _ = module.sanitize(non_target + target)
    assert non_target in sanitized
    assert b"DYNAMIC-TOKEN-C" not in sanitized


def test_cli_metadata(module) -> None:
    payload = (
        b"COPY public.idempotency_key (id, request_key, response_snapshot) FROM stdin;\n"
        b"1\tkey-1\t{\"token\":\"DYNAMIC-TOKEN\"}\n\\.\n"
    )
    with tempfile.TemporaryDirectory() as tmp:
        root = pathlib.Path(tmp)
        source, output, metadata_path = (root / name for name in ("in.sql", "out.sql", "meta.json"))
        source.write_bytes(payload)
        proc = subprocess.run(
            [sys.executable, str(TOOL), str(source), str(output), "--metadata", str(metadata_path)],
            text=True,
            capture_output=True,
            check=False,
        )
        assert proc.returncode == 0, proc.stdout + proc.stderr
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
        assert metadata["rawRetained"] is False
        assert metadata["inputBytes"] == len(payload)
        assert metadata["inputSha256"] == hashlib.sha256(payload).hexdigest()
        assert metadata["responseSnapshotFieldsRedacted"] == 1

        # Final publication is exclusive: a second run cannot overwrite the
        # first evidence file, and a metadata publication failure removes the
        # newly-created SQL instead of leaving an orphan.
        second = subprocess.run(
            [sys.executable, str(TOOL), str(source), str(output)],
            text=True,
            capture_output=True,
            check=False,
        )
        assert second.returncode != 0
        metadata_directory = root / "metadata-directory"
        metadata_directory.mkdir()
        orphan_candidate = root / "orphan-candidate.sql"
        failed_metadata = subprocess.run(
            [
                sys.executable,
                str(TOOL),
                str(source),
                str(orphan_candidate),
                "--metadata",
                str(metadata_directory),
            ],
            text=True,
            capture_output=True,
            check=False,
        )
        assert failed_metadata.returncode != 0
        assert not orphan_candidate.exists()


def test_publish_unlink_failure_rolls_back(module) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        root = pathlib.Path(tmp)
        source = root / "in.sql"
        output = root / "out.sql"
        source.write_bytes(
            b"COPY public.idempotency_key (id, response_snapshot) FROM stdin;\n"
            b"1\t{\"safe\":true}\n\\.\n"
        )
        original_unlink = module.os.unlink
        unlink_calls = 0

        def fail_first_unlink(path):
            nonlocal unlink_calls
            unlink_calls += 1
            if unlink_calls == 1:
                raise OSError("injected post-link temporary unlink failure")
            return original_unlink(path)

        module.os.unlink = fail_first_unlink
        try:
            rc = module.main([str(source), str(output)])
        finally:
            module.os.unlink = original_unlink
        assert rc != 0
        assert not output.exists()
        assert list(root.glob(".out.sql.*")) == []


def test_failure_inputs(module) -> None:
    assert_failure(
        module,
        b"COPY public.idempotency_key (id, response_snapshot) FROM stdin;\n1\tshort\textra\n\\.\n",
        "field-count mismatch must fail",
    )
    assert_failure(
        module,
        b"COPY public.idempotency_key (id, response_snapshot) FROM stdin;\n1\tunterminated\n",
        "missing COPY terminator must fail",
    )
    assert_failure(
        module,
        b"COPY public.other_table (id, value) FROM stdin;\n1\tvalue\n\\.\n",
        "missing target COPY block must fail",
    )


def test_drill_boundary() -> None:
    text = DRILL.read_text(encoding="utf-8")
    required = [
        'backup_file="$scratch_dir/raw-backup.sql"',
        'SCRATCH_FILES+=("$backup_file")',
        "sanitize-idempotency-snapshots.py",
        'backupRawRetained',
        'backupRestoreSha256',
        'backupEvidenceSha256',
        'backupEvidenceFile',
        'backupSnapshotFieldsRedacted',
    ]
    for marker in required:
        assert marker in text, marker
    raw_start = text.index('backup_file="$scratch_dir/raw-backup.sql"')
    raw_register = text.index('SCRATCH_FILES+=("$backup_file")')
    raw_cleanup = text.index('for f in "${SCRATCH_FILES[@]:-}"')
    sanitize_call = text.index("sanitize-idempotency-snapshots.py")
    stop_source = text.index('record_step "stop-source" "running"')
    assert raw_cleanup < raw_start < raw_register < sanitize_call < stop_source
    assert 'structured backup evidence sanitization failed' in text
    assert 'backupRestoreFileKind' in text
    assert 'if ! rm -f -- "$f"; then' in text
    assert 'timeline_written=0' in text
    assert 'SCRATCH_FILE_RESULT' in text
    assert 'scan_err="$SCRATCH_FILE_RESULT"' in text
    assert 'cred_file="$SCRATCH_FILE_RESULT"' in text
    assert 'stale_scratch_count=' in text
    assert 'refusing to start:' in text
    assert 'SCRATCH_DIRS+=("$scratch_dir")' in text
    assert 'backup_raw_retained="true"' in text
    assert 'backup_restore_file_kind="temporary-cleanup-failed"' in text
    subprocess.run(["bash", "-n", str(DRILL)], check=True)


def test_cleanup_failure_semantics() -> None:
    """A scratch cleanup failure must rewrite a pass-shaped timeline as fail."""

    with tempfile.TemporaryDirectory() as tmp:
        root = pathlib.Path(tmp)
        fake_bin = root / "bin"
        fake_bin.mkdir()
        # Permit daemon reachability and source database lookup, then fail at
        # pg_dump.  This reaches the registered scratch-file path without
        # starting any real container.
        (fake_bin / "docker").write_text(
            """#!/bin/sh
if [ "$1" = info ]; then exit 0; fi
if [ "$1" = compose ] && [ "$6" = database ] && [ "$5" = -q ]; then
  printf 'fake-db\\n'; exit 0
fi
if [ "$1" = compose ] && [ "$6" = frontend ] && [ "$5" = -q ]; then exit 0; fi
if [ "$1" = ps ]; then exit 0; fi
if [ "$1" = volume ] && [ "$2" = inspect ]; then exit 1; fi
if [ "$1" = volume ]; then exit 0; fi
exit 1
""",
            encoding="utf-8",
        )
        (fake_bin / "rm").write_text("#!/bin/sh\nexit 1\n", encoding="utf-8")
        for path in (fake_bin / "docker", fake_bin / "rm"):
            path.chmod(path.stat().st_mode | stat.S_IXUSR)
        env_file = root / "env"
        env_file.write_text("FRONTEND_PORT=18092\n", encoding="utf-8")
        out = root / "evidence"
        env = os.environ.copy()
        env.update(
            {
                "PATH": f"{fake_bin}:{env['PATH']}",
                "TMPDIR": str(root / "tmp"),
                "DRILL_ACCOUNT": "test-reader",
                "DRILL_PASSWORD": "temporary-only",
            }
        )
        (root / "tmp").mkdir()
        proc = subprocess.run(
            [
                "bash",
                str(DRILL),
                "--source",
                "test-src",
                "--target",
                "test-tgt",
                "--out",
                str(out),
                "--env-file",
                str(env_file),
            ],
            cwd=ROOT,
            env=env,
            text=True,
            capture_output=True,
            check=False,
        )
        assert proc.returncode != 0
        timeline = json.loads((out / "drill-timeline.json").read_text(encoding="utf-8"))
        assert timeline["status"] == "failed"
        assert timeline["verdict"] == "fail"
        assert timeline["backupRawRetained"] is True
        assert timeline["backupRestoreFileKind"] == "temporary-cleanup-failed"
        # The fake rm leaves the private directory; the TemporaryDirectory
        # owner removes only this test-owned tree after assertions.


def test_stale_scratch_refusal() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        root = pathlib.Path(tmp)
        fake_bin = root / "bin"
        fake_bin.mkdir()
        (fake_bin / "docker").write_text("#!/bin/sh\nexit 1\n", encoding="utf-8")
        (fake_bin / "docker").chmod((fake_bin / "docker").stat().st_mode | stat.S_IXUSR)
        scratch_root = root / "scratch"
        scratch_root.mkdir()
        stale = scratch_root / "mls-drill-backup-test-src-test-tgt-stale"
        stale.mkdir()
        marker = stale / "must-remain"
        marker.write_text("owned-by-test\n", encoding="utf-8")
        unrelated = scratch_root / "mls-drill-backup-other-src-other-tgt-stale"
        unrelated.mkdir()
        unrelated_marker = unrelated / "must-remain"
        unrelated_marker.write_text("owned-by-other\n", encoding="utf-8")
        env_file = root / "env"
        env_file.write_text("FRONTEND_PORT=18092\n", encoding="utf-8")
        env = os.environ.copy()
        env.update(
            {
                "PATH": f"{fake_bin}:{env['PATH']}",
                "TMPDIR": str(scratch_root),
                "DRILL_ACCOUNT": "test-reader",
                "DRILL_PASSWORD": "temporary-only",
            }
        )
        out = root / "evidence"
        proc = subprocess.run(
            [
                "bash",
                str(DRILL),
                "--source",
                "test-src",
                "--target",
                "test-tgt",
                "--out",
                str(out),
                "--env-file",
                str(env_file),
            ],
            cwd=ROOT,
            env=env,
            text=True,
            capture_output=True,
            check=False,
        )
        assert proc.returncode != 0
        assert stale.is_dir() and marker.read_text(encoding="utf-8") == "owned-by-test\n"
        assert unrelated.is_dir() and unrelated_marker.read_text(encoding="utf-8") == "owned-by-other\n"


def main() -> int:
    module = load_tool()
    # Every test_* function defined in this file must be registered here: a
    # defined-but-unregistered test would leave its property unverified while
    # the suite still reported PASS.
    registered = (
        test_copy_structure,
        test_copy_header_variants_fail_closed,
        test_non_target_copy_is_byte_exact,
        test_cli_metadata,
        test_publish_unlink_failure_rolls_back,
        test_failure_inputs,
        test_drill_boundary,
        test_cleanup_failure_semantics,
        test_stale_scratch_refusal,
    )
    defined = {
        name
        for name, value in globals().items()
        if name.startswith("test_") and callable(value)
    }
    names = [fn.__name__ for fn in registered]
    assert len(names) == len(set(names)), f"duplicate test registration: {names}"
    unregistered = sorted(defined - set(names))
    assert not unregistered, f"unregistered test function(s): {unregistered}"
    for fn in registered:
        if inspect.signature(fn).parameters:
            fn(module)
        else:
            fn()
    print(f"sanitize-idempotency-snapshots tests: PASS ({len(names)} cases)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
