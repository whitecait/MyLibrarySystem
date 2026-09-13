# 2026-09-12 后续实证审计与人工处置清单

> 收尾更新：Windows 交接、逻辑 T 选择、V4 排空机制、AI 分模式复测和 4+1 修订已继续登记于 [编码前准备收尾](HANDOFF-CODING-PREP-20260912.md)。本页 H1/H2/H3 的旧状态以该收尾页为准；H4/H5 和 8 张修改图视觉复核仍未完成。

> 性质：开发过程记录，不属于 docs 工程文档库；不新增 MLS-DOC 编号。当前入口为本页，先前签署和执行记录保留为历史。
> 引用文档：
>
> - [当前交接](HANDOFF.md)
> - [开发计划](TODO.md)
> - [全局门禁](docs/00-governance/MLS-DOC-GOV-0006.md)
> - [变更控制](docs/00-governance/MLS-DOC-GOV-0004.md)
> - [决策登记](docs/00-governance/MLS-DOC-GOV-0005.md)
> - [风险登记](docs/00-governance/MLS-DOC-GOV-0007.md)
> - [需求基线](docs/01-requirements/reviews/MLS-DOC-REV-0002.md)
> - [Open 问题](docs/01-requirements/requirements/MLS-DOC-RA-0007.md)
> - [需求追踪](docs/01-requirements/requirements/MLS-DOC-RA-0014.md)
> - [总体设计](docs/02-overall-design/MLS-DOC-OD-0002.md)
> - [场景分析](docs/02-overall-design/MLS-DOC-OD-0003.md)
> - [OD 评审](docs/02-overall-design/reviews/MLS-DOC-REV-0003.md)
> - [模块接口](docs/03-detailed-design/MLS-DOC-DD-0002.md)
> - [数据库与迁移](docs/03-detailed-design/MLS-DOC-DD-0003.md)
> - [事务与截止机制](docs/03-detailed-design/MLS-DOC-DD-0004.md)
> - [事件、缓存与观测](docs/03-detailed-design/MLS-DOC-DD-0005.md)
> - [集成测试规格](docs/03-detailed-design/MLS-DOC-DD-0006.md)
> - [DD 评审](docs/03-detailed-design/reviews/MLS-DOC-REV-0004.md)
> - [AI 端点执行说明](verification/ai-endpoint/README.md)

## 1. 审计结论与授权边界

输入 Git 为 `bff0f3d`（Zcode 收口提交），本轮起点工作区干净。lingzz 本轮指令授权核对并补齐可由代理完成的事项；真实浏览器移至 Windows 原生环境、真实 API 密钥由用户填写。用户确认原代录签署有授权，本轮不否定或删除那些历史签署。

Docker Desktop 实际已运行（29.7.2），原“符号链接悬空、仅编译通过”已不是当前事实。真实 PostgreSQL 测试发现并修复迁移和罚款约束错误；这些不能作为已经通过的旧证据。DD-0006 只有场景规格，业务集成代码未实现，`mvnw verify` 不能执行不存在的业务用例。

严格 T 截止的原实现证明被数据库反例推翻；OQ-0027 因新发现复开，受影响 REV-0003/0004 修订为 In Review，历史 v1.0 批准保留。M2 基线批准不删除，但“全部业务编码无条件准入”目前没有有效依据。SC-Q3 的 T 前实际提交要求保持不变，未擅自换成准入截止或逻辑时间。

## 2. 已完成补齐与证据边界

