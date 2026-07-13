# MyLibrarySystem Requirements Documentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按已批准的需求分析阶段工程化设计，建立可版本控制、可追踪、可评审的 V 模型需求文档权威链，同时将旧文档降级为仅供参考的历史证据。

**Architecture:** 文档体系采用“受控主规格 + 专题登记册”，由 `docs/README.md` 经治理文件指向需求主规格、专题需求、验收定义、证据和评审记录。需求分析仅描述系统级业务与质量边界，不预设 API、数据库表、框架、类或部署拓扑；旧文档和现有代码只作为证据，不反向定义需求。

**Tech Stack:** Markdown、Git、PowerShell 7、ripgrep (`rg`)、CodeGraph CLI（仅在 `.codegraph/` 存在且需要理解代码时使用）。

## Global Constraints

- 本计划消费当前工作树中的 `docs/01-requirements/REQUIREMENTS-STAGE-DESIGN.md`：文档状态为 `Approved`，文档版本为 `v1.0`，文档编号为 `MLS-DOC-RA-0001`。
- `REQUIREMENTS-STAGE-DESIGN.md v1.0` 是“需求分析阶段组织与工作方式”的批准版本，不是最终“需求基线 `v1.0`”；最终需求基线只有在 G5 通过后才能建立。
- 当前执行起点为 Git `HEAD ec6b7234256e75f6742dd8c670486aba4bba2c89`；两个子项目 README 已提交，阶段设计的 `Approved/v1.0` 修改仍在工作树中。
- 根 `README.md` 当前存在但未跟踪；当前 `.gitignore` 的 `*` 与 `/*` 规则仍忽略根 README 和 `docs/`。
- 验收对象是完整系统：浏览器前端、后端、新数据库和完全本地的支付模拟器。
- 系统服务单一图书馆；首版角色为访客、读者、馆员、系统管理员；本地支付模拟器是外部参与者但不是外部网络服务。
- 首版范围为账户与权限、读者管理、书目与实体副本、公开检索、借阅、归还、自助续借、逾期罚款、模拟支付和操作审计。
- 采用全馆统一流通政策；系统自动处理明确项，边界项交馆员处理；馆员例外必须完整审计。
- 不兼容旧拼音 API、旧字段或旧数据库，不设计兼容迁移。
- 支付只能由完全本地的模拟器完成；不得接入真实支付平台、官方沙箱或真实支付网络，不得产生真实资金交易，不得保存真实商户凭据或付款信息。
- 不得修改任何产品代码、构建脚本、数据库配置或测试代码；本计划仅管理 Markdown、`.gitignore` 和文档移动。
- 旧文档只能移入 `docs/90-archive/`；移动前必须把源和目标解析为绝对路径，确认均位于工作区及 `docs/` 下；禁止移动整个 `docs/` 目录。
- 所有内容编辑使用 `apply_patch`；仅对已明确列出的历史文件或目录使用经过绝对路径校验的 `Move-Item -LiteralPath`。
- 新建规范性文档初始状态为 `Draft`、版本为 `v0.1`；提交不等于批准。只有评审记录明确批准后，状态才可改为 `Approved`。
- 未确认的业务数值和质量阈值必须写入 `MLS-OQ-0001` 至 `MLS-OQ-0015`，不得使用占位表达、猜测默认值或虚构验收结果。
- `ACCEPTANCE-REPORTS.md` 初始只能声明“尚未执行”，不得将计划、旧测试结果或静态检查冒充验收证据。
- 每个任务结束前必须检查暂存区只包含该任务的文件；若不能保证单任务提交范围，停止提交并报告，不得混入用户或其他代理的修改。

---

## File Responsibility Map

### 治理、入口与归档

| 文件 | 责任 | 首次建立任务 |
| --- | --- | --- |
| `.gitignore` | 继续采用根目录白名单，同时允许根 README 与完整 `docs/` 权威链被跟踪 | Task 1 |
| `README.md` | 根项目入口；Task 1 建立过渡入口，Task 8 收敛为最终权威导航 | Task 1 |
| `docs/README.md` | 工程文档唯一入口，展示 V 模型阶段、当前状态和权威链 | Task 3 |
| `docs/00-governance/ENGINEERING-LIFECYCLE.md` | 定义需求分析、总体设计、详细设计、模块设计及其右侧测试映射 | Task 3 |
| `docs/00-governance/DOCUMENT-AUTHORITY.md` | 定义规范性、证据性、历史性文档的优先级和冲突处理 | Task 3 |
| `docs/00-governance/CHANGE-CONTROL.md` | 定义状态、版本、变更申请、影响分析和重新评审规则 | Task 3 |
| `docs/00-governance/DECISION-LOG.md` | 维护 `MLS-DEC-NNNN` 决策记录；首批登记已批准的组织与范围决策 | Task 3 |
| `docs/90-archive/README.md` | 历史资料索引和来源清单；不承担当前契约职责 | Task 2 |
| `docs/90-archive/legacy-docs/root/README-2026-07-13.md` | 保存返工前未跟踪根 README 的原始内容和来源 | Task 1 |

### 需求分析主规格与专题登记册

| 文件 | 责任 | 首次建立任务 |
| --- | --- | --- |
| `docs/01-requirements/README.md` | 需求分析入口、阅读顺序、工件状态与 G0–G5 概览 | Task 4 |
| `docs/01-requirements/REQUIREMENTS-STAGE-DESIGN.md` | 已批准的需求分析组织和工作基线；不等同最终需求基线 | Task 1 仅提交当前批准修改 |
| `docs/01-requirements/SYSTEM-REQUIREMENTS.md` | 需求主入口，汇总目标、范围、约束和专题链接，不复制专题事实 | Task 4 |
| `docs/01-requirements/requirements/VISION-AND-SCOPE.md` | 单一图书馆、核心流通版、范围内外和业务目标 | Task 4 |
| `docs/01-requirements/requirements/STAKEHOLDERS-AND-CONTEXT.md` | 访客、读者、馆员、系统管理员、本地支付模拟器和系统边界 | Task 4 |
| `docs/01-requirements/requirements/GLOSSARY.md` | 当前术语及废弃旧拼音 API、旧字段、旧数据库用语 | Task 4 |
| `docs/01-requirements/requirements/OPEN-QUESTIONS.md` | 维护 `MLS-OQ-0001` 至 `MLS-OQ-0015`，记录责任、状态和关闭依据 | Task 4 |
| `docs/01-requirements/requirements/BUSINESS-PROCESSES.md` | 注册、检索、馆藏、借还续、罚款、模拟支付、例外与审计流程 | Task 5 |
| `docs/01-requirements/requirements/USE-CASES.md` | 系统级用例的参与者、前后置条件、主流程、替代和异常流程 | Task 5 |
| `docs/01-requirements/requirements/FUNCTIONAL-REQUIREMENTS.md` | 原子化 `MLS-FR-NNNN` 规范性陈述及验收条件引用 | Task 5 |
| `docs/01-requirements/requirements/BUSINESS-RULES.md` | 独立 `MLS-BR-NNNN` 业务规则，不隐藏在用例叙述中 | Task 5 |
| `docs/01-requirements/requirements/INFORMATION-REQUIREMENTS.md` | `MLS-IR-NNNN` 概念信息及生命周期，不定义物理表 | Task 5 |
| `docs/01-requirements/requirements/NONFUNCTIONAL-REQUIREMENTS.md` | 可测量 `MLS-NFR-类别-NNNN`；未定阈值只引用 Open 问题 | Task 6 |
| `docs/01-requirements/requirements/TRACEABILITY-MATRIX.md` | 目标、用例、需求、验收条件和验收用例的双向追踪 | Task 6 |

### 验收、证据、评审与后续阶段入口

| 文件 | 责任 | 首次建立任务 |
| --- | --- | --- |
| `docs/01-requirements/acceptance/ACCEPTANCE-STRATEGY.md` | 完整系统验收范围、环境原则、数据原则和门禁 | Task 6 |
| `docs/01-requirements/acceptance/ACCEPTANCE-CASES.md` | `MLS-AT-NNNN` 可观察、可重复、可判定的验收场景 | Task 6 |
| `docs/01-requirements/acceptance/ACCEPTANCE-REPORTS.md` | 只记录实际执行结果；初始明确“尚未执行” | Task 6 |
| `docs/01-requirements/evidence/LEGACY-DOCUMENT-ASSESSMENT.md` | 旧文档来源、冲突、可信度和可参考内容 | Task 7 |
| `docs/01-requirements/evidence/CODE-REUSE-INVENTORY.md` | 现有资产直接复用、重构复用、弃用重写的证据性判断 | Task 7 |
| `docs/01-requirements/reviews/REQUIREMENTS-REVIEW-CHECKLIST.md` | 完整性、一致性、可验证性、追踪和设计泄漏检查 | Task 7 |
| `docs/01-requirements/reviews/REQUIREMENTS-BASELINE-RECORD.md` | G0–G5 结论、批准责任和最终需求基线状态 | Task 7，Task 8 更新评审结果 |
| `docs/02-overall-design/README.md` | 总体设计与系统测试入口；当前不写设计结论 | Task 7 |
| `docs/03-detailed-design/README.md` | 详细设计与集成测试入口；当前不写设计结论 | Task 7 |
| `docs/04-module-design/README.md` | 模块设计与单元测试入口；当前不写设计结论 | Task 7 |
| `library-server/README.md` | 后端子项目入口，指向新权威链并将当前代码定位为复用候选 | Task 8 |
| `library-frontend/README.md` | 前端子项目入口，指向新权威链并将当前代码定位为复用候选 | Task 8 |

## Shared Document Contracts

除历史原文外，新文档统一以以下状态头开始；`文档编号` 按文件责任映射中指定的编号写入，初始状态不得自动升级：

```markdown
| 属性 | 值 |
| --- | --- |
| 文档编号 | `MLS-DOC-RA-0002` |
| 文档状态 | `Draft` |
| 文档版本 | `v0.1` |
| 编制日期 | `2026-07-13` |
| 适用阶段 | `需求分析` |
| 验证对应 | `系统验收测试` |
| 权威性质 | `规范性`、`治理性`、`证据性` 或 `历史索引` 中的一个 |
```

历史文件在原始标题之前统一插入以下头部，并由归档清单记录精确来源和替代入口：

```markdown
> **Archive Status:** Historical Reference
> **Original Path:** `docs/API-SPEC.md`
> **Archived On:** `2026-07-13`
> **Historical Purpose:** 返工前的实现、计划、审计或契约记录
> **Superseded By:** `docs/README.md`
> **Authority:** 非规范性；不得作为当前需求、接口、数据或验收契约
```

所有原子化需求使用以下字段；没有批准结论时必须引用对应 `MLS-OQ-NNNN`，不得填入猜测值：

```markdown
### 公开检索馆藏

**ID:** `MLS-FR-0001`
**规范性陈述:** 系统应允许访客公开检索馆藏。
**业务理由:** 使未注册用户能够确认图书馆是否拥有所需资料。
**来源:** `MLS-DOC-RA-0001` 的已批准边界
**参与者:** `MLS-ACT-0001`
**业务域标签:** 馆藏
**优先级:** `Must`
**状态:** `Draft`
**前置条件:** 无
**依赖:** `MLS-BR-0001`、`MLS-IR-0003`、`MLS-IR-0004`
**验收条件:** `MLS-AC-0001`
**风险:** 未识别
**版本:** `v0.1`
**变更记录:** `2026-07-13` 初稿
```

追踪矩阵固定使用以下列；后续设计和测试尚未建立时统一写 `Not created`：

```text
Objective ID | Use Case ID | Requirement ID | Requirement Type | Acceptance Condition ID | Acceptance Test ID | Source Document | Requirement Status | Overall Design ID | System Test ID | Detailed Design ID | Integration Test ID | Module Design ID | Unit Test ID
```

---

### Task 1: 建立 Git 文档权威链并提交阶段设计批准状态

