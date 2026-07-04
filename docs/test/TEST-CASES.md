# TEST-CASES — 系统验收测试用例（基本测试项）

> **编写原则：** 基于等价类划分（Equivalence Class Partitioning），每个等价类保留一个代表性用例。
> **覆盖范围：** 全部功能模块的有效等价类 + 无效等价类。
> **标注说明：** 无效等价类的错误码/提示信息留空，待详细设计阶段补充。边界值测试将在重建完成后单独编写。
>
> 关联文档：[ARCHITECTURE.md](../ARCHITECTURE.md) · [API-SPEC.md](../API-SPEC.md) · [DATA-MODEL.md](../DATA-MODEL.md)

---

## （一）图书管理模块

### BOOK-01 — 新增图书（有效等价类）

- **测试场景：** 管理员录入新书，所有字段填写完整且合法。
- **测试数据：**
  - isbn: `978-7-302-11111`
  - title: `测试图书`
  - author: `测试作者`
  - publisher: `测试出版社`
  - year: `2023`
  - stock: `5`
  - categoryId: `1`
  - description: `测试描述`
- **预期结果：** 新增成功（code=0），数据库中可查询到该记录。
- **涉及文件：** `BookManagement.vue` → `BookController.java` → `BookService.java`

### BOOK-02 — 分页条件查询（有效等价类）

- **测试场景：** 按书名/作者/类别组合条件分页查询图书列表。
- **测试数据：** page=1, limit=10, title=`测试`, author=`测试作者`
- **预期结果：** 返回 `{ list: [...], total: N }`，list 中每条包含 isbn/title/author/publisher/stock/categoryName。
- **涉及文件：** `BookBrowsing.vue` / `BookManagement.vue` → `BookController.java`

### BOOK-03 — 修改图书信息（有效等价类）

- **测试场景：** 管理员修改已有图书的库存、描述等字段。
- **测试数据：** 图书 ID 存在，stock 由 5 → 10，description 变更。
- **预期结果：** 更新成功（code=0），再次查询时字段已变更。
- **涉及文件：** `BookManagement.vue` → `BookController.java`

### BOOK-04 — 删除无关联图书（有效等价类）

- **测试场景：** 管理员删除一本无关联借阅记录的图书。
- **测试数据：** 图书 ID 存在，无 borrow/return/renewal 关联记录。
- **预期结果：** 删除成功（code=0）。
- **涉及文件：** `BookManagement.vue` → `BookController.java`

### BOOK-05 — 输入数据校验失败（无效等价类）

- **测试场景：** 新增/修改时关键字段为空、格式非法、或唯一约束冲突。
- **测试数据（任一即触发）：**
  - (a) title 为空
  - (b) isbn 与已有记录重复
  - (c) stock 为负数
- **预期结果：** 操作被拒绝（code=1），返回错误提示（具体提示信息待后续设计）。
- **涉及文件：** `BookController.java` → `BookService.java`

---

## （二）图书类别模块

### CAT-01 — 新增/修改类别（有效等价类）

- **测试场景：** 管理员新增类别或修改已有类别名称。
- **测试数据：** name=`计算机科学`
- **预期结果：** 保存成功（code=0），类别列表中出现/更新该记录。
- **涉及文件：** `CategoryManagement.vue` → `CategoryController.java`

### CAT-02 — 获取类别列表（有效等价类）

- **测试场景：** 前端获取全部类别作为下拉选项（如新增图书时选择类别）。
- **测试数据：** 无特殊参数。
- **预期结果：** 返回全部类别 `[{id, name}, ...]`。
- **涉及文件：** `category.js` (API) → `CategoryController.java`

### CAT-03 — 删除被引用的类别（无效等价类）

- **测试场景：** 管理员尝试删除一个仍有图书归属的类别——测试引用完整性保护。
- **测试数据：** 类别 ID 存在，且有 `books.categoryId` 引用。
- **预期结果：** 操作被拒绝（code=1），返回错误提示（具体提示信息待后续设计）。
- **涉及文件：** `CategoryManagement.vue` → `CategoryController.java`

---

## （三）图书借阅模块

### BRW-01 — 正常提交借阅申请（有效等价类）