| 编号   | 发现与修复                                                                                                                       | 验证与边界                                                                                                                               |
| ------ | -------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| AUD-01 | V1 的 current_date 未引用，PostgreSQL 42601，迁移无法启动；只为该列加双引号                                                      | 原始失败日志保留；新库迁移通过。原 V1 不能成功应用，本轮为语法修复例外；不对已有成功 V1 环境自动 repair 或修改校验和                     |
| AUD-02 | 一读者一笔 DUE 罚款唯一约束超出 BR-0018，会阻止第二本逾期书归还；V2 删除错误唯一索引，保留 source_tx_id 唯一及非唯一读者欠费索引 | 多笔未缴罚款回归先失败后通过；V1→V2 保留已有罚款、允许第二笔，重复迁移无变化                                                             |
| AUD-03 | pg_indexes 文本断言假定省略 schema/括号/类型转换；改核查索引身份、唯一性及实际谓词                                               | 真实 PostgreSQL 迁移测试通过                                                                                                             |
| AUD-04 | 以聚合＋类型去重会吞掉后续同类型合法事件；DD 改为消费者＋event_id，V3 建立持久化消费回执表                                       | 两个不同事件、两个消费者、重复同事件数据库约束通过；消费效果与回执同事务、分发重试代码仍是 M5                                            |
| AUD-05 | AI S3 允许纯文本通过，S6 未中止也通过；S1～S5 无显式期限且模型有隐式默认                                                         | 强制并校验工具函数/参数，全部请求含响应体有期限，显式模型，S6 只证明客户端预中止；5 个无网络否定/正常自测。真实兼容性待人工填 key 后执行 |
| AUD-06 | DD 候补确认 token 无定义、无事务结果仍要求 txId、通知互斥范围过大、终态关闭暗示自动退款                                          | DD-0002 v0.2 补完整确认字段、null 语义、权限、HTTP、幂等作用域；超时/关闭终态吸收不产生退款，成功到 CLOSED 按已有 AC-0021 显式冲突异常   |
| AUD-07 | @Profile 被误说为编译排除、PromQL 有 ... 占位/status="2xx"、缓存删除被误说为数据库事务组成                                       | DD-0005 v0.2 改测试源码打包边界、完整查询、提交后失效及竞态回填限制；这些是实现规格，不冒充现有业务指标                                  |
| AUD-08 | RA-0014 下游全空，结构脚本反而要求它们永远为空                                                                                   | 回填 34 条核心 OD/系统测试规格/DD/集成测试规格引用，检查器验证所引文档存在；未实现 MD/UT 不伪造完成                                      |
| AUD-09 | DD-0006 号称 13 场景，实际 C1～C5/P1～P3/X1～X3/R1 共12个标识；规格被交接误写为代码交付                                          | 纠正数量与执行状态，补确定性闸门、三种崩溃交错、Outbox 不同事件与多消费者变体                                                            |
| AUD-11 | Python 支付枚举仍残留错误 A6，即使 TLA 已重构仍漏检；补显式 CLOSED/HAD_ORDER，零首命中与 GAP 使脚本失败                          | 两个反例先失败后通过；GAP=0，PAY-D7/D8/D14 首命中为 2/2/3。该子表仍仅覆盖三类守卫，不宣称其它不可结算请求已覆盖                          |
| AUD-10 | 普通 SELECT 控制状态不保证物理提交截止，串行化也不保证墙钟顺序                                                                   | ClosingGateCounterexampleTest 在 READ COMMITTED/SERIALIZABLE 均复现跨隔离提交。该测试 PASS 表示反例成立，不是业务 C2 通过；待人工 H1     |

最终执行结果登记在 §3。业务实现、未建立的业务测试、端到端和最终产品验收属于 M5/M6；本轮没有把这些常规开发任务列成“必须用户亲自执行”。

## 3. 复跑结果与可复现入口

本轮最终 Maven BUILD SUCCESS：**19 项，失败0、错误0、跳过0**（21:36 成功证据）。其中包含真实 PostgreSQL 迁移、逻辑 T 场景和严格截止反例；反例测试 PASS 表示旧方案反例成立，不计为业务截止验收通过。21:54 的后续尝试因当前 WSL 找不到 Docker Desktop socket 未能启动 4 个 Testcontainers 用例，失败日志保存在 `maven-rerun-docker-unavailable.log`，不覆盖 21:36 的成功证据；若代码继续变化或需刷新取证，应在 Docker 可用时重跑。文档结构246个ID/134条关系无错误，42条义务/84条款链接/20条查询通过；Mermaid结构GAP=0（22块，现行图块与22份渲染源逐一一致），判定枚举GAP=0，支付漏检回归2项通过，AI 脚本回归通过，disabled 候选真实端点六场景通过。

