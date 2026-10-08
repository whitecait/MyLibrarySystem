# 对外契约与共同样例

| 字段     | 值                         |
| -------- | -------------------------- |
| 文档编号 | `MLS-DOC-MD-0003`          |
| 文档状态 | `Approved`                 |
| 文档版本 | `v0.12`                    |
| 编制日期 | `2026-09-13`               |
| 阶段     | M5 模块设计收尾与单元测试  |
| 权威性质 | 已批准的前后端共同契约     |

> 引用文档：
>
> - [限定范围交付与 DevOps 阶段规则](../00-governance/MLS-DOC-GOV-0008.md)
>
> - [功能需求](../01-requirements/requirements/MLS-DOC-RA-0010.md)
> - [业务规则](../01-requirements/requirements/MLS-DOC-RA-0011.md)
> - [工程决策日志](../00-governance/MLS-DOC-GOV-0005.md)
> - [模块接口契约](../03-detailed-design/MLS-DOC-DD-0002.md)
> - [模块目录与依赖规则](MLS-DOC-MD-0002.md)
> - [共同场景样例](contracts/examples/contract-scenarios.json)
> - [共同场景 Schema](contracts/examples/contract-scenarios.schema.json)
> - [HTTP 机器契约](contracts/http/openapi.yaml)
> - [P1 裁剪与核心修复结果](../agents/delivery-closeout-20260925/results/P1.md)

## 2026-10-01 当前适用范围与批准修订

lingzz 于 2026-10-01 明确批准 A–E，并追加新增功能 TLA 形式化回归、CI 验证及完整 PR 流程要求。本节与[限定范围交付与 DevOps 阶段规则](../00-governance/MLS-DOC-GOV-0008.md)共同限定当前适用内容；下文相冲突的范围、角色、状态和准入表述保留为原基线历史，其余约束继续有效。批准修订与执行验证分别登记。

当前规则：预约日期暂按每天开馆，线上最早次日、馆员服务台可同日；注册按浏览器会话每小时最多 5 次请求，失败也计数，第 6 次 429；日结默认按 Asia/Shanghai 每日零点触发，逐日追平后保持隔离，由 ADMIN 受控重开。当天 SHELVE 允许上架，登记、正常归还、释放当天仍不允许实际借出；RESTORE 沿用已有同日例外，歧义存量保守延至次日。订单关闭为 READER 本人、LIBRARIAN/ADMIN 他人及合法 SYSTEM，其他接口权限不因此扩大。馆员现场 AI 草稿可显式应用到现行表单，提交仍由人确认；读者现场 AI 预填已移出，既有读者续借草稿保留。

HTTP OpenAPI 与生成物本轮保持既有运行内容；本文补齐 ADMIN 关闭订单文字契约及授权先于幂等/终态返回的语义。新阶段合同变化通过模型、生成物一致性和 CI 后进入完整 PR 流程。关闭权限的扩展不自动扩大 ADMIN 查他人支付单或 AI 导航范围。

## 1. 共同规则

- 根路径为 `/api/v1`；所有响应使用 `{code,message,data}`。成功码为模块稳定码，错误不得放进成功响应伪装办理成功。
- ID 为十进制字符串，金额为整数分，业务日为 `YYYY-MM-DD`，时间戳为带偏移 ISO-8601；可选值缺失时省略，有业务意义的空值显式为 `null`。
- 未认证、无权限、不可见对象、字段错误、业务冲突、规则拒绝和系统失败分别使用 HTTP `401/403/404/400/409/422/500`。
- 所有仍启用的业务写请求携带 `Idempotency-Key`。作用域为认证主体、HTTP 方法、规范路径和键；同键同摘要返回原结果，同键不同摘要返回 `409 MLS-IDEMP-002`。退款与对账保留为废弃兼容操作，始终返回 `422 MLS-PAY-009`，不要求身份、幂等键或请求体，也不产生业务事实。
- `channel`、`readerId`、`onBehalfOf` 和前端隐藏控件都不是权限凭据。服务端以会话身份重新鉴权，并在提交时重新读取权威状态。
- 列表使用 `?limit=&cursor=`，响应为 `{items,nextCursor}`；`limit` 最大 100。服务端处理上限 10 秒，前端总等待上限 30 秒并允许取消。
- `contracts/http/openapi.yaml` 是路径、参数名、请求/响应字段、必填性、空值、枚举和成功 HTTP 状态的唯一机器源；43 个启用操作成功状态为 HTTP `200`，退款与对账两个废弃操作只返回 HTTP `422`。本文件是权限、业务语义、幂等、事务及错误条件的规范入口。
- 前端类型、请求客户端和 mock 结构由 OpenAPI 生成或验证；后端 Controller DTO 与响应也对同一 OpenAPI 验证。前端不得从 Controller 或 Java 类型反推契约，Controller 不得以实体或领域对象作为响应。

