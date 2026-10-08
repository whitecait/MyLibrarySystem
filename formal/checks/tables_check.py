#!/usr/bin/env python3
"""判定表完备性/互斥性枚举检查（SAT 思路的纯枚举实现）。

方法: 每张判定表做布尔分解（分解假设显式登记为 A1~A10），在全局约束下
枚举全布尔空间:
  1. 覆盖性 —— 每个有效赋值在其处理阶段恰命中 ≥1 行，缺行即候选遗漏;
  2. 互斥性 —— 同阶段两行条件区域重叠且结果不同 → 冲突
               （表声明行优先级时，重叠降级为 INFO，由优先级消解）;
  3. 声明核验 —— 文档声明的不可达组合/派生条件逐条验证。

条件公式只编码文档已确认文字与用户已确认决定（CONFIRMED，2026-09-11）；
叙述式条件（PK/RT/RV）的分解假设在 ASSUMPTIONS 中列出。并发唯一性
（PAY-D9）是状态不变量而非行判定，交由 TLA+ 模型检查（models/OrderOccupancy.tla）。

用法: python3 tables_check.py   输出 stdout + output/tables-report.md
"""

import argparse
import itertools
from pathlib import Path


# ------------------------------------------------------------ 引擎 ----

def run_table(name, var_order, constraints, stages, priority=False):
    """stages: [(阶段名, 行列表)]；行 = (行号, 条件fn, 结果)，按判定优先级排序。
    priority=True 表示该表已声明行优先级（用户确认 C2-a 后）：首命中行为判定行，
    多命中且结果不同降级为 INFO（由优先级消解）；False 时仍记 GAP。
    2026-09-12 修补轮阶段 2：同时统计每个有效赋值在各阶段的『首命中行』分布
    （首命中统计），零首命中行单独提示（候选死行）。
    返回 (findings, 有效赋值数)。"""
    findings, names = [], [v for v, _ in var_order]
    total, uncovered, conflicts, overload = 0, [], [], set()
    prio_resolved = set()
    firsthit = {}  # (阶段名, 行号) -> 首命中赋值计数

    for values in itertools.product([True, False], repeat=len(var_order)):
        env = dict(zip(names, values))
        if not all(fn(env) for _, fn in constraints):
            continue
        total += 1
        for stage_name, rows in stages:
            hits = [r for r in rows if r[1](env)]
            if not hits:
                uncovered.append((stage_name, env))
            else:
                key = (stage_name, hits[0][0])
                firsthit[key] = firsthit.get(key, 0) + 1
                if len(hits) > 1:
                    # 首命中为判定行；其余命中按表是否声明优先级分类
                    if hits[0][2] != hits[1][2]:
                        if priority:
                            prio_resolved.add((stage_name, hits[0][0], hits[1][0]))
                        else:
                            conflicts.append((stage_name, hits[0][0], hits[-1][0], fmt_env(env, names)))
                    else:
                        overload.add((stage_name, hits[0][0], hits[1][0]))

    findings.append(("INFO", f"{name}: 约束下有效赋值 {total} / 2^{len(var_order)}"))
    if total == 0:
        findings.append(("GAP", f"{name}: 约束过强，有效空间为空——分解或约束有误"))
        return findings, total
    for stage_name, rows in stages:
        cnt = sum(1 for s, _ in uncovered if s == stage_name)
        if cnt:
            samples = [fmt_env(e, names) for s, e in uncovered if s == stage_name][:3]
            findings.append(("GAP", f"{name}·{stage_name}: {cnt} 个有效赋值未命中任何行"
                                    f"（候选遗漏），例: {'; '.join(samples)}"))
        else:
            findings.append(("PASS", f"{name}·{stage_name}: 覆盖完备（{len(rows)} 行）"))
        # 首命中统计（2026-09-12 阶段 2）：各行为首命中判定行的赋值数；
        # 零首命中行提示为候选死行（可能是恒被更高优先级行遮蔽或条件不可满足）。
        dist = ", ".join(f"{r[0]}: {firsthit.get((stage_name, r[0]), 0)}"
                         for r in rows)
        findings.append(("INFO", f"{name}·{stage_name}: 首命中分布 [{dist}]"))
        dead = [r[0] for r in rows if firsthit.get((stage_name, r[0]), 0) == 0]
        if dead:
            findings.append(("GAP", f"{name}·{stage_name}: 零首命中行（候选死行）: {', '.join(dead)}"))
    seen = set()
    for stage_name, a, b, sample in conflicts:
        if (stage_name, a, b) in seen:
            continue
        seen.add((stage_name, a, b))
        findings.append(("GAP", f"{name}·{stage_name}: {a} 与 {b} 条件区域重叠且结果不同，"
                                f"表未声明优先级，例: {sample}"))
    for stage_name, a, b in sorted(prio_resolved):
        findings.append(("INFO", f"{name}·{stage_name}: {a} 与 {b} 条件区域重叠，"
                                 f"已由声明优先级消解（首命中 {a} 判定）"))
    if overload:
        findings.append(("INFO", f"{name}: 同果重叠行（无实质冲突）: "
                                 f"{'; '.join(f'{s}:{a}~{b}' for s, a, b in sorted(overload)[:6])}"))
    return findings, total


