# API-SPEC — REST API Specification

> Bilingual: EN technical terms + CN explanations.
> **Current state (2026-06-23):** Backend P3 complete — Auth, Book, Category endpoints are LIVE.
> Borrow/Return/Renewal/Penalty/News/Message/Upload/Dashboard endpoints are PLANNED (P4-P5).
> Frontend API modules still use old pinyin paths — migration pending.
> Path mapping is documented in §15 Appendix.

## 1. Conventions

### Base URL

```
Development:  http://localhost:8080/api
Production:   configured via .env.production (Vite proxy)
```

### Authentication

```
Header:  Authorization: Bearer <jwt_token>
```

- Public endpoints (login, register) are marked `🔓 Public`.
- All other endpoints require a valid JWT.
- Admin-only endpoints are marked `🔒 Admin`.

### Unified Response

```json
// Success
{ "code": 0, "msg": "success", "data": { ... } }

// Error
{ "code": 1, "msg": "error description" }

// Paginated
{ "code": 0, "msg": "success", "data": { "list": [...], "total": 100 } }
```

### Pagination

| Param   | Type | Default | Description             |
| ------- | ---- | :-----: | ----------------------- |
| `page`  | int  |    1    | Page number (1-indexed) |
| `limit` | int  |   10    | Page size               |

Response includes `list` (array) and `total` (total record count).

### Implementation Status

| Section | API Group     |  Backend  |  Frontend   |
| :-----: | ------------- | :-------: | :---------: |
|    2    | Auth (admin)  |  √ Live   |  Old paths  |
|    3    | Auth (reader) | √ Live\*  |  Old paths  |
|    4    | Books         |  √ Live   |  Old paths  |
|    5    | Categories    |  √ Live   |  Old paths  |
|    6    | Borrows       | ○ Planned |  Old paths  |
|    7    | Returns       | ○ Planned |  Old paths  |
|    8    | Renewals      | ○ Planned |  Old paths  |
|    9    | Penalties     | ○ Planned |  Old paths  |
|   10    | News          | ○ Planned |  Old paths  |
|   11    | Messages      | ○ Planned | Not created |
|   12    | Upload        | ○ Planned |  Old paths  |
|   13    | Dashboard     | ○ Planned |  Old paths  |

> \* Auth uses unified `/auth/*` endpoints (not `/users/*` / `/readers/*` as documented below). See P2 design spec.

---

## 2. Auth — Admin (`/api/users`)

> Old path: `/users`

### `POST /api/users/login` 🔓 Public

Admin login with username + password.

```
Request:  { username, password }  (query params or JSON body)
Response: { code:0, data: { token: "eyJ...", user: {...} } }
Errors:   "账号或密码不正确"
```

### `GET /api/users/session` 🔒 Admin

Get current admin session info from JWT.

```
Response: { code:0, data: { id, username, role } }
```

### `GET /api/users/page` 🔒 Admin

Paginated admin user list.

```
Params:   page, limit, username (optional search)
Response: { code:0, data: { list:[...], total:N } }
```

### `POST /api/users/save` 🔒 Admin

Create new admin account.

### `POST /api/users/update` 🔒 Admin

Update admin account.

### `POST /api/users/delete` 🔒 Admin

Batch delete (body: `[id1, id2, ...]`).

### `POST /api/users/resetPass` 🔓 Public

Reset admin password to "123456" by username.

---

## 3. Auth — Reader (`/api/readers`)

> Old path: `/duzhe`

### `POST /api/readers/login` 🔓 Public

Reader login with student ID (学号) + password.

```
Request:  { xueshengxuehao, mima } (query params or JSON body)
Response: { code:0, data: { token:"eyJ...", user:{...} } }
Errors:   "账号或密码不正确"
```

### `POST /api/readers/register` 🔓 Public

Self-registration.

```
Request:  { xueshengxuehao, mima, xingming, xingbie, yuanxi, shouji }
Errors:   "注册用户已存在"
```

### `GET /api/readers/page` 🔒 Admin

Paginated reader list with optional search filters.

```
Params:   page, limit, xueshengxuehao?, xingming?
Response: { code:0, data: { list:[...], total:N } }
```

