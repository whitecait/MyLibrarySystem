# 当前限定交付的形式化门禁

## 引用文档

- [内部接口与实现对应](../docs/04-module-design/MLS-DOC-MD-0004.md)。
- [事务边界、并发与恢复规则](../docs/03-detailed-design/MLS-DOC-DD-0004.md)。

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

执行入口包含当前 OD 结构覆盖检查、判定表枚举、真实 TLC 门禁自检、七个模型中的 43 项安全不变量和 80 个单独的可达性探针。安全检查必须完整结束且没有错误；每个探针只在指定取反不变量产生预期违例与状态轨迹时通过。语法错误、工具错误、未达到出口、错误性质、缺轨迹、校验和不符和超时均失败，退出码非零。真实不变量不与探针混跑。

result.json 记录实际检出提交、PR head 提交、工作树是否存在未提交差异、工具与环境版本、输入哈希、命令、退出码和结果；每例保存实际模型、配置与原始 TLC 日志。PR 作业检出的是 GitHub 生成的合并候选，其 checkout_commit 与 pr_head_commit 分别保存，不能把合并候选 SHA 当作 PR head。CI 工件保留 14 天；发布前须另封存对应结果。

有限模型终态没有后继动作，故明确使用 -deadlock 关闭死锁报错。所有模型单 worker 执行，边界仅为各 cfg 的有限常量。不声明活性、无限状态、全系统形式化或真实数据库提交已被模型证明。

## 模型与实现的明确对应

