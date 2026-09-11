# B1-A/B1-B 工程基础行动计划

> 引用文档：
>
> - [开发任务规划](TODO.md)（B1-A、B1-B 与本轮范围补充）
> - [工程基础运行与验证](ENGINEERING.md)
>
> 状态：2026-09-10 P0～P5 已完成；B1-A/B1-B 已取得同一提交的本地干净检出与远端三作业全通过证据。当前验收提交为 `6fd8a8a0ffbe4404adc0dfb9e057059385496990`，详见 §7C。下方失败与待验收表述均为分轮历史记录。B1-C、业务验收与需求门禁不在本次完成范围。

## 1. 目标与范围

本轮用户已要求清理低复用价值的旧前后端实现，保留前端原型，重建最小工程基础。交付目标为：同一提交在 WSL 和 GitHub Actions 中可以按明确命令完成前后端构建、测试与 PostgreSQL 最小验证。

本计划覆盖 B1-A/B1-B；监控、压测和性能阈值验证（B1-C）、业务功能重写、正式数据库模型及正式 V1 迁移不在本轮。最小工程通过不等于业务验收通过。

既有计划中“只用现有代码验证”的约束与本轮先清理再建立骨架的指示存在差异，本轮执行范围以用户当前指示为准；执行者应登记该差异，不把旧业务测试修复作为重建前提，也不擅自宣称正式治理文件已经修订。

## 2. 静态调查事实

| 检查对象          | 当前事实                                                           | 执行影响                                                                             |
| ----------------- | ------------------------------------------------------------------ | ------------------------------------------------------------------------------------ |
| 工作区            | 多份文档与两个前端页面已有未提交修改                               | 清理前保存完整恢复材料；不能用 HEAD 代替未提交内容                                   |
| CI/容器配置       | 未发现 `.github/`、Dockerfile 或 Compose 配置                      | 按新建工作流与容器配置规划；远端历史 CI 状态尚未调查                                 |
| 后端 POM          | Spring Boot 3.5.0、Java 17、MySQL/H2，未引入 Flyway/Testcontainers | P2 将编译目标统一为已批准的 JDK 21，并建立 PostgreSQL 最小依赖链；不顺带升级整个框架 |
| Maven Wrapper     | Maven 3.9.9，Git 中 `mvnw` 为 100755                               | 无需重复修复已正确的执行位                                                           |
| 前端 package.json | Vue/Vite，npm 锁文件；lint 带 `--fix --cache`，无正式 test 命令    | 引入不修改文件的检查和最小页面测试                                                   |
| 数据库模板        | H2 默认配置、MySQL profile、Hibernate `ddl-auto: update`           | 清除旧配置，让 Flyway 单独负责迁移                                                   |
| Git 忽略规则      | 根目录白名单；原型与 application.yml 被忽略                        | 新增 CI、Compose、脚本和无秘密配置必须显式纳入追踪                                   |

这些事实来自当前工作区的配置与目录检查，不是运行故障诊断结果。Docker 仅已知由用户安装在 Windows 侧；WSL 集成是否启用尚未验证。

## 3. 环境方案

建议采用：WSL 保存源码并运行 JDK、Maven Wrapper、Node/npm 和 Docker CLI；Windows Docker Desktop 提供 Linux 容器引擎，并开启当前 WSL 发行版的集成。

- Compose 管理开发用 PostgreSQL、后端骨架和前端骨架。
- Testcontainers 自行创建临时 PostgreSQL，用随机映射端口，测试结束销毁；不连接 Compose 的开发库。
- 无需预先在 WSL 安装 PostgreSQL 服务。若用户另有学习本机安装的需求，可单列环境练习，不作为本轮验收依赖。
- WSL 上运行的应用通过发布的 localhost 端口访问 Compose 数据库；容器内后端通过数据库服务名访问，不能写 localhost。
- 工具链统一使用 JDK 21（2026-09-09 用户批准；当前实测 Eclipse Temurin OpenJDK 21.0.12.1 LTS）和 Maven 3.9.9；Node 支持范围固定为 `>=22.12.0 <23`（下限来自当前 Vite/Vitest 的运行要求，当前实测 22.23.2）。P2 将 Maven 编译目标从旧 POM 的 Java 17 改为 21，P4/P5 的构建镜像与 CI 使用同一主版本。执行者固定确切工具版本、PostgreSQL 镜像版本及摘要，使 Compose 与 Testcontainers 一致；禁止使用 latest。依赖锁文件必须提交。

Docker CLI 能显示版本只证明客户端存在。必须能连接服务端并实际启动 Linux 容器，才具备 Testcontainers 的运行前提。

## 4. 顺序行动包

### P0：保存现场并确定删除边界

执行者先记录提交 SHA、Git 状态、现有差异、被忽略和未追踪资产。对原型目录及其依赖资源做仓库外备份，生成文件校验清单，验证备份可读取。不得运行 `git clean -fdx`、整库 reset 或无差别暂存。

逐项列出“保留、删除、替换”路径：保留文档、原型、原型引用的共享资源和必要构建工具；旧前端 views/api/router/layouts 与后端业务 controller/service/entity/repository、旧业务测试列为清理候选。删除前检查原型对 src、公共资源及 npm 依赖的引用，先解除依赖或保留必需部分，不能只保护 prototypes 文件夹就认定原型已保护。

