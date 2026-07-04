# ARCHITECTURE — System Architecture & Module Design

> Bilingual: EN technical terms + CN explanations.
> Target audience: interviewers & developers reviewing this project.

## 1. System Overview

```
┌──────────────┐     HTTP/REST      ┌──────────────┐     JPA/SQL      ┌────────┐
│  Vue 3 SPA   │ -----------------> │  Spring Boot │ ---------------> │ MySQL  │
│  (Vite)      │ <----------------- │  3.5 (Java)  │ <--------------- │  8.0   │
│  :5173 (dev) │    JSON/ApiResponse│  :8080/api   │                  │  :3306 │
└──────────────┘                    └──────────────┘                  └────────┘
                                          │
                                          │ Dev fallback
                                          ▼
                                    ┌──────────┐
                                    │ H2 (mem) │
                                    └──────────┘
```

| Layer    | Technology                            | Role                                 |
| -------- | ------------------------------------- | ------------------------------------ |
| Frontend | Vue 3.5 + Vite 7 + Element Plus 2     | SPA with 15 views, 11 API modules    |
| Backend  | Spring Boot 3.5 + Spring Data JPA     | REST API, JWT auth, file upload      |
| Database | MySQL 8.0 (prod) / H2 (dev)           | Relational storage                   |
| Auth     | Spring Security + jjwt 0.12           | Stateless JWT Bearer token           |
| Docs     | SpringDoc OpenAPI 2.8                 | Swagger UI at `/api/swagger-ui.html` |
| Build    | Maven 3.9 (backend) / Vite (frontend) | —                                    |
| Deploy   | Docker Compose                        | One-command: `docker compose up`     |

## 2. Backend Architecture

### 2.1 Package Structure (`com.library`)

> **Current status:** Phase 3 complete. Auth, Book, Category controllers/services live.
> Borrow/Return/Renewal/Penalty/News/Message planned (P4-P5).

```
src/main/java/com/library/
├── LibraryApplication.java          # @SpringBootApplication entry
├── common/
│   └── ApiResponse.java             # Unified response {code, msg, data}
├── config/
│   ├── CorsConfig.java              # CORS allow all origins (dev)
│   ├── OpenApiConfig.java           # Swagger/SpringDoc config
│   └── SecurityConfig.java          # Spring Security filter chain (role-based)
├── entity/                          # 5 JPA @Entity + 4 enums
├── repository/                      # 5 Spring Data JPA repositories
├── service/                         # AuthService, BookService, CategoryService
│   └── impl/                        #   3 implementations
├── controller/                      # AuthController, BookController, CategoryController
├── dto/                             # 7 Request/Response DTOs
├── security/                        # JwtProvider + JwtAuthFilter
└── exception/
    ├── ApiException.java            # Business exception
    └── GlobalExceptionHandler.java  # @RestControllerAdvice
```

### 2.2 Request Flow (Phase 3 — JWT + Role-Based Access)

```
Client Request
  │
  ▼
CorsFilter          →  Allow cross-origin
  │
  ▼
JwtAuthFilter       →  Extract token → validate → set SecurityContext
  │                     (skip for /login, /register, /swagger-ui/**)
  ▼
DispatcherServlet   →  Route to @RestController
  │
  ▼
Controller          →  Validate input (@Valid), call Service
  │
  ▼
Service             →  Business logic, transaction management
  │
  ▼
Repository          →  Spring Data JPA → Hibernate → SQL
  │
  ▼
ApiResponse<T>      →  Jackson serializes to JSON → client
  │
  ▼
GlobalExceptionHandler  →  Catch ApiException, validation errors → ApiResponse
```

### 2.3 Unified Response Format

All endpoints return:

```json
{
  "code": 0, // 0 = success, 1 = business error
  "msg": "success", // human-readable message
  "data": {} // payload (null if error)
}
```

## 3. Frontend Architecture

### 3.1 Source Layout (`library-frontend/src/`)

```
src/
├── main.js                  # App bootstrap (Vue + Element Plus + Router)
├── App.vue                  # Root component
├── api/                     # Axios API modules (11 files, old pinyin paths)
│   ├── user.js              #   /users/* — admin auth (old)
│   ├── reader.js            #   /duzhe/* — reader auth & CRUD (old)
│   ├── book.js              #   /tushuxinxi/* — book CRUD & search (old)
│   ├── category.js          #   /tushuleibie/* — category CRUD (old)
│   ├── borrow.js            #   /tushujieyue/*, /chaoqifakuan/* — borrows + fines (old)
│   ├── return.js            #   /tushuguihai/* — return workflow (old)
│   ├── renewal.js           #   /tushuxujie/* — renewal workflow (old)
│   ├── penalty.js           #   /chaoqifakuan/* — fines (old)
│   ├── news.js              #   /news/* — announcements (old)
│   ├── file.js              #   /file/upload — file upload (old)
│   └── home.js              #   mixed old paths — dashboard stats (old)
│   # messages.js — NOT YET CREATED (planned P5)
├── views/                   # Page components (15 views)
│   ├── LoginView.vue        #   Reader/Admin login & register
│   ├── HomeView.vue         #   Dashboard
│   ├── BookManagement.vue   #   Admin: book CRUD
│   ├── BookBrowsing.vue     #   Reader: browse & borrow
│   ├── CategoryManagement.vue
│   ├── ReaderManagement.vue
│   ├── ReaderProfile.vue    #   Reader: my profile
│   ├── MyBorrowing.vue      #   Reader: my borrows
│   ├── MyFines.vue          #   Reader: my penalties
│   ├── BorrowApproval.vue   #   Admin: approve borrows
│   ├── ReturnApproval.vue   #   Admin: approve returns
│   ├── RenewalApproval.vue  #   Admin: approve renewals
│   ├── PenaltyManagement.vue#   Admin: manage fines
│   ├── NewsManagement.vue   #   Admin: announcement CRUD
│   └── AboutView.vue
├── layouts/MainLayout.vue   # App shell (sidebar + header)
├── router/index.js          # Vue Router (token guard, no role guard)
└── utils/
    ├── request.js           # Axios instance (custom `token` header — needs fix)
    └── index.js             # Utility helpers
```

