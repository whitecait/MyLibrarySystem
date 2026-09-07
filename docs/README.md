# MyLibrarySystem 工程文档入口

| 字段     | 值                                               |
| -------- | ------------------------------------------------ |
| 文档编号 | `MLS-DOC-GOV-0000`                               |
| 文档状态 | `Draft`                                          |
| 文档版本 | `v0.12`                                          |
| 编制日期 | `2026-07-13`                                     |
| 最近更新 | `2026-09-07`                                     |
| 适用阶段 | 需求分析                                         |
| 验证对应 | 验收测试                                         |
| 权威性质 | 工程文档唯一入口；经批准的规范性工件由本入口导航 |

> 引用文档：
>
> - 治理规则：[MLS-DOC-GOV-0001](00-governance/MLS-DOC-GOV-0001.md) 至 [MLS-DOC-GOV-0007](00-governance/MLS-DOC-GOV-0007.md)
> - 需求分析阶段：[MLS-DOC-RA-0001](01-requirements/requirements/MLS-DOC-RA-0001.md)、[MLS-DOC-RA-0002](01-requirements/requirements/MLS-DOC-RA-0002.md)、[MLS-DOC-RA-0003](01-requirements/requirements/MLS-DOC-RA-0003.md)、[MLS-DOC-RA-0004](01-requirements/requirements/MLS-DOC-RA-0004.md)、[MLS-DOC-RA-0005](01-requirements/requirements/MLS-DOC-RA-0005.md)、[MLS-DOC-RA-0006](01-requirements/requirements/MLS-DOC-RA-0006.md)、[MLS-DOC-RA-0007](01-requirements/requirements/MLS-DOC-RA-0007.md)、[MLS-DOC-RA-0008](01-requirements/requirements/MLS-DOC-RA-0008.md)、[MLS-DOC-RA-0009](01-requirements/requirements/MLS-DOC-RA-0009.md)、[MLS-DOC-RA-0010](01-requirements/requirements/MLS-DOC-RA-0010.md)、[MLS-DOC-RA-0011](01-requirements/requirements/MLS-DOC-RA-0011.md)、[MLS-DOC-RA-0012](01-requirements/requirements/MLS-DOC-RA-0012.md)、[MLS-DOC-RA-0013](01-requirements/requirements/MLS-DOC-RA-0013.md)、[MLS-DOC-RA-0014](01-requirements/requirements/MLS-DOC-RA-0014.md)、[MLS-DOC-RA-0015](01-requirements/requirements/MLS-DOC-RA-0015.md)、[MLS-DOC-AT-0001](01-requirements/acceptance/MLS-DOC-AT-0001.md)、[MLS-DOC-AT-0002](01-requirements/acceptance/MLS-DOC-AT-0002.md)、[MLS-DOC-AT-0003](01-requirements/acceptance/MLS-DOC-AT-0003.md)、[MLS-DOC-EVD-0001](01-requirements/evidence/MLS-DOC-EVD-0001.md)、[MLS-DOC-EVD-0002](01-requirements/evidence/MLS-DOC-EVD-0002.md)、[MLS-DOC-REV-0001](01-requirements/reviews/MLS-DOC-REV-0001.md)、[MLS-DOC-REV-0002](01-requirements/reviews/MLS-DOC-REV-0002.md)
> - 后续阶段入口：[MLS-DOC-OD-0001](02-overall-design/MLS-DOC-OD-0001.md)、[MLS-DOC-DD-0001](03-detailed-design/MLS-DOC-DD-0001.md)、[MLS-DOC-MD-0001](04-module-design/MLS-DOC-MD-0001.md)
> - 项目级引导：[根 README](../README.md)、[后端项目说明](../library-server/README.md)、[前端项目说明](../library-frontend/README.md)

## 唯一入口声明

本文件是 MyLibrarySystem 工程文档的唯一入口。项目根 `./README.md` 和各子项目 `./library-frontend/README.md` / `./library-server/README.md` 只提供项目级引导，不重复维护需求、设计、测试或验收事实。

**当前阶段:**
为**需求分析**。当前状态为**需求工件编制中，需求基线未建立**。需求、验收定义和评审记录均为 Draft 工件；需求基线未建立，验收未执行。

## 分层权威模型

当前文档按“入口与导航、治理规则、阶段控制、规范内容、控制与批准、支持材料”分层。层级表示职责和阅读依赖，不表示编号越大权威越高，也不允许 Draft 工件覆盖 Approved 工件。

