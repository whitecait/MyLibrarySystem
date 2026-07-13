# MyLibrarySystem

MyLibrarySystem 是一个面向单一图书馆的工程学习项目。项目以完整系统为验收对象，按照 V 模型依次开展需求分析、总体设计、详细设计和模块设计，并分别对应验收测试、系统测试、集成测试和单元测试。

## 当前工程状态

| 项目 | 状态 |
| --- | --- |
| 当前阶段 | 需求分析 |
| 工程文档唯一入口 | [docs/README.md](docs/README.md) |
| 需求基线 | `Not established` |
| 验收执行 | 尚未执行 |
| 当前代码地位 | 代码复用候选：可直接复用、重构复用或弃用重写；不是新需求来源 |

[需求分析阶段工程化设计](docs/01-requirements/REQUIREMENTS-STAGE-DESIGN.md) 的 `v1.0 Approved` 只表示阶段组织方案已经批准，不表示最终需求基线 `v1.0` 已建立。旧契约、旧计划和旧审计仅在 [历史资料归档](docs/90-archive/README.md) 中作为非规范性历史参考。

## 项目入口

- [后端项目与开发说明](library-server/README.md)
- [前端项目与开发说明](library-frontend/README.md)
- [工程文档唯一入口](docs/README.md)

## 参与需求评审

1. 从[系统需求主规格](docs/01-requirements/SYSTEM-REQUIREMENTS.md)进入当前需求工件。
2. 在 [Open 问题登记册](docs/01-requirements/requirements/OPEN-QUESTIONS.md)逐项确认尚未决定的业务与质量边界。
3. 使用[需求评审清单](docs/01-requirements/reviews/REQUIREMENTS-REVIEW-CHECKLIST.md)记录可复现证据与评审发现。
4. 只有[需求基线记录](docs/01-requirements/reviews/REQUIREMENTS-BASELINE-RECORD.md)完成正式批准后，需求工件才可成为下一阶段输入；变更按[变更控制](docs/00-governance/CHANGE-CONTROL.md)处理。