def fmt_env(env, names):
    return ",".join(f"{v}={'T' if env[v] else 'F'}" for v in names if v in env)


# ------------------------------------------------------- 六张判定表 ----

def RS():
    """OD-0003 §2.2 预约提交。V 为派生条件（假设 A5），11 个独立变量。"""
    V = lambda e: e["I"] and not e["F"] and not e["O"] and e["B"] and e["L"] and not e["D"]
    var_order = [("G", "提交受隔离/截止未过"), ("I", "权限/读者/输入有效"),
                 ("F", "未缴罚款"), ("O", "逾期未还"), ("B", "书目存在可预约副本"),
                 ("L", "预约额度允许"), ("D", "重复预约事务"), ("U", "批准规则无法唯一判定"),
                 ("A", "存在即用可借阅副本"), ("Q", "候补队列为空"), ("C", "已确认候补说明")]
    constraints = [("A⊆B（正文：B否而A真不符合副本定义）", lambda e: (not e["A"]) or e["B"])]
    rows = [
        ("RS-D1", lambda e: e["G"], "闭馆隔离/截止失败，不建预约"),
        ("RS-D2", lambda e: (not e["G"]) and (not V(e)), "明确规则拒绝"),
        ("RS-D3", lambda e: (not e["G"]) and V(e) and e["U"], "入口人工处理中"),
        ("RS-D4", lambda e: (not e["G"]) and V(e) and (not e["U"]) and e["A"] and e["Q"], "已确认"),
        ("RS-D5", lambda e: (not e["G"]) and V(e) and (not e["U"]) and (not (e["A"] and e["Q"])) and (not e["C"]), "等待候补确认"),
        ("RS-D6", lambda e: (not e["G"]) and V(e) and (not e["U"]) and (not (e["A"] and e["Q"])) and e["C"], "候补入队"),
    ]
    findings, _ = run_table("RS 预约提交（OD-0003 §2.2，行优先级已在正文声明）",
                            var_order, constraints, [("主判定", rows)])
    bad = []
    for values in itertools.product([True, False], repeat=len(var_order)):
        env = dict(zip([v for v, _ in var_order], values))
        if not all(fn(env) for _, fn in constraints) or env["G"] or V(env):
            continue
        hit = next(r[0] for r in rows if r[1](env))
        if hit != "RS-D2":
            bad.append(hit)
    if bad:
        findings.append(("GAP", f"RS: {len(bad)} 个 ¬G∧¬V 赋值未落入 RS-D2"))
    else:
        findings.append(("PASS", "RS: 正文声明『V为否时 U/A/Q/C 不改变明确拒绝』经枚举成立"))
    return [findings]