通过条件：恢复材料覆盖未提交修改和被忽略原型；清理清单明确；后续操作不覆盖其他文档修改。建议在独立分支分批提交，禁止将整个工作区一起提交。

### P1：验证 WSL 与 Docker 链路

执行者在 WSL 记录 `java -version`、`./library-server/mvnw -version`、`node --version`、`npm --version`、`docker version`、`docker info`、`docker compose version`，并启动一个一次性 Linux 容器。

若服务端不可达，由用户打开 Docker Desktop，确认 Linux containers、WSL2 引擎及当前发行版集成，再由执行者复验。不要并行安装第二套 WSL Docker daemon，也不要以暴露无认证 TCP 端口解决连接问题。

通过条件：WSL 能创建并移除容器；记录实际引擎、Compose 和工具版本。失败时保留错误及检查结论，不继续把环境故障归咎于业务代码。

### P2：清理并建立最小前后端

前端保留 Vue/Vite 启动入口，替换为单一工程状态页，清理旧路由和 API 调用；用 Vitest 加 DOM 测试环境建立真实挂载测试，断言状态页可见。设置 `lint:check`（无 fix、无 cache）、`test:ci`（非 watch）、`build`。原型不进入正式 CI 的输入集合，但必须在本地执行现有原型入口回归；依赖包移除前检查原型用途。

后端保留 Spring Boot 启动骨架，仅提供基础健康检查。删除废弃业务实现及其配套测试，移除无用途的 MySQL/H2、JWT 等依赖；不要为保留旧业务代码而修补 Mockito。采用 PostgreSQL JDBC、Flyway 核心及 PostgreSQL 支持模块，保留所需 Spring JDBC/Web/测试依赖。版本优先由现有 Boot BOM 管理，补充依赖时核对兼容性。

提交无秘密、可直接加载的默认配置，通过环境变量注入数据库连接参数；同步修正忽略规则及模板。取消 Hibernate 自动建表，移除旧本地 application.yml 的影响前先备份，不能靠个人未追踪配置启动成功。

通过条件：旧业务代码不再进入构建；原型文件校验无意外变化、原型入口仍可用；前端检查、挂载测试和构建通过。后端完整测试在 P3 接通数据库后验收。

### P3：建立 Flyway 与 Testcontainers 验证

建议把占位迁移放在独立基础设施 location，例如 `db/infra-migration/R__infrastructure_probe.sql`，使用合法的 `SELECT 1;`。明确配置 Flyway location，让应用和测试加载同一资源；不创建业务表，不占用正式 V1 编号。正式模型阶段如何退出该 location 另行设计，不在本轮假定升级方案。

使用 Testcontainers PostgreSQL 与 Spring Boot 测试上下文，将动态 JDBC 地址传给真实 DataSource。测试必须由 `./mvnw -B -ntp verify` 实际发现并执行：若命名为 `*IT`，显式配置 Failsafe；若使用 Surefire，采用其默认可发现的 `*Test` 命名。

最小断言：

1. 连接真实 PostgreSQL，查询数据库产品信息及 `SELECT 1` 成功。
2. Spring 应用成功启动；Flyway history 存在，指定占位脚本有且仅有一条成功应用记录，排除“没有脚本所以也成功”的假阳性。
3. `validate` 成功；在同一数据库再次 migrate 后没有新增应用记录。
4. 在隔离测试库用被修改的已应用版本迁移夹具验证 checksum 不匹配会失败；夹具仅在测试资源中，不能放入应用正式 location。重复迁移本身不具备 checksum 失败断言，因为 repeatable 迁移变更后允许重跑。
5. 健康接口返回预期状态；无 Docker 时测试明确失败，禁止自动跳过、退回 H2 或使用共享开发数据库。

通过条件：非零测试数、零失败、零跳过；报告能识别数据库与迁移断言。禁止 `skipTests`、`disabledWithoutDocker` 或空测试套件换取绿灯。

### P4：Compose 验证完整启动链

新增后端多阶段 Dockerfile、前端构建/静态服务 Dockerfile、各自 `.dockerignore` 和根 Compose。镜像构建不复制本地 target/node_modules、原型、环境秘密；测试由验证阶段负责，不要求镜像构建内部再启动 Testcontainers。

数据库使用 `pg_isready` 健康检查，后端在数据库健康后启动并执行 Flyway；前端静态服务提供 `/api` 到后端服务名的反向代理。固定健康检查路径，说明 `/api` 前缀的保留或剥离规则，避免 Vite 代理有效但容器代理失效。

使用专用 Compose 项目名和新建验收卷。执行配置解析、镜像构建、带超时的健康等待，分别验证前端页面、经前端代理访问后端健康接口、数据库 SQL 和迁移历史。先保留卷重启复验迁移不重复，再用另一新建空卷重跑证明空库可复现。

通过条件：三个服务健康、HTTP 及 SQL 断言通过，两轮结果一致。仅清理由本轮验收创建的容器和卷；开发库及其他项目数据不在清理范围。

### P5：建立 CI 与可复现证据

新增 GitHub Actions，至少覆盖 PR 和目标分支 push，固定 Ubuntu runner 系列与工具版本。设置三个必需成功的作业：

