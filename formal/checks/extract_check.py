#!/usr/bin/env python3
"""mermaid 图结构检查与 D-编号覆盖矩阵。

用法:
    python3 extract_check.py DOC [DOC ...]
    退出码: 0 = GAP 合计为 0；1 = 存在 GAP（结构性缺陷、字面覆盖缺口或
            覆盖声明核验失败）。2026-09-12 修补轮阶段 4 起 GAP≠0 即失败。

对每份文档:
  1. 抽取全部 ```mermaid 围栏块，按类型分类（因果图/场景流/时序图/状态图/架构图）；
  2. flowchart 结构检查: 不可达节点、无出边节点(候选出口)、判定节点(菱形)出边数、孤立节点、多根；
  3. sequenceDiagram: 声明未使用参与者、alt/else 分支清单；
  4. stateDiagram: 不可达状态、无出边状态；
  5. D-编号(RS/PK/RT/RV/CL/PAY-D*)覆盖矩阵: 判定表行号在各工件类别中的字面出现；
  6. 覆盖声明核验（阶段 4 新增）: 图后紧跟的『覆盖声明：<ID 列表> → <目标章节>』行
     被解析为交叉引用——目标章节的 mermaid 块必须实际携带全部所引 ID：
       - 声明的 ID 计入声明块所属工件类别的覆盖（与字面出现等效）；
       - 目标不可解析、或目标块未携带任一所引 ID → GAP（空引/错引）；
       - 未解释缺口检查保留：族×工件类别的字面缺口（含未获有效声明的行）仍为 GAP。
     不提供任何全局豁免；覆盖要求不得被整体取消。

输出: stdout 摘要 + verification/output/structural-report.md
本脚本属本地验证资产，不入库（AGENTS.md / 用户 2026-09-11 决定）。
"""

import argparse
import re
import sys
from collections import defaultdict
from pathlib import Path

FENCE_OPEN = re.compile(r"^```\s*(\S+)?\s*$")
HEADING = re.compile(r"^(#{1,6})\s+(.+?)\s*$")
DID = re.compile(r"\b(RS|PK|RT|RV|CL|PAY)-D(\d+)\b")
TABLE_ROW = re.compile(r"^\s*\|")

# 覆盖声明（阶段 4）：『覆盖声明：PK-D1、PK-D5 → §3.2 场景流』或
# 『覆盖声明：RT-D1、RT-D2 → OD-0002 §4』。ID 间分隔符为 、/,/；/空格；
# 箭头只认 → 或 ->（不得用含连字符的字符类，避免吞掉 PK-D 的连字符）。
COVER_DECL = re.compile(r"^覆盖声明[:：]\s*(.+?)\s*(?:→|->)\s*(.+?)\s*$")
COVER_TARGET = re.compile(r"^(?:MLS-DOC-)?(OD-\d+)\s*§(\d+(?:\.\d+)?)|§(\d+(?:\.\d+)?)")

ARROW_SPLIT = re.compile(r"(-\.->|-->)")
EDGE_LABEL = re.compile(r"^\s*\|(.+?)\|\s*(.*)$", re.S)
NODE_CHUNK = re.compile(r"^\s*([A-Za-z0-9_]+)\s*(.*)$", re.S)


# ---------------------------------------------------------------- 解析 ----

def parse_blocks(text):
    """返回 [(标题, mermaid类型, 块文本, 尾随覆盖声明或None)]。

    围栏结束后的第一个非空行若以『覆盖声明』开头，则作为该块的交叉引用声明
    （声明必须紧随图后，避免散落正文的声明被误归属）。"""
    blocks, heading, cur, cur_type = [], "(文件头)", None, None
    in_fence, pending_decl = False, False
    for line in text.splitlines():
        if not in_fence:
            m = HEADING.match(line)
            if m:
                heading = m.group(2)
                pending_decl = False
                continue
            m = FENCE_OPEN.match(line)
            if m and (m.group(1) or "") == "mermaid":
                in_fence, cur, cur_type = True, [], "mermaid"
                continue
            if pending_decl:
                dm = COVER_DECL.match(line.strip())
                if dm:
                    blocks[-1] = blocks[-1][:3] + ((dm.group(1), dm.group(2)),)
                    pending_decl = False
                elif line.strip():
                    pending_decl = False
            continue
        if line.strip().startswith("```"):
            blocks.append((heading, cur_type, "\n".join(cur), None))
            in_fence, pending_decl = False, True
            continue
        cur.append(line)
    return blocks


