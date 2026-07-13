> **Archive Status:** Historical Reference
> **Original Path:** `docs/test/TEST-CASES.md`
> **Archived On:** `2026-07-13`
> **Historical Purpose:** 返工前契约或实现记录
> **Superseded By:** `docs/README.md`
> **Authority:** 非规范性；不得作为当前需求、接口、数据或验收契约

# TEST-CASES - Acceptance Planning Matrix

This file is the current acceptance-test planning baseline. It is not the automated test execution report.

## Current Actual State

CodeGraph verified on 2026-07-04.

- Backend automated test source currently contains 22 `@Test` methods.
- Current automated focus: borrow, return, renewal, penalty, and library properties.
- Last recorded Surefire result: `Tests run: 22, Failures: 0, Errors: 0, Skipped: 0`.
- A fresh backend test run requires `JAVA_HOME`; the current shell cannot run Maven tests without it.
- The acceptance cases below are a planning matrix. They include current P1-P4 backend behavior plus planned P5 cases marked as planned.
- Historical P4 spec content is only a source for missing cases. Current test authority is this file after consolidation.

## Status Legend

| Status | Meaning |
| --- | --- |
| Current | Backend endpoint/behavior is implemented and should be accepted/tested now |
| Frontend gap | Backend exists, but frontend still uses legacy integration |
| Planned | P5 or future behavior; not current backend contract |
| Boundary | Boundary-focused case, mirrored in `TEST-CASES-BOUNDARY.md` |

## Automated Test Inventory

| Area | `@Test` count | Notes |
| --- | ---: | --- |
| Borrow service | 5 | Borrow eligibility, duplicate/active borrow behavior |
| Return service | 5 | Return application/approval and duplicate return protection |
| Renewal service | 7 | Renewal application, approval, limits, filtering |
| Penalty service | 4 | Penalty query/payment behavior |
| Library properties | 1 | Configuration binding/defaults |
| Total | 22 | Source count only |

## 1. Auth

| ID | Status | Scenario | Expected result | Current backend entry |
| --- | --- | --- | --- | --- |
| AUTH-01 | Current / frontend gap | Reader logs in with student ID and password | `ApiResponse.data.token`, role, user ID, and reader ID returned | `AuthController.login` |
| AUTH-02 | Current / frontend gap | Admin logs in with username and password | `ApiResponse.data.token`, role, user ID returned | `AuthController.login` |
| AUTH-03 | Current | Login with wrong password or unknown account | `code=1`, no JWT issued | `AuthController.login` |
| AUTH-04 | Current / frontend gap | Reader registers with unique student ID | Reader + User created, login response returned | `AuthController.register` |
| AUTH-05 | Current | Duplicate student ID registration | Operation rejected, no duplicate user/profile | `AuthController.register` |

Notes:

- Login/register acceptance should target current unified auth, not the retired split-controller login model.
- Current password contract is raw request password + backend BCrypt.

## 2. Books

| ID | Status | Scenario | Expected result | Current backend entry |
| --- | --- | --- | --- | --- |
| BOOK-01 | Current / frontend gap | Admin creates a valid book | Book persisted, `code=0` | `BookController.save` |
| BOOK-02 | Current / frontend gap | Query books by page and filters | `data.list` and `data.total` returned | `BookController.page` |
| BOOK-03 | Current / frontend gap | Admin updates stock or metadata | Book fields updated | `BookController.update` |
| BOOK-04 | Current | Admin deletes unreferenced book | Book deleted | `BookController.delete` |
| BOOK-05 | Current | Invalid book data | Operation rejected with `code=1` | `BookController.save/update` |

## 3. Categories

| ID | Status | Scenario | Expected result | Current backend entry |
| --- | --- | --- | --- | --- |
| CAT-01 | Current / frontend gap | Admin creates or updates category | Category persisted | `CategoryController.save/update` |
| CAT-02 | Current | Public category list for selectors | List of `{ id, name }` returned | `CategoryController.list` |
| CAT-03 | Current | Delete category referenced by books | Operation rejected | `CategoryController.delete` |

## 4. Borrows