## 2. 身份与书目契约

| 方法与路径 | 权限 | 请求或查询 | 成功 data | 主要错误 | 覆盖 |
| ---------- | ---- | ---------- | --------- | -------- | ---- |
| `POST /auth/registrations` | 公开 | `{username,password,displayName}`；可选服务端签发浏览器会话 Cookie | `{accountId,role:READER,status:ACTIVE}` | `MLS-ID-002` 重名；`MLS-ID-003` 字段无效；`MLS-ID-008` 会话注册限额（429） | FR-0002 |
| `POST /auth/sessions` | 公开 | `{username,password}` | `{accessToken,expiresAt,actor}` | `MLS-ID-004` 凭据失败；`MLS-ID-005` 账户不可用 | FR-0003 |
| `DELETE /auth/sessions/current` | 已登录 | 无 | `{ended:true}` | `MLS-ID-001` | FR-0003 |
| `GET /accounts` | ADMIN | `role,status,cursor,limit` | 账户摘要列表 | `MLS-ID-001` | FR-0004 |
| `POST /accounts/{accountId}/change-requests` | LIBRARIAN | `{action:SUSPEND|RESTORE|CHANGE_ROLE,payload,reason}` | `{reviewId,status:PENDING}`；批准前账户不变 | `MLS-ID-006` 状态冲突 | FR-0004 |
| `POST /accounts/{accountId}/password-reset` | LIBRARIAN/ADMIN | `{reason}` | `{temporaryPassword,forceChange:true}` | `MLS-ID-006` 账户状态冲突 | FR-0004 |
| `PATCH /readers/{readerId}/status` | LIBRARIAN/ADMIN | `{status,reason}` | 读者状态摘要 | `MLS-ID-006` 状态冲突 | FR-0005 |
| `GET /titles` | 公开 | `q,category,availability,cursor,limit`；`availability=AVAILABLE|UNAVAILABLE|UNKNOWN` | 书目级结果与 `availability` | `MLS-CAT-001` 查询/筛选无效；`MLS-API-001` 参数类型无效 | FR-0001/0040/0041 |
| `GET /titles/{titleId}` | 公开 | 无 | 书目详情、书目级可借状态、阅读内容入口 | `MLS-CAT-002` 不存在；`MLS-API-001` 标识符类型无效 | FR-0001/0040/0041/0045 |
| `POST /titles` | LIBRARIAN | 书目字段，金额为 `priceCents` | 书目详情 | `MLS-CAT-003` ISBN/版本冲突 | FR-0006 |
| `PATCH /titles/{titleId}` | LIBRARIAN | 可维护字段＋`version` | 书目详情 | `MLS-CAT-003` 版本冲突 | FR-0006 |
| `DELETE /titles/{titleId}` | LIBRARIAN | `{version,reason}` | `{status:REMOVED}` | `MLS-CAT-004` 仍有受约束事实 | FR-0006 |
| `GET /titles/{titleId}/content` | 公开 | 只读分页参数 | `{contentType,pages}` | `MLS-CAT-005` 无公开内容 | FR-0045 |
| `POST /titles/{titleId}/copies` | LIBRARIAN | `{copyNo}` | `{copyId,status:PENDING_SHELVING}` | `MLS-CIRC-010` 重复副本 | FR-0007 |
| `GET /copies` | LIBRARIAN | `titleId,status,cursor,limit` | 逐册状态列表 | `MLS-CIRC-001` 查询无效或状态不在契约枚举内；`MLS-API-001` 参数类型无效 | FR-0008 |
| `PATCH /copies/{copyId}` | LIBRARIAN | `{action:SHELVE|MAINTAIN|RESTORE|REMOVE,version}` | 副本状态 | `MLS-CIRC-003` 非法迁移 | FR-0007/0008 |

