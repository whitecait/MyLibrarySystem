> **Archive Status:** Historical Reference
> **Original Path:** `docs/arc/superpowers/specs/2026-06-15-p2-entity-repository-jwt-design.md`
> **Archived On:** `2026-07-13`
> **Historical Purpose:** 返工前契约或实现记录
> **Superseded By:** `docs/README.md`
> **Authority:** 非规范性；不得作为当前需求、接口、数据或验收契约

# P2 Design — Entity + Repository + JWT Security

> **Phase:** P2 of 6 (MyLibrarySystem rebuild)
> **Date:** 2026-06-15
> **Status:** Closed / Historical
> **Predecessor:** P1 — Backend Scaffold (complete)
> **Successor:** P3 — Book & Category CRUD (Controller + Service)
>
> **Related:** [ARCHITECTURE.md](../../ARCHITECTURE.md) · [DATA-MODEL.md](../../DATA-MODEL.md) · [API-SPEC.md](../../API-SPEC.md) · [TEST-CASES.md](../../test/TEST-CASES.md)

## Current Status Note (CodeGraph verified, 2026-07-04)

This is a closed historical P2 design document. P2 has been implemented and the current backend has progressed through P4. Retain this file as design rationale only. Current contracts are maintained in `README.md`, `docs/API-SPEC.md`, `docs/ARCHITECTURE.md`, `docs/DATA-MODEL.md`, and the 2026-07-04 actual-state report.

Current implemented entities include P2/P3/P4 scope: `User`, `Reader`, `Book`, `Category`, `Borrow`, `BorrowReturn`, `Renewal`, and `Penalty`. Authentication is implemented through `/api/auth/login` and `/api/auth/register` with raw request password + backend BCrypt.

## 1. Scope & Deliverables

P2 builds the data access and authentication foundation. No controllers or services yet — those come in P3/P4.

| Layer           | Files                                                    | Count  |
| --------------- | -------------------------------------------------------- | :----: |
| `entity/`       | User, Reader, Book, Category, Borrow                     |   5    |
| `entity/enums/` | UserRole, ApprovalStatus, PaymentStatus, Gender          |   4    |
| `repository/`   | UserRepo, ReaderRepo, BookRepo, CategoryRepo, BorrowRepo |   5    |
| `security/`     | JwtProvider, JwtAuthFilter                               |   2    |
| `dto/`          | LoginRequest, RegisterRequest, LoginResponse             |   3    |
| `config/`       | SecurityConfig (update existing)                         |   1    |
| **Total**       | **19 new + 1 modified**                                  | **20** |

## 2. Design Decisions (4/4 resolved)

| #   | Decision        | Choice                                                             | Rationale                                                                                                                                                 |
| --- | --------------- | ------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| D1  | Table naming    | **English** (`users`, `readers`, `books`, `categories`, `borrows`) | Matches DATA-MODEL.md ER diagram. Code readability.                                                                                                       |
| D2  | Auth model      | **Single User table for all auth, Reader table for profile only**  | Clean separation: User.username = studentId for readers. Single login endpoint. Single JWT filter. Reader has zero auth fields.                           |
| D3  | P2 entity scope | **5 entities only** (User, Reader, Book, Category, Borrow)         | BorrowReturn/Renewal/Penalty have complex workflow logic — defer to P4 with their controllers. News/Message/LibraryCard are independent — insert anytime. |
| D4  | Status fields   | **Java enums + @Enumerated(STRING)**                               | Compile-time safety. DB stores "是"/"否" — frontend-compatible.                                                                                           |
| D5  | Normalization   | **Strict 3NF** — no redundant snapshot fields                      | Borrow keeps only readerId + bookId FKs. Display data via JPA @ManyToOne JOIN. No update anomalies.                                                       |
| D6  | Due date        | **dueDate added to Borrow**                                        | Required by RET-01/RET-02/RNW-02. Set on approval: borrowDate + 30 days. Extended by renewal.                                                             |

## 3. Entity Definitions

### 3.1 User (`users`)

