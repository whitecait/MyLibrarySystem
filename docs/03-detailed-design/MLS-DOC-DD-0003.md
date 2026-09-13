# 数据库设计与迁移

| 字段     | 值                              |
| -------- | ------------------------------- |
| 文档编号 | `MLS-DOC-DD-0003`               |
| 文档状态 | `Draft`，经 DD 评审（REV-0004） |
| 文档版本 | `v0.3`                          |
| 编制日期 | `2026-09-12`                    |
| 阶段     | M4 详细设计                     |

> 引用文档：
>
> - [后续实证审计与人工清单](../../HANDOFF-AUDIT-20260912.md)
> - [模块关系与 4+1 视图草图](../02-overall-design/MLS-DOC-OD-0002.md)
> - [模块接口契约](MLS-DOC-DD-0002.md)
> - [事务边界、并发与恢复规则](MLS-DOC-DD-0004.md)
> - [业务规则](../01-requirements/requirements/MLS-DOC-RA-0011.md)

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
| ai_session / ai_message / ai_quota_usage | AI 会话、消息、额度        | model_calls 按调用计数（DEC-0021）                                                                                                |

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

## 3. 执行状态（2026-09-12 后续审计）

Docker Desktop 29.7.2 已可用。本轮在真实 PostgreSQL 17.6 上复跑：原 V1 因 `current_date` 关键字列名语法错误失败；为该列加双引号后迁移成功，多笔欠费回归暴露原唯一约束错误；V2 移除该限制并保留归还来源唯一约束。实际运行结果与快照见根目录 `HANDOFF-AUDIT-20260912.md`。

DD-0006 是场景规格，业务集成实现仍属于 M5，不能称为已编译或已执行。迁移测试通过不证明支付已结清业务守卫、完整流通或严格 T 截止正确。

## 4. 迁移治理

- 本轮唯一 V1 原地修复是将不能在 PostgreSQL 执行的关键字列名加引号；原字节保存在 e6a187f 与本轮输入快照。原版本本次迁移在事务中失败，未成功落库；不自动 repair 或修改已有环境校验和。已有成功 V1 的环境先核对版本及校验和，不能直接套用本修复。
- 业务约束修订在 `V2__allow_multiple_unpaid_fines.sql`，V1 之后一切 DDL 变更走新版本化迁移（V2+），禁止改写 V1（校验和漂移＝CHECKSUM_MISMATCH，B1 已有负例测试）；既有数据兼容性由迁移测试先行证明。
- 枚举值与 API/文档三方一致（OD-0002 §17）；新增枚举值须同步契约与 DD-0002。

## 修订记录

- 2026-09-12，v0.3（WIN-T）：增加 V4 admission 意图以修复行锁等待期间仍可新发许可的问题；执行授权 lingzz 继续编码准备，Codex 核对，整包批准未代录。影响 DD-0004/LogicalClosingGateTest，无新增固定墙钟保证。

- 2026-09-12，v0.2（AUD-20260912）：原因：后续真实审计发现；提出人/评审人：Codex；执行授权：lingzz 本轮补齐指令；影响需求：沿用现行规则，不新增业务授权；影响验收：真实迁移与多笔罚款、保留数据升级验证；批准人：本轮修订候选未新增代录批准；结论：修订完成，受影响评审结论待重新核对，历史批准保留。

- 2026-09-12，v0.1：原因：M4 出口要求全量 Flyway DDL＋迁移测试；提出人/记录人 Zcode（修补执行代理），批准依据＝用户 2026-09-12 十项清单执行指令及 DD 评审代录授权（REV-0004）；结论：V1 冻结＋四项迁移测试交付；Testcontainers 执行因本会话 Docker 不可用登记延期至统一执行时点。

> 被引用文档：
>
> - [工程文档目录](../README.md)