def classify(heading, first_line):
    if first_line.startswith("stateDiagram"):
        return "状态图"
    if first_line.startswith("sequenceDiagram"):
        return "时序图"
    if "因果" in heading:
        return "因果图"
    return "场景流/视图"


def parse_flowchart(body):
    """返回 (nodes{id:label}, edges[(src,dst,label,dashed)], decisions{set})。"""
    nodes, edges, decisions = {}, [], set()
    for raw in body.splitlines():
        line = raw.strip()
        if not line or line.startswith("%%") or line.startswith(("flowchart", "graph", "subgraph ", "direction ")) or line == "end":
            continue
        parts = ARROW_SPLIT.split(line)
        if len(parts) == 1:
            _consume_def(parts[0], nodes, decisions)
            continue
        prev, dashed_seen = None, False
        for i, chunk in enumerate(parts):
            if i % 2 == 1:  # 箭头本身
                dashed_seen = chunk == "-.->"
                continue
            if i == 0:
                prev = _consume_def(chunk, nodes, decisions)
                continue
            label, node_id = _consume_edge_target(chunk, nodes, decisions)
            if prev and node_id:
                edges.append((prev, node_id, label, dashed_seen))
            if node_id:
                prev = node_id
    return nodes, edges, decisions


def _consume_def(chunk, nodes, decisions):
    m = NODE_CHUNK.match(chunk)
    if not m:
        return None
    nid, rest = m.group(1), m.group(2).strip()
    label, shape = None, "plain"
    for pat, sh in ((r'^\[\("(.*)"\)\]$', "cyl"), (r'^\{"(.*)"\}$', "decision"),
                    (r'^\["(.*)"\]$', "rect"), (r'^\[(.*)\]$', "rect")):
        lm = re.match(pat, rest)
        if lm:
            label, shape = lm.group(1), sh
            break
    nodes[nid] = label if label is not None else nodes.get(nid)
    if shape == "decision":
        decisions.add(nid)
    return nid


def _consume_edge_target(chunk, nodes, decisions):
    label = None
    m = EDGE_LABEL.match(chunk)
    if m:
        label, chunk = m.group(1), m.group(2)
    return label, _consume_def(chunk, nodes, decisions)


def parse_sequence(body):
    """返回 (participants[有序], used{set}, branches[(kind,label)])。"""
    declared, used, branches = [], set(), []
    for raw in body.splitlines():
        line = raw.strip()
        m = re.match(r"^participant\s+(\S+)", line)
        if m:
            declared.append(m.group(1))
            continue
        m = re.match(r"^(Note|note)\s+over\s+[^:]+:(.*)$", line)
        if m:
            continue
        m = re.match(r"^(alt|else|loop|opt)\b[:\s]*(.*)$", line)
        if m:
            branches.append((m.group(1), m.group(2).strip()))
            continue
        m = re.match(r"^([A-Za-z0-9_]+)\s*(-->>|->>|-)|^autonumber", line)
        if m and m.group(1) != "participant":
            used.add(m.group(1))
            tail = re.match(r"^[A-Za-z0-9_]+\s*(-->>|->>|-)\s*([A-Za-z0-9_]+)", line)
            if tail:
                used.add(tail.group(2))
    return declared, used, branches


def parse_state(body):
    """返回 (states{set}, transitions[(src,dst,label)])。"""
    states, transitions = set(), []
    for raw in body.splitlines():
        line = raw.strip()
        if not line or line.startswith(("stateDiagram", "%%", "direction")):
            continue
        m = re.match(r"^(.+?)\s*-->\s*(.+?)(?:\s*:\s*(.*))?$", line)
        if not m:
            continue
        src, dst, label = m.group(1).strip(), m.group(2).strip(), (m.group(3) or "").strip()
        for s in (src, dst):
            if s != "[*]":
                states.add(s)
        transitions.append((src, dst, label))
    return states, transitions


# ---------------------------------------------------------------- 检查 ----