- **测试场景：** 读者满足所有借阅条件（无未缴罚款、未达借阅上限、图书有库存），提交借阅申请。
- **测试数据：**
  - reader: studentId=`2021001`, 未达借阅上限, 无未缴罚款
  - book: isbn=`9787111111111`, stock > 0
- **预期结果：** 申请插入成功，approved=`否`，进入"待审核"状态。
- **涉及文件：** `BookBrowsing.vue` → `BorrowController.java`

### BRW-02 — 借阅申请被拒（无效等价类）

- **测试场景：** 读者不满足借阅资格时提交申请——涵盖库存不足、存在未缴罚款、已达借阅上限三类业务阻断（同一等价类：资格不足）。
- **测试数据（任一即触发）：**
  - (a) 图书 stock = 0
  - (b) 读者存在 paid=否 的罚款记录
  - (c) 读者已借数量 ≥ 系统限额
- **预期结果：** 操作被拒绝（code=1），未插入记录（错误提示信息待后续设计）。
- **涉及文件：** `BorrowController.java` → `BorrowService.java`

### BRW-03 — 管理员审核通过（有效等价类）

- **测试场景：** 管理员对"待审核"借阅申请执行"审核通过"操作，验证库存联动扣减。
- **测试数据：** 借阅 ID 存在且 approved=`否`；审核操作 approved=`是`。
- **预期结果：** 借阅记录 approved=`是`；图书 stock 减 1。
- **涉及文件：** `BorrowApproval.vue` → `BorrowController.java`

### BRW-04 — 管理员审核驳回（有效等价类）

- **测试场景：** 管理员驳回借阅申请，验证库存不变。
- **测试数据：** 借阅 ID 存在且 approved=`否`；审核操作 approved=`否`，reply=`图书已预约`。
- **预期结果：** 借阅记录 approved=`否`（带驳回回复）；图书 stock 不变。
- **涉及文件：** `BorrowApproval.vue` → `BorrowController.java`

---

## （四）图书归还模块

### RET-01 — 正常归还（有效等价类，未超期 → 无罚款）

- **测试场景：** 读者在预还日期前归还图书，验证库存恢复且不产生罚款。
- **测试数据：** 借阅记录存在，实际还书日期 ≤ 预还日期。
- **预期结果：** 归还记录更新；图书 stock 加 1；无罚款记录生成。
- **涉及文件：** `ReturnApproval.vue` → `ReturnController.java`

### RET-02 — 超期归还（有效等价类，超期 → 自动生成罚款）

- **测试场景：** 读者超期归还，系统自动按超期天数 × 单价计算罚款。
- **测试数据：** 借阅记录存在，实际还书日期 > 预还日期（超期 7 天），超时单价 1 元/天。
- **预期结果：** 归还记录更新；stock 加 1；自动生成罚款记录（amount=7 元，paid=否）。
- **涉及文件：** `ReturnApproval.vue` → `ReturnController.java` → `PenaltyService.java`

### RET-03 — 归还不存在的借阅记录（无效等价类）

- **测试场景：** 提交归还申请时借阅 ID 不存在。
- **测试数据：** 借阅 ID = `9999999999`（不存在）。
- **预期结果：** 操作被拒绝（code=1），无数据变更（错误提示信息待后续设计）。
- **涉及文件：** `ReturnController.java`

---

## （五）图书续借模块

### RNW-01 — 正常提交续借申请（有效等价类）

- **测试场景：** 读者对正在借阅中、尚未续借过的图书记录提交续借申请。
- **测试数据：** 借阅记录存在且 approved=`是`，该记录无关联续借记录。
- **预期结果：** 续借申请插入成功，approved=`否`。
- **涉及文件：** `MyBorrowing.vue` → `RenewalController.java`

### RNW-02 — 管理员审核续借（有效等价类，通过/驳回共用同一端点）

- **测试场景：** 管理员对续借申请进行审核（通过或驳回）。
- **测试数据：** 续借 ID 存在；审核操作 (a) approved=`是` 或 (b) approved=`否`。
- **预期结果：** 续借记录状态更新；若通过，对应借阅记录的预还日期延长。
- **涉及文件：** `RenewalApproval.vue` → `RenewalController.java`

