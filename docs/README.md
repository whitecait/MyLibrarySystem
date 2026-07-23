# MyLibrarySystem 工程文档入口

| 字段     | 值                                               |
| -------- | ------------------------------------------------ |
| 文档编号 | `MLS-DOC-GOV-0000`                               |
| 文档状态 | `Draft`                                          |
| 文档版本 | `v0.1`                                           |
| 编制日期 | `2026-07-13`                                     |
| 适用阶段 | 需求分析                                         |
| 验证对应 | 验收测试                                         |
| 权威性质 | 工程文档唯一入口；经批准的规范性工件由本入口导航 |

## 唯一入口声明

本文件是 MyLibrarySystem 工程文档的唯一入口。项目根 `./README.md` 和各子项目 `./library-frontend/README.md` / `./library-server/README.md` 只提供项目级引导，不重复维护需求、设计、测试或验收事实。

**当前阶段:**
为**需求分析**。当前状态为**需求工件编制中，需求基线未建立**。这表示需求、验收定义和评审记录仍在形成，不表示任何需求基线已经批准，也不表示任何验收已经执行。

> @TODO: ▼以下内容需要审查确认▼

## 权威链

当前权威顺序为：

- [MyLibrarySystem 工程文档入口](.)
- [文档权威与冲突处理](00-governance/MLS-DOC-GOV-0003.md)
- [MyLibrarySystem 系统需求主规格](01-requirements/MLS-DOC-RA-0003.md)
- [`docs/01-requirements/requirements/`](01-requirements/requirements/) / [`docs/01-requirements/acceptance/`](01-requirements/acceptance/) / [`docs/01-requirements/reviews/`](01-requirements/reviews/)

- [文档权威与冲突处理](00-governance/MLS-DOC-GOV-0003.md)
- [工程生命周期与门禁](00-governance/MLS-DOC-GOV-0002.md)
- [变更控制](00-governance/MLS-DOC-GOV-0004.md)
- [决策日志](00-governance/MLS-DOC-GOV-0005.md)
- [已批准的需求分析阶段组织与工作基线](01-requirements/MLS-DOC-RA-0001.md)

只有最后批准的规范性工件可以作为后续阶段的正式输入。当前尚未建立的需求主规格与专题登记册不得被视为已批准内容。

> @TODO: ▲以上内容需要审查确认▲

## V 模型四阶段导航

