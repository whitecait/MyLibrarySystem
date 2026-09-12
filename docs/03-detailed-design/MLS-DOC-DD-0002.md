# 模块接口契约

| 字段 | 值 |
| --- | --- |
| 文档编号 | `MLS-DOC-DD-0002` |
| 文档状态 | `Draft`，经 DD 评审（REV-0004） |
| 文档版本 | `v0.1` |
| 编制日期 | `2026-09-12` |
| 阶段 | M4 详细设计 |

> 引用文档：
>
> - [模块关系与 4+1 视图草图](../02-overall-design/MLS-DOC-OD-0002.md)
>
> - [核心流通场景因果与交互分析](../02-overall-design/MLS-DOC-OD-0003.md)
> - [系统测试设计](../02-overall-design/MLS-DOC-OD-0004.md)
> - [业务流程](../01-requirements/requirements/MLS-DOC-RA-0008.md)
> - [功能需求](../01-requirements/requirements/MLS-DOC-RA-0010.md)

## 1. 契约总则

继承 OD-0002 §17：`/api/v1` 前缀、`{code, message, data}` 信封、英文稳定枚举、游标分页、幂等键、`MLS-<域>-<序号>` 错误码。本文冻结核心模块的字段级契约；列表为最小完备集，实现可增不可改语义。所有写接口接受 `Idempotency-Key` 头；所有接口超时上限 10s（服务端），前端展示层另设 30s 总超时与取消。

## 2. 流通模块（CIRC）

### 2.1 提交预约 `POST /api/v1/reservations`

- 权限：READER（本人）或 LIBRARIAN（代读者，`onBehalfOf` 必填）。
- 请求：`{ titleId, reservationDate, pickupSlot, onBehalfOf? }`；入口＝在线（日期守卫：最早次一开馆日）或服务台（`channel: ONLINE|DESK`，DESK 不受在线日期守卫约束）。
- 结果 data：`{ txId, outcome }`，outcome ∈ `CONFIRMED | WAITLIST_PENDING_CONFIRMATION | WAITLIST_ENQUEUED | MANUAL_HANDLING | REJECTED`（对应 RS-D4/D5/D6/D3/D2/D1）；REJECTED 携带 `reasons: string[]`（定位具体失败原因，展示次序由前端固定规则排）。
- 错误码：`MLS-CIRC-001` 参数无效、`MLS-CIRC-002` 闭馆隔离/截止失败（RS-D1）、`MLS-CIRC-003` 状态冲突（提交时复核不通过 BR-0045）、`MLS-ID-001` 权限不足。
- 候补确认复用本接口（`confirmWaitlist: true`＋原 `pendingToken`）。

### 2.2 到台取书借出 `POST /api/v1/pickups/{txId}/claim`

- 权限：LIBRARIAN。结果 outcome ∈ `LENT | NOT_BORROWED_REJECTED | MANUAL_HANDLING`（PK-D4/D2/D3）；失败为 `MLS-CIRC-004`（PK-D1：隔离/无权/对象无效）。

### 2.3 人工事务处置 `POST /api/v1/transactions/{txId}/manual-action`

- 权限：LIBRARIAN。请求 `{ action: RELEASE | CONFIRM_ENTRY | REJECT }`；RELEASE＝借出人工放行（PK-D5，TX-15b）；CONFIRM_ENTRY＝入口人工放行（PK-D6/TX-15a，服务端校验守卫）；REJECT＝拒绝（PK-D7/TX-16）。
- 守卫不满足时 CONFIRM_ENTRY 返回 `MLS-CIRC-005`（`outcome: MANUAL_HANDLING_HELD`，事务保持人工处理中＝PK-D8 留存口径），不产生借出。

### 2.4 归还 `POST /api/v1/returns`

- 权限：LIBRARIAN。请求 `{ copyId, condition: INTACT | DAMAGED | LOST }`；结果 `RETURNED | RETURN_ABNORMAL`＋`fineId?`；重复/冲突 `MLS-CIRC-006`（RT-D1）／`MLS-CIRC-007` 转人工（RT-D2）。

### 2.5 闭馆控制 `POST /api/v1/closing/reopen`（管理员显式开放）

- 权限：ADMIN。前置校验当前仍已追平（服务端复核，不信页面显示）；成功后控制状态 REOPENED；未追平/无权 `MLS-CIRC-008`。

## 3. 罚款与结算模块（PAY）

### 3.1 建单 `POST /api/v1/fines/{fineId}/orders`

- 权限：READER（本人应结算罚款）。结果：`{ orderId, status: OCCUPIED, expiresAt }`；已有未释放订单 `MLS-PAY-001`（D7）；已结清 `MLS-PAY-002`（D14）；隔离期 `MLS-CIRC-002`。
### 3.2 支付尝试回调（模拟器→服务端）`POST /api/v1/payment-callbacks`

- 服务间受控接口（模拟器凭证）；结果幂等：重复通知返回首次结果（D5）；超时不改状态仅日志（D2/D13）；到达已终结订单仅日志（D15 终态吸收）；已支付＋关闭请求 `MLS-PAY-003` 登记退款处理（D6）。
### 3.3 关闭订单 `POST /api/v1/orders/{orderId}/close`

- 权限：READER（本人）、LIBRARIAN（受控）、SYSTEM（到期自动，内部触发）。结果 `CLOSED`；关闭事务提交失败 `MLS-PAY-004`（D4，不伪装成功）。

## 4. 复核模块（REV）

- `POST /api/v1/reviews`（LIBRARIAN 发起）、`POST /api/v1/reviews/{id}/decision`（ADMIN，`{ decision: APPROVED|REJECTED }`）；执行前服务端复核目标最新状态，已变化返回 `MLS-REV-001`＋`reInitiable: true`（RV-D2/SC-Q4）；批准执行整体提交（RV-D4），失败 `MLS-REV-002` 仅审计（RV-D5）。

## 5. 通知/审计/AI（只读与受控接口）

- 通知 `GET /api/v1/notifications`（本人；按关联预约日期临近排序，同日 VISIT/NO_VISIT 互斥）；审计查询 `GET /api/v1/audit-events`（LIBRARIAN/ADMIN）；AI 会话 `POST /api/v1/ai/sessions`、`POST /api/v1/ai/sessions/{id}/messages`（L2 卡片动作仅返回 `actionType+prefill`，前端跳现行表单，不设服务端确认接口——`MLS-DEC-0021`）。

## 6. 调用次序与依赖（跨模块）

归还链固定次序：流通结束在借→副本状态迁移→罚款形成→Outbox 登记→提交（SC-Q2 同事务）；闭馆链：控制状态 ISOLATED→逐日批次三阶段→Outbox→提交或回滚（SC-Q5）；任何模块不得越过所有者直写他模块表（OD-0002 §2）。

## 修订记录

- 2026-09-12，v0.1：原因：M4 出口要求模块接口契约（字段、验证、错误映射、调用次序、超时）；提出人/记录人 Zcode（修补执行代理），批准依据＝用户 2026-09-12 十项清单执行指令及 DD 评审代录授权（REV-0004）；结论：核心模块契约冻结为最小完备集，实现可增不可改语义。

> 被引用文档：
>
> - [总体设计评审记录](../02-overall-design/reviews/MLS-DOC-REV-0003.md)
> - [工程文档目录](../README.md)
