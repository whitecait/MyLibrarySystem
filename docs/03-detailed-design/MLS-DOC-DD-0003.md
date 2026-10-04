# 数据库设计与迁移

| 字段     | 值                              |
| -------- | ------------------------------- |
| 文档编号 | `MLS-DOC-DD-0003`               |
| 文档状态 | `Approved`                      |
| 文档版本 | `v0.6`                          |
| 编制日期 | `2026-09-12`                    |
| 阶段     | M4 详细设计                     |

> 引用文档：
>
> - [限定范围交付与 DevOps 阶段规则](../00-governance/MLS-DOC-GOV-0008.md)
>
> - 后续实证审计与人工清单（历史：`git show 7eedf0b:HANDOFF-AUDIT-20260912.md`）
> - [模块关系与 4+1 视图草图](../02-overall-design/MLS-DOC-OD-0002.md)
> - [模块接口契约](MLS-DOC-DD-0002.md)
> - [事务边界、并发与恢复规则](MLS-DOC-DD-0004.md)
> - [业务规则](../01-requirements/requirements/MLS-DOC-RA-0011.md)

## 2026-10-01 当前适用范围与批准修订

lingzz 于 2026-10-01 明确批准 A–E，并追加新增功能 TLA 形式化回归、CI 验证及完整 PR 流程要求。本节与[限定范围交付与 DevOps 阶段规则](../00-governance/MLS-DOC-GOV-0008.md)共同限定当前适用内容；下文相冲突的范围、角色、状态和准入表述保留为原基线历史，其余约束继续有效。批准修订与执行验证分别登记。

原 V1 及 V2～V9 的设计/批准/执行时点保留为历史。当前交付迁移已覆盖 V1～V13：V10 保守起借日、V11 连续日结进度、V12 注册会话限额、V13 最早可借日期。V13 区分当天上架与实际外借，RESTORE 同日例外保留，歧义旧行延至次日；迁移成功及恢复续行以 P4 实际报告为证，不由本文批准推断。新迁移按 PR 的同版 TLA/CI 及真实升级验证检查，并说明与旧应用兼容、备份和恢复步骤。

## 2026-10-04 借阅事务保证类型追加记录

V14 在 `borrow_transaction` 增加可空的 `guarantee_type`，CHECK 只接受 `DIRECT_CONFIRMATION` 或 `WAITLIST`。CIRCULATION 在创建直接确认预约时写 `DIRECT_CONFIRMATION`；在创建候补事务时写 `WAITLIST`。现场借阅、人工处置等来源不据状态猜测，写入 `NULL`。CIRCULATION 的本人事务窄快照以 `GuaranteeType` 类型返回原值；业务状态变化不更新此列，候补分配为 `CONFIRMED` 后仍保留 `WAITLIST`。

V14 只从 `RESERVATION_DECIDED` 的 JSON 对象读取正向 `relatedId` 与 `outcome`。唯一且可解析的 `CONFIRMED` 映射为 `DIRECT_CONFIRMATION`；唯一且可解析的 `WAITLIST_ENQUEUED` 映射为 `WAITLIST`。重复相同事实可以回填。缺失事实、无效编号、未知结果或同一事务的冲突结果留为 `NULL`。其他事件类型不会参与回填。该规则不从当前状态或副本绑定推断创建来源。

## 1. 正式 V1 DDL（DB model frozen）

正式迁移 `library-server/src/main/resources/db/migration/V1__core_schema.sql`（Flyway 版本化迁移，随 `classpath:db/migration` 位置加载；B1 的 `R__infrastructure_probe.sql` 保留于 `db/infra-migration`）。对象清单：