| Field      | Type          | Constraint             | Note                            |
| ---------- | ------------- | ---------------------- | ------------------------------- |
| `id`       | Long          | PK, auto               |                                 |
| `username` | String        | unique, not-null       | admin="admin", reader=studentId |
| `password` | String        | not-null               | BCrypt encoded                  |
| `role`     | UserRole      | not-null               | enum: ADMIN, READER             |
| `readerId` | Long          | FK → readers, nullable | null for admin                  |
| `addtime`  | LocalDateTime | auto                   | @CreatedDate                    |

### 3.2 Reader (`readers`)

| Field        | Type          | Constraint       | Old Name          |
| ------------ | ------------- | ---------------- | ----------------- |
| `id`         | Long          | PK, auto         | `id`              |
| `studentId`  | String        | unique, not-null | `xueshengxuehao`  |
| `name`       | String        | not-null         | `xingming`        |
| `gender`     | Gender        | —                | enum: 男, 女      |
| `department` | String        | —                | `yuanxi`          |
| `birthDate`  | String        | —                | `chushengnianyue` |
| `phone`      | String        | —                | `shouji`          |
| `avatar`     | String        | —                | `touxiang`        |
| `addtime`    | LocalDateTime | auto             |                   |

> **Removed from old model:** `password` (mima) — auth moved to User table.

### 3.3 Book (`books`)

| Field         | Type          | Constraint      | Old Name                    |
| ------------- | ------------- | --------------- | --------------------------- |
| `id`          | Long          | PK, auto        |                             |
| `isbn`        | String        | —               | `tushubianhao`              |
| `title`       | String        | —               | `tushumingcheng`            |
| `author`      | String        | —               | `zuozhe`                    |
| `publisher`   | String        | —               | `chubanshe`                 |
| `year`        | String        | —               | `nianfen`                   |
| `coverImage`  | String        | —               | `tushufengmian`             |
| `stock`       | Integer       | default 1       | `shuliang`                  |
| `description` | String        | @Lob TEXT       | `tushuxiangqing`            |
| `categoryId`  | Long          | FK → categories | `tushuleixing` (was String) |
| `addtime`     | LocalDateTime | auto            |                             |

> **Changed from old model:** `categoryId` is now a Long FK instead of storing category name directly.

### 3.4 Category (`categories`)

| Field     | Type          | Constraint | Old Name      |
| --------- | ------------- | ---------- | ------------- |
| `id`      | Long          | PK, auto   |               |
| `name`    | String        | —          | `tushuleibie` |
| `addtime` | LocalDateTime | auto       |               |

### 3.5 Borrow (`borrows`)

| Field        | Type           | Constraint   | Note                                         |
| ------------ | -------------- | ------------ | -------------------------------------------- |
| `id`         | Long           | PK, auto     |                                              |
| `readerId`   | Long           | FK → readers | @ManyToOne                                   |
| `bookId`     | Long           | FK → books   | @ManyToOne                                   |
| `borrowDate` | LocalDateTime  | —            | Set on application                           |
| `dueDate`    | LocalDate      | —            | **NEW** — set on approval (borrowDate + 30d) |
| `approved`   | ApprovalStatus | —            | enum: 是, 否                                 |
| `reply`      | String         | —            | Admin approval comment                       |
| `addtime`    | LocalDateTime  | auto         |                                              |

> **Removed from old model:** `readerName`, `studentId`, `bookIsbn`, `bookTitle` — all obtainable via FK JOIN. Strict 3NF.

### 3.6 Enum Definitions

```java
public enum UserRole        { ADMIN, READER }           // @Enumerated(STRING) → "ADMIN"/"READER"
public enum ApprovalStatus  { 是, 否 }                   // @Enumerated(STRING) → "是"/"否"
public enum PaymentStatus   { 是, 否 }                   // @Enumerated(STRING) → "是"/"否"  (used in P4)
public enum Gender          { 男, 女 }                   // @Enumerated(STRING) → "男"/"女"
```

## 4. Repository Layer

All extend `JpaRepository<Entity, Long>`. Custom methods use Spring Data derived query convention.

| Repository           | Custom Methods                                                             | Purpose                      |
| -------------------- | -------------------------------------------------------------------------- | ---------------------------- |
| `UserRepository`     | `findByUsername(String)`, `existsByUsername(String)`                       | Login, duplicate check       |
| `ReaderRepository`   | `findByStudentId(String)`, `existsByStudentId(String)`                     | Profile lookup               |
| `BookRepository`     | `findByIsbn(String)`                                                       | Duplicate ISBN check (P3)    |
| `CategoryRepository` | `findByName(String)`                                                       | Duplicate name check (P3)    |
| `BorrowRepository`   | `findByReaderId(Long)`, `countByReaderIdAndApproved(Long, ApprovalStatus)` | My borrows, limit check (P4) |

