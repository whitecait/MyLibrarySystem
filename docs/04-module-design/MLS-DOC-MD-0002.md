# 模块目录与依赖规则

| 字段     | 值                         |
| -------- | -------------------------- |
| 文档编号 | `MLS-DOC-MD-0002`          |
| 文档状态 | `In Review`（v0.2 历史批准保留） |
| 文档版本 | `v0.3`                     |
| 编制日期 | `2026-09-13`               |
| 阶段     | M5 模块设计收尾与单元测试  |
| 权威性质 | 已批准模块目录与依赖基线   |

> 引用文档：
>
> - [模块关系与 4+1 视图草图](../02-overall-design/MLS-DOC-OD-0002.md)
> - [模块接口契约](../03-detailed-design/MLS-DOC-DD-0002.md)
> - [数据库设计与迁移](../03-detailed-design/MLS-DOC-DD-0003.md)
> - [事务边界、并发与恢复规则](../03-detailed-design/MLS-DOC-DD-0004.md)
> - [Outbox、缓存、故障注入与看板查询](../03-detailed-design/MLS-DOC-DD-0005.md)
> - [HTTP 机器契约](contracts/http/openapi.yaml)

## 1. 适用边界

本文把已确定的状态所有权落实为稳定模块名称、目标代码位置、依赖方向和公开接口。目标位置表示实现应进入的位置，不表示当前源码已经存在；当前正式源码仍只有工程骨架、健康探针、V1～V4 迁移与机制测试。

模块是向调用者提供一个接口并隐藏实现的代码集合。每个业务模块只有一个完整接口；该接口由按调用者能力窄化的子接口组成，完整接口只允许 Composition Root 用于装配，业务调用方只依赖实际使用的子接口。HTTP Controller、PostgreSQL、模型端点与前端 HTTP 客户端是接口处的 Adapter，不是业务事实所有者。应用用例负责跨模块编排，但不复制模块状态。

## 2. 稳定模块目录

| 稳定名 | 中文名称 | 唯一维护的事实 | 后端目标位置 | 前端目标位置 | 公开接口 |
| ------ | -------- | ------------ | ------------ | ------------ | -------- |
| `IDENTITY` | 身份与读者 | 账户、唯一角色、账户生命周期、读者资料 | `library-server/src/main/java/com/library/identity/` | `library-frontend/src/modules/identity/` | 注册、会话身份、授权判断、账户与读者状态办理 |
| `CATALOG` | 书目与阅读内容 | 书目、分类、公开阅读内容关联 | `library-server/src/main/java/com/library/catalog/` | `library-frontend/src/modules/catalog/` | 检索、详情、维护、阅读内容读取 |
| `CIRCULATION` | 流通 | 副本六态、预约/候补、借阅事务、续借、归还、闭馆批次与许可 | `library-server/src/main/java/com/library/circulation/` | `library-frontend/src/modules/circulation/` | 预约、现场借阅、取书、人工处置、续借、归还、闭馆与恢复 |
| `SETTLEMENT` | 罚款与模拟结算 | 罚款、模拟支付订单、回调结果、退款与对账 | `library-server/src/main/java/com/library/settlement/` | `library-frontend/src/modules/settlement/` | 欠费快照、建单、查单、回调、关闭、退款、对账 |
| `REVIEW` | 高风险复核 | 复核申请、决定及目标执行关联 | `library-server/src/main/java/com/library/review/` | `library-frontend/src/modules/review/` | 发起复核、作出决定、查询复核记录 |
| `QUERY` | 查询组合 | 不维护权威业务状态；只组合可见快照 | `library-server/src/main/java/com/library/query/` | `library-frontend/src/modules/query/` | 首页统计、我的业务、公开可借与预约日历查询 |
| `AI` | AI 助手 | 账户/访客会话所有权、会话事件、消息、草稿、账户额度、访客全局额度与工具编排状态 | `library-server/src/main/java/com/library/ai/` | `library-frontend/src/modules/ai/` | 开始会话、发送消息、所有权校验、工具调用与入口卡片预填 |
| `DELIVERY` | 通知与审计 | 站内通知、审计事件、消费回执 | `library-server/src/main/java/com/library/delivery/` | `library-frontend/src/modules/delivery/` | 通知查询、审计查询、Outbox 消费与去重 |

应用用例放在 `library-server/src/main/java/com/library/application/`，按 `registration`、`maintenance`、`loan`、`returning`、`payment`、`reviewing`、`closing`、`querying`、`assistant` 分包；它们只依赖各模块 `contract` 包内的能力子接口与平台事务/事件能力。HTTP Adapter 位于 `com.library.web`，模块实现位于模块自己的 `internal` 包，PostgreSQL Adapter 位于各模块内部的 `internal.adapter.persistence`，装配位于 `com.library.bootstrap`。

前端的页面、模态表单与状态放在对应模块目录；共同 HTTP Adapter、认证头、信封解析和 mock 场景装配放在 `library-frontend/src/platform/`。由 `contracts/http/openapi.yaml` 生成的类型与客户端进入 `library-frontend/src/platform/contracts/generated/`，手写 Adapter 进入 `library-frontend/src/platform/http/`。页面只调用所属模块公开的前端请求接口，不手写 URL、不读取 Controller 或后端实现类型。

## 3. 依赖方向

允许的依赖方向为：

