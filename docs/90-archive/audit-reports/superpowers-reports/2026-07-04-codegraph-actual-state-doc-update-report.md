> **Archive Status:** Historical Reference
> **Original Path:** `docs/superpowers/reports/2026-07-04-codegraph-actual-state-doc-update-report.md`
> **Archived On:** `2026-07-13`
> **Historical Purpose:** 返工前审计证据
> **Superseded By:** `docs/README.md`
> **Authority:** 非规范性；不得作为当前需求、接口、数据或验收契约

# CodeGraph 实际状态与文档更新报告

日期：2026-07-04

## 阶段说明

本报告为第二阶段结果。第一阶段已先生成仅基于文档的交叉审计报告：

- `docs/superpowers/reports/2026-07-04-doc-cross-audit-report.md`

第二阶段在该报告之后使用 CodeGraph 探索代码实际状态，并据此更新文档中的当前状态标注。

## CodeGraph 核实到的后端实际状态

### 当前实现的 Controller

- `AuthController`
- `BookController`
- `CategoryController`
- `BorrowController`
- `ReturnController`
- `RenewalController`
- `PenaltyController`

未在当前 `com.library` 后端中发现：

- `NewsController`
- `MessageController`
- `FileController` / `UploadController`
- `DashboardController`

### 当前实现的 API 组

- `/api/auth/*`
- `/api/books/*`
- `/api/categories/*`
- `/api/borrows/*`
- `/api/returns/*`
- `/api/renewals/*`
- `/api/penalties/*`

P5 规划组仍未实现：

- `/api/news/*`
- `/api/messages/*`
- `/api/upload/*`
- `/api/dashboard/*`

### 当前 DTO 请求体

- 登录：`{ username, password }`
- 注册：`{ studentId, name, password, gender, department, birthDate, phone }`
- 借阅申请：`{ bookId, readerId?, durationDays? }`
- 归还申请：`{ borrowId }`
- 续借申请：`{ borrowId, durationDays? }`
- 批量审核：`{ ids, approved, reply }`
- 罚金支付：`{ id }`

### 当前数据模型

当前实现实体为 8 个：

- `User`
- `Reader`
- `Book`
- `Category`
- `Borrow`
- `BorrowReturn`
- `Renewal`
- `Penalty`

P4 实体实际遵循 strict 3NF：

- `BorrowReturn` 只保存 `borrowId`、`returnDate`、`approved`、`reply`、`addtime` 等字段。
- `Renewal` 只保存 `borrowId`、`durationDays`、`renewalDate`、`approved`、`reply`、`addtime` 等字段。
- `Penalty` 只保存 `fineNo`、`borrowId`、`amount`、`fineDate`、`paid`、`addtime` 等字段。

展示字段如 `readerName`、`studentId`、`bookTitle`、`bookIsbn` 存在于 response DTO 中，不是实体快照字段。

### 当前安全配置

- `/auth/login`、`/auth/register` public。
- `/books/page`、`/books/detail/**` public。
- `/categories/list` public。
- P4 查询接口 authenticated。
- P4 审核/删除接口 admin-only。
- `/penalties/pay` reader-only。

### 当前密码契约

当前后端实际实现为 raw request password + backend BCrypt：

- 登录使用 `passwordEncoder.matches(request.getPassword(), user.getPassword())`。
- 注册使用 `passwordEncoder.encode(request.getPassword())`。

文档中“frontend SHA256 + backend BCrypt”应视为未来设计，不是当前实现契约。

## CodeGraph/文件核实到的前端实际状态

- `vite.config.js` 已将 `/api` 代理到 `http://localhost:8080`，没有旧 `/springboot8393c` rewrite。
- `src/utils/request.js` 已使用 `import.meta.env.VITE_API_BASE_URL || '/api'`，并发送 `Authorization: Bearer <token>`。
- `src/api/user.js`、`src/api/reader.js` 的登录/注册基础包装已迁移到 `/auth/login` 和 `/auth/register`。
- `LoginView.vue` 仍按旧形状读取 `res.token` / `res.user`，与后端 `ApiResponse<LoginResponse>` 的 `res.data.token` 不一致。
- `book.js`、`category.js`、`borrow.js`、`return.js`、`renewal.js`、`penalty.js`、`home.js`、`file.js` 仍有旧 pinyin 路径或旧字段。
- `MyFines.vue` 仍使用旧罚金 update 支付流程，不是 `POST /penalties/pay`。

## 测试实际状态

- 后端测试源码当前包含 22 个 `@Test` 方法。
- 最近一次后端全量测试结果：`Tests run: 22, Failures: 0, Errors: 0, Skipped: 0`。
- 前端构建未验证；当前依赖缺失导致 `vite` 不可用。

## 本次更新的文档

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

更新策略：

- 保留历史设计文档原貌。
- 在文档显眼位置增加 `CodeGraph verified, 2026-07-04` 当前状态块。
- 对无法安全替换的 mojibake/旧表格不做大规模重写，避免引入无关格式噪声。

## 剩余文档债务

- 全面修复文档 mojibake 编码问题。
- 重写 `docs/API-SPEC.md` 第 2-9 节，移除旧登录和 P4 拼音请求体示例。
- 重写 `docs/DATA-MODEL.md` ER 图，删除 P4 实体快照字段并区分 planned/legacy 实体。
- 将 P4 spec 新增测试和近期修复测试正式合并到 `docs/test/TEST-CASES.md`。
- 前端恢复修改后，再同步更新 `library-frontend/README.md` 的迁移 checklist。