| 层级          | 职责                                                               | 当前工件                                                                                                                                                                                                                                                       | 权威边界                                                           |
| ------------- | ------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| L0 入口与导航 | 声明当前阶段、维护清单和阅读入口                                   | 本文件 `MLS-DOC-GOV-0000`                                                                                                                                                                                                                                      | 只导航和汇总，不单独产生需求事实                                   |
| L1 治理规则   | 分别定义编号、生命周期与门禁、权威与冲突、变更、决策、全局开发计划和风险登记 | [`MLS-DOC-GOV-0001`](00-governance/MLS-DOC-GOV-0001.md) 至 [`MLS-DOC-GOV-0007`](00-governance/MLS-DOC-GOV-0007.md)                                                                                                                                           | 各文件只在其声明的治理主题内拥有内容所有权                         |
| L2 阶段控制   | 定义需求分析阶段的组织基线和阶段导航                               | [`MLS-DOC-RA-0001`](01-requirements/requirements/MLS-DOC-RA-0001.md)、[`MLS-DOC-RA-0002`](01-requirements/requirements/MLS-DOC-RA-0002.md)                                                                                                                     | RA-0001 是当前唯一 Approved 的阶段组织与工作基线；不是最终需求基线 |
| L3 规范内容   | 定义需求主规格、专题需求和验收定义                                 | [`MLS-DOC-RA-0003`](01-requirements/requirements/MLS-DOC-RA-0003.md)、[`requirements/`](01-requirements/requirements/)、[`MLS-DOC-AT-0001`](01-requirements/acceptance/MLS-DOC-AT-0001.md)、[`MLS-DOC-AT-0002`](01-requirements/acceptance/MLS-DOC-AT-0002.md) | 只有被基线记录按明确版本批准的内容才成为正式需求基线               |
| L4 控制与批准 | 维护派生追踪、评审检查、基线范围和批准证据                         | [`MLS-DOC-RA-0014`](01-requirements/requirements/MLS-DOC-RA-0014.md)、[`reviews/`](01-requirements/reviews/)                                                                                                                                                   | 不产生新的需求事实；`MLS-DOC-REV-0002` 负责记录被激活的基线集合    |
| 支持材料      | 保存执行证据、分析证据和历史资料                                   | [`MLS-DOC-AT-0003`](01-requirements/acceptance/MLS-DOC-AT-0003.md)、[`evidence/`](01-requirements/evidence/)                                                                                                                                                   | 非规范性或仅在实际执行后形成证据，不得覆盖规范内容                 |

### 内容所有权与生效规则

1. [文档权威与冲突处理](00-governance/MLS-DOC-GOV-0003.md)定义分层关系和冲突处理；编号、门禁、变更和决策分别由对应治理文件定义。
2. `MLS-DOC-RA-0003` 维护系统级目标、范围与约束的受控汇总；`MLS-DOC-RA-0004` 至 `MLS-DOC-RA-0013` 维护各专题详细事实；验收策略和验收定义由 `MLS-DOC-AT-0001`、`MLS-DOC-AT-0002` 维护。
3. `MLS-DOC-RA-0014` 是派生追踪索引。它与来源工件不一致时，应修正矩阵并发起变更评审，不得用矩阵反向改写需求或验收定义。
4. `MLS-DOC-REV-0002` 只有在列出完整工件 ID、版本、批准人、日期和结论后，才能激活需求基线。Git 提交、链接、静态检查或单份文档状态不能替代基线批准。
5. 同一内容所有权范围内以最后批准版本为准。不同所有者之间出现冲突时，保持“冲突未解决”状态，按变更控制同步更新所有受影响工件。

### 当前有效状态