- 前端：`npm ci` → `npm run lint:check` → `npm run test:ci` → `npm run build`。
- 后端：验证 Docker → `./mvnw -B -ntp verify`，运行真实 Testcontainers。
- Compose：配置验证、镜像构建、健康等待、代理 HTTP 和数据库迁移检查；不向 CI 注入本地数据库依赖。

工作目录分别明确为前端目录、后端目录和仓库根目录。失败时仍上传测试报告和脱敏 Compose 日志，设置整体超时并确保回收测试资源；不得使用 continue-on-error 掩盖失败。

根白名单需要逐项放行 `.github/workflows/`、Compose、验证脚本、环境模板和必要配置。用 `git check-ignore` 与 `git ls-files` 核对，禁止强制添加原型或秘密。本地未追踪文件必须不影响干净检出。

建议提供一个根验证脚本，顺序封装上述命令，任何失败立即返回非零。文档说明所需软件、环境变量、首次下载需网络及预期输出。可复现指过程与验证结论一致，不宣称构建产物字节完全相同。

通过条件：同一提交在本地干净检出与远端 CI 成功；保存提交 SHA、CI run URL、命令退出码、工具版本、镜像摘要、测试计数、迁移历史和原型保护检查结果。若执行者没有推送权限，只能报告“本地通过，远端 CI 待执行”，不能标记最终完成。

## 5. P0 执行记录

### 5.1 现场与恢复证据

