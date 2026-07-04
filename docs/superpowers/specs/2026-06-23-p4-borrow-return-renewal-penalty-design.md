# P4 Design — Borrow / Return / Renewal / Penalty API

> **Phase:** P4 of 6 (MyLibrarySystem rebuild)
> **Date:** 2026-06-23
> **Status:** Draft
> **Predecessor:** P3 — Book & Category CRUD + Auth Controller (complete)
> **Successor:** P5 — News/Messages + Frontend Integration
>
> **Related:** [ARCHITECTURE.md](../../ARCHITECTURE.md) · [DATA-MODEL.md](../../DATA-MODEL.md) · [API-SPEC.md](../../API-SPEC.md) · [TEST-CASES.md](../../test/TEST-CASES.md)

## 1. Scope & Deliverables

P4 builds the Controller + Service + Entity + Repository layer for borrow, return, renewal, and penalty workflows. All entities use strict 3NF — no snapshot (快照) fields. Historical data is traced through the `borrow_id` FK chain: Return/Renewal/Penalty → Borrow → Reader/Book.

| Layer         | Files                                                                                                                                     | Count  |
| ------------- | ----------------------------------------------------------------------------------------------------------------------------------------- | :----: |
| `entity/`     | BorrowReturn, Renewal, Penalty                                                                                                            |   3    |
| `repository/` | BorrowReturnRepository, RenewalRepository, PenaltyRepository, BorrowRepository (amend)                                                    |  3+1   |
| `dto/`        | BorrowRequest, BorrowResponse, ReturnRequest, ReturnResponse, RenewalRequest, RenewalResponse, PenaltyResponse, BatchApproval, PayRequest |   9    |
| `service/`    | BorrowService, ReturnService, RenewalService, PenaltyService + 4 impls                                                                    |   8    |
| `controller/` | BorrowController, ReturnController, RenewalController, PenaltyController                                                                  |   4    |
| `config/`     | SecurityConfig (amend)                                                                                                                    |   1    |
| `resources/`  | application.yml (add library.\* config block)                                                                                             |   1    |
| **Total**     | **25 new + 4 modified**                                                                                                                   | **29** |

### 1.1 Borrow Endpoints

| Method | Path                   |      Auth      | Description                                          |
| ------ | ---------------------- | :------------: | ---------------------------------------------------- |
| `GET`  | `/api/borrows/page`    | Authenticated  | Paginated list; Admin sees all, Reader sees own      |
| `POST` | `/api/borrows/add`     | Reader / Admin | Submit borrow application (auto-approve if eligible) |
| `POST` | `/api/borrows/shBatch` |     Admin      | Batch approve/reject                                 |
| `POST` | `/api/borrows/delete`  |     Admin      | Batch delete                                         |

### 1.2 Return Endpoints

| Method | Path                   |      Auth      | Description                                          |
| ------ | ---------------------- | :------------: | ---------------------------------------------------- |
| `GET`  | `/api/returns/page`    | Authenticated  | Paginated list; Admin sees all, Reader sees own      |
| `POST` | `/api/returns/add`     | Reader / Admin | Submit return application                            |
| `POST` | `/api/returns/shBatch` |     Admin      | Batch approve/reject; approval → stock+1 + auto-fine |
| `POST` | `/api/returns/delete`  |     Admin      | Batch delete                                         |

### 1.3 Renewal Endpoints

| Method | Path                    |      Auth      | Description                                                   |
| ------ | ----------------------- | :------------: | ------------------------------------------------------------- |
| `GET`  | `/api/renewals/page`    | Authenticated  | Paginated list; Admin sees all, Reader sees own               |
| `POST` | `/api/renewals/add`     | Reader / Admin | Submit renewal application (auto-approve on 1st renewal ≤30d) |
| `POST` | `/api/renewals/shBatch` |     Admin      | Batch approve/reject; approval → extend dueDate               |
| `POST` | `/api/renewals/delete`  |     Admin      | Batch delete                                                  |

### 1.4 Penalty Endpoints

| Method | Path                    |     Auth      | Description                       |
| ------ | ----------------------- | :-----------: | --------------------------------- |
| `GET`  | `/api/penalties/page`   | Authenticated | Paginated list; filter by `paid?` |
| `POST` | `/api/penalties/pay`    |    Reader     | Pay a fine (single)               |
| `POST` | `/api/penalties/delete` |     Admin     | Batch delete                      |

## 2. Design Decisions

