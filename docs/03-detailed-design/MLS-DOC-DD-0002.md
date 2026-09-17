# 模块接口契约

| 字段     | 值                              |
| -------- | ------------------------------- |
| 文档编号 | `MLS-DOC-DD-0002`               |
| 文档状态 | `Approved`                      |
| 文档版本 | `v0.5`                          |
| 编制日期 | `2026-09-12`                    |
| 阶段     | M4 详细设计                     |

> 引用文档：
>
> - [后续实证审计与人工清单](../../HANDOFF-AUDIT-20260912.md)
> - [模块关系与 4+1 视图草图](../02-overall-design/MLS-DOC-OD-0002.md)
> - [核心流通场景因果与交互分析](../02-overall-design/MLS-DOC-OD-0003.md)
> - [系统测试设计](../02-overall-design/MLS-DOC-OD-0004.md)
> - [业务流程](../01-requirements/requirements/MLS-DOC-RA-0008.md)
> - [功能需求](../01-requirements/requirements/MLS-DOC-RA-0010.md)

## 1. 契约总则

继承 OD-0002 §17：`/api/v1` 前缀、`{code, message, data}` 信封、英文稳定枚举、游标分页、幂等键、`MLS-<域>-<序号>` 错误码。本文冻结核心模块的字段级契约；列表覆盖本轮核心路径；新增或修改契约均按 GOV-0004 影响分析、评审和版本管理，不以“可增”绕过双方契约核对。所有写接口接受 `Idempotency-Key` 头；所有接口超时上限 10s（服务端），前端展示层另设 30s 总超时与取消。

## 2. 流通模块（CIRC）

### 2.1 提交预约 `POST /api/v1/reservations`

- 权限：READER（本人）或 LIBRARIAN（代读者，`onBehalfOf` 必填）。
- 请求：`{ titleId, reservationDate, pickupSlot, onBehalfOf? }`；入口＝在线（日期守卫：最早次一开馆日）或服务台（`channel: ONLINE|DESK`，DESK 不受在线日期守卫约束）。
- 结果 data：`{ txId, outcome }`，outcome ∈ `CONFIRMED | WAITLIST_PENDING_CONFIRMATION | WAITLIST_ENQUEUED | MANUAL_HANDLING | REJECTED`（对应 RS-D4/D5/D6/D3/D2/D1）；REJECTED 携带 `reasons: string[]`（定位具体失败原因，展示次序由前端固定规则排）。
- 错误码：`MLS-CIRC-001` 参数无效、`MLS-CIRC-002` 闭馆隔离/截止失败（RS-D1）、`MLS-CIRC-003` 状态冲突（提交时复核不通过 BR-0045）、`MLS-ID-001` 权限不足。
- 候补确认复用本接口（`confirmWaitlist: true`＋原业务字段，服务端全部重验；见 §7）。

### 2.2 到台取书借出 `POST /api/v1/pickups/{txId}/claim`

- 权限：LIBRARIAN。结果 outcome ∈ `LENT | NOT_BORROWED_REJECTED | MANUAL_HANDLING`（PK-D4/D2/D3）；失败为 `MLS-CIRC-004`（PK-D1：隔离/无权/对象无效）。

### 2.3 人工事务处置 `POST /api/v1/transactions/{txId}/manual-actions`

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

- 服务间受控接口（模拟器凭证）；结果幂等：重复通知返回首次结果（D5）；超时不改状态仅日志（D2/D13）；迟到超时/关闭请求到达已终结订单仅日志（D15）；已关闭订单收到成功结果按 AC-0021 登记显式冲突异常，保留既有订单、罚款与继任订单事实；不得自动退款。已支付订单的退款必须由馆员另行授权发起（FR-0024），不由计时器或关闭请求触发。

### 3.3 关闭订单 `POST /api/v1/orders/{orderId}/close`

- 权限：READER（本人）、LIBRARIAN（受控）、SYSTEM（到期自动，内部触发）。结果 `CLOSED`；关闭事务提交失败 `MLS-PAY-004`（D4，不伪装成功）。

## 4. 复核模块（REV）

- `POST /api/v1/reviews`（LIBRARIAN 发起）、`POST /api/v1/reviews/{reviewId}/decision`（ADMIN，`{ decision: APPROVED|REJECTED }`）；执行前服务端复核目标最新状态，已变化返回 `MLS-REV-001`＋`reInitiable: true`（RV-D2/SC-Q4）；批准执行整体提交（RV-D4），失败 `MLS-REV-002` 仅审计（RV-D5）。

## 5. 通知/审计/AI（只读与受控接口）

- 通知 `GET /api/v1/notifications`（本人；按关联预约日期临近排序，同一预约事务 VISIT/NO_VISIT 互斥，不同事务可同日并存）；审计查询 `GET /api/v1/audit-events`（LIBRARIAN/ADMIN）。
- `POST /api/v1/ai/sessions` 无请求体。未携带 Bearer 时，服务端生成 256-bit 随机访客凭据，通过 `__Host-mls-ai-visitor` 浏览器会话 Cookie 返回，属性固定为 `HttpOnly; Secure; SameSite=Strict; Path=/`；数据库、正文、应用日志和审计只允许出现凭据摘要，不得出现原值。携带有效 Bearer 时创建账户会话；Bearer 与既有访客 Cookie 同时存在时，新会话归账户。
- `POST /api/v1/ai/sessions/{sessionId}/messages` 与 `GET /api/v1/ai/sessions/{sessionId}/events` 接受账户 Bearer 或访客 Cookie 二选一。账户会话只接受对应账户；访客会话只接受匹配摘要的 Cookie，额外 Bearer 不迁移所有权、不改变 `VISITOR` 角色、不切换到账户额度。ADMIN 仅可按既有权限查询事件，不得以管理员身份发送会话消息。
- 没有任何所有权凭据返回 HTTP 401；提供了凭据但与会话不匹配时返回 HTTP 404／`MLS-AI-005`，不得区分会话不存在与不可见。匿名会话仅能使用 L1 与现行访客可见数据；L2 卡片动作继续仅返回 `actionType+prefill`，不设服务端确认接口（`MLS-DEC-0021`）。
- 初次匿名启动若响应在 Cookie 到达前中断，可留下不可恢复的空会话；`sessionId`、`Idempotency-Key` 或客户端自报 ID 均不得恢复所有权。取得 Cookie 后，匿名写请求的幂等作用域为访客凭据摘要＋HTTP 方法＋规范路径＋键值。

