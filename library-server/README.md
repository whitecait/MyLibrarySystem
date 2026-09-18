# library-server — Spring Boot 工程骨架

> 引用文档：
>
> - [项目入口](../README.md)
> - [工程基础运行与验证](../ENGINEERING.md)
> - [工程文档唯一入口](../docs/README.md)
> - [B1-A/B1-B 工程基础行动计划](https://github.com/whitecait/Mylibrary/blob/3181a9fad2951803abe3b2e4f38452bb0123e5a4/HANDOFF-B1-PLAN.md)

当前实现事实（2026-09-17）：B0–B5 后端模块已经接入同一 Java 21 / Spring Boot 应用，包含身份、书目、流通、结算、复核、闭馆、通知/审计和匿名/账户 AI 助手接口；`GET /api/health` 保留为工程健康探针。正式迁移位于 `db/migration`：V8 固化 AI 所有权、事件与额度事实，V9 以幂等 forward migration 保障闭馆 `GLOBAL` 基线且不覆盖既有运行状态。

## 工具和配置

- Eclipse Temurin JDK 21；本地验证版本为 21.0.12.1 LTS。
- Maven Wrapper 3.9.9。
- Spring Boot 3.5.0。
- `application.yml` 不包含真实凭据，通过 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 和 `SERVER_PORT` 覆盖本地默认值。
- Flyway locations 包含正式 `classpath:db/migration` 与工程探针 `classpath:db/infra-migration`；`R__infrastructure_probe.sql`（`SELECT 1;`）不占用版本号。

## 构建与测试

在本目录执行：

```bash
./mvnw -B -ntp verify
```

`verify` 自动发现 MVC 测试和 `PostgresInfrastructureTest`。后者要求当前环境可访问 Linux Docker 引擎，首次运行需要网络下载依赖和镜像；无 Docker 时直接失败。测试使用独立容器和随机端口，不连接默认开发库，覆盖真实 PostgreSQL、Flyway 历史与重复执行、隔离库 checksum 负例及应用 HTTP 健康接口。

当前验证状态：2026-09-09 的 `./mvnw -B -ntp clean verify` 通过，4 项 PostgreSQL 集成测试与 1 项 MVC 测试，零失败、零错误、零跳过。报告位于 `target/surefire-reports/`。P3 已通过；2026-09-10 P4/P5 亦已通过，本地干净检出与远端 CI 对应同一提交 `6fd8a8a`，完整证据见行动计划 §7C。

测试固定官方 PostgreSQL `17.6-bookworm` 的摘要：`postgres@sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3`。Testcontainers 使用 1.21.4，覆盖 Boot BOM 的 1.21.0 以兼容 Docker 29；Flyway 11.7.2 仍由 Boot BOM 管理。测试不使用 Mockito，已排除相关依赖。checksum 的 V1 夹具仅在测试资源与隔离测试库中使用，不打入应用 jar。

连接可用 PostgreSQL 后启动：

```bash
DB_URL=jdbc:postgresql://localhost:5432/library \
DB_USERNAME=library \
DB_PASSWORD=library \
AI_VISITOR_COOKIE_SIGNING_KEY='<at-least-32-random-bytes>' \
./mvnw spring-boot:run
```

默认健康地址为 `http://localhost:8080/api/health`。

## 容器与完整验证

容器启动、前端 `/api` 代理、两轮空库与保留卷验收、CI 和证据位置见[工程基础运行与验证](../ENGINEERING.md)。

## 修订记录

| 日期 | 内容 |
| --- | --- |
| 2026-09-17 | 登记 B0–B5 当前实现事实、V8 AI 迁移与 V9 闭馆基线 forward-fix；真实 PostgreSQL 回归覆盖 V7→V8、V8→V9 缺失修复、状态保留和重复执行。AI 访客 Cookie 签名密钥为必填外部配置。 |
| 2026-09-10 | 登记 P4/P5 完成与同提交本地/远端验收证据。 |
| 2026-09-09 | 增加容器与完整验证入口，关联 P4/P5 配置和证据说明。 |
| 2026-09-09 | P3 验收通过：记录固定镜像摘要、Testcontainers 兼容版本、5/5 测试结果与测试夹具隔离；移除闲置 Mockito 依赖。 |
| 2026-09-09 | P3 新增迁移、Testcontainers 与隔离 checksum 夹具；记录 Docker 阻塞及尚未通过的验收状态。 |
| 2026-09-09 | P2 删除旧业务实现和旧业务测试，编译目标统一为 Java 21，建立 Web/JDBC/PostgreSQL/Flyway 骨架与健康接口 MVC 测试。 |

---


- 2026-09-11：按用户要求清理过时交接文档，历史引用固定至提交 `3181a9f`；仅维护引用位置，不改变需求、业务值或既有批准结论。

> 被引用文档：
>
> - [项目入口](../README.md)
> - [工程基础运行与验证](../ENGINEERING.md)