| #   | Decision              | Choice                                                            | Rationale                                                                                                                                                                                                                                                                                                   |
| --- | --------------------- | ----------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| D1  | 3NF strategy          | **Strict 3NF: all records trace through borrowId FK**             | Return/Renewal/Penalty hold only `borrowId`. Reader/book info JOINed via Borrow. Zero snapshot redundancy. Consistent with P2 Borrow design.                                                                                                                                                                |
| D2  | Approval model        | **Auto-approve on eligible, manual otherwise**                    | Borrow ≤30d + clean → auto. Renewal: only 1st renewal ≤30d + clean → auto. Others await admin review. Reduces admin workload for routine cases.                                                                                                                                                             |
| D3  | Duration control      | **Reader requests days, capped by config**                        | `auto-approve-duration` caps request; `max-duration-cap` (60d from borrowDate) is hard ceiling. Config-driven, no hardcoded constants.                                                                                                                                                                      |
| D4  | Fine generation       | **Auto on overdue return approval**                               | `ReturnService.approveBatch()` computes `overdueDays × fine.rate-per-day`, creates Penalty with auto-generated `fineNo`.                                                                                                                                                                                    |
| D5  | fineNo format         | **FN-YYYYMMDD-NNN (date + sequence)**                             | Deterministic, sortable, human-readable.                                                                                                                                                                                                                                                                    |
| D6  | Service architecture  | **1:1 Controller-Service mapping**                                | Matches P3 pattern. Each service self-contained.                                                                                                                                                                                                                                                            |
| D7  | Response enrichment   | **Service-layer batch JOIN**                                      | Borrow/Return/Renewal/Penalty responses include readerName/bookTitle via batch FK resolution. Same pattern as P3 BookResponse.categoryName.                                                                                                                                                                 |
| D8  | Reader identification | **JwtAuthFilter extracts userId → get readerId from User entity** | Controllers get current user from SecurityContext; Service resolves reader-scoped queries via User.readerId.                                                                                                                                                                                                |
| D9  | Config management     | **application.yml `library.*` block**                             | All business parameters (max-limit, durations, fine rate, auto-approve count) in one config block. No scattered hardcoded values.                                                                                                                                                                           |
| D10 | readerId security     | **Controller validates readerId against JWT**                     | Reader role: request.readerId (if present) must match JWT readerId — prevents cross-reader request forgery. Admin role: readerId required, unrestricted. Applies to borrow/add. Return/renewal only use borrowId FK (reader ownership implied by the borrow record itself).                                 |
| D11 | Approval tri-state    | **ApprovalStatus expanded to 3 values: 待审核/是/否**             | `待审核` = pending review (initial state for non-auto-approved submissions). `是` = approved (auto or admin). `否` = explicitly rejected by admin (terminal). `approveBatch()` only processes `待审核` records — prevents double-approval/rejection. Applied to Borrow, BorrowReturn, and Renewal entities. |

## 3. Entities (Strict 3NF)

### 3.1 Borrow (已有 — P2, amend with durationDays)

```java
@Entity @Table(name = "borrows")
public class Borrow {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reader_id", nullable = false)
    private Long readerId;           // FK → Reader

    @Column(name = "book_id", nullable = false)
    private Long bookId;             // FK → Book

    @Column(name = "duration_days")
    private Integer durationDays;    // ⬅ NEW: requested borrow days

    @Column(name = "borrow_date")
    private LocalDateTime borrowDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING) @Column(name = "approved", length = 8)
    private ApprovalStatus approved;    // 待审核 / 是 / 否

    @Column(name = "reply", length = 500)
    private String reply;

    @Column(name = "addtime", updatable = false)
    private LocalDateTime addtime;

    @PrePersist protected void onCreate() { this.addtime = LocalDateTime.now(); }
}
```

### 3.2 BorrowReturn (新建)

```java
@Entity @Table(name = "returns")
public class BorrowReturn {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "borrow_id", nullable = false)
    private Long borrowId;           // FK → Borrow

    @Column(name = "return_date")
    private LocalDateTime returnDate;

    @Enumerated(EnumType.STRING) @Column(name = "approved", length = 8)
    private ApprovalStatus approved;    // 待审核 / 是 / 否

    @Column(name = "reply", length = 500)
    private String reply;

    @Column(name = "addtime", updatable = false)
    private LocalDateTime addtime;

    @PrePersist protected void onCreate() { this.addtime = LocalDateTime.now(); }
}
```

**vs DATA-MODEL:** Removed `bookId`, `bookIsbn`, `bookTitle`, `studentId`, `readerName`. Replaced with `borrowId` FK.

