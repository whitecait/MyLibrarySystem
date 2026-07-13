# library-frontend - Vue 3 SPA

Vue 3 frontend for MyLibrarySystem.

## Current Actual State

CodeGraph verified on 2026-07-04.

- `vite.config.js` proxies `/api` to `http://localhost:8080`; the old backend context-path rewrite is no longer used.
- `src/utils/request.js` defaults to `VITE_API_BASE_URL || '/api'`.
- Axios sends JWT as `Authorization: Bearer <token>`.
- `src/api/user.js` and `src/api/reader.js` call `/auth/login`; `src/api/reader.js` calls `/auth/register`.
- Auth wrappers are partially migrated, but the UI flow is not fully migrated: `LoginView.vue` still reads old response fields such as `res.token` and `res.user`.
- Most business API modules still use old pinyin paths and/or old fields: book, category, borrow, return, renewal, penalty, home, and file.
- `news.js` calls `/news/*`, but the current backend has no P5 news controller.
- `MyFines.vue` still fetches/pays fines through the old borrow/penalty update flow and old fields instead of `GET /penalties/page` and `POST /penalties/pay`.
- Frontend build has not been verified in this workspace because `node_modules` is absent and dependencies are not installed.

## Tech Stack

| Concern | Choice |
| --- | --- |
| Framework | Vue 3 |
| Build | Vite |
| UI | Element Plus |
| HTTP | Axios via `src/utils/request.js` |
| Router | Vue Router |

## Current Migration Matrix

| Area | State |
| --- | --- |
| Vite proxy | Migrated to `/api -> http://localhost:8080` |
| JWT header | Migrated to `Authorization: Bearer <token>` |
| Auth API wrappers | Partially migrated to `/auth/*` |
| Login/register UI | Not fully migrated; still consumes old response shape |
| Book/category API modules | Still contain legacy paths/fields |
| Borrow/return/renewal API modules | Still contain legacy paths/fields |
| Penalty API and `MyFines.vue` | Not migrated to current `/penalties/pay` flow |
| News/messages/upload/dashboard | Planned or legacy; backend P5 is not current |
| Field names | Broad pinyin-to-English migration still pending |
| Frontend build | Not verified; dependencies are missing |

## Current Backend Contract to Target

| Frontend area | Backend endpoint group |
| --- | --- |
| Auth | `/api/auth/*` |
| Books | `/api/books/*` |
| Categories | `/api/categories/*` |
| Borrows | `/api/borrows/*` |
| Returns | `/api/returns/*` |
| Renewals | `/api/renewals/*` |
| Penalties | `/api/penalties/*` |

P5 groups (`/api/news/*`, `/api/messages/*`, `/api/upload/*`, `/api/dashboard/*`) are not current backend contracts.

## Migration Checklist

- [x] Proxy `/api` to `http://localhost:8080`.
- [x] Send JWT as `Authorization: Bearer <token>`.
- [x] Point auth wrappers at `/auth/login` and `/auth/register`.
- [ ] Update `LoginView.vue` to consume `ApiResponse<LoginResponse>` via `res.data`.
- [ ] Migrate book/category modules to `/books/*` and `/categories/*`.
- [ ] Migrate borrow/return/renewal modules to `/borrows/*`, `/returns/*`, `/renewals/*`.
- [ ] Migrate penalty pages to `/penalties/page` and `/penalties/pay`.
- [ ] Replace pinyin request/response fields with current English DTO fields.
- [ ] Add role-aware routing/menus if required by product scope.
- [ ] Install dependencies and verify `npm run build`.

## Password Handling

Current backend contract is raw request password + backend BCrypt. Frontend SHA256 pre-hashing is not required by the current backend and should be treated as a future enhancement only if both frontend and backend documentation are updated together.

## Quick Start

```powershell
cd library-frontend
npm install
npm run dev
```

Development URL: `http://localhost:5173`.

## References

| Doc | Purpose |
| --- | --- |
| `../docs/API-SPEC.md` | Current REST API contract and legacy migration notes |
| `../docs/ARCHITECTURE.md` | System and frontend integration status |
| `../docs/DATA-MODEL.md` | Entity and DTO field alignment |
| `../docs/test/TEST-CASES.md` | Acceptance planning matrix |
