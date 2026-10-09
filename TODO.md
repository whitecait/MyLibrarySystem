# 两日冲刺待办

引用文档：

- [书目详情布局改进登记](artifacts/title-details-layout-20261008/REPORT.md)
- [书目详情布局 Issue #10](https://github.com/whitecait/Mylibrary/issues/10)
- [本轮本地修改源码提交 3a03a4d](https://github.com/whitecait/Mylibrary/commit/3a03a4da58dcd0b76ca31bdef2fc29fc53f5e91f)
- [本轮源码 CI（四项通过，Compose 失败）](https://github.com/whitecait/Mylibrary/actions/runs/37771624213)
- [现代书目与阅读演示追加记录](artifacts/modern-demo-import-20261008/REPORT.md)
- [日结自动恢复与本地运行更新](artifacts/closing-recovery-20261007/REPORT.md)
- [AI 会话闭馆协调诊断](artifacts/closing-startup-diagnosis-20261007/REPORT.md)
- [项目入口](README.md)
- [当前交接记录](HANDOFF.md)
- [运行与验证说明](ENGINEERING.md)
- [当前页面验证记录](artifacts/frontend-backdrop-close-20261007/REPORT.md)
- [完整分类与随机图书修订](artifacts/frontend-catalog-discovery-20261007/REPORT.md)
- [现有交付包工具](scripts/delivery/README.md)
- [形式化验证入口](formal/README.md)
- [全文书籍语料](data/book-corpus/README.md)
- [语料替换与实库验证记录](artifacts/book-corpus-import-20261007/REPORT.md)
- [现代开源图书演示集](data/book-demo/README.md)
- [现代开放获取书目元数据](data/modern-books/README.md)
- [Issue #8](<https://github.com/whitecait/Mylibrary/issues/8>)
- [PR #9](<https://github.com/whitecait/Mylibrary/pull/9>)
- [推送 CI（五项已通过）](<https://github.com/whitecait/Mylibrary/actions/runs/37733077742>)
- [PR CI（五项已通过）](<https://github.com/whitecait/Mylibrary/actions/runs/37733313686>)
- [PR 合并提交 39c246b](<https://github.com/whitecait/Mylibrary/commit/39c246bc1773610a7a941dd2dc47d70e98856c75>)
- [合并后 main CI（五项已通过）](<https://github.com/whitecait/Mylibrary/actions/runs/37766613941>)
- [合并后 main CI 摘要](<artifacts/closing-recovery-20261007/evidence/remote-main-37766613941/verification-summary.md>)

## 已确认目标

2026-10-07，用户确认项目进入**实机部署的页面微调阶段**。两日冲刺目标是确定一个**可部署在阿里云 ECS 云服务器上的可发布版本**。

下列任务是该目标的执行分解。未勾选项尚未完成。旧批次验收状态不作为当前工作排期；当前候选的发布结论以实际结果为准。

本地预览后端仍运行固定提交 `19b77e70a46e2c30c342eb7bfe8f375d59285d2b` 的镜像。PR [#9](https://github.com/whitecait/Mylibrary/pull/9) 已以普通 merge 方式合入远程 `main`，merge commit 为 [`39c246bc1773610a7a941dd2dc47d70e98856c75`](https://github.com/whitecait/Mylibrary/commit/39c246bc1773610a7a941dd2dc47d70e98856c75)；Issue [#8](https://github.com/whitecait/Mylibrary/issues/8) 已自动关闭。推送 CI、PR CI 与合并后 main CI 五项作业均已通过。PR #9 合并核对时，`origin/main` 刷新到 `39c246bc1773610a7a941dd2dc47d70e98856c75`，权威本地 `main` HEAD 仍为基线 `3a9f142f5427b950b211d8c7607660cda3a5c3b5`，工作区保留 102 条状态项。2026-10-08，本轮页面、书目导入工具和文档整理随后固定为源码提交 `3a03a4d`，并同步 GitHub `main`。运行镜像内容与 merge tree 一致，合并后未重新部署。ECS 尚未部署，最终前端候选仍未固定。

## 本轮已完成

- [x] 提交并同步当前页面、书目导入工具和文档整理，源码为 [`3a03a4d`](https://github.com/whitecait/Mylibrary/commit/3a03a4da58dcd0b76ca31bdef2fc29fc53f5e91f)；304 项前端、12 项导入脚本测试及相关门禁通过，文档结构检查无错误。
- [x] 核对并修复 AI 持续闭馆协调：复现实际拒绝与漏结，固定提交通过后更新本地后端；启动补结成功后自动开放，第二次重启不重复结算。
- [x] 盘点 Git 未追踪及忽略文档，区分文档、历史证据和书籍语料。
- [x] 移出旧计划、历史验收报告、快照和旧交接材料，并保存本机恢复副本。
- [x] 精简 README、HANDOFF 和 TODO，更新相关入口与失效链接。
- [x] 保留当前页面材料、原型、领域文档、运行工具和全文语料。
- [x] 完成文档链接、追踪矩阵、源码保全和现有交付工具检查。
- [x] 按用户确认先备份并恢复检查，再以 996 册真实书目及全文替换当前预览库占位数据，清理验收借阅，保留 8 个账号及会话／AI／审计／闭馆数据。
- [x] 检查 79,882 页全文重拼接哈希、2,500 本副本、误删守卫、失败回滚和重复导入拒绝；隔离借还链与实际预览接口通过。

- [x] 按页面标注完成完整分类／状态按钮和三本随机图书入口；47 个文件、304 项前端测试与门禁／构建通过，真实目录接口检查通过，当前预览已更新。
- [x] 固定提交 `19b77e70a46e2c30c342eb7bfe8f375d59285d2b` 的 `./mvnw -B -ntp verify` 通过：250 项、0 失败、0 错误、0 跳过；固定 formal 结果有 87 个 cases（7 个安全模型检查＋80 个独立探针），7 个模型检查覆盖 43 项安全性质；另有 4 项元数据检查单独通过。两条 Sol xhigh 复审无阻断。
- [x] 备份更新前预览数据库，并仅更新后端容器。启动补结与自动开放成功；第二次重启没有重复结算；运行 API 和数据计数核对通过。备份未做恢复演练。
- [x] 推送闭馆修复分支并创建 Issue #8、PR #9；两轮 CI 全部通过后更新两份远程正文。PR #9 已合入 main，Issue #8 已关闭，合并后 main CI 通过。

## 第1日：页面微调与候选收敛

- [ ] 在实机核对当前 A 版首页、详情、共享 AI、事务模态和角色入口，收集可复现的具体页面反馈。
- [ ] 修正实际反馈，重点检查窄屏溢出、遮罩关闭、助手收起／重开、滚动保持、事件呈现和表单提交结果。
- [ ] 核对登录、预约／取书、借阅／续借／归还、罚款与管理入口的核心操作。确认页面反馈与真实接口结果一致。
- [ ] 对最终页面候选执行前端测试、契约／架构／ESLint 门禁和 HTTP 模式构建，记录同一候选的实际结果。
- [ ] 核对 ECS 实例的系统、架构、资源与访问入口，明确模型配置、数据持久化、账号初始化和启动方式。
- [ ] 根据实机与 ECS 验证结果固定最终发布候选，核对源码、镜像和运行包身份。当前页面与导入工具已提交，本轮同步没有形成最终发布接受结论。

## 第2日：ECS 部署验证与发布定版

- [ ] 完成适用于阿里云 ECS 的运行配置及访问入口。当前 Compose 仅绑定本机环回，公网入口与 HTTPS 尚未验证。
- [x] 从固定提交执行后端完整验证和形式化检查；结果及固定提交标识见日结恢复报告。
- [ ] 完成适用于当前候选的独立基础 Compose／跨侧集成检查；推送 CI 与 PR CI 五项作业均已通过（[推送 CI](https://github.com/whitecait/Mylibrary/actions/runs/37733077742)；[PR CI](https://github.com/whitecait/Mylibrary/actions/runs/37733313686)）。本项独立集成验收仍未登记完成。
- [ ] 从干净的同一提交构建发布包，记录镜像身份、包校验和、配置模板和部署说明。
- [ ] 在 ECS 目标机启动同一候选，核对数据库迁移、服务健康、页面资产、API 代理和真实模型配置。
- [ ] 在目标入口执行核心业务操作与权限核对，检查日结后重开、重启后的数据保持、备份与恢复后验结果。
- [ ] 整理实际部署地址、版本、验证结果、已知问题及重启／备份／恢复方式，向用户交付定版结果。

## 可发布版本的完成条件

| 条件 | 所需实际结果 |
| --- | --- |
| 版本固定 | 源码提交、镜像及发布包对应同一候选 |
| 页面可用 | 实机核心操作完成，已确认的页面阻断已处理 |
| 工程验证 | 当前候选的相关测试、构建与 CI 结果有记录 |
| ECS 可运行 | 目标机能够按说明启动，入口、迁移与核心操作通过 |
| 交接完整 | 配置、数据持久化、重启、备份、恢复及已知问题有明确说明 |

## 已记录的后续事项

- [x] 处理已定位的 AI 闭馆协调阻断。本地预览验证了启动补结、成功后自动开放和重启不重复结算。预算耗尽及外层回滚后仍隔离由 Testcontainers 真实 PostgreSQL 回归验证。更新前备份未做恢复演练；细节见日结恢复报告。
- [ ] 核对 summary 管理写入问题，记录复现与修复范围。
- [ ] 在全新空实例验证 996 册语料初始化；当前已完成既有预览库替换、备份恢复及隔离库借阅链验证。
- [ ] 核对收集脚本与语料说明的追踪范围；原子导入脚本及校验测试已加入源码追踪白名单，全文仍为本地忽略资产。类 RAG 方案尚未制定。
- [x] 追加 1,000 条 DOAB 元数据，写入摘要并按 ISBN／题名与第一作者去重；633 条 ISBN 校验通过，一条留空并记录原值。备份恢复、回滚和重复执行检查通过。
- [x] 将 5 册演示书目及 2,723 页 PDF 提取文字接入现有在线阅读；保留页号、换行与缩进，全部实际阅读接口哈希检查通过。原 PDF 图像和版式仍在本地文件中。
- [ ] 决定 `data/book-scans/`（Internet Archive 古籍选书脚本，无下载产物）去留；现代中文书元数据补充渠道（DOAB 中文稀少）未定。

## 本轮书目详情布局 PR：本机环境

2026-10-08，用户明确本轮先修订书目详情布局；ECS 部署在本轮修订之后进行。AI 模态设计形成独立 PR。本节记录本次详情改进，不扩大下列既有发布任务的完成结论。

- [x] 登记 [Issue #10](https://github.com/whitecait/Mylibrary/issues/10)，明确本机交付和 AI 独立 PR 边界。
- [x] 建立三种本地布局原型，完成构建及 HTTP／模块转换检查；证据见[登记报告](artifacts/title-details-layout-20261008/REPORT.md)。
- [x] 用户选择 A，并明确线上渠道、五块数量与开馆／午间受理区间。
- [ ] 在 Windows Chrome 验收正式产品候选，记录实际视口和结果。
- [x] 在 codex/title-details-layout 分支写入 A 布局、线上表单、五段新输入及契约／模型传导。
- [x] 完成实现工作树的前端 305 项、后端 250 项、契约／架构／ESLint 与 HTTP 构建。
- [ ] 完成固定候选的形式化检查和真实页面回归，记录版本对应。
- [ ] 建立详情页独立 PR，完成当前提交 CI、独立评审和发现处理。
- [ ] 完成 PR 合并、合并提交 CI 及对应版本的本机页面验收，按实际结果关闭 Issue #10。

## 2026-10-09 四条页面注释的实施状态

- 已实现：文字图形截断与完整标题缩小；详情的借阅入口；线上预约与日历查询在借阅页并列；旧日历地址导向借阅页；业务模态同书目父级返回。
- 已实现：按上海时区明天预填，时段取当前或下一合法块，17:00 后取首块；预填不提交预约。五段、线上渠道和候补确认规则继续适用。
- 已通过：当前工作树完整前端 49 文件、325 项测试；契约、模块边界、ESLint、HTTP 构建。
- 当前未登记完成：修订候选的独立复审与远端 CI、固定版本本机预览核对、Windows 原生视觉／操作验收、合并、合并后 CI 和 Issue #10 关闭。修订前 d252b6f 的 CI／评审只作为历史记录。
- 范围继续有效：本机交付，ECS 与 AI 模态设计分别处理。实际候选和执行结果见[书目布局报告](artifacts/title-details-layout-20261008/REPORT.md)。

## 修订记录

- 2026-10-09：登记四条页面注释、默认值与日历并列答复、已实现交互及当前前端验证；修订候选和最终产品验收另记。

- 2026-10-08：登记 A 选择与五段／线上实施，保留完整候选验证、PR 和正式页面验收待办。

- 2026-10-08：登记本轮书目详情布局、本机交付边界、AI 独立 PR、Issue #10 及三方案原型实际检查；设计与产品验收未完成。

- 2026-10-08：登记本轮页面、书目导入工具和文档整理提交至 3a03a4d 并同步 GitHub main。304 项前端、12 项导入脚本及文档结构检查通过。ECS 部署与 Windows 实页复核仍未完成。
- 2026-10-08：登记 1,000 条现代书目与 5 册演示图书追加、ISBN 清洗、2,723 页 PDF 文字阅读、备份恢复及当前预览验证。
- 2026-10-07：登记固定提交后端与形式化通过、启动补结和本地后端更新；将 AI 闭馆协调阻断改为已完成，保留 ECS、远端 CI、GUI 和备份恢复演练未完成状态。
- 2026-10-07：根据用户明确授权，登记修复分支推送、Issue #8、PR #9 与两轮 CI 全部通过；保留尚未合并、ECS、GUI 和备份恢复演练边界。
- 2026-10-07：登记 Issue #8 与 PR #9 正文更新完成；PR 合并、ECS、GUI 和备份恢复演练仍未完成。
- 2026-10-07：登记 AI 闭馆协调的实库诊断、非午夜启动未补结及当前尚未恢复的状态。
- 2026-10-07：按用户确认的阶段与两日目标重写当前待办，移出旧批次计划，将阿里云 ECS 定为部署目标。
- 2026-10-07：登记真实语料替换、备份恢复、全文完整性、隔离借还链和实际预览接口检查的完成结果。
- 2026-10-07：登记首页筛选按钮与随机图书修订、前端回归、真实接口检查和预览更新的完成结果。
- 2026-10-07：登记现代演示集与 DOAB 千册元数据产出的后续事项（合并导入方式、演示接入、book-scans 去留）；收集完成事实见 HANDOFF。
- 2026-10-08：登记 PR #9 合入远程 main、Issue #8 关闭和合并后 main CI 通过；origin/main 已刷新，本地旧 HEAD、ECS、GUI 与备份恢复边界保留。
被引用文档：

- [日结自动恢复与本地运行更新](artifacts/closing-recovery-20261007/REPORT.md)
- [AI 会话闭馆协调诊断](artifacts/closing-startup-diagnosis-20261007/REPORT.md)
- [完整分类与随机图书修订](artifacts/frontend-catalog-discovery-20261007/REPORT.md)
- [ENGINEERING.md](ENGINEERING.md)
- [HANDOFF.md](HANDOFF.md)
- [README.md](README.md)
- [artifacts/frontend-a-implementation-20261007/REPORT.md](artifacts/frontend-a-implementation-20261007/REPORT.md)
- [artifacts/frontend-backdrop-close-20261007/REPORT.md](artifacts/frontend-backdrop-close-20261007/REPORT.md)
- [artifacts/frontend-layout-frameworks-20261007/REPORT.md](artifacts/frontend-layout-frameworks-20261007/REPORT.md)
- [artifacts/frontend-layout-inventory-20261007/LAYOUT-INVENTORY.md](artifacts/frontend-layout-inventory-20261007/LAYOUT-INVENTORY.md)
- [artifacts/frontend-shared-modal-20261007/REPORT.md](artifacts/frontend-shared-modal-20261007/REPORT.md)
- [artifacts/frontend-single-entry-20261007/REPORT.md](artifacts/frontend-single-entry-20261007/REPORT.md)
- [docs/00-governance/MLS-DOC-GOV-0008.md](docs/00-governance/MLS-DOC-GOV-0008.md)
- [docs/02-overall-design/MLS-DOC-OD-0002.md](docs/02-overall-design/MLS-DOC-OD-0002.md)
- [docs/02-overall-design/MLS-DOC-OD-0003.md](docs/02-overall-design/MLS-DOC-OD-0003.md)
- [docs/04-module-design/reviews/MLS-DOC-REV-0005.md](docs/04-module-design/reviews/MLS-DOC-REV-0005.md)
- [docs/README.md](docs/README.md)
- [e2e/P3-AUTOMATION.md](e2e/P3-AUTOMATION.md)
