# MyLibrarySystem — Full-Stack Library Management System

A full-stack library management system with dual-role support (admin & reader),
covering the complete lifecycle of book borrowing, returning, renewal, and fine management.

```
User Browser → Vue 3 Frontend (Vite) → Spring Boot Backend → MySQL
                                            ↑
                                    Docker Compose (one-command startup)
```

## Features

| Module              | Description                                                     |
| ------------------- | --------------------------------------------------------------- |
| **Auth**            | JWT-based stateless authentication for admin & reader roles     |
| **Book Management** | CRUD, category management, cover image upload, paginated search |
| **Borrowing**       | Apply → Approve → Borrow → Return → Renewal workflow            |
| **Fine System**     | Auto-calculated overdue fines, payment tracking                 |
| **Announcements**   | CRUD for library news/notices                                   |
| **Dashboard**       | Admin statistics: total counts, category grouping               |
| **Messages**        | Reader feedback submission & admin reply                        |

## Tech Stack

| Layer    | Technology                       |
| -------- | -------------------------------- |
| Frontend | Vue 3, Vite, Element Plus, Axios |
| Backend  | Spring Boot 3.5, Spring Data JPA |
| Database | MySQL 8.0 (prod) / H2 (dev)      |
| Auth     | JWT (jjwt) + Spring Security     |
| API Docs | SpringDoc OpenAPI (Swagger)      |
| Testing  | JUnit 5, MockMvc                 |
| Build    | Maven (backend), Vite (frontend) |
| Deploy   | Docker Compose                   |

## Project Structure

```
MyLibrarySystem/
├── library-server/                # Spring Boot 3.5 backend
│   ├── arc/                       #   Archived old scaffold (MyBatis-Plus reference)
│   ├── src/main/java/com/library/
│   │   ├── LibraryApplication.java
│   │   ├── common/ApiResponse.java
│   │   ├── config/                #   Cors, Security (role-based), Swagger
│   │   ├── exception/             #   ApiException + GlobalExceptionHandler
│   │   ├── entity/                #   5 JPA entities + 4 enums
│   │   ├── repository/            #   5 Spring Data repositories
│   │   ├── service/               #   Auth, Book, Category (3 impls)
│   │   ├── controller/            #   Auth, Book, Category (3 controllers)
│   │   ├── dto/                   #   7 Request/Response DTOs
│   │   └── security/              #   JwtProvider + JwtAuthFilter
│   ├── src/main/resources/
│   │   └── application.yml        #   H2 (dev) + MySQL (prod) profiles
│   └── pom.xml
├── library-frontend/              # Vue 3 + Vite frontend
│   ├── src/
│   │   ├── api/                   #   11 API modules (old pinyin paths, not yet migrated)
│   │   ├── views/                 #   15 view pages
│   │   ├── router/                #   Vue Router config (token guard, no role guard)
│   │   ├── layouts/               #   MainLayout
│   │   └── utils/                 #   Axios request wrapper (custom token header)
│   ├── package.json
│   └── vite.config.js
├── docs/                          # Technical documentation
│   ├── ARCHITECTURE.md            #   System design & role model
│   ├── API-SPEC.md                #   REST API contract & path mapping
│   ├── DATA-MODEL.md              #   ER diagram & entity field dictionary
│   ├── test/TEST-CASES.md         #   Acceptance test cases (35 cases, 11 modules)
│   ├── arc/                       #   Archived old reference docs
│   └── superpowers/               #   Design specs & implementation plans
│       ├── specs/                 #     Per-phase design documents
│       └── plans/                 #     Per-phase implementation plans
├── Logs/                          # Runtime logs (.gitkeep)
├── .gitignore
├── .codegraph/                    # Code intelligence index
└── README.md
```

## Quick Start

### Prerequisites

- JDK 17+
- Node.js 20+
- MySQL 8.0 (optional — H2 dev mode works without it)

### Backend (library-server)

```bash
cd library-server

# Dev mode: H2 in-memory database, no MySQL needed
./mvnw spring-boot:run
# → Swagger UI:  http://localhost:8080/api/swagger-ui.html
# → H2 Console:  http://localhost:8080/api/h2-console

# Prod mode: MySQL
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

### Frontend (library-frontend)

```bash
cd library-frontend
npm install
npm run dev
# → http://localhost:5173
```

## API Overview

Full interactive docs at `http://localhost:8080/api/swagger-ui.html`.

> **Status:** `/api/auth/*`, `/api/books/*`, `/api/categories/*` live (P3 done).
> `/api/borrows/*`, `/api/returns/*`, `/api/renewals/*`, `/api/penalties/*` live (P4 done).
> `/api/news/*`, `/api/messages/*`, `/api/upload/*`, `/api/dashboard/*` planned (P5).

