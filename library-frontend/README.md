# library-frontend — Vue 3 SPA

Library Management System frontend. Currently using **old pinyin API paths/fields** — to be migrated to new REST API after backend rebuild.

## Tech Stack

| Concern | Choice |
|---------|--------|
| Framework | Vue 3.5 (Composition API) |
| Build | Vite 7 |
| UI | Element Plus |
| HTTP | Axios (wrapped in `utils/request.js`) |
| Router | Vue Router 4 (with route guards) |
| Lint | ESLint + Prettier |

## Current State

| Layer | Files | Status |
|-------|:-----:|--------|
| `src/api/` | 12 modules | Old pinyin paths (`/duzhe`, `/tushuxinxi`, ...) |
| `src/views/` | 16 pages | Working with old API contract |
| `src/router/` | 1 config | Role-based guards (`admin` / `reader`) |
| `src/layouts/` | 1 layout | MainLayout shell |
| `src/utils/` | 2 files | `request.js` (Axios + JWT interceptor), `index.js` (helpers) |

## Migration Checklist (Phase 5)

After backend P3-P4 complete, update frontend to match new API:

- [ ] **API modules** — update all 12 files in `src/api/` to new paths per [API-SPEC.md](../docs/API-SPEC.md) §15
- [ ] **Field names** — replace pinyin fields with English (`xueshengxuehao` -> `studentId`, etc.) per [DATA-MODEL.md](../docs/DATA-MODEL.md) §4
- [ ] **Auth flow** — update `LoginView.vue` to use single `/api/auth/login` endpoint (P2 design: unified User table)
- [ ] **Borrow display** — add JOIN queries for readerName/bookTitle (removed from Borrow entity per 3NF)
- [ ] **dueDate** — add display of due date in borrow views (new field)

## Quick Start

```bash
cd library-frontend
npm install
npm run dev         # -> http://localhost:5173
npm run build       # -> dist/
```

Dev proxy (`vite.config.js`) forwards `/api` to `http://localhost:8080`.

## Reference

| Doc | Content |
|-----|---------|
| [API-SPEC.md](../docs/API-SPEC.md) | New REST API contract, old->new path mapping |
| [DATA-MODEL.md](../docs/DATA-MODEL.md) | Entity definitions, field dictionary |
| [ARCHITECTURE.md](../docs/ARCHITECTURE.md) | System design, route table, role model |
| [TEST-CASES.md](../docs/test/TEST-CASES.md) | Acceptance test cases (35 cases, 11 modules) |
