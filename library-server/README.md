# library-server — Spring Boot Backend

Library Management System backend. Phase 2 — entity/repository/JWT complete.

## Reference Paths

| Path                           | Description                                                                    |
| ------------------------------ | ------------------------------------------------------------------------------ |
| `arc/`                         | Archived old backend (Spring Boot 2.2 + MyBatis-Plus + Shiro) — reference only |
| `../docs/arc/Entity.md`        | Old entity field definitions (pinyin)                                          |
| `../docs/arc/API.ReadMe.md`    | Old API endpoint documentation                                                 |
| `../docs/DATA-MODEL.md`        | New ER diagram & entity field dictionary (current target)                      |
| `../docs/API-SPEC.md`          | New REST API specification & old→new path mapping                              |
| `../docs/ARCHITECTURE.md`      | System architecture & role model                                               |
| `../library-frontend/src/api/` | Frontend API modules (API contract source of truth)                            |

## Restructuring Map

### Entity / Table renaming

| Old (Chinese pinyin) | New (English)  | Old Table    | New Table     |
| -------------------- | -------------- | ------------ | ------------- |
| DuzheEntity          | `Reader`       | duzhe        | readers       |
| UsersEntity          | `User`         | users        | users         |
| TushuxinxiEntity     | `Book`         | tushuxinxi   | books         |
| TushuleibieEntity    | `Category`     | tushuleibie  | categories    |
| TushujieyueEntity    | `Borrow`       | tushujieyue  | borrows       |
| TushuguihaiEntity    | `BorrowReturn` | tushuguihai  | returns       |
| TushuxujieEntity     | `Renewal`      | tushuxujie   | renewals      |
| ChaoqifakuanEntity   | `Penalty`      | chaoqifakuan | penalties     |
| JieyuekaEntity       | `LibraryCard`  | jieyueka     | library_cards |
| NewsEntity           | `News`         | news         | news          |
| MessagesEntity       | `Message`      | messages     | messages      |

### API path mapping (old → new)

| Old Path          | New Path            |
| ----------------- | ------------------- |
| `/duzhe/*`        | `/api/readers/*`    |
| `/users/*`        | `/api/users/*`      |
| `/tushuxinxi/*`   | `/api/books/*`      |
| `/tushuleibie/*`  | `/api/categories/*` |
| `/tushujieyue/*`  | `/api/borrows/*`    |
| `/tushuguihai/*`  | `/api/returns/*`    |
| `/tushuxujie/*`   | `/api/renewals/*`   |
| `/chaoqifakuan/*` | `/api/penalties/*`  |
| `/jieyueka/*`     | `/api/cards/*`      |
| `/news/*`         | `/api/news/*`       |
| `/messages/*`     | `/api/messages/*`   |
| `/file/*`         | `/api/upload/*`     |
| `/common/*`       | `/api/dashboard/*`  |
| `/option/*`       | `/api/options/*`    |

## Project Structure

```
library-server/
├── arc/                          # Archived old backend (reference)
├── src/main/java/com/library/
│   ├── LibraryApplication.java   # Entry point
│   ├── common/
│   │   └── ApiResponse.java      # Unified response wrapper
│   ├── config/
│   │   ├── CorsConfig.java       # CORS
│   │   ├── OpenApiConfig.java    # Swagger/OpenAPI
│   │   └── SecurityConfig.java   # Spring Security + JWT filter chain
│   ├── entity/
│   │   ├── User.java             # Unified auth (admin + reader)
│   │   ├── Reader.java           # Reader profile (no auth fields)
│   │   ├── Book.java             # Book with categoryId FK
│   │   ├── Category.java         # Book category
│   │   ├── Borrow.java           # Borrow record (3NF, with dueDate)
│   │   └── enums/
│   │       ├── UserRole.java
│   │       ├── ApprovalStatus.java
│   │       ├── PaymentStatus.java
│   │       └── Gender.java
│   ├── repository/
│   │   ├── UserRepository.java
│   │   ├── ReaderRepository.java
│   │   ├── BookRepository.java
│   │   ├── CategoryRepository.java
│   │   └── BorrowRepository.java
│   ├── dto/
│   │   ├── LoginRequest.java
│   │   ├── RegisterRequest.java
│   │   └── LoginResponse.java
│   ├── security/
│   │   ├── JwtProvider.java      # Token generation & validation
│   │   └── JwtAuthFilter.java    # OncePerRequestFilter
│   ├── service/                  # Business logic (Phase 3+)
│   │   └── impl/
│   ├── controller/               # REST controllers (Phase 3+)
│   └── exception/
│       ├── ApiException.java
│       └── GlobalExceptionHandler.java
├── src/main/resources/
│   └── application.yml           # H2 (dev) + MySQL (prod) profiles + JWT config
├── src/test/java/com/library/
├── .mvn/wrapper/
├── mvnw / mvnw.cmd
└── pom.xml                       # Spring Boot 3.5 + JPA + Security + jjwt + Swagger
```

## Quick Start

### Dev mode (H2 in-memory, no MySQL required)

```bash
cd library-server
mvn spring-boot:run
```

- **Swagger UI:** http://localhost:8080/api/swagger-ui.html
- **H2 Console:** http://localhost:8080/api/h2-console

### Prod mode (MySQL)

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

> Requires MySQL 8.0 running with database `library` created.

## Phase Progress

| Phase                                          | Status     |
| ---------------------------------------------- | ---------- |
| Phase 1 — Backend Scaffold                     | √ Complete |
| Phase 2 — Auth (JWT + Spring Security)         | √ Complete |
| Phase 3 — Book Management API                  | ○ Pending  |
| Phase 4 — Borrow/Return/Renewal/Penalty        | ○ Pending  |
| Phase 5 — News/Messages + Frontend integration | ○ Pending  |
| Phase 6 — Docker + Tests + Docs                | ○ Pending  |