`availability` 只取 `AVAILABLE|UNAVAILABLE|UNKNOWN`，是书目级展示，不泄露公众逐册状态，也不保证后续预约成功。

注册按服务端签发的 `mls_registration_session` 浏览器 Cookie 计额；Cookie 为随机 opaque 值、`HttpOnly; SameSite=Strict; Path=/`，仅 HTTPS 请求设置 `Secure`。服务端只存 Cookie 摘要，按一小时窗口累计每个请求（包括校验失败）；每会话前 5 次请求进入注册处理，第 6 次及后续请求返回 `429 MLS-ID-008`。限额按浏览器会话，不按 IP；清除浏览器会话会建立新的额度身份。该会话机制的清除边界不构成 IP 防滥用保证。

## 3. 流通契约

| 方法与路径 | 权限 | 请求或查询 | 成功 data | 主要错误 | 覆盖 |
| ---------- | ---- | ---------- | --------- | -------- | ---- |
| `GET /titles/{titleId}/reservation-calendar` | 公开 | `from,to` | 每业务日 `GUARANTEED|NOT_GUARANTEED|UNAVAILABLE` | `MLS-CIRC-001` 日期无效 | FR-0046 |
| `POST /reservations` | READER 本人；LIBRARIAN 代办 | `{titleId,reservationDate,pickupSlot,channel,onBehalfOf?,confirmWaitlist?}` | `{txId,outcome,reasons?}` | `MLS-CIRC-002` 隔离；`003` 冲突；`011` 资格拒绝 | FR-0028 |
| `POST /reservations/{txId}/cancel` | READER 本人；LIBRARIAN | `{reason}` | `{txId,status:NOT_BORROWED}` | `MLS-CIRC-003` 终态/冲突 | FR-0028 |
| `POST /walk-in-loans` | LIBRARIAN 代办 | `{readerId,items:[{titleId,copyId?}],channel:DESK}` | `{items:[{itemRef,txId?,outcome,reasons?}]}` | 逐册返回规则拒绝；请求级鉴权错误 | FR-0009/0010/0036 |
| `POST /pickups/{txId}/claim` | LIBRARIAN | `{copyId}` | `{txId,outcome}` | `MLS-CIRC-004` 无效对象/隔离 | FR-0009/0010/0028 |
| `POST /transactions/{txId}/manual-actions` | LIBRARIAN | `{action:RELEASE|CONFIRM_ENTRY|REJECT,reason}` | `{txId,outcome}` | `MLS-CIRC-005` 守卫不满足，保持人工处理中 | FR-0011/0012/0037 |
| `POST /loans/{txId}/renewals` | READER 本人 | `{requestedDueDate?}` | `{txId,dueDate,outcome}` | `MLS-CIRC-012` 预约/次数/欠费拒绝 | FR-0015/0016 |
| `POST /returns` | LIBRARIAN | `{copyId,condition:INTACT|DAMAGED|LOST}` | `{txId,outcome,fineId?}` | `MLS-CIRC-006` 重复；`007` 转人工 | FR-0013/0014 |
| `POST /closing/runs` | SYSTEM | `{businessDate}` | `{batchId,status}` | `MLS-CIRC-013` 已运行；`014` 协调失败 | FR-0038 |
| `POST /closing/reopen` | ADMIN | `{businessDate}` | `{status:REOPENED,epoch}` | `MLS-CIRC-008` 未追平/无权 | FR-0038 |

预约 `outcome` 为 `CONFIRMED|WAITLIST_PENDING_CONFIRMATION|WAITLIST_ENQUEUED|MANUAL_HANDLING|REJECTED`。待确认结果必须返回 `txId:null`；确认请求复送完整业务字段、使用新幂等键并重新鉴权与判定。现场借阅仅保留馆员到台办理：服务端要求 LIBRARIAN 身份与 DESK 渠道；读者自助扫描/现场借阅会话扩展不属于本次交付。每册是独立事务，混合批次逐册呈现。

