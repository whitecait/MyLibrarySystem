# MyLibrarySystem

面向单一图书馆场景的管理系统学习与实践项目。

项目围绕书目、实体副本、预约、借阅、归还和罚款处理组织业务。读者、馆员与管理员通过各自的页面和权限办理业务。AI 助手部分用于探索对话交互与既有业务规则、用户权限和事务边界的结合。

引用文档：

- [运行与验证说明](ENGINEERING.md)
- [前端说明](library-frontend/README.md)
- [后端说明](library-server/README.md)
- [工程文档入口](docs/README.md)

## 项目范围

- 书目与实体副本管理。
- 预约、候补、借出、归还和闭馆处理。
- 罚款结算与模拟支付。
- 账户、角色权限、复核、通知与审计。
- 结合业务上下文的 AI 助手交互。

项目处于开发与验证阶段。功能范围、实现细节与测试结果按对应提交的代码及记录阅读。

## 技术组成

| 部分 | 使用技术 |
| --- | --- |
| 前端 | Vue 3、Vite、Vitest |
| 后端 | Java 21、Spring Boot、Maven |
| 数据库 | PostgreSQL、Flyway |
| 运行与验证 | Docker Compose、GitHub Actions |

## 阅读与运行

阅读源码可从 `library-frontend/` 与 `library-server/` 开始。业务与设计文档位于 `docs/`，工程运行说明位于 [ENGINEERING.md](ENGINEERING.md)。

前端安装与检查步骤见[前端说明](library-frontend/README.md)。后端构建、数据库配置与测试步骤见[后端说明](library-server/README.md)。

仓库保留开发历史。历史测试和部署记录对应当时执行的版本，本次仓库建立没有重新验证全部应用功能。

## 修订记录

- 2026-10-18：整理公开仓库首页，说明学习与实践定位、业务范围、技术组成和阅读入口。

被引用文档：

- [运行与验证说明](ENGINEERING.md)
- [前端说明](library-frontend/README.md)
- [后端说明](library-server/README.md)
- [工程文档入口](docs/README.md)