- Maven：`cd library-server && ./mvnw verify`，真实 PostgreSQL，不跳过 Testcontainers。最终结果见 `artifacts/coding-prep-20260912/maven-final.log` 与同目录 Surefire XML。
- 文档：`python3 scripts/check-review-docs.py`；条款：`python3 scripts/audit-g34-review.py --output-dir /tmp/mls-final-close-coverage-2`，摘要归档为 `artifacts/coding-prep-20260912/coverage-final.json`。
- 图结构：`python3 verification/extract_check.py docs/02-overall-design/MLS-DOC-OD-0002.md docs/02-overall-design/MLS-DOC-OD-0003.md`；判定表：`python3 verification/tables_check.py`。
- AI 验证脚本自测：`node --test verification/ai-endpoint/verify-endpoint.test.mjs`。替身结果不作真实端点证据。
- TLC 当前模型检查订单模型 60061 生成/9522 去重状态、闭馆逻辑模型 574 生成/410 去重状态，均 No error；本轮加入 DRAINING/排空不变量并重跑有限抽象。退款、墙钟/数据库提交实现、动态跨日扩大到期集合不在现有模型证明内，不能把模型原子动作当作实现证明。
- 输出 SHA-256 manifest [`SHA256SUMS.final`](artifacts/coding-prep-20260912/SHA256SUMS.final) 绑定本轮实际文档、迁移、测试、验证资产；未提交差异不能只绑定 HEAD。verification/ 与 artifacts/ 继续不入库，不复制本地密钥或依赖目录。

## 4. 当前有效的人工介入剩余清单

> 本表覆盖下方初始审计登记中的 H1～H5。H1～H3 的原始提问和处置说明仍保留在 §4A，作为审计轨迹；执行代理按本表状态办理，不按 §4A 的旧状态重复阻塞。

| 编号 | 当前状态                 | 必须人工动作                                                                                                                   | 代理接续与完成证据                                                                                     |
| ---- | ------------------------ | ------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------ |
| H1   | 已完成                   | 无需再次选择；用户已选择逻辑 T：计划钟点只发起排空，隔离控制成功提交为 T                                                       | 代理维护 V4、DD-0004/需求/验收传导，执行 PostgreSQL/TLC 回归；结果见 `HANDOFF-CODING-PREP-20260912.md` |
| H2   | 凭据执行已完成，模式待定 | 不再提供密钥；在实际接入前确认采用 provider-default（当前 S3 失败）还是 `thinking=disabled`（6/6 通过）                        | 代理按选定模式绑定统一配置并归档真实端点结果；不得把候选通过写成默认批准                               |
| H3   | 部分完成                 | 仅需在允许的 Windows 原生环境复核 8 张已修改 Mermaid 图；14 张未变图沿用原 18/18 通过中的对应结论。Grafana C1 已由用户复核通过 | 代理重新渲染、记录逐图结论并绑定副本 manifest；`file://` 安全拒绝不得绕过                              |
| H4   | 待用户确认               | 确认 RISK-0009 的复审责任人、风险接受人和触发时点；当前候选为 lingzz 负责并接受、代理维护证据                                  | 代理更新 GOV-0007 风险映射和阶段出口记录                                                               |
| H5   | 待受影响复审             | 审阅并批准逻辑 T/V4、8 图修订、AI 模式及需求/验收/OD/DD 传导包；不重复签署未改变的 M2 历史字节                                 | 代理按 GOV-0004 留存影响分析、复审结论并更新 REV-0003/0004 与 OQ-0027 状态                             |

**当前编码前门禁结论**：H3 的 8 张图、H4、H5 和 AI 实际模式确认未完成；其余本轮可由代理完成的验证已经完成。H5 通过后才可把受影响的 M3/M4 修订包作为 M5 输入。M5 业务实现和 M6 产品验收仍是后续阶段，不属于本表的人工补做项。

## 4A. 初始人工处置登记（历史，保留审计轨迹）

> 以下 H1～H3 记录形成于逻辑 T、Grafana 和 Windows 复核完成前；其技术细节仍用于解释本轮修订依据，状态以 §4 的当前表为准。