### 3.3 Renewal (新建)

```java
@Entity @Table(name = "renewals")
public class Renewal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "borrow_id", nullable = false)
    private Long borrowId;           // FK → Borrow

    @Column(name = "duration_days")
    private Integer durationDays;    // Requested renewal days

    @Column(name = "renewal_date")
    private LocalDateTime renewalDate;

    @Enumerated(EnumType.STRING) @Column(name = "approved", length = 8)
    private ApprovalStatus approved;    // 待审核 / 是 / 否

    @Column(name = "reply", length = 500)
    private String reply;

    @Column(name = "addtime", updatable = false)
    private LocalDateTime addtime;

    @PrePersist protected void onCreate() { this.addtime = LocalDateTime.now(); }
}
```

**vs DATA-MODEL:** Removed `bookId`, `bookIsbn`, `bookTitle`, `studentId`, `readerName`. Added `borrowId` FK + `durationDays`.

### 3.4 Penalty (新建)

```java
@Entity @Table(name = "penalties")
public class Penalty {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fine_no")
    private String fineNo;           // FN-YYYYMMDD-NNN

    @Column(name = "borrow_id", nullable = false)
    private Long borrowId;           // FK → Borrow

    @Column(name = "amount", precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "fine_date")
    private LocalDate fineDate;

    @Enumerated(EnumType.STRING) @Column(name = "paid", length = 4)
    private PaymentStatus paid;

    @Column(name = "addtime", updatable = false)
    private LocalDateTime addtime;

    @PrePersist protected void onCreate() { this.addtime = LocalDateTime.now(); }
}
```

**vs DATA-MODEL:** Removed `bookId`, `bookIsbn`, `bookTitle`, `studentId`, `readerName`. Added `borrowId` FK.

### 3.5 ER Summary

```
Return --(borrowId)--> Borrow --(readerId)--> Reader
Renewal --(borrowId)--> Borrow --(bookId)--> Book
Penalty --(borrowId)--> Borrow
```

All historical data traced through a single FK chain. No snapshot redundancy.

## 4. DTOs

### 4.1 BorrowRequest

```java
public class BorrowRequest {
    @NotNull(message = "图书ID不能为空")
    private Long bookId;

    private Long readerId;           // optional: Admin specifies target reader; Reader must omit or match self

    private Integer durationDays;    // optional, default from config
}
```

**readerId security validation (Controller layer):**

- Reader role: if `readerId` is present, it MUST equal the JWT-derived readerId (from `User.readerId`). Otherwise → `throw ApiException("无权为其他读者操作")`
- Admin role: `readerId` is required (must be present and valid). No self-match constraint.

### 4.2 BorrowResponse

```java
public class BorrowResponse {
    private Long id;
    private Long readerId;
    private String readerName;       // JOIN Reader
    private String studentId;        // JOIN Reader
    private Long bookId;
    private String bookTitle;        // JOIN Book
    private String bookIsbn;         // JOIN Book
    private String borrowDate;
    private String dueDate;
    private String approved;
    private String reply;
    private String addtime;
}
```

### 4.3 ReturnRequest

```java
public class ReturnRequest {
    @NotNull(message = "借阅记录ID不能为空")
    private Long borrowId;
}
```

### 4.4 ReturnResponse

```java
public class ReturnResponse {
    private Long id;
    private Long borrowId;
    private String readerName;       // JOIN via Borrow
    private String studentId;
    private String bookTitle;        // JOIN via Borrow
    private String bookIsbn;
    private String returnDate;
    private String approved;
    private String reply;
    private String addtime;
}
```

### 4.5 RenewalRequest

```java
public class RenewalRequest {
    @NotNull(message = "借阅记录ID不能为空")
    private Long borrowId;

    private Integer durationDays;   // optional
}
```

### 4.6 RenewalResponse

```java
public class RenewalResponse {
    private Long id;
    private Long borrowId;
    private Integer durationDays;
    private String readerName;       // JOIN via Borrow
    private String studentId;
    private String bookTitle;        // JOIN via Borrow
    private String bookIsbn;
    private String renewalDate;
    private String approved;
    private String reply;
    private String addtime;
}
```

### 4.7 PenaltyResponse

```java
public class PenaltyResponse {
    private Long id;
    private String fineNo;
    private Long borrowId;
    private String readerName;       // JOIN via Borrow
    private String studentId;
    private String bookTitle;        // JOIN via Borrow
    private String bookIsbn;
    private String amount;
    private String fineDate;
    private String paid;
    private String addtime;
}
```

