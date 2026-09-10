# 工程基础运行与验证

> 引用文档：
>
> - [前端工程说明](library-frontend/README.md)
> - [后端工程说明](library-server/README.md)
> - [工程基础行动计划](HANDOFF-B1-PLAN.md)

本入口用于最小 Vue 状态页、Spring 健康接口及 PostgreSQL/Flyway 基础设施验证，不包含业务功能或业务验收。

## 所需环境

在 Linux 或启用 Docker Desktop 集成的 WSL 中运行。需要 Bash、Git、Python 3、curl、Docker Linux 引擎及支持 `up --wait` 的 Compose v2 或 v5；后端测试必须可连接 Docker，不能跳过数据库测试。

- 本机：Temurin JDK 21（已有环境 21.0.12.1）、Node.js 22.23.2、npm 10.9.8。
- CI 和后端容器：Temurin 21.0.8+9；与本机保持 Java 21 编译目标，补丁版本有差异。
- Maven：仓库 Wrapper 3.9.9，无需系统 Maven。
- 前端容器：Node 22.23.2 / npm 10.9.8，静态服务 Nginx 1.28.0。
- 数据库：PostgreSQL 17.6-bookworm，Compose 与 Testcontainers 共用 `sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3`。

Dockerfile 的所有基础镜像均固定标签和摘要。第一次执行需要访问 npm、Maven、Docker Hub；CI 的 Actions 和工具下载还需要访问 GitHub。依赖由 npm 锁文件、Maven POM/BOM 与 Wrapper 固定。复现表示执行过程与断言结论一致，不表示产物字节相同。

## 一次完成工程检查

从仓库根目录执行：

```bash
bash scripts/verify.sh
```

脚本顺序执行 `npm ci`、非修改式 lint、Vitest 挂载测试、前端生产构建、后端 `clean verify` 与 Compose 验收；任一步失败即返回非零。正常结果为前端 1 项测试、后端 5 项测试通过，Compose 三个阶段均报告页面、代理、SQL 与迁移断言通过。后端无 Docker 时明确失败。

仅执行容器验收：

```bash
bash scripts/verify-compose.sh
```

该脚本不读取本地 `.env`，自行生成一次性数据库口令、随机宿主端口及两个唯一 Compose 项目名。验证次序为新空卷启动、保留该卷并重建容器、另一新空卷启动。每次检查三个服务健康、首页及页面 JS 内容、前端代理 `/api/health` 的精确 JSON、`SELECT 1` 与唯一成功迁移；保留卷重启前后比较完整迁移历史。

正常结束和失败退出均保存脱敏日志，并清理这两个验收项目的容器、网络和卷；不清理开发项目。日志默认保存在被 Git 忽略的 `artifacts/compose-<运行号>/`，可用 `EVIDENCE_DIR` 改变位置。退出码记录在 `result.txt`。强制终止进程或 Docker 引擎失联可能使自动清理无法执行；此时按该次证据目录中的项目名执行 `docker compose -p <项目名> down --volumes`，不得清理其他项目。

## 启动开发环境

```bash
cp .env.example .env
# 编辑 .env 中的 DB_PASSWORD，再执行：
docker compose -p mls-dev up --build -d --wait --wait-timeout 180
curl --fail http://localhost:8088/api/health
```

打开 `http://localhost:8088` 查看状态页。只有前端端口发布到宿主机，且仅绑定 `127.0.0.1`；数据库与后端通过 Compose 内部服务名连接。`FRONTEND_PORT` 可修改前端宿主端口。Nginx 将 `/api/` 请求转发到 `backend:8080`，保留前缀，因此 `/api/health` 到达后端仍是 `/api/health`。

数据库使用 `pg_isready`；后端在数据库健康后启动并执行 `db/infra-migration` 的 repeatable 占位脚本，健康检查为 `/api/health`；前端在后端健康后启动。占位脚本不创建业务表，也不占用正式 V1。镜像构建只编译打包，Testcontainers 测试由独立验证命令和 CI 后端作业执行。

停止开发环境并保留数据：

```bash
docker compose -p mls-dev down
```

`.env` 不入库；镜像构建采用输入白名单，不复制原型、node_modules、target、测试夹具或本地配置。

## GitHub Actions

`.github/workflows/ci.yml` 覆盖面向 main 的 PR、main 和 `codex/**` 分支 push，以及手动触发。三个独立作业 `frontend`、`backend`、`compose` 必须全部成功；runner 为 Ubuntu 24.04，Actions 使用提交摘要。前端与后端保留测试报告，Compose 保留脱敏日志、HTTP/SQL 断言材料、镜像和环境信息，保留期 14 天。各作业有超时，失败不被忽略。

原型仍为本地资产，不进入 CI、镜像或干净检出的测试输入。远端通过证据及对应提交记录在行动计划中；工作流存在不代表运行通过，也不代表已配置 GitHub 分支保护。

## 修订记录

| 日期 | 内容 |
| --- | --- |
| 2026-09-09 | 新增工程基础命令、容器环境、验收隔离与证据说明、CI 触发及报告约定。 |

> 被引用文档：
>
> - [前端工程说明](library-frontend/README.md)
> - [后端工程说明](library-server/README.md)
> - [工程基础行动计划](HANDOFF-B1-PLAN.md)
