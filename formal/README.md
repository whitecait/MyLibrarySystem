# 当前限定交付的形式化门禁

## 引用文档

- [内部接口与实现对应](../docs/04-module-design/MLS-DOC-MD-0004.md)。

- [限定范围与迭代规则](../docs/00-governance/MLS-DOC-GOV-0008.md)。
- [总体设计](../docs/02-overall-design/MLS-DOC-OD-0002.md)。
- [场景因果与交互](../docs/02-overall-design/MLS-DOC-OD-0003.md)。
- [验收适用清单](../docs/01-requirements/acceptance/MLS-DOC-AT-0002.md)。
- [验证映射与责任](../docs/04-module-design/MLS-DOC-MD-0005.md)。
- [固定工具版本及校验和](toolchain.json)。
- [检查性质清单](suite.json)。

本目录是可由干净 Git 检出执行的工程验证资产，不取代规范文档。原 verification/ 模型、工具与历史输出仍是本地保全资产；此处的模型从 2026-09-12 输入复制后按当前批准规则修订，旧结果不自动成为本轮结果。

## 执行与判定

需要 Python 3.12 和 Java 21；Linux CI 执行：

```bash
python3 formal/verify.py --output-dir artifacts/formal-new-run
```

工具从 toolchain.json 固定的官方 v1.7.4 发布下载，SHA-256 必须一致；工具缓存、输出和 Python 缓存不入库。可用 --tool-jar 指定同哈希的本地副本。每次选用空输出目录，禁止混合旧结果。

执行入口包含当前 OD 结构覆盖检查、判定表枚举、真实 TLC 门禁自检、两组安全不变量和 22 个单独的可达性探针。安全检查必须完整结束且没有错误；每个探针只在指定取反不变量产生预期违例与状态轨迹时通过。语法错误、工具错误、未达到出口、错误性质、缺轨迹、校验和不符和超时均失败，退出码非零。真实不变量不与探针混跑。

result.json 记录实际检出提交、PR head 提交、工作树是否存在未提交差异、工具与环境版本、输入哈希、命令、退出码和结果；每例保存实际模型、配置与原始 TLC 日志。PR 作业检出的是 GitHub 生成的合并候选，其 checkout_commit 与 pr_head_commit 分别保存，不能把合并候选 SHA 当作 PR head。CI 工件保留 14 天；发布前须另封存对应结果。

有限模型终态没有后继动作，故明确使用 -deadlock 关闭死锁报错。所有模型单 worker 执行，边界仅为各 cfg 的有限常量。不声明活性、无限状态、全系统形式化或真实数据库提交已被模型证明。

## 模型与实现的明确对应

| 当前性质／批准入口 | 模型检查 | 真实实现验证与抽象边界 |
| --- | --- | --- |
| 日结排空、隔离冻结、连续日期推进、失败重试、管理员重开；GOV-0008 §3、AC-0050／AT-0031 | Closing：TypeOK、日期顺序、隔离期冻结、旧许可排空、追平与显式开放守卫；普通轮和补结轮等待／开放及失败出口探针 | ClosingModuleTest、ITCirculationCoreTest、contract/closing 的真实 PostgreSQL 测试。批次事务抽象成单步，最多两个日期、一次自动重试、两次恢复后写入；不证明墙钟准点或数据库锁实现 |
| 罚款订单独占、关闭释放原子性、支付终态、重复回调、关闭后迟到成功回调记录冲突；AC-0051／AT-0032 | OrderOccupancy：类型、至多一未释放订单、占用一致性、已支付结清；14 个出口探针 | SettlementModuleTest、ITCirculationCoreTest、DEFM6008PaymentSimulatorRoleMatrixTest。抽象为一笔罚款、三个订单、类型化事件；冲突日志抽象为 tags，不代表真实审计写入或重复事件持久化已被证明 |
| 注册、每日开馆、上架与最早外借日期、RESTORE 例外 | 这两组共享模型不直接覆盖这些独立规则 | RegistrationContractProbeTest、P1CoreCirculationRulesTest、V13MigrationTest；这是覆盖边界，不能以共享模型通过代替这些测试或未来新增能力的专属映射 |
| 读者对象归属、订单关闭角色、同键降权重放、AI 草稿确认与权限 | 模型动作只抽象已通过入口鉴权的合法业务事件；Closing 的 adminAuth 是当前命令权限与追平的合取，不证明动态权限存储 | SettlementControllerTest、ITID002PermissionAndInvisibilityTest、前端 guard／草稿测试及 Windows 核心冒烟；真实鉴权与重放守卫仍需对应结果 |
| 通知事实、已读归属、审计、看板及原三组混合验收 | 不声明形式化覆盖 | ITDefM6012NotificationChainTest、ITDefM6011AuditTrailTest 及 AT-0002 逐项执行状态。原完整统计、时间轴、Banner 堆叠与未验证的保留呈现按适用清单分别处理 |
| 交付停写备份与新项目恢复 | Closing 的隔离／追平思想仅作为关联抽象，不是运维脚本的证明 | delivery/manage.py 的故障拒绝测试与独立 Docker 操作验证；Windows 原生运行、恢复数据及核心写入分别取证 |

2026-10-02 独立审计整改保留原业务 HTTP 形状和 V1–V13：书目更新仅修正既有字段的列绑定；通知仍使用原数值游标，由所属模块按该读者锚点的日期／创建时间／ID 定位；QUERY 的既有只读 SQL 挪归数据所有者，通过公开窄快照组合，书名按批量 CATALOG 快照取得。这些修复不新增业务状态转换；当前两模型抽象保持，新增 ITReleaseAuditRegressionTest 用真实 PG 验证字段、分页、已读游标、归属和组合结果。该回归不表示原完整统计或未验证页面呈现已实现。当前代码与旧 P4 性能输入不同，原结果按原候选保留，不声明新 QUERY 性能通过。

新增功能必须增加明确映射，必要时新增或修订模型，执行相应安全、需求规定的活性及共享回归。现有入口不会自动证明未映射的新功能，也不能仅复跑旧模型满足新增功能出口。

2026-10-02 增量复审修正 QUERY 混合分页同号遗漏并统一公开快照领域值／ModuleResult。这些只读投影不引入共享模型中的状态迁移，仍以 ITReleaseAuditRegressionTest 真实 PG 覆盖；同号复合游标、旧游标和错误游标分别验证，形式化执行结果按新固定提交重新绑定。

## 修订记录

- 2026-10-02：追加增量复审的领域契约与混合分页映射；未扩大形式化证明范围。

- 2026-10-02：追加五项独立审计整改的模型影响和真实 PG 映射，保留原性能与页面证据边界。

- 2026-10-01：按用户批准计划建立可追踪门禁；日结普通轮改为显式重开，补充关闭后回调冲突路径；明确模型、真实验证和未覆盖边界。实现及本地结果与远端 CI、审计、P5 分别登记。

## 被引用信息

- [工程验证入口](../ENGINEERING.md)。
- [交付运维入口](../scripts/delivery/README.md)。

- [本轮实际执行报告](../artifacts/release-gates-20261001/REPORT.md)。
