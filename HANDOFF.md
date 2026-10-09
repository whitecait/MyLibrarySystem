# 当前交接记录

引用文档：

- [书目详情布局改进登记](artifacts/title-details-layout-20261008/REPORT.md)
- [书目详情布局 Issue #10](https://github.com/whitecait/Mylibrary/issues/10)
- [本轮本地修改源码提交 3a03a4d](https://github.com/whitecait/Mylibrary/commit/3a03a4da58dcd0b76ca31bdef2fc29fc53f5e91f)
- [本轮源码 CI（四项通过，Compose 失败）](https://github.com/whitecait/Mylibrary/actions/runs/37771624213)
- [现代书目与阅读演示追加记录](artifacts/modern-demo-import-20261008/REPORT.md)
- [日结自动恢复与本地运行更新](artifacts/closing-recovery-20261007/REPORT.md)
- [AI 会话闭馆协调诊断](artifacts/closing-startup-diagnosis-20261007/REPORT.md)
- [项目入口](README.md)
- [两日冲刺待办](TODO.md)
- [运行与验证说明](ENGINEERING.md)
- [工程文档入口](docs/README.md)
- [A 版实际前端接入记录](artifacts/frontend-a-implementation-20261007/REPORT.md)
- [详情与共享 AI 面板修订](artifacts/frontend-shared-modal-20261007/REPORT.md)
- [单一 AI 入口与导航修订](artifacts/frontend-single-entry-20261007/REPORT.md)
- [模态外部点击关闭修复](artifacts/frontend-backdrop-close-20261007/REPORT.md)
- [完整分类与随机图书修订](artifacts/frontend-catalog-discovery-20261007/REPORT.md)
- [页面布局清单](artifacts/frontend-layout-inventory-20261007/LAYOUT-INVENTORY.md)
- [全文书籍语料](data/book-corpus/README.md)
- [语料替换与实库验证记录](artifacts/book-corpus-import-20261007/REPORT.md)
- [现代开源图书演示集](data/book-demo/README.md)
- [现代开放获取书目元数据](data/modern-books/README.md)
- [现有交付包工具](scripts/delivery/README.md)
- [Issue #8](<https://github.com/whitecait/Mylibrary/issues/8>)
- [PR #9](<https://github.com/whitecait/Mylibrary/pull/9>)
- [推送 CI（五项已通过）](<https://github.com/whitecait/Mylibrary/actions/runs/37733077742>)
- [PR CI（五项已通过）](<https://github.com/whitecait/Mylibrary/actions/runs/37733313686>)
- [PR 合并提交 39c246b](<https://github.com/whitecait/Mylibrary/commit/39c246bc1773610a7a941dd2dc47d70e98856c75>)
- [合并后 main CI（五项已通过）](<https://github.com/whitecait/Mylibrary/actions/runs/37766613941>)
- [合并后 main CI 摘要](<artifacts/closing-recovery-20261007/evidence/remote-main-37766613941/verification-summary.md>)

## 用户已确认的阶段与目标

2026-10-07，用户确认当前阶段为**实机部署的页面微调**。用户指定两日冲刺，目标是确定可部署在**阿里云 ECS 云服务器**上的可发布版本。

当前交接只维护这一阶段所需的事实。旧 M5、M6 与 P0–P5 批次状态退出当前工作清单。旧材料的移出没有将历史失败改判为通过，也没有形成新的发布接受结论。

## 当前页面实现

- 用户已选定 A 版背景页与事务模态。实际 Vue 页面使用现有后端接口。
- 首页通过真实游标接口连续追加书目。打开模态时保持背景、筛选和滚动位置。
- 详情与 AI 为两个独立面板，共用遮罩。桌面按 2∶1 呈现，窄屏上下排列。
- 右下角与详情侧栏共用同一个助手及入口。助手展开时隐藏入口，收起后恢复。会话和未发送内容在重开后保留。
- 顶部导航为首页与个人信息。角色工作入口保留在个人信息菜单。
- 遮罩、面板间空白及助手收起后的空白可以关闭整个模态。面板内点击和跨边界拖选保留表单。

首页分类与可借状态使用完整书库动态加载的按钮陈列。当前列出 767 个分类及实际存在的“可借”状态，各组提供“全部”按钮。点击后立即筛选，完整选项保持。快捷区域从 2,001 册书库均匀随机抽取三本不同图书，书名打开现有详情模态。完整选项与随机图书共用 21 页公开目录读取，失败可独立重试，页面卸载时取消。

最近一次页面修订报告记录：47 个测试文件、304 项前端测试通过，契约、样例同步、模块边界、ESLint 与 HTTP 模式构建通过。本地预览曾更新至 `http://127.0.0.1:40424/`，六个在线文件与构建一致，健康及书目查询通过。完整分类与随机图书修订已更新同一入口，6 个在线资源与构建一致。真实 HTTP 组件检查通过 102 个分类、三本书的真实详情及分类筛选。以上为已保存的实际验证结果。

本版 Windows 原生视觉与操作复查尚未登记完成。旧账号横向溢出和馆员事件呈现反馈，需要在当前页面重新核对。本轮前端修改及新增文件已纳入源码提交 [`3a03a4d`](https://github.com/whitecait/Mylibrary/commit/3a03a4da58dcd0b76ca31bdef2fc29fc53f5e91f) 并同步 GitHub `main`。发布候选的源码、镜像和运行包身份尚未完成统一核对。

## 当前部署基础

现有 Compose 包含 PostgreSQL、后端和 Nginx 前端。源码包含 V1–V14 数据库迁移。前端宿主入口绑定 `127.0.0.1`，数据库和后端没有发布宿主端口。

现有交付工具能从干净的确切提交生成源码、镜像、校验和及运行入口。现有操作说明覆盖 Windows 本机验证。ECS 公网访问入口、目标机配置和发布候选的同版启动尚未验证，具体待办见 TODO.md。

权威开发副本为 `/home/PUBLIC_USER/src/MyLibrarySystem`。Windows 原生验证副本为 `C:\home\PUBLIC_USER\src\MyLibrarySystem`。GUI 与浏览器实测继续在 Windows 原生环境执行。两侧通过本地 remote 的 fetch 共享已提交内容；fetch 不传递未提交源码或本地忽略材料。

日结开关默认关闭；只有 `library.closing.daily-enabled=true` 的部署会在启动和午夜检查漏结。当前本地预览启用了日结，按 `Asia/Shanghai` 时区运行。所有到期批次成功后系统自动开放；失败或预算耗尽时继续隔离，由管理员后备处置。更新前备份已通过 `pg_restore --list` 检查，但没有对该备份做恢复演练。更新后核对了业务计数和核心 HTTP 读取；这些结果不能替代备份恢复演练。

## 全文书籍语料

`data/book-corpus/` 已收集 996 册全文，其中中文 406 册、英文 590 册。逐册哈希与失败清单位于 `out/manifest.json`，目录数据位于 `out/book-titles.json`，种子 SQL 位于 `out/gutenberg-seed.sql`。四册所选图书没有纯文本版。

语料使用估算价格，ISBN 与 summary 留空，出版年份取电子版发布年。原有种子 SQL 的静态检查通过。2026-10-07，用户批准先备份当前预览库，再清理验收借阅记录并保留账号。此轮实际替换已完成；summary 管理写入问题仍待处理，类 RAG 未实施。

`mls-p5-local` 于该次 Gutenberg 导入后包含 996 册书目、2,500 本副本和 79,882 页全文。当前总量见下方现代图书追加记录。旧 65 条占位书目、38 本副本和 68 条借阅已清理，关联通知、流通事件及涉及旧标识的幂等响应已移除。8 个账号、登录会话、AI 数据、31 条审计和闭馆状态保持。新书目编号为 66–1061，没有重置编号。

最终备份位于 `artifacts/book-corpus-import-20261007/private/before-final.dump`，已恢复到隔离库检查。996 册全文的数据库重拼接哈希均与原文件一致。隔离库的实际借阅、续借及完好归还通过；当前入口的健康、996 册游标分页及中英文完整阅读往返通过。误删守卫、全文损坏回滚、重复导入拒绝及六项导入前校验测试通过。

语料替换验证当时，三个原有服务为 healthy，入口为 `http://127.0.0.1:40424/`；借还链验证只在隔离库设置开馆条件。该时点的服务状态和临时资源清理已作为历史事实保留。2026-10-08 UTC 的当前闭馆状态与服务版本见本交接末尾的闭馆恢复记录。

语料目录及全文均未提交。当前目录完整保留，字段约定和导入方法见语料 README。

## 现代图书演示集与开放获取元数据

用户于同日追加两项数据需求：演示用少量现代书籍（旧版扫描参考价值低），并补充约 1000 本现代书籍的标题、摘要等元数据；少量 PDF 用于页面显示测试。产出位于根白名单外的未跟踪目录：

- `data/book-demo/`：5 册官方渠道现代图书 PDF（Hello 算法 zh／en、动手学深度学习 zh、Dive into Deep Learning en、Ray Tracing in One Weekend en），共 106 MB，逐册 SHA-256 与许可记录见 `out/manifest.json`，另有 demo-titles.json 与本地可打开的 `out/index.html`。全部来自作者／项目官方发布渠道，未使用盗版书库。
- `data/modern-books/`：经 DOAB OAI-PMH 采集的 1000 条现代开放获取图书元数据（扫描 4013 条筛得；zh 2、en 998；634 条采集记录带 ISBN 且零重复；本次导入重新校验后，633 条通过、一条不合格留空；摘要均 ≥120 字符；出版年 2014–2026；每条含许可与溯源 handle）。产物为 manifest.json、modern-oa-titles.json 与 modern-oa-seed.sql（含 summary 列、空库守卫、每册 1–3 本 SHELF_READY 副本；price_cents 按哈希估算；作者名反转为「名 姓」）。静态校验通过：1000 行 VALUES、引号配平、无 undefined。

此前 Internet Archive 古籍扫描方向仅完成选书脚本（`data/book-scans/`，未下载任何 PDF），用户改向后停用。`modern-oa-seed.sql` 仍为原有空库种子；本次使用独立追加脚本完成合并。5 册演示书目及其 PDF 提取文字已接入现有详情／阅读接口，PDF 原图与版式仍在原文件中。DOAB 中文书稀少，现代中文书元数据补充渠道未定。

2026-10-08，当前预览库已追加 1,000 条 DOAB 元数据和 5 册演示图书，总量为 2,001 册、4,510 本副本、82,605 页正文。5 册 PDF 新增 2,723 页文字，全部页号、页数和全文哈希通过实际 HTTP 检查。全部 1,005 册元数据逐字段一致，summary 已写入数据库；管理 API 的 summary 写入问题没有在此轮修复。

最终备份为 `artifacts/modern-demo-import-20261008/private/before-final.dump`，已恢复到隔离库验证。原有 27 张表的原有行均保持，8 个账号与 `REOPENED` 状态保持。重复执行、歧义拒绝与正文损坏回滚检查通过。阅读页面保留换行和缩进，现有 3 项阅读测试、门禁及构建通过，6 个在线资源与构建一致。来源对应与阅读入口见现代追加记录。

## 本轮整理结果

已移出 951 份旧 Markdown、2111 份历史验收／验证文本及 66 份图源。清理范围包括 PLAN-DRAFT、九份旧会话计划、十七份旧阶段计划／规格／结果、旧验收批次、历史快照、旧发布包副本、验证输出和视觉交接材料。

当前页面的六个材料目录、原型、CONTEXT.md、本地操作规则、全文语料、形式化模型和运行工具保留。现有验收工具说明引用的两份 phase3 夹具保留在原路径。运行服务与数据库没有进行变更。

旧材料与整理前文档保存在本机临时目录：`/tmp/mylibrary-document-cleanup-20261007-g7ljl8rb`。`PLAN.json`、`MOVED.json` 与 `before-documents/` 记录清理路径和原文。该目录是本机恢复副本，系统清理临时目录后不能依赖它。Git 中已提交的文档历史另由提交记录保留。

本轮维护核对已通过：80 份现存 Markdown 的本地链接无断链，文档结构检查无错误，550 份非 Markdown 文件哈希保持，997 份语料说明及文本的路径与大小保持，现有交付工具九项测试通过。

## AI 会话闭馆协调诊断（2026-10-07）

本轮实际接口连续两次返回 HTTP 409、`MLS-CIRC-014` 与“闭馆协调中，普通写入未获许可”。当前库保持 `ISOLATED` 与 `draining=true`，`2026-10-06` 批次已成功，`2026-10-07` 尚无批次。后端于 Denver 时间 2026-10-07 22:01（图书馆时区 2026-10-08 12:01）恢复启动，启动后没有补结。日结只有午夜定时入口，当前规则仍要求管理员显式重开。AI 拒绝直接来自持久化隔离，不能将其判为已有批次结算失败；停机发生在结算事务中的假设未得到证据支持。本轮没有执行补结、重开或源码修改，证据见闭馆协调诊断。

## 日结自动恢复实施与本地运行更新（2026-10-08 UTC）

固定后端提交为 `19b77e70a46e2c30c342eb7bfe8f375d59285d2b`，基线为 `3a9f142f5427b950b211d8c7607660cda3a5c3b5`。父代理完成固定提交的后端验证：250 项测试通过。形式化检查有 87 个 cases，由 7 个安全模型检查和 80 个独立探针组成；7 个模型检查覆盖 43 项安全性质。另有 4 项元数据检查单独通过。Spec 和 Standards 两条复核无阻断。逐项证据见[日结恢复报告](artifacts/closing-recovery-20261007/REPORT.md)。

更新前预览库为 `ISOLATED`、`current_date=2026-10-06`、`draining=true`。`2026-10-06` 批次成功，`2026-10-07` 批次缺失；AI 会话请求两次返回 409。更新前数据库备份大小为 168,285,949 字节，SHA-256 为 `e34fe1f922b39c04be3701fea60dfd41ce82a00a0895258266886cbc304d5f46`。目录权限为 `0700`，文件权限为 `0600`。`pg_restore --list` 检查通过；该备份没有做恢复演练。

后端镜像 `mls-backend:closing-recovery-19b77e7` 与固定提交绑定。镜像 ID 为 `sha256:2d239784bcc0f3539633bc3716ad1e6384acbf18d039c125899286d92c1e7f29`；JAR SHA-256 为 `d63bdea178b920b836b04fd39b03461dd5b97dd0ad4d998f10bb2c913e81aa85`。父代理只更新后端容器，未重建数据库或前端。旧镜像 `mls-backend:release-3a9f142f5427` 保留。私有环境值没有复制到报告。

首次启动在 Tomcat 启动前补齐 `2026-10-07`，并自动开放普通写入。数据库中 `2026-10-06` 和 `2026-10-07` 均为 `SUCCEEDED`、attempts=1；控制状态为 `REOPENED`、`current_date=2026-10-07`、version=3、`draining=false`。AI 会话创建返回 HTTP 200、`MLS-AI-S001`，没有发送外部模型消息。

第二次后端重启没有重复执行日结。两日 attempts 仍为 1，控制状态仍为 `REOPENED`、`current_date=2026-10-07`，version=4、`draining=false`。健康、首页和书目请求均返回 HTTP 200；三个服务均 healthy。更新前后数据计数保持为 8 个账号、996 册书目、2,500 本副本、79,882 页全文和 0 笔借阅。当前入口为 `http://localhost:40424/`，也可通过 `http://127.0.0.1:40424/` 访问。前端没有重建，现有 6 个运行文件与 `dist` 哈希一致。

PR [#9](https://github.com/whitecait/Mylibrary/pull/9) 已于 2026-10-08T10:54:54Z（Denver 04:54:54）以普通 merge 合入 `main`，merge commit 为 [`39c246bc1773610a7a941dd2dc47d70e98856c75`](https://github.com/whitecait/Mylibrary/commit/39c246bc1773610a7a941dd2dc47d70e98856c75)；PR head 为固定修复提交 `19b77e70a46e2c30c342eb7bfe8f375d59285d2b`。Issue [#8](https://github.com/whitecait/Mylibrary/issues/8) 于 10:54:56Z 自动关闭。原 5 个修复提交均保留，未绕过门禁或删除分支。推送 CI、PR CI 与合并后 [main CI](https://github.com/whitecait/Mylibrary/actions/runs/37766613941) 五项作业均已通过。PR 候选 formal 的 checkout 是 `a5a8c9b5457267856fb3bfd554657729e3c6d1f0`，PR head 是 `19b77e70a46e2c30c342eb7bfe8f375d59285d2b`；合并后 main formal 的 checkout 与 pr_head 均为 `39c246bc1773610a7a941dd2dc47d70e98856c75`。远程 `main` 与已刷新 `origin/main` 均为 `39c246bc1773610a7a941dd2dc47d70e98856c75`；`19b77e70a46e2c30c342eb7bfe8f375d59285d2b` 是其祖先，代码内容完全一致，`19b77e70a46e2c30c342eb7bfe8f375d59285d2b..origin/main` 只有 merge commit。PR #9 合并核对时，权威本地 `main` HEAD 保持基线 `3a9f142f5427b950b211d8c7607660cda3a5c3b5`；当时仅刷新 `origin/main`，没有移动 HEAD 或覆盖工作区，保留 102 条状态项。后续本地修改的提交与同步见下一节。运行后端镜像仍是 `mls-backend:closing-recovery-19b77e7`，其文件内容与 merge tree 一致，但合并后未重新部署。ECS 尚未更新。更新前备份尚未做恢复演练。Windows 原生 GUI 页面核对由用户完成。

## 本地修改提交与 GitHub 同步（2026-10-08）

用户授权提交本地修改并同步 GitHub。原始快照为 `a725d187e637d57961ed2a00df1acec070b297a0`，该快照没有推送。最终源码以 `39c246bc1773610a7a941dd2dc47d70e98856c75` 为基线，固定为 [`3a03a4d`](https://github.com/whitecait/Mylibrary/commit/3a03a4da58dcd0b76ca31bdef2fc29fc53f5e91f)，并正常推送到 GitHub `main`。

整合时解决七处文档修订记录与历史链接冲突。整合后的提交树与原始快照完全一致，所有保存文件的 SHA-256 均一致。相对远端基线，本次提交变更 93 个文件，包含页面、模态与 AI 交互、书目发现、导入脚本与测试、文档整理和追踪规则。后端与形式模型源码已经合入基线，本次没有重复修改这些源码。

本轮 WSL 使用 Node 22.23.2 和 npm 10.9.8。47 个前端测试文件、304 项测试通过。契约、样例同步、模块边界、ESLint 与 HTTP 模式构建通过。两份导入脚本测试各通过 6 项。

文档结构检查覆盖 246 个唯一标识及 134 行追踪矩阵，错误数为零。日志与快照标识位于 `/tmp/mylibrary-local-sync-20261008/`，该目录是本机临时记录。源码推送的 CI 入口见[本轮源码 CI（四项通过，Compose 失败）](https://github.com/whitecait/Mylibrary/actions/runs/37771624213)，本段记录的测试结果来自本地执行。

源码提交的首轮远端 CI 中，前端、后端、观测和形式验证通过，Compose 失败。失败来自应用包检查仍要求旧英文标题 `MyLibrarySystem`。当前页面已显示中文名称，实际入口包完整且正常挂载 `#app`。原断言在本地实际构建产物上连续两次失败。门禁已改为检查 `#app` 挂载并拒绝 HTML 回退资源，七项 CLI 回归及实际入口包检查通过。

本轮没有更新运行中的容器、数据库或 Windows 验证副本。凭据、全文、PDF、运行数据、忽略证据和本地操作规则仍按现有追踪政策留在本机。ECS 部署、Windows 实页复核及最终发布候选仍未完成。

## 2026-10-08 书目详情布局改进登记

用户明确本轮使用本机环境，ECS 部署在本轮修订之后进行。用户指出当前书目详情缺少视觉设计，需要调整布局；AI 模态设计形成独立 PR。

[Issue #10](https://github.com/whitecait/Mylibrary/issues/10) 已创建。代码工作分支为 codex/title-details-layout，基线为 0cf371e。本机原型包含 A「书籍展陈」、B「信息工作台」和 C「阅读长页」，依现有规则保留为本地忽略资产。正式产品源码没有接入原型。

原型构建与 9 项 HTTP／模块转换检查通过。本次检查时后端未运行；原型使用演示数据，表单没有真实提交。Windows 视觉比较、设计选择、产品实现、PR、独立评审、合并后 CI 及本机验收均未完成。详情改进的收口不包含 ECS 或 AI 模态交付。

实际范围与证据见[登记报告](artifacts/title-details-layout-20261008/REPORT.md)。

## 2026-10-08 书目详情 A 与线上预约实施快照

用户已选择 A，并明确本详情全部使用线上预约、取书时间只划分五块。用户指定开馆 09:00–17:00、闭馆 18:00–次日 08:00，间隔作为缓冲；午间不休馆，但 12:00–13:00 不纳入业务受理。

本轮实现划分为 09:00–10:30、10:30–12:00、13:00–14:30、14:30–16:00、16:00–17:00。正式详情已采用 A 的书目图形、标题／状态／阅读入口、元数据和线上预约层级；表单固定 ONLINE。后端和 OpenAPI 3.0.0 只接受五段新输入，历史时段仍按原值读取。没有数据库迁移。

BusinessReservationMetadata 已更新为五段受理与六类非法输入拒绝，保留历史行及取书不以时段作守卫的映射。需求、契约和验收已传导该局部修订。12 项详情／预约定向测试通过；全量回归发现的两个旧输入已校准，完整前后端检查和固定候选形式化结果分别登记。

当前工作树的完整本地结果为：47 个前端文件、305 项测试通过；契约、架构、ESLint 与 HTTP 构建通过；后端 verify 为 250 项、0 失败、0 错误、0 跳过。首次失败的两处旧输入已经校准。

本段是实施候选提交前的事实快照。PR、独立评审、远端 CI 和本机产品页面验收尚未形成完成结论。当前代码候选与实际结果由 Issue #10 及本轮登记报告关联。ECS 和 AI 模态不计入本轮交付。

## 2026-10-09 书目注释修订实施快照

已有 [Draft PR #11](https://github.com/whitecait/Mylibrary/pull/11) 对应 Issue #10，修订前 d252b6f 的五项 PR CI 与独立规范／需求评审均通过。d252b6f 的本机正式候选在 40426 运行，使用隔离数据副本；Windows 原生产品验收与合并尚未形成完成结论。

lingzz 通过本机书目 68 的四条注释要求限制书目文字图形长度、缩小完整标题、预填日期时段、将预约移入上方借阅入口，并显示模态父级返回。后续答复确认使用 Asia/Shanghai 的明天及当前／下一合法块，17:00 及以后取第一块；日历查询并入借阅页，在预约表单旁显示。

本次代码已实现详情展示与借阅办理分离。旧日历地址导向对应借阅页；预约与查询在宽面板并列、窄面板上下排列。业务模态顶部提供同书目的父级返回。预填不调用预约写接口，五段值域、ONLINE 提交与服务端守卫继续适用。AI 视觉设计没有混入修订。

当前修订的本地完整前端结果为 49 文件、325 项测试通过；契约、模块边界、ESLint 与 HTTP 构建通过。新候选的独立复审、远端 CI 及预览绑定结果由本轮报告另按实际执行登记，不复用旧提交的通过结论。需求、验收及形式化抽象边界已追加本次修订；后端与模型迁移没有改变。

## 修订记录

- 2026-10-09：登记四条页面注释、默认值与日历并列答复、已实现交互及当前前端验证；修订候选和最终产品验收另记。

- 2026-10-08：登记用户选择 A、线上渠道、开馆／午间受理区间及五段实现；同步契约、模型和定向测试事实，完整 PR 与本机验收另记。

- 2026-10-08：登记本轮书目详情布局、本机交付边界、AI 独立 PR、Issue #10 及三方案原型实际检查；设计与产品验收未完成。

- 2026-10-08：登记首次源码 CI 的四项通过及 Compose 标题断言失败。门禁改为检查应用挂载，七项回归与实际构建产物检查通过。
- 2026-10-08：登记本轮页面、书目导入工具和文档整理提交至 3a03a4d 并同步 GitHub main。304 项前端、12 项导入脚本及文档结构检查通过。ECS 部署与 Windows 实页复核仍未完成。
- 2026-10-08：登记 1,000 条现代书目与 5 册演示图书追加、ISBN 清洗、2,723 页 PDF 文字阅读、备份恢复及当前预览验证。
- 2026-10-07：登记固定提交验证、闭馆启动补结、自动开放、后端本地更新、第二次重启不重复结算及尚未完成的 ECS/GUI/远端验证。
- 2026-10-07：根据用户明确授权，登记修复分支推送、Issue #8、PR #9 与两轮 CI 全部通过；保留合并、ECS、GUI、未提交前端和备份恢复演练边界。
- 2026-10-07：登记 Issue #8 与 PR #9 正文已更新为最终 CI 结果；远程正文更新已完成。
- 2026-10-07：登记 AI 闭馆协调的实库诊断、非午夜启动未补结及当前尚未恢复的状态。
- 2026-10-07：执行用户授权的旧计划、历史报告、快照与交接材料整理，收敛当前页面与部署事实，登记阿里云 ECS 两日冲刺目标。
- 2026-10-07：登记用户批准的真实书目及全文替换、数据库备份恢复、账号保全、隔离借还链和实际预览接口验证。
- 2026-10-07：登记首页完整筛选选项、随机图书、304 项前端回归、真实目录接口检查和预览更新。
- 2026-10-07：登记现代演示集（5 册官方 PDF）与 DOAB 千册开放获取元数据产出；IA 古籍方向停用留脚本，导入与页面集成未执行。
- 2026-10-08：登记 PR #9 合并、Issue #8 自动关闭、合并后 main CI 通过；origin/main 已刷新，本地 main HEAD 与未提交工作区保持不变，运行镜像未重部署。
被引用文档：

- [日结自动恢复与本地运行更新](artifacts/closing-recovery-20261007/REPORT.md)
- [AI 会话闭馆协调诊断](artifacts/closing-startup-diagnosis-20261007/REPORT.md)
- [完整分类与随机图书修订](artifacts/frontend-catalog-discovery-20261007/REPORT.md)
- [CONTEXT.md](CONTEXT.md)
- [ENGINEERING.md](ENGINEERING.md)
- [README.md](README.md)
- [TODO.md](TODO.md)
- [artifacts/frontend-a-implementation-20261007/REPORT.md](artifacts/frontend-a-implementation-20261007/REPORT.md)
- [artifacts/frontend-backdrop-close-20261007/REPORT.md](artifacts/frontend-backdrop-close-20261007/REPORT.md)
- [artifacts/frontend-layout-frameworks-20261007/REPORT.md](artifacts/frontend-layout-frameworks-20261007/REPORT.md)
- [artifacts/frontend-layout-inventory-20261007/LAYOUT-INVENTORY.md](artifacts/frontend-layout-inventory-20261007/LAYOUT-INVENTORY.md)
- [artifacts/frontend-shared-modal-20261007/REPORT.md](artifacts/frontend-shared-modal-20261007/REPORT.md)
- [artifacts/frontend-single-entry-20261007/REPORT.md](artifacts/frontend-single-entry-20261007/REPORT.md)
- [docs/00-governance/MLS-DOC-GOV-0008.md](docs/00-governance/MLS-DOC-GOV-0008.md)
- [docs/02-overall-design/MLS-DOC-OD-0002.md](docs/02-overall-design/MLS-DOC-OD-0002.md)
- [docs/02-overall-design/MLS-DOC-OD-0003.md](docs/02-overall-design/MLS-DOC-OD-0003.md)
- [docs/02-overall-design/MLS-DOC-OD-0004.md](docs/02-overall-design/MLS-DOC-OD-0004.md)
- [docs/04-module-design/reviews/MLS-DOC-REV-0005.md](docs/04-module-design/reviews/MLS-DOC-REV-0005.md)
- [docs/README.md](docs/README.md)
- [e2e/P3-AUTOMATION.md](e2e/P3-AUTOMATION.md)
