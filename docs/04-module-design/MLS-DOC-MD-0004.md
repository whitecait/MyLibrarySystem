# 内部接口与适配缝

| 字段     | 值                         |
| -------- | -------------------------- |
| 文档编号 | `MLS-DOC-MD-0004`          |
| 文档状态 | `Approved`                 |
| 文档版本 | `v0.4`                     |
| 编制日期 | `2026-09-13`               |
| 阶段     | M5 模块设计收尾与单元测试  |
| 权威性质 | 已批准内部接口基线         |

> 引用文档：
>
> - [模块目录与依赖规则](MLS-DOC-MD-0002.md)
> - [对外契约与共同样例](MLS-DOC-MD-0003.md)
> - [事务边界、并发与恢复规则](../03-detailed-design/MLS-DOC-DD-0004.md)
> - [Outbox、缓存、故障注入与看板查询](../03-detailed-design/MLS-DOC-DD-0005.md)
> - [工程决策日志](../00-governance/MLS-DOC-GOV-0005.md)
> - [HTTP 机器契约](contracts/http/openapi.yaml)

## 1. 接口约束

以下是调用方与测试共同使用的内部接口。接口包含输入、结果、状态约束和错误模式；名称是稳定实现目标，不要求预先穷举私有类、函数或算法。命令返回结果，不让调用方读取并拼装内部状态迁移。

Java 实现以不可变 `record` 或等价值对象传递命令与结果。业务接口统一返回 `ModuleResult<T>`：`Accepted<T>(value)` 或 `Rejected<T>(code,reasons,currentVersion?)`；业务拒绝不得抛异常。基础设施失败抛出统一 `InfrastructureFailure`，由应用用例回滚并由 HTTP Adapter 映射为 500。模块内部不得抛出 Controller、JDBC、Spring Data 或供应商 SDK 类型。

跨模块值类型位于所有者模块的 `contract.model` 包。ID 使用领域包装值而非裸实体对象；金额使用 `MoneyCents(long value)`，业务日使用 `BusinessDate(LocalDate value)`，版本使用 `Version(long value)`。HTTP Adapter 显式映射 OpenAPI DTO 与这些值类型；模块接口不得 import `com.library.web`、JPA Entity、`Pageable`、`Specification` 或生成的前端类型。

## 2. 业务模块接口

每个调用方只注入需要的能力接口；下列聚合接口仅供 `com.library.bootstrap` 证明一个 Adapter 完整实现模块接口，不得注入应用用例：