### 4.8 BatchApproval (shared)

```java
public class BatchApproval {
    @NotEmpty(message = "审核ID列表不能为空")
    private List<Long> ids;

    @NotNull(message = "审核结果不能为空")
    private String approved;         // "是" / "否"

    private String reply;
}
```

### 4.9 PayRequest

```java
public class PayRequest {
    @NotNull(message = "罚款ID不能为空")
    private Long id;
}
```

## 5. Service Layer

### 5.1 BorrowService

```
BorrowService
├── queryPage(page, limit, readerId?, bookId?, approved?) → Page<BorrowResponse>
│     1. Build Specification with optional filters
│     2. BorrowRepository.findAll(spec, pageable) → Page<Borrow>
│     3. Batch JOIN: collect readerIds + bookIds
│        → ReaderRepository.findAllById(rids) → Map<Long, Reader>
│        → BookRepository.findAllById(bids) → Map<Long, Book>
│     4. Map Borrow → BorrowResponse (fill readerName, studentId, bookTitle, bookIsbn)
│
├── apply(BorrowRequest { bookId, readerId?, durationDays? }, User currentUser) → void   @Transactional
│     0. Resolve readerId:
│        if currentUser.role == READER:
│          reqReaderId = req.readerId != null ? req.readerId : currentUser.readerId
│          if req.readerId != null && !req.readerId.equals(currentUser.readerId):
│            throw ApiException("无权为其他读者操作")
│        else (ADMIN):
│          reqReaderId = req.readerId — if null, throw ApiException("请指定读者ID")
│        → validate ReaderRepository.existsById(reqReaderId) else "读者不存在"
│     1. book = BookRepository.findById(bookId)
│        → not found? throw ApiException("图书不存在")
│     2. book.stock <= 0?
│        → throw ApiException("图书库存不足")
│     3. BorrowRepository.existsByReaderIdAndBookIdAndApproved(rid, bid, 是)
│        → exists? throw ApiException("该图书您已有进行中的借阅记录")
│     4. int actualDays = req.durationDays != null
│            ? Math.min(req.durationDays, config.autoApproveDuration)
│            : config.defaultDurationDays
│     5. Auto-approval check:
│        hasUnpaidFines = PenaltyRepository.countByReaderIdAndPaid(rid, 否) > 0
│        atLimit = BorrowRepository.countByReaderIdAndApproved(rid, 是) >= config.maxLimit
│        overAutoDays = req.durationDays != null && req.durationDays > config.autoApproveDuration
│
│        if (!hasUnpaidFines && !atLimit && !overAutoDays):
│          approved = 是
│          borrowDate = LocalDateTime.now()
│          dueDate = LocalDate.now().plusDays(actualDays)
│          book.stock -= 1 → BookRepository.save(book)
│        else:
│          approved = 待审核           // pending admin review
│          borrowDate = null, dueDate = null
│     6. BorrowRepository.save(borrow)
│
├── approveBatch(List<Long> ids, String approved, String reply) → void   @Transactional
│     for each borrow:
│       // Guard: only process pending records
│       if borrow.approved != 待审核:
│         continue                             // skip already-approved/rejected
│       if approved == "是":
│         borrow.approved = 是
│         borrow.borrowDate = LocalDateTime.now()
│         int actualDays = Math.min(
│             borrow.durationDays != null ? borrow.durationDays : config.defaultDurationDays,
│             config.autoApproveDuration)
│         borrow.dueDate = LocalDate.now().plusDays(actualDays)
│         book.stock -= 1 → BookRepository.save(book)
│       else:
│         borrow.approved = 否                 // terminal: admin rejected
│         borrow.reply = reply
│     BorrowRepository.saveAll(borrows)
│
└── delete(List<Long> ids) → void
      BorrowRepository.deleteAllById(ids)
```

### 5.2 ReturnService