### RNW-03 — 重复续借（无效等价类）

- **测试场景：** 读者对同一借阅记录再次提交续借申请（已有一条 approved=是 的续借记录）。
- **测试数据：** 借阅记录已关联续借记录且 approved=`是`。
- **预期结果：** 操作被拒绝（code=1），错误提示信息待后续设计。
- **涉及文件：** `RenewalController.java`

---

## （六）罚款管理模块

### PEN-01 — 按读者查询罚款（有效等价类，合并未缴/已缴/全部查询）

- **测试场景：** 读者查看自己的罚款记录，支持按 paid 状态筛选；管理员查看全部。
- **测试数据：** studentId=`2021001`；可选过滤 paid=`否`/`是`/无。
- **预期结果：** 返回符合条件的罚款列表 `[{id, fineNo, bookTitle, amount, fineDate, paid}, ...]`。
- **涉及文件：** `MyFines.vue` / `PenaltyManagement.vue` → `PenaltyController.java`

### PEN-02 — 支付罚款（有效等价类）

- **测试场景：** 读者对一笔未支付罚款发起在线支付。
- **测试数据：** 罚款 ID 存在且 paid=`否`。
- **预期结果：** paid 更新为 `是`。
- **涉及文件：** `MyFines.vue` → `PenaltyController.java`

### PEN-03 — 重复支付（无效等价类）

- **测试场景：** 对已支付的罚款再次发起支付。
- **测试数据：** 罚款 ID 存在且 paid=`是`（已支付）。
- **预期结果：** 操作被拒绝（code=1），无状态变更（错误提示信息待后续设计）。
- **涉及文件：** `PenaltyController.java`

---

## （七）用户认证模块

### AUTH-01 — 读者正常登录（有效等价类）

- **测试场景：** 已注册读者使用正确的学号和密码登录。
- **测试数据：** studentId=`2021001`, password=`123456`
- **预期结果：** 登录成功（code=0），返回 JWT Token，role=`reader`。
- **涉及文件：** `LoginView.vue` → `ReaderController.java`

### AUTH-02 — 管理员正常登录（有效等价类）

- **测试场景：** 管理员使用预置账号登录。
- **测试数据：** username=`admin`, password=`admin`
- **预期结果：** 登录成功（code=0），返回 JWT Token，role=`admin`。
- **涉及文件：** `LoginView.vue` → `UserController.java`

### AUTH-03 — 登录凭据错误（无效等价类，含密码错误/账号不存在）

- **测试场景：** 密码错误或账号不存在——认证失败的同一等价类。
- **测试数据（任一即触发）：**
  - (a) 学号存在，密码错误（如 `654321`）
  - (b) 学号/用户名不存在（如 studentId=`2021099`）
- **预期结果：** 登录失败（code=1），返回错误提示（具体提示信息待后续设计）。
- **涉及文件：** `ReaderController.java` / `UserController.java`

### AUTH-04 — 读者注册（有效等价类）

- **测试场景：** 新读者使用未被占用的学号注册。
- **测试数据：** studentId=`2021009`, name=`张三`, password=`123456`, phone=`13823888889`
- **预期结果：** 注册成功（code=0），可正常登录。
- **涉及文件：** `LoginView.vue`（注册模式）→ `ReaderController.java`

### AUTH-05 — 学号重复注册（无效等价类）

- **测试场景：** 使用已注册的学号再次注册。
- **测试数据：** studentId=`2021001`（已存在）
- **预期结果：** 注册失败（code=1），错误提示信息待后续设计。
- **涉及文件：** `ReaderController.java`

---

## （八）公告管理模块

### NEWS-01 — 新增/修改公告（有效等价类）

- **测试场景：** 管理员发布新公告或编辑已有公告。（新增和修改使用同一端点，归属同一等价类。）
- **测试数据：** title=`开馆通知`, introduction=`简介摘要`, content=`图书馆将于...`
- **预期结果：** 保存成功（code=0）。
- **涉及文件：** `NewsManagement.vue` → `NewsController.java`

### NEWS-02 — 公告列表查询（有效等价类，含公开访问和管理分页）