**Files:**
- Modify: `.gitignore`
- Move: `README.md` → `docs/90-archive/legacy-docs/root/README-2026-07-13.md`
- Create: `README.md`
- Create: `docs/90-archive/legacy-docs/root/README-2026-07-13.md`（移动后仅追加历史头）
- Stage without editing: `docs/01-requirements/REQUIREMENTS-STAGE-DESIGN.md`

**Interfaces:**
- Consumes: 当前 `.gitignore` 的白名单模式、未跟踪根 README 的现有内容、工作树中用户已批准的 `MLS-DOC-RA-0001 Approved/v1.0`。
- Produces: 可跟踪根 README 与 `docs/**` 的 Git 规则、返工前根 README 的不可变历史快照、指向新文档入口的过渡根 README、已提交的阶段设计批准证据。

- [ ] **Step 1: 运行缺失性检查，证明当前权威链尚不可跟踪（2–5 分钟）**

Run:

```powershell
git check-ignore -v README.md docs/README.md docs/01-requirements/REQUIREMENTS-STAGE-DESIGN.md
git ls-files --error-unmatch README.md
git diff --exit-code -- docs/01-requirements/REQUIREMENTS-STAGE-DESIGN.md
```

Expected:

- `git check-ignore` 为根 README 和 `docs/README.md` 指向 `.gitignore` 的 `*` 或 `/*` 规则。
- `git ls-files --error-unmatch README.md` 退出码为 1，并报告根 README 不在索引中。
- `git diff --exit-code` 退出码为 1，差异只显示阶段设计从 `In Review/0.9` 变为 `Approved/v1.0` 及用户的格式化结果。

- [ ] **Step 2: 安全归档当前根 README，不移动任何其他文件（2–5 分钟）**

Run:

```powershell
$workspace = (Resolve-Path '.').Path
$docs = (Resolve-Path 'docs').Path
$source = (Resolve-Path 'README.md').Path
$target = [IO.Path]::GetFullPath((Join-Path $workspace 'docs/90-archive/legacy-docs/root/README-2026-07-13.md'))
$insideWorkspace = $source.StartsWith($workspace + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)
$targetInsideDocs = $target.StartsWith($docs + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)
if (-not $insideWorkspace -or -not $targetInsideDocs) { throw 'README archive path escaped workspace/docs' }
if (Test-Path -LiteralPath $target) { throw "Archive target already exists: $target" }
New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
Move-Item -LiteralPath $source -Destination $target
```

Expected: 只移动根 `README.md` 到明确的归档目标；`docs/01-requirements/` 和其他旧文档保持原位。

- [ ] **Step 3: 使用 `apply_patch` 开放 Git 白名单、建立过渡入口并为快照加历史头（2–5 分钟）**

Apply this patch structure exactly; the archive文件中“原有 README 全文”是 Step 2 移动后的原文，必须原样保留，仅在首行前插入给定头部：

```diff
*** Begin Patch
*** Update File: .gitignore
@@
-# Only library-frontend/ and library-server/ are tracked.
+# Root README, docs, library-frontend, and library-server are tracked.
@@
 !.gitignore
+!README.md
+!docs/
+!docs/**
 !library-frontend/
 !library-server/
*** Update File: docs/90-archive/legacy-docs/root/README-2026-07-13.md
@@
+> **Archive Status:** Historical Reference
+> **Original Path:** `README.md`
+> **Archived On:** `2026-07-13`
+> **Historical Purpose:** 返工前的项目状态、API 与阶段说明
+> **Superseded By:** `docs/README.md`
+> **Authority:** 非规范性；不得作为当前需求、接口、数据或验收契约
+
 # MyLibrarySystem
*** Add File: README.md
+# MyLibrarySystem
+
+本项目正在按照 V 模型从需求分析阶段重新建立工程基线。现有代码仅作为复用候选，旧文档仅作为历史证据。
+
+当前已批准的工作方式见 [需求分析阶段工程化设计](docs/01-requirements/REQUIREMENTS-STAGE-DESIGN.md)。工程文档唯一入口将在 [docs/README.md](docs/README.md) 建立。
+
+`REQUIREMENTS-STAGE-DESIGN.md v1.0` 只表示需求分析阶段组织方案已批准，不表示最终需求基线 `v1.0` 已建立，也不表示验收测试已经执行。
*** End Patch
```

Expected: `.gitignore` 继续忽略无关根目录内容，但显式重新包含 `README.md`、`docs/` 与 `docs/**`；过渡 README 不重复旧实现事实。

- [ ] **Step 4: 验证跟踪规则、快照和阶段设计状态（2–5 分钟）**

Run:

```powershell
git check-ignore -q README.md
if ($LASTEXITCODE -eq 0) { throw 'README.md is still ignored' }
git check-ignore -q docs/README.md
if ($LASTEXITCODE -eq 0) { throw 'docs/README.md is still ignored' }
rg -n '^\| 文档状态 \| `Approved`|^\| 文档版本 \| `v1\.0`|尚不表示需求基线|初步的需求分析任务' docs/01-requirements/REQUIREMENTS-STAGE-DESIGN.md README.md
rg -n '^> \*\*Archive Status:\*\* Historical Reference|^> \*\*Original Path:\*\* `README\.md`' docs/90-archive/legacy-docs/root/README-2026-07-13.md
git diff --check
```

Expected:

- 两个 `git check-ignore -q` 均返回 1，脚本不抛错。
- `rg` 找到阶段设计的 `Approved`、`v1.0`，以及过渡 README 对“阶段设计版本不等于需求基线”的声明。
- 历史快照包含正确状态和来源头；`git diff --check` 无输出、退出码 0。

- [ ] **Step 5: 只提交本任务文件（2–5 分钟）**

Run:

```powershell
git add -- .gitignore README.md docs/90-archive/legacy-docs/root/README-2026-07-13.md docs/01-requirements/REQUIREMENTS-STAGE-DESIGN.md
$cached = @(git diff --cached --name-only)
$expected = @('.gitignore','README.md','docs/01-requirements/REQUIREMENTS-STAGE-DESIGN.md','docs/90-archive/legacy-docs/root/README-2026-07-13.md')
if (@(Compare-Object ($cached | Sort-Object) ($expected | Sort-Object)).Count -ne 0) { throw "Unexpected staged files: $($cached -join ', ')" }
git diff --cached --check
git commit -m "docs: establish requirements documentation authority"
```

Expected: 独立提交成功；提交只包含四个列出的路径。阶段设计 `Approved/v1.0` 被提交，但最终需求基线仍未建立。

---

### Task 2: 按明确路径安全归档全部旧 `docs` 资料

**Files:**
- Move: `docs/API-SPEC.md` → `docs/90-archive/legacy-docs/API-SPEC.md`
- Move: `docs/ARCHITECTURE.md` → `docs/90-archive/legacy-docs/ARCHITECTURE.md`
- Move: `docs/DATA-MODEL.md` → `docs/90-archive/legacy-docs/DATA-MODEL.md`
- Move: `docs/test/` → `docs/90-archive/legacy-docs/test/`
- Move: `docs/arc/` → `docs/90-archive/legacy-docs/arc/`
- Move: `docs/superpowers/reports/` → `docs/90-archive/audit-reports/superpowers-reports/`
- Move: `docs/superpowers/specs/` → `docs/90-archive/historical-plans/superpowers-specs/`
- Move: `docs/superpowers/plans/` → `docs/90-archive/historical-plans/superpowers-plans/`
- Create: `docs/90-archive/README.md`
- Modify: every moved Markdown file, only to prepend the standard historical header

**Interfaces:**
- Consumes: Task 1 的可跟踪 `docs/**` 规则和历史分类原则。
- Produces: 来源可追溯、内容保真的旧资料归档；Task 7 的旧文档评估以此索引为输入。

- [ ] **Step 1: 运行缺失性检查并保存移动前来源清单（2–5 分钟）**

Run:

```powershell
$sources = @(
  'docs/API-SPEC.md','docs/ARCHITECTURE.md','docs/DATA-MODEL.md','docs/test','docs/arc',
  'docs/superpowers/reports','docs/superpowers/specs','docs/superpowers/plans'
)
$missing = @($sources | Where-Object { -not (Test-Path -LiteralPath $_) })
if ($missing.Count -ne 0) { throw "Missing archive sources: $($missing -join ', ')" }
Get-ChildItem -LiteralPath docs/API-SPEC.md,docs/ARCHITECTURE.md,docs/DATA-MODEL.md -File |
  Select-Object FullName,Length
Get-ChildItem -LiteralPath docs/test,docs/arc,docs/superpowers/reports,docs/superpowers/specs,docs/superpowers/plans -Recurse -File |
  Sort-Object FullName | Select-Object FullName,Length
Test-Path docs/90-archive/README.md
```

Expected: 八个明确源全部存在；清单包含每个旧文件；最后输出 `False`，证明归档索引尚未建立。

- [ ] **Step 2: 在任何移动前解析并验证全部绝对路径（2–5 分钟）**

Run:

```powershell
$workspace = (Resolve-Path '.').Path
$docs = (Resolve-Path 'docs').Path
$archiveRoot = [IO.Path]::GetFullPath((Join-Path $workspace 'docs/90-archive'))
$moves = [ordered]@{
  'docs/API-SPEC.md' = 'docs/90-archive/legacy-docs/API-SPEC.md'
  'docs/ARCHITECTURE.md' = 'docs/90-archive/legacy-docs/ARCHITECTURE.md'
  'docs/DATA-MODEL.md' = 'docs/90-archive/legacy-docs/DATA-MODEL.md'
  'docs/test' = 'docs/90-archive/legacy-docs/test'
  'docs/arc' = 'docs/90-archive/legacy-docs/arc'
  'docs/superpowers/reports' = 'docs/90-archive/audit-reports/superpowers-reports'
  'docs/superpowers/specs' = 'docs/90-archive/historical-plans/superpowers-specs'
  'docs/superpowers/plans' = 'docs/90-archive/historical-plans/superpowers-plans'
}
$resolved = foreach ($pair in $moves.GetEnumerator()) {
  $source = (Resolve-Path -LiteralPath $pair.Key).Path
  $target = [IO.Path]::GetFullPath((Join-Path $workspace $pair.Value))
  $sourceInsideDocs = $source.StartsWith($docs + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)
  $targetInsideArchive = $target.StartsWith($archiveRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)
  $forbidden = $source -eq $docs -or $source -like '*\docs\01-requirements*'
  if (-not $sourceInsideDocs -or -not $targetInsideArchive -or $forbidden) { throw "Unsafe move: $source -> $target" }
  if (Test-Path -LiteralPath $target) { throw "Archive target already exists: $target" }
  [pscustomobject]@{ Source = $source; Target = $target; Original = $pair.Key; RelativeTarget = $pair.Value }
}
$resolved | Format-Table Original,RelativeTarget
```

Expected: 恰好输出八行映射；所有目标位于 `docs/90-archive/`；不会包含 `docs` 根、阶段设计或本实施计划。

- [ ] **Step 3: 创建归档索引，再按已验证映射逐项移动（2–5 分钟）**

先使用 `apply_patch` 创建 `docs/90-archive/README.md`。文档编号为 `MLS-DOC-ARC-0001`，状态 `Draft`、版本 `v0.1`、权威性质“历史索引”；正文必须包含以下八行来源映射、归档日期、历史用途、替代入口 `docs/README.md`，并声明所有归档均非规范性：

```text
docs/API-SPEC.md -> docs/90-archive/legacy-docs/API-SPEC.md
docs/ARCHITECTURE.md -> docs/90-archive/legacy-docs/ARCHITECTURE.md
docs/DATA-MODEL.md -> docs/90-archive/legacy-docs/DATA-MODEL.md
docs/test/ -> docs/90-archive/legacy-docs/test/
docs/arc/ -> docs/90-archive/legacy-docs/arc/
docs/superpowers/reports/ -> docs/90-archive/audit-reports/superpowers-reports/
docs/superpowers/specs/ -> docs/90-archive/historical-plans/superpowers-specs/
docs/superpowers/plans/ -> docs/90-archive/historical-plans/superpowers-plans/
```