- 基线提交：`9464bd2607191e6dd1051361d5266996ceea6eda`；分支 `main` 相对 `origin/main` ahead 14。
- 执行前有 24 个已追踪文件修改及 1 个未追踪文件 `HANDOFF-B1-PLAN.md`；其中包括 21 份入口/过程/治理/需求/验收文档、前端 README 和两个前端页面。后续操作不得覆盖或把这些修改与工程骨架一起无差别暂存。
- 仓库外恢复目录：`C:\Users\PUBLIC_USER\Documents\MyLibrarySystem-P0-Backup-20260909-6EmoMS\`（WSL 路径 `/mnt/c/Users/PUBLIC_USER/Documents/MyLibrarySystem-P0-Backup-20260909-6EmoMS/`）。`workspace-recovery.tar.gz` 保存当前工作区（排除 `.git`、`node_modules`、构建产物和运行数据），`tracked-working-tree.patch` 单独保存已追踪差异；归档含 530 个路径，总大小 8,664,587 bytes，补丁 570,288 bytes。
- `SHA256SUMS` 已用 `sha256sum -c` 验证通过；`archive-files.txt` 可正常列出归档。`prototypes-before-cleanup.sha256` 登记 27 个原型文件的清理前校验值，供 P2 后对比。
- 首次生成的 WSL `/tmp` 恢复目录在跨轮次时被执行环境清理，不能作为持久证据；因此已在 P1 收尾时重建至上述 Windows 文档目录。该目录独立于仓库且可跨会话保留，本轮结束前不得清理。

### 5.2 原型依赖结论

静态导入与资源引用核对显示，`library-frontend/prototypes/` 未引用正式 `src/`、`src/assets/` 或 `public/` 文件。原型自身文件应整体保留，并继续依赖 `vue`、`vue-router`、`element-plus`、`@element-plus/icons-vue`、`vite`、`@vitejs/plugin-vue`；点击级验证还依赖 `vitest`、`@vue/test-utils` 和 DOM 测试环境，P2 应把这些测试依赖正式写入锁文件，不能依赖当前残留的 `node_modules`。

### 5.3 清理边界

| 处理      | 路径                                                                                                                                        | 边界说明                                                                                            |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| 保留      | 根 `README.md`、`TODO.md`、`HANDOFF*.md`、`docs/`、本地 `AGENTS.md` 与 `docs/agents/`                                                       | 文档及既有未提交修改不属于旧实现清理范围；本地代理文档继续维持不入库约束                            |
| 保留      | `library-frontend/prototypes/**`                                                                                                            | 27 个原型文件及日志整体保护，继续被 Git 忽略；P2 后按清理前校验清单核对并执行本地入口回归           |
| 保留      | 前端 `index.html`、`public/favicon.ico`、Vite/ESLint/格式化配置、`package.json`、`package-lock.json`                                        | 作为启动与构建工具链；依赖按原型和最小状态页的实际用途收敛                                          |
| 替换      | 前端 `src/App.vue`、`src/main.js`、`README.md`、`package.json`/锁文件、必要 lint/Vite 配置                                                  | 改成无旧路由/API 的单一工程状态页、非修改式检查、挂载测试与构建入口                                 |
| 删除      | 前端 `src/api/**`、`src/layouts/**`、`src/router/**`、`src/utils/**`、`src/views/**`、旧脚手架 `src/components/**` 与无引用 `src/assets/**` | 旧业务实现及 Vue 初始示例；两个已有页面修改已进入恢复包，删除前不再另行覆盖                         |
| 保留      | 后端 Maven Wrapper、`LibraryApplication.java`、项目目录结构                                                                                 | Wrapper 版本和执行位继续沿用；启动类作为最小 Spring Boot 入口                                       |
| 替换      | 后端 `pom.xml`、`README.md`、资源配置及测试集                                                                                               | 收敛为 Web/JDBC/PostgreSQL/Flyway/Testcontainers 骨架、环境变量配置、健康检查及真实 PostgreSQL 验证 |
| 删除      | 后端旧 `controller`、`service`、`entity`、`repository`、`dto`、`security`、旧异常/配置/公共响应类及对应旧业务测试                           | 不修补计划全量重写的业务代码；P2/P3 用最小骨架测试替代                                              |
| 修改/新增 | 根 `.gitignore`、后续 `.github/workflows/**`、Compose、Dockerfile、验证脚本与环境模板                                                       | 逐项放行可复现工程文件；原型、秘密与个人配置仍禁止强制入库                                          |

P0 通过：恢复材料覆盖已追踪差异、未追踪计划、被忽略原型和个人配置；归档可读且校验通过；原型依赖与逐路径清理边界已明确。P0 未执行任何删除、安装、构建、测试或容器操作。

## 6. P1 执行记录

### 6.1 工具与引擎版本

| 对象          | 实测值                                                                                                      |
| ------------- | ----------------------------------------------------------------------------------------------------------- |
| WSL           | Ubuntu 24.04.4 LTS；Linux `6.18.33.2-microsoft-standard-WSL2`；`x86_64`                                     |
| Java          | Eclipse Temurin OpenJDK `21.0.12.1` LTS                                                                     |
| Maven Wrapper | Apache Maven `3.9.9`，由 `library-server/mvnw` 启动并使用上述 JDK 21                                        |
| Node.js / npm | Node.js `v22.23.2`；npm `10.9.8`                                                                            |
| Docker        | Client/Engine `29.5.3`；Docker Desktop `4.77.0 (228796)`；Linux `x86_64`；containerd `v2.2.4`；runc `1.3.5` |
| Compose       | Docker Compose `v5.1.4`                                                                                     |

`docker info` 在用户重置 Docker 数据后显示 0 容器、0 镜像，存储驱动为 `overlayfs`、cgroup v2、32 个逻辑 CPU、约 15.2 GiB 内存；未发现引擎警告。首次在 Codex 沙箱内访问 `/var/run/docker.sock` 被权限策略拒绝，获得 Docker socket 访问授权后客户端与服务端通信正常；这是代理执行沙箱边界，不是 Docker Desktop 服务端故障。

### 6.2 容器探针

执行 `docker run --rm alpine:3.22 uname -a`：空镜像缓存下成功下载 Linux/amd64 镜像、创建容器并输出 WSL2 Linux 内核信息，退出码为 0。解析后的不可变镜像标识为 `alpine@sha256:14358309a308569c32bdc37e2e0e9694be33a9d99e68afb0f5ff33cc1f695dce`；结束后按镜像过滤 `docker ps -a` 无结果，证明一次性容器已移除。

P1 通过：WSL 能连接 Docker Desktop Linux 引擎，并能从空数据状态拉取、创建、运行和移除容器；无需安装第二套 WSL Docker daemon。旧 POM 的 Java 17 编译目标与实际 WSL JDK 21 的差异已由用户决定统一到 JDK 21，作为 P2、P4、P5 的执行约束。

## 7. P2 执行记录

### 7.1 前端红—绿切片与清理

- 预先确认的测试接缝为“DOM 挂载根组件后，用户可见工程状态”。先新增 `App.test.js`，保留旧 `<router-view>` 实现执行测试，得到目标文本缺失的失败；随后实现单一工程状态页，测试变为 1/1 通过。
- Vitest 初次默认扫描到 `prototypes/`，据此把正式测试发现范围固定为 `src/**/*.test.js`；原型使用自身配置单独回归，不进入正式 CI 测试集合。
- 删除旧 `src/api`、`layouts`、`router`、`utils`、`views`、Vue 初始示例组件与无引用 assets；正式 `src` 只保留 `App.vue`、`main.js`、`App.test.js`。
- `package.json` 固定 Node `>=22.12.0 <23` 与 npm 10.9.8，保留原型必需的 Vue Router、Element Plus 和图标库，移除旧业务专用 Axios 与未使用的 Vue DevTools 插件；Vitest、Vue Test Utils、jsdom 正式进入锁文件。
- 初次加入的 Vitest 2.1.9 经 `npm audit` 查出 5 项测试工具链漏洞（3 moderate、1 high、1 critical）；显式升级到 Vitest 5.0.0 后重新 `npm ci` 和审计，结果为 0 漏洞。未执行破坏性 `audit fix --force`。
- 干净 `npm ci` 成功；`lint:check`、正式挂载测试和生产 build 均为退出码 0。当前 Vite 解析版本为 7.3.6，正式构建转换 11 个模块。

### 7.2 后端红—绿切片与清理

- 预先确认的测试接缝为 `GET /api/health` 的 HTTP 状态和 JSON 响应。先新增 MVC 测试，因 `HealthController` 不存在而在测试编译阶段失败；随后实现真实控制器，断言 `200` 与 `{"status":"UP"}`，测试变为 1/1 通过。
- Maven 编译目标从 Java 17 改为已批准的 Java 21；删除旧认证、业务 controller/service/entity/repository/dto/security、异常/配置/公共响应类及旧业务测试。
- 依赖收敛为 Spring Boot Web/JDBC、PostgreSQL JDBC、Flyway core/PostgreSQL 支持和 Spring Boot Test；MySQL、H2、JPA、Security、JWT、SpringDoc、Lombok 均不再进入 POM。
- MVC 测试直接以 MockMvc 挂载真实无依赖控制器，不加载 Mockito 测试上下文；JDK 21 下不再出现 Mockito 动态加载 Byte Buddy agent 的警告，也未通过 JVM 参数隐藏警告。
- 无秘密 `application.yml` 已改为可追踪默认配置，支持 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`SERVER_PORT` 环境变量；Flyway location 预置为 `classpath:db/infra-migration`，占位脚本及执行断言留 P3。
- `./mvnw -B -ntp clean verify` 使用 `release 21` 编译 2 个生产源文件与 1 个测试，1 个测试、零失败、零错误、零跳过，并生成可运行 jar。jar 内应用类仅有 `LibraryApplication.class` 和 `HealthController.class`，证明旧增量构建 class 未残留。

### 7.3 原型保护与回归

- 清理前后 27 文件清单一致；26 个文件 SHA-256 未变。唯一预期修改为 `agent-loop-demo/agentLoop.js` 在每轮工具结果处理后实际 `yield`，修复 `require-yield` 错误并兑现其逐步产出事件的既有注释契约；该目录 ESLint 通过。清理后清单已持久化为恢复目录中的 `prototypes-after-p2.sha256`。
- AI 助手点击级真实挂载回归在 Vitest 5 下 7/7 通过；Agent 模拟端点验证 18 场景、70 断言通过。
- 以前端根配置分别构建 AI 助手、Agent Loop、Overlay 三个原型入口，均成功。构建产物写入 `/tmp`，未进入原型目录或仓库。
- 2026-09-09 补做真实浏览器入口与交互取证（Windows Chrome 152.0.7977.83、Vite 7.3.6）：Agent Loop 点击“检索《三体》”后，第 1 个 `yield` 可见运行态、快捷按钮禁用、模型调用 `1/4` 与 5 条事件；下一步增长为 `2/4` 与 9 条事件；最终达到“已回复”、`3/4` 与 12 条事件，显示馆藏回复，busy 消失且按钮重新启用。该结果直接覆盖本轮 `agentLoop.js` 修改点。
- 同轮浏览器取证：AI 助手通过专用 `demo:ai` Vite 配置打开读者入口，执行“预约→登录并继续→预约日期/时段表单”，登录反馈与提交入口可见；Overlay 打开并取消删除模态框，再显示 Snackbar、点击撤销，最终 Snackbar 消失并显示“已撤销归档”。三个入口均完成关键交互。通用 `npm run dev` 对 AI history URL 会回落正式状态页，README 已明确 AI 原型必须使用专用启动命令；此为入口配置边界，不是原型功能错误。

P2 通过：旧业务代码不再进入前后端构建；正式前端 lint/test/build、后端干净 verify、依赖审计和原型本地回归均通过。P2 仅证明最小骨架，未连接真实 PostgreSQL、未执行 Flyway 脚本，后端完整测试仍以 P3 为验收点。下一行动包为 P3 Flyway 与 Testcontainers 验证。

### 7.4 P3 首轮执行记录（当时未通过）

- 新增应用迁移 `library-server/src/main/resources/db/infra-migration/R__infrastructure_probe.sql`，内容为 `SELECT 1;`；沿用应用默认 Flyway location，不创建业务表或正式 V1。
- 新增 Boot BOM 管理的 Testcontainers PostgreSQL/JUnit Jupiter 依赖（1.21.0），Flyway 为 11.7.2。`PostgresInfrastructureTest` 使用随机端口启动 Spring 应用，通过动态属性接入容器数据库；四项测试覆盖产品信息和 SQL、占位历史和再次 migrate、隔离数据库中的版本迁移 checksum 负例、真实 HTTP 健康接口。V1 夹具仅存在于测试资源。
- PostgreSQL 暂选 `postgres:17.6-bookworm`；尚未拉取或核定摘要，不能作为已固定的镜像基线。P3 验收仍需固定版本加摘要，供 P4 共用。
- 当前 WSL `/usr/bin/docker` 指向不存在的 Docker Desktop CLI 路径，`/var/run/docker.sock` 不存在；P1 的历史通过记录不代表当前环境可用。已请求用户恢复 Docker Desktop 的 WSL 集成。
- `./mvnw -B -ntp verify`：依赖下载、Java 21 测试编译成功，Surefire 实际发现集成测试；容器初始化报 `Could not find a valid Docker environment`。报告共 2 项（含集成类初始化错误），0 failures、1 error、0 skipped；原 MVC 测试通过，四项数据库/应用集成测试尚未执行，不能把报告数量理解为数据库断言通过。
- 运行报告位于 `library-server/target/surefire-reports/`（构建产物，不入库）。P3 尚未通过，P4/P5 未执行。

### 7.5 P3 恢复执行与验收通过

2026-09-09 用户启动 Docker Desktop 后，WSL Docker socket 与 CLI 恢复。获准访问 socket 后确认 Docker Engine 29.5.3 / API 1.54 / Linux amd64；沙箱内 socket 权限拒绝与 Docker 服务不可用是不同问题。

- 拉取官方 `postgres:17.6-bookworm`，核对 RepoDigest 为 `postgres@sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3`。测试固定该摘要，实测数据库版本 PostgreSQL 17.6；此为 P4 共用的镜像基线。Testcontainers 1.x 对 tag 加 digest 的组合解析失败，代码采用 repository 加 digest 语法，注释记录对应版本标签。
- Boot 3.5.0 BOM 的 Testcontainers 1.21.0 在当前 Docker 上初始化返回 HTTP 400；直接 API 探针显示 v1.32 返回 400、v1.44 返回 200。POM 仅覆盖 Testcontainers 为 1.21.4，其运行日志确认成功连接 Docker 29.5.3，Spring Boot 与 Flyway 版本未变。
- 完整 Spring 测试上下文会触发 starter-test 中未使用的 Mockito inline agent；已排除 `mockito-core` 与 `mockito-junit-jupiter`，测试仍走真实 HTTP/JDBC 边界。最终日志没有 Mockito 自附加或动态 agent 告警，未设置隐藏告警的 JVM 参数。
- 最终命令：在 `library-server/` 执行 `./mvnw -B -ntp clean verify`，2026-09-09 21:54:17（America/Santiago）退出码 0，耗时 7.311 秒，生成可运行 jar。Surefire 合计 5 tests、0 failures、0 errors、0 skipped，其中 `PostgresInfrastructureTest` 4 项、`HealthControllerTest` 1 项。

| 测试方法                                            | 通过的断言                                                                                                                                                   |
| --------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `connectsToRealPostgres`                            | 真实 DataSource 的产品名为 PostgreSQL，`SELECT 1` 返回 1                                                                                                     |
| `appliesInfrastructureProbeExactlyOnceAndValidates` | 应用启动执行占位脚本；历史中指定 repeatable 脚本成功记录恰为 1；validate 成功；再次 migrate 执行数为 0，全部历史行不变                                       |
| `rejectsChangedChecksumInIsolatedDatabase`          | 独立 `checksum_probe` 数据库应用测试 V1 成功且原版 validate 通过；修改版 validate 返回唯一 V1 的 `CHECKSUM_MISMATCH`，migrate 抛出 `FlywayValidateException` |
| `servesHealthFromRunningApplication`                | 随机真实 HTTP 端口的 `/api/health` 返回 200 与 `{"status":"UP"}`                                                                                             |

Surefire XML/TXT 报告位于 `library-server/target/surefire-reports/`，属于可重建产物，不入库。最终 jar 仅包含应用的 `db/infra-migration/R__infrastructure_probe.sql`，不包含 checksum 夹具或 Testcontainers 测试依赖。测试结束后按 `org.testcontainers=true` 标签检查无残留容器；新增源文件与 SQL 均未被 git 忽略，尚未暂存或提交。上一节无 Docker 时明确失败、零跳过的结果作为负向环境证据保留。

P3 通过。P4 Compose、P5 CI、B1-C 及业务验收未执行；B1-A/B1-B 不作整体完成登记。

## 7A. P4 执行记录

2026-09-09 本轮开始时 Docker Desktop 尚未启动，WSL Docker 链接目标与 socket 不存在；用户启动后恢复 Docker 29.5.3 / Compose 5.1.4。GitHub 认证在沙箱外核验有效，仓库 Actions 已启用。

- 新增前后端多阶段 Dockerfile 与构建输入白名单、Nginx 代理、根 Compose 和无秘密环境模板。数据库镜像沿用 P3 摘要；后端构建/运行分别固定 Temurin 21.0.8+9 JDK/JRE 的镜像摘要，前端固定 Node 22.23.2 与 Nginx 1.28.0 摘要。实际 npm 为 10.9.8；CI 与容器使用相同 JDK 补丁，本机仍为既有 21.0.12.1，编译目标同为 Java 21。
- 验收脚本使用独立项目、临时口令、动态 loopback 端口，忽略本地 `.env`；健康等待上限 180 秒，HTTP 请求也设置超时。正常或失败退出均保存脱敏日志并清理验收容器和卷。开发项目不参与清理。
- `bash scripts/verify-compose.sh` 退出码 0。证据目录为 `artifacts/compose-20260910024223-6783/`，三个阶段为 `first-empty`、`retained-volume`、`second-empty`：每阶段三个服务健康，首页/JS 包含工程状态页，经前端 `/api/health` 精确返回 `{"status":"UP"}`，`SELECT 1` 成功，Flyway 全部历史恰为一条成功占位记录。
- 保留卷重建容器前后，包含安装时间与执行时间的完整迁移历史逐字一致；第二项目从新空卷复跑成功。迁移 checksum 为 `78787420`，数据库报告 17.6。两个项目均由退出钩子执行 `down --volumes`，清理命令成功。
- 原型目录与 P2 清单对比：27 个文件，无新增或丢失，SHA-256 全部一致。前端再次执行干净安装、非修改式 lint、JUnit 报告输出和构建，1/1 测试通过，npm 审计 0 漏洞。
- 首轮验收后将本地构建镜像显式标为 `mls-backend:foundation`、`mls-frontend:foundation`，并补充 HTTP 超时与镜像摘要记录；最终文件随 P5 干净检出再次验证。

P4 通过。运行入口及环境边界见 [ENGINEERING.md](ENGINEERING.md)。P5 已新增三个 CI 作业和根验证脚本，远端结果尚未取得，不登记 B1-A/B1-B 整体完成。

## 7B. P5 干净检出发现与修复

- 工程文件单独提交为 `c9759d4`，分支 `codex/b1-engineering-foundation`；既有需求、治理、根入口及过程文档修改未纳入该提交。干净检出 `/tmp/mls-b1-clean` 中前端 1/1、后端 5/5（零跳过）通过，但首次 Compose 启动失败，根验证脚本退出码 1，未推送该状态。
- 失败材料位于 `artifacts/compose-20260910024828-12017/`（原始位置在干净检出中）。后端报 `Connection to database:5432 refused`；数据库当时的初始化临时服务只接受 Unix socket。旧 `pg_isready -U library -d library` 已报告健康，后端因此被过早启动。这是健康条件不足造成的时序竞态，不是干净检出缺文件。
- 独立 socket-only PostgreSQL 探针稳定显示 socket 接受连接而 TCP 无响应；新增回归脚本直接读取并运行 Compose 中的健康命令，旧配置以明确误报退出码 1 失败。健康命令改为 `pg_isready -h 127.0.0.1 -U library -d library`，使其等待正式 TCP 服务；回归测试进入 Compose 验收入口。
- 首轮成功记录保留为历史结果，不作为该竞态已消除的证据；修复后的干净检出与远端 CI 结果另行追加。

### P5 证据采集修订

修复提交 `57c48d5` 的干净检出中，前端 1/1、后端 5/5、健康条件负例及 Compose 三阶段启动/HTTP/SQL/迁移断言均通过；脚本最终因按旧容器镜像 ID 查询摘要时出现 `No such image` 而退出 1，未登记整体通过。证据保存在 `compose-20260910025612-18149`。已将镜像信息改为每阶段即时记录容器 Image 值、配置引用和镜像 RepoDigests；第二空库直接复用首轮构建镜像，不再重新构建相同标签。修订后仍需完整复验。

### P5 首轮远端 CI

提交 `48090dd2fcd5147e39fc07fb361494db9e0131a5` 的干净检出执行 `bash scripts/verify.sh` 完整通过，退出码 0；本地证据已持久复制至仓库 `artifacts/p5-local/success-compose/` 与 `success-verify.log`。同一提交推送后触发 [CI run 34438144192](https://github.com/whitecait/Mylibrary/actions/runs/34438144192)。前端作业通过；后端停在 setup-java，尚未执行测试，错误为目录无法匹配 `21.0.8+9`，可用目录实际列出 `21.0.8+9.0.LTS`。已按安装器返回的完整标识修正，JDK 发行版、补丁和构建号不变；最终运行结果另行登记。

## 7C. P5 最终验收通过（2026-09-10）

- 分支：`codex/b1-engineering-foundation`，已推送；验收提交：`6fd8a8a0ffbe4404adc0dfb9e057059385496990`。本轮提交只包含前后端骨架、容器、CI、验证脚本及相关工程说明；既有需求/治理文档修改仍保留在原工作区，未混入工程提交。未合并 main，未修改分支保护。
- 本地干净检出：`/tmp/mls-b1-clean`，源码工作区 `git status --porcelain` 为空，无原型、`.env` 或个人配置。执行 `bash scripts/verify.sh` 退出码 0；Node 22.23.2 / npm 10.9.8 / Temurin 21.0.12.1 / Maven Wrapper 3.9.9 / Docker 29.5.3 / Compose 5.1.4。
- 前端 `npm ci`、`lint:check`、Vitest 1/1 和 build 通过；后端 clean verify 5/5，零失败、零错误、零跳过，含真实 PostgreSQL/Flyway 与 checksum 负例。
- Compose 健康条件回归通过；`first-empty`、`retained-volume`、`second-empty` 三阶段的页面、代理 HTTP、SQL、迁移历史与镜像证据均通过。保留卷重启前后完整迁移历史一致；第二空卷复用同一批构建镜像。每库恰有一条成功占位迁移，checksum `78787420`。两个专用项目均完成容器与卷清理，最终 `result.txt` 为 `exit_code=0`。
- [最终 GitHub Actions run 34438399630](https://github.com/whitecait/Mylibrary/actions/runs/34438399630) 的 `headSha` 与上述提交完全一致；`frontend`、`backend`、`compose` 均为 `success`，整体 `success`。CI 使用 Ubuntu 24.04、Node 22.23.2、npm 10.9.8、Temurin 21.0.8+9（安装目录标识 `21.0.8+9.0.LTS`）；PostgreSQL 与所有容器基础镜像摘要与本地一致。
- 本地成功材料：`artifacts/p5-local/final/`（完整命令日志、Surefire XML、Compose 证据与 27 文件原型校验）。远端成功材料：`artifacts/p5-remote/final-run/` 与 `final-run-metadata.json`；首轮远端失败及前两次本地失败材料也已保存。以上为被忽略的可重建证据，不入库；远端工作流产物保留 14 天，关键结论在本节持久登记。
- 本轮结束前原型文件再次与 P2 持久清单比较：27/27 SHA-256 一致，无文件增删；未修改原型。P2 的入口交互证据继续适用于这些未变文件。

P4/P5 与本计划覆盖的 B1-A/B1-B 完成。B1-C 未执行，因此 B1 整体不登记完成；业务验收、G3/G4/G5 与需求基线仍沿用各自状态。复现命令为仓库根目录 `bash scripts/verify.sh`，详细运行说明见 [ENGINEERING.md](ENGINEERING.md)。

## 8. 最终验收清单

| 验收项            | 必须提供的证据                                                  |
| ----------------- | --------------------------------------------------------------- |
| 原型保护          | 备份校验、前后文件差异和本地原型入口检查记录                    |
| 前端              | 干净 npm ci、非修改式 lint、非零挂载测试、build 全通过          |
| 后端              | Wrapper verify 通过、可运行 jar、非零数据库测试且无跳过         |
| PostgreSQL/Flyway | 真实数据库连接、占位执行记录、重复运行无新增、checksum 负例通过 |
| Compose           | 空卷启动、保留卷重启、另一空卷复跑；页面及代理健康请求成功      |
| CI                | 对应同一提交的三个作业全部绿色，失败证据保留机制存在            |
| 复现入口          | 新检出无需本地隐藏配置，按文档命令完成同样检查                  |

建议执行交接语：按 P0→P5 完成本计划，每个行动包通过后再推进；报告实际修改、执行命令、证据和未完成项。只在以上清单全部有证据后登记 B1-A/B1-B 完成，B1-C 与业务验收仍保持各自原状态。

## 修订记录

| 日期       | 内容                                                                                                                                                                                                                                                                                                                                                                                              |
| ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 2026-09-10 | P4/P5 收尾：修复数据库初始化期 socket 健康误报、镜像证据采集和 CI Temurin 完整版本标识；提交 `6fd8a8a` 在本地干净检出及远端三个作业全部通过，登记 B1-A/B1-B 完成并保留 B1-C/业务验收/需求门禁原状态。                                                                                                                                                                                             |
| 2026-09-09 | P4 三阶段 Compose 验收通过，新增容器、代理、隔离验收脚本和复现说明；P5 配置完成，干净检出与远端 CI 待验证。                                                                                                                                                                                                                                                                                       |
| 2026-09-09 | P3 恢复执行通过：固定 PostgreSQL 17.6 镜像摘要，Testcontainers 覆盖为 1.21.4 适配 Docker 29，排除闲置 Mockito；最终 clean verify 5/5 通过，零失败、零错误、零跳过，记录迁移/校验和/HTTP 与打包清理证据。P4/P5 未执行。                                                                                                                                                                            |
| 2026-09-09 | P3 新增占位迁移、Testcontainers 集成测试与 checksum 测试夹具；编译通过，verify 因当前 Docker WSL 集成不可用失败，零跳过。镜像摘要与真实数据库验收未完成，P3 不登记通过。                                                                                                                                                                                                                          |
| 2026-09-09 | P0～P2 遗漏修订：真实浏览器打开并操作 Agent Loop、AI 助手、Overlay 三个入口；直接确认新增 `yield` 后事件按 5→9→12 条分步增长、运行中保持 busy、终态解除 busy 并恢复按钮，另完成 AI 登录续行与 Overlay 模态/Snackbar 撤销链。Node engines 由 `22.x` 收紧为 `>=22.12.0 <23`，同步 README 与锁文件根元数据。                                                                                         |
| 2026-09-09 | 执行 P2：按已批准边界清理旧前后端业务实现，以 TDD 红—绿切片建立 Vue 状态页挂载测试和 Spring MVC 健康接口测试；编译目标统一 Java 21，建立 PostgreSQL/Flyway 依赖与无秘密环境变量配置；正式 lint/test/build、后端 clean verify、三原型构建、点击回归 7/7、Agent 验证 70/70 均通过。Vitest 2 审计发现的 5 项漏洞通过显式升级 Vitest 5 收口为 0；原型仅有 generator `yield` 预期修复。P3～P5 未执行。 |
| 2026-09-09 | 用户批准工程工具链统一使用已有 JDK 21；环境方案改为 JDK 21，P2 负责把 Maven 编译目标从 Java 17 改为 21，P4/P5 的构建镜像与 CI 同步使用 JDK 21。                                                                                                                                                                                                                                                   |
| 2026-09-09 | P1 收尾核验发现首次 P0 `/tmp` 恢复目录已被执行环境清理；立即在 Windows 文档目录重建当前工作区归档、差异补丁与 27 文件原型校验清单，并重新通过 SHA-256 和归档可读性验证。旧 `/tmp` 路径作废。                                                                                                                                                                                                      |
| 2026-09-09 | 执行 P1：记录 WSL、JDK/Maven、Node/npm、Docker Desktop/Engine 与 Compose 版本；在 Docker 数据重置后的空环境成功运行并自动移除 `alpine:3.22` 一次性容器，登记不可变镜像摘要。确认 Docker 链路正常；当时尚待统一的 Java 版本随后由本表上方决策收口。                                                                                                                                                |
| 2026-09-09 | 执行 P0：记录提交与工作区状态，建立仓库外恢复归档、二进制补丁和原型校验清单并验证可读性；核对原型无正式 `src`/共享资源引用，登记保留、删除、替换边界。P1～P5 未执行。                                                                                                                                                                                                                             |
| 2026-09-09 | 基于用户本轮清理与工程基础要求出版初版；完成静态调查，未执行清理、安装、构建、测试或远端操作。                                                                                                                                                                                                                                                                                                    |

> 被引用文档：
>
> - [开发任务规划](TODO.md)
> - [工程基础运行与验证](ENGINEERING.md)
> - [前端工程说明](library-frontend/README.md)
> - [后端工程说明](library-server/README.md)