```
ReturnService
├── queryPage(page, limit, readerId?, approved?) → Page<ReturnResponse>
│     Similar batch-JOIN pattern: BorrowReturn → Borrow → Reader/Book
│
├── apply(ReturnRequest { borrowId }) → void   @Transactional
│     1. borrow = BorrowRepository.findById(borrowId)
│        → not found? throw ApiException("借阅记录不存在")
│     2. borrow.approved != 是?
│        → throw ApiException("该借阅记录未通过审核")
│     3. BorrowReturnRepository.existsByBorrowIdAndApproved(borrowId, 是)
│        → exists? throw ApiException("该借阅记录已归还")
│     4. 创建 BorrowReturn: approved = 待审核
│
├── approveBatch(List<Long> ids, String approved, String reply) → void   @Transactional
│     for each ret:
│       // Guard: only process pending records
│       if ret.approved != 待审核:
│         continue
│       if approved == "是":
│         ret.approved = 是
│         ret.returnDate = LocalDateTime.now()
│         borrow = BorrowRepository.findById(ret.borrowId)
│         book = BookRepository.findById(borrow.bookId)
│         book.stock += 1 → BookRepository.save(book)
│         // Overdue fine calculation
│         long overdueDays = ChronoUnit.DAYS.between(borrow.dueDate, LocalDate.now())
│         if overdueDays > 0:
│           Penalty p = new Penalty()
│           p.borrowId = borrow.id
│           p.fineNo = generateFineNo()   // FN-YYYYMMDD-NNN
│           p.amount = BigDecimal.valueOf(overdueDays).multiply(config.fineRatePerDay)
│           p.fineDate = LocalDate.now()
│           p.paid = 否
│           PenaltyRepository.save(p)
│       else:
│         ret.approved = 否, ret.reply = reply    // terminal: admin rejected
│     ReturnRepository.saveAll(returns)
│
└── delete(List<Long> ids) → void
```

### 5.3 RenewalService

```
RenewalService
├── queryPage(page, limit, readerId?, approved?) → Page<RenewalResponse>
│
├── apply(RenewalRequest { borrowId, durationDays? }) → void   @Transactional
│     1. borrow = BorrowRepository.findById(borrowId)
│        → not found? throw ApiException("借阅记录不存在")
│     2. borrow.approved != 是?
│        → throw ApiException("该借阅记录未通过审核")
│     3. BorrowReturnRepository.existsByBorrowIdAndApproved(borrowId, 是)
│        → exists? throw ApiException("该借阅记录已归还，无法续借")
│     4. int actualDays = Math.min(
│            req.durationDays != null ? req.durationDays : config.defaultDurationDays,
│            config.autoApproveDuration)
│        LocalDate newDueDate = LocalDate.now().plusDays(actualDays)
│        // 60-day hard cap
│        LocalDate maxDueDate = borrow.borrowDate.plusDays(config.maxDurationCap)
│        if (newDueDate.isAfter(maxDueDate)):
│          actualDays = ChronoUnit.DAYS.between(LocalDate.now(), maxDueDate)
│          if (actualDays <= 0):
│            throw ApiException("无法续借：已超过最长持有期限")
│          newDueDate = maxDueDate
│     5. int existingRenewals = RenewalRepository.countByBorrowId(borrowId)
│     6. Auto-approval check:
│        isWithinAutoCount = existingRenewals < config.autoApproveCount
│        withinAutoDays = req.durationDays == null || req.durationDays <= config.autoApproveDuration
│        noUnpaidFines = PenaltyRepository.countByReaderIdAndPaid(rid, 否) == 0
│        notCapped = newDueDate.equals(LocalDate.now().plusDays(requestedOrConfigDaysNoTruncation))
│
│        if isWithinAutoCount && withinAutoDays && noUnpaidFines && notCapped:
│          approved = 是, borrow.dueDate = newDueDate
│          BorrowRepository.save(borrow)
│        else:
│          approved = 待审核          // pending admin review
│     7. RenewalRepository.save(renewal)
│
├── approveBatch(List<Long> ids, String approved, String reply) → void   @Transactional
│     for each renewal:
│       // Guard: only process pending records
│       if renewal.approved != 待审核:
│         continue
│       if approved == "是":
│         renewal.approved = 是
│         borrow = BorrowRepository.findById(renewal.borrowId)
│         int actualDays = Math.min(
│             renewal.durationDays != null ? renewal.durationDays : config.defaultDurationDays,
│             config.autoApproveDuration)
│         LocalDate newDueDate = LocalDate.now().plusDays(actualDays)
│         // Re-check 60-day cap on approval
│         LocalDate maxDueDate = borrow.borrowDate.plusDays(config.maxDurationCap)
│         if (newDueDate.isAfter(maxDueDate)):
│           newDueDate = maxDueDate
│         borrow.dueDate = newDueDate
│         BorrowRepository.save(borrow)
│       else:
│         renewal.approved = 否, renewal.reply = reply   // terminal: admin rejected
│     RenewalRepository.saveAll(renewals)
│
└── delete(List<Long> ids) → void
```

### 5.4 PenaltyService

