--------------------------------- MODULE OrderOccupancy ---------------------------------
(***************************************************************************)
(* OD-0002 §14/§15/§16：同一罚款的订单独占、生命周期与请求事件处置           *)
(* （SC-Q13 / SC-Q14 / SC-Q15，用户确认 C2-a / C3-a）。                     *)
(*                                                                         *)
(* 2026-09-12 v2（修补轮阶段 2）：按用户十项清单第 2 项重构支付验证空间——   *)
(*   1. 显式区分四个订单状态：NONE(未建单) / OCCUPIED(占用中) /             *)
(*      CLOSED(已关闭) / PAID(已支付)。SC-Q15：关闭事务提交成功才进入       *)
(*      CLOSED 并释放占用（关闭与释放同生共死，原子单步，PAY-D11）；        *)
(*      PAID 结算罚款并结束占用。释放是 CLOSED/PAID 的派生事实，不设        *)
(*      独立『已释放』状态。                                                *)
(*   2. 请求事件显式类型化：建单请求 / 支付尝试超时 / 关闭请求(取消或到期)  *)
(*      / 支付成功结果 / 重复成功通知。每个动作 = 一个『请求事件 × 状态』   *)
(*      组合的判定结果，与 §14.3 判定表行一一对应；tags 单调记录已出现的    *)
(*      行结果，供逐行可达性探针（ProbeNotRow*）与 TLC 覆盖统计取证。       *)
(*   3. 未编码组合显式登记：支付成功结果到达已关闭订单只记录冲突，不修改订单 *)
(*      终态或罚款；对应现行 SettlementModuleImpl.applyCallback。                *)
(*                                                                         *)
(* 只编码文档已确认规则；数据库事务原子性抽象为单步动作（与 SC-Q2/Q9 的     *)
(* 整体提交或回滚同风格）。独占按罚款定义，本模型取单笔罚款抽象——多罚款    *)
(* 是逐罚款复制的平凡扩展，不改变独占结构。                                 *)
(*                                                                         *)
(* 行结果 tags 与判定表行的对应（动作 → 行）：                              *)
(*   CreateSucceed         → PAY-D8 （可尝试新建 → 建单成功；首次建单样本）  *)
(*   CreateFail            → PAY-D1 / PAY-D10（建单失败无订单、不留占用）   *)
(*   CreateDeniedSettled   → PAY-D14（已结清罚款不得建单）                  *)
(*   CreateDeniedOccupied  → PAY-D7 （已有未释放订单，不建第二单）          *)
(*   TimeoutLogOccupied    → PAY-D2 / PAY-D13（超时仅日志，不释放占用）     *)
(*   TimeoutAbsorbPaid     → PAY-D15（超时到达已支付订单：终态吸收）        *)
(*   TimeoutAbsorbClosed   → PAY-D15（超时到达已关闭订单：终态吸收）        *)
(*   CloseCommitOccupied   → PAY-D3 / PAY-D12（取消/到期经关闭事务生效）    *)
(*   CloseFailOccupied     → PAY-D4 / PAY-D13（关闭失败仅日志，不释放）     *)
(*   CloseReqPaid          → PAY-D6 （已支付不可被关闭撤销，不自动退款）      *)
(*   CloseReqAbsorbClosed  → PAY-D15（关闭请求到达已关闭订单：终态吸收）    *)
(*   PaySuccess            → 支付成功正常结算（PAY-D5 幂等口径的前提）     *)
(*   DupNoticePaid         → PAY-D5 （重复通知返回既有结果，不重复结算）    *)
(*   PAY-D9  = 状态不变量 InvOneUnreleased（并发至多一单）。                *)
(*   PAY-D11 = CloseCommit 原子性（结构保证）+ InvCoherent。                *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets, TLC

CONSTANTS OrderId, NoOrder

VARIABLES state, occ, settleable, settled, tags
vars == <<state, occ, settleable, settled, tags>>

States == {"NONE", "OCCUPIED", "CLOSED", "PAID"}

\* 行结果标记：单调集合，只增不减；键与判定表行的映射见模块头注释。
RowTags == {"D1", "D2", "D3", "D4", "D5", "D6", "D7", "D8", "D8p",
            "D14", "D15tp", "D15tc", "D15cc", "ClosedPayConflict"}
  \* D8p = 支付成功正常结算路径（PAY-D5 幂等口径的前提）；
  \* D15tp = 超时@已支付，D15tc = 超时@已关闭，D15cc = 关闭请求@已关闭，
  \* 三者均属 PAY-D15 终态吸收行，分键是为给『已关闭终态吸收』独立样本。

-----------------------------------------------------------------------------
(**************************** 初始条件 **************************************)
-----------------------------------------------------------------------------
Init ==
  /\ state = [o \in OrderId |-> "NONE"]
  /\ occ = NoOrder
  /\ settleable = TRUE          \* 罚款当前应结算（可建单）
  /\ settled = FALSE
  /\ tags = {}

-----------------------------------------------------------------------------
(**************************** 动作 ******************************************)
\* 每个动作命名 = 请求事件类型 + 所处状态 + 判定结果；guard 只引用已确认规则。)
-----------------------------------------------------------------------------
OccupiedOrders == {o \in OrderId : state[o] = "OCCUPIED"}