```java
interface IdentityRegistration { ModuleResult<AccountRegistration> register(RegisterReader command); }
interface ActorResolver { ModuleResult<ActorContext> requireActor(SessionToken token); }
interface AuthorizationCheck { ModuleResult<AuthorizationDecision> authorize(ActorContext actor, Capability capability, SubjectRef subject); }
interface ReaderQuery { ModuleResult<ReaderSnapshot> reader(ReaderId readerId); }
interface AccountCommands { ModuleResult<AccountChange> changeAccount(ChangeAccount command); }
interface IdentityModule extends IdentityRegistration, ActorResolver, AuthorizationCheck, ReaderQuery, AccountCommands {}

interface TitleQuery { ModuleResult<TitlePage> search(TitleCriteria query, Visibility visibility); ModuleResult<TitleView> title(TitleId titleId, Visibility visibility); }
interface TitleCommands { ModuleResult<TitleChange> maintain(MaintainTitle command); }
interface ReadingContentQuery { ModuleResult<ReadingContent> readingContent(TitleId titleId, PageCursor page); }
interface CatalogModule extends TitleQuery, TitleCommands, ReadingContentQuery {}

interface AvailabilityQuery { ModuleResult<AvailabilitySnapshot> availability(TitleId titleId, BusinessDate date); }
interface ReservationCommands { ModuleResult<Reservation> reserve(ReservationCommand command); ModuleResult<Transaction> cancel(CancelReservation command); }
interface LoanCommands { ModuleResult<LoanBatch> lend(WalkInLoanCommand command); ModuleResult<Transaction> claim(ClaimPickup command); ModuleResult<Transaction> applyManualAction(ManualAction command); ModuleResult<Renewal> renew(RenewalCommand command); }
interface ReturnCommands { ModuleResult<ReturnedCopy> returnCopy(ReturnCommand command); }
interface CopyCommands { ModuleResult<CopySnapshot> maintainCopy(MaintainCopy command); }
interface ClosingCommands { ModuleResult<ClosingRun> closeBusinessDate(ClosingCommand command); ModuleResult<ReopenedDate> reopen(ReopenCommand command); }
interface CirculationModule extends AvailabilityQuery, ReservationCommands, LoanCommands, ReturnCommands, CopyCommands, ClosingCommands {}

interface DebtQuery { ModuleResult<DebtSnapshot> debtFor(ReaderId readerId); }
interface FineQuery { ModuleResult<FinePage> fines(FineCriteria query); }
interface FineCommands { ModuleResult<Fine> createFine(FineFromReturn command); }
interface OrderCommands { ModuleResult<Order> createOrder(CreateOrder command); ModuleResult<Order> applyCallback(PaymentCallback command); ModuleResult<Order> closeOrder(CloseOrder command); }
interface OrderQuery { ModuleResult<Order> order(OrderId orderId, ActorContext actor); }
interface RefundCommands { ModuleResult<Refund> refund(ApprovedRefundCommand command); }
interface ReconciliationCommands { ModuleResult<Reconciliation> reconcile(ReconciliationCommand command); }
interface SettlementModule extends DebtQuery, FineQuery, FineCommands, OrderCommands, OrderQuery, RefundCommands, ReconciliationCommands {}

interface ReviewRequesting { ModuleResult<Review> request(ReviewCommand command); }
interface ReviewDeciding { ModuleResult<ReviewDecision> decide(DecideReview command, ReviewedAction action); }
interface ReviewQuery { ModuleResult<ReviewPage> reviews(ReviewCriteria query); }
interface ReviewModule extends ReviewRequesting, ReviewDeciding, ReviewQuery {}

interface ArrivalQuery { ModuleResult<ArrivalDashboard> arrivals(BusinessDate date, ActorContext actor); }
interface MyBusinessQuery { ModuleResult<MyBusinessPage> myBusiness(MyBusinessCriteria query, ActorContext actor); }
interface PortalTitleQuery { ModuleResult<PortalTitleView> portalTitle(TitleId titleId); }
interface QueryModule extends ArrivalQuery, MyBusinessQuery, PortalTitleQuery {}

sealed interface AiSessionOwner permits AccountAiOwner, VisitorAiOwner {}
record AccountAiOwner(AccountId accountId) implements AiSessionOwner {}
record VisitorAiOwner(VisitorOwnerDigest digest) implements AiSessionOwner {}
interface AiSessionCommands { ModuleResult<AiSession> start(AiSessionOwner owner); ModuleResult<AiTurn> turn(AiTurnCommand command, AiSessionOwner owner); }
interface AiEventQuery { ModuleResult<AiEventPage> events(AiEventCriteria query, AiSessionOwner owner, Optional<ActorContext> admin); }
interface AiModule extends AiSessionCommands, AiEventQuery {}

interface NotificationQuery { ModuleResult<NotificationPage> notifications(NotificationCriteria query, ActorContext actor); }
interface NotificationCommands { ModuleResult<ReadReceipt> markRead(NotificationId notificationId, ActorContext actor); }
interface AuditQuery { ModuleResult<AuditPage> audit(AuditCriteria query, ActorContext actor); }
interface OutboxConsumer { ModuleResult<DeliveryReceipt> consume(OutboxEnvelope event); }
interface DeliveryModule extends NotificationQuery, NotificationCommands, AuditQuery, OutboxConsumer {}
```

### 2.1 命令、结果与错误的稳定形状