然后在同一个 PowerShell 会话中重新执行 Step 2 生成 `$resolved`，并运行：

```powershell
foreach ($item in $resolved) {
  New-Item -ItemType Directory -Force -Path (Split-Path $item.Target) | Out-Null
  Move-Item -LiteralPath $item.Source -Destination $item.Target
}
```

Expected: 只移动八个明确源；新规格、实施计划和 `docs/90-archive/README.md` 保持原位。

- [ ] **Step 4: 使用 `apply_patch` 给每个已移动 Markdown 文件添加准确历史头（2–5 分钟/每组）**

按 Shared Document Contracts 的历史头逐文件插入，`Original Path` 必须从 Task 2 映射反推，不能写归档后的路径。用途分类固定如下：

- `API-SPEC.md`、`ARCHITECTURE.md`、`DATA-MODEL.md`、`test/**`、`arc/**`：返工前契约或实现记录。
- `audit-reports/**`：返工前审计证据。
- `historical-plans/superpowers-specs/**`：已关闭的历史设计规格。
- `historical-plans/superpowers-plans/**`：已关闭的历史执行计划。

每次 `apply_patch` 只在原文首行前插入六行历史头和一个空行，不改正文、文件名或原始标题。

Expected: `docs/90-archive/README.md` 之外的每个归档 Markdown 文件第一段均为 `Archive Status / Original Path / Archived On / Historical Purpose / Superseded By / Authority`。

- [ ] **Step 5: 验证来源已消失、目标已建立、历史头和清单完整（2–5 分钟）**

Run:

```powershell
$old = @('docs/API-SPEC.md','docs/ARCHITECTURE.md','docs/DATA-MODEL.md','docs/test','docs/arc','docs/superpowers/reports','docs/superpowers/specs','docs/superpowers/plans')
$remaining = @($old | Where-Object { Test-Path -LiteralPath $_ })
if ($remaining.Count -ne 0) { throw "Archive sources still exist: $($remaining -join ', ')" }
$archivedMarkdown = @(Get-ChildItem docs/90-archive -Recurse -File -Filter '*.md' | Where-Object { $_.FullName -ne (Resolve-Path docs/90-archive/README.md).Path })
$withoutHeader = @($archivedMarkdown | Where-Object { (Get-Content -LiteralPath $_.FullName -TotalCount 1) -ne '> **Archive Status:** Historical Reference  ' })
if ($withoutHeader.Count -ne 0) { throw "Missing historical header: $($withoutHeader.FullName -join ', ')" }
rg -n 'docs/API-SPEC\.md ->|docs/test/ ->|docs/arc/ ->|docs/superpowers/reports/ ->|docs/superpowers/specs/ ->|docs/superpowers/plans/ ->' docs/90-archive/README.md
git diff --check
```

Expected: 没有旧源残留；所有归档 Markdown 具有历史头；索引命中八条来源映射；`git diff --check` 无输出。

- [ ] **Step 6: 只提交归档任务（2–5 分钟）**

Run:

```powershell
git add -A -- docs/API-SPEC.md docs/ARCHITECTURE.md docs/DATA-MODEL.md docs/test docs/arc docs/superpowers docs/90-archive
git diff --cached --name-status
git diff --cached --check
git commit -m "docs: archive legacy documentation"
```

Expected: 暂存区只出现上述旧源的删除/重命名、对应 `docs/90-archive/**` 文件和归档索引；不得出现 `.gitignore`、根 README、阶段设计、计划文件或产品代码。

---

### Task 3: 建立 `docs` 唯一入口与四份治理文件

**Files:**
- Create: `docs/README.md`
- Create: `docs/00-governance/ENGINEERING-LIFECYCLE.md`
- Create: `docs/00-governance/DOCUMENT-AUTHORITY.md`
- Create: `docs/00-governance/CHANGE-CONTROL.md`
- Create: `docs/00-governance/DECISION-LOG.md`

**Interfaces:**
- Consumes: Task 1 的可跟踪权威链、Task 2 的归档入口、`MLS-DOC-RA-0001` 的四阶段 V 模型和 G0–G5 规则。
- Produces: Task 4–8 共同依赖的唯一文档入口、状态/版本/权威/变更规则和 `MLS-DEC-NNNN` 决策来源。

- [ ] **Step 1: 运行缺失性检查，证明治理入口尚未建立（2–5 分钟）**

Run:

```powershell
$required = @(
  'docs/README.md',
  'docs/00-governance/ENGINEERING-LIFECYCLE.md',
  'docs/00-governance/DOCUMENT-AUTHORITY.md',
  'docs/00-governance/CHANGE-CONTROL.md',
  'docs/00-governance/DECISION-LOG.md'
)
$existing = @($required | Where-Object { Test-Path -LiteralPath $_ })
if ($existing.Count -ne 0) { throw "Governance files already exist: $($existing -join ', ')" }
```

Expected: 命令退出码 0，表示五个目标文件均缺失，因此 G0 尚未满足。

- [ ] **Step 2: 使用一个 `apply_patch` 创建唯一入口和 V 模型生命周期（2–5 分钟）**

创建以下精确结构：

- `docs/README.md`，文档编号 `MLS-DOC-GOV-0001`；包含唯一入口声明、当前阶段“需求分析”、当前状态“需求工件编制中，需求基线未建立”、权威链、V 模型四阶段导航、归档入口以及“计划/测试用例/执行报告必须区分”。
- `ENGINEERING-LIFECYCLE.md`，文档编号 `MLS-DOC-GOV-0002`；必须逐行写出：`需求分析 ↔ 验收测试`、`总体设计 ↔ 系统测试`、`详细设计 ↔ 集成测试`、`模块设计 ↔ 单元测试`；定义 WP-0 至 WP-4、G0 至 G5、各阶段准入/退出和“后三阶段当前只建入口，不写结论”。

两个文件均使用 Shared Document Contracts 的状态头，状态 `Draft`、版本 `v0.1`。入口链接只使用仓库相对路径，不写本机绝对路径。

Expected: `docs/README.md` 成为唯一导航，但不会宣称任何需求或验收已批准/执行。

- [ ] **Step 3: 使用一个 `apply_patch` 创建权威、变更和决策治理文件（2–5 分钟）**

创建以下精确内容边界：

- `DOCUMENT-AUTHORITY.md`，文档编号 `MLS-DOC-GOV-0003`：权威顺序固定为 `docs/README.md → DOCUMENT-AUTHORITY.md → SYSTEM-REQUIREMENTS.md → requirements/ + acceptance/ + reviews/`；`evidence/` 与 `90-archive/` 明确为非规范性；冲突时以最后批准的规范性工件为准，并发起变更评审。
- `CHANGE-CONTROL.md`，文档编号 `MLS-DOC-GOV-0004`：状态固定为 `Draft → In Review → Approved → Changed/Retired`；ID 永不复用；变更记录必须含原因、提出人、日期、影响需求、影响验收、评审人、批准人和结论；提交不等于批准。
- `DECISION-LOG.md`，文档编号 `MLS-DOC-GOV-0005`：使用 `MLS-DEC-NNNN`；登记 `MLS-DEC-0001`“采用 V 模型四阶段”、`MLS-DEC-0002`“采用受控主规格 + 专题登记册”、`MLS-DEC-0003`“系统服务单一图书馆”、`MLS-DEC-0004`“完整系统为验收对象”、`MLS-DEC-0005`“完全本地支付模拟器且无真实资金”、`MLS-DEC-0006`“不兼容旧拼音 API、旧字段和旧数据库”。每条状态写 `Approved`，来源写 `MLS-DOC-RA-0001`，日期写 `2026-07-13`，不增加未确认业务数值。

Expected: 三个文件只定义治理和已确认决策，不泄漏总体、详细或模块设计。

- [ ] **Step 4: 精确验证入口、状态头、权威链和决策编号（2–5 分钟）**

Run:

```powershell
$files = @(
  'docs/README.md',
  'docs/00-governance/ENGINEERING-LIFECYCLE.md',
  'docs/00-governance/DOCUMENT-AUTHORITY.md',
  'docs/00-governance/CHANGE-CONTROL.md',
  'docs/00-governance/DECISION-LOG.md'
)
$missingStatus = @($files | Where-Object { -not (Select-String -LiteralPath $_ -Quiet -Pattern '^\| 文档状态 \| `Draft` \|$') })
if ($missingStatus.Count -ne 0) { throw "Missing Draft status header: $($missingStatus -join ', ')" }
rg -n '需求分析.*验收测试|总体设计.*系统测试|详细设计.*集成测试|模块设计.*单元测试' docs/00-governance/ENGINEERING-LIFECYCLE.md
rg -n 'docs/README\.md.*DOCUMENT-AUTHORITY\.md.*SYSTEM-REQUIREMENTS\.md|非规范性|提交不等于批准' docs/00-governance docs/README.md
$decisions = @(rg -o 'MLS-DEC-[0-9]{4}' docs/00-governance/DECISION-LOG.md | Sort-Object -Unique)
if ($decisions.Count -ne 6 -or $decisions[0] -ne 'MLS-DEC-0001' -or $decisions[5] -ne 'MLS-DEC-0006') { throw "Unexpected decision IDs: $($decisions -join ', ')" }
git diff --check
```

Expected: 四条 V 模型映射均命中；权威与非规范性声明命中；决策唯一集合恰好为 `MLS-DEC-0001` 至 `MLS-DEC-0006`；格式检查通过。

- [ ] **Step 5: 只提交治理文件（2–5 分钟）**

Run:

```powershell
git add -- docs/README.md docs/00-governance/ENGINEERING-LIFECYCLE.md docs/00-governance/DOCUMENT-AUTHORITY.md docs/00-governance/CHANGE-CONTROL.md docs/00-governance/DECISION-LOG.md
$cached = @(git diff --cached --name-only)
$unexpected = @($cached | Where-Object { $_ -notlike 'docs/00-governance/*' -and $_ -ne 'docs/README.md' })
if ($unexpected.Count -ne 0) { throw "Unexpected staged files: $($unexpected -join ', ')" }
git diff --cached --check
git commit -m "docs: add documentation governance framework"
```

Expected: 独立提交只包含 `docs/README.md` 和四个 `docs/00-governance/` 文件。

---

### Task 4: 建立需求入口、主规格、范围、语境、术语和 Open 问题

**Files:**
- Create: `docs/01-requirements/README.md`
- Create: `docs/01-requirements/SYSTEM-REQUIREMENTS.md`
- Create: `docs/01-requirements/requirements/VISION-AND-SCOPE.md`
- Create: `docs/01-requirements/requirements/STAKEHOLDERS-AND-CONTEXT.md`
- Create: `docs/01-requirements/requirements/GLOSSARY.md`
- Create: `docs/01-requirements/requirements/OPEN-QUESTIONS.md`

**Interfaces:**
- Consumes: `MLS-DOC-RA-0001` 的已确认范围、Task 3 的权威/状态/决策规则、`MLS-DEC-0001` 至 `MLS-DEC-0006`。
- Produces: Task 5 的流程与原子需求边界、Task 6 的质量与验收问题来源、Task 8 的需求基线预评审入口。

- [ ] **Step 1: 运行缺失性检查并确认规格来源仍为已批准版本（2–5 分钟）**

Run:

```powershell
$targets = @(
  'docs/01-requirements/README.md',
  'docs/01-requirements/SYSTEM-REQUIREMENTS.md',
  'docs/01-requirements/requirements/VISION-AND-SCOPE.md',
  'docs/01-requirements/requirements/STAKEHOLDERS-AND-CONTEXT.md',
  'docs/01-requirements/requirements/GLOSSARY.md',
  'docs/01-requirements/requirements/OPEN-QUESTIONS.md'
)
$existing = @($targets | Where-Object { Test-Path -LiteralPath $_ })
if ($existing.Count -ne 0) { throw "Requirement foundation already exists: $($existing -join ', ')" }
rg -n '^\| 文档状态 \| `Approved`|^\| 文档版本 \| `v1\.0`|MLS-OQ-0001|MLS-OQ-0015|单一图书馆|完全本地的支付模拟器' docs/01-requirements/REQUIREMENTS-STAGE-DESIGN.md
```