### `GET /api/readers/session` 🔒 Reader

Get current reader session from JWT.

### `GET /api/readers/info/{id}` 🔒 Admin

Get reader detail by ID.

### `POST /api/readers/save` 🔒 Admin

Create reader (admin-side).

### `POST /api/readers/update` 🔒 Reader/Admin

Update reader info. If `mima` is blank, keeps old password.

### `POST /api/readers/delete` 🔒 Admin

Batch delete.

---

## 4. Book Management (`/api/books`)

> Old path: `/tushuxinxi`

### `GET /api/books/page` 🔓 Public (list) / 🔒 Admin (page)

Paginated book list with multi-condition search.

```
Params:   page, limit, tushumingcheng?, tushuleixing?, zuozhe? (search fields)
Response: { code:0, data: { list:[Book], total:N } }
```

### `GET /api/books/list` 🔓 Public

Frontend public list (same as page but with `@IgnoreAuth` in old system).

### `GET /api/books/info/{id}` 🔒 Admin

Book detail by ID.

### `GET /api/books/detail/{id}` 🔓 Public

Public book detail.

### `POST /api/books/save` 🔒 Admin

Create book.

```
Request:  { tushubianhao, tushumingcheng, tushuleixing, tushufengmian,
            zuozhe, chubanshe, nianfen, shuliang, tushuxiangqing }
```

### `POST /api/books/update` 🔒 Admin

Update book (full field update by ID).

### `POST /api/books/delete` 🔒 Admin

Batch delete.

### `GET /api/books/lists` 🔒 Admin

Exact-match query returning all matching records (no pagination).

### `GET /api/books/query` 🔒 Admin

Single book view query by exact fields.

---

## 5. Category Management (`/api/categories`)

> Old path: `/tushuleibie`

### `GET /api/categories/page` 🔒 Admin

### `GET /api/categories/list` 🔓 Public

### `GET /api/categories/info/{id}` 🔒 Admin

### `POST /api/categories/save` 🔒 Admin

```
Request:  { tushuleibie: "文学" }
```

### `POST /api/categories/update` 🔒 Admin

### `POST /api/categories/delete` 🔒 Admin

---

## 6. Borrow Workflow (`/api/borrows`)

> Old path: `/tushujieyue`

### Borrow Lifecycle

```
Reader applies ──→ Admin reviews ──→ Approved (reader has book)
     │                   │
     └── rejected        └── Reader applies return ──→ Admin confirms ──→ Book stock+1
                                                        │
                                              Reader applies renewal ──→ Admin approves
```

### `GET /api/borrows/page`

Paginated list. Admin sees all; reader sees own records only.

```
Params:   page, limit, xueshengxuehao? (auto-set for reader role)
```

### `POST /api/borrows/add` 🔒 Reader

Submit borrow application.

```
Request:  { jieyueren, xueshengxuehao, tushubianhao, tushumingcheng }
```

### `POST /api/borrows/shBatch` 🔒 Admin

Batch approve/reject.

```
Request params:  sfsh=是|否, shhf=approval comment
Request body:    [id1, id2, ...]
```

### `POST /api/borrows/update` 🔒 Admin

### `POST /api/borrows/delete` 🔒 Admin

---

## 7. Return Workflow (`/api/returns`)

> Old path: `/tushuguihai`

### `GET /api/returns/page`

### `POST /api/returns/add` 🔒 Reader

Submit return application.

```
Request:  { tushubianhao, tushumingcheng, xueshengxuehao, xingming }
```

### `POST /api/returns/shBatch` 🔒 Admin

Batch approve/reject returns. On approval, book stock increments.

---

## 8. Renewal Workflow (`/api/renewals`)

> Old path: `/tushuxujie`

### `GET /api/renewals/page`

### `POST /api/renewals/add` 🔒 Reader

```
Request:  { tushubianhao, tushumingcheng, xueshengxuehao, xingming }
```

### `POST /api/renewals/shBatch` 🔒 Admin

---

## 9. Penalty / Fine (`/api/penalties`)

> Old path: `/chaoqifakuan`

### `GET /api/penalties/page`

Admin sees all; reader sees own records.

