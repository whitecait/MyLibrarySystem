# library-server — Spring Boot Backend

Library Management System backend. **Phase 4 complete** — Borrow/Return/Renewal/Penalty APIs live. 7 controllers, 7 services, 19 tests.

## Quick Start

```bash
cd library-server

# Dev: H2 in-memory (no MySQL needed)
./mvnw spring-boot:run
# -> Swagger:  http://localhost:8080/api/swagger-ui.html
# -> H2:       http://localhost:8080/api/h2-console

# Prod: MySQL
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

## Structure

```
src/main/java/com/library/
├── LibraryApplication.java
├── common/ApiResponse.java              # { code, msg, data }
├── config/{Cors,Security,OpenApi}       # CORS, JWT filter chain (role-based), Swagger
├── entity/
│   ├── User.java                        # Unified auth (admin + reader)
│   ├── Reader.java                      # Profile only (no password)
│   ├── Book.java, Category.java         # Book catalog
│   ├── Borrow.java                      # 3NF + dueDate + durationDays
│   ├── BorrowReturn.java                # 3NF, borrowId FK
│   ├── Renewal.java                     # 3NF, borrowId FK + durationDays
│   ├── Penalty.java                     # 3NF, borrowId FK + fineNo
│   └── enums/{UserRole,ApprovalStatus,PaymentStatus,Gender}
├── repository/{User,Reader,Book,Category,Borrow,BorrowReturn,Renewal,Penalty}Repository
├── dto/
│   ├── LoginRequest, RegisterRequest, LoginResponse
│   ├── BookRequest, BookResponse
│   ├── CategoryRequest, CategoryResponse
│   ├── BorrowRequest, BorrowResponse
│   ├── ReturnRequest, ReturnResponse
│   ├── RenewalRequest, RenewalResponse
│   └── PenaltyResponse, BatchApproval, PayRequest
├── security/{JwtProvider,JwtAuthFilter}
├── service/
│   ├── AuthService, BookService, CategoryService
│   ├── BorrowService, ReturnService, RenewalService, PenaltyService
│   └── impl/{Auth,Book,Category,Borrow,Return,Renewal,Penalty}ServiceImpl
├── controller/{Auth,Book,Category,Borrow,Return,Renewal,Penalty}Controller
└── exception/{ApiException,GlobalExceptionHandler}
```

## Key Design Decisions

| #   | Decision      | Choice                                                                                                      |
| --- | ------------- | ----------------------------------------------------------------------------------------------------------- |
| D1  | Table names   | English (`users`, `readers`, `books`, ...)                                                                  |
| D2  | Auth model    | Single `users` table for all roles; `readers` is pure profile                                               |
| D3  | Status fields | Java enums (`UserRole`, `ApprovalStatus`, ...) + `@Enumerated(STRING)`                                      |
| D4  | Normalization | Strict 3NF — no redundant FK snapshot fields                                                                |
| D5  | dueDate       | Added to Borrow; set on approval (borrowDate + 30d)                                                         |
| D6  | Scope         | P2 = 5 entities + JWT; P3 = 3 controllers + 3 services; P4 = 4 controllers + 4 services + 9 DTOs (complete) |

Full rationale in [P2 design spec](../docs/superpowers/specs/2026-06-15-p2-entity-repository-jwt-design.md).

## Phase Progress

| Phase                                      | Status |
| ------------------------------------------ | :----: |
| P1 — Scaffold (Spring Boot + H2 + Swagger) | √ Done |
| P2 — Entity + Repository + JWT             | √ Done |
| P3 — Book & Category CRUD                  | √ Done |
| P4 — Borrow/Return/Renewal/Penalty         | √ Done |
| P5 — News/Messages + Frontend integration  | ○ Next |
| P6 — Docker + Tests + Docs                 |   ○    |

## API Status

| Group      | Path            | Status    |
| ---------- | --------------- | --------- |
| Auth       | `/auth/*`       | √ Live    |
| Books      | `/books/*`      | √ Live    |
| Categories | `/categories/*` | √ Live    |
| Borrows    | `/borrows/*`    | √ Live    |
| Returns    | `/returns/*`    | √ Live    |
| Renewals   | `/renewals/*`   | √ Live    |
| Penalties  | `/penalties/*`  | √ Live    |
| News       | `/news/*`       | ○ Planned |
| Messages   | `/messages/*`   | ○ Planned |
| Upload     | `/upload/*`     | ○ Planned |
| Dashboard  | `/dashboard/*`  | ○ Planned |

## Reference

| Doc                                                                                               | Content                                               |
| ------------------------------------------------------------------------------------------------- | ----------------------------------------------------- |
| [ARCHITECTURE.md](../docs/ARCHITECTURE.md)                                                        | System design, request flow, role model               |
| [API-SPEC.md](../docs/API-SPEC.md)                                                                | REST API contract, old->new path mapping              |
| [DATA-MODEL.md](../docs/DATA-MODEL.md)                                                            | ER diagram, entity definitions, field dictionary      |
| [TEST-CASES.md](../docs/test/TEST-CASES.md)                                                       | Acceptance tests (35 cases)                           |
| [P3 Design Spec](../docs/superpowers/specs/2026-06-16-p3-book-category-auth-controller-design.md) | P3 controller/service/DTO design                      |
| [P3 Plan](../docs/superpowers/plans/2026-06-16-p3-book-category-auth-controller-plan.md)          | P3 implementation plan                                |
| `arc/`                                                                                            | Archived old backend (Spring Boot 2.2 + MyBatis-Plus) |