## 4. 结算与复核契约

| 方法与路径 | 权限 | 请求或查询 | 成功 data | 主要错误 | 覆盖 |
| ---------- | ---- | ---------- | --------- | -------- | ---- |
| `GET /fines` | READER 本人；LIBRARIAN | `readerId?,status,cursor,limit` | 罚款列表 | `MLS-PAY-005` 对象不可见 | FR-0017 |
| `POST /fines/{fineId}/orders` | READER 本人 | 无 | `{orderId,status:OCCUPIED,expiresAt}` | `MLS-PAY-001` 已占用；`002` 已结清 | FR-0018/0047 |
| `GET /orders/{orderId}` | READER 本人；LIBRARIAN | 无 | 订单终态或占用状态 | `MLS-PAY-005` 不可见 | FR-0019 |
| `POST /payment-simulator/results` | 受控测试操作者 | `{orderId,result,delayMs?,duplicateCount?}` | `{simulationId,status}` | `MLS-PAY-006` 非本地模拟器 | FR-0020/0026 |
| `POST /payment-callbacks` | 模拟器凭据 | `{eventId,orderId,result:SUCCESS|FAILURE,occurredAt}` | 首次或原幂等结果 | `MLS-PAY-003` 冲突事件 | FR-0021/0022 |
| `POST /orders/{orderId}/close` | READER 本人；LIBRARIAN/ADMIN 他人；合法 SYSTEM | 无 | `{orderId,status:CLOSED}`；终态返回既有状态 | `MLS-PAY-004` 关闭提交失败 | FR-0023/0047 |
| `POST /orders/{orderId}/refunds` | 已下线；任意角色或匿名调用 | 无 | 无 | `422 MLS-PAY-009` 功能已下线；不新建复核或退款记录 | 当前交付范围不含退款 |
| `POST /reconciliations` | 已下线；任意角色或匿名调用 | 无 | 无 | `422 MLS-PAY-009` 功能已下线；不插入占位对账记录 | 当前交付范围不含对账 |
| `POST /reviews` | LIBRARIAN | `{targetType,targetId,action,payload,reason}`；账户变更由 `/accounts/{id}/change-requests` 提交同一命令 | `{reviewId,status:PENDING}` | `MLS-REV-003` 不属高风险动作 | FR-0004/0012 |
| `POST /reviews/{reviewId}/decision` | ADMIN | `{decision:APPROVED|REJECTED,reason}` | `{reviewId,status,targetOutcome?}` | `MLS-REV-001` 目标已变；`002` 执行失败 | FR-0004/0012 |
| `GET /reviews` | LIBRARIAN/ADMIN | 状态与分页 | 复核摘要列表 | `MLS-ID-001` | FR-0004/0012 |

关闭操作先按当前角色和订单归属鉴权，再处理幂等重放或终态返回；身份降权后的同键请求不能沿用旧授权。终态订单保持其既有状态。

支付等待超时是本地等待事件，不伪造成失败回调。成功回调只对合法占用订单生效；重复 `eventId` 返回首次结果，冲突事件留证但不改写业务事实。已批准规则中的退款和日终对账在当前收尾范围中整体移出；旧退款、对账和复核历史保持可读，运行时不再提供相应写效果。

`POST /payment-simulator/results` 权限列的「受控测试操作者」即 `contracts/http/openapi.yaml` 的 `x-permission: CONTROLLED_TEST_OPERATOR`；该 capability 映射到 ADMIN 角色（2026-09-22 lingzz 裁定，`docs/agents/M6-RECOVERY-PLAN-20260922.md` 第 8 节第 2 项）：模拟结果调度仅管理员会话可发起，READER 与 LIBRARIAN 调度返回 `403 MLS-ID-005`，匿名保持 `401 MLS-ID-001`。限定对象是模拟器结果调度操作本身，不改变读者正常支付建单（`POST /fines/{fineId}/orders`）与模拟器回调（`POST /payment-callbacks`）的既有权限。

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

### 5.1 本人业务分组与增量投影

`GET /me/business` 保留原 `kind,status,cursor,limit` 查询。可选 `group` 支持三个借阅事务页签，并沿用 `cursor/limit` 独立分页：

