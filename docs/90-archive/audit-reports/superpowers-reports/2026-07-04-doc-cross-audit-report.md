> **Archive Status:** Historical Reference
> **Original Path:** `docs/superpowers/reports/2026-07-04-doc-cross-audit-report.md`
> **Archived On:** `2026-07-13`
> **Historical Purpose:** 返工前审计证据
> **Superseded By:** `docs/README.md`
> **Authority:** 非规范性；不得作为当前需求、接口、数据或验收契约

# 文档内部交叉审计报告

日期：2026-07-04

## 审计边界

本报告为第一阶段审计结果，仅基于文档之间的互相对照，不读取、不引用任何代码实现。

审计文档：

- `README.md`
- `library-server/README.md`
- `library-frontend/README.md`
- `docs/API-SPEC.md`
- `docs/ARCHITECTURE.md`
- `docs/DATA-MODEL.md`
- `docs/test/TEST-CASES.md`
- `docs/test/TEST-CASES-BOUNDARY.md`
- `docs/superpowers/specs/2026-06-15-p2-entity-repository-jwt-design.md`
- `docs/superpowers/specs/2026-06-16-p3-book-category-auth-controller-design.md`
- `docs/superpowers/specs/2026-06-23-p4-borrow-return-renewal-penalty-design.md`

## 高优先级冲突

### 1. 阶段状态不一致

- 根 README 与后端 README 声称 P4 已完成，Borrow/Return/Renewal/Penalty API live。
- `docs/API-SPEC.md` 顶部和实现状态表仍写 P3 complete，P4/P5 planned。
- `docs/ARCHITECTURE.md` 后端结构仍写 Phase 3 complete，Borrow/Return/Renewal/Penalty planned。
- `library-frontend/README.md` 写 Backend P3 live，P4-P5 planned。
- P4 spec 写 P4 为 Draft，但根 README 又将 P4 标为 Complete。

影响：读者无法判断当前权威状态；前端迁移范围、测试范围、接口可用性都被这个状态冲突影响。

建议：设立单一状态口径：

- P1-P4：Backend implemented/current。
- P5：News/Messages/Upload/Dashboard 仍 planned 或 partial，需后续由代码核实。
- Frontend：integration partial/pending。

### 2. Auth 路径设计冲突

- P2/P3 spec 明确统一登录注册为 `/api/auth/login`、`/api/auth/register`。
- `docs/API-SPEC.md` 第 2、3 节仍详细描述 `/api/users/login`、`/api/readers/login`、`/api/readers/register`。
- `docs/API-SPEC.md` 又在同一表格注释中承认 Auth 实际使用 unified `/auth/*`，造成文档自相矛盾。
- 测试用例 AUTH-01/AUTH-02/AUTH-04 仍引用 `ReaderController.java`、`UserController.java`，与统一 `AuthController` 冲突。

影响：前端和测试可能继续按旧 users/readers 登录接口适配，导致认证集成错误。

建议：API-SPEC 保留 `/users/*`、`/readers/*` 为旧路径映射或管理 CRUD 规划，不再作为登录注册规范；认证测试涉及文件统一改为 `AuthController.java`。

### 3. P4 数据模型与 DATA-MODEL 冲突

- P4 spec 明确 Return/Renewal/Penalty 均采用严格 3NF，只保存 `borrowId`，通过 Borrow 链路关联 Reader/Book。
- `docs/DATA-MODEL.md` 的 ER 图和实体表仍写：
  - BorrowReturn 直接含 `bookId`、`bookIsbn`、`bookTitle`、`studentId`、`readerName`。
  - Renewal 直接含 `bookId`、`bookIsbn`、`bookTitle`、`studentId`、`readerName`。
  - Penalty 直接含 `bookId`、`bookIsbn`、`bookTitle`、`studentId`、`readerName`。
- `docs/API-SPEC.md` 罚金字段也仍使用旧快照字段和拼音字段。

影响：数据建模、DTO、前端字段映射和测试断言会出现方向性错误。

建议：DATA-MODEL 应以 P4 spec 的 `borrowId` FK 链为准；响应 DTO 可包含 JOIN 后的展示字段，但实体表不应再记录快照字段。

### 4. P4 API 请求体与 API-SPEC 冲突

- P4 spec 定义：
  - BorrowRequest: `{ bookId, readerId?, durationDays? }`
  - ReturnRequest: `{ borrowId }`
  - RenewalRequest: `{ borrowId, durationDays? }`
  - BatchApproval: `{ ids, approved, reply }`
  - PayRequest: `{ id }`
- `docs/API-SPEC.md` 第 6-9 节仍写旧拼音请求体：
  - borrow add 使用 `{ jieyueren, xueshengxuehao, tushubianhao, tushumingcheng }`
  - return/renewal add 使用图书和读者快照字段
  - shBatch 使用 query params `sfsh/shhf` + body `[id1, id2]`
  - penalty 没有正式列出 `/penalties/pay`，仍写 save/update/delete 管理接口。

影响：前端 P4 模块如果按 API-SPEC 实现，会与 P4 spec 的 REST DTO 不兼容。

建议：API-SPEC 第 6-9 节以 P4 spec 为准重写请求/响应示例。

### 5. P5 模块状态混淆

- 根 README Features 中列出 Announcements、Dashboard、Messages，容易读成当前可用功能。
- 根 README API Overview 又明确 `/news/*`、`/messages/*`、`/upload/*`、`/dashboard/*` planned。
- TEST-CASES 将 NEWS/MSG/UPL/DASH 纳入 35 个验收用例，没有标注这些用例依赖 P5。
- DATA-MODEL 已完整列出 News、Message、LibraryCard，和 P2/P3/P4 已实现实体范围没有明显区分。