\* ---- 建单请求（读者对本人应结算罚款发起模拟支付，§13.2 唯一正向来源）----

\* PAY-D8：无未释放订单且仍可结算 → 建单提交成功，进入占用中（首次建单样本）。
CreateSucceed(o) ==
  /\ state[o] = "NONE"
  /\ occ = NoOrder
  /\ settleable
  /\ state' = [state EXCEPT ![o] = "OCCUPIED"]
  /\ occ' = o
  /\ tags' = tags \cup {"D8"}
  /\ UNCHANGED <<settleable, settled>>

\* PAY-D1/PAY-D10：建单事务提交失败 → 不留下订单、不留占用，仅失败日志。
CreateFail(o) ==
  /\ state[o] = "NONE"
  /\ occ = NoOrder
  /\ settleable
  /\ tags' = tags \cup {"D1"}
  /\ UNCHANGED <<state, occ, settleable, settled>>

\* PAY-D14：罚款已结清 → 不得再为已结清罚款建单（请求被拒，仅日志）。
CreateDeniedSettled(o) ==
  /\ state[o] = "NONE"
  /\ occ = NoOrder
  /\ ~settleable
  /\ tags' = tags \cup {"D14"}
  /\ UNCHANGED <<state, occ, settleable, settled>>

\* PAY-D7：同一罚款已有未释放订单 → 不建第二订单（SC-Q14 独占）。
CreateDeniedOccupied(o) ==
  /\ occ # NoOrder
  /\ tags' = tags \cup {"D7"}
  /\ UNCHANGED <<state, occ, settleable, settled>>

\* ---- 支付尝试超时事件（SC-Q11/Q13：不产生提交事实）----

\* PAY-D2/PAY-D13：超时到达占用中订单 → 保留原订单供再次支付，仅日志。
TimeoutLogOccupied(o) ==
  /\ state[o] = "OCCUPIED"
  /\ tags' = tags \cup {"D2"}
  /\ UNCHANGED <<state, occ, settleable, settled>>

\* PAY-D15：超时到达已支付订单 → 终态吸收，仅日志（C3-a）。
TimeoutAbsorbPaid(o) ==
  /\ state[o] = "PAID"
  /\ tags' = tags \cup {"D15tp"}
  /\ UNCHANGED <<state, occ, settleable, settled>>

\* PAY-D15：超时到达已关闭订单 → 终态吸收，仅日志（C3-a）。
TimeoutAbsorbClosed(o) ==
  /\ state[o] = "CLOSED"
  /\ tags' = tags \cup {"D15tc"}
  /\ UNCHANGED <<state, occ, settleable, settled>>

\* ---- 关闭请求事件（读者取消 / 订单到期 / 馆员发起，SC-Q12/Q15）----

\* PAY-D3/PAY-D12：关闭请求到达占用中未支付订单且符合关闭条件
\* → 关闭事务提交成功，关闭事实与占用释放共同生效（原子单步）。
CloseCommitOccupied(o) ==
  /\ state[o] = "OCCUPIED"
  /\ settleable            \* 未支付（已支付订单 settleable 已转 FALSE）
  /\ state' = [state EXCEPT ![o] = "CLOSED"]
  /\ occ' = NoOrder
  /\ tags' = tags \cup {"D3"}
  /\ UNCHANGED <<settleable, settled>>

\* PAY-D4/PAY-D13：关闭事务提交失败 → 不产生关闭事实，仍占用，仅日志。
CloseFailOccupied(o) ==
  /\ state[o] = "OCCUPIED"
  /\ settleable
  /\ tags' = tags \cup {"D4"}
  /\ UNCHANGED <<state, occ, settleable, settled>>

\* PAY-D6：关闭请求到达已支付订单 → 不执行关闭（已支付不可被关闭撤销，
\* SC-Q15），只记录关闭拒绝，不调用已移出的退款流程。
CloseReqPaid(o) ==
  /\ state[o] = "PAID"
  /\ tags' = tags \cup {"D6"}
  /\ UNCHANGED <<state, occ, settleable, settled>>

\* PAY-D15：关闭请求到达已关闭订单 → 终态吸收，仅日志（C3-a）。
CloseReqAbsorbClosed(o) ==
  /\ state[o] = "CLOSED"
  /\ tags' = tags \cup {"D15cc"}
  /\ UNCHANGED <<state, occ, settleable, settled>>

\* ---- 支付成功结果 / 重复成功通知 ----

