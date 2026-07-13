> **Archive Status:** Historical Reference
> **Original Path:** `docs/superpowers/plans/2026-06-16-p3-book-category-auth-controller-plan.md`
> **Archived On:** `2026-07-13`
> **Historical Purpose:** 已关闭的历史执行计划
> **Superseded By:** `docs/README.md`
> **Authority:** 非规范性；不得作为当前需求、接口、数据或验收契约

# P3 — Book & Category CRUD + Auth Controller Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Status:** Closed / Historical execution plan. P3 has been implemented and superseded by the current P4 backend baseline. Any unchecked task boxes below are archival and do not represent current pending work. Current contracts are maintained in the README files and baseline docs under `docs/`.

**Goal:** Build 15 new files (3 controllers, 3 service interfaces, 3 service implementations, 4 DTOs, 2 repository extensions) + update SecurityConfig for role-based access, enabling full CRUD for books, categories, and unified auth login/register.

**Architecture:** 1:1 Controller-Service mapping. AuthService handles login (BCrypt verify + JWT issue) and register (Reader + User creation in transaction). BookService uses Spring Data JPA Specification for dynamic multi-condition queries with batch category-name lookup. CategoryService handles CRUD with reference-integrity delete guards. SecurityConfig enforces ADMIN/READER/Public three-tier access.

**Tech Stack:** Spring Boot 3.5, Spring Data JPA, Spring Security, jjwt 0.12.6, BCrypt, Lombok (optional, not used in P2 entities), H2 (dev), Java 17

**Spec:** [2026-06-16-p3-book-category-auth-controller-design.md](../specs/2026-06-16-p3-book-category-auth-controller-design.md)

> it has archived in `docs/arc/superpowers/plans/2026-06-16-p3-book-category-auth-controller-plan.md` for reference.
