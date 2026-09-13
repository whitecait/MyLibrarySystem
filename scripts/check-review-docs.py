#!/usr/bin/env python3
"""Read-only structural checks for the current requirement review. Not approval."""
import collections
import hashlib
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
ID = re.compile(r"MLS-(?:OBJ|UC|FR|BR|IR|NFR-[A-Z]+|AC|AT)-\d{4}\b")
tracked = subprocess.check_output(
    ["git", "ls-files", "docs"], cwd=ROOT, text=True
).splitlines()
files = [ROOT / p for p in tracked if p.endswith('.md')]
texts = {p: p.read_text(encoding='utf-8') for p in files}
definitions = collections.defaultdict(list)
for p, text in texts.items():
    for match in re.finditer(r'^\*\*ID:\*\*\s*`([^`]+)`', text, re.M):
        definitions[match[1]].append(str(p.relative_to(ROOT)))
matrix = texts[ROOT / 'docs/01-requirements/requirements/MLS-DOC-RA-0014.md']
rows = [line.split('|')[1:-1] for line in matrix.splitlines()
        if line.startswith('| MLS-')]
used = set().union(*(set(ID.findall('|'.join(row))) for row in rows))
expected = {key for key in definitions if ID.fullmatch(key)}
errors = []
for key, paths in definitions.items():
    if len(paths) != 1:
        errors.append(f'duplicate definition: {key}: {paths}')
for key in sorted(expected - used):
    errors.append(f'missing from matrix rows: {key}')
for key in sorted(used - expected):
    errors.append(f'undefined in matrix rows: {key}')
for row in rows:
    if len(row) != 14:
        errors.append(f'matrix column count: {row}')
        continue
    requirement = row[2].strip()
    if requirement.startswith('MLS-FR-') and not (
        'MLS-AC-' in row[4] and 'MLS-AT-' in row[5]
    ):
        errors.append(f'FR missing AC/AT edge: {requirement}')
    source = ROOT / row[6].strip()
    if not source.is_file() or requirement not in re.findall(
        r'^\*\*ID:\*\*\s*`([^`]+)`', source.read_text(), re.M
    ):
        errors.append(f'wrong source: {requirement}')
    # Design stages now exist. Validate actual references instead of requiring
    # every downstream cell to remain absent forever.
    for cell in row[8:]:
        value = cell.strip()
        if value == 'Not created':
            continue
        refs = re.findall(r'MLS-DOC-(?:OD|DD|MD)-\d{4}', value)
        if not refs:
            errors.append(f'invalid downstream reference: {requirement}: {value}')
        for ref in refs:
            if not any(p.stem == ref for p in files):
                errors.append(f'missing downstream document: {requirement}: {ref}')
broken = []
for p, text in texts.items():
    text = re.sub(r'^```[^\n]*\n.*?^```\s*$', '', text, flags=re.M | re.S)
    for target in re.findall(r'\]\(([^\s)]+)(?:\s+"[^"]*")?\)', text):
        target = target.strip('<>').split('#')[0]
        if not target or re.match(r'[a-zA-Z][a-zA-Z0-9+.-]*:', target):
            continue
        if not (p.parent / target).exists():
            broken.append({'file': str(p.relative_to(ROOT)), 'target': target})
errors.extend(f'broken local link: {item}' for item in broken)
oq = texts[ROOT / 'docs/01-requirements/requirements/MLS-DOC-RA-0007.md']
oq_status = {}
for key, block in re.findall(r'\*\*ID:\*\*\s*`(MLS-OQ-\d+)`(.*?)(?=\*\*ID:\*\*|\Z)', oq, re.S):
    status = re.search(r'\*\*状态：\*\*\s*(.*)', block)
    oq_status[key] = status[1].strip() if status else 'MISSING'
manifest = []
for p, text in texts.items():
    version = re.search(r'\| 文档版本\s*\|\s*`([^`]+)`', text)
    manifest.append({'path': str(p.relative_to(ROOT)),
                     'version': version[1] if version else None,
                     'sha256': hashlib.sha256(p.read_bytes()).hexdigest()})
print(json.dumps({
    'scope': 'tracked docs; local link targets only, not anchors; structural checks, not semantic approval',
    'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
    'counts': dict(sorted(collections.Counter(key.rsplit('-', 1)[0] for key in expected).items())),
    'unique_ids': len(expected), 'matrix_rows': len(rows),
    'oq_status': oq_status, 'errors': errors, 'manifest': manifest,
}, ensure_ascii=False, indent=2))
raise SystemExit(bool(errors))