- `RegisterReader`、`MaintainTitle`、`ReservationCommand`、`WalkInLoanCommand`、`ClaimPickup`、`ManualAction`、`RenewalCommand`、`ReturnCommand`、`MaintainCopy`、`CreateOrder`、`PaymentCallback`、`CloseOrder`、`ReconciliationCommand`、`ReviewCommand`、`DecideReview` 与 `AiTurnCommand` 的业务字段，分别与 OpenAPI 中同名或对应的 `*Request` schema 一致；AI 会话启动无请求业务字段。认证得到的 `ActorContext`、服务端版本和业务时点由应用用例附加，不接受客户端覆盖。
- `AccountRegistration`、`ReaderSnapshot`、`TitlePage`、`TitleView`、`ReadingContent`、`CopySnapshot`、`AvailabilitySnapshot`、`Reservation`、`Transaction`、`LoanBatch`、`Renewal`、`ReturnedCopy`、`ClosingRun`、`ReopenedDate`、`FinePage`、`Fine`、`Order`、`Reconciliation`、`Review`、`ReviewDecision`、`ReviewPage`、`ArrivalDashboard`、`MyBusinessPage`、`PortalTitleView`、`NotificationPage`、`AuditPage`、`AiSession`、`AiTurn` 与 `AiEventPage` 的可见字段分别映射对应 OpenAPI `*Data` schema；映射可以删去仅供 HTTP 展示的 `message`，不得新增数据库、框架或 Adapter 类型。
- `ActorContext(accountId,role,authenticatedAt)` 只由 `ActorResolver` 产生；`SubjectRef(type,id)`、`Capability(value)` 和 `Visibility(value)` 是服务端受控值。调用方不得自行构造更高权限 `ActorContext`。
- `ActorContext.accountId` 保持非空，不为匿名访问扩散可空语义。HTTP Adapter 把 Bearer 解析为 `AccountAiOwner`，把访客 Cookie 的摘要解析为 `VisitorAiOwner`；原始 Cookie 不进入模块接口。`VisitorAiOwner` 的有效角色固定为 `VISITOR`，账户角色每轮经 IDENTITY 重新解析。会话记录的所有者类型决定后续使用哪种凭据，同时携带另一种凭据不能迁移或提升会话。
- `PageCursor(cursor?,limit)` 的 `limit` 为 1～100；所有 Page 结果为 `items,nextCursor?`。`ModuleResult.Rejected.code` 只能使用 MD-0003 已登记稳定错误码，`reasons` 保持调用方可展示顺序；错误不得携带 JPA Entity、SQL、堆栈或不可见对象数据。
- 每个命令接口保证完成全部守卫后才返回 `Accepted`；返回 `Rejected` 不产生业务状态变化。跨模块原子性由表 §3 的应用用例与 `UnitOfWork` 承担，单个模块接口不得自行提交。

`ReviewedAction` 不是任意回调。它是由 Composition Root 登记的封闭动作集合，只接受 `ACCOUNT_CHANGE`、`CIRCULATION_EXCEPTION`、`REFUND` 等已批准高风险动作及其类型化参数；执行前由目标模块重验版本和守卫。`ApprovedRefundCommand` 只能由 `ReviewedAction` 在管理员批准事务内构造，普通 Controller 不得直接创建。

## 3. 应用用例接口

HTTP Adapter 依赖按能力分组的入口，不依赖所有业务模块的合集：

| 应用入口 | 主要命令 | 依赖的模块接口 | 事务责任 |
| -------- | -------- | -------------- | -------- |
| `RegistrationUseCases` | 注册、账户维护 | `IdentityRegistration`、`AccountCommands` | 账户与读者资料整体提交 |
| `MaintenanceUseCases` | 书目维护、副本维护 | `TitleCommands`、`CopyCommands` | 单动作提交；副本由流通维护 |
| `LoanUseCases` | 预约、现场借阅、取书、续借、人工处置 | `AuthorizationCheck`、`DebtQuery`、`ReservationCommands`、`LoanCommands`、事件登记 | 提交时重验并整体提交 |
| `ReturnUseCases` | 归还 | `AuthorizationCheck`、`ReturnCommands`、`FineCommands`、事件登记 | 流通、罚款、Outbox 同事务 |
| `PaymentUseCases` | 建单、回调、关闭、退款、对账 | `AuthorizationCheck`、`OrderCommands`、`OrderQuery`、`RefundCommands`、`ReconciliationCommands`、`ReviewRequesting`、事件登记 | 每个状态迁移与事件同事务 |
| `ReviewUseCases` | 发起、决定 | `AuthorizationCheck`、`ReviewRequesting`、`ReviewDeciding`、目标 `ReviewedAction`、事件登记 | 决定、目标变化、事件整体提交 |
| `ClosingUseCases` | 闭馆、补结、开放 | `AuthorizationCheck`、`ClosingCommands`、事件登记 | 按固定业务日批次提交 |
| `QueryUseCases` | 门户、我的业务、首页统计 | `ArrivalQuery`、`MyBusinessQuery`、`PortalTitleQuery`、`NotificationQuery` | 只读，不以缓存结果判定提交 |
| `AssistantUseCases` | 会话、消息、工具循环 | AI 所有者解析、`AuthorizationCheck`、`AiSessionCommands`、`PortalTitleQuery`、已登记业务能力接口 | 账户每轮重新鉴权；访客固定 L1；草稿不形成业务事实 |

## 4. 持久化与事务内部接口

每个业务模块在自己的实现内定义持久化接口；它们是内部缝，不暴露给其他模块。PostgreSQL Adapter 与测试用 PostgreSQL Adapter 均实现同一接口，测试通过业务模块接口观察结果。涉及锁、部分唯一索引、事务隔离、`pg_trgm` 或迁移的测试必须使用真实 PostgreSQL，不提供能冒充这些语义的内存 Adapter。

