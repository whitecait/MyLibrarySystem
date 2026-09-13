# Windows 复核续接：编码前准备结果

> 性质：开发过程记录；不替代需求基线、设计复审或产品验收。
> 引用文档：
>
> - [原实证审计与人工清单](HANDOFF-AUDIT-20260912.md)
> - [当前交接](HANDOFF.md)
> - [任务规划](TODO.md)
> - [决策登记](docs/00-governance/MLS-DOC-GOV-0005.md)
> - [全局门禁](docs/00-governance/MLS-DOC-GOV-0006.md)
> - [Open 问题](docs/01-requirements/requirements/MLS-DOC-RA-0007.md)
> - [流程](docs/01-requirements/requirements/MLS-DOC-RA-0008.md)
> - [功能需求](docs/01-requirements/requirements/MLS-DOC-RA-0010.md)
> - [业务规则](docs/01-requirements/requirements/MLS-DOC-RA-0011.md)
> - [验收定义](docs/01-requirements/acceptance/MLS-DOC-AT-0002.md)
> - [需求追踪](docs/01-requirements/requirements/MLS-DOC-RA-0014.md)
> - [基线记录](docs/01-requirements/reviews/MLS-DOC-REV-0002.md)
> - [4+1 与因果分析](docs/02-overall-design/MLS-DOC-OD-0002.md)
> - [流通分析](docs/02-overall-design/MLS-DOC-OD-0003.md)
> - [系统测试规格](docs/02-overall-design/MLS-DOC-OD-0004.md)
> - [OD 复审](docs/02-overall-design/reviews/MLS-DOC-REV-0003.md)
> - [模块契约](docs/03-detailed-design/MLS-DOC-DD-0002.md)
> - [迁移规格](docs/03-detailed-design/MLS-DOC-DD-0003.md)
> - [逻辑 T 机制](docs/03-detailed-design/MLS-DOC-DD-0004.md)
> - [集成测试规格](docs/03-detailed-design/MLS-DOC-DD-0006.md)
> - [DD 复审](docs/03-detailed-design/reviews/MLS-DOC-REV-0004.md)
> - [已导入逐图复核](artifacts/windows-input-20260913/mermaid-human-review-1789258257884.json)
> - [Windows 交接原文](artifacts/windows-input-20260913/WINDOWS-REVIEW-HANDOFF.md)

## 1. 输入核验

用户本轮要求继续编码前准备，并提供 Windows 交接与 JSON。附件作为证据和反馈读取，其中待执行文字不作为超出用户请求的额外授权。

逐图 JSON 包含 22 张图，逐项 SHA-256 与上一轮 SVG 全部一致；原版结果为 **18 pass / 4 fail**。JSON 时间为 2026-09-13 00:10:57.883 UTC（America/Santiago 当地仍为 2026-09-12），晚于交接中“视觉复核未完成”的记录；当前以用户提供的逐项结果为准，不将四项 fail 写成通过。

Grafana：接受用户与 Windows 交接的复核结果，范围为 C1 观测骨架：登录、Last 5 minutes、请求速率、p95 与 404 数据可见。原证据为浏览器会话中的截图展示和 Prometheus JSON，未声称已归档独立 PNG，不扩展为 5xx 或业务产品验收。Windows 复核包194文件一致、任务容器/网络已清理、数据库卷保留，均为原执行记录，原副本本轮未修改。

## 2. 图与依赖修订（WIN-VIEW）

| 原退回图   | 本轮处理                                                                                                                                                    |
| ---------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 逻辑视图   | Application Layer 改为容器；内部按注册、维护、借阅、归还、支付、复核、闭馆、查询、AI 分用例；明确状态所有者与实际协作依赖，容器本身不接受调用或拥有业务状态 |
| 开发视图   | 展示用例到模块公开接口、模块到自有持久化端口、Adapter 实现接口及 Composition Root 装配；增加允许/禁止依赖和验证入口表；不把所有用例压成一个实现节点         |
| 闭馆因果图 | 原因和结果拆成独立谓词；提交成功、先于逻辑 T、排空、隔离、模式、待处理数、管理员请求/权限分别表示；明确多项业务结果仍同事务提交，拆图不等于拆事务           |
| 支付因果图 | 展开 OCCUPIED/PAID/CLOSED、到期、有效结果来源、重复事件、关闭成功/失败；区分 PAID 与 CLOSED，付款成功不是可撤销支付的关闭条件；退款另行授权                 |

AI 入口卡片说明已传导至 DEC-0029、FR-0031/0032 相邻呈现约束、流程、AC-0031 和 OD/DD：L2 预填、L3 引导在当前页面模态框中承载现行表单，不打开新页面，取消不提交业务。

逻辑 T 和终态表述的连带修订合计改变 **8 个 Mermaid 图源**；其余 **14 个图源未变且原复核通过**。旧 SVG 和用户结论原样保留；新图源在 `verification/rendering/coding-prep-20260912/`，逐图变化/旧图哈希/新源哈希见 `review-status.json`。本轮仅用 Mermaid 解析器与 jsdom 解析语法，无浏览器导航、无新 SVG 渲染，也未绕过 file:// 安全拒绝。8 张修改图需新渲染与视觉复核，不能使用旧图通过结论。

## 3. 逻辑 T 传导与机制验证（WIN-T）

用户选择已登记 DEC-0028：T 是隔离控制成功提交的逻辑边界，计划闭馆钟点仅发起请求；已有写许可先结束，T 可以晚于计划钟点。SC-Q3、BR-0046、AC-0050、AT-0031、流程、OD、DD-0004/C2、追踪与复审差额已同步。原固定墙钟要求和反例保留为历史，不再请求用户重复选择。