```
Params:   page, limit, xueshengxuehao?
Response: { code:0, data: { list:[Penalty], total:N } }
Fields:   id, fakuanbianhao, tushubianhao, tushumingcheng,
          fakuanjine, fakuanshijian, xueshengxuehao, xingming, sfzf (是否支付)
```

### `POST /api/penalties/save` 🔒 Admin

### `POST /api/penalties/update` 🔒 Admin (e.g., mark as paid)

### `POST /api/penalties/delete` 🔒 Admin

---

## 10. News / Announcements (`/api/news`)

> Old path: `/news`

### `GET /api/news/page` 🔓 Public (list) / 🔒 Admin (page)

### `GET /api/news/info/{id}`

### `GET /api/news/detail/{id}` 🔓 Public

### `POST /api/news/save` 🔒 Admin

### `POST /api/news/update` 🔒 Admin

### `POST /api/news/delete` 🔒 Admin

```
News fields: id, title, introduction, picture, content, addtime
```

---

## 11. Messages (`/api/messages`)

> Old path: `/messages`

### `GET /api/messages/page`

Admin sees all; reader sees own messages only.

```
Message fields: id, userid, username, avatarurl, content, cpicture, reply, rpicture, addtime
```

### `POST /api/messages/add` 🔒 Reader

Submit message / feedback.

### `POST /api/messages/update` 🔒 Admin

Reply to a message (set `reply` field).

### `POST /api/messages/delete` 🔒 Admin

---

## 12. File Upload (`/api/upload`)

> Old path: `/file`

### `POST /api/upload` 🔒 Authenticated

```
Content-Type: multipart/form-data
Form field:   file
Response:     { code:0, data: { file: "filename.jpg" } }
```

Uploaded files stored in `uploads/` directory (dev) or configured volume (prod).

---

## 13. Dashboard & Options

> Old paths: `/common`, `/option`

### `GET /api/dashboard/stats` 🔒 Admin

```
Response: { code:0, data: {
  bookTotal: N, readerTotal: N, borrowTotal: N, ...
}}
```

### `GET /api/options/categories` 🔒 Authenticated

```
Response: { code:0, data: [...category names] }
```

Used for category dropdown / select components in the frontend.

---

## 14. Error Codes

| Code | Meaning                                             |
| :--: | --------------------------------------------------- |
|  0   | Success                                             |
|  1   | Business error (validation, duplicate, not found)   |
| 401  | Unauthorized — missing or invalid JWT (HTTP status) |
| 403  | Forbidden — valid JWT but wrong role (HTTP status)  |
| 500  | Internal server error (HTTP status)                 |

---

## 15. Appendix: Old → New Path Mapping

| Old Path (pinyin) | New Path (RESTful)  | Module             |
| ----------------- | ------------------- | ------------------ |
| `/users/*`        | `/api/users/*`      | Admin auth & CRUD  |
| `/duzhe/*`        | `/api/readers/*`    | Reader auth & CRUD |
| `/tushuxinxi/*`   | `/api/books/*`      | Book CRUD          |
| `/tushuleibie/*`  | `/api/categories/*` | Category CRUD      |
| `/tushujieyue/*`  | `/api/borrows/*`    | Borrow workflow    |
| `/tushuguihai/*`  | `/api/returns/*`    | Return workflow    |
| `/tushuxujie/*`   | `/api/renewals/*`   | Renewal workflow   |
| `/chaoqifakuan/*` | `/api/penalties/*`  | Penalty management |
| `/news/*`         | `/api/news/*`       | Announcements      |
| `/messages/*`     | `/api/messages/*`   | Reader messages    |
| `/file/upload`    | `/api/upload`       | File upload        |
| `/common/*`       | `/api/dashboard/*`  | Dashboard stats    |
| `/option/*`       | `/api/options/*`    | Dropdown options   |

> **Note for Phase 5 (frontend integration):** All 11 frontend API modules currently use old paths.
> Auth/Book/Category (3 modules) can be migrated now — backend P3 is live.
> Borrow/Return/Renewal/Penalty/News/File/Dashboard (7 modules) wait for backend P4-P5.
> Messages API module not yet created.