| 内部接口 | 所属模块 | 必须隐藏的行为 |
| -------- | -------- | -------------- |
| `AccountStore` | IDENTITY | 账户唯一性、版本条件更新、角色与状态读取 |
| `CatalogStore` | CATALOG | 检索、书目版本条件更新、阅读内容分页 |
| `CirculationStore` | CIRCULATION | 副本/事务条件更新、FIFO、closing admission 与统一锁序 |
| `SettlementStore` | SETTLEMENT | 罚款来源唯一、订单独占、回调事件去重、终态吸收 |
| `ReviewStore` | REVIEW | 复核版本、决定与目标执行关联 |
| `AiSessionStore` | AI | 账户/访客所有者 XOR、访客摘要查询、逐事件日志、账户额度与访客全局额度 CAS、草稿关联 |
| `DeliveryStore` | DELIVERY | Outbox 扫描、消费者回执、通知与审计查询 |

应用层使用 `UnitOfWork.inTransaction(work)` 传播同一 JDBC 事务上下文。该接口只控制提交/回滚，不允许业务模块自行提交；故障注入由测试装配提供，不进入生产路由或生产 JAR。

## 5. 外部与可替换依赖

只有存在两个合理 Adapter 的依赖才建立接口：

| 接口 | 性质 | 生产 Adapter | 测试 Adapter | 约束 |
| ---- | ---- | ------------ | ------------ | ---- |
| `ModelEndpoint` | 真外部端点 | OpenAI 兼容 HTTP Adapter | 脚本化模型 Adapter | 传入工具 schema、超时和 thinking 配置；不暴露 SDK 类型 |
| `PaymentSimulatorPort` | 本项目拥有的网络进程 | 本地 HTTP Adapter | 确定性内存 Adapter | 只产生模拟结果，不能直写结算事实 |
| `BusinessClock` | 进程内可替换时间 | 系统时钟 Adapter | 固定时钟 Adapter | 所有到期、业务日与重试判定统一取时 |

缓存是查询实现细节；首版只有一个 Redis/进程内候选实现时不把缓存端口提升为模块公开接口。通知是站内事实，由 `DELIVERY` 持久化，不预设尚未批准的邮件、短信或第三方消息接口。

## 6. AI 工具登记

每个工具定义 `{name,requiredCapability,inputSchema,handler,level}`。`handler` 只能指向 `QUERY` 或表中应用用例入口；模型给出的角色、对象归属、金额、版本与判定结果一律不可信。工具调用前执行授权和额度预检，成功模型响应后扣减，失败/超时不扣减，重试是新调用。单消息轮数与额度耗尽时停止后续模型调用并记录事件。

实际 `ModelEndpoint` 配置显式采用 `thinking=disabled`（`MLS-DEC-0030`）；配置保持必填，provider-default 仅保留为不兼容回归场景。

## 修订记录

- 2026-09-17，v0.4：新增 `AiSessionOwner` 的账户/访客封闭类型，保持全局 `ActorContext.accountId` 非空；固定 HTTP Adapter 只向模块传访客摘要、会话所有者决定后续凭据、账户每轮重验与访客 L1 边界。影响需求：无；影响验收：匿名所有权和登录不提权可从模块接口测试；评审人/批准人：lingzz；结论：随 REV-0005 v1.2 批准。

- 2026-09-14，v0.3：将聚合模块接口拆为调用方能力接口，补齐稳定值类型、`ModuleResult`、命令/结果到 OpenAPI schema 的映射及错误保证；聚合接口只供 Composition Root 装配；批准人：lingzz；结论：批准。

- 2026-09-14，v0.2：固定 `ModelEndpoint` 的首版 thinking 配置并完成内部接口评审；影响需求：无；影响验收：`IT-AI-004` 按批准配置执行；批准人：lingzz；结论：批准。

- 2026-09-13，v0.1：原因：模块 spec 工作包要求调用接口、输入输出类型、持久化和外部端点明确；提出人：执行代理；影响需求：不改变需求；影响验收：接口成为单元测试表面，真实 PostgreSQL 语义继续由集成测试验证；评审人：执行代理按 DD-0004/0005 核对；批准人：待模块设计评审；结论：形成待评审内部接口，未穷举私有实现。

> 被引用文档：
>
> - [模块设计阶段入口](MLS-DOC-MD-0001.md)
> - [验证映射与测试责任](MLS-DOC-MD-0005.md)
> - [模块设计评审与基线记录](reviews/MLS-DOC-REV-0005.md)
> - [模块关系与 4+1 视图](../02-overall-design/MLS-DOC-OD-0002.md)