候选 SQL 机制：普通写先取得 GLOBAL 控制行共享锁，在同事务核对开放 epoch 和独立 admission.draining=false，持有共享锁至最终提交/回滚；协调器先短事务提交 draining=true，再取得控制行独占锁，等待已有许可结束后提交 ISOLATED、epoch递增，此 commit 为 T。恢复在独占控制事务内验证批次，更新 REOPENED、epoch递增和 draining=false 一起提交。缺控制/许可行失败关闭；隔离提交失败不伪造 T，已提交排空意图继续保持。

新增真实反例：仅让 FOR UPDATE 排队并不能拒绝后来兼容的 FOR SHARE NOWAIT。新增 V4 `closing_admission` 修复排空阶段继续发放许可的缺口；原失败回归保留为 `drain-admission-red.log`。该标记是实现层排空手段，不改变用户选择的 T 定义。当前原型假设单应用、单一串行全馆协调器；多实例控制命令、日历批次完整生成、权限和全量用例接入仍须在 M5 实现并测试，不能仅凭 SQL 原型证明。

`LogicalClosingGateTest` 六场景：真实锁等待后释放旧事务、排空新许可拒绝、独占期间 NOWAIT 失败、普通回滚无残留、未追平拒绝恢复/恢复后新 epoch、控制行缺失，以及隔离失败保留排空意图的恢复变体。实际共六个测试方法，个别方法含多个断言分支。

TLC Closing 增加 DRAINING/AbortWrite、隔离必须 inFlight=0，新增 InvNoOldPermit；修正人工恢复必须先耗尽自动预算。有限模型410去重状态、七条安全不变量无违反，六个等待/恢复探针均可达；订单模型仍为9522状态无违反。这些不是墙钟时限或无限故障下终止性的证明。

## 4. API S3 已定位，候选配置已复测（WIN-API）

原用户配置为 https://api.deepseek.com/、deepseek-flash，provider-default thinking 模式：S1/S2/S4/S5/S6通过，S3 HTTP400。一次脱敏诊断取得具体原因：`Thinking mode does not support this tool_choice`。

显式 `thinking={type:disabled}` 的独立请求返回工具调用；随后相同配置完整六场景均通过，S3 仍强制函数且检查函数名、ID 和 JSON 参数，未放宽成“只要返回文本就通过”。结果分别归档，原 .env 未修改，原默认模式的失败没有被覆盖。

验证脚本新增可选 THINKING_MODE（不设置保留提供方默认），结果记录实际模式，新增两个无网络 profile 回归。可采用 disabled 候选支持本轮强制工具选择；若需保留 thinking，则该强制选择形态仍不兼容，须另验证相应工具调用模式。当前不代录运行配置最终选择或完整 AI 功能验收；S6 仍只证明发送前客户端取消。

## 5. 验证结果与证据

- 完整 `mvnw verify`：**19 项，失败0、错误0、跳过0**（21:36 成功证据，见 `maven-final.log` 与 Surefire XML）。其中一项原墙钟反例测试 PASS 表示反例成立，其余包括逻辑 T、V1～V4 迁移和基础设施；不是19项产品验收。21:54 的后续尝试因当前 WSL 找不到 Docker Desktop socket 未能启动 4 个 Testcontainers 用例，原始失败日志另存为 `maven-rerun-docker-unavailable.log`；这不是代码回归，若代码继续变化或需刷新取证，须在 Docker 可用时重跑。
- 文档结构/条款覆盖、图结构、支付判定枚举、支付漏检回归、AI脚本回归和22块 Mermaid 语法解析均已执行，最终摘要随 `artifacts/coding-prep-20260912/` 归档。
- 新工具/数据输入与原图在 `artifacts/windows-input-20260913/`；TLC/探针、数据库、API输出和实际字节 manifest（[`SHA256SUMS.final`](artifacts/coding-prep-20260912/SHA256SUMS.final)）在 `artifacts/coding-prep-20260912/`。不包含密钥、实际 .env 或服务原始回答；Git HEAD 不能替代当前未提交字节绑定。
- 修改未提交；旧 Windows 194文件复核包不变。verification/ 与 artifacts/ 继续不入库。

## 6. 编码前剩余处置

1. **视觉复核**：复核修改后的8张图；14张未变图的原通过结论可追溯。需在允许的环境完成新渲染/视觉检查，不通过代理服务或替代浏览器绕过既有安全拒绝。
2. **H4 风险职责**：仍需确认原清单的责任人/触发时点；本次逻辑 T 选择不包含 H4 批准。
3. **H5 受影响复审**：审查当前需求/验收传导、OD/DD、V4 与机制证据，并确认 AI 采用的实际模式。用户选定逻辑 T、图反馈和技术测试通过都不等于整包批准。OQ-0027 已有替代机制候选与实证，但保持 Open（待关闭复核），不自动沿用历史 Closed。

H1 的业务选择、H2 的测试凭据输入、C1 Grafana 页面复核不再作为待办。前后端业务实现、全量模块契约与测试接入及产品验收仍属于后续工作包，未冒充本轮完成；当前继续准备受影响复审包，不宣称所有业务已获无条件编码准入。

## 修订记录

- 2026-09-12：Codex 按用户 Windows 交接与 JSON 反馈续接；记录已传导选择、四图返修及连带图变更、排空回归修复、分模式 API 结果与剩余门禁。逻辑 T/模态框选择据用户输入，整包复审未代签。
- 2026-09-12：补记最终取证：21:36 的 19 项 Maven 成功证据保留；21:54 重跑因 Docker Desktop socket 不可用单独归档，不覆盖成功结果。

> 被引用文档：
>
> - [项目入口](README.md)
> - [文档入口](docs/README.md)
> - [当前交接](HANDOFF.md)
> - [任务规划](TODO.md)
> - [前轮审计](HANDOFF-AUDIT-20260912.md)