### H1 — 确认严格截止边界（历史登记，已由 §4 当前表处理）

原要求是固定 T 前实际提交完成，T 时未提交者失败回滚。已备反例及 DD-0004 §4 候选，不能只补一个“批准”解决技术矛盾。

- 可选候选：以全馆控制行独占锁＋隔离 epoch 提交为逻辑 T；普通写持共享锁到提交。它能确保隔离提交后无旧 epoch 提交，但会等待已有事务，可能晚于计划闭馆钟点。选择此项意味着受控修改 SC-Q3/BR-0046/AC-0050/AT-0031，不是纯实现调整。
- 若保持固定墙钟 T：明确保留原要求，当前方案退回；代理继续设计可满足所声明运行环境/故障假设的机制，未有证据前不登记 DD 出口通过。提前停收或提交前检查时钟不等于绝对提交截止证明。

用户只需确认是否允许逻辑 T 方案改变原业务边界；无需自行写 SQL 或锁代码。确认后的机制、追踪、回归由执行代理完成。

### H2 — 填写真实统一配置（历史登记，凭据阶段已执行）

1. WSL 本地 `.env` 已准备（已有内容保留，只补缺失字段），Windows 复核副本独立生成本地配置；两份均不入库。无需自行复制配置模板。
2. 填写 `BASE_URL`（含真实 API 前缀）、`MODEL`、`API_KEY`；Grafana 使用本地 `.env` 中独立生成或原有的 `GRAFANA_PASSWORD`，仅在登录页面输入。不要将 API_KEY 发到聊天、提交 Git 或写入截图。
3. 告知代理配置文件已就绪，由代理执行 `node --env-file=.env verification/ai-endpoint/verify-endpoint.mjs`，归档无凭据结果并完成端点形态决策。若服务缺少某能力，记录不兼容，不改测试制造通过。

完成判据：真实端点逐项结果、服务/模型配置指纹、失败处置和决策记录可追溯。客户端取消测试不代表真实慢端点测试或系统业务验收。

### H3 — 切换 Windows 原生任务并复核页面（历史登记，当前仅剩 8 张修订图）

复核副本在 `C:\home\PUBLIC_USER\src\MyLibrarySystem\review-bundles\followup-20260912`，包含本轮源码/文档快照、22 张 SVG、人工复核入口和 SHA-256 manifest；不覆盖 Windows 根目录的已有修改，不共享 WSL 依赖。

- 切换至 Windows 原生环境，在副本打开 `verification\manual-review\index.html`；逐图检查中文字形、截断、重叠、箭头与分支标签可读性，记录文件名及通过/失败。图结构和无头渲染不替代此项。
- 需要完整运行时，在 Windows 副本本地准备 Node 22、JDK 21 与 Docker 环境；依赖在该环境安装，不复制 WSL node_modules 或 target。
- Grafana：配置就绪后由 Windows 代理启动 `docker compose -f compose.yaml -f compose.observability.yaml -p mls-manual-review up -d --build`。人工登录本地 Grafana（模板端口 3000）检查数据源、看板和时间范围，截图时不包含凭据。代理记录健康请求、Prometheus 查询、对应时间窗口与截图证据并负责清理该独立 Compose 项目。
- 页面异常交由代理修复；不要求用户人工运行 Maven、收集日志或排查数据库。

完成判据：截图/结论绑定副本 manifest，Grafana 证据有真实数据时间窗口。产品业务页面尚未实现，不能用骨架看板取证宣称产品验收通过。

### H4 — 确认风险复审职责（治理决定）

RISK-0009 仍 Open。候选处置已具体化：lingzz 为风险复审责任人和接受剩余风险的批准人；执行代理维护证据及映射；每次阶段出口评审前、受影响需求/设计变更后、发现重大验证反例后复审；将 GOV-0007 稳定 RISK ID 映射至 OD 风险矩阵，记录处置、依据及仍有影响的范围，不能用 OD 的 R1～R10 替代原 ID。请确认此角色/时点方案或给出替代者；代理据此登记，不预填用户批准。

