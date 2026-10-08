# 运行与验证说明

引用文档：

- [日结自动恢复与本地运行更新](artifacts/closing-recovery-20261007/REPORT.md)
- [项目入口](README.md)
- [当前交接记录](HANDOFF.md)
- [两日冲刺待办](TODO.md)
- [工程文档入口](docs/README.md)
- [容器配置](compose.yaml)
- [私有配置模板](.env.example)
- [工程检查入口](scripts/verify.sh)
- [形式化验证入口](formal/README.md)
- [交付包操作说明](scripts/delivery/README.md)
- [Issue #8](<https://github.com/whitecait/Mylibrary/issues/8>)
- [PR #9](<https://github.com/whitecait/Mylibrary/pull/9>)
- [推送 CI（五项已通过）](<https://github.com/whitecait/Mylibrary/actions/runs/37733077742>)
- [PR CI（五项已通过）](<https://github.com/whitecait/Mylibrary/actions/runs/37733313686>)
- [PR 合并提交 39c246b](<https://github.com/whitecait/Mylibrary/commit/39c246bc1773610a7a941dd2dc47d70e98856c75>)
- [合并后 main CI（五项已通过）](<https://github.com/whitecait/Mylibrary/actions/runs/37766613941>)
- [合并后 main CI 摘要](<artifacts/closing-recovery-20261007/evidence/remote-main-37766613941/verification-summary.md>)

## 当前适用范围

项目处于实机部署的页面微调阶段。部署目标为阿里云 ECS 云服务器。ECS 候选与目标机部署结果尚未完成；执行分解与发布条件见 TODO.md。本地预览后端已更新到固定提交 `19b77e70a46e2c30c342eb7bfe8f375d59285d2b`，启动补结、自动开放和重启不重复结算均已核对，细节见[运行报告](artifacts/closing-recovery-20261007/REPORT.md)。

本说明保留已实现的运行、验证和交付入口。旧批次测试数量、冻结链和验收交接已移出当前入口。

## 环境与依赖

本地工程使用 Java 21、Node.js 22.23.2、npm 10.9.8 与 Maven Wrapper 3.9.9。容器使用 PostgreSQL 17.6、Nginx 1.28.0 和固定摘要的基础镜像。后端集成测试需要可用的 Docker Linux 引擎。

## 日结启动恢复

`library.closing.daily-enabled` 默认值为 `false`。只有启用日结的部署会在应用启动前检查缺失的上一业务日，并与午夜任务共用逐日补结流程。全部到期批次成功后，系统自动开放普通写入。失败或重试预算耗尽时，系统保留隔离，管理员重开作为后备处置。时区与 cron 仍由 `library.closing.time-zone` 和 `library.closing.daily-cron` 控制。

当前本地预览启用了日结，时区为 `Asia/Shanghai`，cron 为 `0 0 0 * * *`。运行后端镜像仍为 `mls-backend:closing-recovery-19b77e7`，内容与远程 main merge commit `39c246bc1773610a7a941dd2dc47d70e98856c75` 一致；合并后未重新部署。PR [#9](https://github.com/whitecait/Mylibrary/pull/9) 已合并，Issue [#8](https://github.com/whitecait/Mylibrary/issues/8) 已关闭；推送、PR 与合并后 main CI 五项作业均通过。权威本地 `main` HEAD 仍为基线 `3a9f142f5427b950b211d8c7607660cda3a5c3b5`，`origin/main` 已刷新到 `39c246bc1773610a7a941dd2dc47d70e98856c75`。ECS 尚未更新。

从仓库根目录运行命令。需要 Bash、Git、Python 3、curl 和支持 `up --wait` 的 Docker Compose。第一次构建需要下载已锁定的依赖和基础镜像。WSL 与 Windows 各自安装依赖，不共享 node_modules 或 Maven 缓存。

## 本地启动

首次配置时，从 `.env.example` 创建 `.env`。填写数据库口令、访客 Cookie 签名键和所需模型配置。已有 `.env` 使用原文件，不覆盖本地配置。私有配置不入库。

```bash
docker compose -p mls-dev up --build -d --wait --wait-timeout 180
curl --fail http://localhost:8088/api/health
```

入口为 `http://localhost:8088`。`FRONTEND_PORT` 可修改前端宿主端口。当前配置只绑定 `127.0.0.1`；数据库和后端通过容器内部服务名连接。Nginx 保留 `/api/` 前缀并代理到后端。

启动顺序为数据库健康、后端迁移与健康、前端健康。源码包含 V1–V14 Flyway 迁移。停止所选开发项目并保留数据：

```bash
docker compose -p mls-dev down
```

## 工程验证

完整本地工程检查：

```bash
bash scripts/verify.sh
```

该入口依次检查前端依赖、非修改式 lint、测试、构建、后端验证、基础 Compose 与跨侧集成。后端数据库测试需要 Docker。

定向验证入口：

```bash
npm --prefix library-frontend run lint:check
npm --prefix library-frontend run test:ci
VITE_API_MODE=http npm --prefix library-frontend run build
bash scripts/verify-compose.sh
bash scripts/verify-integration.sh
python3 formal/verify.py --output-dir artifacts/formal-new-run
```

形式化检查需要 Java 21 与 Python 3.12。每次使用新的输出目录。容器和集成脚本创建隔离资源，默认把本次结果写入 artifacts/；旧结果清理不影响新结果生成。

观测检查入口为 `bash scripts/verify-observability.sh`。当前 CI 包含 frontend、backend、compose、observability 和 formal 五个作业。推送 CI、PR #9 CI 与合并后 main CI 的五项作业均已通过（[推送 CI](https://github.com/whitecait/Mylibrary/actions/runs/37733077742)；[PR CI](https://github.com/whitecait/Mylibrary/actions/runs/37733313686)；[main CI](https://github.com/whitecait/Mylibrary/actions/runs/37766613941)）。三轮后端各通过 250 项测试，形式化结果各通过 87 cases 与 4 项元数据检查。PR formal 的 checkout 候选 SHA `a5a8c9b5457267856fb3bfd554657729e3c6d1f0` 与 PR head SHA `19b77e70a46e2c30c342eb7bfe8f375d59285d2b` 不同；合并后 main CI 的 checkout 与 pr_head 均为 `39c246bc1773610a7a941dd2dc47d70e98856c75`，细节见日结恢复报告。

## 发布包与 ECS 待适配项

现有打包入口为 `scripts/delivery/package.py`，要求干净的确切提交。它从 Git 导出源码，构建并导出镜像，生成提交身份、文件校验和与压缩包封存信息。操作方法见交付包说明。

当前工作树存在未提交的前端修改，不能直接作为干净发布包输入。现有包默认用于本机环回运行。ECS 访问入口、HTTPS、目标机配置、持久化和同版启动检查尚未完成，具体任务由 TODO.md 维护。

GUI 与真实浏览器操作在 Windows 原生环境执行。WSL 负责源码修订、非浏览器检查和容器工程操作。

## 修订记录

- 2026-10-07：补充日结启动恢复的启用条件、失败隔离规则和固定提交的本地预览后端更新事实。
- 2026-10-07：根据用户明确授权，登记修复分支推送、Issue #8、PR #9 与两轮 CI 全部通过；ECS 和 PR 合并尚未完成。
- 2026-10-07：精简旧验收与冻结记录，保留实际运行命令及当前 CI 入口，登记 ECS 适配边界。
- 2026-10-08：登记 PR #9 合入、Issue #8 关闭、合并后 main CI 通过；origin/main 已刷新，本地工作区未切换，运行镜像未重建。
被引用文档：

- [日结自动恢复与本地运行更新](artifacts/closing-recovery-20261007/REPORT.md)
- [HANDOFF.md](HANDOFF.md)
- [README.md](README.md)
- [TODO.md](TODO.md)
- [docs/00-governance/MLS-DOC-GOV-0008.md](docs/00-governance/MLS-DOC-GOV-0008.md)
- [e2e/EVIDENCE-POLICY.md](e2e/EVIDENCE-POLICY.md)
- [e2e/README.md](e2e/README.md)
- [formal/README.md](formal/README.md)
- [library-frontend/README.md](library-frontend/README.md)
- [library-server/README.md](library-server/README.md)
- [scripts/delivery/README.md](scripts/delivery/README.md)
- [verification/README.md](verification/README.md)
