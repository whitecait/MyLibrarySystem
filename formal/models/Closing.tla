------------------------------------ MODULE Closing ------------------------------------
(***************************************************************************)
(* OD-0002 §9：闭馆分界、三阶段事务、逐日补结与恢复控制（SC-Q3～SC-Q10）。  *)
(*                                                                         *)
(* 时间抽象为相位（方案约定，不上实时时钟）：                               *)
(*   OPEN      —— T 前，普通业务写入可正常提交                              *)
(*   ISOLATED  —— T 起，普通写入隔离（迟到提交回滚、新请求拒绝）            *)
(*   REOPENED  —— 恢复外部事务写入（2026-10-07 自动恢复与管理员后备开放）           *)
(*                                                                         *)
(* 三阶段整体提交或回滚（SC-Q5）抽象为单步原子动作（CloseBatchSucc 把       *)
(* done[d] 一次性置 TRUE，无中间可观察态），与 SC-Q2/Q9 同风格。            *)
(*                                                                         *)
(* 到期批次集合 Dates 上的日期有先后（D1 < D2）；到期集合只可能是           *)
(* {D1}（正常路径）或 {D1,D2}（补结路径），由 Init 两种配置给出。           *)
(* "补结轮次"判定 backlogRound：T 时刻未完成到期批次 > 1（SC-Q10）。        *)
(*                                                                         *)
(* 逻辑 T：OPEN/DRAINING 可完成已获许可写入；TCutoff 仅在 inFlight=0。     *)
(* DRAINING 不发放新许可；提交或回滚排空；ISOLATED 期间不存在普通提交。      *)
(* 任何提交动作（新请求明确失败的 CL-D3 由动作缺失表达）。                  *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets

CONSTANTS Dates, D1, D2, RetryBudget, NewWriteBound
ASSUME Dates = {D1, D2}
ASSUME D1 # D2

VARIABLES
  phase,        \* OPEN / ISOLATED / REOPENED
  due,          \* [Dates -> BOOLEAN] 本轮到期批次
  done,         \* [Dates -> BOOLEAN] 批次三阶段已提交成功
  curDate,      \* 当前处理批次（失败重试固定原日期，SC-Q7）
  retryLeft,    \* 自动重试额度（SC-Q8 配置化，抽象为常量额度）
  manualReady,  \* 转人工恢复且故障处理完毕，可重试原批次（CL-D7）
  inFlight,     \* T 前在途未提交的普通写入
  committed,    \* 已提交普通写入计数
  committedAtT, \* T 时刻已提交快照（隔离期冻结基准）
  backlogRound, \* 本轮是否补结轮（T 时未完成到期批次 > 1）
  roundSettled, \* 全部到期批次已处理完毕（进入恢复决策）
  awaitingAdmin,\* 已追平待管理员显式开放（SC-Q10）
  adminAuth,    \* 管理员开放命令具备权限且当前仍追平（Init 非确定）
  opened,  \* 受控开放已记录（自动恢复或 CL-D10）
  adminFails,   \* 开放命令失败次数（CL-D11，有界计数）
  newWrites     \* 恢复后的新普通写入（SC-Q6，有界）

vars == <<phase, due, done, curDate, retryLeft, manualReady, inFlight,
          committed, committedAtT, backlogRound, roundSettled,
          awaitingAdmin, adminAuth, opened, adminFails, newWrites>>

PHASES == {"OPEN", "DRAINING", "ISOLATED", "REOPENED"}
AllDone == \A d \in Dates : due[d] => done[d]

-----------------------------------------------------------------------------
Init ==
  /\ phase = "OPEN"
  /\ \/ due = [d \in Dates |-> d = D1]      \* 正常路径：仅一个到期批次
     \/ due = [d \in Dates |-> TRUE]        \* 补结路径：两个到期批次
  /\ done = [d \in Dates |-> FALSE]
  /\ curDate = D1
  /\ retryLeft = RetryBudget
  /\ manualReady = FALSE
  /\ inFlight = 0
  /\ committed = 0
  /\ committedAtT = 0
  /\ backlogRound = FALSE
  /\ roundSettled = FALSE
  /\ awaitingAdmin = FALSE
  /\ adminAuth \in BOOLEAN
  /\ opened = FALSE
  /\ adminFails = 0
  /\ newWrites = 0

-----------------------------------------------------------------------------
(**************************** 动作 ******************************************)
-----------------------------------------------------------------------------
\* 普通写入：开始与提交，仅 OPEN 相位（CL-D1；闭馆窗口外无写）
BeginWrite ==
  /\ phase = "OPEN"
  /\ inFlight + committed < 4
  /\ inFlight' = inFlight + 1
  /\ UNCHANGED <<phase, due, done, curDate, retryLeft, manualReady,
                 committed, committedAtT, backlogRound, roundSettled,
                 awaitingAdmin, adminAuth, opened, adminFails, newWrites>>

CommitWrite ==
  /\ phase \in {"OPEN", "DRAINING"}
  /\ inFlight > 0
  /\ inFlight' = inFlight - 1
  /\ committed' = committed + 1
  /\ UNCHANGED <<phase, due, done, curDate, retryLeft, manualReady,
                 committedAtT, backlogRound, roundSettled,
                 awaitingAdmin, adminAuth, opened, adminFails, newWrites>>

\* 请求隔离后不再发放本轮许可，等待已发许可的事务提交或回滚。
RequestIsolation ==
  /\ phase = "OPEN"
  /\ phase' = "DRAINING"
  /\ UNCHANGED <<due, done, curDate, retryLeft, manualReady, inFlight,
                 committed, committedAtT, backlogRound, roundSettled,
                 awaitingAdmin, adminAuth, opened, adminFails, newWrites>>

AbortWrite ==
  /\ phase \in {"OPEN", "DRAINING"}
  /\ inFlight > 0
  /\ inFlight' = inFlight - 1
  /\ UNCHANGED <<phase, due, done, curDate, retryLeft, manualReady,
                 committed, committedAtT, backlogRound, roundSettled,
                 awaitingAdmin, adminAuth, opened, adminFails, newWrites>>

\* 逻辑 T 是隔离提交；必须等待本轮共享许可事务全数结束，不是墙钟强制回滚。
TCutoff ==
  /\ phase = "DRAINING"
  /\ inFlight = 0
  /\ phase' = "ISOLATED"
  /\ committedAtT' = committed
  /\ inFlight' = 0
  /\ backlogRound' = (Cardinality({d \in Dates : due[d] /\ ~done[d]}) > 1)
  /\ UNCHANGED <<due, done, curDate, retryLeft, manualReady, committed,
                 roundSettled, awaitingAdmin, adminAuth, opened,
                 adminFails, newWrites>>

\* 当日三阶段整体提交成功（SC-Q5 原子；CL-D4/D8 路径）。
\* 重试与人工恢复后仍使用原日期（SC-Q7）：curDate 不因失败改变。
CloseBatchSucc ==
  /\ phase = "ISOLATED"
  /\ due[curDate] /\ ~done[curDate]
  /\ retryLeft > 0 \/ manualReady
  /\ done' = [done EXCEPT ![curDate] = TRUE]
  /\ UNCHANGED <<phase, due, curDate, retryLeft, manualReady, inFlight,
                 committed, committedAtT, backlogRound, roundSettled,
                 awaitingAdmin, adminAuth, opened, adminFails, newWrites>>

\* 当日确定失败：整体回滚（done 不变），消耗一次自动重试额度（CL-D5/D6）。
CloseBatchFail ==
  /\ phase = "ISOLATED"
  /\ due[curDate] /\ ~done[curDate]
  /\ retryLeft > 0
  /\ retryLeft' = retryLeft - 1
  /\ UNCHANGED <<phase, due, done, curDate, manualReady, inFlight,
                 committed, committedAtT, backlogRound, roundSettled,
                 awaitingAdmin, adminAuth, opened, adminFails, newWrites>>

\* 自动重试耗尽，转人工恢复（CL-D7）；人工恢复不跳过结算、不改判完成。
ManualRepair ==
  /\ phase = "ISOLATED"
  /\ retryLeft = 0
  /\ ~manualReady
  /\ \E d \in Dates : due[d] /\ ~done[d]
  /\ manualReady' = TRUE
  /\ UNCHANGED <<phase, due, done, curDate, retryLeft, inFlight,
                 committed, committedAtT, backlogRound, roundSettled,
                 awaitingAdmin, adminAuth, opened, adminFails, newWrites>>

\* 当前批次成功且仍有到期未完成批次 → 推进到下一业务日（CL-D8，按日期先后）。
AdvanceDate ==
  /\ phase = "ISOLATED"
  /\ curDate = D1
  /\ done[D1] /\ due[D2] /\ ~done[D2]
  /\ curDate' = D2
  /\ UNCHANGED <<phase, due, done, retryLeft, manualReady, inFlight,
                 committed, committedAtT, backlogRound, roundSettled,
                 awaitingAdmin, adminAuth, opened, adminFails, newWrites>>

\* 全部到期批次处理完毕 → 恢复决策（CL-D4 vs CL-D9）：
\*   正常轮和补结轮全部成功后自动开放；
\*   自动开放失败时保持隔离，管理员开放作为后备（2026-10-07 批准）。
SettleRound ==
  /\ phase = "ISOLATED"
  /\ ~roundSettled
  /\ AllDone
  /\ roundSettled' = TRUE
  /\ \/ /\ phase' = "REOPENED"
        /\ opened' = TRUE
        /\ awaitingAdmin' = FALSE
     \/ /\ phase' = phase
        /\ opened' = opened
        /\ awaitingAdmin' = TRUE
  /\ UNCHANGED <<due, done, curDate, retryLeft, manualReady, inFlight,
                 committed, committedAtT, backlogRound, adminAuth,
                 adminFails, newWrites>>

\* 管理员开放命令：有权且当前仍已追平 → 记录开放并到达 R（CL-D10）。
AdminOpen ==
  /\ awaitingAdmin
  /\ adminAuth
  /\ AllDone
  /\ phase' = "REOPENED"
  /\ opened' = TRUE
  /\ awaitingAdmin' = FALSE     \* 开放完成，"待开放"控制态随之结束
  /\ UNCHANGED <<due, done, curDate, retryLeft, manualReady, inFlight,
                 committed, committedAtT, backlogRound, roundSettled,
                 adminAuth, adminFails, newWrites>>

\* 管理员命令无权或当前未追平 → 命令失败，保持隔离（CL-D11）。
AdminOpenFail ==
  /\ awaitingAdmin
  /\ ~adminAuth
  /\ adminFails < 3
  /\ adminFails' = adminFails + 1
  /\ UNCHANGED <<phase, due, done, curDate, retryLeft, manualReady, inFlight,
                 committed, committedAtT, backlogRound, roundSettled,
                 awaitingAdmin, adminAuth, opened, newWrites>>

\* 恢复后的新普通写入按最新事实处理（SC-Q6），计数有界。
NewWrite ==
  /\ phase = "REOPENED"
  /\ newWrites < NewWriteBound
  /\ newWrites' = newWrites + 1
  /\ committed' = committed + 1
  /\ UNCHANGED <<phase, due, done, curDate, retryLeft, manualReady, inFlight,
                 committedAtT, backlogRound, roundSettled,
                 awaitingAdmin, adminAuth, opened, adminFails>>

Next ==
  \/ BeginWrite \/ CommitWrite \/ AbortWrite \/ RequestIsolation \/ TCutoff
  \/ CloseBatchSucc \/ CloseBatchFail \/ ManualRepair \/ AdvanceDate
  \/ SettleRound \/ AdminOpen \/ AdminOpenFail \/ NewWrite

-----------------------------------------------------------------------------
(************************** 不变量（safety） ********************************)
-----------------------------------------------------------------------------
TypeOK ==
  /\ phase \in PHASES
  /\ due \in [Dates -> BOOLEAN] /\ done \in [Dates -> BOOLEAN]
  /\ curDate \in Dates
  /\ retryLeft \in Nat /\ retryLeft <= RetryBudget
  /\ manualReady \in BOOLEAN
  /\ inFlight \in Nat /\ committed \in Nat /\ committedAtT \in Nat
  /\ backlogRound \in BOOLEAN /\ roundSettled \in BOOLEAN
  /\ awaitingAdmin \in BOOLEAN /\ adminAuth \in BOOLEAN
  /\ opened \in BOOLEAN /\ adminFails \in Nat
  /\ newWrites \in Nat

\* 失败日期之后的日期不得越序先结算（§9.3 场景流约束）。
InvOrder == done[D2] => done[D1]

\* SC-Q3 / CL-D2：隔离期间已提交事实冻结——T 后任何相位都不产生
\* 归入分界前事实的普通写入提交。（快照 committedAtT 仅由 TCutoff 写一次，
\* 无任何动作可改写，由动作清单构造保证。）
InvFreeze ==
  (phase = "ISOLATED") => (committed = committedAtT)

InvNoOldPermit == (phase \in {"ISOLATED", "REOPENED"}) => inFlight = 0

\* SC-Q6 / SC-Q10（v0.6 修正）：REOPENED 只能来自
\*   正常轮和补结轮均须追平且受控开放（自动恢复或管理员后备）。
InvReopenGated ==
  (phase = "REOPENED") =>
    (AllDone /\ opened)

\* 追平待开放态的合法性：正常轮或补结轮、全部追平、尚未恢复。
InvAwaitGated ==
  awaitingAdmin =>
    (phase = "ISOLATED" /\ roundSettled /\ AllDone /\ ~opened)

\* CL-D10：开放记录只出现在已追平的 REOPENED 状态。
InvOpenCoherent ==
  opened => (phase = "REOPENED" /\ roundSettled /\ AllDone)

-----------------------------------------------------------------------------
(****************** 可达性探针（期望 TLC 报 violated） **********************)
(* 以下每条"不变量"取反写出：TLC 报告 violated 并给出轨迹，即为该出口的     *)
(* 可达性证明（对应 CL-D6/D7/D10/D11 与正常路径 SC-Q6 的图可达性）。        *)
-----------------------------------------------------------------------------
ProbeNotAutoReopen == ~((phase = "REOPENED") /\ ~adminAuth)

ProbeNotNormalAwaiting == ~(awaitingAdmin /\ ~backlogRound)
ProbeNotBacklogAwaiting == ~(awaitingAdmin /\ backlogRound)

ProbeNotDraining  == phase # "DRAINING"
ProbeNotReopen    == phase # "REOPENED"        \* 期望违反：恢复可达
ProbeNotAwaiting  == ~awaitingAdmin            \* 期望违反：追平待开放可达（SC-Q10）
ProbeNotManual    == ~manualReady              \* 期望违反：转人工可达（CL-D7）
ProbeNotAdminFail == adminFails = 0            \* 期望违反：开放命令失败可达（CL-D11）
ProbeNotRetry     == retryLeft = RetryBudget   \* 期望违反：自动重试被消耗（CL-D6）

=============================================================================
