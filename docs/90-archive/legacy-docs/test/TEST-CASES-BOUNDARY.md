> **Archive Status:** Historical Reference
> **Original Path:** `docs/test/TEST-CASES-BOUNDARY.md`
> **Archived On:** `2026-07-13`
> **Historical Purpose:** 返工前契约或实现记录
> **Superseded By:** `docs/README.md`
> **Authority:** 非规范性；不得作为当前需求、接口、数据或验收契约

# TEST-CASES-BOUNDARY - Boundary Planning Baseline

This file tracks boundary-focused acceptance cases. It complements `TEST-CASES.md`; it is not an automated test report.

## Current Actual State

CodeGraph verified on 2026-07-04.

- Backend P1-P4 is current.
- P5 features are planned and excluded from current boundary acceptance.
- Backend automated tests currently include P4 service boundary coverage, but this file remains the planning baseline.

## Boundary Cases

| ID | Status | Boundary | Expected behavior |
| --- | --- | --- | --- |
| B-BOOK-01 | Current | `stock = 0` | Borrow is rejected because no copy is available |
| B-BOOK-02 | Current | `stock = 1` | One eligible borrow can proceed and stock is adjusted by service rules |
| B-BRW-01 | Current | active borrow count = `maxLimit - 1` | New borrow can proceed if other eligibility checks pass |
| B-BRW-02 | Current | active borrow count = `maxLimit` | New borrow is rejected |
| B-BRW-03 | Current | active borrow count = `maxLimit + 1` | New borrow is rejected |
| B-BRW-04 | Current | approved returned borrow exists | Returned borrow no longer counts as active |
| B-BRW-05 | Current | same reader/book before return | Duplicate active borrow is rejected |
| B-BRW-06 | Current | same reader/book after approved return | New borrow is allowed |
| B-RET-01 | Current | duplicate pending return | Second pending return request is rejected |
| B-RET-02 | Current | duplicate approved return | Second return after approved return is rejected |
| B-FINE-01 | Current | overdue days = 0 | No penalty is generated |
| B-FINE-02 | Current | overdue days = 1 | Penalty is generated for one overdue day |
| B-RNW-01 | Current | renewal duration within auto-approve threshold | Renewal follows configured auto-approval behavior |
| B-RNW-02 | Current | renewal duration over max cap | Renewal is capped or rejected by service/config rules |
| B-RNW-03 | Current | repeated renewal after an approved renewal | Repeated renewal is rejected when rule limit is reached |
| B-PEN-01 | Current | unpaid penalty | Reader can pay own unpaid penalty |
| B-PEN-02 | Current | already-paid penalty | Duplicate payment is rejected |

## Planned Boundary Areas

| Area | Status | Notes |
| --- | --- | --- |
| News | Planned | Add when `/api/news/*` is implemented |
| Messages | Planned | Add when `/api/messages/*` is implemented |
| Upload | Planned | Add when `/api/upload/*` is implemented |
| Dashboard | Planned | Add when `/api/dashboard/*` is implemented |
| Frontend build | Pending verification | Requires dependencies to be installed |
