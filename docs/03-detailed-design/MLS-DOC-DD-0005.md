# Outbox、缓存、故障注入与看板查询

| 字段     | 值                              |
| -------- | ------------------------------- |
| 文档编号 | `MLS-DOC-DD-0005`               |
| 文档状态 | `Approved`                      |
| 文档版本 | `v0.2`                          |
| 编制日期 | `2026-09-12`                    |
| 阶段     | M4 详细设计                     |

> 引用文档：
>
> - [后续实证审计与人工清单](../../HANDOFF-AUDIT-20260912.md)
> - [模块关系与 4+1 视图草图](../02-overall-design/MLS-DOC-OD-0002.md)
> - [数据库设计与迁移](MLS-DOC-DD-0003.md)
> - [非功能需求](../01-requirements/requirements/MLS-DOC-RA-0013.md)

## 1. Outbox 分发

- 表 `outbox_event`（DD-0003）；分发器单实例轮询 `published_at IS NULL`（索引扫描），批 50 条，按 id 升序；所有必需消费者确认成功才置 `published_at`；部分成功后的重试依赖各消费者自己的持久化去重记录；失败 `publish_attempts+1`，退避 `min(2^n,300)s`。
- **至少一次＋消费幂等**：通知/审计消费者以 `(consumer_id, event_id)` 幂等去重，其中 event_id 是稳定的 outbox_event.id；同一聚合的两次同类型合法事件具有不同 event_id，不可相互吞掉。消费记录与对应业务效果同事务提交，重复投递先检查持久化消费记录（V3 的 event_consumer_receipt）（BR-0042 事件侧）；重复投递不重复产生业务通知（AT-0027 口径）。
- 并行度：单分发器（避免多实例竞争；多实例演进预留 `FOR UPDATE SKIP LOCKED`，首版不启用）；与业务同事务写入，崩溃恢复后未提交事件自然消失（DD-0004 §1）。

## 2. 缓存规格

- 只缓存读路径：书目检索结果（键 `title:search:{q-hash}:{page}`，TTL 60s，自加载开始计时而非写回计时）、书目可借状态（`title:avail:{titleId}`，TTL 30s）、日历保证状态（`title:cal:{titleId}:{date}`，TTL 60s）。
- 失效：相应业务提交（归还/借出/闭馆结算）提交成功后失效（after-commit，不把缓存删除误称为数据库事务的一部分）；若失效失败依靠 TTL 限制陈旧，竞态回填必须核对版本/失效代数；允许陈旧上界已由 `MLS-DEC-0023`（AC-0048）签署——展示不改变提交判定（BR-0037/0038/0045）。
- 不缓存：提交判定读取、我的业务事务列表（会话敏感）。

## 3. 故障注入点（仅受控测试环境）

| 注入点            | 方式                                                        | 验证                             |
| ----------------- | ----------------------------------------------------------- | -------------------------------- |
| 业务提交中断      | 测试接口 `POST /test/inject/commit-crash`（profile `test`） | AT-0030 恢复场景、DD-0006 R 场景 |
| 支付回调迟到/重复 | 模拟器测试面板延迟/重发开关                                 | AT-0032、D5/D15                  |
| 闭馆批次失败      | `POST /test/inject/closing-fail`（批次指定）                | AT-0031、C 场景                  |
| 锁等待超时        | 测试环境 `lock_timeout=200ms`＋并发脚本                     | AT-0030 竞争场景                 |
| Outbox 投递失败   | 消费端 `POST /test/inject/outbox-fail`                      | R5 风险验证                      |

`@Profile("test")` 只控制运行时 Bean 装配，不是编译期排除。注入门面必须仅置于测试源码/测试夹具，生产打包不得包含；同时检查生产 JAR 内容和路由表。测试场景中的挂起、失败与进程终止是三种不同注入，不能用同一 HTTP 成功响应冒充进程崩溃。

## 4. 看板查询（SLI→PromQL 锚点）

对应 C1 测量链（Micrometer/Prometheus 已建）与 SLI 字典（`MLS-DEC-0023`）：

| SLI                           | PromQL 锚点                                                                            |
| ----------------------------- | -------------------------------------------------------------------------------------- | ----------- | ------- | ---------- | ------------------------------------------------------------------------------------------------------------------ | ----------- | ------- | ---------- | --------------------------- |
| API 可用性/延迟 p95/p99       | `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le))` |
| 借阅/预约/归还/支付用例成功率 | `sum(rate(http_server_requests_seconds_count{uri=~"/api/v1/(reservations               | pickups/.\* | returns | orders/.\* | payment-callbacks)",status=~"2.."}[5m])) / sum(rate(http_server_requests_seconds_count{uri=~"/api/v1/(reservations | pickups/.\* | returns | orders/.\* | payment-callbacks)"}[5m]))` |
| Outbox 积压                   | `library_outbox_unpublished`（Gauge，分发器暴露）                                      |
| 闭馆批次状态                  | `library_closing_batch_status{status=...}`                                             |
| DB 连接池饱和                 | `hikaricp_connections_active / hikaricp_connections_max`                               |
| AI 调用/额度拦截              | `library_ai_model_calls_total`、`library_ai_quota_blocked_total`                       |

告警条件随 M6 NFR 执行定参（资源饱和阈值已由 `MLS-DEC-0025` 登记）；Grafana 实页取证仍属统一人工操作时间点。

## 修订记录

- 2026-09-14，不变：消费去重、缓存、故障注入与看板查询规格完成受影响复审；批准人：lingzz；结论：批准。Grafana 实页取证仍属于实现与验收证据，不影响本规格批准。

- 2026-09-12，v0.2（AUD-20260912）：原因：后续真实审计发现；提出人/评审人：Codex；执行授权：lingzz 本轮补齐指令；影响需求：沿用现行规则，不新增业务授权；影响验收：事件身份去重、故障注入打包边界及可解析 PromQL；批准人：本轮修订候选未新增代录批准；结论：修订完成，受影响评审结论待重新核对，历史批准保留。

- 2026-09-12，v0.1：原因：M4 出口要求缓存与观测规格、故障注入点和看板查询定义；提出人/记录人 Zcode（修补执行代理），批准依据＝用户 2026-09-12 十项清单执行指令及 DD 评审代录授权（REV-0004）；结论：Outbox/缓存/注入点/查询冻结为首版实现规格。

> 被引用文档：
>
> - [工程文档目录](../README.md)