| `group` | 事务类别与状态 | 排除内容 |
| ------- | -------------- | -------- |
| `CURRENT_LOANS` | `LOAN/LENT` | 预约、终态借阅、罚款与订单 |
| `RESERVATIONS` | `RESERVATION/AWAIT`、`RESERVATION/CONFIRMED`、`RESERVATION/MANUAL_HANDLING`、`RESERVATION/WAITLIST` | 借阅、历史事务、罚款与订单 |
| `HISTORY` | `LOAN/RETURNED`、`LOAN/RETURN_ABNORMAL`、`RESERVATION/NOT_BORROWED` | 当前借阅、活动预约、罚款与订单 |

`group` 与 `kind` 同时出现时返回 `400 MLS-QUERY-001`。分组和 `status` 同时出现时，结果同时满足该分组与状态。未提供 `group` 时，旧 `kind` 过滤、混合分页和 FINE/ORDER 查询语义保持不变。详情继续通过本人业务列表按事务编号定位；本次未增加事务详情 HTTP 路径。

OpenAPI 2.1.0 为 `BusinessItem` 增加可选属性，以保持旧响应仍符合契约。更新服务器会返回 `reservationDate`、`pickupSlot`、`copyId` 和 `notifications`。`reservationDate` 只映射 `borrow_transaction.reservation_date`，不从其他时间字段补值；旧字段 `businessDate` 保持原义。`pickupSlot` 逐值映射 `pickup_slot`，不转换成钟点；`copyId` 只映射 `copy_id`。三个属性在来源值为空时显式返回 `null`。`notifications` 是数组，成功读取后没有关联记录时返回 `[]`。

通知项只含 `{notificationId,type,read,reservationDate,createdAt}`，`type` 为 `VISIT|NO_VISIT`。DELIVERY 按当前读者和列表内事务编号筛选通知。读取失败按请求失败处理，不以空数组表示失败。事务编号已由父级 `BusinessItem.id` 关联，因此不重复返回 `relatedTxId`、读者 id、审计细节或凭据。

`BusinessItem.guaranteeType` 是可选兼容属性。更新服务器始终返回该键。流通事务只映射创建时保存的 `DIRECT_CONFIRMATION` 或 `WAITLIST`；候补分配后仍返回 `WAITLIST`。旧记录无法由正向事实可靠还原时返回 `null`。FINE、ORDER 或无适用来源的事务返回 `null`。查询不得从当前状态或副本绑定推导保证类型。完整状态时间轴仍按 AT-0002 当前适用清单移出本次交付验收。

AI `actions` 只允许 `{actionType,level,prefill,draftId,label}`。L1 可带查看入口；L2 只预填当前页面模态表单；L3 只给引导；资金与高风险动作不得由 AI 直接执行。用户提交预填后调用本文件同一业务契约，并携带可选 `Ai-Draft-Id` 供操作审计关联；取消不提交业务请求。

匿名启动由服务端签发 `__Host-mls-ai-visitor` 浏览器会话 Cookie（256-bit 随机凭据；`HttpOnly; Secure; SameSite=Strict; Path=/`），仅存摘要，不在响应正文、日志或审计中出现原值。`clientSessionId` 从 OpenAPI 2.0 删除，`sessionId`、幂等键和客户端自报值均不是所有权凭据。已有访客会话即使请求同时携带 Bearer 也保持 `VISITOR`、使用访客全局额度；Bearer 只使同次新建会话归账户。没有任何所有权凭据返回 401；错误或不匹配凭据统一返回 404／`MLS-AI-005`，不泄露会话存在性。首次响应若在 Cookie 抵达前中断，允许留下不可恢复的空会话。

实际模型运行配置显式采用 `thinking=disabled`（`MLS-DEC-0030`）。provider-default 的强制工具选择不兼容结果保留为证据，不作为首版配置。

### 5.2 预约取书时段值域

