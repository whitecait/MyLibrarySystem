> **Archive Status:** Historical Reference
> **Original Path:** `docs/superpowers/plans/2026-06-23-p4-borrow-return-renewal-penalty-plan.md`
> **Archived On:** `2026-07-13`
> **Historical Purpose:** 已关闭的历史执行计划
> **Superseded By:** `docs/README.md`
> **Authority:** 非规范性；不得作为当前需求、接口、数据或验收契约

# P4 Borrow/Return/Renewal/Penalty Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Status:** Closed / Historical execution plan. P4 has been implemented. Any unchecked task boxes below are archival and do not represent current pending work. Current behavior and later fixes are maintained in the README files and baseline docs under `docs/`.

**Goal:** Build the complete borrow/return/renewal/penalty backend API — 4 controllers, 4 services, 3 new entities, 3 new repositories, 9 DTOs, with tri-state approval and auto-approval grading.

**Architecture:** Strict 3NF — Return/Renewal/Penalty trace through a single `borrowId` FK chain (Return → Borrow → Reader/Book). Controller → Service interface → ServiceImpl (@Transactional) → Repository (Spring Data JPA). All business exceptions use ApiException (HTTP 200 + code=1). ApprovalStatus expanded to 3 values: 待审核/是/否. Config-driven durations, fines, and auto-approval thresholds via `@ConfigurationProperties`.

**Tech Stack:** Spring Boot 3.5, Spring Data JPA, H2 (test), JUnit 5, MockMvc, Java 17

> it has archived in `docs/arc/superpowers/plans/2026-06-23-p4-borrow-return-renewal-penalty-plan.md` for reference.
