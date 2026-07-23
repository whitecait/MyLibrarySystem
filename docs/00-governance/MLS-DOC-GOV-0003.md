# 文档权威与冲突处理

| 字段     | 值                                       |
| -------- | ---------------------------------------- |
| 文档编号 | `MLS-DOC-GOV-0003`                       |
| 文档状态 | `Draft`                                  |
| 文档版本 | `v0.1`                                   |
| 编制日期 | `2026-07-13`                             |
| 适用阶段 | 全生命周期治理；当前处于需求分析         |
| 验证对应 | 评审与基线控制                           |
| 权威性质 | 规范性、证据性与历史性工件的权威顺序规则 |

## 固定权威顺序

规范性文档的固定权威顺序为：[`docs/README.md`](../README.md) → [`docs/00-governance/MLS-DOC-GOV-0003.md`](MLS-DOC-GOV-0003.md) → [`docs/01-requirements/MLS-DOC-RA-0003.md`](../01-requirements/MLS-DOC-RA-0003.md) → [`docs/01-requirements/requirements/`](../01-requirements/requirements/) + [`docs/01-requirements/acceptance/`](../01-requirements/acceptance/) + [`docs/01-requirements/reviews/`](../01-requirements/reviews/)。

1. [`docs/README.md`](../README.md) 是工程文档唯一入口，声明当前阶段、状态和导航。
2. [`MLS-DOC-GOV-0003.md`](MLS-DOC-GOV-0003.md) 定义权威层级和冲突处理规则。
3. [`MLS-DOC-RA-0003.md`](../01-requirements/MLS-DOC-RA-0003.md) 在建立后承担需求主规格入口职责；未建立或未批准时不得视为需求基线。
4. [`requirements/`](../01-requirements/requirements/)、[`acceptance/`](../01-requirements/acceptance/) 和 [`reviews/`](../01-requirements/reviews/) 在建立后分别承载专题规范、验收定义及评审与基线记录，并受主规格约束。

## 工件性质

| 类别   | 目录或工件                                                   | 规则                                                     |
| ------ | ------------------------------------------------------------ | -------------------------------------------------------- |
| 规范性 | 权威链中最后批准的主规格、专题登记册、验收定义和评审基线记录 | 可以作为后续阶段正式输入；状态必须可核验                 |
| 证据性 | `docs/01-requirements/evidence/`                             | 非规范性；只记录来源、现状、评估和复用判断，不产生新需求 |
| 历史性 | [`docs/90-archive/`](../90-archive/README.md)                | 非规范性；只用于历史追溯，不构成当前契约或验收依据       |

`evidence/` 与 `90-archive/` 明确为非规范性资料。现有代码、旧文档、旧测试和审计报告可以支持分析，但不能单独批准或覆盖规范性要求。

## 冲突处理

当工件之间出现冲突时，以最后批准的规范性工件为准，并发起变更评审。处理过程必须：

1. 标识冲突项、各自版本、状态和批准记录；
2. 记录受影响的需求、验收定义、追踪关系和后续阶段输入；
3. 按[变更控制](MLS-DOC-GOV-0004.md)开展影响分析、评审和批准；
4. 在结论批准后同步更新受影响工件，不删除历史 ID 或批准记录。

Draft、In Review 或仅已提交的内容不得覆盖最后批准的规范性工件。若不存在已批准的规范性工件，必须保持“未建立基线”状态，不得从证据或归档资料推定结论。