def check_flowchart(name, nodes, edges, decisions):
    findings = []
    succ, indeg = defaultdict(list), defaultdict(int)
    for s, d, _, _ in edges:
        succ[s].append(d)
        indeg[d] += 1
    isolated = [n for n in nodes if n not in succ and n not in indeg]
    if isolated:
        findings.append(("WARN", f"孤立节点(无边): {isolated}"))
    roots = [n for n in nodes if indeg[n] == 0 and (n in succ or n in indeg)]
    if len(roots) > 1:
        findings.append(("INFO", f"多根(入度0)节点 {len(roots)} 个: {roots}"))
    # 从所有根可达性
    seen = set()
    stack = list(roots)
    while stack:
        n = stack.pop()
        if n in seen:
            continue
        seen.add(n)
        stack.extend(succ[n])
    unreachable = sorted(n for n in nodes if n not in seen and (n in succ or n in indeg))
    if unreachable:
        findings.append(("GAP", f"从任何根都不可达: {unreachable}"))
    dead = sorted(n for n in nodes if n in indeg and n not in succ)
    if dead:
        findings.append(("INFO", f"无出边节点(候选出口，需人工确认是否预期): {dead}"))
    for d in sorted(decisions):
        out = len(succ[d])
        if out <= 1:
            findings.append(("GAP", f"判定节点 {d} 出边数={out} (缺分支)"))
        elif out > 2:
            findings.append(("INFO", f"判定节点 {d} 出边数={out} (多路分支，确认完备)"))
    return findings


def check_sequence(name, declared, used, branches):
    findings = []
    unused = [p for p in declared if p not in used]
    if unused:
        findings.append(("GAP", f"声明但未使用的参与者: {unused}"))
    if branches:
        labels = "; ".join(f"{k}:{t}" for k, t in branches if t)
        findings.append(("INFO", f"分支/循环 {len(branches)} 个: {labels}"))
    return findings


def check_state(name, states, transitions):
    findings = []
    succ = defaultdict(list)
    indeg = defaultdict(int)
    for s, d, _ in transitions:
        if s != "[*]":
            succ[s].append(d)
        if d != "[*]":
            indeg[d] += 1
    start_next = [d for s, d, _ in transitions if s == "[*]"]
    seen = set(stack := list(start_next)) if start_next else set()
    while stack:
        n = stack.pop()
        for m in succ[n]:
            if m not in seen:
                seen.add(m)
                stack.append(m)
    unreachable = sorted(s for s in states if s not in seen)
    if unreachable:
        findings.append(("GAP", f"从初始状态不可达: {unreachable}"))
    no_out = sorted(s for s in states if not succ[s])
    if no_out:
        findings.append(("INFO", f"无出边状态(应为终态或接[*]，需确认): {no_out}"))
    return findings


# ------------------------------------------------------------- 覆盖矩阵 ----

FAMILIES = ["RS", "PK", "RT", "RV", "CL", "PAY"]
CLASSES = ["判定表", "因果图", "场景流/视图", "时序图", "状态图", "正文"]
# OD-0003 系判定族在图中应出现的工件类别（族无因果图的不要求因果图）
EXPECTED_CLASSES = {
    "RS": ["因果图", "场景流/视图", "时序图"],
    "PK": ["场景流/视图", "时序图"],
    "RT": ["场景流/视图", "时序图"],
    "RV": ["场景流/视图", "时序图"],
    # CL/PAY 属 OD-0002 画法，不做字面要求
}


def coverage(blocks_by_doc, docs, cross_refs):
    # did -> class -> set((doc, heading))；交叉引用命中以标记 '⟂' 前缀区分字面出现
    matrix = defaultdict(lambda: defaultdict(set))
    for doc, blocks in blocks_by_doc.items():
        short = Path(doc).name
        for heading, btype, body, decl in blocks:
            cls = classify(heading, body.strip().splitlines()[0] if body.strip() else "")
            for m in DID.finditer(body):
                matrix[f"{m.group(1)}-D{m.group(2)}"][cls].add((short, heading))
        # 判定表与正文（表行 vs 其他文本）
        for line in docs[doc].splitlines():
            m = DID.search(line)
            if not m:
                continue
            did = m.group(0)
            if TABLE_ROW.match(line):
                matrix[did]["判定表"].add((short, "(表行)"))
            else:
                matrix[did]["正文"].add((short, ""))
    # 阶段 4：核验通过的覆盖声明计入声明块类别的覆盖（与字面出现等效）
    for (did, short, heading, cls, tgt_desc) in cross_refs:
        matrix[did][cls].add((f"⟂{short}", f"交叉引用→{tgt_desc}"))
    return matrix


def resolve_section_number(heading):
    """『3.2 场景与模块时序』→ '3.2'；『4. 进程视图…』→ '4'；无编号返回 None。"""
    m = re.match(r"^(\d+(?:\.\d+)*)[.\s]", heading.strip())
    return m.group(1) if m else None


