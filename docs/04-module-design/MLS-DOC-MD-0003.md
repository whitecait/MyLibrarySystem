# 对外契约与共同样例

| 字段     | 值                         |
| -------- | -------------------------- |
| 文档编号 | `MLS-DOC-MD-0003`          |
| 文档状态 | `In Review`（v0.3 历史批准保留） |
| 文档版本 | `v0.4`                     |
| 编制日期 | `2026-09-13`               |
| 阶段     | M5 模块设计收尾与单元测试  |
| 权威性质 | 已批准的前后端共同契约     |

> 引用文档：
>
> - [功能需求](../01-requirements/requirements/MLS-DOC-RA-0010.md)
> - [业务规则](../01-requirements/requirements/MLS-DOC-RA-0011.md)
> - [工程决策日志](../00-governance/MLS-DOC-GOV-0005.md)
> - [模块接口契约](../03-detailed-design/MLS-DOC-DD-0002.md)
> - [模块目录与依赖规则](MLS-DOC-MD-0002.md)
> - [共同场景样例](contracts/examples/contract-scenarios.json)
> - [共同场景 Schema](contracts/examples/contract-scenarios.schema.json)
> - [HTTP 机器契约](contracts/http/openapi.yaml)

## 1. 共同规则

- 根路径为 `/api/v1`；所有响应使用 `{code,message,data}`。成功码为模块稳定码，错误不得放进成功响应伪装办理成功。
- ID 为十进制字符串，金额为整数分，业务日为 `YYYY-MM-DD`，时间戳为带偏移 ISO-8601；可选值缺失时省略，有业务意义的空值显式为 `null`。
- 未认证、无权限、不可见对象、字段错误、业务冲突、规则拒绝和系统失败分别使用 HTTP `401/403/404/400/409/422/500`。
- 所有写请求携带 `Idempotency-Key`。作用域为认证主体、HTTP 方法、规范路径和键；同键同摘要返回原结果，同键不同摘要返回 `409 MLS-IDEMP-002`。
- `channel`、`readerId`、`onBehalfOf` 和前端隐藏控件都不是权限凭据。服务端以会话身份重新鉴权，并在提交时重新读取权威状态。
- 列表使用 `?limit=&cursor=`，响应为 `{items,nextCursor}`；`limit` 最大 100。服务端处理上限 10 秒，前端总等待上限 30 秒并允许取消。
- `contracts/http/openapi.yaml` 是路径、参数名、请求/响应字段、必填性、空值、枚举和成功 HTTP 状态的唯一机器源；全部 45 个操作当前成功状态为 HTTP `200`。本文件是权限、业务语义、幂等、事务及错误条件的规范入口。
- 前端类型、请求客户端和 mock 结构由 OpenAPI 生成或验证；后端 Controller DTO 与响应也对同一 OpenAPI 验证。前端不得从 Controller 或 Java 类型反推契约，Controller 不得以实体或领域对象作为响应。

## 2. 身份与书目契约

| 方法与路径 | 权限 | 请求或查询 | 成功 data | 主要错误 | 覆盖 |
| ---------- | ---- | ---------- | --------- | -------- | ---- |
| `POST /auth/registrations` | 公开 | `{username,password,displayName}` | `{accountId,role:READER,status:ACTIVE}` | `MLS-ID-002` 重名；`MLS-ID-003` 字段无效 | FR-0002 |
| `POST /auth/sessions` | 公开 | `{username,password}` | `{accessToken,expiresAt,actor}` | `MLS-ID-004` 凭据失败；`MLS-ID-005` 账户不可用 | FR-0003 |
| `DELETE /auth/sessions/current` | 已登录 | 无 | `{ended:true}` | `MLS-ID-001` | FR-0003 |
| `GET /accounts` | ADMIN | `role,status,cursor,limit` | 账户摘要列表 | `MLS-ID-001` | FR-0004 |
| `POST /accounts/{accountId}/change-requests` | LIBRARIAN | `{action:SUSPEND|RESTORE|CHANGE_ROLE,payload,reason}` | `{reviewId,status:PENDING}`；批准前账户不变 | `MLS-ID-006` 状态冲突 | FR-0004 |
| `POST /accounts/{accountId}/password-reset` | LIBRARIAN/ADMIN | `{reason}` | `{temporaryPassword,forceChange:true}` | `MLS-ID-006` 账户状态冲突 | FR-0004 |
| `PATCH /readers/{readerId}/status` | LIBRARIAN/ADMIN | `{status,reason}` | 读者状态摘要 | `MLS-ID-006` 状态冲突 | FR-0005 |
| `GET /titles` | 公开 | `q,category,cursor,limit` | 书目级结果与 `availability` | `MLS-CAT-001` 查询无效 | FR-0001/0040/0041 |
| `GET /titles/{titleId}` | 公开 | 无 | 书目详情、书目级可借状态、阅读内容入口 | `MLS-CAT-002` 不存在 | FR-0001/0040/0041/0045 |
| `POST /titles` | LIBRARIAN | 书目字段，金额为 `priceCents` | 书目详情 | `MLS-CAT-003` ISBN/版本冲突 | FR-0006 |
| `PATCH /titles/{titleId}` | LIBRARIAN | 可维护字段＋`version` | 书目详情 | `MLS-CAT-003` 版本冲突 | FR-0006 |
| `DELETE /titles/{titleId}` | LIBRARIAN | `{version,reason}` | `{status:REMOVED}` | `MLS-CAT-004` 仍有受约束事实 | FR-0006 |
| `GET /titles/{titleId}/content` | 公开 | 只读分页参数 | `{contentType,pages}` | `MLS-CAT-005` 无公开内容 | FR-0045 |
| `POST /titles/{titleId}/copies` | LIBRARIAN | `{copyNo}` | `{copyId,status:PENDING_SHELVING}` | `MLS-CIRC-010` 重复副本 | FR-0007 |
| `GET /copies` | LIBRARIAN | `titleId,status,cursor,limit` | 逐册状态列表 | `MLS-CIRC-001` 查询无效 | FR-0008 |
| `PATCH /copies/{copyId}` | LIBRARIAN | `{action:SHELVE|MAINTAIN|RESTORE|REMOVE,version}` | 副本状态 | `MLS-CIRC-003` 非法迁移 | FR-0007/0008 |

