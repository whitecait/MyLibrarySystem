# MyLibrarySystem

> 引用文档：
>
> - [工程文档唯一入口](docs/README.md)
> - [后续实证审计与人工清单](HANDOFF-AUDIT-20260912.md)
> - [编码前准备收尾](HANDOFF-CODING-PREP-20260912.md)
> - [文档交叉审计与维护报告](HANDOFF-AUDIT-20260913.md)
> - [模块设计评审与基线记录](docs/04-module-design/reviews/MLS-DOC-REV-0005.md)
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

## 当前有效状态

本节是项目当前状态的唯一概述，更新至 2026-09-17 匿名 AI 契约受影响修订候选。

M2 需求基线为 `v1.0 Approved`。前端 B0～B5 与后端 B0～B4 已在 `codex/unified-implementation` 形成提交。2026-09-17 匿名 AI 契约差异获 lingzz 批准，REV-0004 v1.5、REV-0005 v1.2 与 OpenAPI 2.0 成为当前基线，OQ-0029 已关闭。后端 AI、匿名所有权迁移、前端匿名接合、系统测试和产品验收尚未完成。

全部未完成工作以 [开发任务规划](TODO.md) 为准。匿名 AI 契约虽已批准，但按用户选择暂不重建前端生成物，CONTRACT-002 与完整 CONTRACT-003 仍阻塞该修订分支合入实现分支。Grafana 实页、真实页面场景和 M6 产品验收仍在各自阶段执行。

### 当前工程状态

| 项目             | 状态                                                      |
| ---------------- | --------------------------------------------------------- |
| 当前阶段         | M5 部分实现完成；匿名 AI 契约受影响修订                    |
| 工程文档唯一入口 | [docs/README.md](docs/README.md)                          |
| 需求基线         | `v1.0 Approved`                                           |
| 设计评审         | REV-0003 v1.3、REV-0004 v1.5、REV-0005 v1.2 `Approved`   |
| Open 问题        | 无                                                         |
| 验收执行         | 尚未执行                                                  |
| 当前代码地位     | 前端 B0～B5、后端 B0～B4 已提交；后端 AI 与匿名契约未实现 |
| 工程基础         | B1-A/B1-B、C0~C4、G3/G4/G5 完成；修补轮与编码准备收尾完成 |

需求分析阶段工程化设计（`MLS-DOC-RA-0001`）的 `v1.0 Approved` 表示阶段组织方案；最终需求基线由 `MLS-DOC-REV-0002 v1.0` 于 2026-09-11 激活。产品验收未执行，原型验证与迁移测试不替代系统验收。旧契约、旧计划和旧审计为非规范性历史参考。

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

## 参与需求评审

1. 从[系统需求主规格](docs/01-requirements/requirements/MLS-DOC-RA-0003.md)进入当前需求工件。
2. 在[Open 问题登记册](docs/01-requirements/requirements/MLS-DOC-RA-0007.md)核对关闭依据与决策人。当前无 Open 项；OQ-0029 的匿名 AI 契约批准与关闭依据登记于 2026-09-17 条目。
3. 使用[需求评审清单](docs/01-requirements/reviews/MLS-DOC-REV-0001.md)记录可复现证据与评审发现。
4. 只有[需求基线记录](docs/01-requirements/reviews/MLS-DOC-REV-0002.md)完成正式批准后，需求工件才可成为下一阶段输入；变更按[变更控制](docs/00-governance/MLS-DOC-GOV-0004.md)处理。

## 修订记录

- 2026-09-17：lingzz 明确批准匿名 AI 契约差异；REV-0004 v1.5、REV-0005 v1.2 与 OpenAPI 2.0 成为当前批准基线，OQ-0029 关闭。生成物与实现门禁仍未恢复。

- 2026-09-17：同步前端 B0～B5、后端 B0～B4 的提交事实，登记 OQ-0029 与 REV-0004 v1.5/REV-0005 v1.2/OpenAPI 2.0 `In Review`；匿名实现、生成物同步和 CONTRACT-002 恢复仍未完成，不新增批准结论。

- 2026-09-14：同步 H4/H5、8 图复核、AI `thinking=disabled`、OQ-0027 关闭及 M3/M4/模块设计基线 Approved。批准人：lingzz；业务实现与产品验收尚未完成。

- 2026-09-13：登记模块 spec 候选材料、47 项功能覆盖与 REV-0005 `In Review`；正式模块基线、业务实现和产品验收状态不变。
- 2026-09-13：执行全量交叉审计与维护。当前状态口径全库统一，合并重复的修订记录节，删除被取代的历史状态段；已完成事实收敛至 [HANDOFF.md](HANDOFF.md)，待办收敛至 [TODO.md](TODO.md)；维护报告见 [HANDOFF-AUDIT-20260913.md](HANDOFF-AUDIT-20260913.md)。
- 2026-09-12：按 lingzz 后续补齐指令同步实证审计入口；登记 Docker 补跑、迁移与罚款约束修复、OQ-0027 复开，不新增批准或产品验收结论。
- 2026-09-11：登记 G3/G4/G5 按授权签署、质量协议与配置核对完成，需求基线 `v1.0 Approved` 激活。
- 2026-09-10：同步最小骨架与 B1-A/B1-B 完成状态，新增工程运行与验证入口。

> 被引用文档：
>
> - [工程文档唯一入口](docs/README.md)
> - [后端项目说明](library-server/README.md)
> - [前端项目说明](library-frontend/README.md)
> - [需求分析阶段工程化设计](docs/01-requirements/requirements/MLS-DOC-RA-0001.md)