def validate_declarations(blocks_by_doc, docs):
    """核验全部覆盖声明。返回 (findings, cross_refs)。

    cross_refs 元素 = (did, 声明文档短名, 声明块标题, 声明块类别, 目标描述)，
    仅收录核验通过的条目；失败条目计入 findings（GAP）。"""
    findings, cross_refs = [], []
    # 预建 (文档短名前缀, 章节号) -> 该章节下所有块体的 ID 集合
    section_ids = {}
    for doc, blocks in blocks_by_doc.items():
        short = Path(doc).name
        doc_key = re.search(r"(OD-\d+|RA-\d+|AT-\d+|DD-\d+|MD-\d+)", short)
        doc_key = doc_key.group(1) if doc_key else short
        for heading, btype, body, decl in blocks:
            sec = resolve_section_number(heading)
            if sec is None:
                continue
            key = (doc_key, sec)
            ids = section_ids.setdefault(key, set())
            ids.update(f"{m.group(1)}-D{m.group(2)}" for m in DID.finditer(body))
            # 声明目标核验只认目标章节图块携带的编号；判定表行不计入目标侧

    for doc, blocks in blocks_by_doc.items():
        short = Path(doc).name
        self_key_m = re.search(r"(OD-\d+|RA-\d+|AT-\d+|DD-\d+|MD-\d+)", short)
        self_key = self_key_m.group(1) if self_key_m else short
        # 加严（阶段 4）：文档中任何『覆盖声明』行都必须已被归属到某个图块；
        # 游离在正文、无法归属的声明本身就是 GAP（写了声明但检查器无法核验）。
        decl_lines_in_text = sum(
            1 for line in docs[doc].splitlines()
            if COVER_DECL.match(line.strip()))
        decl_captured = sum(1 for _, _, _, d in blocks if d)
        if decl_lines_in_text > decl_captured:
            findings.append(("GAP", f"{short}: {decl_lines_in_text - decl_captured} 条覆盖声明"
                                  "未紧随图块（声明必须为图后第一个非空行），无法归属核验"))
        for heading, btype, body, decl in blocks:
            if not decl:
                continue
            ids_str, tgt_str = decl
            cls = classify(heading, body.strip().splitlines()[0] if body.strip() else "")
            ids = sorted({m.group(0) for m in DID.finditer(ids_str)})
            if not ids:
                findings.append(("GAP", f"{short} [{heading}] 覆盖声明未解析出任何 D-编号: 『{ids_str}』"))
                continue
            tm = COVER_TARGET.search(tgt_str)
            if not tm:
                findings.append(("GAP", f"{short} [{heading}] 覆盖声明目标无法解析: 『{tgt_str}』"
                                      "（应为 §N.N 或 文档前缀 §N 形式）"))
                continue
            doc_key, sec = (tm.group(1), tm.group(2)) if tm.group(1) else (self_key, tm.group(3))
            target = section_ids.get((doc_key, sec))
            if target is None:
                findings.append(("GAP", f"{short} [{heading}] 覆盖声明目标章节不存在或无图: "
                                      f"{doc_key} §{sec}"))
                continue
            missing = [i for i in ids if i not in target]
            if missing:
                findings.append(("GAP", f"{short} [{heading}] 覆盖声明空引/错引: "
                                      f"{', '.join(missing)} 未出现在 {doc_key} §{sec} 的图块中"))
                continue
            for i in ids:
                cross_refs.append((i, short, heading, cls, f"{doc_key} §{sec}"))
    return findings, cross_refs