```
PenaltyService
├── queryPage(page, limit, readerId?, paid?) → Page<PenaltyResponse>
│     Query Penalty → join through Borrow → filter by readerId
│     Specification: root → join("borrowId") → borrow.get("readerId")
│
├── pay(Long penaltyId) → void   @Transactional
│     penalty = PenaltyRepository.findById(penaltyId)
│       → not found? throw ApiException("罚款记录不存在")
│     penalty.paid == 是?
│       → throw ApiException("该罚款已支付")
│     penalty.paid = 是 → PenaltyRepository.save(penalty)
│
└── delete(List<Long> ids) → void
```

## 6. Repository Extensions

### 6.1 BorrowRepository (amend existing)

```java
@Repository
public interface BorrowRepository extends JpaRepository<Borrow, Long> {
    // Existing (P2)
    List<Borrow> findByReaderId(Long readerId);
    long countByReaderIdAndApproved(Long readerId, ApprovalStatus approved);
    List<Borrow> findByBookId(Long bookId);
    long countByBookIdIn(List<Long> bookIds);

    // P4 additions
    boolean existsByReaderIdAndBookIdAndApproved(Long readerId, Long bookId, ApprovalStatus approved);
}
```

### 6.2 BorrowReturnRepository (new)

```java
@Repository
public interface BorrowReturnRepository extends JpaRepository<BorrowReturn, Long> {
    boolean existsByBorrowIdAndApproved(Long borrowId, ApprovalStatus approved);
}
```

### 6.3 RenewalRepository (new)

```java
@Repository
public interface RenewalRepository extends JpaRepository<Renewal, Long> {
    long countByBorrowId(Long borrowId);
    boolean existsByBorrowIdAndApproved(Long borrowId, ApprovalStatus approved);
}
```

### 6.4 PenaltyRepository (new)

```java
@Repository
public interface PenaltyRepository extends JpaRepository<Penalty, Long> {

    @Query("SELECT COUNT(p) FROM Penalty p JOIN Borrow b ON p.borrowId = b.id " +
           "WHERE b.readerId = :readerId AND p.paid = :paid")
    long countByReaderIdAndPaid(@Param("readerId") Long readerId, @Param("paid") PaymentStatus paid);
}
```

> NOTE: `countByBorrow_ReaderIdAndPaid` (Spring Data derived method) would NOT work here — Penalty stores only `borrowId` (Long FK), not a `@ManyToOne Borrow` entity. The `_` navigation syntax requires an actual JPA association. Use `@Query` with explicit JOIN instead.

## 7. Business Rules Summary

| #   | Rule                                           |    Phase     | Outcome                               |
| --- | ---------------------------------------------- | :----------: | ------------------------------------- |
| R1  | Book stock = 0                                 |    apply     | code=1 "图书库存不足"                 |
| R2  | Same book already borrowed by same reader      |    apply     | code=1 "该图书您已有进行中的借阅记录" |
| R3  | Unpaid fines exist                             |    apply     | approved=待审核 (manual review)       |
| R4  | At borrow limit (5)                            |    apply     | approved=待审核 (manual review)       |
| R5  | duration > auto-approve-duration (30d)         |    apply     | approved=待审核 (manual review)       |
| R6  | Clean: no R3/R4/R5 → auto-approve              |    apply     | approved=是, stock-1, set dueDate     |
| R7  | Borrow not approved yet (approved != 是)       |    return    | code=1 "该借阅记录未通过审核"         |
| R8  | Already returned                               |    return    | code=1 "该借阅记录已归还"             |
| R9  | Return approval → stock+1                      | approveBatch | book.stock += 1                       |
| R10 | Return overdue → auto-generate fine            | approveBatch | Penalty created (days × rate)         |
| R11 | Borrow not approved (approved != 是)           |   renewal    | code=1 "该借阅记录未通过审核"         |
| R12 | Already returned                               |   renewal    | code=1 "该借阅记录已归还"             |
| R13 | Renewal exceeds max-duration-cap (60d)         |   renewal    | code=1 "无法续借：已超过最长持有期限" |
| R14 | 1st renewal + ≤30d + clean → auto-approve      |   renewal    | approved=是, extend dueDate           |
| R15 | Non-1st-renewal or >30d or violations → manual |   renewal    | approved=待审核                       |
| R16 | Fine already paid                              |     pay      | code=1 "该罚款已支付"                 |
| R17 | approveBatch target not 待审核                 | approveBatch | skip (idempotent guard)               |

## 8. Approval Decision Matrix

### 8.1 Borrow