`availability` 只取 `AVAILABLE|UNAVAILABLE|UNKNOWN`，是书目级展示，不泄露公众逐册状态，也不保证后续预约成功。

## 3. 流通契约

| 方法与路径 | 权限 | 请求或查询 | 成功 data | 主要错误 | 覆盖 |
| ---------- | ---- | ---------- | --------- | -------- | ---- |
| `GET /titles/{titleId}/reservation-calendar` | 公开 | `from,to` | 每业务日 `GUARANTEED|NOT_GUARANTEED|UNAVAILABLE` | `MLS-CIRC-001` 日期无效 | FR-0046 |
| `POST /reservations` | READER 本人；LIBRARIAN 代办 | `{titleId,reservationDate,pickupSlot,channel,onBehalfOf?,confirmWaitlist?}` | `{txId,outcome,reasons?}` | `MLS-CIRC-002` 隔离；`003` 冲突；`011` 资格拒绝 | FR-0028 |
| `POST /reservations/{txId}/cancel` | READER 本人；LIBRARIAN | `{reason}` | `{txId,status:NOT_BORROWED}` | `MLS-CIRC-003` 终态/冲突 | FR-0028 |
| `POST /walk-in-loans` | READER 本人；LIBRARIAN 代办 | `{readerId?,items:[{titleId,copyId?}],channel}` | `{items:[{itemRef,txId?,outcome,reasons?}]}` | 逐册返回规则拒绝；请求级鉴权错误 | FR-0009/0010/0036 |
| `POST /pickups/{txId}/claim` | LIBRARIAN | `{copyId}` | `{txId,outcome}` | `MLS-CIRC-004` 无效对象/隔离 | FR-0009/0010/0028 |
| `POST /transactions/{txId}/manual-actions` | LIBRARIAN | `{action:RELEASE|CONFIRM_ENTRY|REJECT,reason}` | `{txId,outcome}` | `MLS-CIRC-005` 守卫不满足，保持人工处理中 | FR-0011/0012/0037 |
| `POST /loans/{txId}/renewals` | READER 本人 | `{requestedDueDate?}` | `{txId,dueDate,outcome}` | `MLS-CIRC-012` 预约/次数/欠费拒绝 | FR-0015/0016 |
| `POST /returns` | LIBRARIAN | `{copyId,condition:INTACT|DAMAGED|LOST}` | `{txId,outcome,fineId?}` | `MLS-CIRC-006` 重复；`007` 转人工 | FR-0013/0014 |
| `POST /closing/runs` | SYSTEM | `{businessDate}` | `{batchId,status}` | `MLS-CIRC-013` 已运行；`014` 协调失败 | FR-0038 |
| `POST /closing/reopen` | ADMIN | `{businessDate}` | `{status:REOPENED,epoch}` | `MLS-CIRC-008` 未追平/无权 | FR-0038 |

预约 `outcome` 为 `CONFIRMED|WAITLIST_PENDING_CONFIRMATION|WAITLIST_ENQUEUED|MANUAL_HANDLING|REJECTED`。待确认结果必须返回 `txId:null`；确认请求复送完整业务字段、使用新幂等键并重新鉴权与判定。现场借阅的每册是独立事务，混合批次逐册呈现。

