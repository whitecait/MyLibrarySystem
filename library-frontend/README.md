# library-frontend — Vue 3 SPA

Library Management System frontend. Currently using **old pinyin API paths/fields** — to be migrated to new REST API. **Backend P3 (auth + book + category APIs) is live** — migration can begin incrementally.

## Tech Stack

| Concern   | Choice                                |
| --------- | ------------------------------------- |
| Framework | Vue 3.5 (Composition API)             |
| Build     | Vite 7                                |
| UI        | Element Plus                          |
| HTTP      | Axios (wrapped in `utils/request.js`) |
| Router    | Vue Router 4 (token guard, no role check) |
| Lint      | ESLint + Prettier                     |

## Current State

| Layer          |   Files    | Status                                                       |
| -------------- | :--------: | ------------------------------------------------------------ |
| `src/api/`     | 11 modules | Old pinyin paths (`/tushuxinxi`, `/duzhe`, ...); `messages.js` missing |
| `src/views/`   |  15 pages  | Working with old API contract; admin + reader views complete |
| `src/router/`  |  1 config  | Token-based guard only (no role check); 14 routes            |
| `src/layouts/` |  1 layout  | MainLayout shell with sidebar + header                       |
| `src/utils/`   |  2 files   | `request.js` (Axios + custom `token` header), `index.js` (helpers) |

## Known Issues (Require Fix Before Migration)

### 1. JWT Header Mismatch

Frontend sends JWT as `token` custom header, but backend expects `Authorization: Bearer <token>`.

```js
// utils/request.js — CURRENT (wrong)
config.headers['token'] = token;

// Should be:
config.headers['Authorization'] = `Bearer ${token}`;
```

### 2. Vite Proxy Path Mismatch

`vite.config.js` rewrites `/api` to `/springboot8393c` (old context path), but the new backend uses `/api` as context path.

```js
// vite.config.js — CURRENT (wrong for new backend)
rewrite: (path) => path.replace(/^\/api/, '/springboot8393c')

// Should be:
// rewrite: (path) => path  // no rewrite needed, or remove rewrite entirely
```

### 3. Old Pinyin API Paths

All 11 API modules use old pinyin paths (e.g., `/tushuxinxi/page`, `/duzhe/login`). Backend now serves:
- New: `/api/auth/*`, `/api/books/*`, `/api/categories/*` (P3 done)
- Not yet: `/api/borrows/*`, `/api/returns/*`, etc. (P4-P5 planned)

## Migration Checklist

Backend P3 is **done** (auth + book + category APIs). Migration can proceed incrementally:

- [ ] **Fix JWT header** — `utils/request.js`: change `config.headers['token']` → `config.headers['Authorization'] = 'Bearer ' + token`
- [ ] **Fix vite proxy** — `vite.config.js`: remove or fix the `/springboot8393c` rewrite
- [ ] **Auth API** — `user.js` + `reader.js`: migrate login/register to `/auth/login`, `/auth/register` per [API-SPEC.md](../docs/API-SPEC.md) §2-3
- [ ] **Book API** — `book.js`: migrate `/tushuxinxi/*` → `/books/*` per [API-SPEC.md](../docs/API-SPEC.md) §4
- [ ] **Category API** — `category.js`: migrate `/tushuleibie/*` → `/categories/*` per [API-SPEC.md](../docs/API-SPEC.md) §5
- [ ] **Remaining APIs** — `borrow.js`, `return.js`, `renewal.js`, `penalty.js`, `news.js`, `file.js`, `home.js` — wait for backend P4-P5 (or add `messages.js`)
- [ ] **Field names** — replace pinyin fields with English (`xueshengxuehao` -> `studentId`, etc.) per [DATA-MODEL.md](../docs/DATA-MODEL.md) §4
- [ ] **Role guards** — add admin/reader role checks in router (currently only token check)
- [ ] **Borrow display** — add JOIN queries for readerName/bookTitle (removed from Borrow entity per 3NF)
- [ ] **dueDate** — add display of due date in borrow views (new field)

## Design Decisions

### Password Transmission: SHA256 + BCrypt (Planned, Not Yet Implemented)

To avoid transmitting plaintext passwords over the network, the frontend should apply SHA256 before sending:

```
User input "123456"
  -> frontend SHA256 -> "8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92"
  -> HTTPS transport
  -> backend BCrypt.encode() -> "$2a$10$..."
  -> DB stores BCrypt(SHA256(password))
```

- **Current state**: Frontend sends raw passwords; backend BCrypt handles them correctly. SHA256 pre-hashing is a future enhancement.
- **Frontend responsibility**: All API modules (`src/api/user.js`, `src/api/reader.js`) MUST apply SHA256 to the password field before sending login/register requests. The backend always expects a 64-character hex string in the `password` field.
- **Backend**: Unaware of frontend hashing — BCrypt treats the SHA256 hex string as the "password" and works identically. No backend changes needed.
- **Consistency**: Both login and register apply the same SHA256 transform, so `BCrypt.matches()` produces correct results.
- **Caveat**: Password strength validation (min length, complexity) can only be enforced in the frontend — the backend always receives a fixed-length hex string.

> Implementation: add `import { sha256 } from 'js-sha256'` (or use Web Crypto API) to `LoginView.vue` and any register/password-reset forms.

## Quick Start

```bash
cd library-frontend
npm install
npm run dev         # -> http://localhost:5173
npm run build       # -> dist/
```

Dev proxy (`vite.config.js`) forwards `/api` to `http://localhost:8080`.

## Reference

| Doc                                         | Content                                      |
| ------------------------------------------- | -------------------------------------------- |
| [API-SPEC.md](../docs/API-SPEC.md)          | New REST API contract, old->new path mapping |
| [DATA-MODEL.md](../docs/DATA-MODEL.md)      | Entity definitions, field dictionary         |
| [ARCHITECTURE.md](../docs/ARCHITECTURE.md)  | System design, route table, role model       |
| [TEST-CASES.md](../docs/test/TEST-CASES.md) | Acceptance test cases (35 cases, 11 modules) |
