# MyLibrarySystem 工程文档入口

| 字段 | 值 |
| --- | --- |
| 文档编号 | `MLS-DOC-GOV-0001` |
| 文档状态 | `Draft` |
| 文档版本 | `v0.1` |
| 编制日期 | `2026-07-13` |
| 适用阶段 | 需求分析 |
| 验证对应 | 验收测试 |
| 权威性质 | 工程文档唯一入口；经批准的规范性工件由本入口导航 |

## 唯一入口声明

本文件是 MyLibrarySystem 工程文档的唯一入口。项目根 README 和各子项目 README 只提供项目级引导，不重复维护需求、设计、测试或验收事实。

当前阶段为**需求分析**。当前状态为**需求工件编制中，需求基线未建立**。这表示需求、验收定义和评审记录仍在形成，不表示任何需求基线已经批准，也不表示任何验收已经执行。

## 权威链

当前权威顺序为：`docs/README.md → docs/00-governance/DOCUMENT-AUTHORITY.md → docs/01-requirements/SYSTEM-REQUIREMENTS.md → docs/01-requirements/requirements/ + docs/01-requirements/acceptance/ + docs/01-requirements/reviews/`。

- [文档权威与冲突处理](00-governance/DOCUMENT-AUTHORITY.md)
- [工程生命周期与门禁](00-governance/ENGINEERING-LIFECYCLE.md)
- [变更控制](00-governance/CHANGE-CONTROL.md)
- [决策日志](00-governance/DECISION-LOG.md)
- [已批准的需求分析阶段组织与工作基线](01-requirements/REQUIREMENTS-STAGE-DESIGN.md)

只有最后批准的规范性工件可以作为后续阶段的正式输入。当前尚未建立的需求主规格与专题登记册不得被视为已批准内容。

## V 模型四阶段导航

| 左侧工程阶段 | 右侧验证阶段 | 当前导航与状态 |
| --- | --- | --- |
| 需求分析 | 验收测试 | [生命周期定义](00-governance/ENGINEERING-LIFECYCLE.md#需求分析--验收测试)；当前编制需求工件，尚未建立需求基线，尚未执行验收 |
| 总体设计 | 系统测试 | [生命周期定义](00-governance/ENGINEERING-LIFECYCLE.md#总体设计--系统测试)；当前只建入口，不写结论 |
| 详细设计 | 集成测试 | [生命周期定义](00-governance/ENGINEERING-LIFECYCLE.md#详细设计--集成测试)；当前只建入口，不写结论 |
| 模块设计 | 单元测试 | [生命周期定义](00-governance/ENGINEERING-LIFECYCLE.md#模块设计--单元测试)；当前只建入口，不写结论 |

## 工件类型边界

- 计划描述拟开展的活动、责任和预期门禁，不是执行证据。
- 测试用例描述可重复、可观察、可判定的验证场景，不表示场景已经执行。
- 执行报告只记录实际执行结果、证据和缺陷；没有执行证据时必须明确写为“尚未执行”。

计划、测试用例和执行报告必须分别维护，不得相互替代，也不得用静态检查或历史结果冒充当前验收证据。

## 归档入口

[历史资料归档索引](90-archive/README.md)仅用于追溯。`docs/90-archive/` 中的资料均为非规范性资料，不构成当前需求、设计、实现或验收依据。