Expected: 六个目标文件均不存在；规格查询命中 `Approved/v1.0`、首尾 Open ID、单一图书馆和完全本地支付模拟器。

- [ ] **Step 2: 使用 `apply_patch` 创建需求入口和主规格（2–5 分钟）**

创建：

- `docs/01-requirements/README.md`，文档编号 `MLS-DOC-RA-0002`：给出阅读顺序 `REQUIREMENTS-STAGE-DESIGN → SYSTEM-REQUIREMENTS → requirements/ → acceptance/ → reviews/`；列出 RA-00 至 RA-15；状态明确“需求编制中，G5 未通过”。
- `docs/01-requirements/SYSTEM-REQUIREMENTS.md`，文档编号 `MLS-DOC-RA-0003`：只汇总 `MLS-OBJ-0001` 至 `MLS-OBJ-0004`、范围、项目约束和专题链接；每个业务目标与 `MLS-CON-0001` 至 `MLS-CON-0005` 均使用唯一 `**ID:**` 定义标记；必须写“需求基线状态：Not established”“验收执行状态：尚未执行”；不得复制后续功能需求全文。

业务目标固定为：

```text
MLS-OBJ-0001 为单一图书馆建立统一、可审计的核心业务系统。
MLS-OBJ-0002 支持访客、读者、馆员和系统管理员完成首版核心流通目标。
MLS-OBJ-0003 通过自动判定、人工边界处理和完整审计提高业务处理的一致性与可追责性。
MLS-OBJ-0004 在不连接任何真实支付网络的前提下演示罚款支付完整闭环。
```

项目约束固定为：

```text
MLS-CON-0001 只允许完全本地支付模拟器，不连接真实平台、官方沙箱或真实支付网络。
MLS-CON-0002 不保存真实商户号、真实密钥、真实付款凭据或真实付款信息。
MLS-CON-0003 首版除本地支付模拟器外不集成其他外部系统。
MLS-CON-0004 不兼容旧拼音 API、旧字段和旧数据库，不提供兼容迁移。
MLS-CON-0005 旧文档、旧测试和现有代码均非新需求的规范性来源。
```

Expected: 主规格只导航和汇总，不把阶段设计 `v1.0` 错写成最终需求基线 `v1.0`。

- [ ] **Step 3: 使用 `apply_patch` 创建愿景范围与参与者语境（2–5 分钟）**

创建：

- `VISION-AND-SCOPE.md`，文档编号 `MLS-DOC-RA-0004`：包含四个 `MLS-OBJ` 的业务理由；范围内能力逐项列出账户与权限、读者管理、书目与实体副本、公开检索、借阅、归还、自助续借、逾期罚款、模拟支付、操作审计；范围外逐项列出多馆点/多租户/跨馆调拨、电子资源、预约排队、业务通知、公告、留言、通用上传、统计仪表盘、遗失损坏赔偿、真实支付/官方沙箱、支付之外外部系统、旧兼容迁移。
- `STAKEHOLDERS-AND-CONTEXT.md`，文档编号 `MLS-DOC-RA-0005`：使用唯一 `**ID:**` 定义标记规范定义 `MLS-ACT-0001` 访客、`MLS-ACT-0002` 读者、`MLS-ACT-0003` 馆员、`MLS-ACT-0004` 系统管理员、`MLS-ACT-0005` 本地支付模拟器；明确浏览器前端、后端、新数据库和本地模拟器均在完整验收对象中；模拟器不得被描述为真实外部网络服务。

两个文件都使用 `Draft/v0.1`，来源为 `MLS-DOC-RA-0001` 与相关 `MLS-DEC`。不得写 API 路径、字段、表、框架或部署拓扑。

Expected: 范围内、范围外、参与者和系统边界可独立评审，且不把业务域提前固化为子系统。

- [ ] **Step 4: 使用 `apply_patch` 创建术语表（2–5 分钟）**

创建 `GLOSSARY.md`，文档编号 `MLS-DOC-RA-0006`。至少规范以下术语及彼此区别：访客、读者、馆员、系统管理员、书目、实体副本、流通记录、借阅、归还、续借、逾期、罚款、支付订单、退款、日终对账、自动判定、边界项、人工例外、审计事件、本地支付模拟器、需求基线。

另设“废弃术语与兼容禁令”章节，明确：

- 旧拼音 API 名称只可在历史证据中出现，不能成为当前契约。
- 旧字段和旧数据库模型不进入新需求，不建立别名或迁移要求。
- “阶段设计 `v1.0`”与“最终需求基线 `v1.0`”是两个不同对象。
- “验收用例”不是“验收执行报告”；没有执行证据时状态只能是“尚未执行”。

Expected: 术语定义业务概念而不是类、表、字段或接口签名。

- [ ] **Step 5: 使用 `apply_patch` 原样建立 15 项 Open 问题，不填任何默认值（2–5 分钟）**

创建 `OPEN-QUESTIONS.md`，文档编号 `MLS-DOC-RA-0007`。每项使用唯一 `**ID:**` 定义标记，初始状态 `Open`，来源 `MLS-DOC-RA-0001`，并写清关闭要求。ID 与问题必须严格对应：

| ID | 待确认内容 | 关闭要求 |
| --- | --- | --- |
| `MLS-OQ-0001` | 单个读者的借阅数量上限、借期、续借次数和最大续借期限 | 形成统一政策并给出可验收边界 |
| `MLS-OQ-0002` | 罚款计费单位、费率、宽限期、舍入方式和封顶规则 | 明确计算规则、示例和边界条件 |
| `MLS-OQ-0003` | 未缴罚款或逾期未还对新借阅和续借的影响 | 明确自动通过、转人工或拒绝规则 |
| `MLS-OQ-0004` | 自动通过、转人工和自动拒绝的完整判定条件 | 覆盖正常、边界、冲突和重复操作 |
| `MLS-OQ-0005` | 哪些高风险人工例外需要系统管理员复核 | 定义风险分级、权限和审计要求 |
| `MLS-OQ-0006` | 注册验证、防滥用、密码重置、账户停用和恢复规则 | 形成账户生命周期及验收场景 |
| `MLS-OQ-0007` | 馆藏实体副本的可借、借出、维护、停用等业务状态及转换 | 形成状态定义、转换条件和异常处理 |
| `MLS-OQ-0008` | 数据删除方式、业务记录保留和审计留存期限 | 明确保留、访问、删除和追责边界 |
| `MLS-OQ-0009` | 模拟支付退款权限、对账周期和异常处理时限 | 形成完整支付业务规则和验收依据 |
| `MLS-OQ-0010` | 目标用户量、馆藏量、业务交易量和峰值并发 | 形成容量和负载基线 |
| `MLS-OQ-0011` | 页面与服务响应时间目标 | 形成可测量场景、分位值和准入阈值 |
| `MLS-OQ-0012` | 安全、可用性、备份恢复和隐私指标 | 形成可验证的质量需求或正式豁免 |
| `MLS-OQ-0013` | 支持的浏览器、屏幕范围和无障碍要求 | 明确兼容与可访问性验收范围 |
| `MLS-OQ-0014` | 验收环境、验收数据、缺陷分级和准入规则 | 形成验收策略与退出判定 |
| `MLS-OQ-0015` | 文档与用户界面采用中文、英文或双语 | 形成语言与术语一致性规则 |

每项还必须包含责任角色、提出日期 `2026-07-13`、影响工件、决策人、决策日期和关闭依据字段；后三个字段初始写“未形成批准结论”，不得使用占位表达。

Expected: Open 登记册完整承接规格中的 15 个问题，没有虚构业务数值、质量阈值或关闭结论。

- [ ] **Step 6: 验证 ID、范围、状态和设计泄漏（2–5 分钟）**

Run:

```powershell
$files = @(
  'docs/01-requirements/README.md',
  'docs/01-requirements/SYSTEM-REQUIREMENTS.md',
  'docs/01-requirements/requirements/VISION-AND-SCOPE.md',
  'docs/01-requirements/requirements/STAKEHOLDERS-AND-CONTEXT.md',
  'docs/01-requirements/requirements/GLOSSARY.md',
  'docs/01-requirements/requirements/OPEN-QUESTIONS.md'
)
$missingStatus = @($files | Where-Object { -not (Select-String -LiteralPath $_ -Quiet -Pattern '^\| 文档状态 \| `Draft` \|$') })
if ($missingStatus.Count -ne 0) { throw "Missing Draft status: $($missingStatus -join ', ')" }
$oq = @(rg -o 'MLS-OQ-[0-9]{4}' docs/01-requirements/requirements/OPEN-QUESTIONS.md | Sort-Object -Unique)
$expectedOq = @(1..15 | ForEach-Object { 'MLS-OQ-{0:D4}' -f $_ })
if (@(Compare-Object $oq $expectedOq).Count -ne 0) { throw "Open ID mismatch: $($oq -join ', ')" }
$placeholderPattern = ('T'+'BD|T'+'ODO|待'+'补充|稍后'+'实现|implement '+'later')
$forbiddenPlaceholders = @(rg -n $placeholderPattern $files)
if ($LASTEXITCODE -eq 0) { throw "Placeholder text found: $($forbiddenPlaceholders -join '; ')" }
$leaks = @(rg -n '@RestController|\bController\b|\bService\b|\bRepository\b|CREATE TABLE|VARCHAR|/api/|Spring Boot|Vue 3|MySQL|Axios|Vite' docs/01-requirements/SYSTEM-REQUIREMENTS.md docs/01-requirements/requirements)
if ($LASTEXITCODE -eq 0) { throw "Design leakage found: $($leaks -join '; ')" }
rg -n '需求基线状态.*Not established|验收执行状态.*尚未执行' docs/01-requirements/SYSTEM-REQUIREMENTS.md
git diff --check
```

Expected: 六个文件均为 `Draft`；Open ID 唯一集合恰好为 0001–0015；占位符和设计泄漏扫描退出码为 1（无匹配）；主规格明确基线未建立、验收未执行；格式检查通过。

- [ ] **Step 7: 只提交需求基础工件（2–5 分钟）**

Run:

```powershell
git add -- docs/01-requirements/README.md docs/01-requirements/SYSTEM-REQUIREMENTS.md docs/01-requirements/requirements/VISION-AND-SCOPE.md docs/01-requirements/requirements/STAKEHOLDERS-AND-CONTEXT.md docs/01-requirements/requirements/GLOSSARY.md docs/01-requirements/requirements/OPEN-QUESTIONS.md
$cached = @(git diff --cached --name-only)
$unexpected = @($cached | Where-Object { $_ -notlike 'docs/01-requirements/*' })
if ($unexpected.Count -ne 0 -or $cached.Count -ne 6) { throw "Unexpected staged scope: $($cached -join ', ')" }
git diff --cached --check
git commit -m "docs: add requirements scope foundation"
```

Expected: 独立提交恰好包含六个需求基础文件；不包含阶段设计、实施计划、归档、治理或产品代码。

---

### Task 5: 建立业务流程、用例、功能需求、业务规则和信息需求

**Files:**
- Create: `docs/01-requirements/requirements/BUSINESS-PROCESSES.md`
- Create: `docs/01-requirements/requirements/USE-CASES.md`
- Create: `docs/01-requirements/requirements/FUNCTIONAL-REQUIREMENTS.md`
- Create: `docs/01-requirements/requirements/BUSINESS-RULES.md`
- Create: `docs/01-requirements/requirements/INFORMATION-REQUIREMENTS.md`