## 4. 结算与复核契约

| 方法与路径 | 权限 | 请求或查询 | 成功 data | 主要错误 | 覆盖 |
| ---------- | ---- | ---------- | --------- | -------- | ---- |
| `GET /fines` | READER 本人；LIBRARIAN | `readerId?,status,cursor,limit` | 罚款列表 | `MLS-PAY-005` 对象不可见 | FR-0017 |
| `POST /fines/{fineId}/orders` | READER 本人 | 无 | `{orderId,status:OCCUPIED,expiresAt}` | `MLS-PAY-001` 已占用；`002` 已结清 | FR-0018/0047 |
| `GET /orders/{orderId}` | READER 本人；LIBRARIAN | 无 | 订单终态或占用状态 | `MLS-PAY-005` 不可见 | FR-0019 |
| `POST /payment-simulator/results` | 受控测试操作者 | `{orderId,result,delayMs?,duplicateCount?}` | `{simulationId,status}` | `MLS-PAY-006` 非本地模拟器 | FR-0020/0026 |
| `POST /payment-callbacks` | 模拟器凭据 | `{eventId,orderId,result:SUCCESS|FAILURE,occurredAt}` | 首次或原幂等结果 | `MLS-PAY-003` 冲突事件 | FR-0021/0022 |
| `POST /orders/{orderId}/close` | READER 本人；LIBRARIAN；SYSTEM | 无 | `{orderId,status:CLOSED}`；终态返回既有状态 | `MLS-PAY-004` 关闭提交失败 | FR-0023/0047 |
| `POST /orders/{orderId}/refunds` | LIBRARIAN 发起，ADMIN 复核 | `{amountCents,reason}` | `{reviewId,status:PENDING}`；批准前不退款 | `MLS-PAY-007` 金额/终态不合法 | FR-0024 |
| `POST /reconciliations` | SYSTEM/ADMIN | `{businessDate}` | `{reconciliationId,counts,exceptions}` | `MLS-PAY-008` 日期重复/冲突 | FR-0025 |
| `POST /reviews` | LIBRARIAN | `{targetType,targetId,action,payload,reason}`；账户变更由 `/accounts/{id}/change-requests` 提交同一命令 | `{reviewId,status:PENDING}` | `MLS-REV-003` 不属高风险动作 | FR-0004/0012 |
| `POST /reviews/{reviewId}/decision` | ADMIN | `{decision:APPROVED|REJECTED,reason}` | `{reviewId,status,targetOutcome?}` | `MLS-REV-001` 目标已变；`002` 执行失败 | FR-0004/0012 |
| `GET /reviews` | LIBRARIAN/ADMIN | 状态与分页 | 复核摘要列表 | `MLS-ID-001` | FR-0004/0012 |

支付等待超时是本地等待事件，不伪造成失败回调。成功回调只对合法占用订单生效；重复 `eventId` 返回首次结果，冲突事件留证但不改写业务事实。任何金额的退款都是高风险动作：馆员发起、管理员复核，批准前不生效；迟到关闭或计时器不能自动退款。

## 5. 查询、通知、审计与 AI 契约

| 方法与路径 | 权限 | 请求或查询 | 成功 data | 主要错误 | 覆盖 |
| ---------- | ---- | ---------- | --------- | -------- | ---- |
| `GET /dashboard/arrivals` | LIBRARIAN | `businessDate` | 到馆统计四项固定口径 | `MLS-QUERY-001` 日期无效 | FR-0039 |
| `GET /me/business` | READER | `kind,status,cursor,limit` | 本人预约、借阅、罚款与订单组合 | `MLS-ID-001` | FR-0043 |
| `GET /notifications` | 已登录本人 | `unreadOnly,cursor,limit` | 按关联预约日期临近排序 | `MLS-ID-001` | FR-0044 |
| `POST /notifications/{notificationId}/read` | 已登录本人 | 无 | `{read:true}` | `MLS-DEL-001` 不可见 | FR-0044 |
| `GET /audit-events` | LIBRARIAN/ADMIN | 关联 ID、操作者、类型与分页 | 审计事件列表 | `MLS-ID-001` | FR-0027 |
| `POST /ai/sessions` | 公开或 Bearer 已登录 | 无请求体；匿名成功响应设置访客 Cookie | `{sessionId,role,quota}`，匿名 role=`VISITOR` | `MLS-AI-001` AI 不可用 | FR-0029/0030/0035 |
| `POST /ai/sessions/{sessionId}/messages` | 账户 Bearer 本人或匹配访客 Cookie | `{message}` | `{messageId,answer,citations,actions,quota}` | `MLS-AI-002` 超额；`003` 端点失败；`004` 工具拒绝；`005` 不可见 | FR-0030/0031/0033/0034/0035 |
| `GET /ai/sessions/{sessionId}/events` | 账户 Bearer 本人、匹配访客 Cookie；ADMIN | 分页 | 逐事件会话记录 | `MLS-ID-001` 无凭据；`MLS-AI-005` 不可见 | FR-0033 |