| 表                                       | 承载                       | 关键约束/索引                                                                                                                     |
| ---------------------------------------- | -------------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| account                                  | 账户与角色                 | role CHECK（一账户一角色 BR-0031）                                                                                                |
| book_title                               | 书目                       | `ix_book_title_trgm`（pg_trgm GIN，T4 检索）                                                                                      |
| book_copy                                | 副本六态                   | status CHECK；`ix_book_copy_title_status`                                                                                         |
| borrow_transaction                       | 借阅事务八态               | `uq_borrow_tx_open_per_reader_title` 部分唯一（重复预约守卫）；WAITLIST FIFO 锚点 queued_at                                       |
| fine                                     | 罚款                       | `UNIQUE(source_tx_id)`（不重复计罚 BR-0019）；V2 改为 `ix_fine_due_per_reader` 非唯一；同一读者可有多笔未缴罚款                   |
| payment_order                            | 订单三态行（未建单＝无行） | **`uq_payment_order_unreleased` 部分唯一索引（fine_id WHERE status='OCCUPIED'）＝建单独占的数据库层强制（BR-0047/SC-Q14/D7~D9）** |
| closing_admission（V4）                  | 独立停止新许可的持久化意图 | scope 引用唯一全馆控制行；draining 在旧共享许可仍持有时可独立提交；初始化与 control 行共同完成                                    |
| closing_control / closing_batch          | 闭馆控制与逐日批次         | scope UNIQUE（全馆隔离）；business_date PK（原日期固定 SC-Q7）                                                                    |
| review_request                           | 高风险复核                 | 决策与目标执行同事务承载（DD-0004 §3.4）                                                                                          |
| outbox_event                             | 事务性 Outbox              | `ix_outbox_unpublished`（待投递扫描）                                                                                             |
| event_consumer_receipt（V3）             | 每消费者的事件消费回执     | PRIMARY KEY(consumer_id,event_id)，与消费效果同事务提交；保留事件引用                                                             |
| idempotency_key                          | 幂等键                     | request_key UNIQUE（BR-0042）                                                                                                     |
| notification                             | 站内通知                   | 未读索引携带 reservation_date（临近排序锚点）                                                                                     |
| audit_event                              | 审计事件                   | 独立提交（SC-Q5/Q9 失败审计不随业务回滚）                                                                                         |
| ai_session / ai_message / ai_quota_usage | AI 会话、消息、账户额度    | 会话所有者为账户或访客摘要二选一；账户 model_calls 按调用计数（DEC-0021/0031 候选）                                               |
| ai_visitor_quota_usage                   | 访客全局共享额度           | `usage_date` 唯一；全体访客共享同一业务日计数，不绑定账户或单个 Cookie                                                            |

可变核心业务行带 version 用于 CAS；Outbox、通知、审计等记录按各自实际 DDL 管理，并非全部表都有 version/updated_at。扩展：`pg_trgm`（迁移内 `CREATE EXTENSION IF NOT EXISTS`，幂等）。

## 2. 迁移测试

`library-server/src/test/java/com/library/V1MigrationTest.java`（Testcontainers＋PostgreSQL 17.6 固定摘要，沿用 B1 骨架）：

1. `v1CoreSchemaAppliedOnceAndRevalidationIsNoOp`——V1 应用一次、校验通过、重复 migrate 零执行（幂等）。
2. `coreTablesAndIndexesExist`——16 表＋部分唯一索引谓词＋pg_trgm/GIN 断言。
3. `paymentOrderExclusivityAllowsSuccessorAfterRelease`——同罚款第二个 OCCUPIED 订单被 23505 拒绝（SC-Q14）；关闭提交成功（→CLOSED）后允许继任订单；支付成功（→PAID）结清罚款后无未释放占用（SC-Q15/D14 链）。
4. `duplicateReservationAndDuplicateFineForSameReturnAreRejected`——重复预约及同一归还事务重复计罚被拒绝。
5. `separateOverdueReturnsCanCreateMultipleUnpaidFinesForOneReader`——同一读者两笔归还可各自形成未缴罚款。
6. `v2UpgradePreservesExistingFineAndAllowsAnotherReturn`——V1→V2 保留已有罚款，允许第二笔归还计罚，重复迁移无变化。

7. `consumerReceiptsDistinguishEventsAndConsumers`——两个同聚合同类型事件分别可消费，两个消费者独立去重，同消费者重放同事件被拒绝；这仅验证数据库回执约束，分发器实现仍属 M5。

### 2.1 匿名 AI 目标数据约束（待追加迁移实现）

- 已成功应用的 V1 不回写。后续迁移把 `ai_session.account_id` 改为可空，新增非明文的 `visitor_owner_digest`，并用 CHECK 保证两列恰有一列非空；分别为账户和访客摘要建立所有权查询索引。原始 Cookie 不得持久化。
- `ai_quota_usage` 保持账户外键与 `(account_id,usage_date)` 唯一语义；新增 `ai_visitor_quota_usage(usage_date PRIMARY KEY,model_calls,version,updated_at)`，以单行 CAS 更新承载全体访客的当日共享池。
- 会话所有者在创建后不可从访客改为账户或反向迁移。账户角色每轮从 IDENTITY 重新解析；访客角色固定为 `VISITOR`。事件日志继续永久留存，Cookie 丢失只使访客无法再次证明所有权，不删除会话或日志。
- 追加迁移测试须覆盖账户/访客 XOR、明文不落库、同一访客多会话、两个访客竞争同一额度行、重复迁移、已有账户会话保留以及错误凭据无法通过数据库查询旁路取得会话。