\* 支付成功结果到达占用中订单 → 幂等结算罚款，进入已支付，占用结束。
\* （结算本身是 PAY-D5 幂等口径的前提路径；SC-Q15：支付成功结清罚款。）
PaySuccess(o) ==
  /\ state[o] = "OCCUPIED"
  /\ state' = [state EXCEPT ![o] = "PAID"]
  /\ settleable' = FALSE
  /\ settled' = TRUE
  /\ occ' = NoOrder
  /\ tags' = tags \cup {"D8p"}

\* PAY-D5：重复成功通知到达已支付订单 → 返回既有结果，不重复结算。
DupNoticePaid(o) ==
  /\ state[o] = "PAID"
  /\ tags' = tags \cup {"D5"}
  /\ UNCHANGED <<state, occ, settleable, settled>>

\* 已关闭订单的迟到成功回调：只记录冲突，不结清罚款或恢复占用。
PaySuccessClosed(o) ==
  /\ state[o] = "CLOSED"
  /\ tags' = tags \cup {"ClosedPayConflict"}
  /\ UNCHANGED <<state, occ, settleable, settled>>

Next ==
  \/ \E o \in OrderId :
        CreateSucceed(o) \/ CreateFail(o) \/ CreateDeniedSettled(o)
        \/ CreateDeniedOccupied(o)
        \/ TimeoutLogOccupied(o) \/ TimeoutAbsorbPaid(o) \/ TimeoutAbsorbClosed(o)
        \/ CloseCommitOccupied(o) \/ CloseFailOccupied(o) \/ CloseReqPaid(o)
        \/ CloseReqAbsorbClosed(o)
        \/ PaySuccess(o) \/ DupNoticePaid(o) \/ PaySuccessClosed(o)

-----------------------------------------------------------------------------
(************************** 不变量（safety） ********************************)
-----------------------------------------------------------------------------
TypeOK ==
  /\ state \in [OrderId -> States]
  /\ occ \in OrderId \cup {NoOrder}
  /\ settleable \in BOOLEAN
  /\ settled \in BOOLEAN
  /\ tags \subseteq RowTags

\* PAY-D9 / SC-Q14：同一罚款至多一个未释放订单。
InvOneUnreleased == Cardinality(OccupiedOrders) <= 1

\* SC-Q15"关闭与释放同生共死"：占用槽位与占用中订单一一对应；
\* 已关闭/已支付均不再占用（释放为派生事实，无『已关闭仍占用』状态）。
InvCoherent ==
  /\ (occ = NoOrder) <=> (OccupiedOrders = {})
  /\ (occ # NoOrder) => state[occ] = "OCCUPIED"

\* PAY-D6 / PAY-D14：已支付 ⇒ 已结算、不可再结算、不再占用；
\* 支付已完成与已关闭均为吸收态（无任何动作以其为前提改写业务状态，
\* 由动作守卫保证；订单历史不删除——无动作把非 NONE 改回 NONE）。
InvPaidSettled ==
  \A o \in OrderId :
    (state[o] = "PAID") => (settled /\ ~settleable /\ occ # o)

-----------------------------------------------------------------------------
(****************** 逐行可达性探针（逐个单独运行，期望 violated）************)
\* 探针为取反的可达性断言：TLC 报 violated 恰为该判定行结果可达的证明。
\* 运行方式见 formal/README.md（不得与真实不变量混跑）。
-----------------------------------------------------------------------------
ProbeNotClosedPayConflict == "ClosedPayConflict" \notin tags
ProbeNotD1  == "D1"    \notin tags   \* PAY-D1  建单失败无订单
ProbeNotD2  == "D2"    \notin tags   \* PAY-D2  超时仅日志（占用中）
ProbeNotD3  == "D3"    \notin tags   \* PAY-D3  关闭事务生效
ProbeNotD4  == "D4"    \notin tags   \* PAY-D4  关闭失败仅日志
ProbeNotD5  == "D5"    \notin tags   \* PAY-D5  重复通知幂等
ProbeNotD6  == "D6"    \notin tags   \* PAY-D6  已支付不关闭、登记退款
ProbeNotD7  == "D7"    \notin tags   \* PAY-D7  不建第二订单
ProbeNotD8  == "D8"    \notin tags   \* PAY-D8  建单成功（首次建单样本）
ProbeNotD14 == "D14"   \notin tags   \* PAY-D14 已结清不得建单
ProbeNotD15tp == "D15tp" \notin tags \* PAY-D15 超时@已支付 吸收
ProbeNotD15tc == "D15tc" \notin tags \* PAY-D15 超时@已关闭 吸收（指定样本）
ProbeNotD15cc == "D15cc" \notin tags \* PAY-D15 关闭请求@已关闭 吸收（指定样本）
ProbeNotD8p == "D8p"   \notin tags   \* 支付成功正常结算路径
=============================================================================
