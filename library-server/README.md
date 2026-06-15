# library-server — Spring Boot Backend

Library Management System backend. **Phase 2 complete** — entity/repository/JWT layer built.

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
├── config/{Cors,Security,OpenApi}       # CORS, JWT filter chain, Swagger
├── entity/
│   ├── User.java                        # Unified auth (admin + reader)
│   ├── Reader.java                      # Profile only (no password)
│   ├── Book.java, Category.java         # Book catalog
│   ├── Borrow.java                      # 3NF + dueDate
│   └── enums/{UserRole,ApprovalStatus,PaymentStatus,Gender}
├── repository/{User,Reader,Book,Category,Borrow}Repository
├── dto/{LoginRequest,RegisterRequest,LoginResponse}
├── security/{JwtProvider,JwtAuthFilter}
├── service/                             # Phase 3+
├── controller/                          # Phase 3+
└── exception/{ApiException,GlobalExceptionHandler}
```

## Key Design Decisions

| # | Decision | Choice |
|---|----------|--------|
| D1 | Table names | English (`users`, `readers`, `books`, ...) |
| D2 | Auth model | Single `users` table for all roles; `readers` is pure profile |
| D3 | Status fields | Java enums (`UserRole`, `ApprovalStatus`, ...) + `@Enumerated(STRING)` |
| D4 | Normalization | Strict 3NF — no redundant FK snapshot fields |
| D5 | dueDate | Added to Borrow; set on approval (borrowDate + 30d) |
| D6 | Scope | P2 = 5 entities only; BorrowReturn/Renewal/Penalty deferred to P4 |

Full rationale in [P2 design spec](../docs/superpowers/specs/2026-06-15-p2-entity-repository-jwt-design.md).

## Phase Progress

| Phase | Status |
|-------|:------:|
| P1 — Scaffold (Spring Boot + H2 + Swagger) | √ Done |
| P2 — Entity + Repository + JWT | √ Done |
| P3 — Book & Category CRUD | ○ Next |
| P4 — Borrow/Return/Renewal/Penalty | ○ |
| P5 — News/Messages + Frontend integration | ○ |
| P6 — Docker + Tests + Docs | ○ |

## Reference

| Doc | Content |
|-----|---------|
| [ARCHITECTURE.md](../docs/ARCHITECTURE.md) | System design, request flow, role model |
| [API-SPEC.md](../docs/API-SPEC.md) | REST API contract, old->new path mapping |
| [DATA-MODEL.md](../docs/DATA-MODEL.md) | ER diagram, entity definitions, field dictionary |
| [TEST-CASES.md](../docs/test/TEST-CASES.md) | Acceptance tests (35 cases) |
| `arc/` | Archived old backend (Spring Boot 2.2 + MyBatis-Plus) |
