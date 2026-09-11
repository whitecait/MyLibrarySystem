#!/usr/bin/env python3
"""Audit working-tree traceability and G34 revision evidence; never approve a gate."""
import argparse
import collections
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
BASE = '705a0e8fab539daa4d89780b6f89a991202e857c'


def blocks(text):
    return {m[1]: m[2].split('## 变更记录')[0]
            for m in re.finditer(r'^\*\*ID:\*\*\s*`([^`]+)`(.*?)(?=^\*\*ID:\*\*|\Z)',
                                 text, re.M | re.S)}


def audit():
    current, old, origins = {}, {}, {}
    for path in sorted((ROOT / 'docs/01-requirements').rglob('*.md')):
        if '/reviews/' in str(path):
            continue
        entries = blocks(path.read_text())
        current.update(entries)
        origins.update({key: str(path.relative_to(ROOT)) for key in entries})
    paths = subprocess.check_output(['git', 'ls-tree', '-r', '--full-tree', '--name-only', BASE],
                                    cwd=ROOT, text=True).splitlines()
    for path in paths:
        if path.startswith('docs/') and path.endswith('.md'):
            old.update(blocks(subprocess.check_output(['git', 'show', f'{BASE}:{path}'],
                                                      cwd=ROOT, text=True)))
    matrix = (ROOT / 'docs/01-requirements/requirements/MLS-DOC-RA-0014.md').read_text()
    rows = []
    for line in matrix.splitlines():
        if not line.startswith('| MLS-'):
            continue
        cells = [cell.strip() for cell in line.split('|')[1:-1]]
        requirement = cells[2]
        ac = re.findall(r'MLS-AC-\d{4}', cells[4])
        at = re.findall(r'MLS-AT-\d{4}', cells[5])
        notes = [target + ' 正文未显式引用 ' + requirement for target in ac + at
                 if requirement not in current.get(target, '')]
        rows.append({'requirement': requirement, 'source': origins.get(requirement),
                     'delta_from_g2': '新增' if requirement not in old else '存量（正文需结合历史审查）',
                     'ac': ac, 'at': at, 'direct_reference_notes': notes})
    acceptance = (ROOT / 'docs/01-requirements/acceptance/MLS-DOC-AT-0002.md').read_text()
    queries, corpus = [], set()
    for line in acceptance.splitlines():
        if line.startswith('| R-'):
            corpus.add(line.split('|')[1].strip())
        if line.startswith('| Q-'):
            cells = [cell.strip() for cell in line.split('|')[1:-1]]
            query = cells[2].strip('`')
            queries.append({'id': cells[0], 'domain': cells[1], 'query': query,
                            'key_set': re.findall(r'R-\d{2}', cells[3]),
                            'contains_chinese': bool(re.search(r'[\u4e00-\u9fff]', query)),
                            'contains_latin': bool(re.search('[A-Za-z]', query))})
    errors = []
    expected_domains = {'书名完整', '书名片段', '作者', 'ISBN', '分类', '中文短词', '中英混合', '数字', '全半角'}
    counts = collections.Counter(q['domain'] for q in queries)
    if len(queries) != 20 or len({q['id'] for q in queries}) != 20:
        errors.append('查询须为 20 条唯一记录')
    if set(counts) != expected_domains | {'无匹配'}:
        errors.append('输入域集合不完整或出现未登记类别')
    if any(counts[d] != 2 for d in expected_domains | {'无匹配'}):
        errors.append('九类输入域与无匹配须各有两条')
    for query in queries:
        if query['domain'] == '中英混合' and not (query['contains_chinese'] and query['contains_latin']):
            errors.append(query['id'] + ' 不是中英混合输入')
        if '全角数字' in query['query'] or '半角括号' in query['query']:
            errors.append(query['id'] + ' 查询混入说明文字')
        keys = query['key_set']
        if len(keys) != len(set(keys)) or not set(keys) <= corpus or len(keys) > 10:
            errors.append(query['id'] + ' 关键集重复、未定义或超 K')
        if bool(keys) != (query['domain'] != '无匹配'):
            errors.append(query['id'] + ' 匹配/无匹配分母分类错误')
    clauses = []
    for rid, body in current.items():
        for anchor, clause, statement in re.findall(
                r'^\| <a id="([^"]+)"></a>(C\d+) [^|]+ \| (.*?) \|$', body, re.M):
            source = origins[rid]
            needle = Path(source).name + '#' + anchor
            tests = [tid for tid, test_body in current.items()
                     if tid.startswith('MLS-AT-') and needle in test_body]
            if not tests:
                errors.append(anchor + ' 无 AT 独立检查点')
            if needle not in matrix:
                errors.append(anchor + ' 无条款追踪记录')
            clauses.append({'requirement': rid, 'clause': clause, 'anchor': anchor,
                            'source': source, 'statement': statement, 'tests': tests})
    if not clauses or len({c['anchor'] for c in clauses}) != len(clauses):
        errors.append('条款为空或定位重复')
    anchors = []
    for path in sorted((ROOT / 'docs').rglob('*.md')):
        if 'agents' in path.parts:
            continue
        for target in re.findall(r'\]\(([^\s)]+#[^\s)]+-c\d+)\)', path.read_text()):
            filename, anchor = target.split('#', 1)
            destination = path.parent / filename
            valid = destination.is_file() and f'id="{anchor}"' in destination.read_text()
            anchors.append({'source': str(path.relative_to(ROOT)), 'target': target, 'valid': valid})
            if not valid:
                errors.append('失效条款锚点 ' + target)
    return {'comparison_commit': BASE,
            'note': '评审对象为当前工作区；旧提交仅用于差额比较。直接引用提示不自动等于缺覆盖。条款/样本结构检查不代表产品测试、语义批准或责任人签署。',
            'rows': rows, 'queries': queries, 'domain_counts': dict(counts),
            'clauses': clauses, 'clause_links': anchors, 'errors': errors}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-dir', type=Path, required=True,
                        help='Explicit evidence directory; use a new directory for a new review round.')
    args = parser.parse_args()
    result = audit()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / 'coverage.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'matrix_rows': len(result['rows']), 'queries': len(result['queries']),
                      'clauses': len(result['clauses']), 'clause_links': len(result['clause_links']),
                      'errors': result['errors']}, ensure_ascii=False))
    raise SystemExit(bool(result['errors']))


if __name__ == '__main__':
    main()