| Condition                             |       Result       |
| ------------------------------------- | :----------------: |
| stock = 0                             | code=1 (no record) |
| Same book + same reader + approved=是 | code=1 (no record) |
| unpaid fines OR at limit OR >30d      |  approved=待审核   |
| None of above                         |    approved=是     |

### 8.2 Renewal

| Condition                            |       Result       |
| ------------------------------------ | :----------------: |
| borrow approved != 是                | code=1 (no record) |
| already returned                     | code=1 (no record) |
| exceeds 60d cap from borrowDate      | code=1 (no record) |
| renewal count < auto-approve-count   |                    |
| AND duration ≤ auto-approve-duration |                    |
| AND no unpaid fines                  |    approved=是     |
| AND not truncated by 60d cap         |                    |
| Any violation of above               |  approved=待审核   |

## 9. Configuration

```yaml
# application.yml (new block)
library:
  borrow:
    max-limit: 5 # max concurrent borrows per reader
    default-duration-days: 30 # default borrow/renewal duration
    auto-approve-duration: 30 # duration ≤ this → eligible for auto-approval
    max-duration-cap: 60 # hard cap: max days from borrowDate
  fine:
    rate-per-day: 1.0 # overdue fine rate (CNY/day)
  renewal:
    auto-approve-count: 1 # first N renewals eligible for auto-approval
```

## 10. SecurityConfig Amendment

Insert after `// ---- Admin: category management` block:

```java
// ---- Borrower flow (reader/admin apply, admin approve) ----
.requestMatchers(HttpMethod.POST, "/borrows/add",
        "/returns/add", "/renewals/add")
        .hasAnyRole("READER", "ADMIN")
.requestMatchers(HttpMethod.GET, "/borrows/page",
        "/returns/page", "/renewals/page",
        "/penalties/page")
        .authenticated()
.requestMatchers("/borrows/shBatch", "/borrows/delete",
        "/returns/shBatch", "/returns/delete",
        "/renewals/shBatch", "/renewals/delete",
        "/penalties/delete")
        .hasRole("ADMIN")
// ---- Penalty payment ----
.requestMatchers(HttpMethod.POST, "/penalties/pay")
        .hasRole("READER")
```

## 11. Error Messages

| Error                                           | code | msg                            |
| ----------------------------------------------- | :--: | ------------------------------ |
| Book not found                                  |  1   | "图书不存在"                   |
| Stock exhausted                                 |  1   | "图书库存不足"                 |
| Duplicate borrow (same book)                    |  1   | "该图书您已有进行中的借阅记录" |
| Unpaid fines block borrow                       |  1   | "您有未缴纳的罚款，无法借阅"   |
| At borrow limit                                 |  1   | "借阅数量已达上限"             |
| Borrow not found                                |  1   | "借阅记录不存在"               |
| Borrow not approved (return/renewal)            |  1   | "该借阅记录未通过审核"         |
| Already returned                                |  1   | "该借阅记录已归还"             |
| Already returned (renewal)                      |  1   | "该借阅记录已归还，无法续借"   |
| Exceeds max duration cap                        |  1   | "无法续借：已超过最长持有期限" |
| Penalty not found                               |  1   | "罚款记录不存在"               |
| Penalty already paid                            |  1   | "该罚款已支付"                 |
| Reader self-bypass (cross-reader forgery)       |  1   | "无权为其他读者操作"           |
| Admin omit readerId                             |  1   | "请指定读者ID"                 |
| Reader not found                                |  1   | "读者不存在"                   |
| Approve already-processed record                |  —   | (silently skip, not error)     |
| (category-specific errors carried over from P3) |  —   | —                              |

All business errors return HTTP 200 with `code=1`. Auth errors (401/403) handled by Spring Security.

## 12. TEST-CASES Coverage

### 12.1 Existing Cases Covered

| Case   | Status | Notes                                               |
| ------ | :----: | --------------------------------------------------- |
| BRW-01 |   √    | Normal borrow (auto-approve path when eligible)     |
| BRW-02 |   √    | Qualification failure (stock/fines/limit)           |
| BRW-03 |   √    | Admin approve (manual path)                         |
| BRW-04 |   √    | Admin reject                                        |
| RET-01 |   √    | Normal return, no fine                              |
| RET-02 |   √    | Overdue return → auto fine                          |
| RET-03 |   √    | Return nonexistent borrow                           |
| RNW-01 |   √    | Normal renewal (auto-approve on 1st ≤30d)           |
| RNW-02 |   √    | Admin approve renewal (manual path, extend dueDate) |
| RNW-03 |   √    | Renewal rejected — shifting to "60d cap exceeded"   |
| PEN-01 |   √    | Query fines by reader/paid                          |
| PEN-02 |   √    | Pay fine                                            |
| PEN-03 |   √    | Duplicate payment                                   |

