# library-server - Spring Boot Backend

Spring Boot backend for MyLibrarySystem.

## Current Actual State

CodeGraph verified on 2026-07-04.

- External API base path: `/api`.
- Backend P1-P4 scope is implemented/current.
- Current controllers: `AuthController`, `BookController`, `CategoryController`, `BorrowController`, `ReturnController`, `RenewalController`, `PenaltyController`.
- Current entities: `User`, `Reader`, `Book`, `Category`, `Borrow`, `BorrowReturn`, `Renewal`, `Penalty`.
- Current repositories: 8 Spring Data repositories for the current entities.
- Current services: 7 service interfaces and 7 service implementations.
- Current DTOs: 16 request/response/helper DTO classes.
- Current automated test source: 22 `@Test` methods across borrow, return, renewal, penalty, and library properties tests.
- Last recorded Surefire result: `Tests run: 22, Failures: 0, Errors: 0, Skipped: 0`.
- P5 backend controllers are not present in `com.library`: news, messages, upload/file, and dashboard are planned or legacy-only.
- Current password handling is raw request password + backend BCrypt. SHA256 pre-hashing is not the current backend contract.

## Quick Start

```powershell
cd library-server
.\mvnw.cmd spring-boot:run
```

Local URLs:

- Swagger UI: `http://localhost:8080/api/swagger-ui.html`
- H2 console: `http://localhost:8080/api/h2-console`

Production profile:

```powershell
cd library-server
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=prod
```

## Current Structure

```text
src/main/java/com/library/
|-- LibraryApplication.java
|-- common/ApiResponse.java
|-- config/
|   |-- CorsConfig.java
|   |-- LibraryProperties.java
|   |-- OpenApiConfig.java
|   `-- SecurityConfig.java
|-- controller/
|   |-- AuthController.java
|   |-- BookController.java
|   |-- CategoryController.java
|   |-- BorrowController.java
|   |-- ReturnController.java
|   |-- RenewalController.java
|   `-- PenaltyController.java
|-- dto/
|   |-- LoginRequest.java
|   |-- RegisterRequest.java
|   |-- LoginResponse.java
|   |-- BookRequest.java
|   |-- BookResponse.java
|   |-- CategoryRequest.java
|   |-- CategoryResponse.java
|   |-- BorrowRequest.java
|   |-- BorrowResponse.java
|   |-- ReturnRequest.java
|   |-- ReturnResponse.java
|   |-- RenewalRequest.java
|   |-- RenewalResponse.java
|   |-- PenaltyResponse.java
|   |-- BatchApproval.java
|   `-- PayRequest.java
|-- entity/
|   |-- User.java
|   |-- Reader.java
|   |-- Book.java
|   |-- Category.java
|   |-- Borrow.java
|   |-- BorrowReturn.java
|   |-- Renewal.java
|   |-- Penalty.java
|   `-- enums/
|-- repository/
|-- security/
|-- service/
|   `-- impl/
`-- exception/
```

## API Status

| Group      | Controller path   | External path         | Status      |
| ---------- | ----------------- | --------------------- | ----------- |
| Auth       | `/auth/*`       | `/api/auth/*`       | Implemented |
| Books      | `/books/*`      | `/api/books/*`      | Implemented |
| Categories | `/categories/*` | `/api/categories/*` | Implemented |
| Borrows    | `/borrows/*`    | `/api/borrows/*`    | Implemented |
| Returns    | `/returns/*`    | `/api/returns/*`    | Implemented |
| Renewals   | `/renewals/*`   | `/api/renewals/*`   | Implemented |
| Penalties  | `/penalties/*`  | `/api/penalties/*`  | Implemented |
| News       | n/a               | `/api/news/*`       | Planned     |
| Messages   | n/a               | `/api/messages/*`   | Planned     |
| Upload     | n/a               | `/api/upload/*`     | Planned     |
| Dashboard  | n/a               | `/api/dashboard/*`  | Planned     |

## Key Design Decisions

| Decision           | Current choice                                                                                            |
| ------------------ | --------------------------------------------------------------------------------------------------------- |
| API response       | `ApiResponse<T>` with `{ code, msg, data }`; success code is `0`                                    |
| Auth model         | Single`users` table for admin and reader login; `readers` stores profile data                         |
| JWT transport      | `Authorization: Bearer <token>`                                                                         |
| Password handling  | Backend BCrypt over the request password                                                                  |
| Data normalization | P4 return, renewal, and penalty entities store`borrowId`; display fields are assembled in response DTOs |
| P5 scope           | News, messages, upload, dashboard are not current backend contracts                                       |

Historical P2/P3/P4 specs and plans are closed implementation records. They may explain design lineage, but the current backend contract is this README plus the baseline docs under `docs/`.

## Test Status

| Test area          | `@Test` count |
| ------------------ | --------------: |
| Borrow service     |               5 |
| Return service     |               5 |
| Renewal service    |               7 |
| Penalty service    |               4 |
| Library properties |               1 |
| Total              |              22 |

Fresh test execution in this workspace currently requires `JAVA_HOME` to be set.

## References

| Doc                                     | Purpose                                                  |
| --------------------------------------- | -------------------------------------------------------- |
| `../docs/API-SPEC.md`                 | Current REST API contract and legacy migration notes     |
| `../docs/ARCHITECTURE.md`             | Current system architecture and module status            |
| `../docs/DATA-MODEL.md`               | Current entity model and planned/legacy model separation |
| `../docs/test/TEST-CASES.md`          | Acceptance planning matrix                               |
| `../docs/test/TEST-CASES-BOUNDARY.md` | Boundary test planning                                   |
| `arc/`                                | Archived old backend scaffold reference                  |