影响：项目当前功能范围不清，验收时可能把 planned 模块当作当前失败项。

建议：所有 P5 模块统一标注 `Planned / not current backend contract`，测试用例按 phase 分组或加状态列。

## 中优先级冲突

### 6. 密码传输策略冲突

- P3 spec 和 `library-frontend/README.md` 设计为前端 SHA256 + 后端 BCrypt。
- `library-frontend/README.md` 又写当前状态为前端发送 raw password，后端 BCrypt handles correctly。
- P2 spec 的 Login/Register flow 只写 BCrypt，不要求 SHA256。

影响：如果前端按 SHA256 发送但后端数据按 raw password BCrypt 建立，会造成登录不兼容；反之亦然。

建议：明确当前实现状态和目标状态分离：当前为 raw password over dev HTTP + backend BCrypt；SHA256 prehash 如未落地，应标注为 future enhancement，不应写成 MUST。

### 7. 测试规划总量与 P4 新增测试不一致

- `docs/test/TEST-CASES.md` 汇总 35 个基础用例。
- P4 spec 在原 BRW/RET/RNW/PEN 基础上新增 12 个 P4 测试，并把 P4 子集更新为 25 个。
- `docs/test/TEST-CASES.md` 未合并 P4 新增测试，也未体现 P4 后实际需要覆盖的重复归还、重复借阅、续借过滤等问题。
- `docs/test/TEST-CASES-BOUNDARY.md` 文件存在但内容为空。

影响：测试规划与最新设计脱节，无法作为实现质量门禁。

建议：将 `TEST-CASES.md` 加状态列和 phase 标签；将 P4 新增测试合并到基础/扩展用例；补全 `TEST-CASES-BOUNDARY.md` 或标明尚未编写。

### 8. README 结构统计和状态可能过时

- 根 README 项目结构写后端为 5 entities、5 repositories、3 services、3 controllers、7 DTOs，但同文又写 P4 done 和 P4 增加了 3 entities、3 repos、4 controllers、4 services、9 DTOs。
- 后端 README 写 7 controllers、7 services、19 tests；与根 README 的结构统计冲突。
- 根 README Frontend 部分写 API modules still old pinyin paths、custom token header，而前端 README 也写这些为当前问题；这些状态在后续代码探索后需要核实。

影响：README 作为入口文档，当前统计会误导代码导航和项目进度判断。

建议：根 README 改为概览，不维护容易过时的精确数量；具体数量由后端/前端 README 或自动生成状态表维护。

### 9. API-SPEC 的 old->new path mapping 与正文冲突

- Appendix 映射 `/users/* -> /api/users/*`、`/duzhe/* -> /api/readers/*`。
- 但 P2/P3 spec 表明登录注册应迁移到 `/api/auth/*`，不是 `/api/users/login` 和 `/api/readers/login`。
- API-SPEC 顶部注释承认 Auth 使用 `/auth/*`，但正文未同步。

影响：路径映射既承担旧路径迁移说明，又承担新 API 契约，两种职责混在一起。

建议：拆成两列：`Legacy path`、`Current endpoint`、`Notes`；Auth legacy 登录全部映射到 `/api/auth/login`。

## 低优先级问题

### 10. 文档编码/字符显示损坏

大量中文、箭头、勾叉符号显示为 mojibake，例如 `鈥?`、`鈫?`、`鈭?`、`寰呭鏍?`。

影响：中文业务语义、枚举值和错误信息不可读，尤其影响 `ApprovalStatus`、`PaymentStatus`、错误消息、测试说明。

建议：统一文档为 UTF-8，无 BOM；后续可逐步修复关键 API 和枚举文本，避免一次性大规模噪声改动。

### 11. LibraryCard 状态不清

- DATA-MODEL 有 LibraryCard 实体和关系。
- 其他 README/API-SPEC/历史 specs 没有对应阶段和 API。

影响：读者无法判断 LibraryCard 是遗留模型、未来功能还是遗漏模块。

建议：标注为 `Legacy/Future, not in current API scope`，或移至附录。

### 12. Docker Compose 状态需要澄清

- 根 README 和 ARCHITECTURE 提到 Docker Compose one-command startup。
- Phase Progress 又将 Docker + Tests + Docs 标为 P6 pending。

影响：部署能力是否可用不明确。

建议：若 P6 未完成，则 README 顶部不要宣称 one-command startup 为当前能力；可改为 planned deployment target。

## 建议的文档权威顺序

在未读取代码前，建议暂定如下权威层级：

1. 已批准且阶段最新的 spec 对应其模块内部设计：P2 -> P3 -> P4。
2. API-SPEC 应作为当前 API 契约，但当前明显落后，需要在第二阶段按代码实际状态修订。
3. DATA-MODEL 应作为实体约束，但 P4 部分明显落后于 P4 spec。
4. README 只作为项目入口，应减少精确实现统计。
5. TEST-CASES 应作为验收规划，但必须补充 phase/status，不应混淆 planned 与 current。

## 第二阶段待代码核实项

以下项必须在本报告之后使用 CodeGraph 核实：

- 后端当前实际 controller/service/entity/repository/DTO 数量和 API 路径。
- `/auth/*`、`/users/*`、`/readers/*` 的实际存在关系。
- P4 Return/Renewal/Penalty 实体是否确实只保留 `borrowId`。
- P4 批量审批和罚金支付实际请求体。
- News/Message/Upload/Dashboard 是否存在后端实现。
- 前端当前实际是否仍使用旧路径、旧 token header、旧 proxy rewrite。
- 测试代码当前覆盖数量和重点是否已经超过文档规划。