**Interfaces:**
- Consumes: Task 4 的 `MLS-OBJ-0001` 至 `MLS-OBJ-0004`、`MLS-ACT-0001` 至 `MLS-ACT-0005`、范围内外清单、术语和 `MLS-OQ-0001` 至 `MLS-OQ-0015`。
- Produces: Task 6 定义 `MLS-AC-NNNN`、`MLS-AT-NNNN` 和追踪矩阵所需的 `MLS-UC-NNNN`、`MLS-FR-NNNN`、`MLS-BR-NNNN`、`MLS-IR-NNNN`。

- [ ] **Step 1: 运行缺失性与前置追踪检查（2–5 分钟）**

Run:

```powershell
$targets = @(
  'docs/01-requirements/requirements/BUSINESS-PROCESSES.md',
  'docs/01-requirements/requirements/USE-CASES.md',
  'docs/01-requirements/requirements/FUNCTIONAL-REQUIREMENTS.md',
  'docs/01-requirements/requirements/BUSINESS-RULES.md',
  'docs/01-requirements/requirements/INFORMATION-REQUIREMENTS.md'
)
$existing = @($targets | Where-Object { Test-Path -LiteralPath $_ })
if ($existing.Count -ne 0) { throw "Business requirement files already exist: $($existing -join ', ')" }
rg -n 'MLS-OBJ-0001|MLS-OBJ-0004' docs/01-requirements/requirements/VISION-AND-SCOPE.md
rg -n 'MLS-ACT-0001|MLS-ACT-0005' docs/01-requirements/requirements/STAKEHOLDERS-AND-CONTEXT.md
rg -n 'MLS-OQ-0001|MLS-OQ-0015' docs/01-requirements/requirements/OPEN-QUESTIONS.md
```

Expected: 五个目标文件均不存在；目标、参与者与 Open 问题首尾 ID 均可从 Task 4 工件追踪。

- [ ] **Step 2: 使用 `apply_patch` 创建端到端业务流程（2–5 分钟）**

创建 `BUSINESS-PROCESSES.md`，文档编号 `MLS-DOC-RA-0008`、状态 `Draft`、版本 `v0.1`。分别描述以下流程的触发者、前置业务状态、正常步骤、替代步骤、异常步骤、结束状态和关联 Open 问题：

1. 访客公开检索馆藏。
2. 访客自助注册且无需人工审核。
3. 馆员维护书目与实体副本。
4. 馆员为读者办理具体副本借阅。
5. 馆员办理具体副本归还并触发逾期判定。
6. 读者发起自助续借并由系统自动判定。
7. 系统自动通过、转人工或拒绝，馆员处理边界项。
8. 系统形成罚款，读者查看并通过本地模拟器支付。
9. 支付订单创建、查询、异步回调、重复回调幂等、关闭、退款和日终对账。
10. 系统管理员和授权角色查询操作审计。

对借阅上限、借期、罚率、状态枚举、风险分级、退款权限和时限只引用 `MLS-OQ-0001` 至 `MLS-OQ-0009`；不写任何数值或默认判定。支付流程必须明确“不离开受控本地环境”和“无真实资金”。

Expected: 流程覆盖正常、替代、异常和人工例外，但不写 API、字段、数据表、类或部署拓扑。

- [ ] **Step 3: 使用 `apply_patch` 创建 14 个系统用例（2–5 分钟）**

创建 `USE-CASES.md`，文档编号 `MLS-DOC-RA-0009`。每个用例都使用 `**ID:**` 定义标记，并包含目标、主要参与者、次要参与者、前置条件、触发、主流程、替代流程、异常流程、后置条件、相关目标、相关 Open 问题：

| ID | 用例 | 主要参与者 | 目标 |
| --- | --- | --- | --- |
| `MLS-UC-0001` | 公开检索馆藏 | `MLS-ACT-0001` | `MLS-OBJ-0002` |
| `MLS-UC-0002` | 自助注册读者 | `MLS-ACT-0001` | `MLS-OBJ-0002` |
| `MLS-UC-0003` | 管理账户与访问权限 | `MLS-ACT-0004` | `MLS-OBJ-0001` |
| `MLS-UC-0004` | 管理读者 | `MLS-ACT-0003` | `MLS-OBJ-0002` |
| `MLS-UC-0005` | 管理书目与实体副本 | `MLS-ACT-0003` | `MLS-OBJ-0001` |
| `MLS-UC-0006` | 办理借阅 | `MLS-ACT-0003` | `MLS-OBJ-0002`、`MLS-OBJ-0003` |
| `MLS-UC-0007` | 办理归还 | `MLS-ACT-0003` | `MLS-OBJ-0002`、`MLS-OBJ-0003` |
| `MLS-UC-0008` | 自助续借 | `MLS-ACT-0002` | `MLS-OBJ-0002`、`MLS-OBJ-0003` |
| `MLS-UC-0009` | 查看罚款 | `MLS-ACT-0002` | `MLS-OBJ-0002` |
| `MLS-UC-0010` | 完成模拟支付 | `MLS-ACT-0002` | `MLS-OBJ-0004` |
| `MLS-UC-0011` | 处理模拟退款 | 授权角色，受 `MLS-OQ-0009` 约束 | `MLS-OBJ-0004` |
| `MLS-UC-0012` | 执行日终对账 | 授权角色，受 `MLS-OQ-0009` 约束 | `MLS-OBJ-0004` |
| `MLS-UC-0013` | 处理人工例外 | `MLS-ACT-0003` | `MLS-OBJ-0003` |
| `MLS-UC-0014` | 查询操作审计 | 授权角色 | `MLS-OBJ-0003` |

Expected: 用例只表达参与者可观察行为；未确认“授权角色”时保留 Open 引用，不推断系统管理员或馆员的最终权限。

- [ ] **Step 4: 使用 `apply_patch` 创建 27 项原子功能需求（2–5 分钟/每组）**

创建 `FUNCTIONAL-REQUIREMENTS.md`，文档编号 `MLS-DOC-RA-0010`。每项严格使用 Shared Document Contracts 的需求模板、唯一 `**ID:**` 定义和一对一预留的验收条件 `MLS-AC-0001` 至 `MLS-AC-0027`：

| FR | 规范性能力 | 来源用例 | AC |
| --- | --- | --- | --- |
| `MLS-FR-0001` | 允许访客公开检索馆藏 | `MLS-UC-0001` | `MLS-AC-0001` |
| `MLS-FR-0002` | 允许访客自助注册并在无需人工审核时成为读者 | `MLS-UC-0002` | `MLS-AC-0002` |
| `MLS-FR-0003` | 按访客、读者、馆员、系统管理员角色限制业务能力 | `MLS-UC-0003` | `MLS-AC-0003` |
| `MLS-FR-0004` | 允许系统管理员管理账户与访问权限 | `MLS-UC-0003` | `MLS-AC-0004` |
| `MLS-FR-0005` | 允许馆员管理读者业务状态 | `MLS-UC-0004` | `MLS-AC-0005` |
| `MLS-FR-0006` | 允许馆员维护书目信息 | `MLS-UC-0005` | `MLS-AC-0006` |
| `MLS-FR-0007` | 允许馆员在书目下维护可独立识别的实体副本 | `MLS-UC-0005` | `MLS-AC-0007` |
| `MLS-FR-0008` | 展示实体副本当前业务可用性 | `MLS-UC-0005` | `MLS-AC-0008` |
| `MLS-FR-0009` | 允许馆员为读者办理具体副本借阅 | `MLS-UC-0006` | `MLS-AC-0009` |
| `MLS-FR-0010` | 按全馆统一业务配置自动判定借阅 | `MLS-UC-0006` | `MLS-AC-0010` |
| `MLS-FR-0011` | 将不能自动确定的边界项转入人工处理 | `MLS-UC-0006`、`MLS-UC-0013` | `MLS-AC-0011` |
| `MLS-FR-0012` | 允许馆员例外处理并记录原判定、原因、说明、人员和时间 | `MLS-UC-0013` | `MLS-AC-0012` |
| `MLS-FR-0013` | 允许馆员办理具体副本归还 | `MLS-UC-0007` | `MLS-AC-0013` |
| `MLS-FR-0014` | 在归还时判定逾期并形成相应罚款记录 | `MLS-UC-0007` | `MLS-AC-0014` |
| `MLS-FR-0015` | 允许读者对本人符合候选条件的借阅发起自助续借 | `MLS-UC-0008` | `MLS-AC-0015` |
| `MLS-FR-0016` | 按全馆统一业务配置自动判定续借 | `MLS-UC-0008` | `MLS-AC-0016` |
| `MLS-FR-0017` | 允许读者查看本人罚款及其结算状态 | `MLS-UC-0009` | `MLS-AC-0017` |
| `MLS-FR-0018` | 为应结算罚款创建本地模拟支付订单 | `MLS-UC-0010` | `MLS-AC-0018` |
| `MLS-FR-0019` | 查询本地模拟支付订单结果 | `MLS-UC-0010` | `MLS-AC-0019` |
| `MLS-FR-0020` | 可控地产生成功、失败、取消和超时结果 | `MLS-UC-0010` | `MLS-AC-0020` |
| `MLS-FR-0021` | 处理本地模拟器的异步结果通知 | `MLS-UC-0010` | `MLS-AC-0021` |
| `MLS-FR-0022` | 对重复支付结果通知保持幂等 | `MLS-UC-0010` | `MLS-AC-0022` |
| `MLS-FR-0023` | 关闭不再继续的模拟支付订单 | `MLS-UC-0010` | `MLS-AC-0023` |
| `MLS-FR-0024` | 支持模拟支付的全额或部分退款 | `MLS-UC-0011` | `MLS-AC-0024` |
| `MLS-FR-0025` | 生成日终对账结果并识别模拟差异 | `MLS-UC-0012` | `MLS-AC-0025` |
| `MLS-FR-0026` | 在支付交互中显著展示“模拟支付，无真实资金交易” | `MLS-UC-0010` | `MLS-AC-0026` |
| `MLS-FR-0027` | 记录并允许授权角色查询关键业务与例外操作审计 | `MLS-UC-0014` | `MLS-AC-0027` |

凡依赖借阅数值、罚款算法、状态转换、风险复核、账户生命周期或退款/对账权限的需求，依赖字段必须引用 `MLS-OQ-0001` 至 `MLS-OQ-0009`，状态保持 `Draft`。

Expected: 27 项需求均原子化、均有来源用例和预留验收条件；没有猜测业务数字或权限结论。

- [ ] **Step 5: 使用 `apply_patch` 创建业务规则和信息需求（2–5 分钟）**

创建 `BUSINESS-RULES.md`（`MLS-DOC-RA-0011`）并以 `**ID:**` 定义以下规则：

```text
MLS-BR-0001 单一图书馆边界。
MLS-BR-0002 全馆统一流通政策。
MLS-BR-0003 书目与实体副本分离，流通对象为具体副本。
MLS-BR-0004 借阅和归还由馆员发起。
MLS-BR-0005 读者可以发起自助续借。
MLS-BR-0006 系统按已批准业务配置自动判定。
MLS-BR-0007 不能自动确定的边界项必须转人工。
MLS-BR-0008 馆员例外必须记录原判定、原因、说明、人员和时间。
MLS-BR-0009 自助注册无需人工审核。
MLS-BR-0010 支付只在完全本地模拟环境内运行且无真实资金。
MLS-BR-0011 重复支付通知不得重复改变罚款或订单结果。
MLS-BR-0012 支付闭环包含创建、查询、通知、关闭、退款和日终对账。
MLS-BR-0013 首版除本地支付模拟器外不集成外部系统。
MLS-BR-0014 不兼容旧拼音 API、旧字段和旧数据库。
MLS-BR-0015 关键业务和人工例外必须可审计。
```

创建 `INFORMATION-REQUIREMENTS.md`（`MLS-DOC-RA-0012`）并以 `**ID:**` 定义：`MLS-IR-0001` 账户、`0002` 读者资料、`0003` 书目、`0004` 实体副本、`0005` 流通记录、`0006` 罚款、`0007` 支付订单、`0008` 退款、`0009` 对账记录、`0010` 自动/人工判定、`0011` 审计事件。每项只描述业务含义、所有者、生命周期、业务关系、可见性和关联 Open 问题；不得出现表、列、类型、索引或持久化产品。