- **测试场景：** 前台用户/管理员查看公告列表（分页）。
- **测试数据：** page=1, limit=10
- **预期结果：** 返回分页数据，按 addtime 倒序。
- **涉及文件：** `HomeView.vue` / `NewsManagement.vue` → `NewsController.java`

### NEWS-03 — 删除公告（有效等价类）

- **测试场景：** 管理员删除一条公告。
- **测试数据：** 公告 ID 存在。
- **预期结果：** 删除成功（code=0）。
- **涉及文件：** `NewsManagement.vue` → `NewsController.java`

---

## （九）在线留言模块

### MSG-01 — 读者提交留言（有效等价类）

- **测试场景：** 已登录读者提交留言反馈。
- **测试数据：** content=`建议增加自习室预约功能`
- **预期结果：** 提交成功（code=0），留言插入数据库，reply 字段为空。
- **涉及文件：** `AboutView.vue`（或留言页）→ `MessageController.java`

### MSG-02 — 管理员回复留言（有效等价类）

- **测试场景：** 管理员对读者留言进行回复。
- **测试数据：** 留言 ID 存在且 reply 为空。
- **预期结果：** 留言的 reply 字段更新为回复内容。
- **涉及文件：** 管理端留言页 → `MessageController.java`

### MSG-03 — 删除留言（有效等价类）

- **测试场景：** 管理员删除不当留言。
- **测试数据：** 留言 ID 存在。
- **预期结果：** 删除成功（code=0）。
- **涉及文件：** 管理端留言页 → `MessageController.java`

---

## （十）文件上传模块

### UPL-01 — 上传图片（有效等价类）

- **测试场景：** 管理员上传图书封面（jpg/png 格式）。
- **测试数据：** Content-Type: `multipart/form-data`；表单字段名 `file`；文件类型 jpg 或 png，大小 ≤ 2MB。
- **预期结果：** 上传成功，返回保存后的文件名 `{ file: "xxx.jpg" }`。
- **涉及文件：** `BookManagement.vue` → `FileController.java`

### UPL-02 — 上传非法格式（无效等价类）

- **测试场景：** 上传非图片格式文件或超大文件。
- **测试数据：** 文件类型为 `.exe` / `.pdf` 或大小超过限制。
- **预期结果：** 上传被拒绝（code=1），错误提示信息待后续设计。
- **涉及文件：** `FileController.java`

---

## （十一）仪表盘统计模块

### DASH-01 — 首页统计数据准确性（有效等价类）

- **测试场景：** 管理员登录后首页展示系统概览统计。
- **测试数据：** 数据库中已有 N 本书、M 个读者、K 条借阅记录（通过增删操作验证数字联动）。
- **预期结果：** 返回 `{ bookTotal: N, readerTotal: M, borrowTotal: K, ... }`，各数值与数据库实际记录数一致。
- **涉及文件：** `HomeView.vue` → `DashboardController.java`

---

## 测试用例汇总

| 模块     | 编号前缀 | 用例数 | 有效等价类 | 无效等价类 |
| -------- | -------- | :----: | :--------: | :--------: |
| 图书管理 | BOOK     |   5    |     4      |     1      |
| 图书类别 | CAT      |   3    |     2      |     1      |
| 图书借阅 | BRW      |   4    |     3      |     1      |
| 图书归还 | RET      |   3    |     2      |     1      |
| 图书续借 | RNW      |   3    |     2      |     1      |
| 罚款管理 | PEN      |   3    |     2      |     1      |
| 用户认证 | AUTH     |   5    |     3      |     2      |
| 公告管理 | NEWS     |   3    |     3      |     0      |
| 在线留言 | MSG      |   3    |     3      |     0      |
| 文件上传 | UPL      |   2    |     1      |     1      |
| 仪表盘   | DASH     |   1    |     1      |     0      |
| **合计** |          | **35** |   **26**   |   **9**    |

> **说明：** 公告管理和留言模块的无效等价类（如字段为空、ID 不存在）与 BOOK-05 模式一致，属于通用 CRUD 校验，不重复列出。边界值测试（如 stock=0 vs stock=1、借阅上限 ±1、超期 0 天 vs 1 天）将在此基本测试项通过后，另建 `TEST-CASES-BOUNDARY.md` 详细覆盖。
