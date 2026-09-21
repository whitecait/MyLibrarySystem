# 当前交接记录

> 性质：开发过程记录，不属于工程文档库。本页是已完成事实在全库的唯一登记区；唯一待办清单见 [开发任务规划](TODO.md)，需求与决策的规范性事实以 `docs/` 工件为准。
> 引用文档：
>
> - [编码前准备收尾](HANDOFF-CODING-PREP-20260912.md)
> - [后续实证审计与人工清单](HANDOFF-AUDIT-20260912.md)
> - [文档交叉审计与维护报告](HANDOFF-AUDIT-20260913.md)
> - [开发任务规划](TODO.md)
> - [工程运行与验证](ENGINEERING.md)
> - [工程文档唯一入口](docs/README.md)
> - [模块设计评审与基线记录](docs/04-module-design/reviews/MLS-DOC-REV-0005.md)
> - [收口合并 PR #2](https://github.com/whitecait/Mylibrary/pull/2)

## 已完成事实

本节登记截至 2026-09-18 已确认的完成事实。被取代的历史过程细节已从本页删除，原文可依 git 提交历史追溯。

### 需求与阶段评审

- M2 需求分析已于 2026-09-11 经 lingzz 授权签署收口，需求基线 `v1.0 Approved` 由 `MLS-DOC-REV-0002` 激活。产品验收未执行。
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

审计对象、证据与人工处置清单登记于 [HANDOFF-AUDIT-20260912.md](HANDOFF-AUDIT-20260912.md)，编号 AUD-01 至 AUD-11。已处置项如下：

- AUD-01：V1 迁移 `current_date` 列名加双引号，修复 PostgreSQL 42601 语法错误。
- AUD-02：V2 迁移删除超出 BR-0018 的一读者一笔 DUE 罚款唯一索引。
- AUD-04：V3 迁移建立持久化事件消费回执表。
- AUD-10：严格 T 截止旧证明被真实 PostgreSQL 反例推翻，`ClosingGateCounterexampleTest` 复现该反例；OQ-0027 据此复开。

### 编码准备验证证据

详细证据与边界登记于 [HANDOFF-CODING-PREP-20260912.md](HANDOFF-CODING-PREP-20260912.md)：

- 逻辑 T 已由用户选择并登记为 `MLS-DEC-0028`：计划闭馆钟点只发起排空，隔离控制成功提交才是 T；已有写许可先结束，T 可晚于计划钟点。需求、OD/DD、验收与追踪已完成传导。
- V4 迁移 `closing_admission` 排空意图已交付，修复"仅靠行锁等待仍可发放新许可"的反例。
- `mvnw verify` 最终 19 项通过、失败 0。重跑因 WSL 无法连接 Docker Desktop socket 失败，日志存 `artifacts/coding-prep-20260912/maven-rerun-docker-unavailable.log`。
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
- 集成证据不保存原始访客 Cookie、Bearer token 或 Cookie 签名秘密；真实 Cookie 只在一次性临时目录中使用，清理时删除。Windows `E2E-001`、`E2E-AI-001`、Grafana 实页和真实模型端点 smoke 未执行，不构成产品验收完成。

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
- AT-0016 三轮独立运行全绿（2026-09-20 12:22–14:47 UTC）：24 个场景行全 PASS、零非预期失败、零越限阈值、三轮饱和判定清洁（六维判据）；检索四子场景各 361 有效样本；写路径真实性核验（每轮预约创建 110、支付链 10 含回调落地）。证据：`artifacts/m6-acceptance/phase4/at-0016/`（run-1~3＋env-p-compliance＋phase4-verdicts.json/summary）。AT-0003 v0.2 已登记三行通过记录与观察窗口进行中行。
- 数据集升级 seed-20260910-v2（v1 的 COPY NULL 双重转义修复，`5c2ebfa`），dataset.sql SHA-256 `203c8c31…5b0e`，字节级确定性复核通过，逐轮新鲜库装载。
- harness 修复六笔（首次真实执行暴露：k6 2.2.0 模块导出/路径、检索参数 `q=`、`titleId` 字段、预约/取消请求体、副本清单令牌与枚举、k6 容器 uid 导出权限、探针幂等键、凭据值集过滤），提交 `5c2ebfa`/`5f115cb`/`7f90800`/`81fded2`/`dbdf18c`。
- Env-O 上线：2026-09-20T14:55:00Z 窗口启动（项目 `m6-observation`、独立卷与端口 8091/19091/13001、配额 overlay、V1–V9 全 success、探针 30s 三探针全绿）；**宿主设备重启致计划外停机 14:56:30Z→2026-09-21T00:22:56Z（约 9.4 小时）已如实登记**并恢复；无预登记维护时段；窗口按 lingzz 裁定不真实等待、虚拟签署时点由其决定。
- 阶段 4 观察五项登记（详见清单执行记录）：非法 `/titles/{id}` 得 500；支付模拟器端点鉴权未强制（DEF-M6-007 关联观察）；副本 status 过滤语义；书目 availability 三态个案（title 1001）；audit/outbox/notification 链路未运行（AT-0014/0027 批次前必须核实设计意图）。

## 未完成责任

本表与 [TODO.md](TODO.md) 当前待办按类别对应，逐项完成条件以 TODO.md 和 REV-0005 §3 为准：

| 类别 | 未完成责任 |
| ---- | ---------- |
| Windows 验证 | Grafana 实页取证；功能实现后的 `E2E-001` 与 `E2E-AI-001` |
| 集成与验收 | 真实 OpenAI-compatible 模型端点 smoke；M6 系统测试和产品验收 |

原型及 `artifacts/` 取证目录不入库；历史交接原文可经提交 `3181a9f` 恢复，不意味着本地证据已随 git 发布。

## 修订记录

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
- 2026-09-13：执行全量交叉审计与维护。已完成事实收敛为本页唯一登记区，未完成责任表与 TODO.md 六项待办一一对齐；删除场景分析九轮过程、形式化验证交接、修补轮阶段 2~10 等被取代的过程节，原文见 git 历史；维护报告见 [HANDOFF-AUDIT-20260913.md](HANDOFF-AUDIT-20260913.md)。
- 2026-09-12：按 lingzz 后续补齐指令同步实证审计入口；Codex 记录。Docker 与迁移测试已补跑，严格截止反例和受影响门禁待复核；不新增批准或产品验收结论。
- 2026-09-12：登记阶段 10 与本轮收口——编码准入达标、四工作包分工登记、十项清单完成与提交链、遗留责任表刷新。
- 2026-09-12：登记阶段 9 完成——DD 五工件＋REV-0004 评审通过、DEC-0008 登记、OQ-0027 关闭、V1 DDL 与迁移测试交付。
- 2026-09-12：登记阶段 8 完成——M3 出口交付物齐全（OD-0002 v0.12/OD-0004 v0.1），OD 评审经 REV-0003 通过（授权代录），AI 端点延期包就绪，GOV-0006/OD-0001/docs README 状态同步。
- 2026-09-12：登记阶段 7 完成——ODV-F01～F05 五项传导落六份工件（RA-0008/0010/0011/0014、AT-0002、REV-0002），新增 BR-0046~0048、FR-0047、AC-0050~0053、AT-0031~0033 及追踪行。
- 2026-09-12：登记阶段 6 完成——22 块全部无头渲染通过，截断/可读性启发式标记与真实浏览器复核推迟项登记。
- 2026-09-12：登记阶段 5 完成——四项重跑通过、18 探针可达、勘误与 C1～C11 落地核对登记于 findings.md §九。
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
> - [项目入口](README.md)
> - [开发任务规划](TODO.md)
> - [文档交叉审计与维护报告](HANDOFF-AUDIT-20260913.md)