`HTTP Adapter → 应用用例 → 业务模块公开接口 → 模块实现 → 模块自有持久化 Adapter`

补充规则：

- `QUERY` 只能读取 `IDENTITY`、`CATALOG`、`CIRCULATION`、`SETTLEMENT` 的公开快照，不能办理状态变更。
- `AI` 的账户会话每轮先经 `IDENTITY` 解析当前账户角色并授权；访客会话由 HTTP Adapter 校验访客 Cookie 摘要，固定为 `VISITOR` 且只能调用公开 L1 查询。会话创建后所有者类型不可迁移；AI 草稿提交仍回到现行业务 HTTP 契约，不新增绕过校验的办理接口。
- `REVIEW` 只通过被复核模块的受控动作接口生效，不接受通用表名、字段名或任意更新参数。
- `DELIVERY` 消费事务性 Outbox；通知投递和审计记录不得反向修改流通、结算或复核状态。
- `CATALOG` 不拥有副本状态；书目维护用例新增或维护副本时调用 `CIRCULATION`。
- `CIRCULATION` 只读取 `SETTLEMENT` 的当前欠费快照，不直写罚款或订单；归还用例在同一事务内分别调用两个模块。
- 任何模块不得导入另一模块的 `internal`、`internal.adapter.persistence` 或数据库表访问类型；跨模块 import 只允许目标模块的 `contract` 包。
- `application` 只依赖能力子接口，不能依赖完整聚合接口；完整聚合接口只在 `bootstrap` 中用于装配和一致性检查。
- `web` 只依赖应用用例入口和 OpenAPI 请求/响应 Adapter；不得直接调用模块实现或持久化接口。
- 前端模块不得导入其他模块的 `internal` 路径；所有 HTTP 调用经生成客户端或 `platform/http` Adapter 发出。

## 4. 跨模块事务责任

| 用例 | 编排责任 | 同一事务内的结果 | 事务外结果 |
| ---- | -------- | ---------------- | ---------- |
| 注册 | `application.registration` | 账户与读者资料完整建立 | 无 |
| 归还 | `application.returning` | 结束在借、迁移副本、形成罚款、登记 Outbox | 通知/审计消费 |
| 支付回调 | `application.payment` | 幂等接收结果、订单与罚款合法迁移、登记 Outbox | 审计消费 |
| 复核决定 | `application.reviewing` | 复核决定、目标受控动作、关联事件整体提交或回滚 | 失败审计按 DD-0004 独立记录 |
| 闭馆 | `application.closing` | 每个固定业务日批次的清点、结算、Outbox | 至少一次投递 |

事务由应用用例开启并向模块调用传播；模块不得另开连接或提前提交。PostgreSQL 锁、约束和隔离语义必须用 Testcontainers 验证，不能由内存替身证明。

## 5. 公开接口稳定性

公开接口只暴露办理命令、查询条件、结果、业务冲突和调用约束。数据库行、JPA/JDBC 对象、Controller DTO、缓存键、锁语句与重试实现属于模块内部。删除任一业务模块时，其规则复杂度会重新散落到多个用例与测试中，说明这些模块承担了实际行为，不是转发层。

物理依赖规则由 `ARCH-BE-001` 与 `ARCH-FE-001` 执行：后端扫描 Java package/import，前端扫描模块 import 和 HTTP 调用位置。规则在业务模块进入源码时即为构建门禁，不以代码评审代替。OpenAPI 是 HTTP 字段、必填性、枚举、路径参数和成功状态的唯一机器源；Markdown 保留权限、业务语义、事务、幂等和错误条件。两者冲突时停止实现并修订基线，不由任一侧自行选择。

## 修订记录

- 2026-09-17，v0.3：补 AI 模块对账户/访客二选一所有权、访客全局额度和 HTTP Cookie 适配责任；IDENTITY 的全局 `ActorContext` 不改为可空。影响需求：无；影响验收：增加匿名所有权边界；评审人/批准人：lingzz 待 REV-0005 v1.2 复核；结论：In Review。

- 2026-09-14，v0.2：将完整模块接口拆为调用方能力子接口，固定 `contract/internal/bootstrap` 物理位置、前端生成客户端位置和自动依赖检查责任；批准人：lingzz；结论：批准。

- 2026-09-14，不变：模块名称、代码位置、状态所有权、依赖方向和公开边界完成评审；批准人：lingzz；结论：批准。

- 2026-09-13，v0.1：原因：9 月 14—15 日模块 spec 工作包要求明确稳定名称、位置、职责、依赖和公开边界；提出人：执行代理；影响需求：不改变需求，仅分配全部已批准功能的实现责任；影响验收：为单元与集成测试定位责任；评审人：执行代理完成一致性检查；批准人：待模块设计评审；结论：形成待评审目录，不因创建文档而冻结正式基线。

> 被引用文档：
>
> - [模块设计阶段入口](MLS-DOC-MD-0001.md)
> - [对外契约与共同样例](MLS-DOC-MD-0003.md)
> - [内部接口与适配缝](MLS-DOC-MD-0004.md)
> - [验证映射与测试责任](MLS-DOC-MD-0005.md)
> - [模块设计评审与基线记录](reviews/MLS-DOC-REV-0005.md)
> - [模块关系与 4+1 视图](../02-overall-design/MLS-DOC-OD-0002.md)