### 12.2 New Test Cases

| Case   | Scenario                                                                |    Type    |
| ------ | ----------------------------------------------------------------------- | :--------: |
| BRW-05 | Borrow auto-approve: ≤30d, no fines, under limit → approved=是, stock-1 |  Valid EC  |
| BRW-06 | Borrow pending: duration > 30d → approved=待审核                        | Invalid EC |
| BRW-07 | Same book duplicate borrow → code=1                                     | Invalid EC |
| BRW-08 | Reader submits with mismatched readerId → code=1 "无权为其他读者操作"   | Invalid EC |
| BRW-09 | Admin submits borrow without readerId → code=1 "请指定读者ID"           | Invalid EC |
| BRW-10 | Admin approves borrow that is already approved → skip (idempotent)      |  Valid EC  |
| RET-04 | Return already-returned borrow → code=1                                 | Invalid EC |
| RET-05 | Return unapproved borrow → code=1                                       | Invalid EC |
| RNW-04 | 1st renewal auto-approve: ≤30d, clean, within 60d                       |  Valid EC  |
| RNW-05 | Non-1st renewal → approved=待审核                                       | Invalid EC |
| RNW-06 | Renewal exceeds 60d cap → code=1                                        | Invalid EC |
| RNW-07 | Multiple renewals cumulative within 60d → allow submit                  |  Valid EC  |

### 12.3 Updated Totals

| Module    | Existing |  New   | Total  |
| --------- | :------: | :----: | :----: |
| Borrows   |    4     |   6    |   10   |
| Returns   |    3     |   2    |   5    |
| Renewals  |    3     |   4    |   7    |
| Penalties |    3     |   0    |   3    |
| **Sum**   |  **13**  | **12** | **25** |

## 13. File Manifest

### New Files (25)

```
entity/BorrowReturn.java
entity/Renewal.java
entity/Penalty.java
repository/BorrowReturnRepository.java
repository/RenewalRepository.java
repository/PenaltyRepository.java
dto/BorrowRequest.java
dto/BorrowResponse.java
dto/ReturnRequest.java
dto/ReturnResponse.java
dto/RenewalRequest.java
dto/RenewalResponse.java
dto/PenaltyResponse.java
dto/BatchApproval.java
dto/PayRequest.java
service/BorrowService.java
service/ReturnService.java
service/RenewalService.java
service/PenaltyService.java
service/impl/BorrowServiceImpl.java
service/impl/ReturnServiceImpl.java
service/impl/RenewalServiceImpl.java
service/impl/PenaltyServiceImpl.java
controller/BorrowController.java
controller/ReturnController.java
controller/RenewalController.java
controller/PenaltyController.java
```

### Modified Files (4)

```
entity/enums/ApprovalStatus.java — add 待审核 enum value
entity/Borrow.java               — add durationDays field
repository/BorrowRepository.java  — add existsByReaderIdAndBookIdAndApproved
config/SecurityConfig.java       — add P4 endpoint rules
src/main/resources/application.yml — add library.* config block
```

## 14. Self-Review Checklist

- [x] No TBD/TODO placeholders
- [x] All 11 design decisions documented with rationale
- [x] ApprovalStatus expanded to 3 values (待审核/是/否) — D11
- [x] Entity fields match 3NF strategy — borrowId FK chain consistent
- [x] Borrow entity updated in-place (§3.1) with durationDays field
- [x] BorrowRequest includes readerId with Controller-layer security validation (D10)
- [x] PenaltyRepository uses @Query (not broken derived method) — documented rationale
- [x] All endpoints mapped to Service methods
- [x] approveBatch() guarded: only processes 待审核 records (idempotent)
- [x] Auto-approval vs manual-approval rules explicit (matrix in §8)
- [x] Config keys enumerated with defaults
- [x] Error messages specified for all business exceptions (including readerId forgery)
- [x] Batch JOIN pattern consistent with P3 BookServiceImpl
- [x] Renewal multiple-submission allowed; auto-approve-count gates auto-approval
- [x] 60-day hard cap enforced on borrowDate basis, not configurable duration
- [x] Approval column length changed: 4 -> 8 (accommodates 待审核)
- [x] Test coverage: 13 existing + 12 new = 25 cases
- [x] Scope boundary clear: no News/Message/Upload/Dashboard — those are P5