def PK():
    """OD-0003 §3.1。按生命周期拆两子空间（假设 A1/A2/A8）。"""
    f1, _ = run_table(
        "PK·到台自动借出判定（§3.1 D1~D4）",
        [("BAD0", "隔离/无权限/无效对象"), ("REF", "明确拒绝(当日欠费/逾期未还)"),
         ("XD", "跨日无豁免 或 规则未命中"), ("OKD", "日期符合/有豁免 且 规则通过")],
        [("XD 与 OKD 互补（假设 A2）", lambda e: e["XD"] != e["OKD"])],
        [("自动借出", [
            ("PK-D1", lambda e: e["BAD0"], "失败，不冒充办理"),
            ("PK-D2", lambda e: (not e["BAD0"]) and e["REF"], "预约未借出结束，解绑待清点"),
            ("PK-D3", lambda e: (not e["BAD0"]) and (not e["REF"]) and e["XD"], "人工处理中，保留绑定"),
            ("PK-D4", lambda e: (not e["BAD0"]) and (not e["REF"]) and e["OKD"], "借阅中"),
        ])])
    f2, _ = run_table(
        "PK·人工事务处置（§3.1 D5~D8，D8 为 C1-a 确认后正式补充行）",
        [("ENTRY", "入口人工事务(否则借出人工)"), ("GO", "馆员放行"), ("DENY", "馆员拒绝"),
         ("AVAIL", "有即用副本且队列为空")],
        [("放行/拒绝互斥且必有处置（假设 A8）", lambda e: e["GO"] != e["DENY"])],
        [("人工处置", [
            ("PK-D5", lambda e: (not e["ENTRY"]) and e["GO"], "执行借出，跨日记豁免"),
            ("PK-D6", lambda e: e["ENTRY"] and e["GO"] and e["AVAIL"], "确认，现场继续借出"),
            # C1-a（2026-09-11 用户确认）：放行守卫不满足时放行不执行，事务保持
            # 人工处理中（保留事务、无绑定、计入预约额度，同 RS-D3 留存口径）。
            # 2026-09-12 修补轮阶段 3 分配正式行号 PK-D8（占位编号 PK-D6b 废止），
            # 已同步落文 OD-0003 §3.1。
            ("PK-D8", lambda e: e["ENTRY"] and e["GO"] and (not e["AVAIL"]),
             "放行不执行，事务保持人工处理中"),
            ("PK-D7", lambda e: e["DENY"], "未借出，按物理状态释放"),
        ])])
    return [f1, f2]


def RT():
    """OD-0003 §4 归还。两阶段。"""
    f1, _ = run_table(
        "RT·归还判定（§4 D1~D4）",
        [("BAD", "无权/对象无效/重复归还/提交受隔离"), ("CONF", "业务事实冲突无法自动判定"),
         ("INTACT", "副本完好")],
        [],
        [("归还判定", [
            ("RT-D1", lambda e: e["BAD"], "不重复归还/计罚，失败审计"),
            ("RT-D2", lambda e: (not e["BAD"]) and e["CONF"], "转人工"),
            ("RT-D3", lambda e: (not e["BAD"]) and (not e["CONF"]) and e["INTACT"], "已归还，待清点"),
            ("RT-D4", lambda e: (not e["BAD"]) and (not e["CONF"]) and (not e["INTACT"]), "归还异常，维护中"),
        ])])
    f2, _ = run_table(
        "RT·提交阶段（RT-D5，D3/D4 的后续阶段）",
        [("WF", "业务写入任一失败")],
        [],
        [("提交", [
            ("RT-D5", lambda e: e["WF"], "整体回滚，仅失败审计"),
            ("RT-提交成功", lambda e: not e["WF"], "整体提交"),
        ])])
    return [f1, f2]


def RV():
    """OD-0003 §5 高风险复核。"""
    f, _ = run_table(
        "RV 复核（§5 D1~D5）",
        [("OK0", "复核权限且申请待处理且依据有效"), ("CON", "目标相关事实仍一致"),
         ("APP", "批准"), ("EX", "业务执行成功")],
        [],
        [("复核判定", [
            ("RV-D1", lambda e: not e["OK0"], "失败审计"),
            ("RV-D2", lambda e: e["OK0"] and not e["CON"], "不直接生效，重新发起"),
            ("RV-D3", lambda e: e["OK0"] and e["CON"] and not e["APP"], "拒绝，目标不变"),
            ("RV-D4", lambda e: e["OK0"] and e["CON"] and e["APP"] and e["EX"], "批准+目标变更整体提交"),
            ("RV-D5", lambda e: e["OK0"] and e["CON"] and e["APP"] and not e["EX"], "整体回滚，仅审计"),
        ])])
    return [f]