## 5. JWT Security Design

### 5.1 Token Payload

```json
{
  "sub": "2", // userId
  "username": "2021001", // login identifier
  "role": "READER", // UserRole enum name
  "readerId": 1, // null for admin
  "iat": 1718400000,
  "exp": 1718486400 // +24 hours
}
```

### 5.2 JwtProvider

- `generateToken(User user)` — build signed JWT with claims above
- `validateToken(String token)` — parse, verify signature, check expiry
- `getClaims(String token)` — extract claims for SecurityContext
- Secret: configured via `app.jwt.secret` in application.yml
- Expiration: 24 hours (`app.jwt.expiration-ms`)

### 5.3 JwtAuthFilter

- Extends `OncePerRequestFilter`
- Reads `Authorization: Bearer <token>` header
- Validates → builds `UsernamePasswordAuthenticationToken` with `SimpleGrantedAuthority("ROLE_" + role)`
- Skips: `/api/auth/**`, `/swagger-ui/**`, `/h2-console/**`, `/api/books/list` (public)

### 5.4 SecurityConfig (Update)

Wire `JwtAuthFilter` before `UsernamePasswordAuthenticationFilter`. Permit public paths. Stateless session.

## 6. DTOs

### LoginRequest

```
username: String  @NotBlank
password: String  @NotBlank
```

### RegisterRequest

```
studentId:  String  @NotBlank
name:       String  @NotBlank
password:   String  @NotBlank
gender:     String  (maps to Gender enum)
department: String
birthDate:  String
phone:      String  @Pattern(regexp = "^1[3-9]\\d{9}$")
```

### LoginResponse

```
token:    String
userId:   Long
username: String
role:     UserRole
readerId: Long?   // null for admin
```

## 7. application.yml Additions

```yaml
app:
  jwt:
    secret: ${JWT_SECRET:library-jwt-secret-key-min-256-bits-long}
    expiration-ms: 86400000 # 24 hours

spring:
  jpa:
    hibernate:
      ddl-auto: update # H2 dev: auto-create tables from @Entity
```

## 8. Auth Flows (for P3 Controller implementation)

### Login

```
POST /api/auth/login { username, password }
  → UserRepository.findByUsername()
  → BCrypt.verify(password, hash)
  → JwtProvider.generateToken(user)
  → return LoginResponse
  → Error: code=1, msg="账号或密码错误"
```

### Register

```
POST /api/auth/register { studentId, name, password, ... }
  → UserRepository.existsByUsername(studentId)
  → if exists: code=1, msg="该账号已注册"
  → @Transactional:
      1. reader = INSERT readers (studentId, name, gender, ...)
      2. user   = INSERT users (username=studentId, password=BCrypt, role=READER, readerId=reader.id)
  → token = JwtProvider.generateToken(user)
  → return LoginResponse
```

## 9. TEST-CASES.md Coverage

P2 entities + security directly enable these test cases:

| Test Case | Enabled By                                              |
| --------- | ------------------------------------------------------- |
| AUTH-01   | User entity + JWT (reader login via username=studentId) |
| AUTH-02   | User entity + JWT (admin login via username=admin)      |
| AUTH-03   | JWT validation (bad credentials → 401)                  |
| AUTH-04   | User + Reader entities (register creates both)          |
| AUTH-05   | UserRepository.existsByUsername()                       |

Remaining 30 cases require P3-P5 controllers/services.

## 10. Self-Review Checklist

- [x] No TBD/TODO placeholders
- [x] All 6 design decisions documented with rationale
- [x] Entity fields match DATA-MODEL.md updated field dictionary
- [x] All 5 entities verified to 3NF
- [x] JWT payload covers all needed claims (userId, role, readerId)
- [x] Repository methods sufficient for P3/P4 needs
- [x] DTO validation annotations specified
- [x] Scope boundary clear: no controllers/services in P2
- [x] Enum values compatible with old frontend string expectations