| 左侧工程阶段 | 右侧验证阶段 | 当前导航与状态                                                                                                         |
| ------------ | ------------ | ---------------------------------------------------------------------------------------------------------------------- |
| 需求分析     | 验收测试     | [生命周期定义](00-governance/MLS-DOC-GOV-0002.md#需求分析--验收测试)；当前编制需求工件，尚未建立需求基线，尚未执行验收 |
| 总体设计     | 系统测试     | [生命周期定义](00-governance/MLS-DOC-GOV-0002.md#总体设计--系统测试)；当前只建入口，不写结论                           |
| 详细设计     | 集成测试     | [生命周期定义](00-governance/MLS-DOC-GOV-0002.md#详细设计--集成测试)；当前只建入口，不写结论                           |
| 模块设计     | 单元测试     | [生命周期定义](00-governance/MLS-DOC-GOV-0002.md#模块设计--单元测试)；当前只建入口，不写结论                           |

## 工件类型边界

- 计划描述拟开展的活动、责任和预期门禁，不是执行证据。
- 测试用例描述可重复、可观察、可判定的验证场景，不表示场景已经执行。
- 执行报告只记录实际执行结果、证据和缺陷；没有执行证据时必须明确写为“尚未执行”。

计划、测试用例和执行报告必须分别维护，不得相互替代，也不得用静态检查或历史结果冒充当前验收证据。

## 文档维护基线

以下清单中列出了 `docs/` 下所有已写入的文档（`docs/90-archive/` 除外）。
该清单是 `MLS-DOC-GOV-0000` 的规范性内容——不在清单中的文档视为未提交或已排除。

### 治理框架 (00-governance)

| 文档编号           | 标题                                                    | 状态    |
| ------------------ | ------------------------------------------------------- | ------- |
| `MLS-DOC-GOV-0000` | [MyLibrarySystem 工程文档入口](.)                       | `Draft` |
| `MLS-DOC-GOV-0001` | [编号规范](00-governance/MLS-DOC-GOV-0001.md)           | `Draft` |
| `MLS-DOC-GOV-0002` | [工程生命周期与门禁](00-governance/MLS-DOC-GOV-0002.md) | `Draft` |
| `MLS-DOC-GOV-0003` | [文档权威与冲突处理](00-governance/MLS-DOC-GOV-0003.md) | `Draft` |
| `MLS-DOC-GOV-0004` | [文档与需求变更控制](00-governance/MLS-DOC-GOV-0004.md) | `Draft` |
| `MLS-DOC-GOV-0005` | [工程决策日志](00-governance/MLS-DOC-GOV-0005.md)       | `Draft` |

### 需求分析阶段 (01-requirements) ── 阶段入口

| 文档编号          | 标题                                                                         | 状态       |
| ----------------- | ---------------------------------------------------------------------------- | ---------- |
| `MLS-DOC-RA-0001` | [MyLibrarySystem 需求分析阶段工程化设计](01-requirements/MLS-DOC-RA-0001.md) | `Approved` |
| `MLS-DOC-RA-0002` | [需求分析文档入口](01-requirements/MLS-DOC-RA-0002.md)                       | `Draft`    |
| `MLS-DOC-RA-0003` | [MyLibrarySystem 系统需求主规格](01-requirements/MLS-DOC-RA-0003.md)         | `Draft`    |

#### 需求分析阶段 (01-requirements/acceptance/) ── 验收

| 文档编号          | 标题                                                                    | 状态    |
| ----------------- | ----------------------------------------------------------------------- | ------- |
| `MLS-DOC-AT-0001` | [系统验收策略](01-requirements/acceptance/MLS-DOC-AT-0001.md)           | `Draft` |
| `MLS-DOC-AT-0002` | [系统验收条件与验收用例](01-requirements/acceptance/MLS-DOC-AT-0002.md) | `Draft` |
| `MLS-DOC-AT-0003` | [系统验收执行报告登记册](01-requirements/acceptance/MLS-DOC-AT-0003.md) | `Draft` |

#### 需求分析阶段 (01-requirements/evidence/) ── 证据

| 文档编号           | 标题                                                             | 状态    |
| ------------------ | ---------------------------------------------------------------- | ------- |
| `MLS-DOC-EVD-0001` | [旧文档证据评估](01-requirements/evidence/MLS-DOC-EVD-0001.md)   | `Draft` |
| `MLS-DOC-EVD-0002` | [代码复用候选台账](01-requirements/evidence/MLS-DOC-EVD-0002.md) | `Draft` |

#### 需求分析阶段 (01-requirements/requirements/) ── 专题工件

| 文档编号          | 标题                                                                      | 状态    |
| ----------------- | ------------------------------------------------------------------------- | ------- |
| `MLS-DOC-RA-0004` | [愿景与范围](01-requirements/requirements/MLS-DOC-RA-0004.md)             | `Draft` |
| `MLS-DOC-RA-0005` | [参与者与系统语境](01-requirements/requirements/MLS-DOC-RA-0005.md)       | `Draft` |
| `MLS-DOC-RA-0006` | [术语表](01-requirements/requirements/MLS-DOC-RA-0006.md)                 | `Draft` |
| `MLS-DOC-RA-0007` | [Open 问题登记册](01-requirements/requirements/MLS-DOC-RA-0007.md)        | `Draft` |
| `MLS-DOC-RA-0008` | [业务流程](01-requirements/requirements/MLS-DOC-RA-0008.md)               | `Draft` |
| `MLS-DOC-RA-0009` | [系统用例](01-requirements/requirements/MLS-DOC-RA-0009.md)               | `Draft` |
| `MLS-DOC-RA-0010` | [功能需求](01-requirements/requirements/MLS-DOC-RA-0010.md)               | `Draft` |
| `MLS-DOC-RA-0011` | [业务规则](01-requirements/requirements/MLS-DOC-RA-0011.md)               | `Draft` |
| `MLS-DOC-RA-0012` | [信息需求](01-requirements/requirements/MLS-DOC-RA-0012.md)               | `Draft` |
| `MLS-DOC-RA-0013` | [非功能需求](01-requirements/requirements/MLS-DOC-RA-0013.md)             | `Draft` |
| `MLS-DOC-RA-0014` | [需求与验收双向追踪矩阵](01-requirements/requirements/MLS-DOC-RA-0014.md) | `Draft` |

#### 需求分析阶段 (01-requirements/reviews/) ── 评审

| 文档编号           | 标题                                                        | 状态    |
| ------------------ | ----------------------------------------------------------- | ------- |
| `MLS-DOC-REV-0001` | [需求评审清单](01-requirements/reviews/MLS-DOC-REV-0001.md) | `Draft` |
| `MLS-DOC-REV-0002` | [需求基线记录](01-requirements/reviews/MLS-DOC-REV-0002.md) | `Draft` |

### 总体设计阶段 (02-overall-design)

| 文档编号          | 标题                                                     | 状态    |
| ----------------- | -------------------------------------------------------- | ------- |
| `MLS-DOC-OD-0001` | [总体设计阶段入口](02-overall-design/MLS-DOC-OD-0001.md) | `Draft` |

### 详细设计阶段 (03-detailed-design)

| 文档编号          | 标题                                                      | 状态    |
| ----------------- | --------------------------------------------------------- | ------- |
| `MLS-DOC-DD-0001` | [详细设计阶段入口](03-detailed-design/MLS-DOC-DD-0001.md) | `Draft` |

### 模块设计阶段 (04-module-design)

| 文档编号          | 标题                                                    | 状态    |
| ----------------- | ------------------------------------------------------- | ------- |
| `MLS-DOC-MD-0001` | [模块设计阶段入口](04-module-design/MLS-DOC-MD-0001.md) | `Draft` |

## 归档入口

[历史资料归档索引](90-archive/README.md)仅用于追溯。`docs/90-archive/` 中的资料均为非规范性资料，不构成当前需求、设计、实现或验收依据。