`POST /reservations` 的 `pickupSlot` 仍为必填字段。旧值 `AM`、`PM` 继续有效。新增值为 48 个同日半小时时段，格式严格为 ASCII `HH:mm-HH:mm`；起点从 `00:00` 到 `23:30`，终点恰为起点后 30 分钟。最后一段为 `23:30-24:00`，`24:00` 仅可作该段终点。其他格式、错位、时长不符、跨日和以 `24:00` 起始的值返回 `400 MLS-CIRC-001`。OpenAPI `PickupSlot` 枚举列出全部新旧值。

`pickupSlot` 原样保存并返回，不转换为钟点。它只用于备书与统计，不作为到馆、取书或提前／准时／延后处理的状态守卫。既有 MLS-AT-0015 取书语义继续有效。

## 6. 登录续行与共同样例

未登录用户触发需认证动作时，前端只保存 `{intent,path,params}`，完成登录后回到原页面并重新取得显示数据；不得保存权限结论、锁、副本占用或已通过业务判定。最终提交仍按本文件契约鉴权和复核，覆盖 FR-0042。

`contracts/examples/contract-scenarios.json` 是前端 mock、后端 Controller 契约测试与联调共同使用的固定样例，并由相邻的 `contract-scenarios.schema.json` 校验样例外形。请求与响应结构还必须逐操作通过 OpenAPI 校验；样例中的 `request`、`response` 和 `expect` 必须同时更新。14 个样例是跨域关键行为集，其中新增 5 个匿名 AI 场景覆盖 Cookie 启动、连续交互、错误凭据不可见、登录不提权与跨访客共享额度；它们不承担 45 个操作的结构穷举。样例通过不替代模块规则、PostgreSQL 并发或产品验收测试。

## 7. 审计事件类型词表

`GET /audit-events` 的类型过滤取值与 `audit_event.event_type` 的写入取值同表。命名与 Outbox 事件名对齐：同一业务操作在 `outbox_event` 与 `audit_event` 使用同一稳定名，使「事件 → 审计 → 责任链」可按同一字符串互查；无 Outbox 对应者（受限访问、复核、模拟支付）在本表登记。实现侧取值集合见 `library-server/src/main/java/com/library/contract/audit/AuditEventTypes.java`。

| eventType | 触发用例 | `detail.relatedId` | `detail.outcome` |
| --------- | -------- | ------------------ | ---------------- |
| `ACCESS_DENIED` | 401/403/404 拒绝响应与令牌解析失败 | 无（路径与状态在 `detail.path`／`detail.status`） | 无（拒绝码在 `detail.code`） |
| `RESERVATION_DECIDED` | 预约判定成立（绑定成功、候补入队、转人工处理中） | 借阅事务 id | `CONFIRMED`／`WAITLIST_ENQUEUED`／`MANUAL_HANDLING` |
| `RESERVATION_CANCELLED` | 预约取消 | 借阅事务 id | `CANCELLED` |
| `WALKIN_LOAN_BATCH` | 现场逐册借阅（每个成功册一条） | 该册借阅事务 id | `LENT` |
| `PICKUP_CLAIMED` | 到台取书借出 | 借阅事务 id | `CLAIMED` |
| `MANUAL_ACTION_APPLIED` | 人工处置 | 借阅事务 id | `RELEASED`／`ENTRY_CONFIRMED`／`REJECTED` |
| `LOAN_RENEWED` | 续借成功 | 借阅事务 id | `RENEWED` |
| `COPY_RETURNED` | 归还成功 | 借阅事务 id | `RETURNED`／`RETURN_ABNORMAL` |
| `FINE_CREATED` | 归还形成罚款 | 罚款 id | `CREATED` |
| `WAITLIST_ASSIGNED` | 候补分配确认（闭馆结算、维护完成刷新结算） | 借阅事务 id | `ASSIGNED` |
| `WAITLIST_EXPIRED` | 候补到期清退（闭馆结算） | 借阅事务 id | `EXPIRED` |
| `REVIEW_REQUESTED` | 复核发起 | 复核 id | `PENDING` |
| `REVIEW_DECIDED` | 复核决定 | 复核 id | `APPROVED`／`REJECTED` |
| `SIMULATION_SCHEDULED` | 模拟支付调度 | 订单 id | `SCHEDULED` |