Expected: 规则独立于用例，信息需求保持概念层，不泄漏物理数据设计。

- [ ] **Step 6: 验证 ID 集合、来源、AC 引用、占位表达和设计泄漏（2–5 分钟）**

Run:

```powershell
$uc = @(rg -o 'MLS-UC-[0-9]{4}' docs/01-requirements/requirements/USE-CASES.md | Sort-Object -Unique)
$fr = @(rg -o 'MLS-FR-[0-9]{4}' docs/01-requirements/requirements/FUNCTIONAL-REQUIREMENTS.md | Sort-Object -Unique)
$br = @(rg -o 'MLS-BR-[0-9]{4}' docs/01-requirements/requirements/BUSINESS-RULES.md | Sort-Object -Unique)
$ir = @(rg -o 'MLS-IR-[0-9]{4}' docs/01-requirements/requirements/INFORMATION-REQUIREMENTS.md | Sort-Object -Unique)
if ($uc.Count -ne 14 -or $fr.Count -ne 27 -or $br.Count -ne 15 -or $ir.Count -ne 11) { throw "Unexpected ID counts: UC=$($uc.Count) FR=$($fr.Count) BR=$($br.Count) IR=$($ir.Count)" }
$acRefs = @(rg -o 'MLS-AC-[0-9]{4}' docs/01-requirements/requirements/FUNCTIONAL-REQUIREMENTS.md | Sort-Object -Unique)
if ($acRefs.Count -ne 27 -or $acRefs[0] -ne 'MLS-AC-0001' -or $acRefs[26] -ne 'MLS-AC-0027') { throw "Unexpected AC references: $($acRefs -join ', ')" }
$placeholderPattern = ('T'+'BD|T'+'ODO|待'+'补充|稍后'+'实现|implement '+'later')
$placeholders = @(rg -n $placeholderPattern docs/01-requirements/requirements)
if ($LASTEXITCODE -eq 0) { throw "Placeholder expression found: $($placeholders -join '; ')" }
$leaks = @(rg -n '@RestController|\bController\b|\bService\b|\bRepository\b|CREATE TABLE|VARCHAR|/api/|Spring Boot|Vue 3|MySQL|Axios|Vite' docs/01-requirements/requirements/BUSINESS-PROCESSES.md docs/01-requirements/requirements/USE-CASES.md docs/01-requirements/requirements/FUNCTIONAL-REQUIREMENTS.md docs/01-requirements/requirements/BUSINESS-RULES.md docs/01-requirements/requirements/INFORMATION-REQUIREMENTS.md)
if ($LASTEXITCODE -eq 0) { throw "Design leakage found: $($leaks -join '; ')" }
git diff --check
```

Expected: `UC=14 FR=27 BR=15 IR=11`，AC 引用为 0001–0027；占位表达和设计泄漏扫描均无匹配；格式检查通过。

- [ ] **Step 7: 只提交业务需求工件（2–5 分钟）**

Run:

```powershell
git add -- docs/01-requirements/requirements/BUSINESS-PROCESSES.md docs/01-requirements/requirements/USE-CASES.md docs/01-requirements/requirements/FUNCTIONAL-REQUIREMENTS.md docs/01-requirements/requirements/BUSINESS-RULES.md docs/01-requirements/requirements/INFORMATION-REQUIREMENTS.md
$cached = @(git diff --cached --name-only)
if ($cached.Count -ne 5 -or @($cached | Where-Object { $_ -notlike 'docs/01-requirements/requirements/*' }).Count -ne 0) { throw "Unexpected staged scope: $($cached -join ', ')" }
git diff --cached --check
git commit -m "docs: add core business requirements"
```

Expected: 独立提交恰好包含五个业务需求工件。

---

### Task 6: 建立非功能需求、验收定义和双向追踪矩阵

**Files:**
- Create: `docs/01-requirements/requirements/NONFUNCTIONAL-REQUIREMENTS.md`
- Create: `docs/01-requirements/requirements/TRACEABILITY-MATRIX.md`
- Create: `docs/01-requirements/acceptance/ACCEPTANCE-STRATEGY.md`
- Create: `docs/01-requirements/acceptance/ACCEPTANCE-CASES.md`
- Create: `docs/01-requirements/acceptance/ACCEPTANCE-REPORTS.md`

**Interfaces:**
- Consumes: Task 5 的 14 个用例、27 个功能需求、15 个业务规则和 11 个信息需求；Task 4 的 15 个 Open 问题。
- Produces: Task 7 评审清单和 Task 8 G3–G5 评审所需的质量需求、`MLS-AC-0001` 至 `MLS-AC-0027`、`MLS-AT-0001` 至 `MLS-AT-0014` 以及完整追踪列。

- [ ] **Step 1: 运行缺失性检查并证明验收证据尚不存在（2–5 分钟）**

Run:

```powershell
$targets = @(
  'docs/01-requirements/requirements/NONFUNCTIONAL-REQUIREMENTS.md',
  'docs/01-requirements/requirements/TRACEABILITY-MATRIX.md',
  'docs/01-requirements/acceptance/ACCEPTANCE-STRATEGY.md',
  'docs/01-requirements/acceptance/ACCEPTANCE-CASES.md',
  'docs/01-requirements/acceptance/ACCEPTANCE-REPORTS.md'
)
$existing = @($targets | Where-Object { Test-Path -LiteralPath $_ })
if ($existing.Count -ne 0) { throw "Quality or acceptance files already exist: $($existing -join ', ')" }
rg -n '验收执行状态.*尚未执行' docs/01-requirements/SYSTEM-REQUIREMENTS.md
```

Expected: 五个目标文件均不存在；主规格明确当前没有验收执行结果。

- [ ] **Step 2: 使用 `apply_patch` 创建非功能需求登记册（2–5 分钟）**

创建 `NONFUNCTIONAL-REQUIREMENTS.md`，文档编号 `MLS-DOC-RA-0013`、状态 `Draft`。以 `**ID:**` 定义以下已确认约束，并将未确认阈值显式关联 Open 问题：

```text
MLS-NFR-SEC-0001 系统和测试环境不得向真实支付域名、官方支付沙箱或真实支付网络发起请求；关联 MLS-CON-0001。
MLS-NFR-SEC-0002 系统不得保存真实商户号、真实密钥、真实付款凭据或真实付款信息；关联 MLS-CON-0002。
MLS-NFR-SEC-0003 角色未获授权时不得执行对应业务能力；具体账户生命周期关联 MLS-OQ-0006。
MLS-NFR-REL-0001 重复支付通知不得造成重复结算或重复状态转换；关联 MLS-FR-0022。
MLS-NFR-USE-0001 所有模拟支付交互必须显著展示“模拟支付，无真实资金交易”；关联 MLS-FR-0026。
MLS-NFR-AUD-0001 人工例外和关键业务操作必须保留可追责记录；留存期限关联 MLS-OQ-0008。
MLS-NFR-PER-0001 容量、并发和响应时间指标在 MLS-OQ-0010 与 MLS-OQ-0011 关闭前保持 Draft，不写阈值。
MLS-NFR-AVL-0001 可用性、备份恢复和隐私指标在 MLS-OQ-0012 关闭前保持 Draft，不写阈值。
MLS-NFR-CMP-0001 浏览器、屏幕与无障碍范围在 MLS-OQ-0013 关闭前保持 Draft，不写兼容列表。
```

每项包含来源、理由、测量方法、验收条件、状态和 Open 依赖。前三项支付安全约束的测量方法写为“离线网络出口检查、配置/密钥扫描和本地模拟场景”，但不指定实现工具或部署产品。

Expected: 已确认约束可验证；未确认指标只保持 Draft 并引用 Open 问题，没有虚构数字或正式豁免。

- [ ] **Step 3: 使用 `apply_patch` 创建验收策略和 27 个验收条件（2–5 分钟）**

创建 `ACCEPTANCE-STRATEGY.md`（`MLS-DOC-AT-0001`），明确：验收对象是完整系统；需求准入要求、受控本地数据原则、无外部支付网络、执行证据要求、缺陷与退出规则由 `MLS-OQ-0014` 关闭后批准；当前只定义策略草案，不执行测试。

创建 `ACCEPTANCE-CASES.md`（`MLS-DOC-AT-0002`）：

- 以 `**ID:**` 定义 `MLS-AC-0001` 至 `MLS-AC-0027`，每个 AC 一对一验证同号 FR 的用户可观察结果。
- 以 `**ID:**` 定义 `MLS-AT-0001` 至 `MLS-AT-0014`，分别对应 `MLS-UC-0001` 至 `MLS-UC-0014`。
- 每个 AT 包含关联目标、用例、FR/BR/IR/NFR、前置业务状态、数据类别、操作、预期可观察结果、异常场景、证据要求和状态 `Draft`。
- 数值政策未关闭时，AC 写“依据已批准的统一政策配置判定；当前由 `MLS-OQ-NNNN` 阻塞形成可执行边界”，不得写示例数字。
- 支付 AT 必须覆盖成功、失败、取消、超时、重复回调、关闭、全额/部分退款、对账差异，并验证显著模拟标识和零真实网络调用。

Expected: 需求到验收条件一一对应，用例到验收用例一一对应；受 Open 阻塞的场景不伪装成可执行测试。

- [ ] **Step 4: 使用 `apply_patch` 创建“尚未执行”的验收报告登记册（2–5 分钟）**

创建 `ACCEPTANCE-REPORTS.md`（`MLS-DOC-AT-0003`）。正文必须包含：

```text
总体执行状态：尚未执行
执行次数：0
通过：0
失败：0
阻塞：0（仅表示没有执行批次，不表示用例可执行）
验收证据：无
```

提供未来执行记录表的固定列：执行批次、基线版本、环境标识、数据集、AT ID、执行人、开始/结束时间、结果、证据链接、缺陷 ID、复核人。明确旧测试、旧 Surefire 结果、文档检查和计划编写均不能登记为系统验收通过。

Expected: 报告区分“计划”和“结果”，不含任何虚构通过记录。

- [ ] **Step 5: 使用 `apply_patch` 创建完整追踪矩阵（2–5 分钟）**

创建 `TRACEABILITY-MATRIX.md`，文档编号 `MLS-DOC-RA-0014`。列必须严格等于 Shared Document Contracts 中的 14 列。至少包含：

- 27 行功能需求追踪，每行从目标、用例、FR、AC 到对应 AT。
- 15 项业务规则、11 项信息需求和 9 项非功能需求的追踪行；允许一个需求关联多个 AT，但每行只写一个明确关系。
- `Source Document` 使用仓库相对路径。
- `Requirement Status` 使用实际 `Draft`。
- 后续总体设计、系统测试、详细设计、集成测试、模块设计、单元测试六列全部写 `Not created`。

Expected: 矩阵能够反向识别每个目标、用例、需求、AC 和 AT；不会提前虚构右侧后续测试证据。

- [ ] **Step 6: 验证 NFR、AC、AT、报告状态和追踪列（2–5 分钟）**

Run:

```powershell
$nfr = @(rg -o 'MLS-NFR-[A-Z]+-[0-9]{4}' docs/01-requirements/requirements/NONFUNCTIONAL-REQUIREMENTS.md | Sort-Object -Unique)
$ac = @(rg -o 'MLS-AC-[0-9]{4}' docs/01-requirements/acceptance/ACCEPTANCE-CASES.md | Sort-Object -Unique)
$at = @(rg -o 'MLS-AT-[0-9]{4}' docs/01-requirements/acceptance/ACCEPTANCE-CASES.md | Sort-Object -Unique)
if ($nfr.Count -ne 9 -or $ac.Count -ne 27 -or $at.Count -ne 14) { throw "Unexpected acceptance counts: NFR=$($nfr.Count) AC=$($ac.Count) AT=$($at.Count)" }
rg -n '^总体执行状态：尚未执行$|^执行次数：0$|^验收证据：无$' docs/01-requirements/acceptance/ACCEPTANCE-REPORTS.md
$header = (Select-String -LiteralPath docs/01-requirements/requirements/TRACEABILITY-MATRIX.md -Pattern '^\| Objective ID \|').Line
$columns = @($header.Split('|') | ForEach-Object { $_.Trim() } | Where-Object { $_ }).Count
if ($columns -ne 14) { throw "Traceability matrix must have 14 columns, found $columns" }
$future = @(rg -o 'Not created' docs/01-requirements/requirements/TRACEABILITY-MATRIX.md)
if ($future.Count -lt 6) { throw 'Future V-model mappings are not explicitly Not created' }
$realPaymentPatterns = 'api\.paypal|stripe\.com|openapi\.alipay|pay\.weixin|merchant_secret|client_secret|private_key'
$realMatches = @(rg -n $realPaymentPatterns docs/01-requirements/requirements docs/01-requirements/acceptance)
if ($LASTEXITCODE -eq 0) { throw "Real payment domain or credential pattern found: $($realMatches -join '; ')" }
rg -n '完全本地|不得向真实支付|不得保存真实|模拟支付，无真实资金交易' docs/01-requirements/requirements/NONFUNCTIONAL-REQUIREMENTS.md docs/01-requirements/acceptance/ACCEPTANCE-CASES.md
git diff --check
```

Expected: `NFR=9 AC=27 AT=14`；报告仍为零执行；矩阵恰好 14 列并明确未来映射未创建；真实支付域名/凭据扫描无匹配，正向安全约束至少命中四类声明；格式检查通过。

- [ ] **Step 7: 只提交质量、验收和追踪工件（2–5 分钟）**

Run:

```powershell
git add -- docs/01-requirements/requirements/NONFUNCTIONAL-REQUIREMENTS.md docs/01-requirements/requirements/TRACEABILITY-MATRIX.md docs/01-requirements/acceptance/ACCEPTANCE-STRATEGY.md docs/01-requirements/acceptance/ACCEPTANCE-CASES.md docs/01-requirements/acceptance/ACCEPTANCE-REPORTS.md
$cached = @(git diff --cached --name-only)
if ($cached.Count -ne 5 -or @($cached | Where-Object { $_ -notlike 'docs/01-requirements/*' }).Count -ne 0) { throw "Unexpected staged scope: $($cached -join ', ')" }
git diff --cached --check
git commit -m "docs: add acceptance and traceability foundation"
```

Expected: 独立提交恰好包含五个质量、验收和追踪工件。

---

### Task 7: 建立证据、评审工件和后三阶段占位入口

**Files:**
- Create: `docs/01-requirements/evidence/LEGACY-DOCUMENT-ASSESSMENT.md`
- Create: `docs/01-requirements/evidence/CODE-REUSE-INVENTORY.md`
- Create: `docs/01-requirements/reviews/REQUIREMENTS-REVIEW-CHECKLIST.md`
- Create: `docs/01-requirements/reviews/REQUIREMENTS-BASELINE-RECORD.md`
- Create: `docs/02-overall-design/README.md`
- Create: `docs/03-detailed-design/README.md`
- Create: `docs/04-module-design/README.md`

**Interfaces:**
- Consumes: Task 2 的归档来源清单、当前 `.codegraph/` 索引、Task 4–6 的全部需求和验收工件。
- Produces: Task 8 全局评审所需的历史证据判断、代码复用候选、评审模板、基线状态和后续 V 模型阶段入口。

- [ ] **Step 1: 运行缺失性检查并确认 CodeGraph 可用（2–5 分钟）**

Run:

```powershell
$targets = @(
  'docs/01-requirements/evidence/LEGACY-DOCUMENT-ASSESSMENT.md',
  'docs/01-requirements/evidence/CODE-REUSE-INVENTORY.md',
  'docs/01-requirements/reviews/REQUIREMENTS-REVIEW-CHECKLIST.md',
  'docs/01-requirements/reviews/REQUIREMENTS-BASELINE-RECORD.md',
  'docs/02-overall-design/README.md',
  'docs/03-detailed-design/README.md',
  'docs/04-module-design/README.md'
)
$existing = @($targets | Where-Object { Test-Path -LiteralPath $_ })
if ($existing.Count -ne 0) { throw "Evidence/review/stage files already exist: $($existing -join ', ')" }
if (-not (Test-Path -LiteralPath '.codegraph')) { throw '.codegraph index is required for the planned reuse audit' }
codegraph explore "Locate the Maven and Vite project skeletons, backend authentication and circulation implementations, frontend legacy pinyin API usage, library-server/arc, tests, fixed JWT/database credentials, CORS, and ddl-auto configuration. Return exact current files and symbols only."
```

Expected: 七个目标文件均不存在；CodeGraph 返回当前源文件/符号路径。按仓库规则，不先用 `rg` 或逐文件阅读理解代码。

- [ ] **Step 2: 使用 `apply_patch` 创建旧文档评估（2–5 分钟）**

创建 `LEGACY-DOCUMENT-ASSESSMENT.md`，文档编号 `MLS-DOC-EVD-0001`、权威性质 `证据性`。逐行承接 `docs/90-archive/README.md` 的八个来源类别，记录：原路径、归档路径、历史用途、可参考内容、已知冲突、可信度、是否需要代码复核、替代规范。固定结论：所有旧文档均为 `Historical Reference`，不能产生或批准新需求。

Expected: 评估保留证据价值但不恢复旧 API、字段、数据库或历史计划的契约地位。

- [ ] **Step 3: 使用 CodeGraph 输出和 `apply_patch` 创建代码复用台账（2–5 分钟）**

创建 `CODE-REUSE-INVENTORY.md`，文档编号 `MLS-DOC-EVD-0002`、权威性质 `证据性`。每项记录精确路径、CodeGraph 证据摘要、初始处置、关联新需求、风险、后续验证和决定状态。至少包含：

| 资产组 | 初始处置 |
| --- | --- |
| Maven/Vite 工程骨架和通用开发工具 | 优先评估直接复用或轻量调整 |
| 后端认证、借阅、归还、续借、罚款实现 | 重构复用候选，必须按新需求重新评估 |
| 依赖旧拼音 API/旧字段的前端业务层和页面 | 原则上弃用重写；非业务展示资产另行评估 |
| `library-server/arc/**` | 归档弃用，不进入新实现基线 |
| 旧测试 | 仅作历史行为/特征测试参考，不作验收证据 |
| 固定 JWT 密钥、数据库口令、开放式 CORS、自动更新表结构配置 | 弃用，不得继承 |

Expected: 台账明确“代码现状不反向定义需求”，不把当前实现状态改写为总体设计结论。

- [ ] **Step 4: 使用 `apply_patch` 创建评审清单和基线记录（2–5 分钟）**

创建：

- `REQUIREMENTS-REVIEW-CHECKLIST.md`，文档编号 `MLS-DOC-REV-0001`：按 G0–G5 分组列出 Git 跟踪、权威、归档、范围、原子性、来源、验收条件、NFR 可测量性、追踪孤立项、设计泄漏、真实支付域名/凭据、报告证据和批准责任检查；初始结果写 `Not reviewed`。
- `REQUIREMENTS-BASELINE-RECORD.md`，文档编号 `MLS-DOC-REV-0002`：明确“阶段设计版本：`v1.0 Approved`”“需求基线：`Not established`”“验收执行：尚未执行”；记录 G0–G5 结果、编写/评审/批准角色、Open 问题、延期依据和签字区；初始状态 `Draft`，不得预填通过结论或批准人。

Expected: 两份文件提供评审工具，不把创建文件本身视为完成 G5。

- [ ] **Step 5: 使用 `apply_patch` 创建总体、详细、模块设计入口（2–5 分钟）**

创建：

- `docs/02-overall-design/README.md`，编号 `MLS-DOC-OD-0001`，映射“总体设计 ↔ 系统测试”。
- `docs/03-detailed-design/README.md`，编号 `MLS-DOC-DD-0001`，映射“详细设计 ↔ 集成测试”。
- `docs/04-module-design/README.md`，编号 `MLS-DOC-MD-0001`，映射“模块设计 ↔ 单元测试”。

三份入口状态均为 `Draft/v0.1`，正文都写：当前阶段未开始；只有已批准需求基线可作为输入；对应设计、测试和评审目录将在阶段启动时建立；当前不存在设计结论或测试执行证据。不得创建虚假的设计文档、测试用例或空结果报告。

Expected: V 模型目录可导航，但不会提前冻结子系统、构件、模块或技术方案。

- [ ] **Step 6: 验证证据性质、基线状态和后三阶段无设计结论（2–5 分钟）**

Run:

```powershell
rg -n '证据性|不能产生或批准新需求|代码现状不反向定义需求' docs/01-requirements/evidence
rg -n '阶段设计版本.*v1\.0.*Approved|需求基线.*Not established|验收执行.*尚未执行|Not reviewed' docs/01-requirements/reviews
rg -n '总体设计.*系统测试|详细设计.*集成测试|模块设计.*单元测试' docs/02-overall-design/README.md docs/03-detailed-design/README.md docs/04-module-design/README.md
$premature = @(rg -n '已完成设计|测试通过|验收通过|基线已批准' docs/02-overall-design docs/03-detailed-design docs/04-module-design)
if ($LASTEXITCODE -eq 0) { throw "Premature stage conclusion found: $($premature -join '; ')" }
git diff --check
```

Expected: 证据和基线状态声明命中；三条 V 模型映射命中；提前结论扫描无匹配；格式检查通过。

- [ ] **Step 7: 只提交证据、评审和阶段入口（2–5 分钟）**

Run:

```powershell
git add -- docs/01-requirements/evidence docs/01-requirements/reviews docs/02-overall-design/README.md docs/03-detailed-design/README.md docs/04-module-design/README.md
$cached = @(git diff --cached --name-only)
if ($cached.Count -ne 7) { throw "Expected 7 staged files, found $($cached.Count): $($cached -join ', ')" }
git diff --cached --check
git commit -m "docs: add evidence review and design stage entries"
```

Expected: 独立提交恰好包含七个文件，不包含产品代码或其他 README。

---

### Task 8: 收敛三份 README、执行全局验证并记录基线评审

**Files:**
- Modify: `README.md`
- Modify: `library-server/README.md`
- Modify: `library-frontend/README.md`
- Modify: `docs/01-requirements/reviews/REQUIREMENTS-REVIEW-CHECKLIST.md`
- Modify: `docs/01-requirements/reviews/REQUIREMENTS-BASELINE-RECORD.md`

**Interfaces:**
- Consumes: Task 1–7 的完整文档权威链、归档、需求、验收、证据和评审工件。
- Produces: 一致的三 README 导航、可复现的全局验证证据、G0–G5 实际评审结论和后续 Open 问题处理入口。

- [ ] **Step 1: 运行失败检查，识别 README 中的旧权威声明和未评审状态（2–5 分钟）**

Run:

```powershell
rg -n 'Current Documentation Authority|docs/API-SPEC\.md|docs/ARCHITECTURE\.md|docs/DATA-MODEL\.md|P1-P4.*implemented|当前契约|current contract' README.md library-server/README.md library-frontend/README.md
rg -n 'Not reviewed|需求基线.*Not established' docs/01-requirements/reviews/REQUIREMENTS-REVIEW-CHECKLIST.md docs/01-requirements/reviews/REQUIREMENTS-BASELINE-RECORD.md
```

Expected: 至少一个 README 仍含旧权威或当前实现契约表述，或子项目 README 尚未指向新权威链；评审文件仍为未评审/未建立状态。

- [ ] **Step 2: 使用 `apply_patch` 收敛根与两个子项目 README（2–5 分钟）**

三份 README 必须共享以下事实且只在根 README 维护完整导航：

- 项目正在按 V 模型从需求分析重新建立基线。
- `docs/README.md` 是工程文档唯一入口。
- `docs/01-requirements/REQUIREMENTS-STAGE-DESIGN.md v1.0` 是阶段组织方案，不是最终需求基线。
- 当前代码是直接复用、重构复用或弃用重写的候选，不是新需求来源。
- 旧契约、旧计划和旧审计只在 `docs/90-archive/` 作为历史参考。
- 当前需求基线为 `Not established`，验收状态为“尚未执行”。

