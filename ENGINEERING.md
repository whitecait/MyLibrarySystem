# 工程基础运行与验证

> 引用文档：

> - [当前交接记录](HANDOFF.md)。
> - [流通及HTTP契约](docs/04-module-design/MLS-DOC-MD-0003.md)。
> - [形式化门禁](formal/README.md)。
> - [交付运维入口](scripts/delivery/README.md)。
>
> - [限定范围交付与 DevOps 阶段规则](docs/00-governance/MLS-DOC-GOV-0008.md)
>
> - [前端工程说明](library-frontend/README.md)
> - [后端工程说明](library-server/README.md)
> - [工程基础行动计划](https://github.com/whitecait/Mylibrary/blob/3181a9fad2951803abe3b2e4f38452bb0123e5a4/HANDOFF-B1-PLAN.md)

本入口用于最小 Vue 状态页、Spring 健康接口及 PostgreSQL/Flyway 基础设施验证，不包含业务功能或业务验收。

## 2026-10-04 当前源码增量

当前源码新增 V14 创建保证类型迁移。实际目标栈版本仍须检查 Flyway 历史和镜像身份，旧 V1～V13 包的运行证据不代替新包。半小时时段与创建保证类型的 PostgreSQL/HTTP 验证已完成。clean a1020a7 的完整后端 227 项、前端 262 项和共享 formal 79 个模型用例通过；两项独立初审规范发现已实施领域类型与应用层通知拼接整改，18 项定向测试通过；整改冻结提交 5e274c3 的完整后端 51 类、234 项已通过，独立复审尚未完成。当前没有新提交 CI、同版包或 Windows 部署结论。定向过程事实见 [当前交接记录](HANDOFF.md)，新增契约见 [MD-0003](docs/04-module-design/MLS-DOC-MD-0003.md)。

本次交付拓扑沿用根 Compose：只有 frontend 的 Nginx 端口发布到宿主机环回，UI 与 API 使用同一入口。API 经 `/api/` 转发到同项目的 backend:8080。Vite 预览和旧 API 的组合不作为同版交付证明。

## 2026-10-01 当前适用范围与批准修订

lingzz 于 2026-10-01 明确批准 A–E，并追加新增功能 TLA 形式化回归、CI 验证及完整 PR 流程要求。本节与[限定范围交付与 DevOps 阶段规则](docs/00-governance/MLS-DOC-GOV-0008.md)共同限定当前适用内容；下文相冲突的范围、角色、状态和准入表述保留为原基线历史，其余约束继续有效。批准修订与执行验证分别登记。

当前 CI 源码包含前端、后端、Compose、观测及 formal 五作业；formal 可由干净检出获得模型、配置及固定工具入口，参见 [形式化门禁](formal/README.md)。本地结果与 PR 当前提交及合并提交的远端 CI 分别登记，工作流存在不等于远端运行通过。

当前 Compose 已承载 V1～V13 业务迁移和完整应用；根服务入口仍仅本机环回。用户已确定初次交付为 Windows 本机环回访问与专用学习数据，运行配置按目标环境维护。日结后 ADMIN 受控重开；备份/恢复及版本输入按 P4 实证与新部署检查分别记录。

## 2026-10-01 接合实现与本地实测

- 后端完整 verify 为 208／208，无失败、错误或跳过；WSL Node 22.23.2／npm 10.9.8 下前端 lint、223／223 和生产构建通过。
- 当前共享模型两组安全检查、22 个独立出口探针以及真实 TLC 失败传播自检通过；结构及判定表 GAP=0。模型不覆盖全部身份、日期、通知或 AI 语义；映射与边界见 formal/README.md。
- Compose／跨侧集成的旧 V9 断言已改为与源码迁移清单严格比较；迁移拒绝测试通过。观测入口补 Cookie 密钥并在写配置证据前脱敏。
- Windows 原生运维入口与干净提交打包入口已实现，见 [交付运维](scripts/delivery/README.md)。独立 Linux 运维试验通过，未计为 Windows 原生通过。
- 本地标准 Compose 首轮构建失败：容器中 Maven Wrapper 下载发生 SSLHandshakeException，三段 Compose 未完成。本地失败证据保留；远端 CI、独立 PR 审计、合并、目标机实测与 P5 另行登记。

## 2026-10-02 e1冻结候选实际出口

PR run36978456313／push run36978451716五个作业全部成功，固定head为e1c73916cf19dc034726da9da87976b7a868139c；形式化PR检出合并候选5128e5e221fd43ab4a99675c39846b290512d5ff，分别记录两个身份。全部CI产物已封存；独立Standards／Spec整改复审通过。候选包20项WSL操作通过，包含恢复后核心HTTP取消预约写入。本节仅对应所列冻结输入，不声明合并提交、Windows原生或P5结果；最新提交材料见本轮执行报告和PR #4。

## 2026-10-02 增量复审验证

完整后端 verify 215／215、真实 PG 七项通过；混合同号分页在旧冻结 a51cf7f 上一项断言失败，在修复后通过。交付六项和宿主 JDK 身份三项拒绝测试、迁移两项及 Compose 配置隔离通过。两个远端轮次四作业成功，观测最新失败为固定模板 URI 白名单遗漏；修订后的全部五作业尚待新固定提交结果。候选包、合并提交、Windows 与 P5 不能以这些测试代替。

## 2026-10-02 独立审计整改验证

本轮新真实 PG 回归六项在原冻结提交上五项失败，在修复后六项通过；完整后端 verify 为214／214。观测配置隔离不继承真实模型参数，保存证据前脱敏并有实际 Compose config 负例。基架以已存在书目查询的404来校验预期4xx，并对两个选定 API 路径计数合计，四项初次远端 CI 已成功，最新全五项另按冻结提交核对。

WSL 本轮重新编译需要完整 JDK21：默认 java 是 JRE21而默认 javac 为17，不能据 java -version 推定编译器可用。独立临时 Temurin21.0.8 JDK 来自项目固定容器镜像；仅用于 WSL，不修改系统或共享 Windows 工具链。打包 host 模式从同提交源快照编译并使用固定 JRE镜像，显式记录构建方式及摘要。

## 所需环境

在 Linux 或启用 Docker Desktop 集成的 WSL 中运行。需要 Bash、Git、Python 3、curl、Docker Linux 引擎及支持 `up --wait` 的 Compose v2 或 v5；后端测试必须可连接 Docker，不能跳过数据库测试。

- 本机：Temurin JDK 21（已有环境 21.0.12.1）、Node.js 22.23.2、npm 10.9.8。
- CI 和后端容器：Temurin 21.0.8+9（setup-java 目录中的完整标识为 `21.0.8+9.0.LTS`）；与本机保持 Java 21 编译目标，补丁版本有差异。
- Maven：仓库 Wrapper 3.9.9，无需系统 Maven。
- 前端容器：Node 22.23.2 / npm 10.9.8，静态服务 Nginx 1.28.0。
- 数据库：PostgreSQL 17.6-bookworm，Compose 与 Testcontainers 共用 `sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3`。

Dockerfile 的所有基础镜像均固定标签和摘要。第一次执行需要访问 npm、Maven、Docker Hub；CI 的 Actions 和工具下载还需要访问 GitHub。依赖由 npm 锁文件、Maven POM/BOM 与 Wrapper 固定。复现表示执行过程与断言结论一致，不表示产物字节相同。

## 一次完成工程检查

从仓库根目录执行：

```bash
bash scripts/verify.sh
```

脚本顺序执行 `npm ci`、非修改式 lint、Vitest 测试、前端生产构建、后端 `clean verify`、基础 Compose 验收与 B5 跨侧集成验收；任一步失败即返回非零。2026-09-18 的基线结果为前端 193 项测试、后端 127 项测试通过，基础 Compose 三个阶段均报告页面、代理、SQL 与 V1～V9 迁移断言通过，随后跨侧集成脚本验证匿名 AI、模型端点与现行续借提交。后端无 Docker 时明确失败。

仅执行容器验收：

```bash
bash scripts/verify-compose.sh
```

该脚本首先执行 `test-database-readiness.sh`，验证实际配置的健康命令会拒绝仅监听 Unix socket 的 PostgreSQL 服务，然后执行完整启动链。它不读取本地 `.env`，自行生成一次性数据库口令、随机宿主端口及两个唯一 Compose 项目名。验证次序为新空卷启动、保留该卷并重建容器、另一新空卷启动。每次检查三个服务健康、首页及页面 JS 内容、前端代理 `/api/health` 的精确 JSON、`SELECT 1` 与唯一成功迁移；保留卷重启前后比较完整迁移历史。

正常结束和失败退出均保存脱敏日志，并清理这两个验收项目的容器、网络和卷；不清理开发项目。日志默认保存在被 Git 忽略的 `artifacts/compose-<运行号>/`，可用 `EVIDENCE_DIR` 改变位置。退出码记录在 `result.txt`。强制终止进程或 Docker 引擎失联可能使自动清理无法执行；此时按该次证据目录中的项目名执行 `docker compose -p <项目名> down --volumes`，不得清理其他项目。

## B5 跨侧集成验证

```bash
bash scripts/verify-integration.sh
```

该脚本以 `compose.yaml` 加 `compose.integration.yaml` 启动隔离项目，模型边界由独立的 OpenAI-compatible HTTP stub 提供，业务数据库使用真实 PostgreSQL。验证请求全部经前端 Nginx `/api` 代理进入后端，覆盖匿名启动与 Cookie 连续消息、错误 Cookie 的 404 不可见性、Bearer 不提升既有访客会话、两访客共享额度的并发 CAS、登录读者的 L2 续借预填及现行续借提交，并在重启 backend/frontend 后复核事件与额度留存。stub 同时拒绝非 `thinking={type:disabled}` 请求。

脚本自行生成临时数据库口令、Cookie 签名键、随机宿主端口和唯一 Compose 项目名；原始 Cookie、Bearer token 与签名秘密只存在于临时目录，不写入证据或命令参数。成功或失败退出时，脚本都会动态脱敏日志，并以 `rg --no-ignore --hidden` 显式扫描被 Git 忽略的证据目录。证据保存在 `artifacts/integration-<运行号>/`，退出时清理隔离容器、网络和卷。该门禁不调用真实模型服务，也不替代 Windows 原生浏览器场景。

## 观测验证入口（C1 测量脚手架）

```bash
bash scripts/verify-observability.sh
```

该脚本使用基础文件加观测 overlay（`compose.observability.yaml`）启动专用 Compose 项目：在既有 database/backend/frontend 之上接入 Prometheus、Grafana 与 OTel Collector（镜像标签与摘要固定在 overlay 中），backend 仅在该 overlay 下经 `MANAGEMENT_OTLP_TRACING_ENDPOINT` 向 Collector 导出 trace；不带 overlay 的启动行为不变。脚本验证七项内容：全链路启动后 Prometheus target UP 且真实请求使计数增长、Grafana 数据源与面板查询返回非空数据、真实请求的 Trace ID 出现在 Collector 导出、固定窗口内 k6/nginx 访问日志/应用指标三方差额可解释、错误分类口径（成功、预期 4xx、非预期 5xx、超时）与隔离夹具证明、确定性阈值失败返回非零且恢复后通过、停止观测组件后健康请求仍完成且恢复后采集恢复。指标标签基数与敏感信息扫描随后执行，最后重跑 `scripts/verify.sh`（CI 中由同提交的兄弟作业覆盖，可用 `SKIP_EXISTING_GATES=1` 跳过）。

k6 负载参数为脚手架验证参数，不构成已批准业务负载；Grafana 验证走 API，不代表浏览器交互通过。观测栈的 Collector 导出终点为 Collector 自身日志（debug），无可查询存储、重启即失，正式链路存储平台不在本轮范围。证据保存在被 Git 忽略的 `artifacts/c1-<运行号>/`，可用 `EVIDENCE_DIR` 改变位置。

## 启动开发环境

```bash
cp .env.example .env
# 编辑 .env 中的 DB_PASSWORD，再执行：
docker compose -p mls-dev up --build -d --wait --wait-timeout 180
curl --fail http://localhost:8088/api/health
```

打开 `http://localhost:8088` 进入应用。只有前端端口发布到宿主机，且仅绑定 `127.0.0.1`；数据库与后端通过 Compose 内部服务名连接。`FRONTEND_PORT` 可修改前端宿主端口。Nginx 将 `/api/` 请求转发到 `backend:8080`，保留前缀，因此 `/api/health` 到达后端仍是 `/api/health`。

数据库使用 `pg_isready -h 127.0.0.1` 检查 TCP，避免将首次初始化期间仅监听 Unix socket 的临时服务误判为可供后端连接；后端在数据库健康后启动并执行业务 Flyway 迁移，当前源码包含 V1～V14，健康检查为 `/api/health`；前端在后端健康后启动。旧工程骨架的占位说明属于历史。镜像构建只编译打包，Testcontainers 测试由独立验证命令和 CI 后端作业执行。

停止开发环境并保留数据：

```bash
docker compose -p mls-dev down
```

`.env` 不入库；镜像构建采用输入白名单，不复制原型、node_modules、target、测试夹具或本地配置。

## GitHub Actions

`.github/workflows/ci.yml` 覆盖面向 main 的 PR、main 和 `codex/**` 分支 push，以及手动触发。四个独立作业 `frontend`、`backend`、`compose`、`observability` 必须全部成功；runner 为 Ubuntu 24.04，Actions 使用提交摘要。前端与后端保留测试报告，Compose 保留脱敏日志、HTTP/SQL 断言材料、镜像和环境信息，观测作业保留 C1 验证证据，保留期 14 天。各作业有超时，失败不被忽略。

原型仍为本地资产，不进入 CI、镜像或干净检出的测试输入。远端通过证据及对应提交记录在行动计划中；工作流存在不代表运行通过，也不代表已配置 GitHub 分支保护。

## 修订记录

- 2026-10-04：登记干净5e274c3的完整后端234项通过及未变输入桥接，保留复审、当前CI与Windows/P5未完成门禁。

- 2026-10-04：登记限流断点恢复、独立初审与两项规范整改的定向结果；整改候选完整回归和复审未预填通过。

- 2026-10-04：登记 5e62 旧测试失败、独立诊断与 a102 冻结完整回归通过；当前 CI、评审、同版运行与 P5 仍另行留证。

- 2026-10-04：追加 V14 源码增量与同入口 Nginx 发布拓扑，保留旧运行证据的版本边界；未登记新 CI 或实机通过。

- 2026-10-02：记录e1候选五作业、两轴复审和20项运行实证，以固定SHA明确复用边界。

- 2026-10-02：追加增量审计的215项后端、混合分页红绿及打包身份保护实测。

- 2026-10-02：追加五项审计整改的真实回归、JDK边界和观测计数修复，不预填新CI／发布通过。

- 2026-10-01：登记形式化／迁移／原生运维入口和本地测试，明确容器 TLS 失败及 Windows／远端未完成边界。


- 2026-10-01：同步实际业务迁移、日结重开责任及新阶段 TLA/CI/PR 门禁，明确 TLA 尚未接入 CI；未修改工作流或执行新远端检查。
- 2026-09-18：加固 B5 集成证据边界：敏感响应不再复制到证据，Cookie 与 Bearer 改由临时文件传给 curl，退出时显式扫描 ignored evidence。

| 日期       | 内容                                                                                                                                                          |
| ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 2026-09-18 | 新增 B5 跨侧 Compose/API 门禁与脚本化 OpenAI-compatible HTTP stub；根验证现包含匿名所有权、共享额度并发、L2 续借预填/提交及重启留存验证。                    |
| 2026-09-10 | 新增 C1 观测验证入口：观测 overlay、固定摘要镜像、Micrometer/OTel 后端接线与 `verify-observability.sh` 七项验证；CI 增加 observability 作业（保留原三作业）。 |
| 2026-09-10 | 项目入口已链接本文，补齐反向引用登记。                                                                                                                        |
| 2026-09-09 | 干净检出发现数据库初始化期 socket 健康误报；检查改为 TCP 并加入直接执行 Compose 健康命令的回归测试。                                                          |
| 2026-09-09 | 新增工程基础命令、容器环境、验收隔离与证据说明、CI 触发及报告约定。                                                                                           |

- 2026-09-11：按用户要求清理过时交接文档，历史引用固定至提交 `3181a9f`；仅维护引用位置，不改变需求、业务值或既有批准结论。

> 被引用文档：
>
> - [当前交接记录](HANDOFF.md)
> - [项目入口](README.md)
> - [前端工程说明](library-frontend/README.md)
> - [后端工程说明](library-server/README.md)
> - [工程基础行动计划](https://github.com/whitecait/Mylibrary/blob/3181a9fad2951803abe3b2e4f38452bb0123e5a4/HANDOFF-B1-PLAN.md)
>
> - [限定范围交付与 DevOps 阶段规则](docs/00-governance/MLS-DOC-GOV-0008.md)。
>
> - [本轮文档维护与独立审计入口](artifacts/doc-maintenance-20261001/REPORT.md)。