| 当前性质／批准入口 | 模型检查 | 真实实现验证与抽象边界 |
| --- | --- | --- |
| 日结排空、隔离冻结、连续日期推进、失败重试、自动开放及管理员后备；GOV-0008 §3、AC-0050／AT-0031 | Closing：TypeOK、日期顺序、隔离期冻结、旧许可排空、追平与受控开放守卫；普通轮和补结轮自动开放、开放失败后管理员处置及失败出口探针 | ClosingModuleTest、ITCirculationCoreTest、contract/closing 的真实 PostgreSQL 测试。批次事务抽象成单步，最多两个日期、一次自动重试、两次恢复后写入；不证明墙钟准点或数据库锁实现 |
| 罚款订单独占、关闭释放原子性、支付终态、重复回调、关闭后迟到成功回调记录冲突；AC-0051／AT-0032 | OrderOccupancy：类型、至多一未释放订单、占用一致性、已支付结清；14 个出口探针 | SettlementModuleTest、ITCirculationCoreTest、DEFM6008PaymentSimulatorRoleMatrixTest。抽象为一笔罚款、三个订单、类型化事件；冲突日志抽象为 tags，不代表真实审计写入或重复事件持久化已被证明 |
| 注册、每日开馆、上架与最早外借日期、RESTORE 例外 | 这两组共享模型不直接覆盖这些独立规则 | RegistrationContractProbeTest、P1CoreCirculationRulesTest、V13MigrationTest；这是覆盖边界，不能以共享模型通过代替这些测试或未来新增能力的专属映射 |
| 读者对象归属、订单关闭角色、同键降权重放、AI 草稿确认与权限 | 模型动作只抽象已通过入口鉴权的合法业务事件；Closing 的 adminAuth 是当前命令权限与追平的合取，不证明动态权限存储 | SettlementControllerTest、ITID002PermissionAndInvisibilityTest、前端 guard／草稿测试及 Windows 核心冒烟；真实鉴权与重放守卫仍需对应结果 |
| 通知产生、互斥与持久化归属；审计、看板及原三组混合验收 | 不声明形式化覆盖 | ITDefM6012NotificationChainTest、ITDefM6011AuditTrailTest 及 AT-0002 逐项执行状态。原完整统计、时间轴和 Banner 堆叠按适用清单分别处理 |
| AT-0026／AT-0002 §2.26 保留的三组本人业务页签、分页、完整只读详情及关联通知；完整状态时间轴移出范围 | BusinessTabs、BusinessReservationMetadata、BusinessDetail：8 个页签安全不变量和 13 个页签反取反 probe 检查分组映射：CURRENT_LOANS=(LOAN,LENT)，RESERVATIONS=(RESERVATION,AWAIT／CONFIRMED／MANUAL_HANDLING／WAITLIST)，HISTORY=(RESERVATION或LOAN,RETURNED／RETURN_ABNORMAL／NOT_BORROWED)；并检查 FINE／ORDER 排除、独立游标、最多两个在途响应下的页签／账号／请求代次竞态，以及取消后清空 RESERVATIONS／HISTORY 缓存、重置列表代次、按当前页签立即重查和丢弃旧响应。模型保留一条旧 HISTORY 或 RESERVATIONS 请求；探针证明当前 HISTORY 结果先渲染后旧 HISTORY 响应到达仍被丢弃，也覆盖当前 RESERVATIONS 立即重查和历史页惰性重查。请求代次与取消前后快照对应 MyBusinessView 的 requestId 保护。取消路径仅抽象一条 CONFIRMED 预约转 NOT_BORROWED；它不扩大完整状态时间轴。6 个元数据安全不变量和 16 个反取反 probe 检查创建来源写入、候补转 CONFIRMED 后仍保留 WAITLIST、旧行缺来源时显示未知、AM／PM 兼容、半小时首段／中间／末段样本与非法边界样本拒绝，并检查时段不进入抽象取书资格条件。5 个详情安全不变量和 10 个反取反 probe 检查本人 RESERVATION／LOAN 精确字符串 ID、同号 FINE／ORDER、通知父事务、缺失字段、外来／缺失详情及“新结果先渲染、旧路由／账号响应后到” | MyBusinessTabs.test.js、MyBusinessDetailView.test.js、ITReleaseAuditRegressionTest 提供前端请求／呈现和本人投影的对应检查。页签与详情模型把本人过滤后的业务事实、分页页内容和按读者／类型／父 ID 预关联的通知当作外部快照；元数据模型则显式抽象创建写入与候补分配迁移，但不把模型状态当成 SQL 持久化证明。时段模型仅枚举首段 00:00-00:30、中间样本 12:30-13:00、末段 23:30-24:00 和六个非法样本；它不穷举全部 48 段，也不解析任意字符串。必须由独立 Java／HTTP 测试核对全部 48 段、旧客户端输入和格式异常。模型不证明真实 SQL、HTTP 鉴权、分页游标实现、前端 DOM、浏览器呈现、通知关联持久化或活性，也不覆盖已移出的完整状态时间轴。
| 单条未读 Banner 的最近预约日期选择、关联事务点击、成功标记已读后的推进／隐藏；中心列表分页不清空 Banner 来源；会话或路由变化后丢弃旧响应。GOV-0008／AT-0002 §1.44 保留单条呈现，明确不覆盖已移出范围的堆叠卡片和“另有 N 条”计数 | NotificationNavigation：类型、单个或无 Banner、最近日期候选、Banner 来源账号、关联 ID 保持、本人业务详情类型／ID／状态；12 个独立反取反 probe 给出成功路径的可达轨迹 | AppShell.test.js、DeliveryViews.test.js、MyBusinessDetailView.test.js、ITDefM6012NotificationChainTest 和 ITReleaseAuditRegressionTest 分别覆盖呈现、前端请求边界、通知持久化关联，以及服务端本人业务投影的类型筛选／分页／同号行。模型将成功响应抽象为一步，假定通知查询和本人业务投影已经由服务端按当前读者过滤；只将相关 ID 作为路由事务 ID，再从 RESERVATION／LOAN 投影解析类型。有限样本显式含同号 FINE／ORDER、他人唯一事务和不存在事务。模型不证明 HTTP 鉴权、数据库查询／提交、DOM、浏览器、游标算法、通知生产／互斥或活性；同日通知没有规定先后 |
| 交付停写备份与新项目恢复 | Closing 的隔离／追平思想仅作为关联抽象，不是运维脚本的证明 | delivery/manage.py 的故障拒绝测试与独立 Docker 操作验证；Windows 原生运行、恢复数据及核心写入分别取证 |

2026-10-02 独立审计整改保留原业务 HTTP 形状和 V1–V13：书目更新仅修正既有字段的列绑定；通知仍使用原数值游标，由所属模块按该读者锚点的日期／创建时间／ID 定位；QUERY 的既有只读 SQL 挪归数据所有者，通过公开窄快照组合，书名按批量 CATALOG 快照取得。这些修复不新增业务状态转换；当前两模型抽象保持，新增 ITReleaseAuditRegressionTest 用真实 PG 验证字段、分页、已读游标、归属和组合结果。该回归不表示原完整统计或未验证页面呈现已实现。当前代码与旧 P4 性能输入不同，原结果按原候选保留，不声明新 QUERY 性能通过。

新增功能必须增加明确映射，必要时新增或修订模型，执行相应安全、需求规定的活性及共享回归。现有入口不会自动证明未映射的新功能，也不能仅复跑旧模型满足新增功能出口。