- `MLS-DOC-RA-0001 v1.0` 是已标记 Approved 的需求分析阶段组织与工作基线。
- 需求主规格、专题需求、验收定义、追踪和基线记录仍为 Draft；最终需求基线为 `Not established`。
- G3 至 G5 仍为 `Blocked`，G2 已于 2026-08-14 通过；系统验收尚未执行，后三个设计阶段尚未开始。
- `MLS-DOC-RA-0001 v1.0` 中的旧目标目录（§3.1 目录树仍含 `90-archive/`）和箭头式权威关系（§3.3）仍属于上次批准内容。G0 治理重评审已于 2026-07-24 通过，分层模型作为当前维护与评审导航；但不得宣称已经覆盖或静默改写该批准版本。
- 全局开发计划已按 M0 正式化为 `MLS-DOC-GOV-0006`（2026-09-05）：G3/G4 复审与 G5 基线批准（对应计划里程碑 M2）是进入设计阶段的唯一路径；全量重写策略已登记为 `MLS-DEC-0007`。
- `MLS-DOC-RA-0001` 至 `MLS-DOC-RA-0003` 现位于 `01-requirements/requirements/`。
- 项目定位调整（图书信息管理系统 + AI 助手综合系统）已登记为 `MLS-DEC-0010`（2026-09-05）。
- AI 助手需求已按 `MLS-DEC-0010`/`MLS-DEC-0011` 传导：`MLS-DOC-RA-0003` 修订约束 `MLS-CON-0003` 并新增 `MLS-OBJ-0005`，`MLS-DOC-RA-0004` 至 `MLS-DOC-RA-0013` 增补 AI 专题内容，`MLS-DOC-AT-0001`/`0002` 增补 AI 验收口径与用例；`MLS-DOC-RA-0007` 登记 `MLS-OQ-0016`~`0019`（2026-09-05 登记）；AI 需求收尾（2026-09-06，`MLS-DEC-0012`）后 `MLS-OQ-0016`~`0020` 全部关闭（截至 2026-09-06 登记册无 Open 项），并新增 `MLS-FR-0035`、`MLS-BR-0030`/`0031`、`MLS-IR-0015`、`MLS-NFR-AI-0006`、`MLS-AC-0035`。
- 原型确认业务决定已登记为正式决策（2026-09-07，来源 `HANDOFF-20260906`）：预约到馆工作流业务模型、借阅办理与事务终态、首页到馆统计口径登记为 `MLS-DEC-0013`~`0015`（2026-09-06 评审拍板），读者页业务口径登记为 `MLS-DEC-0016`（2026-09-07 评审拍板）；正式需求工件的传导尚未执行，登记为 `MLS-OQ-0025`/`0026`，与 `MLS-DOC-RA-0001` 外部集成约束行差异同按“冲突未解决、经变更控制处置”模式管理。
- `MLS-DOC-RA-0007` 当前 Open 项为 `MLS-OQ-0021`~`0027`（2026-09-07 登记）：`MLS-OQ-0021` AI 呈现形态统一口径、`MLS-OQ-0022` AI 设计阶段技术口径确认、`MLS-OQ-0023` AI 调用额度计数粒度、`MLS-OQ-0024` AI 会话日志事件粒度、`MLS-OQ-0025` 预约到馆工作流正式需求传导、`MLS-OQ-0026` 读者页业务口径正式需求传导、`MLS-OQ-0027` 高并发下保证状态变化处理规则。
- `MLS-DOC-RA-0001 v1.0`（Approved）中的外部系统范围约束行仍为"除本地支付模拟器外不接入其他外部系统"的旧口径，与修订后的 `MLS-CON-0003`（允许 AI 模型服务端点）存在口径差异；该批准版本未被改写，差异在此显式登记，随 G3 复审按变更控制同步处置。
- `MLS-DOC-RA-0003` §3、`MLS-DOC-RA-0004` §4 与 `MLS-DOC-RA-0001 v1.0` §7.3 的范围排除行仍含「预约排队、业务通知」，与 `MLS-OQ-0001` 关闭口径（预约借阅+书目队列）、`MLS-FR-0028` 需求簇及 `MLS-DEC-0013`~`0016` 存在口径差异；Draft 文档（RA-0003/RA-0004）的排除行留待 `MLS-OQ-0025`/`0026` 传导时按变更控制修订，批准版本（RA-0001）不改写，差异在此显式登记。

## 推荐阅读顺序

