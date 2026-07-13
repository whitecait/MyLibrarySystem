# 旧文档证据评估

| 字段 | 值 |
| --- | --- |
| 文档编号 | `MLS-DOC-EVD-0001` |
| 文档状态 | `Draft` |
| 文档版本 | `v0.1` |
| 编制日期 | `2026-07-13` |
| 适用阶段 | 需求分析 |
| 权威性质 | `证据性` |

## 使用边界

本评估逐项承接 [`docs/90-archive/README.md`](../../90-archive/README.md) 的八个来源类别。所有旧文档均为 `Historical Reference`，只能帮助识别历史意图、冲突和复核线索，不能产生或批准新需求，也不能恢复旧 API、旧字段、旧数据库或历史计划的契约地位。新需求只由当前治理链中的规范性需求工件及其正式批准记录确定。

## 来源评估

| 原路径 | 归档路径 | 历史用途 | 可参考内容 | 已知冲突 | 可信度 | 需要代码复核 | 替代规范 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `docs/API-SPEC.md` | `docs/90-archive/legacy-docs/API-SPEC.md` | 记录旧接口草案和实现口径 | 识别历史业务动作、接口命名和待清理依赖 | 旧 API、字段和对象粒度不得进入新契约；当前需求采用书目与实体副本分离，并排除旧拼音 API | 低；仅作历史线索 | 是；仅核对遗留依赖，不据此补需求 | [`SYSTEM-REQUIREMENTS.md`](../SYSTEM-REQUIREMENTS.md)、[`FUNCTIONAL-REQUIREMENTS.md`](../requirements/FUNCTIONAL-REQUIREMENTS.md)、[`BUSINESS-RULES.md`](../requirements/BUSINESS-RULES.md) |
| `docs/ARCHITECTURE.md` | `docs/90-archive/legacy-docs/ARCHITECTURE.md` | 描述旧实现结构和技术选型 | 识别已有工程边界、技术债和可复用候选 | 历史结构不是当前总体设计；当前尚无总体设计结论 | 低；结构事实须以当前代码复核 | 是 | [`REQUIREMENTS-STAGE-DESIGN.md`](../REQUIREMENTS-STAGE-DESIGN.md)、[`ENGINEERING-LIFECYCLE.md`](../../00-governance/ENGINEERING-LIFECYCLE.md)、[`02-overall-design/README.md`](../../02-overall-design/README.md) |
| `docs/DATA-MODEL.md` | `docs/90-archive/legacy-docs/DATA-MODEL.md` | 记录旧数据库和字段模型 | 识别历史概念、数据风险和待废弃字段 | 新系统不兼容旧字段、旧数据库或兼容迁移；历史表结构不得成为信息需求 | 低；概念与物理结构均需重新判断 | 是 | [`GLOSSARY.md`](../requirements/GLOSSARY.md)、[`INFORMATION-REQUIREMENTS.md`](../requirements/INFORMATION-REQUIREMENTS.md)、[`BUSINESS-RULES.md`](../requirements/BUSINESS-RULES.md) |
| `docs/test/` | `docs/90-archive/legacy-docs/test/` | 保存旧测试计划、用例和边界说明 | 识别曾观察的行为和遗漏场景 | 历史测试不是当前验收条件、验收用例或执行证据 | 中低；只可信为历史记录 | 是；需核对测试针对的旧实现 | [`ACCEPTANCE-STRATEGY.md`](../acceptance/ACCEPTANCE-STRATEGY.md)、[`ACCEPTANCE-CASES.md`](../acceptance/ACCEPTANCE-CASES.md)、[`ACCEPTANCE-REPORTS.md`](../acceptance/ACCEPTANCE-REPORTS.md) |
| `docs/arc/` | `docs/90-archive/legacy-docs/arc/` | 保存更早的 API、实体、前端模块和迁移资料 | 识别旧拼音命名、外部依赖及迁移遗留 | 与“不兼容旧拼音 API、旧字段和旧数据库”的现行范围直接冲突 | 很低；仅作风险线索 | 是；用于确认遗留引用和清理范围 | [`VISION-AND-SCOPE.md`](../requirements/VISION-AND-SCOPE.md)、[`SYSTEM-REQUIREMENTS.md`](../SYSTEM-REQUIREMENTS.md)、[`CODE-REUSE-INVENTORY.md`](CODE-REUSE-INVENTORY.md) |
| `docs/superpowers/reports/` | `docs/90-archive/audit-reports/superpowers-reports/` | 保存既往文档和代码审计结果 | 识别历史时点的差异、检查方法和未验证项 | 审计结论可能随代码和文档变化而失效；不能替代本轮评审证据 | 中；仅对其记录时点有效 | 是；漂移项必须重新验证 | [`REQUIREMENTS-REVIEW-CHECKLIST.md`](../reviews/REQUIREMENTS-REVIEW-CHECKLIST.md)、[`TRACEABILITY-MATRIX.md`](../requirements/TRACEABILITY-MATRIX.md) |
| `docs/superpowers/specs/` | `docs/90-archive/historical-plans/superpowers-specs/` | 保存已关闭阶段的历史设计规格 | 识别曾采用的假设、范围和实现风险 | 已关闭规格不是当前需求或设计输入，且可能含旧对象、字段和技术方案 | 低；只作决策考古 | 是；仅复核遗留实现痕迹 | [`VISION-AND-SCOPE.md`](../requirements/VISION-AND-SCOPE.md)、[`FUNCTIONAL-REQUIREMENTS.md`](../requirements/FUNCTIONAL-REQUIREMENTS.md)、[`OPEN-QUESTIONS.md`](../requirements/OPEN-QUESTIONS.md) |
| `docs/superpowers/plans/` | `docs/90-archive/historical-plans/superpowers-plans/` | 保存已关闭阶段的历史执行计划 | 识别过去的实施顺序和未决风险 | 历史计划不批准需求、不证明实施完成，也不构成当前阶段门禁证据 | 低；计划不证明结果 | 是；如需判断完成情况必须核对当前代码和证据 | [`ENGINEERING-LIFECYCLE.md`](../../00-governance/ENGINEERING-LIFECYCLE.md)、[`REQUIREMENTS-REVIEW-CHECKLIST.md`](../reviews/REQUIREMENTS-REVIEW-CHECKLIST.md) |

## 固定结论

- 八类资料的统一分类为 `Historical Reference`。
- 它们可用于提出复核问题、识别冲突和定位遗留风险，不能产生或批准新需求。
- 旧内容与当前规范冲突时，以经治理链正式批准的当前规范为准；冲突本身保留为证据。
- 任何代码复核结果也只说明现状，不授予历史文档规范性地位。