### H5 — 对具体修订包作受影响复审（批准，不重复原签署）

当前可审材料：工作区中 DD-0002～0006、RA-0014、OD/需求/验收传导的当前版本，本轮 V1 语法修复/V2/V3/V4 与实测证据，以及 REV-0003/0004 的差额修订；ODV-F01～F05 的业务确认沿用原授权，但 REV-0002 仍明确把传导条目标为 Draft，需要明确其需求变更批准结论并绑定精确版本。无需重签未改变的 M2 历史字节。

严格 T 部分须先处置 H1 并由代理完成设计/验证；不能借批准本轮其他修复把它一并认定通过。GOV-0004 规定“已批准内容发生变化时，旧版本保留追溯关系，新版本在完成影响分析和重新评审前不得冒充批准基线”，因此原代录授权不能代替对新反例及修订版本的实际复核。

## 5. 当前人工决定/复审后仍由执行代理承担

H1 的逻辑 T 机制、需求/测试传导和 PostgreSQL/TLC 证据已由代理完成；H2 的凭据执行已取得默认失败与 disabled 候选结果，仍需在 M5 接入前确认模式；H3 只剩 8 张修订图的 Windows 视觉复核；H4 后登记风险职责和矩阵映射；按 H5 批准范围更新受影响门禁。未实现的业务 API、完整业务数据模型、前后端、DD-0006 自动化和 M6 验收仍由执行工作包交付，不能交给用户手工补写，也不能以本轮迁移绿灯宣称全部实现完成。

## 修订记录

- 2026-09-12：收尾核对后新增 §4 当前有效人工清单，明确逻辑 T、凭据执行和 Grafana 状态已完成；H3 收敛为 8 张修订图视觉复核，H4/H5 与 AI 模式确认仍待人工处置。原 H1～H3 详细登记改标为历史审计轨迹。
- 2026-09-12：将 §3 的复跑摘要、TLC 状态、版本引用和 manifest 路径更新为本轮 `coding-prep-20260912` 最终证据；不新增批准或产品验收结论。
- 2026-09-12：补记 21:54 的 Docker Desktop socket 环境失败重跑；保留 21:36 的 19 项成功证据，不把环境不可用误写成代码回归。
- 2026-09-12：Codex 按 lingzz 后续核对与补齐指令编制；登记实测错误、已修复项、严格截止反例、原签署保留及五项人工处置。当前修订未提交 Git，未代录新批准。

> 被引用文档：
>
> - [项目入口](README.md)
> - [工程文档入口](docs/README.md)
> - [当前交接](HANDOFF.md)
> - [开发计划](TODO.md)
> - [AI 端点说明](verification/ai-endpoint/README.md)
> - [MLS-DOC-GOV-0005](docs/00-governance/MLS-DOC-GOV-0005.md)
> - [MLS-DOC-GOV-0006](docs/00-governance/MLS-DOC-GOV-0006.md)
> - [MLS-DOC-RA-0007](docs/01-requirements/requirements/MLS-DOC-RA-0007.md)
> - [MLS-DOC-RA-0014](docs/01-requirements/requirements/MLS-DOC-RA-0014.md)
> - [MLS-DOC-REV-0002](docs/01-requirements/reviews/MLS-DOC-REV-0002.md)
> - [MLS-DOC-REV-0003](docs/02-overall-design/reviews/MLS-DOC-REV-0003.md)
> - [MLS-DOC-DD-0002](docs/03-detailed-design/MLS-DOC-DD-0002.md)
> - [MLS-DOC-DD-0003](docs/03-detailed-design/MLS-DOC-DD-0003.md)
> - [MLS-DOC-DD-0004](docs/03-detailed-design/MLS-DOC-DD-0004.md)
> - [MLS-DOC-DD-0005](docs/03-detailed-design/MLS-DOC-DD-0005.md)
> - [MLS-DOC-DD-0006](docs/03-detailed-design/MLS-DOC-DD-0006.md)
> - [MLS-DOC-REV-0004](docs/03-detailed-design/reviews/MLS-DOC-REV-0004.md)
> - [README](docs/README.md)