1. 本文件：确认当前阶段、分层权威模型和维护清单。
2. 治理规则：`MLS-DOC-GOV-0003` → `MLS-DOC-GOV-0001` → `MLS-DOC-GOV-0002` → `MLS-DOC-GOV-0004` → `MLS-DOC-GOV-0005` → `MLS-DOC-GOV-0006`；`MLS-DOC-GOV-0007`（风险登记册）按需查阅。
3. 阶段控制：`MLS-DOC-RA-0002` → `MLS-DOC-RA-0001`。
4. 规范内容：`MLS-DOC-RA-0003` → `MLS-DOC-RA-0004` 至 `MLS-DOC-RA-0007` → `MLS-DOC-RA-0008` 至 `MLS-DOC-RA-0013`。
5. 验收与追踪：`MLS-DOC-AT-0001` → `MLS-DOC-AT-0002` → `MLS-DOC-RA-0014` → `MLS-DOC-AT-0003`。
6. 评审与基线：`MLS-DOC-REV-0001` → `MLS-DOC-REV-0002`。
7. `evidence/` 按需读取；`02-overall-design/`、`03-detailed-design/`、`04-module-design/` 当前仅包含阶段入口文档。

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

以下清单中列出了 `docs/` 下所有已写入的文档。
该清单是 `MLS-DOC-GOV-0000` 的规范性内容——不在清单中的文档视为未提交或已排除。
显式排除项：`docs/agents/` 下的代理操作说明与仓库根目录 `AGENTS.md`（2026-09-07 用户决定不入库，作为本地文件维护，不占编号）。
开发过程文档：仓库根目录的 `HANDOFF.md` 与 `TODO.md` 纳入 git 追踪（2026-09-07 用户决定），作为开发过程记录维护；不属于工程文档库，不在本清单与文档治理范围内。

### 治理框架 (00-governance)

| 文档编号           | 标题                                                    | 状态    |
| ------------------ | ------------------------------------------------------- | ------- |
| `MLS-DOC-GOV-0000` | [MyLibrarySystem 工程文档入口](.)                       | `Draft` |
| `MLS-DOC-GOV-0001` | [编号规范](00-governance/MLS-DOC-GOV-0001.md)           | `Draft` |
| `MLS-DOC-GOV-0002` | [工程生命周期与门禁](00-governance/MLS-DOC-GOV-0002.md) | `Draft` |
| `MLS-DOC-GOV-0003` | [文档权威与冲突处理](00-governance/MLS-DOC-GOV-0003.md) | `Draft` |
| `MLS-DOC-GOV-0004` | [文档与需求变更控制](00-governance/MLS-DOC-GOV-0004.md) | `Draft` |
| `MLS-DOC-GOV-0005` | [工程决策日志](00-governance/MLS-DOC-GOV-0005.md)       | `Draft` |
| `MLS-DOC-GOV-0006` | [全局开发计划](00-governance/MLS-DOC-GOV-0006.md)       | `Draft` |
| `MLS-DOC-GOV-0007` | [风险登记册](00-governance/MLS-DOC-GOV-0007.md)         | `Draft` |

### 需求分析阶段 (01-requirements) ── 阶段入口

| 文档编号          | 标题                                                                                      | 状态       |
| ----------------- | ----------------------------------------------------------------------------------------- | ---------- |
| `MLS-DOC-RA-0001` | [MyLibrarySystem 需求分析阶段工程化设计](01-requirements/requirements/MLS-DOC-RA-0001.md) | `Approved` |
| `MLS-DOC-RA-0002` | [需求分析文档入口](01-requirements/requirements/MLS-DOC-RA-0002.md)                       | `Draft`    |
| `MLS-DOC-RA-0003` | [MyLibrarySystem 系统需求主规格](01-requirements/requirements/MLS-DOC-RA-0003.md)         | `Draft`    |

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
| `MLS-DOC-RA-0015` | [P1 质量与验收政策草案](01-requirements/requirements/MLS-DOC-RA-0015.md)  | `Draft` |

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

## 变更记录