2026-10-02 增量复审修正 QUERY 混合分页同号遗漏并统一公开快照领域值／ModuleResult。这些只读投影不引入共享模型中的状态迁移，仍以 ITReleaseAuditRegressionTest 真实 PG 覆盖；同号复合游标、旧游标和错误游标分别验证，形式化执行结果按新固定提交重新绑定。

## 2026-10-07 启动日结恢复映射

用户批准启动自检与成功后自动开放，管理员重开为后备。Closing 的 SettleRound 将全部成功后的自动开放和开放失败后保持隔离表示为两个结果；opened 表示受控开放已经完成，不限定操作者必须为管理员。

ClosingRecovery 对应 DailyClosingScheduler 的启动检查和共用恢复流程。Start 抽象应用启动前的检查入口，CloseSuccess 按两个到期日期处理，AutomaticOpen 仅在全部成功时恢复普通写入。Stop 保留成功日期，最多两个 Start 表示一次重启。七项安全性质检查类型、顺序、既有成功日期不重复、未追平不开放、失败保持隔离、恢复后的写入条件和当前未到期日期不处理。六个探针给出启动补结、两日漏结、重启恢复、既有成功日期保留、失败隔离及跨午夜补结轨迹。两日漏结探针要求初始到期边界包含两个日期、两日最初均未成功、D1 与 D2 在本次模型中各成功一次，之后追平并开放。重启探针要求首次 Stop 时 D1 已成功、D2 尚未成功且 horizon=2；第二次 Start 必须实际成功结算 D2，并最终以两日各成功一次、追平和开放作为出口。`interruptedCatchUp` 记录符合条件的中断，`secondStartClosedD2` 记录第二次启动期间的 D2 成功。

模型的 isolated 同时抽象应用尚未就绪和持久化准入隔离，不声明两者是同一个数据库字段。Dates 最多包含两个日期。horizon 表示已到期日期边界，MidnightDuringRecovery 表示启动仍隔离时新增一个到期日期；未到期日期不能处理。writes 在每次 Start 清零，表示本启动轮的普通写入计数。失败与修复抽象为可观测结果，真实重试预算仍由 Closing 模型与数据库回归检查。该模型只枚举一次边界推进；无限日期增长、进程崩溃时数据库事务、多实例调度和 PostgreSQL 控制行锁语义没有在该有限模型中证明。

实现回归为 DailyClosingSchedulerTest 与 P1CoreCirculationRulesTest：实际 Spring 启动生命周期、真实 PostgreSQL 多日补结、重复执行、跨午夜、持久化 MANUAL 或 attempts 超预算不再自动重试、预算内 FAILED 恢复、自动开放失败、控制行锁等待后的最新范围复查、失败状态独立事务提交、结算整体回滚及 AI 普通写入准入。真实 SQL 锁与事务行为由这些 PostgreSQL 回归验证；有限模型不证明控制行锁实现。模型结果与实现结果分别记录，不作完整活性证明。

## 修订记录

- 2026-10-07：按用户批准追加启动恢复规则、模型及实现映射；补充控制行锁、锁内最新范围与真实预算由 PostgreSQL 回归覆盖，有限模型不证明数据库锁；七个模型保持 43 项安全性质和 80 个探针。

- 2026-10-02：新增通知呈现与关联事务导航的有限模型、反取反 probe 和实现映射；明确服务端读者过滤为输入假设，未扩展到通知生产、HTTP 鉴权或浏览器证明。

- 2026-10-02：追加增量复审的领域契约与混合分页映射；未扩大形式化证明范围。

- 2026-10-02：追加五项独立审计整改的模型影响和真实 PG 映射，保留原性能与页面证据边界。

- 2026-10-04：新增 AT-0026 页签、创建来源／时段和只读详情三个有限模型；补入取消后双缓存失效、当前页签立即重查及旧 requestId 丢弃轨迹；保留完整时间轴移出边界，并明确有限时段样本与 SQL／HTTP 验证限制。

- 2026-10-01：按用户批准计划建立可追踪门禁；日结普通轮改为显式重开，补充关闭后回调冲突路径；明确模型、真实验证和未覆盖边界。实现及本地结果与远端 CI、审计、P5 分别登记。

## 被引用信息

- [工程验证入口](../ENGINEERING.md)。
- [事务边界、并发与恢复规则](../docs/03-detailed-design/MLS-DOC-DD-0004.md)。
- [交付运维入口](../scripts/delivery/README.md)。

- [本轮实际执行报告](../artifacts/release-gates-20261001/REPORT.md)。
