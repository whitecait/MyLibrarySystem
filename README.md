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
│   │   ├── config/                #   Cors, Security, Swagger
│   │   ├── exception/             #   GlobalExceptionHandler
│   │   ├── entity/                #   JPA entities
│   │   ├── repository/            #   Spring Data repositories
│   │   ├── service/               #   Business logic
│   │   ├── controller/            #   REST controllers
│   │   ├── dto/                   #   Request/Response DTOs
│   │   └── security/              #   JWT provider & filters
│   ├── src/main/resources/
│   │   └── application.yml        #   H2 (dev) + MySQL (prod) profiles
│   └── pom.xml
├── library-frontend/              # Vue 3 + Vite frontend
│   ├── src/
│   │   ├── api/                   #   12 API modules (book, borrow, reader, ...)
│   │   ├── views/                 #   16 view pages
│   │   ├── router/                #   Vue Router config
│   │   ├── layouts/               #   MainLayout
│   │   └── utils/                 #   Axios request wrapper
│   ├── package.json
│   └── vite.config.js
├── docs/                          # Entity docs & API reference from old project
│   ├── Entity.md
│   ├── API.ReadMe.md
│   ├── FRONTEND_MODULES.md
│   └── MIGRATION.md
├── Logs/                          # Runtime logs (.gitkeep)
├── .gitignore
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

| Phase                                          | Status                                                               |
| ---------------------------------------------- | -------------------------------------------------------------------- |
| Phase 1 — Backend Scaffold                     | ✅ Complete — Spring Boot 3.5, H2, Swagger, compile & start verified |
| Phase 2 — Auth (JWT + Spring Security)         | ⬜ Pending                                                           |
| Phase 3 — Book Management API                  | ⬜ Pending                                                           |
| Phase 4 — Borrow/Return/Renewal/Penalty        | ⬜ Pending                                                           |
| Phase 5 — News/Messages + Frontend Integration | ⬜ Pending                                                           |
| Phase 6 — Docker + Tests + Docs                | ⬜ Pending                                                           |

> See [Docs/internship-plan-main.md](../Docs/internship-plan-main.md) for detailed plan.

## Git

```bash
# Repo: Projects/MyLibrarySystem
# Branch: master
# Key commits:
#   a8562cf  feat(server): Phase 1 scaffold
#   4bcad1d  chore: init MyLibrarySystem workspace
```
