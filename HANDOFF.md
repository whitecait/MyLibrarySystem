# 当前交接记录

> 性质：开发过程记录，不属于工程文档库。本页是已完成事实在全库的唯一登记区；唯一待办清单见 [开发任务规划](TODO.md)，需求与决策的规范性事实以 `docs/` 工件为准。
> 引用文档：

> - [本轮门禁接合实际报告](artifacts/release-gates-20261001/REPORT.md)。
>
> - [本轮文档维护与独立审计入口](artifacts/doc-maintenance-20261001/REPORT.md)。
>
> - [限定范围交付与 DevOps 阶段规则](docs/00-governance/MLS-DOC-GOV-0008.md)
>
> - [本地执行故障处理](AGENTS.md)
> - [2026-10-01 P3 修订与测试记录](artifacts/p3-revision-20261001/RUN-REPORT.md)
>
> - [新完整／增量候选身份](artifacts/p3-reader-target-20260930/candidate-seal.json)
>
> - [P3 增量自动化设计](e2e/P3-AUTOMATION.md)
>
> - [本轮实施与命令摘要](artifacts/p3-reader-target-20260930/implementation-summary.json)
> - [本轮候选镜像身份](artifacts/p3-reader-target-20260930/reader-target-images.json)
>
> - [Windows 2026-09-30 原生复验完整报告](/mnt/c/Users/PUBLIC_USER/.codex/visualizations/2026/09/30/01a0f22c-19aa-7a61-883e-0270a22fa6d3/P3-Windows-native-validation-20260930.md)
>
> - [Windows 2026-09-30 最终验收报告](/mnt/c/Users/PUBLIC_USER/.codex/visualizations/2026/09/30/01a0efee-3291-7471-be57-fecbb61cbd33/P3-Windows-final-20260930.md)
>
> - [收尾阶段 spec 索引](docs/agents/delivery-closeout-20260925/README.md)
> - [P0 范围与基线结果](docs/agents/delivery-closeout-20260925/results/P0.md)
> - [P1 裁剪与核心修复规格](docs/agents/delivery-closeout-20260925/P1-core-implementation.spec.md)
> - [P1 当前结果](docs/agents/delivery-closeout-20260925/results/P1.md)
> - [P2 最终候选自动化回归规格](docs/agents/delivery-closeout-20260925/P2-automated-regression.spec.md)
> - [P2 当前结果](docs/agents/delivery-closeout-20260925/results/P2.md)
> - [P3 Windows 集中验收规格](docs/agents/delivery-closeout-20260925/P3-windows-acceptance.spec.md)
> - [P3 当前结果](docs/agents/delivery-closeout-20260925/results/P3.md)
> - [P4 当前结果](docs/agents/delivery-closeout-20260925/results/P4.md)
> - [P4执行报告](artifacts/p4-delivery-20261001/RUN-REPORT.md)
> - [Windows 2026-09-29 完整复验报告](/mnt/c/Users/PUBLIC_USER/.codex/visualizations/2026/09/29/01a0ebd6-aa36-7f91-9e0e-d18c2bb772bf/P3-browser-followup-20260929.md)
> - [开发任务规划](TODO.md)
> - [M6 验收恢复与完成计划](docs/agents/M6-RECOVERY-PLAN-20260922.md)
> - [M6 恢复批次执行报告](artifacts/m6-recovery-20260922/RUN-REPORT.md)
> - [工程运行与验证](ENGINEERING.md)
> - [工程文档唯一入口](docs/README.md)
> - [模块设计评审与基线记录](docs/04-module-design/reviews/MLS-DOC-REV-0005.md)
> - [流通模块设计](docs/04-module-design/MLS-DOC-MD-0003.md)
> - [功能需求](docs/01-requirements/requirements/MLS-DOC-RA-0010.md)
> - [收口合并 PR #2](https://github.com/whitecait/Mylibrary/pull/2)

## 2026-10-01 今日门禁接合实现（远端与上线尚未完成）

用户明确要求实施已确认计划，决定先门禁后上线、一个完整交付 PR、独立审计代理和 Windows 本机核心冒烟。独立文档审计由用户报告完成，发现 P2 的适用歧义；AT-0002 v0.28／RA-0014 v0.23 已追加逐项清单，GOV-0008 v1.1／DEC-0033 登记本轮局部批准。原完整统计目标保留并列为上线后首个整改项，未记通过。

已实现可追踪 formal/ 门禁，修订正常日结显式重开与关闭后成功回调冲突路径；当前本地两组安全检查、22 个独立探针、失败传播自检及结构／判定表检查通过。原 verification 和 doc-maintenance-20261001 字节核对保持，首轮 TLC 配置解析失败日志保留，修复后第二轮通过。

后端 verify 208／208，前端 lint、223／223、build 通过；迁移清单拒绝测试和运维保护测试通过。独立 Linux 项目使用 P4 固定镜像完成空库、V13、重启持久性、停写备份、新卷恢复、数据库续写及已有目标拒绝，精确项目已清理。该结果不证明新包、Windows 实页或完整核心 HTTP 续行。

本地标准 Compose 首轮在容器 Maven Wrapper 下载时 SSLHandshakeException，三段未完成，失败保留。CI 原四作业保留并追加 formal；观测入口补必要 Cookie 密钥并脱敏配置证据。远端 CI、独立交付 PR 审计、合并、同版打包、Windows 真页面及 P5 尚待实际执行。证据入口为 artifacts/release-gates-20261001/REPORT.md，原 P4 包和文档审计快照未改写。

## 2026-09-25 收尾范围决策与规划交付

- 用户明确决定跳过所有文档治理性质的门禁，并将非核心业务移出本次交付；“将来有机会再实现”不构成后续实现承诺。
- 已将完整执行方案写入 [TODO.md](TODO.md)「当前待办：限定范围交付收尾计划」：包括范围、移出功能的前后端关闭要求、P0–P5 依赖顺序、S1–S9 验证、完成标准和条件估时。
- 具体功能分组属于执行拆分。用户于 2026-09-25 对本次质量范围答复原话：“性能与并发采用建议，浏览器只测试chrome，不观察计时。”据此仅测试 Windows 当前 Chrome 的保留核心链及桌面/364/430 px 页面；性能采用最终候选一轮既有缩减协议且阈值不变；并发采用同副本竞争、重复支付、关闭与回调竞争、日结与在途写入；不设置计时观察窗口。Edge/Firefox、AT-0030 九场景全覆盖、2 小时/七日可用率或稳定性不在本次结论内。P4 仍须实际运行/日志检查与恢复验证。上述决定不表示测试已执行，也不表示产品交付完成。
- 本次入口为 TODO 新收尾节；下方“未完成责任”和既有 M6 批次结果保留原范围下的历史事实，不再作为要求全量履行的当前收尾清单。原验收文档尚未按新范围重写，本次完成声明只能限定于实际交付范围。

## 2026-09-25 阶段 spec 落入本地文档库

- 按用户逐阶段交给执行代理的要求，已建立 `docs/agents/delivery-closeout-20260925/` 索引与 P0–P5 六份 spec，各含输入、步骤、完成条件与结果交接格式。执行代理一次只推进一个阶段。
- spec 和结果约定属于本地 agent 文档库，按仓库规则不加入 Git；Windows 阶段需显式复制本地文件，代码 fetch 不携带这些文件。
- 初次登记时只编写执行规格；随后 P0 已完成范围、代码入口、编号分类、环境与质量决策核验，结果见本地 `results/P0.md`。P0 未进行产品裁剪、运行测试或发布；P1–P5 尚未开始。

## 已完成事实

本节按日期登记截至 2026-09-25 已确认的完成事实。被取代的历史过程细节可依 Git 提交历史追溯。

### 需求与阶段评审

- M2 需求分析已于 2026-09-11 经 lingzz 授权签署收口，需求基线 `v1.0 Approved` 由 `MLS-DOC-REV-0002` 激活。当时产品验收尚未执行。
- M3 总体设计已交付，产物为 OD-0001 至 OD-0004；M4 详细设计已交付，产物为 DD-0001 至 DD-0006。
- `MLS-DOC-REV-0003` 与 `MLS-DOC-REV-0004` 的实证审计差额已于 2026-09-14 完成受影响复审；当前版本分别为 v1.3、v1.4，均为 `Approved`。REV-0004 v1.4 另纳入路径规范化复审。批准人：lingzz。
- 场景分析 SC-Q1 至 SC-Q15 已由用户逐项确认，结论已传导至 OD-0002、OD-0003 与需求、验收工件。
- Open 问题 `MLS-OQ-0001`～`0028` 的历史关闭依据保持有效；匿名 AI 契约冲突 `MLS-OQ-0029` 于 2026-09-17 经 lingzz 批准差异后关闭，登记册当前无 Open 项。

### 工程基础与提交链

- B1-A 最小绿色门禁与 B1-B 环境数据库验证于 2026-09-10 完成，证据为提交 `6fd8a8a` 的本地干净检出与远端三作业通过。
- 测量脚手架 C0 至 C4 于 2026-09-10 完成，证据为提交 `89bafb8` 与远端 CI run `34488923689`。
- G3/G4/G5 门禁已按授权代录签署；分支经 PR #2 合并进 main，合并提交 `c0f9251`，两轮八项 CI 全部通过。
- 2026-09-11 清理提交 `3181a9f` 固定四份旧交接文档原文，交接记录收敛至本页。
- 2026-09-12 修补轮十项清单全部完成，提交链为 `13d8a46`、`cf8e4cf`、`33eb6cf`、`a86d9e8`、`c627b19`、`6c9a333`、`c0a71bf`、`f708cbe`、`e6a187f`、`bff0f3d`。
- 2026-09-12 编码准备轮产物已提交为 `38bb7af`。

### M6 系统验收执行（Env-W 探路与修复）

- 2026-09-20 阶段 3 补齐与修复轮（Windows 侧执行，双仓库 ff 同步，提交 dd43ea8…d57f4bd + a97b051）：登出回归修复（DEF-M6-004 重开后闭环：注销请求带令牌先行、本地清理入 finally，组件测试＋实栈旧令牌 401 断言）；观察统计工具补窗口完整性/缺测判定；性能 harness 接线修复（overlay 显式钉死、k6 变量透传、幂等键与 Content-Type 注入、稳态补 audit/payment 两类负载）并经 Env-W 60 秒校准（checks 100%、零意外失败）。
- E2E 覆盖补齐并实跑全绿：E2E-001 三场景（主流程+登出会话终结、损毁归还→罚款→建单→模拟支付→PAID、账户变更复核发起/拒绝）；E2E-AI-001 五场景（访客基线、读者/馆员角色矩阵、跨访客会话所有权拒绝、L2 预填提交与取消＋L3 引导、额度触顶停发）。
- 新缺陷登记与处置：DEF-M6-005（复核发起表单页面-权限错配，2026-09-20 lingzz 裁定方向 b·表单迁移，排期下一批次实施）；DEF-M6-006（模拟支付回调未投递，Sev-2，已修复并经实栈复测闭环）；DEF-M6-007（复核三端点鉴权缺失，Sev-2，登记排期下一批次，见下文裁定落账节）。缺陷台账与阶段 3 全部证据镜像至 `artifacts/m6-acceptance/`。
- 浏览器矩阵 precondition 就绪：Chrome 153/152、Edge 147、Firefox 155 四项 Playwright 驱动验证通过；Firefox 156 与 Edge 146 按人工规程登记（`e2e/browsers/registry.json`）。
- lingzz 口头裁定（2026-09-20，过程记录）：个人开发项目，Env-O 七日观察不真实等待后虚拟签署；部署探针后持续运行、保持 devops 改进，正式签署时点由 lingzz 决定。

### 实证审计处置

2026-09-12 实证审计的编号为 AUD-01 至 AUD-11；原报告随本轮清理从工作树移除，可从 Git 历史恢复。已处置项如下：

- AUD-01：V1 迁移 `current_date` 列名加双引号，修复 PostgreSQL 42601 语法错误。
- AUD-02：V2 迁移删除超出 BR-0018 的一读者一笔 DUE 罚款唯一索引。
- AUD-04：V3 迁移建立持久化事件消费回执表。
- AUD-10：严格 T 截止旧证明被真实 PostgreSQL 反例推翻，`ClosingGateCounterexampleTest` 复现该反例；OQ-0027 据此复开。

### 编码准备验证证据

2026-09-12 编码准备轮的详细过程可从 Git 历史恢复；完成事实如下：

- 逻辑 T 已由用户选择并登记为 `MLS-DEC-0028`：计划闭馆钟点只发起排空，隔离控制成功提交才是 T；已有写许可先结束，T 可晚于计划钟点。需求、OD/DD、验收与追踪已完成传导。
- V4 迁移 `closing_admission` 排空意图已交付，修复"仅靠行锁等待仍可发放新许可"的反例。
- `mvnw verify` 最终 19 项通过、失败 0。该次复验因 WSL 无法连接 Docker Desktop socket 失败，日志存 `artifacts/coding-prep-20260912/maven-rerun-docker-unavailable.log`。
- TLC 验证：Closing 模型 410 个去重状态、订单模型 9522 个去重状态，安全不变量均无违反。
- 22 张旧 SVG 逐项 SHA-256 核对一致，旧图结论为 18 张通过、4 张退回。4 张退回图已修订，共 8 个图源变化；14 个未变图沿用旧视觉结论。
- AI 真实端点验证两组结果分别保留：provider-default 配置 5/6 通过且强制工具调用 S3 返回 400；`thinking=disabled` 配置 6/6 通过并由 `MLS-DEC-0030` 批准为首版实际模式。批准人：lingzz。
- Grafana C1 观测骨架已由用户复核通过。

### 已登记的前后端分工

四工作包于 2026-09-12 登记，输入版本绑定 git 提交 `e6a187f`：

| 工作边界           | 承担人与执行               | 目录范围                                    | 产物与完成判据                                                                                               |
| ------------------ | --------------------------- | ------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| 共同设计与契约维护 | 责任人 lingzz；执行代理 Zcode | DD-0002 契约源＋契约样例目录                  | 契约版本、示例与固定数据、状态/权限矩阵、异常场景表、变更记录；接口变更双侧同步并检查通过                       |
| frontend           | 责任人 lingzz；执行代理 Zcode | `library-frontend/`，原型除外                 | 页面与组件、交互状态、路由与权限呈现、API 适配、AI 卡片呈现；契约驱动 mock 全场景通过，lint/test/build 绿      |
| backend            | 责任人 lingzz；执行代理 Zcode | `library-server/`                            | 业务规则实现、真实鉴权、数据模型、事务/并发、任务与 Outbox、AI 执行与观测；`mvnw verify` 全绿含迁移与集成测试   |
| 阶段验收与集成协调 | 责任人 lingzz；协调 Zcode    | 跨侧                                         | 接合清单、证据、偏差及处理责任；每个跨边界场景两侧交付后执行联调                                               |

2026-09-12 编码准入核对曾对副本状态机、预约队列、支付、Outbox 四核心模块作出"可实现、可测试"达标结论。该结论基于当时的 REV-0003/0004 v1.0 批准；实证审计后不再存在无条件的编码准入，剩余门禁以 [TODO.md](TODO.md) 为准。

### 9 月 13—15 日模块 spec 候选

- 2026-09-13 建立 `MLS-DOC-MD-0002`～`0005` 与 `MLS-DOC-REV-0005 v0.1`：8 个稳定业务模块及目标代码位置、全部对外契约、内部接口与适配缝、单元/集成测试责任和模块设计评审记录均已有可评审入口。
- 功能覆盖检查将 `MLS-FR-0001`～`0047` 共 47 个唯一功能需求逐项分配给一个主责模块；REVIEW 的协作责任另列。共同 JSON 样例含 9 个跨域场景，可由前端 mock 与后端契约测试共用。
- 截至 2026-09-13，候选检查结论为材料齐备但尚未批准；该状态已由 2026-09-14 的批准结论取代。当时业务源码仍未实现，模块目标目录不得解释为当时已存在实现；后续实现事实以本页 2026-09-15～17 条目为准。

### 2026-09-14 门禁与模块基线批准

- 8 张修订图已在 Windows 原生 Chrome 完成视觉复核，8/8 通过；复核 JSON 的图源哈希与当前图源逐项一致。归档副本为 `artifacts/windows-input-20260914/revised-8-visual-review.json`，SHA-256 为 `a49246be1c7b020b46ea61e9ae9b31847ba115d2cbb1c5f30d695f042e71df29`；原下载附件 SHA-256 为 `1d75d24b6fd0ee6e318b7a5d78bfbed704eb8887de0e57e29b698cdccf1bf3d6`，两者仅换行字节规范化不同。
- `MLS-RISK-0009` 的复审责任人和剩余风险接受人为 lingzz，触发点为阶段出口前、受影响需求或设计变更后、重大验证反例后；GOV-0007 v0.7 与 OD-0002 R11 已登记。批准人：lingzz。
- 首版 AI 实际模式为 `thinking=disabled`，登记为 `MLS-DEC-0030`；provider-default 的 S3 失败保留为不兼容证据。批准人：lingzz。
- H5 受影响复审通过，REV-0003/0004 v1.3 为 Approved，OQ-0027 已关闭，M3/M4 基线恢复有效。批准人：lingzz。
- MD-0002～0005、OpenAPI、共同样例及其 Schema 完成评审，REV-0005 v1.1 为 Approved；调用方能力接口、稳定类型映射和物理依赖门禁纳入模块设计基线。批准人：lingzz。

### 2026-09-15 前端 B0：共同契约与运行骨架（frontend-implement 分支）

- 前端工作流在新建分支 `frontend-implement`（基于 `aeeb58a`）交付 G0/B0：openapi-typescript 7.13.0 与 yaml 2.9.1 精确锁定，`npm run contracts:generate` 单命令重建 `src/platform/contracts/generated/openapi.d.ts` 与 45 操作运行时清单 `operations.json`（含 basePath、权限、公开与幂等标记）。
- `CONTRACT-001`（OpenAPI 3.1 解析、内部 `$ref` 完整、45 个唯一 operationId、幂等键仅在非 GET）与 `CONTRACT-002`（生成物重建哈希一致、`/api/` URL 字面量仅限 platform/http 与 generated）经 `npm run contracts:check` 纳入默认 `lint:check` 链。
- 共同样例 `contract-scenarios.json` 逐字节同步至 `src/platform/contracts/scenarios/`，同步校验脚本核对与 docs 源一致；9 场景经 Mock Adapter 按场景选择回放，未登记操作立即失败，mock 不实现业务规则。
- `src/platform/http`：统一 `{code,message,data}` 信封解析、Bearer 认证头、31 个写操作 Idempotency-Key 自动注入、30 秒总等待上限与取消、400/401/403/404/409/422/429/500 与网络/超时/取消/信封异常的统一失败分类；`src/platform/auth`：会话状态与登录续行意图（仅 `{intent,path,params}`）。
- 应用壳、路由元数据（module/requiresAuth/roles）与守卫（未登录保存续行意图跳登录、角色不足 403 呈现）接入 vue-router 与 element-plus；`App.vue` 由静态状态页改为壳布局。
- 前端门禁全绿：`architecture:check`、`contracts:check`、`lint:check`、`test:ci`（10 文件 69 测试）、`build`；证据 `artifacts/m5-b0-frontend-run1/`。B0-GATE 的后端侧（CONTRACT-003、共同后端类型与机制）由平行后端代理负责，本批前端未涉及、未登记其状态。

### 2026-09-15 前端 B1：身份、书目与公开门户闭环（frontend-implement 分支）

- 交付 IDENTITY（7 操作）与 CATALOG（6 操作）前端闭环：登录/注册/退出、馆员账户管理（列表、高风险变更申请仅显示待复核受理号、密码重置一次性临时密码、读者状态办理）、公开门户检索（书目级 `availability` 三态、空态、加载更多）、书目详情（404 不可见独立呈现）、分页阅读器、馆员书目维护（新增/乐观锁更新/带原因下架，409 版本冲突呈现刷新提示）。
- 新增 `src/platform/http/useApi.js` 统一请求结果模型（idle/loading/success/failure + RequestFailure）；页面经 `src/modules/<模块>/index.js` 公开门面调用，视图经模块公开入口导出，路由不触碰模块 `internal`。
- 状态覆盖：401/403/404/409/422/500 与加载/空/成功态在 HTTP 与 Mock Adapter 下经组件测试断言一致；登录续行意图全链路（保存→登录→回跳）有测试。
- 前端门禁全绿：architecture:check、contracts:check、lint:check、test:ci（20 文件 111 测试）、build；证据 `artifacts/m5-b1-frontend-run1/`。后端侧同批实现由平行后端代理负责。

### 2026-09-15 前端 B2：副本、预约、借阅、续借、归还与查询闭环（frontend-implement 分支）

- 交付 CIRCULATION 11 操作与 QUERY 2 操作前端闭环：副本维护（乐观锁动作）、公开预约日历、预约面板（五种 outcome 独立呈现；候补待确认需读者显式确认，确认复送完整字段并携带新幂等键）、现场逐册借阅（混合成败逐册呈现，DESK 渠道携带 readerId）、馆员工作台（归还含罚款形成指引与异常转人工呈现、到台取书确认、人工处置）、我的业务（kind 过滤、行内续借/取消）、到馆看板。
- 状态覆盖：MLS-CIRC-006/010/011/012 等稳定错误码与 401/403/404/409/422/500 状态均在组件测试断言；书目级 availability 仅作展示的提示落在详情页与现场借阅页。
- 过程证据：架构门禁实际拦截一次跨模块 internal 导入（看板测试误置于流通模块目录），已按边界拆分，证明 ARCH-FE-001 门禁有效。
- 前端门禁全绿：architecture:check、contracts:check、lint:check、test:ci（28 文件 143 测试）、build；证据 `artifacts/m5-b2-frontend-run1/`。B4 批次将补充 runClosing/reopenBusinessDate 入口；后端侧由平行后端代理负责。

### 2026-09-15 前端 B3：支付、复核、通知与审计闭环（frontend-implement 分支）

- 交付 SETTLEMENT/REVIEW/DELIVERY 14 操作前端闭环：罚款列表与建单（并发冲突 409 MLS-PAY-001 呈现且罚款不变）、支付单查询（仅手动刷新，明示"等待超时不代表支付失败"）、失败单关闭、退款申请仅显示待复核受理号、营业日对账（计数与异常清单）、受控测试操作员模拟调度页（result/delayMs/duplicateCount）、复核列表与决定（批准前明示目标不执行；目标已变 409 MLS-REV-001 呈现未执行与重新发起提示）、通知中心（一次性已读标记、未读过滤）、审计查询（三过滤条件）。
- 契约边界：applyPaymentCallback 为模拟器凭证回调，仅保留门面无页面；requestReview 由后端高风险用例内部发起，无独立发起页。
- 前端门禁全绿：architecture:check、contracts:check、lint:check、test:ci（34 文件 163 测试）、build；期间一次 test:ci 偶发失败未复现，随后连续三次全绿。证据 `artifacts/m5-b3-frontend-run1/`。后端侧由平行后端代理负责。

### 2026-09-15 前端 B4：闭馆、恢复与隔离期呈现（frontend-implement 分支）

- 交付闭馆恢复管理员视图：重开营业日成功呈现 REOPENED 状态与新 epoch（明示旧请求不自动重放）；批次未追平（MLS-CIRC-008/013）触发"先补结再重开"独立阻断提示；其他失败走通用呈现。runClosing 为 SYSTEM 专用，仅保留契约门面无业务入口。
- 隔离期呈现：预约在隔离期收到 422 MLS-CIRC-014 明确失败（组件测试覆盖），不做无限等待呈现。
- 前端门禁全绿：architecture:check、contracts:check、lint:check、test:ci（35 文件 167 测试）、build；证据 `artifacts/m5-b4-frontend-run1/`。后端侧（逻辑 T、批次补结、恢复证据）由平行后端代理负责。

### 2026-09-15 前端 B5：AI 助手与现行业务入口接合（frontend-implement 分支）

- 交付 AI 3 操作前端闭环与现行表单接合：AI 助手视图（会话与额度呈现、消息问答、引用、事件流按需加载）、入口卡片 L1 只读/L3 引导/拒绝语义呈现；L2 仅预填当前页面模态表单——我的业务页续借模态经 AI 卡片预填（txId/草稿号），提交走现行 renewLoan 契约接口并携带 `Ai-Draft-Id` 头，取消不发任何业务请求（组件测试断言调用记录）；额度耗尽 429 MLS-AI-002 呈现耗尽态并禁用输入。
- 匿名边界：/assistant 仅登录态开放；匿名 AI 会话所有权待 OpenAPI 契约修订批准后接合，届时 CONTRACT-002 生成物漂移检查将强制前端同步。
- 前端门禁全绿：architecture:check、contracts:check、lint:check、test:ci（37 文件 180 测试）、build；证据 `artifacts/m5-b5-frontend-run1/`。后端侧（AI 会话内核、额度与工具循环）由平行后端代理负责。

### 2026-09-15 前端 M5 收口：B0～B5 全量门禁与覆盖核对（frontend-implement 分支）

- B0～B5 六个批次全部交付并逐批提交（e54e8b2 / ebe5077 / 333af0a / a751bc5 / ffda7de / 7330dfd，另有收口提交）：45/45 个 OpenAPI 操作在 identity/catalog/circulation/settlement/review/query/delivery/ai 八个模块门面接合（脚本核对无缺失、无多余）。
- 最终全量门禁全绿：architecture:check、contracts:check、lint:check、test:ci（37 文件 180 测试）、build；证据 `artifacts/m5-frontend-final/`，逐批证据在 `artifacts/m5-b0～b5-frontend-run1/`。
- 稳定化：vitest maxWorkers 限制为 8（本机与后端平行代理共享）；本日 2 次 test:ci 偶发失败均无法复现（分别 3 次、6 次连续全绿），无重复失败的测试，已在证据目录如实登记。
- 移交事项：匿名 AI 会话所有权等 OpenAPI 契约修订批准后，经 contracts:generate 重建并接合；E2E-001、E2E-AI-001 与真实浏览器验证移交 Windows 原生环境。

### 2026-09-16～17 后端 B0～B4（codex/unified-implementation 分支）

- B0 提交 `a504740` 建立共同契约与运行骨架：CONTRACT-001/003、共同类型、幂等、事务、Outbox、时钟和注册垂直切片；提交记录登记 `mvnw verify` 40/40 通过。
- B1 提交 `6254041` 交付 IDENTITY、CATALOG、Bearer 会话、授权与不可见性、V5 迁移和对应单元/集成/契约测试；提交记录登记 57/57 通过。
- B2 提交 `e36523f` 交付 CIRCULATION、QUERY、归还罚款形成、V6 迁移和核心闭环测试；提交记录登记 72/72 通过。
- B3/B4 提交 `8ff6182` 交付 SETTLEMENT、REVIEW、DELIVERY、闭馆/恢复接口、V7 迁移及对应模块测试文件。该提交未在提交说明中登记全量验证结果，本交接只登记源码与测试资产存在，不冒充批门已复跑通过。
- 后端 AI、ModelEndpoint、匿名会话所有权和访客全局额度尚未实现；当前分支不存在 `com.library.ai` 或 `application.assistant` 实现。

### 2026-09-17 匿名 AI 契约差异批准

- lingzz 明确回复“批准差异”；`MLS-DEC-0031`、DD-0002 v0.5、DD-0003 v0.4、REV-0004 v1.5、MD-0002～0005 受影响版本、OpenAPI 2.0、14 个共同样例与 REV-0005 v1.2 转为 Approved，OQ-0029 关闭。
- 批准内容固定浏览器会话级 HttpOnly 访客 Cookie、账户/访客所有权二选一、登录不迁移或提权、独立全局访客额度表、401/404 不可见性及对应测试责任；需求业务值不变。
- CONTRACT-001 与源 CONTRACT-003 六项结构检查通过。前端类型/operations/样例副本未同步，后端仍绑定旧 9 样例且未登记 `MLS-AI-005`，因此 CONTRACT-002 与完整 CONTRACT-003 继续失败并阻塞合并；批准不表示迁移或匿名 AI 已实现。
- 文档治理阶段已将提交 `489572e`、`45f3258` 以 `--ff-only` 纳入 `codex/unified-implementation`。原 `main` 的 25 项未提交文档修改保存在具名 stash `stage1-pre-anonymous-ai-governance-20260917`（创建时为 `stash@{0}`，对象 `0da7e6f20458eec01a2f391da2ea59979301c2e4`；原补丁 SHA-256 `927b531742fb09270d7bd9abc797d34bf847478eaeaa98a7d7f611b3bb78cc8b`），未删除或应用；15 个文件与候选树一致，其余 10 个为被后续候选/批准内容取代的较旧状态，未发现独立语义。

### 2026-09-18 后端 B5、匿名前端接合与自动化集成收口

- 后端 B5 提交链为 `cdde35f`、`bf6a718`、`319c208`、`9bca05e`：V8 匿名所有权与访客全局额度、V9 闭馆全局种子、AI 深模块、Cookie/模型 Adapter、14 个共同场景及契约加固均已实现；`./mvnw -B -ntp clean verify` 最终 127 项通过、失败 0、错误 0、跳过 0，包含真实 PostgreSQL、V1～V9、B3 结算/复核、B4 闭馆恢复和 B5 权限/额度/事件测试。
- 前端匿名接合提交 `0d35b1c` 已重建 OpenAPI 2.0 生成物、同步 14 个共同样例并开放匿名 `/assistant`；architecture、contracts、lint、Vitest（37 文件、193 项）和生产 build 五门禁通过。
- 新增 `compose.integration.yaml` 与 `scripts/verify-integration.sh`：经前端 Nginx 代理、真实 PostgreSQL 和独立 OpenAI-compatible HTTP stub 验证匿名启动及 Cookie 连续消息、错误 Cookie 404、Bearer 不提升访客、两访客共享额度 CAS（并发结果 200/429）、登录读者 L2 续借预填与现行续借提交，以及 backend/frontend 重启后的事件和额度留存；stub 观测到的请求均为 `thinking={type:disabled}`。
- 基础 Compose 空库、保留卷和第二空库三阶段均通过，迁移历史为 V1～V9 且保留卷重建前后一致；修复了旧脚本的 V1～V9 SQL 引号和已失效的 B1 占位文案断言。根 `scripts/verify.sh` 已纳入 B5 跨侧集成门禁。
- 集成证据不保存原始访客 Cookie、Bearer token 或 Cookie 签名秘密；真实 Cookie 只在一次性临时目录中使用，清理时删除。该 2026-09-18 批次尚未执行 Windows `E2E-001`、`E2E-AI-001`、Grafana 实页和真实模型端点 smoke，不构成产品验收完成；后续执行事实见本页 2026-09-20 与 2026-09-25 条目。

### 2026-09-18 AI 事件会话绑定与 AI 工具矩阵补齐

- 前端提交 `ee0ec24`（fix(frontend): bind AI events to active session）：`listAiEvents` 增加 `sessionId` 路径参数，AI 助手事件流展开时按当前活动会话加载（`src/modules/ai/index.js`、`internal/AiAssistantView.vue`），模块与组件测试补充会话绑定断言，防止事件流跨会话串读。
- 后端提交 `e4eb4ee`（fix(backend): complete approved AI tool matrix）：`ToolRegistry` 按已批准角色能力矩阵补齐 L3 导航动作（19 项 `open_*`）并修正 L2 预填动作的能力归属（读者本人/馆员），`AiModuleTest` 扩展对应矩阵断言。
- 两笔提交均为 M5 收口后的缺陷修复；门禁复跑与 M6 系统验收属后续执行事项，本节不登记验收结论。

### 2026-09-20 M6 验收阻塞裁定落账（DEF-M6-005/007，阶段 4 前置）

- DEF-M6-005 裁定（lingzz，2026-09-20，经 grill 两轮裁定）：方向 b·表单迁移——identity 模块内独立馆员页面（新路由 `roles:['LIBRARIAN']`＋AppShell 馆员导航入口），accountId 手工录入（沿用 `/walk-in` DESK 渠道 `walkin-reader-id` 先例），后端与契约零改动；实施时 E2E-001/03 改回真实 UI 发起路径。台账原引 "AT-0002 §2.12" 勘误为 §2.3（MLS-AT-0003）。
- DEF-M6-007 新登记（Sev-2）：复核三端点（`GET /reviews`＝LIBRARIAN_OR_ADMIN、`POST /reviews`＝LIBRARIAN、`POST /reviews/{id}/decision`＝ADMIN）实现只查认证不查角色，任何登录角色（含读者）可直发复核、决定复核、列出待复核清单，违反 AT-0002 §2.3 R5。修复边界（lingzz 裁定）＝控制器层对齐契约补角色检查（循 `CirculationController.java:381,392`、`SettlementController.java:29` 先例）＋READER/LIBRARIAN/ADMIN 交叉负例 403 测试；复测要求＝负例绿＋读者经 decision approve 不落地账户变更核实＋`mvnw clean verify` 全绿。
- 下一批次任务：DEF-M6-007 修复（先行，Sev-2 清零门禁）＋DEF-M6-005-b 实施（馆员页面、表单迁移、E2E 改真实路径），均须 M6 收口前完成；不阻塞阶段 4 Env-P/AT-0016 与 Env-O 上线（AT-0016 负载与 Env-O 可用率探针均不触达复核端点，窗口受影响情况在 007 修复落地时按窗口内变更规则另行做证据影响分析）。

### 2026-09-20 M6 阶段 4 执行（Env-P AT-0016 三轮与 Env-O 上线）

- 冻结候选：产品树＝e4eb4ee＋闭环修复 DEF-M6-001/002/004/006；提交链 `e5c1b66`→`dbdf18c`（其后六笔均为 harness/工具修复，未触产品路径），镜像自该产品树重建。
- Env-P 合规性登记（§4.8/4.9 项）：daemon＝Docker Desktop（WSL2 后端，与 Env-W 共享同一 utility VM 与 daemon）；Docker 29.7.2、Compose v5.5.1（与协议表述差异按差异规则登记）；内核 6.18.33.2-microsoft-standard-WSL2、VM 32 CPU/16GB/40GB 根盘。lingzz 2026-09-20 驳回 .wslconfig 约束后，约束验证＝逐容器配额生效（七服务 docker inspect 与清单 §4.1 一致）＋测量窗口宿主纯净（每轮 docker ps 快照，仅 m6-perf 容器运行）。
- AT-0016 三轮独立运行全绿（2026-09-20 12:22–14:47 UTC）：24 个场景行全 PASS、零非预期失败、零越限阈值、三轮饱和判定清洁（六维判据）；检索四子场景各 361 有效样本；写路径真实性核验（每轮预约创建 110；支付链仅 run-3 记录回调数 10，run-1 未记录、run-2 审计查询报列不存在，证据不足）。证据：`artifacts/m6-acceptance/phase4/at-0016/`（run-1~3＋env-p-compliance＋phase4-verdicts.json/summary）。AT-0003 v0.2 已登记三行通过记录与观察窗口进行中行。
- 数据集升级 seed-20260910-v2（v1 的 COPY NULL 双重转义修复，`5c2ebfa`），dataset.sql SHA-256 `203c8c31…5b0e`，字节级确定性复核通过，逐轮新鲜库装载。
- harness 修复六笔（首次真实执行暴露：k6 2.2.0 模块导出/路径、检索参数 `q=`、`titleId` 字段、预约/取消请求体、副本清单令牌与枚举、k6 容器 uid 导出权限、探针幂等键、凭据值集过滤），提交 `5c2ebfa`/`5f115cb`/`7f90800`/`81fded2`/`dbdf18c`。
- Env-O 上线：2026-09-20T14:55:00Z 窗口启动（项目 `m6-observation`、独立卷与端口 8091/19091/13001、配额 overlay、V1–V9 全 success、探针 30s 三探针全绿）；**宿主设备重启致计划外停机 14:56:30Z→2026-09-21T00:22:56Z（约 9.4 小时）已如实登记**并恢复；无预登记维护时段；窗口按 lingzz 裁定不真实等待、虚拟签署时点由其决定。
- 阶段 4 观察五项登记（详见清单执行记录）：非法 `/titles/{id}` 得 500；支付模拟器端点鉴权未强制（DEF-M6-007 关联观察）；副本 status 过滤语义；书目 availability 三态个案（title 1001）；audit/outbox/notification 链路未运行（AT-0014/0027 批次前必须核实设计意图）。

### 2026-09-21 M6 复核整改启动（W1）

- 2026-09-21 起 M6 验收整改执行：复核对 M6 验收准备材料形成 R01～R19 共 19 项发现，经会话确认全部成立；发现明细不在本节展开，以相应整改项的登记为准。
- 三项会话裁定：全量重置；环境确认；AT-0016 三轮运行改登为待复跑。本页 2026-09-20 阶段 4 节的执行记录按历史事实保留，其「三轮全绿」不再作为当前有效结论。
- 旧 Env-O 观察窗口（项目 `m6-observation`）归档作废，观察栈已由归档代理统一处置；阶段 4 节的窗口登记相应转为历史事实。

### 2026-09-22 A5 失败调查与验收恢复规划

- 本轮核对 WSL main 为 `9a5fea3`、领先 origin/main 74 提交；既有安全修复和 AT-0003 修改尚未提交。读取登记册 v0.8 与 A5 报告确认：AT-0016 run3–run5 已通过；A5 attempt1 功能完成但备份证据含动态 token，attempt2 前置登录 HTTP 500；A5 FAIL，A6–A7 未执行，不补判历史通过。
- 静态调查发现旧工作流缺少失败后的整改入口，安全收尾受串行门禁阻断；台账仍有 DEF-M6-008/011/012 Sev-2 未闭环记录；八个 E2E 探路场景不能替代 33 AT 全覆盖，浏览器正式版本准备未齐。登录 500 未复现，未归因产品或 fixture/config。
- 已形成恢复计划提案 R0–R7，包含前置诊断、已知缺陷整改、覆盖映射、候选影响分析、独立安全收尾及最终签署出口；新正式演练与依赖调整尚待确认。当前仅规划，没有执行产品修复、容器操作或浏览器，也未提交或推送。

### 2026-09-22 恢复计划与模拟支付权限确认

- lingzz 在本会话明确确认：“确认采用新批次，模拟支付限定ADMIN。更新到计划中。”已同步恢复计划及旧计划入口、TODO；新批次准入与重试规则生效，旧 A5 两次失败及旧批次禁止 attempt3 不变。
- CONTROLLED_TEST_OPERATOR 限定 ADMIN，perf 模拟器调度使用独立 ADMIN 会话，普通流通角色不变；实施与回归尚未完成。此次确认不代表产品修复完成或最终验收签署，本轮仅更新文档。

### 2026-09-22 M6 恢复批次执行（R0–R4 完整单次执行结果）

- 批次按 [恢复计划](docs/agents/M6-RECOVERY-PLAN-20260922.md) R0–R4 在 WSL 权威目录执行，全部产物落 `artifacts/m6-recovery-20260922/`（不入库）；逐包判定、证据定位、提交与阻断汇总于 `artifacts/m6-recovery-20260922/RUN-REPORT.md`。批次链八笔提交：`4ba4474`（R0 harness）、`467922a`（R0 docs）、`941150d`／`54e2e49`（DEF-M6-008）、`3d49c49`／`5d92252`／`07275d0`（DEF-M6-011/012）、`e8d0523`（R1c 协议缩减与判定门禁）；其后为本批 docs 登记提交：`79cc4b6`（AT-0003 v0.9）、`4cbb148`（AT-0003 v1.0 完整结果登记）、`9b46563`、`adc4a2f` 及本次同步提交（以上均仅含 docs 路径）。
- R0（记录／安全回归／保全，三子包均通过）：基线 `9a5fea3`、工作树 7 项 `M` + 5 项 `??`、无 mode change 残留；四条指定安全检查与八项语法检查退出码全 0，并修好 `scripts/test-sanitize-idempotency-snapshots.py` 的用例登记缺陷（9 个 `test_*` 中 2 个从未被 `main()` 调用，复跑 7→9 全通过并加登记守卫）；受跟踪 `100755` 文件 16 项与报告清单集合差为空、逐文件 `git hash-object` 与 HEAD 内容比对 diff=0，故**补提交对象为 0、工作树保留不动**。证据 `R0/baseline.md`、`R0/safety-checks.md`。
- R1a（两轮诊断已执行；**归因分类＝未归因**）：`diag/diag-plan.md`（405 行，diagnostic，H1~H9 九条假设）为计划体，实际执行按该计划第 3–4 节进行两轮，每轮先落 prediction 再运行，产物永久标 `diagnostic`，scratch 项目 `m6-recovery-20260922-diag1`／`diag2` 于轮末按标签核验后 `down -v`、残留 `0/0/0`、`m6-observation` 六容器清单 0 差异。**Round 1**（基线复现，13:46:13Z–13:46:51Z，脚本退出 0）：冻结候选上登录 HTTP **200**／信封 `MLS-ID-S002`，五条续行断言全 PASS（`txId=100001`），临时账户 `m6-a5-2-reader` 口令结构全合规，A5 attempt2 的 500 未复现；旧镜像 `sha256:0b319029…54ce9` 按 ID 查询返回 `No such image` 且无 dangling，仅存活于已退出容器 `m6-observation-backend-1` 的镜像引用，以只读方式（`docker cp`，操作前后容器 inspect 快照 diff 0 行、未启停）提取两侧 `/app/app.jar` 逐类对照：`PasswordHasher.class`、`AuthController.class`、`ApiResponses.class`、`ApiExceptionHandler.class`、`JdbcIdempotencyService.class` 两侧 sha256 相同，登录入口方法 `issue(String,String)` 的 `javap` 输出含绝对偏移逐行相同；`IdentityModuleImpl` 唯一类级差异落在与登录无关的 `authorize` 分支（14→15，对应 `CONTROLLED_TEST_OPERATOR -> role == Role.ADMIN`）；`701a13b..HEAD` 的 Flyway 迁移无改动。**Round 2**（唯一变量＝`password_hash` 生成形态，13:48:39Z–13:49:19Z，脚本退出 0）：7 条预测逐行成立——合规十六进制 200／`MLS-ID-S002`；base64 盐、空迭代段、空盐段、奇长度盐四例 500／`MLS-SYS-001`，栈顶分别在 `PasswordHasher.matches(PasswordHasher.java:40)`、`matches(:39)`、`derive(:47)`←`matches(:42)`、`matches(:40)`，上层帧为 `IdentityModuleImpl.issue(:115)`→`RegistrationUseCasesImpl.createSession(:76)`→`AuthController.createSession(:50)`、处理器 `ApiExceptionHandler#unexpected`；迭代数 10000 与 bcrypt 形态 401／`MLS-ID-004`；同键同体重放 200 且 `expiresAt` 与首次相同（读取已存快照）。**归因结论**：500 的唯一实测充分条件位于 fixture 侧注入的畸形 `password_hash` 值（结构 4 段、前缀 `pbkdf2`、内容非法或为空），两种镜像与幂等分支均不产生 500；attempt2 的实际注入值、`creds.env`、账户创建命令原文与 backend 日志随 `down -v` 与 `/tmp` 重置销毁、不可恢复，故归因分类登记为**未归因**（缺口属证据链，缺失清单见 `diag/round2-result.md` 第 6 节），不把「注入值受损」升格为已证实根因。**对冻结候选的限定判断**：无证据表明该 500 仍影响 `e8d0523`＋重建镜像（同一断言本批三次独立执行全部通过，且登录路径实现与 attempt2 镜像逐字节一致），不得登记为「根因仅影响旧候选、已被本批次变更消除」。两轮证据扫描退出码均 0、命中 0 行，轮末对整份 `diag/` 树复扫 `final-scan.sh` 退出码 0、命中 0 行；未改产品源码、未更新 AT-0003。证据 `diag/round1-result.md`、`diag/round2-result.md`、`diag/round{1,2}/`。
- R1b（通过）：静态核验 008/009/010/011/012 均无未登记修复（`R1b/verification.md`）。**已实施** 008（`SettlementController.java:32` 补 ADMIN 角色检查、无权 403/MLS-ID-005；`IdentityModuleImpl.java:172` 映射 `CONTROLLED_TEST_OPERATOR→ADMIN`；新增 `DEFM6008PaymentSimulatorRoleMatrixTest` 与 `IdentityModuleCapabilitiesTest` 映射断言，单跑 8/8 通过并在 `54e2e49` 工作树副本复跑同样 8/8；harness 侧 `steady.js`/`common.js`/`load-dataset.sh` 改用独立 ADMIN 探针会话）与 011/012（审计端口新增 `appendWithBusinessFact` 与 13 类 eventType 词表、成功路径落 `audit_event`；落地应用内 Outbox 分发器与 `WAITLIST_ASSIGNED`/`WAITLIST_EXPIRED` 生产者，`Tests run: 27, Failures: 0, Errors: 0`）。实施中首次在真库执行闭馆，发现并修复两处**既有** SQL 缺陷（`closing_control."current_date"` 未加引号、`UPDATE book_copy…FROM borrow_transaction` 的 `SET version` 歧义）——闭馆与开放命令此前在真实数据库上必然失败。**未实施** 009/010（仅登记处置建议）。台账 008/011/012 节末追加 2026-09-22 实施记录（状态口径「已修复待批次回归」）；005-b/007 实页与角色回归登记为 Windows 原生缓办（`R1b/deferred-realpage.md`）。全量 `mvnw clean verify` 未执行，单类测试结果不构成门禁通过主张。
- R1c（通过）：2026-09-22 裁定的缩减协议落地——预热 60 s、稳态四场景各 300 s、检索四子场景各 180 s、峰值 120 s 爬升＋180 s 保持（并发 50 不变）、恢复 300 s，单轮约 28 分钟；阈值与采样语义未变。新增统一判定门禁 `e2e/perf/judge-attempt.sh`（四项门禁，全过退 0）。两份 guards 测试 18/18 通过、五个改动脚本 `bash -n` 通过。证据 `R1c/protocol-reduction.md`。
- R2（覆盖映射与准备核验**均通过**）：七份来源件合并为 `coverage.md`（1997 行），33 个 AT 去重逐个在列、47 个分条检查点逐项在列、AT 级检查点行 388；登记 126 项 GAP（P0 14／P1 27／P2 9／P3 62／P4 9／P5 5）与 29 项阻断/待裁定项，全部「结果」列留空。六项准备核验的**未达成项为无**：AI 接线实测 `bash scripts/prepare-acceptance-env.sh` 退出码 `0`、判定 `DERIVED`，`.env.acceptance` 已派生（`-rw-------` 600、154 B，键为 `AI_ENDPOINT_URL`／`AI_MODEL`／`AI_API_KEY`／`AI_VISITOR_DAILY_LIMIT`），stderr 0 字节、stdout 仅含写入确认行与端点主机名（无值回显），派生件与键值不入证据、不入库（`git check-ignore` 命中 `.gitignore:26`）；其余五项只读复核结论无需变动。保留项：AT-0030 九场景在 `e2e/`、`scripts/` 内全量 GAP、`ds-concurrency` 数据集缺失、六浏览器槽位未执行（`readiness.md`）；两项运行件 `scripts/collect-ai-connection-evidence.sh` 与端点 smoke 前置为运行栈与出网，尚未运行。
- R3（通过）：最终候选 `e8d05232c853b23e91c112974d65ced214cde0b1`（`main`，工作树干净，`ahead 82`）；`docker compose -p m6-recovery-20260922-build -f compose.yaml build backend frontend` 退 0，镜像摘要 backend `sha256:1071074f75…e2af00f`、frontend `sha256:b199e4ed43…db72e`（backend 的 `COPY src/main/` 未命中缓存、`mvnw` 实跑 4 分 34 秒且日志含 `Compiling 186 source files`；批次 52 个改动路径不含任何 `library-frontend` 文件）；`dataset.sql` `53ce44a7…8b8dffea` 与 manifest／fixtures-manifest 全部吻合；受影响 AT 判定经覆核并补齐五项漏项，新增逐 AT 判定覆盖核对节。证据 `R3/freeze.md`、`R3/impact-matrix.md`。候选尚未进入 `origin/main`。
- R4（前置核验通过；AT-0016 缩减协议单轮通过；**正式演练未执行、准入不满足**；验收登记通过）：前置核验五项实测完成——两个全新空卷项目 `m6-recovery-20260922-pre1`/`pre2` 各十五条判定全 PASS 且归一化后 diff 为空、Flyway 9 条全 `success`、四表规模 1002/10000/30000/100000、临时 READER 结构回读一致；两次登录均 HTTP 200（被测镜像为重建后 `1071074f75…`，与 attempt2 的 `0b31902912a8…` 不同，故不构成 R1a 归因）；隔离与数据/镜像校验通过；两项目 `down -v` 退 0、残留各 0、`m6-observation` 六容器全程保持 `exited`。AT-0016 单轮窗口 `2026-09-22T12:34:28Z–13:06:19Z`，统一判定门禁五项退出码 `0/0/0/0/0`、`attempt.json valid=true`、窗口采集器 `done:0`，八场景 `unexpected_failures=0`、饱和七维全 `evaluated` 且零越限，`steady` 的 `audit`（62）／`payment`（20）与 `warmup.public_read`（61）低于 100 下限登记 `samples_insufficient` 且不判轮无效；`peak` 实测窗口 330 s（含 `gracefulRampDown` 30 s）、`at-0016/gates/teardown.exitcode` 残留 1（`grep -c` 计数缺陷，修正后的收尾核验为 `ALL-PASS`，未以后续核验结果覆盖该残留值）均如实登记。**正式演练 C03/C04：前置核验四项按恢复计划第 62 行全部通过，而演练准入要求 R1a 登录诊断闭环；本批 R1a 归因分类＝未归因，依恢复计划第 46 行「未归因则保持待诊断，不消费正式演练额度」，本批未执行演练**，`artifacts/m6-recovery-20260922/` 下无演练 attempt 目录、无 timeline／RPO／RTO、无续行判定，既有 `M6-分条-0018-C03/C04` 失败行不改写。结果登记入 AT-0003 v0.9（新增 `M6-复跑-R4-R1` 通过行与准入登记行，提交 `79cc4b6`）与 v1.0（准入登记行收敛为演练执行行 `M6-演练-R4-01`，结果「未执行（准入不满足）」；分条行 `M6-分条-0018-C03/C04` 结果值与 2026-09-21 未达判定事实不变、仅追加本批依据与成因处置事实；六处占位行名收敛为描述式指引；计数执行次数 14／通过 4／失败 4／受阻 0／未执行批次行 2，提交 `4cbb148`）。
- 本批次全部结论不替代 M6 总体退出结论：A1–A4 已完成，A5 失败，A6–A7 尚未执行；所有通过结论只存在于 `MLS-DOC-AT-0003` v1.0。R1a 的归因缺口属证据链、不可恢复，冻结候选仅有「无证据表明该 500 仍影响」的限定判断，不得升格为修复结论。实页与 Windows GUI 事项（六浏览器正式矩阵、批次 A~E 实页断言、AT-0017）本批全部未执行、未缩减。批次全程未 push、未建 PR、未动分支或 stash、未用 `git add -f`，未对 `m6-observation` 做任何启停、未执行全局 prune。

### 2026-09-25 WSL AI 助手与窄屏缺陷修订

- 修复 AI 多轮上下文：所有权校验通过后，仅按当前 session 与当前 USER 消息 ID 之前读取持久化 USER/ASSISTANT 消息；按完整轮次取最近上下文，最多六轮、历史文本合计最多 12,000 字符，单条最多 6,000 字符；失败后未回答的 USER 不进入后续上下文。当前问题只加入一次，本轮模型工具调用与结果仍在内存请求序列中延续。脚本模型测试覆盖追问上下文、会话隔离、当前问题去重、失败轮与长度上限。
- 将已批准预约规则加入系统指引与模型可见工具描述：书目详情有“预约此书”入口，支持 ONLINE/DESK；提交需已登录读者本人或馆员代办，访客仅查询；预约请求由服务端判定，ONLINE 日期最早为次一开馆日、DESK 可当日；书目级 availability 不保证预约成功，预约不等于借出，到馆后仍须校验已绑定副本。脚本模型请求断言与本机 localhost HTTP 适配器测试确认指引、工具描述进入实际请求线格式；未调用真实模型端点。
- AI 事件面板按 cursor 顺序拉完后续页面，按 eventId 去重；展开、成功发送后及重新展开时从当前游标增量刷新，并丢弃已失效的关闭/重开请求结果；错误继续显示 ErrorAlert。表格与 Markdown 表格、代码块有各自局部横向滚动容器。
- AI 消息采用受限 Markdown 解析并渲染 Vue 文本/VNode，不把模型 HTML 送入 `v-html`；支持标题、强调、列表、代码块、表格等。回归覆盖 `<img onerror>`、`<svg onload>` 与 `javascript:` Markdown 链接，均不生成可执行 DOM 节点；长代码与表格留在局部滚动区。
- AppShell 窄屏提供原生按钮控制的可访问角色导航，保留原路由和登出行为；窄屏菜单可收起、展开并在选路后收起。事件表、长代码、表格的局部滚动结构由组件回归覆盖；屏幕实际宽度下的布局结果仍待 Windows 原生页面复查。
- WSL 验证：前端 `npm run test:ci` 40 个文件、207 项通过；`npm run lint:check` 通过；`npm run build` 通过（Vite 报告产物 JS chunk 大于 500 kB）。后端 `./mvnw -Dtest=AiModuleConversationTest,OpenAiCompatibleModelEndpointTest test` 5 项通过。Testcontainers 集成 `AiModuleTest` 因 WSL 未提供 Docker socket 未能启动；未运行真实浏览器、真实模型端点或六浏览器正式验收。
- Docker Desktop 启动后的补验：WSL 经 Docker socket 连接 Docker Desktop 29.7.2，PostgreSQL 17.6 Testcontainers 容器启动并应用 10 条 Flyway 迁移。首次 `./mvnw -B -ntp -Dtest=AiModuleTest test` 在约 11 秒内执行 19 项，其中新加两项因测试夹具使用非十六进制访客摘要而在进入被测逻辑前报错；将测试摘要字符改为合法十六进制后重跑同一命令，19 项通过、失败 0、错误 0、跳过 0，Maven 总耗时 11.975 秒。该 WSL 轮次未执行后端全量集成测试、真实浏览器、真实模型端点或六浏览器正式验收；随后 Windows 定向复测见下一节。

### 2026-09-25 Windows 浏览器与真实 API 定向复测

- 用户在 Windows 验证副本启动前端、后端和数据库，并报告三者健康运行；复测时本地 `main` 与 `wsl-local/main` 均为 `7eedf0b`，修订已同步，工作树干净。书目验证页保留在浏览器 `http://127.0.0.1:8088/titles/700001`。
- 浏览器中的两轮问答经真实模型端点完成：追问“上一轮那本书”正确回答作者 C. J. Date；借阅/预约指引指出线上预约入口开放，并区分预约与实际借出。真实模型端点 S1–S6 检查全部通过。
- AI 事件流在第二轮后由 13 条增至 17 条，收起再展开仍显示 17 条；回复的标题、列表、表格正常渲染。430 px 视口下，馆员和管理员页面宽均为 421 px，导航可展开并正常收起；364 px 视口下页面宽为 358 px，事件表仅在局部横向滚动。
- 本次结论来自用户提供的 Windows 定向复测结果；超过 20 条事件的分页、Markdown 强调与恶意 HTML/`javascript:` 字符串的真实页面检查未在该结果中报告。未执行完整 M6 验收或 R5 六浏览器正式矩阵。

### 2026-09-25 P0 范围与基线（完成）

- P0 在 WSL 权威副本 `/home/PUBLIC_USER/src/MyLibrarySystem`、基线 `7eedf0b2b705ecf3820d49e69ad1e242ea8dcfb3` 执行；从启动时 `main` 建立本地 `codex/delivery-closeout-p0-20260925` 分支。WSL 启动前已有 3 个删除与 4 个修改，均保留且未计作 P0 成果：`HANDOFF-AUDIT-20260912.md`、`HANDOFF-AUDIT-20260913.md`、`HANDOFF-CODING-PREP-20260912.md`（删除），`HANDOFF.md`、`README.md`、`TODO.md`、`docs/README.md`（修改）。Windows `/mnt/c/home/PUBLIC_USER/src/MyLibrarySystem` 为 `main`、同一 HEAD、工作树干净。local remote 的 fetch-only URL 与 disabled push URL 已核对；一次 `git fetch windows-local` 因 sandbox 对 `.git/FETCH_HEAD` 只读以 255 退出，无引用刷新或工作树改变。
- 初始 WSL 中 Docker 命令不可用；用户启动 Docker Desktop 后，通过受控只读命令核实 Docker 29.7.2、Compose 5.5.1 可用。列出的 13 个旧容器均退出，当前运行容器列表为空。本阶段没有启动、停止、删除或清理任何容器、卷、镜像。WSL 的 Node/npm、Python、Java、Maven、CodeGraph、项目依赖目录已核对；两边 `.env` 与 `.env.acceptance` 中模型配置存在且必要字段非空，未输出秘密值。Windows GUI 与内嵌浏览器操作未在 WSL 执行。
- CodeGraph 当前源确认：退款页面/API 与 REFUND 审批可达；退款和对账均有直达 REST 写路径，对账还有 `/admin/reconciliation` 路由、导航、表单和客户端调用，目前会写固定 0 占位结果；`CIRCULATION_EXCEPTION` 复核申请可建但缺动作执行器；核心 `ACCOUNT_CHANGE` 批准亦未生效。对账入口需在 P1 同时关闭菜单、页面表单、客户端方法、直达路由和 REST API，现有历史数据保留。
- `CirculationModuleImpl.reserve` 只做同读者/书目未终结事务检查，欠费/逾期/额度和线上日期守卫未见；30 天借期、14 天续借/一次与已批准规则不符；归还罚款费率亦有差异。闭馆 API 仅接受 SYSTEM，reopen 为管理员路径；后台调度查到的是 Outbox 轮询，不是日结任务。`DeliveryModuleImpl.consume` 当前只为 WAITLIST_ASSIGNED/WAITLIST_EXPIRED 生成通知。详细证据、移出/保留入口表和 P1 清单见 [P0 结果](docs/agents/delivery-closeout-20260925/results/P0.md)。
- 已把旧 coverage 的 126 GAP（A1 30/A2 16/B 16/C 21/D 20/E 11/F 12）及 29 BLK（A1 11/A2 14/D 4）逐号映射到保留核心修复/验证、随已批准业务裁剪或质量决定移出、治理格式豁免。文档记录每个旧 ID 的归并项；缺 harness/数据的保留核心 GAP 不因文档门禁豁免而免测。
- 用户于 2026-09-25 答复原话：“性能与并发采用建议，浏览器只测试chrome，不观察计时。”确认本次仅在 Windows 当前 Chrome 验收保留核心链和桌面/364/430 px 页面；性能按最终候选一轮既有缩减协议、阈值不变；并发覆盖同副本竞争、重复支付、关闭与回调竞争、日结与在途写入；不设置计时观察窗口。Edge/Firefox、不声称多浏览器兼容；不声称三轮、p99、完整容量、AT-0030 九场景全覆盖或 2 小时/七日可用率/稳定性。P4 仍须做实际运行/日志检查与恢复验证。恢复演练保持必做。
- P0 在 2026-09-25 完成；当时没有改产品代码、未执行测试/构建/真实模型或浏览器，也尚未开始 P1。P0 的入口和质量范围基线见 [P0 结果](docs/agents/delivery-closeout-20260925/results/P0.md)。

## 2026-09-27 P1 裁剪与核心修复（执行与现场复核完成）

- 本阶段在 WSL 权威工作树 `/home/PUBLIC_USER/src/MyLibrarySystem`、分支 `codex/delivery-closeout-p0-20260925` 执行，基线 HEAD 为 `7eedf0b2b705ecf3820d49e69ad1e242ea8dcfb3`。P0 前已有三项历史 HANDOFF 文件删除、四项文件修改继续保留；未重置或清理。完整逐项状态、命令和退出码见 [P1 结果](docs/agents/delivery-closeout-20260925/results/P1.md)。
- 已实现退款/对账直接写端点恒拒绝，退款、对账与旧 REFUND/高风险复核请求均不能产生业务写入；前端对应业务页面/入口与退款复核关联移除，AI L3 页面引导移除。P0 静态核对未发现 AI 退款/对账写 handler。另关闭读者自助 walk-in 的 HTTP 和内部模块命令写入口，`READER` 的 `SELF_SERVICE`/`DESK` 请求均 403 且零流通写入，馆员 `DESK` 办理保留。
- 已实现账户复核原子批准/拒绝，流通资格/分开额度/借期/预约截止日/罚款规则，受控逐日日结及失败整体回滚，通知接收者/审计责任链、支付回调竞争保护。书目有 `CONFIRMED`/`WAITLIST` 有效预约时，续借上限截到最早预约日前一日；reservation 与 renewal 在同一书目锁上串行，真实 PG 屏障用例覆盖并发次序。新增 V10 起借日保守下界、V11 闭馆连续进度、V12 注册会话限流迁移；Compose 显式启用受控日结 cron 与 `Asia/Shanghai` 时区。自动日结不自动 reopen，也没有降低 SYSTEM HTTP 角色要求。
- 已补 `/titles` 书目可借筛选（AVAILABLE/UNAVAILABLE/UNKNOWN）与门户控件；支付页有显著模拟支付说明；注册端以服务器签发 HttpOnly/SameSite 会话 Cookie 计数，小时内第 6 次 429。最终 P1 真实 PostgreSQL/API 扩大批次 93/93 通过；4 个直接影响前端测试文件 18/18、contract/lint check 与生产 build 通过。编译/命令/逐入口证据、首轮失败和限制见 [P1 结果](docs/agents/delivery-closeout-20260925/results/P1.md)。
- 用户于 2026-09-27 确认预约本轮暂按每日开馆（线上次日、服务台当日）；不代表长期节假日历规则。用户同日确认每个浏览器会话每小时 5 次注册请求、每次请求都计（含失败），第 6 次 429；脚本仅变换 X-Forwarded-For 验证共用 cookie 额度，未做本机真实 IP 切换。限流使用服务端签发 opaque HttpOnly/SameSite Cookie、PG 摘要计数；Cookie 可被用户清除后额度可重置的技术限制已登记并告知，不记为用户单独接受。
- 最后定向批次命令为 `cd library-server && mvn -B -ntp -Dtest=P1CoreCirculationRulesTest,V1MigrationTest,ClosingModuleTest,SettlementModuleTest,ITDefM6011AuditTrailTest,ITDefM6012NotificationChainTest,DEFM6007ReviewRoleMatrixTest,DEFM6008PaymentSimulatorRoleMatrixTest,Contract003LiveScenarioTest,ITAI001To007Test,ToolExecutorTest,RegistrationContractProbeTest,ITCAT001TrigramSearchTest,ITCirculationCoreTest,CirculationScenarioContractTest,CirculationModuleTest,Contract001Test test`，退出码 0，93 tests/0 failures/0 errors/0 skipped，PostgreSQL 17.6、Docker Engine 29.7.2。新增预约日 cutoff 与 reservation/renewal advisory-lock 屏障后，定向 `P1CoreCirculationRulesTest,CirculationModuleTest` 26/26 通过。
- 前端最终直接影响 UI 测试 4 个文件 18/18 通过；`contracts:generate` 退出码 0，受控升级 `contracts:check` 与 `lint:check` 退出码 0，`npm run build` 退出码 0（Vite 对 1,136.32 kB JS chunk 有非阻断提示）。默认沙箱 Node 子进程曾报 `EPERM`，相同 lint/contracts 检查经受控升级成功。首次 Contract003 与 IT-CAT 失败事实及修复后的通过结果列于 P1 结果。
- Testcontainers 使用测试自有标签/临时实例，测试结束后由 Testcontainers 清理；现场复核时按 P1 标签查询无残留；未操作既有用户容器、卷或镜像。未提交 P1 变更；结果文件按仓库规则处于 `.gitignore`，不强制加入版本库。P1 执行与现场文档复核完成；P2–P5 未开始，未在 WSL 操作浏览器/Windows GUI；P3 Chrome 实页、真实 IP 切换、AI 最终候选端点证据与 P4 运行/恢复仍留后续，未声称整体验收或发布完成。

## 2026-09-27 P2 最终候选自动化回归（执行完成）

- P2 在 WSL 权威工作树 `/home/PUBLIC_USER/src/MyLibrarySystem`、分支 `codex/delivery-closeout-p0-20260925`、HEAD `7eedf0b2b705ecf3820d49e69ad1e242ea8dcfb3` 执行。保留所有 P0/P1 未提交差异、数据和工作树状态，未 reset、clean、stash、提交或操作 Windows 工作树。P0 前已有三项 HANDOFF 历史文件删除和四项文件修改继续保留；不能将这些来源改记为 P2 产出。
- 最终 `library-server/./mvnw -B -ntp clean verify` 退出码 0；Surefire 40 类、176 项，失败/错误/跳过均为 0。真实 PostgreSQL 17.6/Testcontainers 覆盖账户复核与退役写路径零副作用、ADMIN 模拟支付权限、审计、Outbox 通知、注册限流、流通、AI、目录和共享场景。前端 `test:ci` 208/208，升级后的合同/场景/架构/ESLint `lint:check` 退出码 0，`build` 退出码 0；各轮首次失败、定位和修复记录在本地 [P2 实际结果](docs/agents/delivery-closeout-20260925/results/P2.md)。
- 已修复两处回归发现：账户额度共享常量移入公开 circulation contract，移除应用用例对内部模块实现的依赖；操作清单测试同步 P1 退役端点契约（两个匿名兼容拒绝路径固定 422/UNSUPPORTED，均无业务执行及幂等键）。V9 单迁移测试固定 target 9；账户复核集成正向夹具改用真实存在账户。修复后定向后端 5/5、前端操作清单 7/7，最后完成全量复验。
- 构建了独立唯一标签候选镜像：backend `mls-backend:delivery-p2-20260927`，image ID `sha256:f46660629c97eae1826fb0e6684fbe90c0b20dcc83556ba826ddd9ccf5750663`；frontend `mls-frontend:delivery-p2-20260927`，image ID `sha256:02d9703af5cdabdb8c752b1dd190ca3a2b4cf9d8fb369f7b3753d6f1bf24b9ea`。合并 Compose 镜像清单只读检查退出码 0；PostgreSQL 固定 `postgres:17.6-bookworm@sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3`。Compose build 配置未启动服务，未覆盖 `foundation` 标签。
- 无秘密配置摘要：Compose backend 指向容器网络数据库 `database:5432/library`，用户名为 `library`；运行需在 Windows 本机提供 `DB_PASSWORD`、`AI_VISITOR_COOKIE_SIGNING_KEY`。日结默认启用，cron `0 0 0 * * *`、时区 `Asia/Shanghai`；前端端口默认 8088 且绑定 `127.0.0.1`。本地 `.env` 和 `.env.acceptance` 权限均为 600、按 `.gitignore` 排除；不复制或记录其值。P3 需在 Windows 安全配置 Compose 运行密钥和真实模型端点变量。
- 最终 Docker 检查没有 `org.testcontainers=true` 临时容器残留。回归只运行 Testcontainers 自建临时数据库；未停止或删除用户容器、卷、数据库、镜像。候选镜像保留供 P3 使用；若 Windows 使用不同 Docker daemon，按 P2 结果中的精确 Dockerfile 命令重建。
- P3 仍须在 Windows 原生 Codex 当前 Chrome 实测；本轮未操作浏览器或 Windows GUI。Windows 起始 `main` 与 WSL HEAD 相同且工作树干净。结果文件给出相对该 HEAD 的完整候选补丁及只读 `git apply --check` 结果；补丁含原 P0/P1 tracked 差异与 14 个 P1 新文件。P3 显式复制本地 P2 规格/结果、P0 与 P1 结果、P3 规格、Compose 镜像覆盖文件及补丁；P3 需准备隔离读者/馆员/管理员账号、书目/馆藏、罚款/模拟订单、账户复核目标和真实模型配置。不得把 WSL 本地秘密复制进结果或聊天。

## 2026-09-28 P3 Windows 原生集中验收（首轮，部分完成）

- Windows 原生 Chrome `154.0.8037.58` 在实际 `1280×800`、`800×600` 完成基础页面 smoke 18/18，退出码 0。首跑 16/18 的两项失败由脚本选择器错误造成，修正后复跑通过；测试涉及无页面级横向溢出、检索详情、注册、错误密码、登录、读者管理员路由 403 和退出后的受保护路由。此 18 项不是 S1–S9 全部通过，也不是最终修复候选复验。用户于 2026-09-28 明确将 P3 视口组合由桌面／364／430 px 替换为实际 `1280×800`、`800×600`；先前内嵌浏览器 1920×1200 设置没有实际页面尺寸证据，不计通过。
- Windows 报告记录 S2 流通、S4 模拟支付、S5 账户复核、S7 真实模型及 S8 安全排版的已执行页面范围；S3 取消预约返回 `MLS-SYS-001` 且重载仍未取消，S6 隔离期管理员退出后重新登录被 `MLS-CIRC-014` 阻断。另有提前取书成功、OCCUPIED 未付单缺关闭入口、已归还借阅仍显示续借，以及 AI 建议访问移出对账页。S2–S9 尚需按缺陷影响补验，真实模型在新候选上仍需复跑。详细场景表、失败证据和资源状态见本地 [P3 当前结果](docs/agents/delivery-closeout-20260925/results/P3.md)。
- WSL 核对已批准 `MLS-BR-0020`：取书应在预约日，跨日到台转人工，因此前一天 CONFIRMED→LENT 是缺陷；全部副本均为`已预定`时应拒绝新预约，不应期待候补，故此项更正验收预期。Windows 验证工作树 `main` 干净且已提交 HEAD 与 WSL 相同，但 WSL P2 候选及新修复未提交，Windows 运行 tag 不能代替源码／镜像 ID 对齐核验。两个 Windows 隔离栈及数据保留供复现，未操作用户其他容器或卷；WSL 未执行 Windows GUI 或内嵌浏览器。

## 2026-09-29 P3 缺陷 WSL 修复与聚焦验证

- 执行代理在权威副本修复五项确认问题：取消预约请求按契约带 `reason`；非预约日取书转 `MANUAL_HANDLING`、保留原已预定副本，人工 `RELEASE` 以原副本借出，馆员页明确显示转人工；`JdbcWriteAdmissionGate` 对精确登录签发路径放行以支持隔离期管理员重登，普通业务写仍受隔离；未付 `OCCUPIED` 订单显示关闭入口；仅 `LENT` 借阅显示续借。P3-05 则依批准规则更正验收预期，不修改合法拒绝行为。
- 后端无容器模块与门禁测试 13/13、前端受影响测试 23/23 通过；前端合同／样例／架构／ESLint 及生产 build 退出码 0。首次合同命令在沙箱因 Node `EPERM` 退出 1，经受控升级单次重跑通过；首次合并后端测试因 Docker socket 不存在与两项过时取书夹具退出 1，夹具已改为当日服务台预约，最终无容器测试通过。PostgreSQL/Testcontainers 版跨日及审计测试仅编译、未执行；当前 WSL `docker` 命令也不存在。新候选尚未完成 P2 数据库回归、构建镜像或 Windows 页面复跑。完整命令、退出码与界限见 [P3 当前结果](docs/agents/delivery-closeout-20260925/results/P3.md)。
- AI 系统指引针对首轮建议访问已移出对账页的自由文本问题，明确本次版本没有退款、退款复核、对账及差异处理入口；同时将非预约日取书写为转人工、由馆员依现行规则决定。模型请求捕获与批准工具注册表测试 4/4 通过，未调用真实模型；最终候选仍需 Windows 真实端点复验，不承诺模型不会误答。
- WSL 已将共同 HEAD 以来的全部未提交源码差异及 16 个非忽略未跟踪文件汇成完整 `P3-candidate.patch`，并在权威工作树作反向检查、在干净 Windows 副本作 `git apply --check`；没有应用或改动 Windows 副本。补丁哈希、文件数和精确整合边界见本地 P3 结果；新镜像尚未构建。

## 2026-09-29 P3 Windows 新候选复验核对

- 现场指挥已读取 Windows 完整复验报告并与当前 P3 spec、P0/P2/P3、工作树核对；测试结果归属补丁 `4a5a2eefbbd085545109df9d057e43bc1a719e2b3056278bcdd2a6434305f6c6` 的临时基线副本及 `mls-p3-final-20260929` 隔离栈。Windows 验证仓库实时只读检查仍为干净 main、共同 HEAD `7eedf0b2b705ecf3820d49e69ad1e242ea8dcfb3`，引用关系 0/0；未提交 WSL 差异不能据此视为已对齐。
- Windows 报告记录 PostgreSQL/Testcontainers 181/181、前端 211/211、build 与最终合同／lint 通过；Chrome 实际 1280×800、800×600。原 P3-01/02/03/04/06 通过，包含取消释放、跨日转人工、隔离期重登重开、未付订单关闭和借阅终态入口；复核批准／拒绝、模拟支付成功／失败、真实模型多轮及事件流、安全显示名与受控 Markdown 已补验。首次脚本等待误报及 CRLF 导致合同检查失败后修复的事实保留，详见 P3 结果和 Windows 报告。
- P3 仍未通过：追加 P3-07 取消终态动作、P3-08 草稿续借拒绝弹窗反馈、P3-09 审计宽表、P3-10 模型模拟支付说明、P3-11 读者现场借阅表单入口五项失败；未取书释放、通知送达、实际溢出 AI 宽表滚动仍未完成。馆员现场借出／取书和 ADMIN 模拟支付已有批准保留范围，不因模型误答删除能力。执行代理为用户指定 GPT-6 Luna·max。
- 主、执行代理默认沙箱命令启动均报 ENOENT；相同命令申请提升权限后可运行，WSL、shell 和仓库均存在，已撤回要求重新连接工作区的过早判断。未改系统或 Codex 安全配置；恢复后继续权威副本工作，WSL 不操作 Windows GUI。旧、新隔离栈和数据库保留；后续修复的测试和页面结论另行追加，不覆盖本批候选的归属。
- AI 同域核对发现 `prefill_walk_in_loan` 与旧测试仍允许读者取得只预填草稿。用户于 2026-09-29 明确裁定：“同步移除读者预填，保留馆员预填”。决定已派给执行代理并纳入 P3-11；不新增管理员权限、不移出普通读者预约。该条登记批准与任务派发，不表示实现或验证已完成。

## 2026-09-29 P3 本轮 WSL 修复与技术收口

- 用户指定 GPT-6 Luna·max 完成 P3-07～11：预约取消动作按有效状态展示；AI 续借弹窗呈现拒绝并清理旧结果；审计表增加局部横滚区域；模型指引明确保留 ADMIN 模拟支付且无真实扣款；读者菜单／直达现场借阅和 AI 现场草稿均限制，馆员办理／预填保留。AI 工具变更按用户当日“同步移除读者预填，保留馆员预填”决定执行。未改生产业务规则、时钟或直接请求守卫；布局与真实模型尚未在 Windows 复验。
- 聚焦前端 20/20、后端 AI 25/25 通过；最终前端 40 文件、216/216，合同／样例／架构／ESLint 和 build 退出 0；后端 clean verify 183/183、0 失败／错误／跳过，41 个 Surefire XML 独立汇总一致。首次全量 2 失败／2 错误源于两类审计／通知测试按主机 LocalDate.now() 构造日期，与应用 Asia/Shanghai 不一致；仅测试改为注入 BusinessClock.today()，定向 8/8 后全量复跑通过。首次定向误删 LocalDate import 的编译失败也保留。日志在忽略目录 artifacts/p3-followup-20260929/，精确命令与证据见 P3 结果。
- 现场指挥使用既有 Dockerfile 构建独立 mls-backend:p3-followup-20260929 与 mls-frontend:p3-followup-20260929，均退出 0；未覆盖旧标签或启动部署。准备了独立 Compose image 覆盖文件和新完整候选补丁路径，原 4a5a 候选及历史证据保留；补丁最终哈希和双侧检查以本地 P3 结果为准，避免修改本文件后改变补丁身份。
- 本轮 WSL 源码与测试修复完成，产品未提交；Testcontainers 自建资源已回收，未操作 Windows 保存栈／数据库。P3 仍部分完成：新候选 Windows 1280×800／800×600 实页、模型支付说明及 AI 角色联动待复验；未取书释放、通知送达、AI 实际溢出宽表滚动仍未执行。P4 未开始，不将技术通过当成正式页面通过。

## 2026-09-30 P3 Windows 续测接收与三项阻断派发

- 现场指挥实读最新 Windows 完整报告及原始 Spec：9f11 候选前端预检 216/216、合同／lint／构建通过；后端 183/183 仅引用上游。本批 P3-07～11、到期释放／通知、预约额度、AI 实际宽表和模型失败恢复已有对应证据；未执行矩阵与自然链仍按报告边界保留。
- Windows 批用户已批准原生 Codex IAB 范围和主线程操作／Luna 判读方式，实际视口 1280×800、800×600；未核实 IAB 精确引擎版本。原 Chrome-only 记录为先前范围事实，不当作本批已执行 Chrome。
- P3 仍未通过：用户已确认 P3-12 占用订单重进恢复、P3-13 当日归还副本当日禁借、P3-14 馆员 AI 草稿应用预填。现场指挥已派发 GPT-6 Luna·max 核对和必要修复；未重新裁定已批准规则，也未登记本轮实现／技术或页面通过。
- 本轮只读两副本 HEAD 均为 7eedf0b；Windows main 仍干净，WSL 保留上轮大量未提交修改。默认沙箱启动失败后，经自动审批提升的相同 bash 命令恢复读取；未修改系统设置、Windows 源码、模型配置、调度或测试库。WSL 不执行真实 GUI；新候选实页仍需 Windows 原生环境。

- 本轮 P3-12 核对发现关闭路径缺少操作者归属判定；用户于 2026-09-30 明确“读者不能关闭他人订单，馆员可以关闭他人订单”，已纳入相邻回归与必要修复。隔离 HTTP 测试尚待执行，不先登记漏洞已确认或修复通过。采用 fix-finding 作独立只读边界调查，原三个阻断分给两名 GPT-6 Luna·max，Maven 检查按窗口串行。

## 2026-09-30 P3 阶段性实施与待裁定边界

- 两名 GPT-6 Luna·max 已分别完成 P3-14 馆员 AI 表单应用（相关与相邻前端 29/29）及 P3-13 正常归还／释放／闭馆清点链（真实 PG 及模块 17/17）；均保留先实红、单变量探针和首次失败事实。点击 AI 应用前零借出、显式提交携草稿号；同日归还隔离、历史补处理不得清今日副本、清点后次日可借均有技术断言。
- P3-12 前端无缓存占用单恢复聚焦 11/11；中间全量前端 221/221 与合同／lint／build 通过，后续错误提示和后端 HTTP／关闭守卫仍在本轮范围，不能将中间数当最终源码通过。后端本轮尚未全量重跑。
- 用户对 SHELVE 冲突回复“保留该入口，单独确认其日期约束”；具体日期边界已另问，暂未修改 SHELVE。ADMIN 关闭的当前行为／契约不一致也待用户答复。P3-13 整体及关闭权限未登记完成，P3 仍未通过；Windows 新候选实页与 P4 均未执行。

- 用户随后确认 SHELVE 日期为“允许当天上架，但当天仍禁止外借”。P3-13 已重新派发同一 Luna·max 实施真实借出日期校验及领取／现场／人工放行相邻回归；不能将先前17/17延伸为新约束已完成。维护RESTORE同日例外保持，ADMIN关闭仍待答。

## 2026-09-30 已接通订单查询与日期存量取舍

- P3-12本人占用单查询HTTP与读者隔离已接通，12项模块和1项真实PG查询通过；初次字符串订单号断言误报已修为ID比较并保留。最终该前端40文件222/222、46操作合同／lint／构建通过，包含空列表查询失败可见提示。
- 订单他人关闭及馆员降级后同幂等键重放均已在独立PG实红（各1项，实际200）；合法馆员首次关闭200/CLOSED控制成立。关闭权限仍待补丁与最终验证，ADMIN边界未答。
- 用户确认歧义存量可以延至次日，现场指挥采用该回填；特殊同日允许沿用RESTORE例外，不新增操作。已派发最早可借日期字段／V13及三个借出入口的原子守卫，尚未登记新日期实现通过；此前17/17不自动覆盖该变更。未生成最终候选、未操作Windows实页或既有库。

## 2026-09-30 全部取舍答复接收

- 用户对ADMIN关闭明确“允许管理员，并同步契约”；最终关闭边界为READER本人、LIBRARIAN／ADMIN他人、合法SYSTEM，其他操作权限不一并扩大。关闭代理已在独立副本恢复实施／验证，权威Java日期窗口仍归流通代理。
- 用户明确特殊同日允许“沿用现有维护恢复例外（推荐）”，不新增馆员当日放行动作。历史歧义副本延次日已获允许，现已采纳。ADMIN／SHELVE／历史回填均已获得回复，当前剩余是代码与回归，不是用户待授权。
- 新关闭补丁、V13日期字段及最终候选尚未完成；P3仍未通过，Windows真实页面未在WSL运行。

## 2026-09-30 最终源码技术回归完成

- Luna·max完成三个阻断的源码修复；现场指挥核对并整合关闭归属／重放守卫、允许ADMIN契约、日期字段／V13／三个借出入口和馆员AI预填。P3-13最终聚焦23项通过；其他读者关闭与降权同幂等键重放由实红转拒绝，合法控制、终态授权、竞争通过，服务端安全项限定结论fixed。
- 权威副本实际 `./mvnw -B -ntp clean verify`最终42套件198/198，0失败／错误／跳过；前端整合后40文件222/222、46操作契约／lint／架构和build通过。首次198项有两处旧计数失败，修为接口46／V9升级迁移4，业务语义断言保留，首次日志未覆盖。完整结果／原始日志／独立复核／安全专门归档见[P3结果](docs/agents/delivery-closeout-20260925/results/P3.md)。
- 额度中断后用户要求从断点继续，工具通路恢复；已有成功测试不重复运行。周额度剩余约8%，不触发用户允许的低于1%重置一次条件，未消耗重置。
- 管理员AI关闭导航与页面范围扩展尚待用户选择，原入口保持；已批准的管理员关闭API及契约同步已完成。Windows新候选实页未在WSL运行，P3仍未通过，P4未启动。

## 2026-09-30 独立候选交接完成

- 新完整候选 `docs/agents/delivery-closeout-20260925/results/P3-blockers-candidate-20260930.patch`相对共同HEAD，包含此前未提交差异和本轮修复，133路径／18个原未跟踪文件；WSL反向与Windows正向只读应用核验通过。最终内容SHA及镜像完整ID见本地P3结果，勿将新全量patch叠加旧9f11；本地ignored报告／override需显式读取，不假定随Git交付。
- backend `mls-backend:p3-blockers-20260930` ID `sha256:7b7df53f82d8320122da9368eee4ba4866df011c2add8b5032c2c0b0eb2aa001`；frontend `mls-frontend:p3-blockers-20260930` ID `sha256:1e0178fcf4670a49412acd8bdb0a085904bf8bf5d3cdee1adb5ed95bde7a10b2`。backend原Dockerfile网络握手失败／host停滞中断均留日志，最终从已verify JAR构建相同锁定JRE运行层；jar SHA256 `46c4ae8e7d86f026791191e90e58e5cb3eaeffeefbbe623b5444922794f5cc5e`。frontend原Dockerfile成功，Compose只读选镜像通过，未启动部署。
- 旧候选镜像与两旧完整patch不变，Windows工作树干净、旧复现库及真实模型配置未动，Testcontainers自有容器无残留。源码仍未提交，暂存为空，未push。后续Windows原生IAB优先三个阻断及支付／日期／AI相邻权限，两实际视口，旧通过按影响与身份说明复用边界。管理员AI导航扩展待选择，P3未通过，P4未开始。

## 2026-09-30 b72c 原生复验接收

- 已读取用户最新完整报告和原 P3 spec。b72c 候选 Windows 复验已实际完成，支付核心、同日借阅门槛、S6 日结／通知／恢复后继续流通及实际两视口已留证；P3 未通过，真实 AI 现场草稿缺 readerId，馆员罚款无读者筛选返回500为既存缺陷。旧报告、夹具误判和未执行边界均保留。
- 用户要求现场指挥规划管理并派 GPT-6 Luna·max 执行，已分别派发缺陷修复／回归与增量前端自动化。现行前端 Vue3／Vitest／Vue Test Utils、e2e Playwright 均已核对；不引入 React 测试体系，不擅自替代获批 IAB 验收方式。
- 两仓库已只读核对：共同 HEAD `7eedf0b`，Windows main 干净，权威工作树保留历轮未提交差异；未整合、提交或push。最新报告记载临时调度关闭、测试库开放、一次额度reset由周额度1%触发；这些为接收事实，未在本轮另执行。
- 默认 shell 通道失败后，显式 bash 的自动审批沙箱外执行已恢复；CodeGraph先尝试后确认不可用。当前 WSL Docker CLI 断链、无socket，已请求恢复；本轮修复／测试和新脚本运行结论尚未登记通过。

## 2026-09-30 readerId／罚款源码修复与技术回归完成

- Luna·max完成两个明确症状的红测、实现和相邻权限回归：馆员现场AI工具只允许可信馆员的根级目标readerId，schema、动作返回、保存事件均保持三字段；其它身份字段与角色拒绝保持。罚款控制器显式可空Long分支，馆员空筛选不再拆箱，读者仍强制本人。规范核对未发现新冲突。
- 最终聚焦16/16、完整Maven clean verify为44套件206/206、0失败／错误／跳过；指挥JUnit汇总一致。前端40文件222/222，运行代码未改。首次绿测Optional.null和非hex访客摘要为测试夹具误报，原日志保留；中间79无容器不冒充全量。原Docker连接断链在用户启动Desktop后恢复，真实PG/Testcontainers已执行，测试容器清理。
- 基于本轮verified JAR构建独立后端运行层镜像exit0，锁定JRE／用户／入口保持；未重跑Docker内Maven构建阶段。新镜像、JAR摘要及命令见镜像记录；前端未改运行源码，复用已核对b72c前端镜像。Compose示例配置只读选镜像通过，未启动部署。
- 未提交、push或操作Windows GUI；Windows真实模型和受影响IAB页面仍待新候选实跑，P3未通过、P4未开始。可选管理员AI导航范围未扩展。

## 2026-09-30 P3 增量自动化准备完成

- Luna·max已落地现有Playwright上的两缺陷子集、Windows原生执行守卫、事件／副本正常API读取、一次明确借出、独立run ID和安全摘要；Vue组件回归继续既有体系，未换React库、浏览器矩阵或IAB正式验收方式。设计及运行命令见e2e/P3-AUTOMATION.md。
- WSL Node22.23.2用例发现退出0、2份用例，语法／run ID检查通过；正式fines在WSL以退出2被守卫拦截，零浏览器启动。早先Windows Node24跨用WSL依赖的静态发现已排除，改回WSL独立依赖重验，不计任何Windows原生通过。
- 真实模型及新候选受影响IAB页面仍须切Windows原生执行；只应用而不明确提交不能登记完整P3-14通过。业务日来自Asia/Shanghai时钟，与GLOBAL日结进度分开读取，不照抄旧报告日期；一次借出后需先读回状态再准备新fixture。
- 新验证材料两份日志按既有动态认证值形态策略脱敏，复核退出0，测试事实保留；未动旧交付证据。完整候选的比较基线已按b72c哈希核验并在临时目录重建，供新修复归属对照。

## 2026-09-30 新完整／增量候选封存完成

- Luna·max从哈希核对过的b72c比较基线确认本轮后端四生产／三测试增量，前端运行源码不变；完整和相对b72c两patch已生成。完整含旧历轮工作，不全归本轮；各自SHA／尺寸／路径统计见candidate-seal.json，原b72c不变。
- 完整WSL反向／Windows干净HEAD正向、增量旧b72c正向／authority反向均只读exit0，Windows仍clean，真实index和HEAD未改变。首次增量临时头路径不规范已修正，只改patch头后复验，源码不变。过程记录更新后内容最终身份以seal记录为准，避免自引用hash。
- Windows接续只按适合基线的一份patch建独立候选，核对新后端ID／原前端ID／V13；先两缺陷自动脚本，再批准IAB受影响字段／确认与相邻续借取消、角色／双视口。原支付、日期及S6源码未因本轮修复改变，旧证据按实际身份和影响复用，不冒称本轮新页测。详细恢复次序见本地P3结果与e2e设计。
- 未提交或push，原库／调度／GUI未动；P3未通过，P4未开始。可选管理员AI导航仍不扩展。

## 未完成责任

本表与 [TODO.md](TODO.md) 当前待办按类别对应，逐项完成条件以 TODO.md 和 REV-0005 §3 为准：

| 类别 | 未完成责任 |
| ---- | ---------- |
| 缺陷修复 | R1a 登录 500 归因缺口（未归因、旧输入不可恢复）；005-b/007 Windows 实页与角色回归；DEF-M6-008/011/012 批次最终复核；闭馆旧 SQL 修复是否新立编号待裁定。P1 范围修复及直接影响复验已完成，逐项证据见 P1 结果。 |
| 集成与验收 | P1/P2 已完成；b72c 候选 Windows 支付核心、同日门槛和S6恢复后流通已留证，P3未通过。两项源码修复和206/222技术回归已完成，新镜像及完整／增量封存已准备，Windows新候选真实模型／受影响页面待补；另有恢复演练准入与P4运行／恢复。不测 Edge/Firefox、不做原九场景并发、不作计时稳定性声明。R7 最终确认仍待安排。 |

原型及 `artifacts/` 取证目录不入库；历史交接原文可经提交 `3181a9f` 恢复，不意味着本地证据已随 git 发布。

## 2026-10-01 P3 流程核对、脚本修订与非浏览器验证

已接收用户提供的 Windows 新候选报告。该轮真实模型三字段、双视口预填、单笔业务借出、读者权限和正确草稿取消通过；罚款脚本1/1，AI脚本因三处API状态断言错误失败；唯一POST和非空他人罚款隔离未证明，误续借的变化与证据继续保留。本轮权威WSL HEAD为7eedf0b、分支codex/delivery-closeout-p0-20260925且有多轮未提交改动；Windows主副本同HEAD且干净。没有将Windows干净状态误用于权威副本。

已修复三处API状态断言AVAILABLE／AVAILABLE／BORROWED，保留交易outcome=LENT；另以红测复现并修正发送包装结果的actions读取层级，避免有效草稿被误判为空。脚本自测7/7已纳入发现入口，发现2个正式用例。新增非空双方罚款的真实PG／HTTP归属测试、Vue表单经现有HTTP适配器和本地接收端的零／一次POST测试；HTTP设施移至platform/http/testing后边界检查通过，首次失败保留。

完整后端clean verify为44套件207/207、0失败／错误／跳过；最终前端40文件223/223，契约／模块边界／lint通过。JUnit XML已独立汇总。本轮无产品运行源码、迁移或运行配置修改，无镜像重建或部署。唯一POST证明限Vue／实际适配器与本地接收端；罚款归属证明限本轮真实集成栈，均不冒充Windows新页面或真实模型实测。

普通沙盒进程启动失败后，用户明确要求提权交自动审批；实际WSL Shell执行恢复，处理方式追加到本地AGENTS.md。Docker初始断链，用户启动Desktop后WSL socket恢复，测试使用独立WSL Node22.23.2。CodeGraph恢复可执行（此前命令不在PATH），未覆盖的前端路径回退定向读取。

本轮技术修订通过；是否跳过额外浏览器验收并接受分层证据仍待用户最终裁定，P3原口径保持部分完成、未通过，P4未启动。完整证据和本轮归属见开头修订记录；未提交／推送，未操作既有Windows候选数据库或GUI。

## 2026-10-01 用户批准缩减后的阶段收口

用户在本聊天明确决定跳过浏览器实测，并授权按顺序执行 P3/P4；另已确认管理员 AI 关闭导航与页面入口沿用现有范围，不扩大本次交付。P3 按该批准范围完成收口，不代表原浏览器全量验收通过。

采用既有 Windows 新候选真实模型、双视口、单笔业务借出及权限／取消实证，以及 2026-10-01 WSL 后端207/207、前端223/223、脚本7/7和契约／模块边界／lint结果。非空罚款隔离与零／一次POST采用真实PG／HTTP及Vue／实际适配器分层证据。修订后AI浏览器脚本未在Windows执行，浏览器未补测的断言作为已批准跳过项保留；原脚本失败与误续借事故不改写为通过。

P4 接收当前权威工作树与已记录的后端reader-target镜像、前端blockers镜像。P4仍须核对源码与镜像对应关系、从空环境部署、单轮性能、重启持久性、隔离备份恢复和核心业务续行。P3此处不提前登记任何P4通过结果。

## 2026-10-01 P4完成：运行可靠性与交付包

P3按本聊天明确批准的浏览器豁免与管理员入口维持收口。P4已完成独立空库部署、14表重启持久性、完整单轮P95性能（19067测量HTTP、0非预期失败）、修复后隔离恢复及14项HTTP续行，RPO 7秒／RTO 20秒。运行健康、迁移V1–V13成功，无应用异常；不设置计时观察。

恢复曾暴露模拟回调重启后sim-1与历史事件冲突，原失败保留；唯一新增生产Java修复为每实例UUID命名空间，同次重复回调身份不变。真实PG回归先红后绿，后端完整208/208通过。最终后端ID 544d2de436c6，前端ID 1e0178fcf467保持不变。用户明确接受修复前完整性能证据的影响复用，并要求补支付／恢复，现均已执行；没有伪称新镜像完整性能新轮。

独立解压包18项操作实测通过，另含26项准备与14项恢复核心HTTP，14表一致；包内JAR重建成功。交付重启脚本同时重启导致Nginx找不到backend，已按健康依赖顺序修正，重启14表摘要一致。最终包、SHA256、源码快照与未提交补丁、镜像及已知限制见artifacts/p4-delivery-20261001/package-seal.json及RUN-REPORT.md。仅本轮精确标签的临时项目已清理；保留mls-p4-restore-fixed-20261001于http://127.0.0.1:18096，私有.env.p4-restore-20261001未入包。

完整阶段结果为[results/P4.md](docs/agents/delivery-closeout-20260925/results/P4.md)，下阶段入口为[P5交付确认规格](docs/agents/delivery-closeout-20260925/P5-delivery-close.spec.md)。P5、提交、push、release、ECS与公网配置未执行。WSL权威工作树保留已有修改，Windows副本未整合本轮修复；无待回复范围问题。

## 2026-10-01 文档维护审批与传导

用户明确回复“A-E均批准”，并追加“新增功能要进行TLA形式化回归验证和CI验证”“走完整PR流程模拟开发场景”。规范决策登记为 MLS-DEC-0032，新规范 GOV-0008 v1.0 获批，窗口至 10-06。P5 为本次交付确认，独立审计由用户在维护后派发，当前不登记通过。

本轮已传导限定范围、注册会话 5 次/小时、每天开馆临时日期口径、日结后管理员重开、SHELVE/RESTORE 及 V13 日期、ADMIN 关闭与草稿角色边界；补齐受影响需求、设计、契约、验收、追踪与局部批准版本。旧完整基线、测试失败、未执行和旧登录未归因保留。仅文档修改，原产品源码、运行配置、CI 工作流和模型未修改。

只读检查确认 CI 仍为 frontend/backend/compose/observability 四作业，TLA 模型仍在本地忽略 verification。现行模型到新版规则的映射、OD 修改后的形式化重跑及 CI 接合未完成，不能把规则批准写成工具接合通过；新增功能以当前 PR 的模型/CI/评审及合并后同版部署为出口。

E 的限制接受仅适用于学习数据试运行：Cookie 额度可重置、内存延迟任务、旧登录归因缺口保留处置待办；日结后重开是管理员责任，RPO/RTO 是本次小库实测。目标机、访问范围、分发方式仍待定。未执行 P5、Git 提交/push、PR、远端 CI、release 或新目标部署。

维护前文档与 552 个 tracked/untracked 文件哈希保存在 artifacts/doc-maintenance-20261001/before.json；本轮增量、SHA、检查及审计入口由同目录报告提供，独立审计应与原已有产品修改分开核对。原 P4 交付包未改写，包内文档不含本轮规则；当前文档与旧包身份分别保留。

## 修订记录

- 2026-10-01：登记本轮 P2 与接合实现及本地实测、首次失败和原证据保全；发布与 P5 未登记完成。


- 2026-10-01：登记本聊天 A–E 及 D 追加批准、规范传导和本轮维护范围；明确独立审计/P5/形式化重跑/CI 接合/PR/新部署未完成，保全历史和产品修改。
- 2026-10-01：追加P4完成、恢复修复、用户性能复用、可运行包与资源归属；P5未执行。

- 2026-10-01：记录本聊天用户批准浏览器豁免、管理员入口维持及顺序执行P3/P4；ECS配置需要时告知。

- 2026-10-01：接收Windows新候选验证，登记脚本两类根因、207/223/7及lint、非浏览器证据边界和自动审批恢复；浏览器豁免未裁定。

- 2026-09-30（新候选封存）：登记完整／增量和只读双侧检查、真实归属／复用边界；Windows新页测、P3与P4未登记完成。

- 2026-09-30（自动化准备）：追加2份Windows专用用例、WSL静态检查和原生守卫，排除跨依赖尝试并保留真正页面未执行；登记新证据脱敏。

- 2026-09-30（readerId／罚款技术闭环）：追加16/206/222实测、夹具误报与Docker恢复、候选后端运行层和前端复用；保留Windows与P3未通过。

- 2026-09-30（b72c 原生复验接收）：追加最新通过子链与两项失败，登记Luna·max分工和现行测试栈，未将新修复或P3登记通过。

- 2026-09-30（独立候选交接）：登记新镜像及后端替代构建、完整补丁与双侧检查，剩余Windows实页与未答导航范围保持未完成。

- 2026-09-30（最终源码技术回归）：追加实际198/222、两处计数修订与服务端授权fixed的范围，Windows验收与候选封存未登记完成。

- 2026-09-30（最终权限裁定）：追加ADMIN关闭获准／契约同步、RESTORE沿用与存量回填答复，撤销当前待裁定；实施和最终验证仍在本轮边界。

- 2026-09-30（查询接线与日期取舍）：追加222前端、12/1自有查询、关闭与重放实红、存量延次日和RESTORE决定；新日期与关闭实现未登记完成。

- 2026-09-30（SHELVE 日期裁定）：追加当天上架允许／当天外借禁止的用户决定与执行派发；新日期约束尚未登记通过。

- 2026-09-30（阶段性技术闭环）：登记已实红的AI/归还修复及29/17聚焦、订单前端11和中间221全量；记录保留SHELVE入口与ADMIN/日期待裁定，不登记最终页面完成。

- 2026-09-30（关闭权限裁定）：追加读者本人／馆员他人关闭边界及 P3-12 相邻验证授权，独立调查已派发；实施和测试结论另行追加。

- 2026-09-30（Windows 续测接收）：登记最新候选实页、216 预检／183 上游引用、IAB 获批范围与协作方式、三个确认阻断及 Luna·max 派发；未登记本轮修复完成。

- 2026-09-29（本轮 WSL 修复收口）：追加五项实现及获批 AI 预填角色限制、183/216 技术回归、首次跨时区夹具失败与修复、独立镜像和保留资源；Windows 新候选实页与 P3 完成仍未登记通过。
- 2026-09-29（Windows 新候选现场核对）：追加完整复验报告、181/211 的候选归属、原缺陷复验通过、五个新缺陷与三项未执行断言；登记沙箱提权恢复执行通路及错误判断撤回，P3 仍未通过。
- 2026-09-29（P3 WSL 修复）：追加五项缺陷和 S9 AI 提示的权威副本修正、聚焦测试、首次失败及完整候选补丁准备；明确数据库、镜像、Windows 页面及最终候选复验仍未完成。
- 2026-09-28（P3 首轮接续）：登记 Windows 原生 Chrome 两种实际视口基础 smoke 18/18 的有限范围、用户视口替换决定、S3/S6 主链失败、四项其他页面问题与已批准规则核对；P3 仍未通过，Windows 资源保留待复现。
- 2026-09-27（P2 自动化回归收口）：登记最终后端 176/176 PostgreSQL/Testcontainers、前端 208/208、合同/场景/架构/ESLint、生产 build 及两张唯一候选镜像；保留首次失败和修复记录、P0 前工作树来源边界、临时容器清理与 Windows patch/Compose override 交接。P3 Windows Chrome 实页和整体交付仍未完成。
- 2026-09-27（P1 执行收口）：在原执行事实基础上更新 P1 状态为实施和定向复验完成、待现场指挥复核；补录续借最早有效预约日前一日截止、书目锁并发屏障、DESK 当日有册预约即刻取书、读者自助 walk-in 零写入与最终 93/93 PG/API 批次。登记前端直接影响测试、契约/lint/build、Testcontainers 清理和 Git 忽略状态；P2–P5 仍未开始，不新增整体验收结论。
- 2026-09-27（P1 现场文档复核）：复核 P1 实际结果、最终 93/93 PostgreSQL/API 批次、18 项前端直接影响测试、合同/lint/build 结果、工作树未提交边界和 P1 标签资源清理；确认 P1 收口完成。P2–P5、Windows 原生验收、运行可靠性和最终交付确认保持未执行。
- 2026-09-27（P1 执行中，历史阶段状态）：追加裁剪与核心修复的实际状态、用户本轮每日开馆/注册限流口径、V10–V12 与日结部署配置、当时 PG/HTTP 证据及首轮失败；当时 P1 尚需最终复验。该条是阶段性历史记录，当前状态以本节后续修订与 P1 结果为准。

- 2026-09-25（P0 范围与基线及质量决定）：登记双工作树与 `codex/` 分支实况、Docker Desktop/Compose 受控只读核验、P0 当前源码缺口和 126 GAP/29 BLK 核心影响分流；补录用户原话“性能与并发采用建议，浏览器只测试chrome，不观察计时。”及 Chrome-only、单轮性能、四类并发、无计时观察的边界；P4 运行/日志及恢复核验仍保留，P1–P5 暂估 3–5 个工作日且不含计时等待。P0 完成；未改产品代码、未执行测试/构建/浏览器，未开始 P1。

- 2026-09-25（阶段 spec）：登记六份本地执行规格及单阶段交接方式，不新增执行通过结论。

- 2026-09-25（限定范围交付规划）：登记用户业务裁剪与文档治理豁免决定，指向 TODO 完整执行计划；区分已批准原则、质量范围建议与未执行工作。

- 2026-09-25（Windows 定向复测与文档清理）：追加用户报告的真实端点 S1–S6、两轮问答、事件流、排版及窄屏实测结果；标明未覆盖检查点和完整 M6 验收边界。移除三份已完成旧交接报告的当前引用，原文保留于 Git 历史。

- 2026-09-25（Docker 启动后 AI 集成补验）：登记新增测试夹具修正与 `AiModuleTest` 在真实 PostgreSQL/Testcontainers 上 19/19 通过；保留此前 Docker 未就绪时的执行事实，不扩展为后端全量门禁或产品验收结论。

- 2026-09-25（WSL AI 助手与窄屏缺陷修订）：追加本轮源码实现与可运行测试事实；区分 Docker 集成测试阻断、Windows 原生页面复查与六浏览器正式验收未执行状态。未宣称产品验收通过。

- 2026-09-22（R0–R4 完整单次执行结果）：本节标题由「R0–R4 缩减协议单轮」改为「R0–R4 完整单次执行结果」，并按批次最终结果更新该节：批次后登记提交补入 `4cbb148`；R1a 一条由「受阻、诊断轮未开展」重写为两轮诊断已执行、归因分类＝未归因（含 Round 1 登录 200 与五条续行断言 PASS、旧镜像只读逐类对照下登录路径类与入口方法字节相同、Round 2 七条形态判别矩阵与 `PasswordHasher` 栈顶、缺失输入清单、对冻结候选的限定判断、两轮证据扫描结果）；R2 一条改为覆盖映射与准备核验均通过（AI 接线退出码 0、判定 `DERIVED`、`.env.acceptance` 已派生）并保留 AT-0030 资产与六浏览器缺口及两项运行件；R4 一条补入 AT-0003 v1.0 登记内容（`M6-演练-R4-01` 行、分条行改登、占位行名收敛、计数）并将 `at-0016/gates/teardown.exitcode` 残留句改述为「修正后的收尾核验为 ALL-PASS」；本节末条补「所有通过结论只存在于 AT-0003 v1.0」与 R1a 限定判断不可升格；「未完成责任」表两行按最终状态重写。只登记执行事实，未新增任何验收通过或批准结论。

- 2026-09-22（R0–R4 恢复批次执行后）：新增「2026-09-22 M6 恢复批次执行（R0–R4 缩减协议单轮）」一节，登记批次链八笔提交与 `79cc4b6`、R0 三子包、R1a 受阻、R1b 008/011/012 实施与两处既有闭馆 SQL 缺陷处置、R1c 缩减协议、R2 覆盖映射与 AI 接线受阻、R3 冻结候选与镜像摘要、R4 前置核验与 AT-0016 单轮判定、演练准入不满足与 AT-0003 v0.9 登记；同步刷新「未完成责任」表两行以对应 TODO 新状态。只登记执行事实，未新增任何验收通过或批准结论。

- 2026-09-22（用户确认）：追加新批次恢复与模拟支付 ADMIN 限定批准记录，更新未完成责任；实施、验收和最终签署状态不变。

- 2026-09-22：追加 A5 失败调查与恢复规划事实，更新未完成责任；不新增验收或执行授权结论。

- 2026-09-21：启动 M6 复核整改（W1）：新增「2026-09-21 M6 复核整改启动（W1）」小节，登记复核发现 R01～R19 全部成立、三项会话裁定（全量重置、环境确认、AT-0016 改登待复跑）与旧观察窗口归档作废；「已完成事实」日期戳更新至 2026-09-21。同轮统一当前结论：纠正阶段 4 节支付链回调证据表述（仅 run-3 记录回调数 10，run-1 未记录、run-2 审计查询报列不存在，证据不足）；未完成责任表移除已完成的 Grafana 实页取证、E2E-001/E2E-AI-001 与真实端点 smoke，按 TODO.md 对应补列 DEF-M6-007/005-b 缺陷修复项。

- 2026-09-21：登记 M6 阶段 4 执行（Env-P AT-0016 三轮全绿、Env-O 窗口启动与宿主重启停机恢复、观察五项、harness 修复链）；只登记执行事实，验收通过结论以 AT-0003 登记为准。

- 2026-09-20：登记 M6 验收阻塞裁定落账（DEF-M6-005 裁定方向 b·表单迁移、DEF-M6-007 新登记 Sev-2）与下一批次排期；lingzz 经 grill 两轮裁定，本页仅登记过程事实。

- 2026-09-19：补登 2026-09-18 两笔 AI 修复提交（`ee0ec24`、`e4eb4ee`）小节；「已完成事实」日期戳修正至 2026-09-18。仅登记提交事实，不新增批准结论。

- 2026-09-18：修复 B5 集成证据门禁：登录响应仅保留在一次性 scratch，curl 敏感头不进入命令参数，`rg --no-ignore --hidden` 覆盖 ignored evidence；补列真实模型端点 smoke 为 M6 后置项。

- 2026-09-18：登记后端 B5、匿名前端接合与自动化集成收口：后端 127 项、前端 193 项、基础 Compose 三阶段与 B5 跨侧 HTTP 门禁通过；Windows 实页、Grafana 实页、真实模型端点及 M6 产品验收保持未完成。

- 2026-09-17：登记匿名 AI 契约文档治理完成：批准提交安全快进至统一实现分支，原未提交文档修改以具名 stash 保留；确认 OpenAPI `info.version=2.0.0`、45 个唯一 operationId、14 个共同样例，且 CONTRACT-002 与完整 CONTRACT-003 按批准记录保持预期失败，统一实现分支仍不得进入 `main`。

- 2026-09-17：登记 lingzz 对匿名 AI 契约差异的明确批准；REV-0004 v1.5、REV-0005 v1.2、`MLS-DEC-0031` 与 OpenAPI 2.0 成为批准基线，OQ-0029 关闭。生成物、迁移、实现与完整门禁仍未完成。

- 2026-09-17：登记统一实现分支后端 B0～B4 的提交事实与证据边界；同步 OQ-0029、匿名 AI 契约 In Review、生成物漂移和后续迁移/后端/前端责任。不把候选契约、未复跑 B3/B4 或匿名实现登记为完成。

- 2026-09-15：登记前端 M5 收口（frontend-implement 分支）：45/45 操作接合核对、最终五项门禁与 180 项测试全绿、偶发失败登记与 maxWorkers 稳定化、Windows 侧移交事项与契约修订依赖；证据在 artifacts/m5-frontend-final/。执行代理：Zcode（前端）。
- 2026-09-15：登记前端 B5 交付（frontend-implement 分支）：AI 助手视图与 L1~L4 卡片呈现、L2 预填当前页续借模态并携 Ai-Draft-Id 走现行接口、取消零请求、额度耗尽呈现；五项前端门禁与 180 项测试通过，证据在 artifacts/m5-b5-frontend-run1/。执行代理：Zcode（前端）。
- 2026-09-15：登记前端 B4 交付（frontend-implement 分支）：闭馆恢复管理员视图（epoch 呈现、未追平阻断提示）、隔离期明确失败呈现；五项前端门禁与 167 项测试通过，证据在 artifacts/m5-b4-frontend-run1/。执行代理：Zcode（前端）。
- 2026-09-15：登记前端 B3 交付（frontend-implement 分支）：SETTLEMENT/REVIEW/DELIVERY 14 操作前端闭环（建单冲突、等待不伪造失败、退款待复核、复核目标重验、通知与审计）；五项前端门禁与 163 项测试通过，证据在 artifacts/m5-b3-frontend-run1/。执行代理：Zcode（前端）。
- 2026-09-15：登记前端 B2 交付（frontend-implement 分支）：CIRCULATION/QUERY 13 操作前端闭环（副本、预约五态、现场逐册借阅、取书、人工处置、续借、归还计罚、我的业务、到馆看板）；五项前端门禁与 143 项测试通过，证据在 artifacts/m5-b2-frontend-run1/。执行代理：Zcode（前端）。
- 2026-09-15：登记前端 B1 交付（frontend-implement 分支）：IDENTITY/CATALOG 13 操作前端闭环、统一请求结果模型、权限与错误状态组件测试、登录续行全链路；五项前端门禁与 111 项测试通过，证据在 artifacts/m5-b1-frontend-run1/。执行代理：Zcode（前端）。
- 2026-09-15：登记前端 B0 交付（frontend-implement 分支）：契约生成与 CONTRACT-001/002 前端门禁、platform/http 统一信封与双 Adapter、会话与登录续行、应用壳与守卫；五项前端门禁与 69 项测试通过，证据在 artifacts/m5-b0-frontend-run1/。执行代理：Zcode（前端）。

- 2026-09-14：补齐 OpenAPI/JSON Schema、调用方能力接口、稳定类型映射及前后端物理依赖门禁；REV-0005 v1.1 批准修订基线。生成客户端和 Controller 契约校验仍属 M5 实现任务。批准人：lingzz。

- 2026-09-14：登记 8 图复核、RISK-0009 职责、AI `thinking=disabled`、H5、OQ-0027 关闭及 M3/M4/模块设计基线 Approved。批准人：lingzz。业务实现和产品验收仍未完成。

- 2026-09-13：登记 9 月 13—15 日模块 spec 候选完成证据、47 项功能覆盖、9 个共同样例及正式冻结阻塞；同步未完成责任分类，不新增上游批准或业务实现结论。
- 2026-09-13：执行全量交叉审计与维护。已完成事实收敛为本页唯一登记区，未完成责任表与 TODO.md 六项待办一一对齐；删除场景分析九轮过程、形式化验证交接、修补轮阶段 2~10 等被取代的过程节，原文见 Git 历史。
- 2026-09-12：按 lingzz 后续补齐指令同步实证审计入口；Codex 记录。Docker 与迁移测试已执行，严格截止反例和受影响门禁待复核；不新增批准或产品验收结论。
- 2026-09-12：登记阶段 10 与本轮收口——编码准入达标、四工作包分工登记、十项清单完成与提交链、遗留责任表刷新。
- 2026-09-12：登记阶段 9 完成——DD 五工件＋REV-0004 评审通过、DEC-0008 登记、OQ-0027 关闭、V1 DDL 与迁移测试交付。
- 2026-09-12：登记阶段 8 完成——M3 出口交付物齐全（OD-0002 v0.12/OD-0004 v0.1），OD 评审经 REV-0003 通过（授权代录），AI 端点延期包就绪，GOV-0006/OD-0001/docs README 状态同步。
- 2026-09-12：登记阶段 7 完成——ODV-F01～F05 五项传导落六份工件（RA-0008/0010/0011/0014、AT-0002、REV-0002），新增 BR-0046~0048、FR-0047、AC-0050~0053、AT-0031~0033 及追踪行。
- 2026-09-12：登记阶段 6 完成——22 块全部无头渲染通过，截断/可读性启发式标记与真实浏览器复核推迟项登记。
- 2026-09-12：登记阶段 5 完成——四项复验通过、18 探针可达、勘误与 C1～C11 落地核对登记于 findings.md §九。
- 2026-09-12：登记阶段 4 完成——覆盖声明核验机制上线（空引/错引/游离声明均为 GAP），GAP≠0 退出码 1；结构检查对修订后文档 GAP=0。
- 2026-09-12：登记阶段 3 完成——C1～C11 全部落文（OD-0002 v0.11 / OD-0003 v0.2），正式行号 PK-D8/PAY-D15 分配并同步脚本，覆盖声明机制为检查器核验准备。
- 2026-09-12：登记阶段 2 完成——OrderOccupancy v2 四态+类型化请求事件模型、13 探针全 violated、9522 状态 No error、首命中统计上线并修正 PAY-D14 死行约束。
- 2026-09-12：登记修补执行轮入口——输入快照与 SHA-256 manifest（`artifacts/od-repair-20260912/`）、原验证报告归档（`verification/output/archive-20260911/`）、AI 端点与人工操作统一推迟决定、评审签署代录授权。
- 2026-09-11：登记形式化验证差距 F1～F11 的用户确认结论（C1-a～C11 全按建议采纳、A1～A10 符合）、三条业务口径、八项文档修订项与修补代理移交责任；验证资产留在不入库的 verification/，未产生 git 提交。
- 2026-09-11：按用户答复登记 SC-Q1～SC-Q15 逐轮确认结论；每轮均未将未答复选项视为批准，未扩展为完整 OD/DD 评审通过。
- 2026-09-11：回填 PR #2 实际合并结果、合并提交与 CI 通过范围，移除已完成合并待办；按用户要求将有效交接收敛至本文件，移除四份过时交接文档，历史证据固定在清理前提交 `3181a9f`。
- 2026-09-11：登记关系草图交付、Q1 用户确认及来源同步待办。

> 被引用文档：
>
> - [收尾阶段 spec 索引](docs/agents/delivery-closeout-20260925/README.md)
>
> - [P1 裁剪与核心修复规格](docs/agents/delivery-closeout-20260925/P1-core-implementation.spec.md)
>
> - [P1 当前结果](docs/agents/delivery-closeout-20260925/results/P1.md)
>
> - [P2 最终候选自动化回归规格](docs/agents/delivery-closeout-20260925/P2-automated-regression.spec.md)
> - [P2 当前结果](docs/agents/delivery-closeout-20260925/results/P2.md)
> - [P3 Windows 集中验收规格](docs/agents/delivery-closeout-20260925/P3-windows-acceptance.spec.md)
> - [P3 当前结果](docs/agents/delivery-closeout-20260925/results/P3.md)
>
> - [M6 验收恢复与完成计划](docs/agents/M6-RECOVERY-PLAN-20260922.md)
>
> - [项目入口](README.md)
> - [开发任务规划](TODO.md)
> - [工程文档唯一入口](docs/README.md)
>
> - [限定范围交付与 DevOps 阶段规则](docs/00-governance/MLS-DOC-GOV-0008.md)。
>
> - [本轮文档维护与独立审计入口](artifacts/doc-maintenance-20261001/REPORT.md)。