def CL():
    """OD-0002 §9.2 闭馆。三主体五子表（假设 A3/A9；GOV-0008 当前适用层）。"""
    f1, _ = run_table(
        "CL·普通业务请求（§9.2 D1~D3）",
        [("BEFORE", "T前实际提交成功"), ("PENDING", "计划触发后已有许可事务尚未结束"), ("ISO", "T后新请求遇隔离")],
        [("当前观察阶段三分（假设 A9）", lambda e: sum([e["BEFORE"], e["PENDING"], e["ISO"]]) == 1)],
        [("当前观察", [
            ("CL-D1", lambda e: e["BEFORE"], "纳入闭馆前事实"),
            ("CL-D2", lambda e: e["PENDING"], "隔离提交等待已有许可事务结束；不保证计划钟点截止"),
            ("CL-D3", lambda e: e["ISO"], "明确失败，保持隔离"),
        ])])
    f2, _ = run_table(
        "CL·补结批次推进（§9.2 D8~D10，当前批次成功且未发命令，正常轮与补结轮均适用）",
        [("MORE", "还有到期闭馆批次"), ("WAITING", "全部批次已追平"),
         ("BACKLOG", "是否补结轮"), ("AUTO_OK", "自动开放成功")],
        [("推进二分（假设 A3）", lambda e: e["MORE"] != e["WAITING"])],
        [("批次推进", [
            ("CL-D8", lambda e: e["MORE"], "继续下一业务日"),
            ("CL-D9", lambda e: e["WAITING"] and e["AUTO_OK"], "自动开放普通写入"),
            ("CL-AUTO-FAIL", lambda e: e["WAITING"] and not e["AUTO_OK"], "保持隔离，管理员后备处置"),
        ])])
    f3, _ = run_table(
        "CL·失败后处置（§9.2 D5~D7，D5 为 D6/D7 前置）",
        [("RETRY", "自动重试未耗尽")],
        [],
        [("失败处置", [
            ("CL-D6", lambda e: e["RETRY"], "按原日期自动重试"),
            ("CL-D7", lambda e: not e["RETRY"], "转人工恢复"),
        ])])
    f4, _ = run_table(
        "CL·管理员开放命令（§9.2 D10/D11）",
        [("ADM_OK", "有权限且当前仍追平")],
        [],
        [("命令结果", [
            ("CL-D10", lambda e: e["ADM_OK"], "记录开放，恢复"),
            ("CL-D11", lambda e: not e["ADM_OK"], "命令失败，保持隔离"),
        ])])
    f5, _ = run_table(
        "CL·事件投递（§9.2 D12）",
        [("DF", "已提交事件投递失败或重复")],
        [("事件驱动（假设 A7）：仅投递失败/重复事件时判定", lambda e: e["DF"])],
        [("投递", [
            ("CL-D12", lambda e: e["DF"], "幂等重试，不改变开放状态"),
        ])])
    return [f1, f2, f3, f4, f5]