## 3. 执行状态（2026-09-12 后续审计）

Docker Desktop 29.7.2 已可用。本轮在真实 PostgreSQL 17.6 上复跑：原 V1 因 `current_date` 关键字列名语法错误失败；为该列加双引号后迁移成功，多笔欠费回归暴露原唯一约束错误；V2 移除该限制并保留归还来源唯一约束。实际运行结果与快照见根目录 `HANDOFF-AUDIT-20260912.md`。

DD-0006 是场景规格，业务集成实现仍属于 M5，不能称为已编译或已执行。迁移测试通过不证明支付已结清业务守卫、完整流通或严格 T 截止正确。

## 4. 迁移治理

- 本轮唯一 V1 原地修复是将不能在 PostgreSQL 执行的关键字列名加引号；原字节保存在 e6a187f 与本轮输入快照。原版本本次迁移在事务中失败，未成功落库；不自动 repair 或修改已有环境校验和。已有成功 V1 的环境先核对版本及校验和，不能直接套用本修复。
- 业务约束修订在 `V2__allow_multiple_unpaid_fines.sql`，V1 之后一切 DDL 变更走新版本化迁移（V2+），禁止改写 V1（校验和漂移＝CHECKSUM_MISMATCH，B1 已有负例测试）；既有数据兼容性由迁移测试先行证明。
- 枚举值与 API/文档三方一致（OD-0002 §17）；新增枚举值须同步契约与 DD-0002。

## 修订记录

- 2026-10-04，v0.6：追加借阅事务创建保证类型和V14迁移规则。影响需求：落实已裁定的直接确认、候补及旧行未知语义；影响验收：支持AT-0026保证类型读取；批准人：lingzz已明确该字段语义；结论：追加字段规则与实现记录，迁移回填只采用无冲突的正向创建审计。

- 2026-10-01：v0.5，追加当前适用说明并传导 A–E 与新增功能 TLA/CI/完整 PR 要求。原因：限定交付与原完整基线存在范围及状态差异；提出人：lingzz/Codex；影响需求：当前范围和相邻角色、日期规则；影响验收：获批批次边界及迭代验证；内容评审/批准人：lingzz；结论：本轮局部修订 Approved，独立审计及未执行验证另行登记。
- 2026-09-17，v0.4：登记匿名 AI 目标数据模型：账户/访客所有者 XOR、只存访客摘要、账户额度表保持外键并新增按日唯一访客全局额度表；明确 V1 不回写，实际追加迁移与测试仍属 M5。影响需求：无；影响验收：补所有权与共享额度数据库不变量；评审人/批准人：lingzz；结论：随 REV-0004 v1.5 批准，迁移尚未实现。

- 2026-09-14，不变：V1 语法修复、V2/V3/V4 与真实 PostgreSQL 证据完成受影响复审；批准人：lingzz；结论：批准。

- 2026-09-12，v0.3（WIN-T）：增加 V4 admission 意图以修复行锁等待期间仍可新发许可的问题；执行授权 lingzz 继续编码准备，Codex 核对，整包批准未代录。影响 DD-0004/LogicalClosingGateTest，无新增固定墙钟保证。

- 2026-09-12，v0.2（AUD-20260912）：原因：后续真实审计发现；提出人/评审人：Codex；执行授权：lingzz 本轮补齐指令；影响需求：沿用现行规则，不新增业务授权；影响验收：真实迁移与多笔罚款、保留数据升级验证；批准人：本轮修订候选未新增代录批准；结论：修订完成，受影响评审结论待重新核对，历史批准保留。

- 2026-09-12，v0.1：原因：M4 出口要求全量 Flyway DDL＋迁移测试；提出人/记录人 Zcode（修补执行代理），批准依据＝用户 2026-09-12 十项清单执行指令及 DD 评审代录授权（REV-0004）；结论：V1 冻结＋四项迁移测试交付；Testcontainers 执行因本会话 Docker 不可用登记延期至统一执行时点。

> 被引用文档：
>
> - [工程文档目录](../README.md)
>
> - [本轮文档维护与独立审计入口](../../artifacts/doc-maintenance-20261001/REPORT.md)。
