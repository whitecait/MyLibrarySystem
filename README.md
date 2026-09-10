# MyLibrarySystem

> 引用文档：
>
> - [工程文档唯一入口](docs/README.md)
> - [需求分析阶段工程化设计](docs/01-requirements/requirements/MLS-DOC-RA-0001.md)
> - [系统需求主规格](docs/01-requirements/requirements/MLS-DOC-RA-0003.md)
> - [Open 问题登记册](docs/01-requirements/requirements/MLS-DOC-RA-0007.md)
> - [需求评审清单](docs/01-requirements/reviews/MLS-DOC-REV-0001.md)
> - [需求基线记录](docs/01-requirements/reviews/MLS-DOC-REV-0002.md)
> - [文档与需求变更控制](docs/00-governance/MLS-DOC-GOV-0004.md)
> - [后端项目说明](library-server/README.md)
> - [工程基础运行与验证](ENGINEERING.md)
> - [前端项目说明](library-frontend/README.md)

MyLibrarySystem 是一个面向单一图书馆的工程学习项目，形态为**图书信息管理系统 + AI 助手**的综合系统：图书信息管理系统承载借阅、归还、逾期罚款等核心流通业务；对话式 AI 助手与业务系统一体运行，实现业务系统与 AI 能力的有机融合。项目以完整系统为验收对象，按照 V 模型依次开展需求分析、总体设计、详细设计和模块设计，并分别对应验收测试、系统测试、集成测试和单元测试。

## 项目定位

- **业务系统**：面向单一图书馆的图书信息管理系统，承载核心流通业务（借阅、归还等，完整范围以需求工件为准）。
- **AI 助手**：内嵌于业务系统的对话式助手，围绕业务数据与业务规则提供服务。
- **有机融合**：AI 助手与业务系统共用同一套数据、业务规则和权限体系，不是独立于业务系统之外的附加聊天界面。

## 学习目标：对照真实 ERP 的 AI 对话助手

本项目的业务规模是课程项目级，与真实 ERP 相差悬殊（见下表），但"AI 对话助手如何在业务数据、业务规则和权限约束下可靠工作"是两者共同的工程问题。项目以真实 ERP 的 AI 对话助手为参照，按下表逐维度对照学习：差距是学习切入点，不是缺陷。

| 维度       | 图书管理系统           | 真实 ERP                                             |
| ---------- | ---------------------- | ---------------------------------------------------- |
| 业务广度   | 一个领域（借阅）       | 几十上百个模块（财务、供应链、生产、人力）跨模块联动 |
| 流程深度   | 借书/还书几条规则      | 审批流、财务记账、MRP 物料计算等深层业务逻辑         |
| 数据规模   | 课程项目级：千行数据   | 百万级事务、多组织并发写入                           |
| 权限体系   | 可能只有一个管理员角色 | 按组织、岗位、数据行级的精细权限                     |
| 集成复杂度 | 单体系统               | 与外部系统（银行、税务、物流）对接                   |

## 项目入口

- [工程文档唯一入口](docs/README.md)
- [工程基础运行与验证](ENGINEERING.md)
- [后端项目与开发说明](library-server/README.md)
- [前端项目与开发说明](library-frontend/README.md)

## 快速上手

### 当前工程状态

| 项目             | 状态                             |
| ---------------- | -------------------------------- |
| 当前阶段         | 需求分析                         |
| 工程文档唯一入口 | [docs/README.md](docs/README.md) |
| 需求基线         | `Not established`                |
| 验收执行         | 尚未执行                         |
| 当前代码地位     | 已重建最小工程骨架，业务功能尚未实现 |
| 工程基础         | B1-A/B1-B 已通过本地与 CI 验证；B1-C 未执行 |

需求分析阶段工程化设计（`MLS-DOC-RA-0001`）的 `v1.0 Approved` 只表示阶段组织方案已经批准，不表示最终需求基线 `v1.0` 已建立。
旧契约、旧计划和旧审计为非规范性历史参考。

### 参与需求评审

1. 从[系统需求主规格](docs/01-requirements/requirements/MLS-DOC-RA-0003.md)进入当前需求工件。
2. 在[Open 问题登记册](docs/01-requirements/requirements/MLS-DOC-RA-0007.md)核对已关闭问题（`MLS-OQ-0001`~`0026`、`MLS-OQ-0028`）的关闭依据与决策人；`MLS-OQ-0027` 为当前唯一 Open 问题（属详细设计机制，需求层外部行为口径已登记 `MLS-BR-0035`），尚未批准的边界不得作为需求值。
3. 使用[需求评审清单](docs/01-requirements/reviews/MLS-DOC-REV-0001.md)记录可复现证据与评审发现。
4. 只有[需求基线记录](docs/01-requirements/reviews/MLS-DOC-REV-0002.md)完成正式批准后，需求工件才可成为下一阶段输入；变更按[变更控制](docs/00-governance/MLS-DOC-GOV-0004.md)处理。

## 修订记录

| 日期 | 内容 |
| --- | --- |
| 2026-09-10 | 同步最小骨架和 B1-A/B1-B 完成状态，新增工程运行与验证入口；业务验收及需求基线状态不变。 |

---

> 被引用文档：
>
> - [工程文档唯一入口](docs/README.md)
> - [后端项目说明](library-server/README.md)
> - [前端项目说明](library-frontend/README.md)
> - [需求分析阶段工程化设计](docs/01-requirements/requirements/MLS-DOC-RA-0001.md)