| ID | Status | Scenario | Expected result | Current backend entry |
| --- | --- | --- | --- | --- |
| BRW-01 | Current / frontend gap | Eligible reader submits borrow request | Borrow record created/approved according to service rules | `BorrowController.add` |
| BRW-02 | Current | Borrow rejected for no stock, unpaid fines, or active limit | Operation rejected, no invalid borrow | `BorrowService.apply` |
| BRW-03 | Current | Admin approves pending borrow | Borrow approved and stock adjusted | `BorrowController.shBatch` |
| BRW-04 | Current | Admin rejects pending borrow | Borrow rejected and stock unchanged | `BorrowController.shBatch` |
| BRW-05 | Current / Boundary | Active borrow limit excludes approved returned borrows | Returned borrows do not block later borrowing | `BorrowService.apply` |
| BRW-06 | Current / Boundary | Borrow same book again after approved return | New borrow is allowed after completed return | `BorrowService.apply` |

## 5. Returns

| ID | Status | Scenario | Expected result | Current backend entry |
| --- | --- | --- | --- | --- |
| RET-01 | Current / frontend gap | Reader returns before due date | Return approved/recorded, stock restored, no fine | `ReturnController.add/shBatch` |
| RET-02 | Current | Overdue return | Penalty generated based on overdue days | `ReturnService.approveBatch` |
| RET-03 | Current | Return unknown borrow ID | Operation rejected | `ReturnService.apply` |
| RET-04 | Current / Boundary | Duplicate pending return request | Duplicate pending return is rejected | `ReturnService.apply` |
| RET-05 | Current / Boundary | Duplicate return after approved return | Duplicate approved return is rejected | `ReturnService.apply` |

## 6. Renewals

| ID | Status | Scenario | Expected result | Current backend entry |
| --- | --- | --- | --- | --- |
| RNW-01 | Current / frontend gap | Reader submits renewal for active borrow | Renewal record created/approved according to rules | `RenewalController.add` |
| RNW-02 | Current | Admin approves or rejects renewal | Renewal state updated; due date extended when approved | `RenewalController.shBatch` |
| RNW-03 | Current / Boundary | Duplicate renewal beyond allowed rules | Operation rejected | `RenewalService.apply` |
| RNW-04 | Current / Boundary | Renewal duration exceeds cap | Duration capped or rejected according to config | `RenewalService.apply` |
| RNW-05 | Current | Admin filters renewals by `approved` | Returned page respects filter | `RenewalController.page` |

## 7. Penalties

| ID | Status | Scenario | Expected result | Current backend entry |
| --- | --- | --- | --- | --- |
| PEN-01 | Current / frontend gap | Reader queries own penalties | Only own penalties returned | `PenaltyController.page` |
| PEN-02 | Current / frontend gap | Reader pays unpaid penalty | Penalty marked paid | `PenaltyController.pay` |
| PEN-03 | Current / Boundary | Reader pays already-paid penalty | Operation rejected | `PenaltyService.pay` |
| PEN-04 | Current | Admin deletes penalty records | Records deleted | `PenaltyController.delete` |

## 8. P5 Planned Cases

These are not current backend contract cases. They should not be treated as failures until P5 is implemented.

| ID | Status | Scenario | Planned backend group |
| --- | --- | --- | --- |
| NEWS-01 | Planned | Admin creates or edits announcement | `/api/news/*` |
| NEWS-02 | Planned | Public/admin announcement list | `/api/news/*` |
| NEWS-03 | Planned | Admin deletes announcement | `/api/news/*` |
| MSG-01 | Planned | Reader submits message | `/api/messages/*` |
| MSG-02 | Planned | Admin replies to message | `/api/messages/*` |
| MSG-03 | Planned | Admin deletes message | `/api/messages/*` |
| UPL-01 | Planned | Upload cover image | `/api/upload/*` |
| DASH-01 | Planned | Dashboard statistics | `/api/dashboard/*` |

## Frontend Acceptance Gaps

The backend supports P1-P4, but frontend acceptance should remain partial until these are fixed:

- `LoginView.vue` must consume `ApiResponse<LoginResponse>` through `res.data`.
- Business API modules must migrate from pinyin paths to current endpoint groups.
- Pinyin fields must be mapped to current English DTO fields.
- `MyFines.vue` must use `/penalties/page` and `/penalties/pay`.
- Dependencies must be installed before build verification.
