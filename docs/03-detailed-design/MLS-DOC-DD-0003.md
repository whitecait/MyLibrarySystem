# 数据库设计与迁移

| 字段 | 值 |
| --- | --- |
| 文档编号 | `MLS-DOC-DD-0003` |
| 文档状态 | `Draft`，经 DD 评审（REV-0004） |
| 文档版本 | `v0.1` |
| 编制日期 | `2026-09-12` |
| 阶段 | M4 详细设计 |

> 引用文档：
>
> - [模块关系与 4+1 视图草图](../02-overall-design/MLS-DOC-OD-0002.md)
>
> - [模块接口契约](MLS-DOC-DD-0002.md)
> - [事务边界、并发与恢复规则](MLS-DOC-DD-0004.md)
> - [业务规则](../01-requirements/requirements/MLS-DOC-RA-0011.md)

## 1. 正式 V1 DDL（DB model frozen）

正式迁移 `library-server/src/main/resources/db/migration/V1__core_schema.sql`（Flyway 版本化迁移，随 `classpath:db/migration` 位置加载；B1 的 `R__infrastructure_probe.sql` 保留于 `db/infra-migration`）。对象清单：

| 表 | 承载 | 关键约束/索引 |
| --- | --- | --- |
| account | 账户与角色 | role CHECK（一账户一角色 BR-0031） |
| book_title | 书目 | `ix_book_title_trgm`（pg_trgm GIN，T4 检索） |
| book_copy | 副本六态 | status CHECK；`ix_book_copy_title_status` |
| borrow_transaction | 借阅事务八态 | `uq_borrow_tx_open_per_reader_title` 部分唯一（重复预约守卫）；WAITLIST FIFO 锚点 queued_at |
| fine | 罚款 | `UNIQUE(source_tx_id)`（不重复计罚 BR-0019）；`uq_fine_open_per_reader` 部分唯一（未缴罚款唯一） |
| payment_order | 订单三态行（未建单＝无行） | **`uq_payment_order_unreleased` 部分唯一索引（fine_id WHERE status='OCCUPIED'）＝建单独占的数据库层强制（BR-0047/SC-Q14/D7~D9）** |
| closing_control / closing_batch | 闭馆控制与逐日批次 | scope UNIQUE（全馆隔离）；business_date PK（原日期固定 SC-Q7） |
| review_request | 高风险复核 | 决策与目标执行同事务承载（DD-0004 §3.4） |
| outbox_event | 事务性 Outbox | `ix_outbox_unpublished`（待投递扫描） |
| idempotency_key | 幂等键 | request_key UNIQUE（BR-0042） |
| notification | 站内通知 | 未读索引携带 reservation_date（临近排序锚点） |
| audit_event | 审计事件 | 独立提交（SC-Q5/Q9 失败审计不随业务回滚） |
| ai_session / ai_message / ai_quota_usage | AI 会话、消息、额度 | model_calls 按调用计数（DEC-0021） |

所有业务表携带 `version BIGINT`（CAS 条件更新，DD-0004 §2）与 `created_at/updated_at`。扩展：`pg_trgm`（迁移内 `CREATE EXTENSION IF NOT EXISTS`，幂等）。

## 2. 迁移测试

`library-server/src/test/java/com/library/V1MigrationTest.java`（Testcontainers＋PostgreSQL 17.6 固定摘要，沿用 B1 骨架）：

1. `v1CoreSchemaAppliedOnceAndRevalidationIsNoOp`——V1 应用一次、校验通过、重复 migrate 零执行（幂等）。
2. `coreTablesAndIndexesExist`——16 表＋部分唯一索引谓词＋pg_trgm/GIN 断言。
3. `paymentOrderExclusivityAllowsSuccessorAfterRelease`——同罚款第二个 OCCUPIED 订单被 23505 拒绝（SC-Q14）；关闭提交成功（→CLOSED）后允许继任订单；支付成功（→PAID）结清罚款后无未释放占用（SC-Q15/D14 链）。
4. `duplicateOpenReservationAndDuplicateOpenFineAreRejected`——重复预约守卫与同读者未缴罚款唯一。

## 3. 执行状态（如实登记）

- 编译与既有非容器测试通过（`mvnw test-compile`、HealthControllerTest）。
- **Testcontainers 运行时执行当前环境受阻**：本 WSL 会话 Docker Desktop 未运行（`/usr/bin/docker` 符号链接悬空至 `/mnt/wsl/docker-desktop`），无本地 PostgreSQL。按用户 2026-09-12 决定并入"统一配置建立时"一次性执行：届时 `cd library-server && ./mvnw verify` 即完成取证；远端 CI（Docker 可用）亦会在推送时执行同一测试。设计产物不受阻塞影响。

## 4. 迁移治理

- V1 之后一切 DDL 变更走新版本化迁移（V2+），禁止改写 V1（校验和漂移＝CHECKSUM_MISMATCH，B1 已有负例测试）；既有数据兼容性由迁移测试先行证明。
- 枚举值与 API/文档三方一致（OD-0002 §17）；新增枚举值须同步契约与 DD-0002。

## 修订记录

- 2026-09-12，v0.1：原因：M4 出口要求全量 Flyway DDL＋迁移测试；提出人/记录人 Zcode（修补执行代理），批准依据＝用户 2026-09-12 十项清单执行指令及 DD 评审代录授权（REV-0004）；结论：V1 冻结＋四项迁移测试交付；Testcontainers 执行因本会话 Docker 不可用登记延期至统一执行时点。

> 被引用文档：
>
> - [工程文档目录](../README.md)
