> **Archive Status:** Historical Reference
> **Original Path:** `docs/ARCHITECTURE.md`
> **Archived On:** `2026-07-13`
> **Historical Purpose:** 返工前契约或实现记录
> **Superseded By:** `docs/README.md`
> **Authority:** 非规范性；不得作为当前需求、接口、数据或验收契约

# ARCHITECTURE - Current System Architecture

This file describes the current architecture baseline for MyLibrarySystem.

## Current Actual State

CodeGraph verified on 2026-07-04.

- Backend package `com.library` currently contains auth, book, category, borrow, return, renewal, and penalty modules.
- Backend P1-P4 scope is implemented/current.
- News, messages, upload/file, dashboard, and library-card features are planned or legacy reference scope, not current backend contracts.
- Frontend connection infrastructure is partially migrated: `/api` proxy and Bearer token header are current.
- Frontend business modules still mostly target legacy pinyin paths/fields.
- Docker Compose is a planned deployment target unless separately verified.

## System Overview

```text
Browser
  |
  | HTTP JSON via /api
  v
Vue 3 SPA (Vite dev server)
  |
  | Axios, Authorization: Bearer <token>
  v
Spring Boot 3.5 backend (:8080/api)
  |
  | Spring Data JPA
  v
H2 dev/test or MySQL production
```

## Backend Architecture

### Package Layout

```text
com.library
|-- common
|   `-- ApiResponse
|-- config
|   |-- CorsConfig
|   |-- LibraryProperties
|   |-- OpenApiConfig
|   `-- SecurityConfig
|-- controller
|   |-- AuthController
|   |-- BookController
|   |-- CategoryController
|   |-- BorrowController
|   |-- ReturnController
|   |-- RenewalController
|   `-- PenaltyController
|-- dto
|-- entity
|-- repository
|-- security
|-- service
|   `-- impl
`-- exception
```

### Request Flow

```text
HTTP request
  -> CORS handling
  -> JwtAuthFilter
       - skips public endpoints
       - validates Bearer token
       - loads User and role
  -> DispatcherServlet
  -> Controller
  -> Service
  -> Repository
  -> Database
  -> ApiResponse<T>
```

### Security Model

| Endpoint area | Access |
| --- | --- |
| `/auth/login`, `/auth/register` | Public |
| `/books/page`, `/books/detail/{id}` | Public |
| `/categories/list` | Public |
| Book/category management | Admin |
| Borrow/return/renewal add | Reader/Admin |
| Borrow/return/renewal page | Authenticated; reader scoped to self |
| Borrow/return/renewal approval/delete | Admin |
| Penalty page | Authenticated; reader scoped to self |
| Penalty pay | Reader |
| Penalty delete | Admin |

## Frontend Architecture

### Source Layout

```text
library-frontend/src/
|-- api/          API wrappers; many still use legacy paths
|-- layouts/      MainLayout shell
|-- router/       Vue Router config
|-- utils/        Axios wrapper and helpers
`-- views/        Admin/reader pages
```

### Current Request Flow

```text
View component
  -> src/api/*.js
  -> src/utils/request.js
       baseURL = VITE_API_BASE_URL || /api
       Authorization: Bearer <token>
  -> Vite proxy /api -> http://localhost:8080
  -> Spring Boot /api
```

Known frontend integration gaps:

- `LoginView.vue` still reads auth response as the old shape.
- Business API modules still use paths such as `/tushuxinxi`, `/tushujieyue`, and `/chaoqifakuan`.
- Many views still use pinyin fields and legacy approval/payment values.
- `MyFines.vue` does not use `POST /penalties/pay`.
- Dependencies are not installed in the workspace, so frontend build has not been verified.

## Role Model

| Capability | Admin | Reader | Current backend |
| --- | :---: | :---: | --- |
| Login | yes | yes | Implemented through `/api/auth/login` |
| Reader registration | no | yes | Implemented through `/api/auth/register` |
| Book/category management | yes | no | Implemented |
| Browse books/categories | yes | yes | Implemented |
| Borrow application | optional/admin-created | yes | Implemented |
| Return application | optional/admin-created | yes | Implemented |
| Renewal application | optional/admin-created | yes | Implemented |
| Approve borrow/return/renewal | yes | no | Implemented |
| View own penalties | no | yes | Implemented |
| Pay own penalties | no | yes | Implemented |
| Delete penalties | yes | no | Implemented |
| News/messages/upload/dashboard | planned | planned | Not current backend contract |

## Deployment

Current verified local startup is direct backend/frontend startup:

```powershell
cd library-server
.\mvnw.cmd spring-boot:run
```

```powershell
cd library-frontend
npm install
npm run dev
```

Docker Compose should be documented as planned until a fresh compose verification is recorded.
