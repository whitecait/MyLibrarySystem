# MyLibrarySystem 系统需求主规格

| 字段 | 值 |
| --- | --- |
| 文档编号 | `MLS-DOC-RA-0003` |
| 文档状态 | `Draft` |
| 文档版本 | `v0.1` |
| 编制日期 | `2026-07-13` |
| 适用阶段 | 需求分析 |
| 验证对应 | 系统验收测试 |
| 权威性质 | 需求目标、范围、约束和专题工件的受控汇总；当前不是已批准基线 |

## 1. 状态声明

- 需求基线状态：Not established
- 验收执行状态：尚未执行
- 当前阶段状态：需求编制中，G5 未通过

[阶段设计](REQUIREMENTS-STAGE-DESIGN.md)的 `v1.0` 是已批准的需求分析组织与工作基线，不是最终需求基线 `v1.0`。Git 提交、链接建立或静态检查通过均不能替代正式评审和批准记录。

## 2. 业务目标

**ID:** `MLS-OBJ-0001`

为单一图书馆建立统一、可审计的核心业务系统。

**ID:** `MLS-OBJ-0002`

支持访客、读者、馆员和系统管理员完成首版核心流通目标。

**ID:** `MLS-OBJ-0003`

通过自动判定、人工边界处理和完整审计提高业务处理的一致性与可追责性。

**ID:** `MLS-OBJ-0004`

在不连接任何真实支付网络的前提下演示罚款支付完整闭环。

## 3. 范围摘要

系统服务单一图书馆。首版范围包括账户与权限、读者管理、书目与实体副本、公开检索、借阅、归还、自助续借、逾期罚款、模拟支付和操作审计。

首版明确排除多馆点或多租户、跨馆调拨、电子资源、预约排队、业务通知、公告、留言、通用上传、统计仪表盘、遗失损坏赔偿、真实支付或官方沙箱、支付之外的外部系统，以及旧接口、旧字段、旧数据库的兼容迁移。完整边界以[愿景与范围](requirements/VISION-AND-SCOPE.md)为专题入口。

## 4. 项目约束

**ID:** `MLS-CON-0001`

只允许完全本地支付模拟器，不连接真实平台、官方沙箱或真实支付网络。

**ID:** `MLS-CON-0002`

不保存真实商户号、真实密钥、真实付款凭据或真实付款信息。

**ID:** `MLS-CON-0003`

首版除本地支付模拟器外不集成其他外部系统。

**ID:** `MLS-CON-0004`

不兼容旧拼音 API、旧字段和旧数据库，不提供兼容迁移。

**ID:** `MLS-CON-0005`

旧文档、旧测试和现有代码均非新需求的规范性来源。

## 5. 专题工件

| 专题 | 当前入口 | 状态说明 |
| --- | --- | --- |
| 愿景、目标与范围 | [VISION-AND-SCOPE.md](requirements/VISION-AND-SCOPE.md) | Draft |
| 参与者与系统语境 | [STAKEHOLDERS-AND-CONTEXT.md](requirements/STAKEHOLDERS-AND-CONTEXT.md) | Draft |
| 术语 | [GLOSSARY.md](requirements/GLOSSARY.md) | Draft |
| 未批准问题 | [OPEN-QUESTIONS.md](requirements/OPEN-QUESTIONS.md) | 15 项均为 Open |
| 业务流程、用例、功能需求、业务规则和概念信息 | `requirements/` 中对应专题工件 | Not created |
| 非功能需求 | `requirements/NONFUNCTIONAL-REQUIREMENTS.md` | Not created |
| 验收定义 | `acceptance/` 中对应工件 | Not created |
| 评审与基线 | `reviews/` 中对应工件 | Not created |

本主规格只维护导航与汇总，不复制后续功能需求、业务规则、信息需求、质量要求或验收用例全文。

## 6. 来源与决策依据

- [MLS-DOC-RA-0001 需求分析阶段工程化设计](REQUIREMENTS-STAGE-DESIGN.md)
- [MLS-DEC-0001 至 MLS-DEC-0006](../00-governance/DECISION-LOG.md)
- [文档权威与冲突处理](../00-governance/DOCUMENT-AUTHORITY.md)
- [文档与需求变更控制](../00-governance/CHANGE-CONTROL.md)