# ---------------------------------------------------------------- 主流程 ----

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("docs", nargs="+")
    ap.add_argument("-o", "--out", default=None)
    args = ap.parse_args()

    docs = {}
    blocks_by_doc = {}
    for d in args.docs:
        p = Path(d)
        text = p.read_text(encoding="utf-8")
        docs[str(p)] = text
        blocks_by_doc[str(p)] = [(h, t, b, dec) for h, t, b, dec in parse_blocks(text)]

    report = ["# mermaid 结构检查与 D-编号覆盖矩阵\n",
              f"对象: {', '.join(Path(d).name for d in args.docs)}\n"]
    gap_total = 0

    for doc, blocks in blocks_by_doc.items():
        short = Path(doc).name
        report.append(f"\n## 文档 {short}\n")
        for i, (heading, btype, body, decl) in enumerate(blocks, 1):
            first = body.strip().splitlines()[0] if body.strip() else ""
            label = f"{short} 块{i} [{heading}] ({classify(heading, first)})"
            report.append(f"\n### {label}\n")
            if first.startswith("flowchart") or first.startswith("graph"):
                nodes, edges, decisions = parse_flowchart(body)
                fs = check_flowchart(label, nodes, edges, decisions)
                report.append(f"节点 {len(nodes)} 个，边 {len(edges)} 条。")
            elif first.startswith("sequenceDiagram"):
                declared, used, branches = parse_sequence(body)
                fs = check_sequence(label, declared, used, branches)
            elif first.startswith("stateDiagram"):
                states, transitions = parse_state(body)
                fs = check_state(label, states, transitions)
            else:
                fs = [("INFO", "未识别的块类型，跳过")]
            for sev, msg in fs:
                report.append(f"- **{sev}**: {msg}")
                if sev == "GAP":
                    gap_total += 1

    # 阶段 4：覆盖声明核验（空引/错引/目标不可解析均为 GAP）
    decl_findings, cross_refs = validate_declarations(blocks_by_doc, docs)
    report.append("\n## 覆盖声明核验（交叉引用）\n")
    if decl_findings:
        for sev, msg in decl_findings:
            report.append(f"- **{sev}**: {msg}")
            if sev == "GAP":
                gap_total += 1
    else:
        report.append(f"共 {len(cross_refs)} 条声明全部核验通过"
                      f"（声明的 ID 均实际出现于目标章节图块）。")

    report.append("\n## D-编号覆盖矩阵（字面出现＋核验通过的交叉引用）\n")
    matrix = coverage(blocks_by_doc, docs, cross_refs)
    report.append("| 判定行 | 判定表 | 因果图 | 场景流/视图 | 时序图 | 状态图 | 正文提及 |")
    report.append("| --- | --- | --- | --- | --- | --- | --- |")
    missing_literal = []
    for fam in FAMILIES:
        ids = sorted(
            (k for k in matrix if k.startswith(fam + "-D")),
            key=lambda k: int(k.split("D")[1]))
        if not ids:
            # 表行都没匹配到说明族不存在，跳过
            continue
        for did in ids:
            cells = []
            for cls in CLASSES:
                hits = matrix[did].get(cls, set())
                src = "; ".join(f"{d} {h}".strip() for d, h in sorted(hits)) or "—"
                cells.append(("✓" if hits else "—") + (f" ({src})" if hits and cls != "判定表" else ""))
            report.append(f"| {did} | " + " | ".join(cells) + " |")
        # OD-0003 约定：判定行应以字面编号出现在图中；按 (族, 工件类别) 汇总缺口。
        # OD-0002 族（CL/PAY）图形用 C/E 条件编号与叙述标签，不适用字面规则，仅提示。
        docs_of_family = {did.split("-")[0] for did in ids
                          if matrix[did].get("判定表")}
        for fam_doc in docs_of_family:
            expected = EXPECTED_CLASSES.get(fam_doc)
            if expected is None:
                continue
            fam_ids = [k for k in ids if matrix[k].get("判定表")]
            for cls in expected:
                hit = [k for k in fam_ids if matrix[k].get(cls)]
                miss = [k for k in fam_ids if k not in hit]
                if miss:
                    missing_literal.append((fam_doc, cls, miss))
    # CL/PAY：零字面引用属 OD-0002 既有画法，输出 INFO 而非 GAP
    for fam in ("CL", "PAY"):
        fam_ids = sorted((k for k in matrix if k.startswith(fam + "-D")
                          and matrix[k].get("判定表")),
                         key=lambda k: int(k.split("D")[1]))
        if fam_ids and not any(matrix[k].get(c) for k in fam_ids
                               for c in ("因果图", "场景流/视图", "时序图", "状态图")):
            report.append(f"- **INFO**: {fam} 族 {len(fam_ids)} 行在图中无字面引用"
                          f"（OD-0002 用 C/E 条件编号与叙述标签），需语义对照，见 findings。")

    report.append("\n### 字面覆盖缺口（OD-0003 系判定行，按族×工件汇总）\n")
    if missing_literal:
        for fam_doc, cls, miss in missing_literal:
            total = sum(1 for k in matrix if k.startswith(fam_doc + "-D")
                        and matrix[k].get("判定表"))
            report.append(f"- **GAP**: {fam_doc} 族 {len(miss)}/{total} 行未在「{cls}」出现: "
                          f"{', '.join(miss)}")
            gap_total += 1
    else:
        report.append("无。")

    report.append(f"\n\nGAP 合计: {gap_total}\n")
    out = Path(args.out) if args.out else Path(__file__).parent / "output" / "structural-report.md"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(report), encoding="utf-8")
    print(f"GAP 合计: {gap_total}；完整报告: {out}")
    # 阶段 4：GAP≠0 视为检查失败（退出码 1），供门禁/脚本判定
    if gap_total:
        sys.exit(1)


if __name__ == "__main__":
    main()