成功路径类型的 `detail` 至少携带 `relatedId` 与 `outcome`：`GET /audit-events` 的关联过滤与结果呈现按 `detail->>'relatedId'`、`detail->>'outcome'` 读出，缺键即该行在反查结果中关联为空。`ACCESS_DENIED` 沿用既有结构（`path`／`code`／`status`），不含这两个键，故按 `relatedId` 反查不会返回拒绝审计行。`actor_id` 为当步操作者账户 id；自动判定（闭馆结算的分配与清退）无操作者，按 0 登记。

事务语义分两类：成功路径审计加入业务事务，与所登记的业务事实同提交同回滚；拒绝与失败审计沿用 DD-0004 §1 的独立提交。

## 2026-10-07 日结自动恢复批准修订

lingzz 于 2026-10-07 明确要求修复：管理员重开作为后备处置手段，系统自行处理恢复，启动时自检并补齐漏结。本节替代此前日结成功后必须等待管理员重开的适用规则。日期顺序、事务原子性、重试预算及权限守卫继续适用。

- 启用日结的部署在应用完成启动前检查图书馆时区上一业务日及此前的漏结。按原业务日期先后逐日补结，成功批次不重复执行；不提前结算当前业务日。
- 启动检查与每日定时任务共用恢复流程。全部应结日期成功后，系统自动恢复普通写入。
- 补结失败、批次未成功或自动开放被拒绝时，保留隔离与失败记录。管理员重开保留原权限及追平守卫，用于后备处置。
- 自动恢复读取持久化重试结果，已转人工的批次不因重启重新获得自动重试预算。补结及开放期间跨午夜时，启动完成前继续检查新到期日期。
- 普通写入在恢复成功后继续按当前事实和权限判定。AI 会话创建沿用普通写入准入，不绕过闭馆隔离。

## 修订记录

- 2026-10-04，v0.11：OpenAPI 升至 2.2.0。`GET /me/business` 增加可选兼容属性 `guaranteeType`；服务器始终返回枚举值或 `null`，值只来自创建时保存的事务事实。候补分配不改写 `WAITLIST`，未知历史不从状态或副本推导。完整时间轴仍不在范围内。批准人：本任务用户明确授权；结论：保证类型本人查询投影已补。
- 2026-10-04，v0.12：在 OpenAPI 2.2.0 中扩展预约请求 `pickupSlot`：保留 `AM`／`PM` 并增加全部 48 个同日半小时时段；`24:00` 仅用于最后一段终点。服务器原样保存与返回该值，且不将其作为到馆或取书状态守卫。批准人：本任务用户明确授权；结论：预约时段契约已补。

- 2026-10-04，v0.10：OpenAPI 升至 2.1.0，`GET /me/business` 增加三个独立分页分组，以及预约日期、时段、副本关联和本人通知事实的增量投影。旧 `kind/status/cursor/limit` 语义不变；分组与 `kind` 冲突时返回 `MLS-QUERY-001`。本次不登记保证类型或完整时间轴为已实现能力。批准人：本任务用户明确授权；结论：本人查询局部契约已补，FR-0043 仍有未完成断言。

- 2026-10-01：v0.9，追加当前适用说明并传导 A–E 与新增功能 TLA/CI/完整 PR 要求。原因：限定交付与原完整基线存在范围及状态差异；提出人：lingzz/Codex；影响需求：当前范围和相邻角色、日期规则；影响验收：获批批次边界及迭代验证；内容评审/批准人：lingzz；结论：本轮局部修订 Approved，独立审计及未执行验证另行登记。
- 2026-09-27，v0.8（P1 裁剪与核心修复）：依据 [P0 范围与基线结果](../agents/delivery-closeout-20260925/results/P0.md) 明确 `/walk-in-loans` 仅允许馆员以 DESK 渠道代办；读者自助扫描扩展从当前交付范围移出。同步 OpenAPI 权限与渠道枚举。影响需求：不改变已批准预约与借阅业务规则；影响验收：读者用任一渠道请求均拒绝且不产生流通事实，馆员 DESK 借出保留；批准事实以 P0 结果为准。

- 2026-09-26，v0.7（P1 裁剪与核心修复）：登记当前交付范围停用退款、退款复核与对账写能力，保留兼容路径但仅返回 `422 MLS-PAY-009`；同步 HTTP 契约和生成接口；登记 `MLS-API-001` 供请求参数类型错误返回 400。退款、对账历史和必要表结构保留。此项是当前交付范围裁剪，不改写原始业务规则基线；执行事实见 P1 结果。