| 日期       | 版本  | 变更内容                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| ---------- | ----- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 2026-09-07 | v0.12 | 文档维护基线声明开发过程文档：仓库根目录 `HANDOFF.md` 与 `TODO.md` 纳入 git 追踪（2026-09-07 用户决定），不属工程文档库、不在本清单与文档治理范围内                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| 2026-09-07 | v0.11 | 文档维护基线显式声明排除项：`docs/agents/` 代理操作说明与仓库根目录 `AGENTS.md`（2026-09-07 用户决定不入库，作为本地文件维护，不占编号）                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| 2026-09-07 | v0.10 | 当前有效状态：新增登记 `MLS-DOC-RA-0003` §3/`MLS-DOC-RA-0004` §4/`MLS-DOC-RA-0001` §7.3 的「预约排队、业务通知」排除行与 `MLS-OQ-0001` 关闭口径及 `MLS-DEC-0013`~`0016` 的差异（Draft 排除行随 `MLS-OQ-0025`/`0026` 传导处置）；扩展 RA-0001 旧目标目录登记条目并点名 §3.1/§3.3 位置；开头引用列表逐条列出需求分析阶段工件。原因：`MLS-OQ-0025`/`0026` 影响工件补全后需按 GOV-0003 冲突处理第 3/5 条显式登记差异；提出人：文档维护轮（ZCode）；影响需求：无（仅登记，不改写工件）；影响验收：无；评审人/批准人：lingzz（业务代表，修订计划评审拍板）；结论：排除行冲突显式化，传导闭环成立 |
| 2026-09-07 | v0.9  | 落库 `HANDOFF-20260906` 原型确认业务决定：`MLS-DEC-0013`~`0016` 登记；`MLS-DOC-RA-0007` 新登记 `MLS-OQ-0021`~`0027` 并同步当前 Open 状态表述；显式登记预约工作流与读者页口径的需求传导差异（`MLS-OQ-0025`/`0026`）                                                                                                                                                                                                                                                                                                                                                                         |
| 2026-09-06 | v0.8  | 维护基线登记 `MLS-DOC-GOV-0007`（风险登记册）：登记需求基线签署前识别的风险 `MLS-RISK-0001`~`0011`，仅含风险描述与判断来源依据；治理引用范围与 L1 治理规则行同步至 GOV-0007                                                                                                                                                                                                                                                                                                                                                                                                                |
| 2026-09-06 | v0.7  | 当前有效状态同步 AI 需求收尾事实（`MLS-DEC-0012`）：`MLS-OQ-0016`~`0020` 全部关闭、无 Open 项；新增需求条目 `MLS-FR-0035`、`MLS-BR-0030`/`0031`、`MLS-IR-0015`、`MLS-NFR-AI-0006`、`MLS-AC-0035` 已登记并纳入追踪                                                                                                                                                                                                                                                                                                                                                                          |
| 2026-09-05 | v0.6  | 当前有效状态补充 AI 需求传导事实：`MLS-CON-0003` 修订、`MLS-OBJ-0005` 登记、RA-0004~0013 与 AT-0001/0002 增补 AI 内容、`MLS-OQ-0016`~`0019` 登记（Open）；显式登记 `MLS-DOC-RA-0001` 外部集成约束行与 `MLS-CON-0003` 的口径差异（待 G3 复审处置）                                                                                                                                                                                                                                                                                                                                          |
| 2026-09-05 | v0.5  | 修复分层权威模型表中 RA-0003 链接残留旧路径；移除对 `docs/90-archive/` 的引用（该目录不存在，`MLS-DOC-ARC-0001` 已退役）；当前有效状态补充 RA-0001 至 RA-0003 现位置与 `MLS-DEC-0010`                                                                                                                                                                                                                                                                                                                                                                                                      |
| 2026-09-05 | v0.4  | 按文档规范补充开头引用文档列表与结尾被引用文档列表；同步 `MLS-DOC-RA-0001` 至 `MLS-DOC-RA-0003` 移入 `requirements/` 后的链接路径；将过程性表述改为当前状态表述                                                                                                                                                                                                                                                                                                                                                                                                                            |
| 2026-09-05 | v0.3  | 维护基线登记 `MLS-DOC-GOV-0006`（全局开发计划，M0 计划落盘）；当前有效状态补充 G3/G4/G5 路径与 `MLS-DEC-0007`                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| 2026-08-20 | v0.2  | 当前有效状态补充 G0 已通过（2026-07-24）事实，明确分层模型为当前维护与评审导航                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| 2026-07-13 | v0.1  | 初稿                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |

---

> 被引用文档：
>
> - [编号规范](00-governance/MLS-DOC-GOV-0001.md)
> - [文档权威与冲突处理](00-governance/MLS-DOC-GOV-0003.md)
> - [全局开发计划](00-governance/MLS-DOC-GOV-0006.md)
> - [风险登记册](00-governance/MLS-DOC-GOV-0007.md)
> - [需求分析阶段工程化设计](01-requirements/requirements/MLS-DOC-RA-0001.md)
> - [需求评审清单](01-requirements/reviews/MLS-DOC-REV-0001.md)
> - [根 README](../README.md)
> - [后端项目说明](../library-server/README.md)
> - [前端项目说明](../library-frontend/README.md)
