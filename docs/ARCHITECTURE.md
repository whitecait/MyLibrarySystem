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
| Frontend | Vue 3.5 + Vite 7 + Element Plus 2     | SPA with 16 views, 12 API modules    |
| Backend  | Spring Boot 3.5 + Spring Data JPA     | REST API, JWT auth, file upload      |
| Database | MySQL 8.0 (prod) / H2 (dev)           | Relational storage                   |
| Auth     | Spring Security + jjwt 0.12           | Stateless JWT Bearer token           |
| Docs     | SpringDoc OpenAPI 2.8                 | Swagger UI at `/api/swagger-ui.html` |
| Build    | Maven 3.9 (backend) / Vite (frontend) | —                                    |
| Deploy   | Docker Compose                        | One-command: `docker compose up`     |

## 2. Backend Architecture

### 2.1 Package Structure (`com.library`)

```
src/main/java/com/library/
├── LibraryApplication.java          # @SpringBootApplication entry
├── common/
│   └── ApiResponse.java             # Unified response {code, msg, data}
├── config/
│   ├── CorsConfig.java              # CORS allow all origins (dev)
│   ├── OpenApiConfig.java           # Swagger/SpringDoc config
│   └── SecurityConfig.java          # Spring Security filter chain
├── entity/                          # JPA @Entity classes
├── repository/                      # Spring Data JPA repositories
├── service/                         # Business logic interfaces
│   └── impl/                        #   implementations
├── controller/                      # @RestController handlers
├── dto/                             # Request/Response DTOs
├── security/                        # JWT provider & auth filter
└── exception/
    ├── ApiException.java            # Business exception
    └── GlobalExceptionHandler.java  # @RestControllerAdvice
```

### 2.2 Request Flow (Phase 2 — with JWT)

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
├── main.js                  # App bootstrap
├── App.vue                  # Root component
├── api/                     # Axios API modules (12 files)
│   ├── user.js              #   /api/users/* — admin auth
│   ├── reader.js            #   /api/readers/* — reader auth & CRUD
│   ├── book.js              #   /api/books/* — book CRUD & search
│   ├── category.js          #   /api/categories/* — category CRUD
│   ├── borrow.js            #   /api/borrows/* — borrow workflow
│   ├── return.js            #   /api/returns/* — return workflow
│   ├── renewal.js           #   /api/renewals/* — renewal workflow
│   ├── penalty.js           #   /api/penalties/* — fines
│   ├── news.js              #   /api/news/* — announcements
│   ├── file.js              #   /api/upload/* — file upload
│   └── home.js              #   /api/dashboard/* — statistics
├── views/                   # Page components (16 views)
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
│   ├── AboutView.vue
│   └── ...
├── layouts/MainLayout.vue   # App shell (sidebar + header)
├── router/index.js          # Vue Router with route guards
└── utils/
    ├── request.js           # Axios instance with interceptors
    └── index.js             # Utility helpers
```

### 3.2 Axios Request Flow

```
View Component
  │  import { getBookPage } from '@/api/book'
  ▼
api/book.js          →  calls request.get('/books/page', { params })
  │
  ▼
utils/request.js     →  Axios instance
  │  baseURL: '/api' (dev proxy via Vite) or env-configured
  │  request interceptor: attach JWT from localStorage
  │  response interceptor: unwrap ApiResponse, handle 401 → redirect /login
  │
  ▼
Backend :8080/api
```

### 3.3 Route Design

| Route                  | View                      | Public? | Role         |
| ---------------------- | ------------------------- | :-----: | ------------ |
| `/login`               | LoginView                 |   ✅    | —            |
| `/register`            | LoginView (register mode) |   ✅    | —            |
| `/home`                | HomeView                  |   ❌    | Admin/Reader |
| `/book-management`     | BookManagement            |   ❌    | Admin        |
| `/book-browsing`       | BookBrowsing              |   ❌    | Reader       |
| `/category-management` | CategoryManagement        |   ❌    | Admin        |
| `/reader-management`   | ReaderManagement          |   ❌    | Admin        |
| `/reader-profile`      | ReaderProfile             |   ❌    | Reader       |
| `/my-borrowing`        | MyBorrowing               |   ❌    | Reader       |
| `/my-fines`            | MyFines                   |   ❌    | Reader       |
| `/borrow-approval`     | BorrowApproval            |   ❌    | Admin        |
| `/return-approval`     | ReturnApproval            |   ❌    | Admin        |
| `/renewal-approval`    | RenewalApproval           |   ❌    | Admin        |
| `/penalty-management`  | PenaltyManagement         |   ❌    | Admin        |
| `/news-management`     | NewsManagement            |   ❌    | Admin        |

## 4. Dual-Role Model

| Capability                       |        Admin         |           Reader           |
| -------------------------------- | :------------------: | :------------------------: |
| Login                            | ✅ username/password |   ✅ studentId/password    |
| Register                         |   ❌ (pre-seeded)    |      ✅ self-register      |
| Manage books (CRUD)              |          ✅          |             ❌             |
| Browse / search books            |          ✅          |             ✅             |
| Borrow book                      |          ❌          | ✅ (apply → admin approve) |
| Return book                      |          ❌          | ✅ (apply → admin confirm) |
| Renew book                       |          ❌          | ✅ (apply → admin approve) |
| Approve borrows/returns/renewals |          ✅          |             ❌             |
| View own borrows/fines           |          ❌          |             ✅             |
| Manage penalties                 |          ✅          |             ❌             |
| Manage readers (CRUD)            |          ✅          |             ❌             |
| Post announcements               |          ✅          |             ❌             |
| Send messages                    |          ❌          |             ✅             |
| Reply to messages                |          ✅          |             ❌             |
| View dashboard statistics        |          ✅          |             ❌             |

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