def PAY():
    """OD-0002 §14.3(D1~D6,D15)+§15.1(D7~D14)。两主体（假设 A4/A6/A7/A10）。
    2026-09-11 用户确认 C2-a/C3-a 后的口径；2026-09-12 修补轮阶段 3 分配正式行号：
    - 行优先级 D5 > D6 > D3 > D2 > D15（幂等返回最优先 → 已支付不可被关闭撤销〔SC-Q15〕
      → 未支付到期发起关闭 → 超时仅日志）；D1 与其余行自然互斥；D15 为终态吸收兜底。
    - PAY-D3 编码收紧为 CR∧EXP∧VAL：『符合关闭条件』不含已终结订单，
      已终结订单收到的迟到关闭请求改由 D15 吸收（此前编码 CR∧EXP∧¬PD
      把已关闭订单的重复关闭请求误判给 D3，属编码修正，非新发现）。
    - PAY-D15（原占位编号 PAY-D2b，2026-09-12 废止）：超时/关闭请求类事件
      到达已终结订单（已支付或已关闭）→ 仅记录日志，不改变既有事实。
    - PAY-D15 在生命周期表内的排序位置：D2 之后（兜底行），与文档 §14.3 声明一致。"""
    f1, _ = run_table(
        "PAY·订单生命周期（§14.3 D1/D5/D6/D3/D2/D15 + C2-a 优先级 + C3-a 终态吸收）",
        [("CR", "建单提交成功"), ("TO", "支付尝试超时"), ("VAL", "订单有效且未终结"),
         ("EXP", "订单自身到期且满足关闭条件"), ("DUP", "重复成功通知/重复处理"),
         ("PD", "已完成支付"), ("CLOSED", "未支付已关闭")],
        [("订单存在时占用/已关闭/已支付恰一态，不存在时均为否（A6 编码勘误）",
          lambda e: sum([e["VAL"], e["PD"], e["CLOSED"]]) == int(e["CR"])),
         ("存在需判定事件（假设 A7：在途无事件不在表范围）",
          lambda e: (not e["CR"]) or e["TO"] or e["EXP"] or e["DUP"])],
        [("订单处置（优先级 D5>D6>D3>D2>D15）", [
            ("PAY-D1", lambda e: not e["CR"], "无订单，失败日志"),
            ("PAY-D5", lambda e: e["CR"] and e["DUP"], "返回既有结果，不重复结算"),
            ("PAY-D6", lambda e: e["CR"] and e["EXP"] and e["PD"], "仅记录关闭拒绝，不自动退款；退款需独立授权"),
            ("PAY-D3", lambda e: e["CR"] and e["EXP"] and e["VAL"], "发起关闭事务"),
            ("PAY-D2", lambda e: e["CR"] and e["TO"] and e["VAL"], "保留原订单，仅超时日志"),
            ("PAY-D15", lambda e: e["CR"] and (not e["VAL"]) and (not e["DUP"])
                                      and (e["TO"] or e["EXP"]),
             "仅日志，不改变既有事实（终态吸收）"),
        ])], priority=True)
    f1b, _ = run_table(
        "PAY·关闭事务结果（PAY-D4，D3 的后续阶段）",
        [("CS", "关闭事务提交成功")],
        [],
        [("关闭结果", [
            ("PAY-D4", lambda e: not e["CS"], "失败日志，不伪装关闭成功"),
            ("PAY-D3b", lambda e: e["CS"], "关闭事实生效"),
        ])])
    f2, _ = run_table(
        "PAY·建单与独占（§15.1 D7/D8/D14）",
        [("OCC", "同一罚款已有未释放订单"), ("STILL", "仍符合可结算条件"),
         ("CLS", "关闭事务成功提交"), ("PD2", "支付成功且罚款已结清"),
         ("HAD_ORDER", "已存在过本次判定关联订单")],
        [("已结清则不可结算", lambda e: (not e["PD2"]) or (not e["STILL"])),
         ("占用先要求订单存在（A6 编码勘误）", lambda e: e["OCC"] == (e["HAD_ORDER"] and (not e["CLS"]) and (not e["PD2"]))),
         ("关闭成功必须有订单", lambda e: not e["CLS"] or e["HAD_ORDER"]),
         # 2026-09-12 阶段 2 首命中统计发现：原约束 OCC∨STILL 把『已结清罚款仍
         # 发来建单请求』（PD2=T, OCC=F, STILL=F）排除出可判定空间，使 PAY-D14
         # 成为零首命中死行；该请求是真实可判定事件（模型动作 CreateDeniedSettled
         # 与 SC-Q15"不得再为已结清罚款建单"），属编码修正而非新发现。
         ("建单请求可判定空间（OCC∨STILL∨PD2，其余为非本子表范围的不可结算请求，不能据此宣称全部建单输入已覆盖）",
          lambda e: e["OCC"] or e["STILL"] or e["PD2"])],
        [("建单守卫", [
            ("PAY-D7", lambda e: e["OCC"], "不建第二订单"),
            ("PAY-D8", lambda e: (not e["OCC"]) and e["STILL"], "可尝试新建"),
            ("PAY-D14", lambda e: e["PD2"], "不得为已结清罚款建单"),
        ])])
    return [f1, f1b, f2]


ASSUMPTIONS = [
    "A1: PK 表拆为『到台自动借出判定』(D1~D4) 与『人工事务处置』(D5~D7) 两个子空间；"
    "PK-D3 产生的借出人工事务由 D5/D7 处置，入口人工事务来源为 RS-D3。",
    "A2: PK 自动判定中 XD(跨日无豁免或规则未命中) 与 OKD(通过) 互补——『日期符合但规则未通过』计入 XD。",
    "A3: CL 表按主体拆为 普通请求(D1~D3)/补结批次推进(D8~D10)/失败后处置(D5~D7)/管理员命令(D10~D11)/事件投递(D12)；"
    "D5 是 D6/D7 的前置结果行，非同阶段竞争。",
    "A4: PAY 生命周期表将 D4(关闭结果) 移入独立后续阶段；PAY-D10(建单失败不留占用) 视为 D1 的占用侧注记不单列。",
    "A5: RS 的 V 为派生条件(=I∧¬F∧¬O∧B∧L∧¬D)，枚举在 11 个独立条件上进行。",
    "A6（2026-09-12 编码勘误）: 订单存在时 VAL/PD/CLOSED 恰一态；占用要求 HAD_ORDER 且未关闭未结清。"
    "旧 PD↔¬VAL 与无订单存在条件的 OCC 公式错误，不能由用户采纳业务假设推出。",
    "A7: PAY 生命周期与 CL-D12 为事件驱动表，『在途/正常且无任何事件』的状态不要求判定行。",
    "A8: PK 人工处置假设已发生处置动作且放行/拒绝互斥（GO≠DENY）；『入口人工+放行+无即用副本』的"
    "业务结果表内未定义——此为本检查要暴露的候选遗漏，不是假设。",
    "A9: CL-D1~D3 仅按当前观察阶段分类；D2 等待后可进入 D1，不宣称同一请求生命周期三种互斥结局。恢复后的请求不属该子表。",
    "A10: PAY-D9(并发至多一单) 是状态不变量而非行判定，由 TLA+ 模型检查。",
]