根 README 只保留项目目的、当前工程阶段、文档入口、前后端入口和“如何参与需求评审”；删除返工前 API 列表、阶段完成表、旧权威文件列表和未经本轮验证的实现计数。

后端与前端 README 保留各自开发说明，但在首段加入新权威链和复用候选声明；旧接口/字段现状只能放在“历史实现风险”中并链接 `CODE-REUSE-INVENTORY.md`，不能称为当前需求契约。

Expected: 三份 README 对项目阶段、权威入口、代码地位和基线状态完全一致，不重复维护需求事实。

- [ ] **Step 3: 验证 Git ignore/track、状态头和历史头（2–5 分钟）**

Run:

```powershell
$mustNotIgnore = @('README.md','docs/README.md','docs/01-requirements/SYSTEM-REQUIREMENTS.md')
foreach ($path in $mustNotIgnore) {
  git check-ignore -q -- $path
  if ($LASTEXITCODE -eq 0) { throw "$path is ignored" }
}
$mustTrack = @('README.md','docs/README.md','docs/01-requirements/REQUIREMENTS-STAGE-DESIGN.md','docs/01-requirements/SYSTEM-REQUIREMENTS.md')
foreach ($path in $mustTrack) { git ls-files --error-unmatch -- $path | Out-Null }
$activeDocs = @(Get-ChildItem docs/00-governance,docs/01-requirements,docs/02-overall-design,docs/03-detailed-design,docs/04-module-design -Recurse -File -Filter '*.md' | Where-Object { $_.Name -ne 'REQUIREMENTS-DOCUMENTATION-IMPLEMENTATION-PLAN.md' })
$missingHeader = @($activeDocs | Where-Object { -not (Select-String -LiteralPath $_.FullName -Quiet -Pattern '^\| 文档状态 \|') })
if ($missingHeader.Count -ne 0) { throw "Active doc missing status header: $($missingHeader.FullName -join ', ')" }
$archiveDocs = @(Get-ChildItem docs/90-archive -Recurse -File -Filter '*.md' | Where-Object { $_.FullName -ne (Resolve-Path docs/90-archive/README.md).Path })
$missingArchiveHeader = @($archiveDocs | Where-Object { -not (Select-String -LiteralPath $_.FullName -Quiet -Pattern '^> \*\*Archive Status:\*\* Historical Reference') })
if ($missingArchiveHeader.Count -ne 0) { throw "Archive doc missing historical header: $($missingArchiveHeader.FullName -join ', ')" }
```

Expected: 权威入口均未被忽略且已跟踪；所有当前文档有状态头；所有归档原文有历史头。

- [ ] **Step 4: 验证本地 Markdown 链接和 ID 定义唯一性（2–5 分钟）**

Run:

```powershell
$workspace = (Resolve-Path '.').Path
$markdown = @(
  (Get-Item README.md,library-server/README.md,library-frontend/README.md,docs/README.md,docs/90-archive/README.md)
  (Get-ChildItem docs/00-governance,docs/01-requirements,docs/02-overall-design,docs/03-detailed-design,docs/04-module-design -Recurse -File -Filter '*.md')
) | ForEach-Object { $_ } | Sort-Object FullName -Unique
$broken = foreach ($file in $markdown) {
  $content = Get-Content -LiteralPath $file.FullName -Raw
  foreach ($match in [regex]::Matches($content, '\[[^\]]+\]\((?!https?://|mailto:|#)([^)#]+)(?:#[^)]*)?\)')) {
    $raw = [Uri]::UnescapeDataString($match.Groups[1].Value.Trim('<','>'))
    $candidate = if ([IO.Path]::IsPathRooted($raw)) { Join-Path $workspace $raw.TrimStart('/','\') } else { Join-Path $file.DirectoryName $raw }
    if (-not (Test-Path -LiteralPath ([IO.Path]::GetFullPath($candidate)))) { [pscustomobject]@{ File=$file.FullName; Link=$raw } }
  }
}
if (@($broken).Count -ne 0) { throw "Broken local links: $($broken | ConvertTo-Json -Compress)" }
$definitionPattern = '^\*\*ID:\*\* `(?<id>MLS-(?:OBJ|ACT|UC|FR|BR|IR|NFR-[A-Z]+|CON|AC|AT|OQ)-[0-9]{4})`'
$definitions = foreach ($file in $markdown) {
  Select-String -LiteralPath $file.FullName -Pattern $definitionPattern | ForEach-Object { $_.Matches[0].Groups['id'].Value }
}
$duplicates = @($definitions | Group-Object | Where-Object Count -gt 1)
if ($duplicates.Count -ne 0) { throw "Duplicate canonical ID definitions: $($duplicates.Name -join ', ')" }
```

Expected: 本地链接全部解析到现有路径；每个规范 ID 只有一个 `**ID:**` 定义，追踪引用不会被误判为重复定义。

- [ ] **Step 5: 验证追踪完整性、设计泄漏和支付安全约束（2–5 分钟）**

Run:

```powershell
$matrix = Get-Content docs/01-requirements/requirements/TRACEABILITY-MATRIX.md -Raw
$requiredIds = @()
$requiredIds += @(rg -o 'MLS-OBJ-[0-9]{4}' docs/01-requirements/requirements/VISION-AND-SCOPE.md | Sort-Object -Unique)
$requiredIds += @(rg -o 'MLS-UC-[0-9]{4}' docs/01-requirements/requirements/USE-CASES.md | Sort-Object -Unique)
$requiredIds += @(rg -o 'MLS-FR-[0-9]{4}' docs/01-requirements/requirements/FUNCTIONAL-REQUIREMENTS.md | Sort-Object -Unique)
$requiredIds += @(rg -o 'MLS-BR-[0-9]{4}' docs/01-requirements/requirements/BUSINESS-RULES.md | Sort-Object -Unique)
$requiredIds += @(rg -o 'MLS-IR-[0-9]{4}' docs/01-requirements/requirements/INFORMATION-REQUIREMENTS.md | Sort-Object -Unique)
$requiredIds += @(rg -o 'MLS-NFR-[A-Z]+-[0-9]{4}' docs/01-requirements/requirements/NONFUNCTIONAL-REQUIREMENTS.md | Sort-Object -Unique)
$requiredIds += @(rg -o 'MLS-AC-[0-9]{4}' docs/01-requirements/acceptance/ACCEPTANCE-CASES.md | Sort-Object -Unique)
$requiredIds += @(rg -o 'MLS-AT-[0-9]{4}' docs/01-requirements/acceptance/ACCEPTANCE-CASES.md | Sort-Object -Unique)
$requiredIds = @($requiredIds | Sort-Object -Unique)
$missingTrace = @($requiredIds | Where-Object { $matrix -notmatch [regex]::Escape($_) })
if ($missingTrace.Count -ne 0) { throw "IDs missing from traceability matrix: $($missingTrace -join ', ')" }
$normative = @('docs/01-requirements/SYSTEM-REQUIREMENTS.md') + @(Get-ChildItem docs/01-requirements/requirements,docs/01-requirements/acceptance -File -Filter '*.md' | ForEach-Object FullName)
$leaks = @(rg -n '@RestController|\bController\b|\bService\b|\bRepository\b|CREATE TABLE|VARCHAR|/api/|Spring Boot|Vue 3|MySQL|Axios|Vite' $normative)
if ($LASTEXITCODE -eq 0) { throw "Design leakage found: $($leaks -join '; ')" }
$realPaymentPatterns = 'api\.paypal|stripe\.com|openapi\.alipay|pay\.weixin|merchant_secret|client_secret|private_key'
$realMatches = @(rg -n $realPaymentPatterns $normative)
if ($LASTEXITCODE -eq 0) { throw "Real payment domain or credential pattern found: $($realMatches -join '; ')" }
rg -n '完全本地|不得向真实支付|不得保存真实|模拟支付，无真实资金交易|不产生真实资金交易' $normative
```

Expected: 所有目标、用例、需求、AC 和 AT 均出现在矩阵；设计泄漏和真实支付域名/凭据扫描无匹配；本地模拟与无真实资金的正向约束有明确命中。

- [ ] **Step 6: 执行 G0–G5 评审并使用 `apply_patch` 记录实际结论（2–5 分钟）**

按验证证据更新两个评审文件：

- `REQUIREMENTS-REVIEW-CHECKLIST.md`：每项写执行命令、日期、结果和证据路径；只把实际通过的检查标为 `Passed`。
- `REQUIREMENTS-BASELINE-RECORD.md`：在 15 个 Open 问题仍为 `Open` 且需求/验收工件仍为 `Draft` 的本计划初次执行状态下，记录 `G0 Passed`、`G1 Passed`、`G2 Blocked`、`G3 Blocked`、`G4 Blocked`、`G5 Blocked`；需求基线继续为 `Not established`，验收继续为“尚未执行”。

阻塞依据固定为：`MLS-OQ-0001` 至 `MLS-OQ-0009` 阻塞完整业务规则和可执行边界；`MLS-OQ-0010` 至 `MLS-OQ-0014` 阻塞量化质量与验收门禁；所有派生需求仍需逐条评审。`MLS-OQ-0015` 作为语言一致性决策继续登记，不伪造关闭结论。

Expected: 评审记录诚实反映已完成的文档工程和未完成的需求批准；不会发布最终需求基线 `v1.0`。

- [ ] **Step 7: 运行最终格式和工作区检查（2–5 分钟）**

Run:

```powershell
git diff --check
rg -n 'docs/README\.md|需求基线.*Not established|验收.*尚未执行|代码.*复用候选' README.md library-server/README.md library-frontend/README.md
rg -n 'G0.*Passed|G1.*Passed|G2.*Blocked|G3.*Blocked|G4.*Blocked|G5.*Blocked' docs/01-requirements/reviews/REQUIREMENTS-BASELINE-RECORD.md
git status --short
```

Expected: `git diff --check` 无输出；三份 README 均指向新权威并说明基线/验收状态；基线记录准确显示 G0/G1 通过、G2–G5 阻塞；状态仅包含本任务五个待提交文件。

- [ ] **Step 8: 只提交 README 收敛和基线评审（2–5 分钟）**

Run:

```powershell
git add -- README.md library-server/README.md library-frontend/README.md docs/01-requirements/reviews/REQUIREMENTS-REVIEW-CHECKLIST.md docs/01-requirements/reviews/REQUIREMENTS-BASELINE-RECORD.md
$cached = @(git diff --cached --name-only)
$expected = @('README.md','library-server/README.md','library-frontend/README.md','docs/01-requirements/reviews/REQUIREMENTS-REVIEW-CHECKLIST.md','docs/01-requirements/reviews/REQUIREMENTS-BASELINE-RECORD.md')
if (@(Compare-Object ($cached | Sort-Object) ($expected | Sort-Object)).Count -ne 0) { throw "Unexpected staged scope: $($cached -join ', ')" }
git diff --cached --check
git commit -m "docs: converge requirements documentation authority"
git status --short
```

Expected: 独立提交只包含五个文件；提交成功后工作区干净。需求基线仍为 `Not established`，下一项工作是逐项关闭 Open 问题并重新执行 G2–G5。

---

## Execution Handoff

Plan complete and saved to `docs/01-requirements/REQUIREMENTS-DOCUMENTATION-IMPLEMENTATION-PLAN.md`. Two execution options:

1. **Subagent-Driven (recommended)** — 使用 `superpowers:subagent-driven-development`，每个任务由新的子代理执行，并在任务间进行规格与质量复核。
2. **Inline Execution** — 使用 `superpowers:executing-plans`，在当前会话按批次执行，并在检查点等待复核。

无论采用哪种方式，都必须按 Task 1 至 Task 8 顺序执行；每个任务独立验证、独立提交，且不得修改产品代码。