| Endpoint Group      | Description                         | Old Path          |
| ------------------- | ----------------------------------- | ----------------- |
| `/api/users/*`      | Admin login & management            | `/users/*`        |
| `/api/readers/*`    | Reader registration, login, profile | `/duzhe/*`        |
| `/api/books/*`      | Book CRUD & paginated search        | `/tushuxinxi/*`   |
| `/api/categories/*` | Category management                 | `/tushuleibie/*`  |
| `/api/borrows/*`    | Borrow application & approval       | `/tushujieyue/*`  |
| `/api/returns/*`    | Return application & approval       | `/tushuguihai/*`  |
| `/api/renewals/*`   | Renewal application & approval      | `/tushuxujie/*`   |
| `/api/penalties/*`  | Fine query & payment                | `/chaoqifakuan/*` |
| `/api/news/*`       | Announcement CRUD                   | `/news/*`         |
| `/api/messages/*`   | Reader messages & admin reply       | `/messages/*`     |
| `/api/upload/*`     | Cover image upload                  | `/file/*`         |
| `/api/dashboard/*`  | Dashboard statistics                | `/common/*`       |

## Phase Progress

| Phase                                          | Status                                                              |
| ---------------------------------------------- | ------------------------------------------------------------------- |
| Phase 1 — Backend Scaffold                     | √ Complete — Spring Boot 3.5, H2, Swagger, compile & start verified |
| Phase 2 — Auth (JWT + Spring Security)         | √ Complete — 5 entities, 4 enums, 5 repos, JWT auth filter verified |
| Phase 3 — Book & Category Management API       | √ Complete — 3 controllers, 3 services, 4 DTOs, role-based access   |
| Phase 4 — Borrow/Return/Renewal/Penalty        | √ Complete — 4 controllers, 4 services, 3 entities, 3 repos, 9 DTOs |
| Phase 5 — News/Messages + Frontend Integration | ○ Pending                                                           |
| Phase 6 — Docker + Tests + Docs                | ○ Pending                                                           |

> See [Docs/internship-plan-main.md](../Docs/internship-plan-main.md) for detailed plan.

## Git

```bash
# Repo: Projects/MyLibrarySystem
# Branch: master
# Key commits:
#   1c8c347  feat(p4): update SecurityConfig with P4 endpoint rules
#   a542310  feat(p4): add Borrow/Return/Renewal/Penalty controllers (15 endpoints)
#   5fb273a  feat(p4): add PenaltyService with pay + query
#   754eebe  feat(p4): add RenewalService with auto-approve + 60d cap
#   8971293  feat(p4): add ReturnService with auto-fine on overdue return
#   935c899  feat(p4): add BorrowService with auto-approval + pending tri-state
#   9830dfb  test(p4): add TestAuthHelper for JWT-authenticated MockMvc tests
#   686c8ac  feat(p4): add all P4 DTOs (Borrow/Return/Renewal/Penalty/BatchApproval/Pay)
#   5945435  feat(p4): add BorrowReturn/Renewal/Penalty repositories
#   a411904  feat(p4): add Renewal + Penalty entities (strict 3NF, borrowId FK)
#   3fd0734  feat(p4): add BorrowReturn entity (strict 3NF, borrowId FK)
#   04ef668  feat(p4): add Borrow.durationDays field
#   7f78846  feat(p4): add ApprovalStatus.待审核 + LibraryProperties config
#   90094a5  feat(p3): update SecurityConfig — role-based access control
#   deb1323  feat(p3): add CategoryController — 6 endpoints
#   00bd727  feat(p3): add BookController — 6 active + 3 deprecated endpoints
#   2dac8ad  feat(p3): add AuthController — /auth/login and /auth/register
#   5ff4e03  feat(p3): add CategoryService — CRUD with delete guard
#   8057351  feat(p3): add BookService — Specification query, batch category lookup
#   9a9302d  feat(p3): add AuthService — BCrypt+JWT login, @Transactional register
#   8784001  docs: sync DATA-MODEL, READMEs with P2 implementation
#   ccc18f5  feat(p2): add JwtProvider + JwtAuthFilter, wire into SecurityConfig
#   303248c  feat(p2): add 5 repositories + 3 DTOs
#   6714786  feat(p2): add 5 JPA entities
#   038e20a  feat(p2): add enum classes
#   a8562cf  feat(server): Phase 1 scaffold
```

## Sub-READMEs

| README                                                   | Content                                             |
| -------------------------------------------------------- | --------------------------------------------------- |
| [library-server/README.md](library-server/README.md)     | Backend structure, design decisions, phase progress |
| [library-frontend/README.md](library-frontend/README.md) | Frontend state, migration checklist, quick start    |