- 2026-09-22，v0.6（DEF-M6-011 实施）：新增 §7 审计事件类型词表，登记 `GET /audit-events` 的类型取值、关联对象与结果取值、操作者语义与两类事务语义。原因：缺陷 DEF-M6-011 的登记事实「audit_event 仅记录 ACCESS_DENIED、成功业务无审计写入」以该词表为可判定前提，而成文时 `AuditEvent` 的 `eventType` 为自由字符串、词表无既有定义。提出人：DEF-M6-011/012 实施员；影响需求：无（落实 `MLS-NFR-AUD-0001` 既有要求）；影响验收：AT-0002 §2.3 R5、§2.14 的类型与关联断言可判；评审人：无；批准人：未新增代录批准；结论：修订候选随实施落地，文档批准状态未变。

- 2026-09-22，v0.5：登记 `CONTROLLED_TEST_OPERATOR` capability 到 ADMIN 角色的映射（依据 2026-09-22 lingzz 批准的 M6 恢复计划第 8 节第 2 项），补记模拟结果调度端点仅管理员会话可发起；`x-permission` 取值与 45 个操作的成功状态未变，故机器契约与前端生成物无需重建。影响需求：FR-0020/0026 的调度权限语义可判；影响验收：批次 B AT-0010 越权负例、AT-0016 支付链调度角色；评审人/批准人：lingzz（2026-09-22 裁定）；结论：登记该裁定，未做结构变更。

- 2026-09-17，v0.4：OpenAPI 契约升至 2.0.0，删除 `clientSessionId` 和启动请求体，增加匿名 Cookie/Bearer 替代安全方案、Set-Cookie 响应、`VISITOR` AI 角色、401/404 所有权语义与 5 个共同样例；45 个 operationId 不变。影响需求：无；影响验收：匿名所有权、不可见性、登录不提权和共享额度可结构验证；前端生成物按批准选择暂不重建，CONTRACT-002 预期漂移；评审人/批准人：lingzz；结论：随 REV-0005 v1.2 批准，生成物与实现仍未同步。

- 2026-09-14，v0.3：增加覆盖 45 个操作的 OpenAPI 3.1 机器契约与共同样例 Schema，统一语义化路径参数和 HTTP 200 成功状态，修正写请求样例的幂等头；批准人：lingzz；结论：批准。

- 2026-09-14，v0.2：将 AI 实际模式固定为 `thinking=disabled`，其余契约内容完成评审；影响需求：无；影响验收：按该配置执行 AI 场景；批准人：lingzz；结论：批准。

- 2026-09-13，v0.1：原因：DD-0002 只冻结核心路径，9 月 14—15 日工作包要求补齐全部已批准功能及共同样例；提出人：执行代理；影响需求：覆盖 FR-0001～0047，不改变业务值；影响验收：为前端 mock、后端契约测试和联调提供同一输入；评审人：执行代理完成需求覆盖检查；批准人：待模块设计评审；结论：形成待评审契约，AI 运行模式保持未决。

- 2026-10-07：依据 lingzz 本轮明确指令追加日结启动补结、成功后自动开放及管理员后备规则；原强制人工重开表述作为历史基线保留。

> 被引用文档：
>
> - [模块设计阶段入口](MLS-DOC-MD-0001.md)
> - [内部接口与适配缝](MLS-DOC-MD-0004.md)
> - [验证映射与测试责任](MLS-DOC-MD-0005.md)
> - [模块设计评审与基线记录](reviews/MLS-DOC-REV-0005.md)
> - [模块关系与 4+1 视图](../02-overall-design/MLS-DOC-OD-0002.md)
> - [P1 裁剪与核心修复结果](../agents/delivery-closeout-20260925/results/P1.md)
>
> - [限定范围交付与 DevOps 阶段规则](../00-governance/MLS-DOC-GOV-0008.md)。
>
> - [本轮文档维护与独立审计入口](../../artifacts/doc-maintenance-20261001/REPORT.md)。
