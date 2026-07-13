> **Archive Status:** Historical Reference
> **Original Path:** `docs/API-SPEC.md`
> **Archived On:** `2026-07-13`
> **Historical Purpose:** 返工前契约或实现记录
> **Superseded By:** `docs/README.md`
> **Authority:** 非规范性；不得作为当前需求、接口、数据或验收契约

# API-SPEC - Current REST API Contract

This file is the current API baseline for MyLibrarySystem.

## Current Actual State

CodeGraph verified on 2026-07-04.

- External base URL: `http://localhost:8080/api`.
- Spring controllers are mapped without `/api`; `/api` comes from `server.servlet.context-path`.
- Current backend API groups: `/auth/*`, `/books/*`, `/categories/*`, `/borrows/*`, `/returns/*`, `/renewals/*`, `/penalties/*`.
- P5 groups are not implemented in the current `com.library` backend: `/news/*`, `/messages/*`, `/upload/*`, `/dashboard/*`.
- Current password contract: raw request password + backend BCrypt.
- Current JWT contract: `Authorization: Bearer <token>`.
- Legacy pinyin paths and request fields are migration notes only, not current backend contracts.

## Conventions

### Response Envelope

```json
{
  "code": 0,
  "msg": "success",
  "data": {}
}
```

Rules:

- `code = 0` means success.
- `code = 1` means business or validation error.
- Paginated responses use `data.list` and `data.total`.

### Authentication

```http
Authorization: Bearer <jwt_token>
```

Public endpoints:

- `POST /api/auth/login`
- `POST /api/auth/register`
- `GET /api/books/page`
- `GET /api/books/detail/{id}`
- `GET /api/categories/list`
- Swagger, H2 console, and error endpoints

## Implementation Status

| Section | API group | Backend status | Frontend status |
| --- | --- | --- | --- |
| 1 | Auth | Implemented | Wrappers partially migrated; UI response handling stale |
| 2 | Books | Implemented | Legacy paths/fields remain |
| 3 | Categories | Implemented | Legacy paths/fields remain |
| 4 | Borrows | Implemented | Legacy paths/fields remain |
| 5 | Returns | Implemented | Legacy paths/fields remain |
| 6 | Renewals | Implemented | Legacy paths/fields remain |
| 7 | Penalties | Implemented | Legacy paths/fields remain; `MyFines.vue` not migrated |
| 8 | News | Planned | Legacy/P5 only |
| 9 | Messages | Planned | Not current backend contract |
| 10 | Upload | Planned | Legacy/P5 only |
| 11 | Dashboard | Planned | Legacy/P5 only |

## 1. Auth

### `POST /api/auth/login`

Public. Unified admin/reader login.

Request:

```json
{
  "username": "admin or studentId",
  "password": "plain request password"
}
```

Response data shape:

```json
{
  "token": "jwt",
  "role": "ADMIN",
  "userId": 1,
  "username": "admin",
  "readerId": null
}
```

Notes:

- Readers log in with `username = studentId`.
- Backend verifies with `BCryptPasswordEncoder.matches`.
- Old `/api/users/login` and `/api/readers/login` docs are legacy notes only.

### `POST /api/auth/register`

Public. Reader self-registration.

Request:

```json
{
  "studentId": "2021001",
  "name": "Reader Name",
  "password": "plain request password",
  "gender": "MALE",
  "department": "Computer Science",
  "birthDate": "2001-01-01",
  "phone": "13800000000"
}
```

Response data shape is the same as login.

## 2. Books

### `GET /api/books/page`

Public listing and admin management page query.

Query params:

| Param | Type | Required | Notes |
| --- | --- | --- | --- |
| `page` | int | no | default `1` |
| `limit` | int | no | default `10` |
| `title` | string | no | fuzzy title filter |
| `author` | string | no | fuzzy author filter |
| `publisher` | string | no | fuzzy publisher filter |
| `isbn` | string | no | fuzzy ISBN/code filter |
| `categoryId` | long | no | category filter |
| `year` | string | no | publication year |

Response data:

```json
{
  "list": [
    {
      "id": 1,
      "isbn": "978-7-302-11111",
      "title": "Book Title",
      "coverImage": "cover.jpg",
      "categoryId": 1,
      "categoryName": "Computer",
      "author": "Author",
      "publisher": "Publisher",
      "year": "2024",
      "stock": 5,
      "description": "..."
    }
  ],
  "total": 1
}
```

### Other book endpoints

| Method | Path | Auth | Request |
| --- | --- | --- | --- |
| `GET` | `/api/books/info/{id}` | Admin | n/a |
| `GET` | `/api/books/detail/{id}` | Public | n/a |
| `POST` | `/api/books/save` | Admin | `BookRequest` |
| `POST` | `/api/books/update?id={id}` | Admin | `BookRequest` |
| `POST` | `/api/books/delete` | Admin | JSON array of IDs |

