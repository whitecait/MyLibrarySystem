# library-server - Spring Boot Backend

本目录保存 MyLibrarySystem 后端候选实现。项目正在按照 V 模型从需求分析阶段重新建立基线，[`docs/README.md`](../docs/README.md) 是工程文档唯一入口；`MLS-DOC-RA-0001.md v1.0` 只是已批准的阶段组织方案，不是最终需求基线。

当前需求基线为 `Not established`，验收尚未执行。本目录中的工程骨架与实现只可作为直接复用、重构复用或弃用重写的候选，不能作为新需求来源，也不能被解释为当前需求契约。

## 当前候选实现的开发说明

开发环境启动：

```powershell
cd library-server
.\mvnw.cmd spring-boot:run
```

当前候选实现提供以下本地开发入口；它们不是新系统已批准的外部接口或部署要求：

- Swagger UI：`http://localhost:8080/api/swagger-ui.html`
- H2 console：`http://localhost:8080/api/h2-console`

使用现有 `prod` 配置进行本地检查：

```powershell
cd library-server
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=prod
```

运行现有自动化测试：

```powershell
cd library-server
.\mvnw.cmd test
```

现有测试只可作为历史行为和特征测试参考。其通过结果不能替代新需求的分层测试，也不能登记为完整系统验收结果。

## 当前代码结构

```text
src/main/java/com/library/
|-- LibraryApplication.java
|-- common/
|-- config/
|-- controller/
|-- dto/
|-- entity/
|-- exception/
|-- repository/
|-- security/
`-- service/
```

结构清单仅用于定位现有候选资产，不批准未来的子系统、构件、模块、接口或数据设计。

## 历史实现风险

- 现有认证、馆藏、流通和罚款实现可能包含返工前的对象粒度、接口语义、固定业务规则或安全假设，必须在需求基线建立后逐项复核。
- `arc/` 下的旧拼音 API、旧字段、旧数据库和旧脚手架已归档弃用，不得进入新实现基线。
- 固定密钥、数据库口令、自动更新表结构和开放式跨域等不安全配置不得继承。
- 精确资产、风险与后续验证要求记录在工程文档入口所维护的代码复用候选台账中；该台账本身也不构成设计批准。

## 项目与工程文档入口

- [项目入口](../README.md)
- [工程文档唯一入口](../docs/README.md)