### 3.2 Axios Request Flow

```
View Component
  │  import { getBookPage } from '@/api/book'
  ▼
api/book.js          →  calls request.get('/tushuxinxi/page', { params })
  │                     (currently OLD pinyin path — pending migration)
  ▼
utils/request.js     →  Axios instance
  │  baseURL: VITE_API_BASE_URL (env-configured)
  │  request interceptor: attach JWT as custom `token` header (NEEDS FIX: should be Bearer)
  │  response interceptor: unwrap ApiResponse, handle 401 → redirect /login
  │
  ▼
Vite Dev Proxy       →  /api -> :8080/springboot8393c (NEEDS FIX: backend uses /api)
  │
  ▼
Backend :8080/api
```

> **Known issues:** (1) JWT sent as `token` custom header — backend expects `Authorization: Bearer`. (2) Vite proxy rewrites `/api` to `/springboot8393c` — old context path no longer used.

### 3.3 Route Design

| Route                  | View                      | Public? | Role         |
| ---------------------- | ------------------------- | :-----: | ------------ |
| `/login`               | LoginView                 |    √    | —            |
| `/register`            | LoginView (register mode) |    √    | —            |
| `/home`                | HomeView                  |    ×    | Admin/Reader |
| `/book-management`     | BookManagement            |    ×    | Admin        |
| `/book-browsing`       | BookBrowsing              |    ×    | Reader       |
| `/category-management` | CategoryManagement        |    ×    | Admin        |
| `/reader-management`   | ReaderManagement          |    ×    | Admin        |
| `/reader-profile`      | ReaderProfile             |    ×    | Reader       |
| `/my-borrowing`        | MyBorrowing               |    ×    | Reader       |
| `/my-fines`            | MyFines                   |    ×    | Reader       |
| `/borrow-approval`     | BorrowApproval            |    ×    | Admin        |
| `/return-approval`     | ReturnApproval            |    ×    | Admin        |
| `/renewal-approval`    | RenewalApproval           |    ×    | Admin        |
| `/penalty-management`  | PenaltyManagement         |    ×    | Admin        |
| `/news-management`     | NewsManagement            |    ×    | Admin        |

## 4. Dual-Role Model

| Capability                       |        Admin        |          Reader           |
| -------------------------------- | :-----------------: | :-----------------------: |
| Login                            | √ username/password |   √ studentId/password    |
| Register                         |   × (pre-seeded)    |      √ self-register      |
| Manage books (CRUD)              |          √          |             ×             |
| Browse / search books            |          √          |             √             |
| Borrow book                      |          ×          | √ (apply → admin approve) |
| Return book                      |          ×          | √ (apply → admin confirm) |
| Renew book                       |          ×          | √ (apply → admin approve) |
| Approve borrows/returns/renewals |          √          |             ×             |
| View own borrows/fines           |          ×          |             √             |
| Manage penalties                 |          √          |             ×             |
| Manage readers (CRUD)            |          √          |             ×             |
| Post announcements               |          √          |             ×             |
| Send messages                    |          ×          |             √             |
| Reply to messages                |          √          |             ×             |
| View dashboard statistics        |          √          |             ×             |

## 5. Project File Tree (Abbreviated)

```
MyLibrarySystem/
├── library-server/           # Spring Boot backend (Java 17, Maven)
│   ├── src/main/java/com/library/
│   │   ├── LibraryApplication.java
│   │   ├── {common,config,entity,repository,service,controller,dto,security,exception}/
│   │   └── ...
│   ├── src/main/resources/application.yml
│   ├── pom.xml
│   └── arc/                  #   Archived old scaffold (reference)
├── library-frontend/         # Vue 3 frontend (Node 20, Vite)
│   ├── src/{api,views,router,layouts,utils}/
│   ├── package.json
│   └── vite.config.js
├── docs/                     # Technical documentation
│   ├── ARCHITECTURE.md       #   This file
│   ├── API-SPEC.md           #   API specification
│   ├── DATA-MODEL.md         #   Data model & ER diagram
│   └── test/                 #   Test cases
├── Logs/                     # Runtime logs
├── README.md                 # Project overview (interview-facing)
└── .gitignore
```