## 6. 调用次序与依赖（跨模块）

归还链固定次序：流通结束在借→副本状态迁移→罚款形成→Outbox 登记→提交（SC-Q2 同事务）；闭馆链：控制状态 ISOLATED→逐日批次三阶段→Outbox→提交或回滚（SC-Q5）；任何模块不得越过所有者直写他模块表（OD-0002 §2）。

## 7. 核心接口差额规格（本轮修订候选）

- ID 用十进制字符串传输；日期为图书馆业务日 `YYYY-MM-DD`，时间戳为带偏移 ISO-8601；金额为整数分，禁止浮点金额。缺少可选值时省略字段，具有业务意义的“无事务”用 `txId: null` 表示。
- 预约请求完整字段为 `{titleId, reservationDate, pickupSlot, channel, onBehalfOf?, confirmWaitlist?}`。`channel` 不构成权限凭据；READER 只能 ONLINE 且本人，DESK 必须由 LIBRARIAN 代读者办理。全部资格和日期重新判定。
- 候补确认未提交业务事实，响应 `txId: null, outcome: WAITLIST_PENDING_CONFIRMATION`；确认时重复提交相同业务字段及 `confirmWaitlist: true`，服务端重新鉴权与判定。不使用未定义的 `pendingToken`；不把客户端确认当作锁定副本或保留额度。确认请求使用新的幂等键，同一确认请求重试保持原键。
- RS-D1 使用 HTTP 409／`MLS-CIRC-002`，RS-D2 使用 422；未认证 401，无权限 403，对象不存在或不可见 404，冲突 409，字段错误 400，系统失败 500。成功或待确认响应均带 `code,message,data`，`reasons` 位于 data 内；错误响应不能伪装为已创建事务。
- 写请求的幂等标识作用域为认证主体＋HTTP 方法＋规范路径＋键值，规范请求摘要覆盖业务字段；相同键不同摘要拒绝 409，相同键相同摘要返回既有结果。并发处理占位与业务事实原子提交；失败不缓存为成功。`idempotency_key.request_key` 存规范作用域的 SHA-256，不直接存用户提供的原键，`request_hash` 存规范正文摘要。
- 模拟回调请求为 `{eventId, orderId, result: SUCCESS|FAILURE, occurredAt}`，身份来自受控模拟器凭据；支付等待超时是本地等待事件，不伪造成 FAILURE 回调。成功只对合法占用订单生效；重复事件返回既有结果，冲突事件按 AC-0021 留异常证据。
- 关闭接口无额外请求字段；成功返回 `{orderId,status:CLOSED}`。PAID/CLOSED 上迟到关闭只返回现有终态，不创建退款。退款接口及对账需遵守 FR-0024/0025 的已批准范围，业务实现不由关闭接口代行。

## 修订记录

- 2026-09-17，v0.5：补匿名 AI 会话所有权线协议、Cookie 安全属性、登录不迁移、401/404 不可见性与匿名幂等作用域；删除 `clientSessionId` 请求字段。影响需求：无，访客仍仅 L1；影响验收：增加匿名所有权与登录不提权契约场景；评审人/批准人：lingzz；结论：随 REV-0004 v1.5 批准。

- 2026-09-14，v0.4：与 MD-0003 v0.3/OpenAPI 统一人工处置复数路径及语义化复核路径参数；业务规则不变；批准人：lingzz；结论：批准。

- 2026-09-14，不变：当前契约完成受影响复审，AI 实际模式由 `MLS-DEC-0030` 固定；批准人：lingzz；结论：批准。

- 2026-09-12，v0.3（WIN-T/WIN-UI）：原因：Windows 实测及用户逐图反馈；提出人/选择批准人 lingzz，记录/核对 Codex；影响需求：BR-0046、FR-0031/0032 的边界与呈现；影响验收：AC-0050/0031、AT-0031/0020 及对应设计追踪；评审人：Codex（本轮核对）；整包批准人：未新增代签；结论：用户选择已传导，图与机制为修订候选，H4/H5 保持待确认。

- 2026-09-12，v0.2（AUD-20260912）：原因：后续真实审计发现；提出人/评审人：Codex；执行授权：lingzz 本轮补齐指令；影响需求：沿用现行规则，不新增业务授权；影响验收：候补确认字段、幂等作用域、HTTP 映射、终态冲突与通知互斥范围；批准人：本轮修订候选未新增代录批准；结论：修订完成，受影响评审结论待重新核对，历史批准保留。

- 2026-09-12，v0.1：原因：M4 出口要求模块接口契约（字段、验证、错误映射、调用次序、超时）；提出人/记录人 Zcode（修补执行代理），批准依据＝用户 2026-09-12 十项清单执行指令及 DD 评审代录授权（REV-0004）；结论：核心模块契约冻结为最小完备集，实现可增不可改语义。

> 被引用文档：
>
> - [总体设计评审记录](../02-overall-design/reviews/MLS-DOC-REV-0003.md)
> - [工程文档目录](../README.md)