`BookRequest`:

```json
{
  "isbn": "978-7-302-11111",
  "title": "Book Title",
  "coverImage": "cover.jpg",
  "categoryId": 1,
  "author": "Author",
  "publisher": "Publisher",
  "year": "2024",
  "stock": 5,
  "description": "..."
}
```

## 3. Categories

| Method | Path | Auth | Notes |
| --- | --- | --- | --- |
| `GET` | `/api/categories/page` | Admin | paginated management query |
| `GET` | `/api/categories/list` | Public | list for selectors |
| `GET` | `/api/categories/info/{id}` | Admin | detail |
| `POST` | `/api/categories/save` | Admin | create |
| `POST` | `/api/categories/update?id={id}` | Admin | update |
| `POST` | `/api/categories/delete` | Admin | delete ID array |

`CategoryRequest`:

```json
{
  "name": "Computer"
}
```

## 4. Borrows

| Method | Path | Auth | Notes |
| --- | --- | --- | --- |
| `GET` | `/api/borrows/page` | Authenticated | reader sees own records; admin can filter |
| `POST` | `/api/borrows/add` | Reader/Admin | submit borrow application |
| `POST` | `/api/borrows/shBatch` | Admin | batch approval |
| `POST` | `/api/borrows/delete` | Admin | delete ID array |

`BorrowRequest`:

```json
{
  "bookId": 1,
  "readerId": 1,
  "durationDays": 30
}
```

`readerId` is optional for reader role because it is derived from the JWT. Admin-created borrow requests must provide it.

Query params for `/page`:

| Param | Type | Required |
| --- | --- | --- |
| `page` | int | no |
| `limit` | int | no |
| `readerId` | long | no |
| `bookId` | long | no |
| `approved` | string | no |

## 5. Returns

| Method | Path | Auth | Notes |
| --- | --- | --- | --- |
| `GET` | `/api/returns/page` | Authenticated | reader sees own records |
| `POST` | `/api/returns/add` | Reader/Admin | submit return application |
| `POST` | `/api/returns/shBatch` | Admin | batch approval |
| `POST` | `/api/returns/delete` | Admin | delete ID array |

`ReturnRequest`:

```json
{
  "borrowId": 1
}
```

## 6. Renewals

| Method | Path | Auth | Notes |
| --- | --- | --- | --- |
| `GET` | `/api/renewals/page` | Authenticated | reader sees own records |
| `POST` | `/api/renewals/add` | Reader/Admin | submit renewal application |
| `POST` | `/api/renewals/shBatch` | Admin | batch approval |
| `POST` | `/api/renewals/delete` | Admin | delete ID array |

`RenewalRequest`:

```json
{
  "borrowId": 1,
  "durationDays": 30
}
```

## 7. Penalties

| Method | Path | Auth | Notes |
| --- | --- | --- | --- |
| `GET` | `/api/penalties/page` | Authenticated | reader sees own records |
| `POST` | `/api/penalties/pay` | Reader | pay own unpaid penalty |
| `POST` | `/api/penalties/delete` | Admin | delete ID array |

`PayRequest`:

```json
{
  "id": 1
}
```

Query params for `/page`:

| Param | Type | Required |
| --- | --- | --- |
| `page` | int | no |
| `limit` | int | no |
| `paid` | string | no |

## Batch Approval

Borrow, return, and renewal approvals share this request body:

```json
{
  "ids": [1, 2],
  "approved": "APPROVED",
  "reply": "Approved"
}
```

Use the enum values actually accepted by the backend enum serialization. Do not use old query params such as `sfsh` or `shhf` as the current request contract.

## Planned / Legacy Groups

The following groups are not implemented in the current `com.library` backend:

| Planned group | Legacy/current frontend references | Current status |
| --- | --- | --- |
| News | `/news/*` | P5 planned |
| Messages | `/messages/*` | P5 planned |
| Upload | `/file/upload` | P5 planned |
| Dashboard | `/common/*`, `/option/*` | P5 planned |

## Legacy Path Mapping

Legacy paths are retained only to guide frontend migration.

| Legacy path | Current or planned replacement |
| --- | --- |
| `/users/login` | `/api/auth/login` |
| `/duzhe/login` | `/api/auth/login` |
| `/duzhe/register` | `/api/auth/register` |
| `/tushuxinxi/*` | `/api/books/*` |
| `/tushuleibie/*` | `/api/categories/*` |
| `/tushujieyue/*` | `/api/borrows/*` |
| `/tushuguihai/*` | `/api/returns/*` |
| `/tushuxujie/*` | `/api/renewals/*` |
| `/chaoqifakuan/*` | `/api/penalties/*` |
| `/news/*` | planned `/api/news/*` |
| `/messages/*` | planned `/api/messages/*` |
| `/file/*` | planned `/api/upload/*` |
| `/common/*`, `/option/*` | planned `/api/dashboard/*` |