# 2026-09-11 用户 lingzz 逐条确认（"均可以采纳建议值"）。占位编号 PK-D6b/PAY-D2b
# 已于 2026-09-12 修补轮阶段 3 分配正式行号 PK-D8 / PAY-D15 并落文（OD-0003 §3.1、
# OD-0002 §14.3），占位编号同步废止。
CONFIRMED = [
    "C1-a(F1): 入口人工事务放行守卫不满足（无即用副本或队列非空）时，放行不执行，"
    "事务保持人工处理中（保留事务、无绑定、计入预约额度，同 RS-D3 留存口径）；"
    "馆员可待守卫满足后再放行或改走拒绝（PK-D7/TX-16）。依据 RA-0008 §13 TX-15a 守卫语义。",
    "C2-a(F2): PAY 生命周期表补行优先级声明 D5 > D6 > D3 > D2（幂等返回最优先 → "
    "已支付不可被关闭撤销〔SC-Q15〕→ 未支付到期发起关闭 → 超时仅日志不影响其他判定）。",
    "C3-a(F3): 超时/关闭请求类事件到达已终结订单（已支付或已关闭）→ 仅记录日志，"
    "不改变既有事实（终态吸收，与 §16 状态机终态语义和 PAY-D5 幂等口径一致）。",
    "C4-a(F4): §14.2 因果图补 C6'：关闭事务提交失败 → 只留失败日志。（文档修订项）",
    "C5-a(F5): §14.4 场景流 V 节点补『订单已终结』出边，指向不执行关闭、"
    "现行适用层只记录关闭拒绝，不启动已移出的退款。（文档修订项）",
    "C6-a(F6): §2.4 预约时序图补 RS-D1 分支（不创建预约事务、记失败审计）。（文档修订项）",
    "C7-b(F7): §5 复核时序图以图注声明『拒绝与守卫失败路径见场景流 TD』，不新增分支。（文档修订项）",
    "C8-b(F8): OD-0002 §11 覆盖索引登记『人工处置路径以场景流为准』，不新增时序图。（文档修订项）",
    "C9-a(F9): OD-0003 各图统一携带 D-编号；OD-0002 维持 C/E 编号并在 §11 说明映射。（文档修订项）",
    "C10-a(F10): §2.1 因果图 A/Q/C 升为独立节点并给 M/OK 补『非G』输入边，或注明复合标签展开规则。（文档修订项）",
    "C11-a(F11): §9.2 CL 表按主体分组并注明『组间无交互、组内按优先级/阶段判定』。（文档修订项）",
    "A1~A10: 全部符合用户理解（2026-09-11）。",
]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', default=str(Path(__file__).parent / 'output' / 'tables-report.md'))
    args = parser.parse_args()
    lines = ["# 判定表完备性/互斥性枚举检查\n",
             "## 用户确认决定（2026-09-11，编码依据）\n"]
    lines += [f"- **{c.split(':')[0]}**: {c.split(':', 1)[1]}" for c in CONFIRMED]
    lines += ["\n## 分解假设（结论依赖以下假设，2026-09-11 用户确认全部符合）\n"]
    lines += [f"- **A{i+1}**: {a}" for i, a in enumerate(ASSUMPTIONS)]
    lines.append("")
    gap = 0
    for family in (RS, PK, RT, RV, CL, PAY):
        for findings in family():
            for sev, msg in findings:
                lines.append(f"- **{sev}**: {msg}")
                if sev == "GAP":
                    gap += 1
        lines.append("")
    lines.append(f"GAP 合计: {gap}")
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(lines), encoding="utf-8")
    print("\n".join(lines))
    raise SystemExit(bool(gap))


if __name__ == "__main__":
    main()