AI `actions` 只允许 `{actionType,level,prefill,draftId,label}`。L1 可带查看入口；L2 只预填当前页面模态表单；L3 只给引导；资金与高风险动作不得由 AI 直接执行。用户提交预填后调用本文件同一业务契约，并携带可选 `Ai-Draft-Id` 供操作审计关联；取消不提交业务请求。

匿名启动由服务端签发 `__Host-mls-ai-visitor` 浏览器会话 Cookie（256-bit 随机凭据；`HttpOnly; Secure; SameSite=Strict; Path=/`），仅存摘要，不在响应正文、日志或审计中出现原值。`clientSessionId` 从 OpenAPI 2.0 删除，`sessionId`、幂等键和客户端自报值均不是所有权凭据。已有访客会话即使请求同时携带 Bearer 也保持 `VISITOR`、使用访客全局额度；Bearer 只使同次新建会话归账户。没有任何所有权凭据返回 401；错误或不匹配凭据统一返回 404／`MLS-AI-005`，不泄露会话存在性。首次响应若在 Cookie 抵达前中断，允许留下不可恢复的空会话。

实际模型运行配置显式采用 `thinking=disabled`（`MLS-DEC-0030`）。provider-default 的强制工具选择不兼容结果保留为证据，不作为首版配置。

## 6. 登录续行与共同样例

未登录用户触发需认证动作时，前端只保存 `{intent,path,params}`，完成登录后回到原页面并重新取得显示数据；不得保存权限结论、锁、副本占用或已通过业务判定。最终提交仍按本文件契约鉴权和复核，覆盖 FR-0042。

`contracts/examples/contract-scenarios.json` 是前端 mock、后端 Controller 契约测试与联调共同使用的固定样例，并由相邻的 `contract-scenarios.schema.json` 校验样例外形。请求与响应结构还必须逐操作通过 OpenAPI 校验；样例中的 `request`、`response` 和 `expect` 必须同时更新。14 个样例是跨域关键行为集，其中新增 5 个匿名 AI 场景覆盖 Cookie 启动、连续交互、错误凭据不可见、登录不提权与跨访客共享额度；它们不承担 45 个操作的结构穷举。样例通过不替代模块规则、PostgreSQL 并发或产品验收测试。

## 修订记录

- 2026-09-17，v0.4：OpenAPI 契约升至 2.0.0，删除 `clientSessionId` 和启动请求体，增加匿名 Cookie/Bearer 替代安全方案、Set-Cookie 响应、`VISITOR` AI 角色、401/404 所有权语义与 5 个共同样例；45 个 operationId 不变。影响需求：无；影响验收：匿名所有权、不可见性、登录不提权和共享额度可结构验证；前端生成物按批准选择暂不重建，CONTRACT-002 预期漂移；评审人/批准人：lingzz 待 REV-0005 v1.2 复核；结论：In Review。

- 2026-09-14，v0.3：增加覆盖 45 个操作的 OpenAPI 3.1 机器契约与共同样例 Schema，统一语义化路径参数和 HTTP 200 成功状态，修正写请求样例的幂等头；批准人：lingzz；结论：批准。

- 2026-09-14，v0.2：将 AI 实际模式固定为 `thinking=disabled`，其余契约内容完成评审；影响需求：无；影响验收：按该配置执行 AI 场景；批准人：lingzz；结论：批准。

- 2026-09-13，v0.1：原因：DD-0002 只冻结核心路径，9 月 14—15 日工作包要求补齐全部已批准功能及共同样例；提出人：执行代理；影响需求：覆盖 FR-0001～0047，不改变业务值；影响验收：为前端 mock、后端契约测试和联调提供同一输入；评审人：执行代理完成需求覆盖检查；批准人：待模块设计评审；结论：形成待评审契约，AI 运行模式保持未决。

> 被引用文档：
>
> - [模块设计阶段入口](MLS-DOC-MD-0001.md)
> - [内部接口与适配缝](MLS-DOC-MD-0004.md)
> - [验证映射与测试责任](MLS-DOC-MD-0005.md)
> - [模块设计评审与基线记录](reviews/MLS-DOC-REV-0005.md)
> - [模块关系与 4+1 视图](../02-overall-design/MLS-DOC-OD-0002.md)
