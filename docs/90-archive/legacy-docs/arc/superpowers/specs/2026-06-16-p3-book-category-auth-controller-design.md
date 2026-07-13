> **Archive Status:** Historical Reference
> **Original Path:** `docs/arc/superpowers/specs/2026-06-16-p3-book-category-auth-controller-design.md`
> **Archived On:** `2026-07-13`
> **Historical Purpose:** 返工前契约或实现记录
> **Superseded By:** `docs/README.md`
> **Authority:** 非规范性；不得作为当前需求、接口、数据或验收契约

# P3 Design — Book & Category CRUD + Auth Controller

> **Phase:** P3 of 6 (MyLibrarySystem rebuild)
> **Date:** 2026-06-16
> **Status:** Closed / Historical
> **Predecessor:** P2 — Entity + Repository + JWT Security (complete)
> **Successor:** P4 — Borrow/Return/Renewal/Penalty
>
> **Related:** [ARCHITECTURE.md](../../ARCHITECTURE.md) · [DATA-MODEL.md](../../DATA-MODEL.md) · [API-SPEC.md](../../API-SPEC.md) · [TEST-CASES.md](../../test/TEST-CASES.md)

## Current Status Note (CodeGraph verified, 2026-07-04)

This is a closed historical P3 design document. P3 has been implemented and superseded by the current P4 backend baseline. Retain this file as implementation rationale only. Current contracts are maintained in `README.md`, `docs/API-SPEC.md`, `docs/ARCHITECTURE.md`, `docs/DATA-MODEL.md`, and the 2026-07-04 actual-state report.

Auth, book, category, borrow, return, renewal, and penalty controllers are implemented. The SHA256 + BCrypt password flow in this document is not the current implemented contract; current backend accepts the request password directly and stores/verifies it with BCrypt.

## 1. Scope & Deliverables

P3 builds the Controller + Service layer for authentication, book management, and category management.

| Layer           | Files                                                                           | Count  |
| --------------- | ------------------------------------------------------------------------------- | :----: |
| `controller/`   | AuthController, BookController, CategoryController                              |   3    |
| `service/`      | AuthService, BookService, CategoryService                                       |   3    |
| `service/impl/` | AuthServiceImpl, BookServiceImpl, CategoryServiceImpl                           |   3    |
| `dto/`          | BookRequest, BookResponse, CategoryRequest, CategoryResponse                    |   4    |
| `repository/`   | BookRepository (extend JpaSpecificationExecutor), BorrowRepository (+2 methods) |   2    |
| `config/`       | SecurityConfig (update — role-based rules)                                      |   1    |
| **Total**       | **15 new + 1 modified**                                                         | **16** |

### 1.1 Auth Endpoints

| Method | Path                 |  Auth  | Description                                       |
| ------ | -------------------- | :----: | ------------------------------------------------- |
| `POST` | `/api/auth/login`    | Public | Unified login (admin username + reader studentId) |
| `POST` | `/api/auth/register` | Public | Reader self-registration (creates Reader + User)  |

### 1.2 Book Endpoints

| Method | Path                     |  Auth  | Description                                       |
| ------ | ------------------------ | :----: | ------------------------------------------------- |
| `GET`  | `/api/books/page`        | Public | Unified query: paginated + multi-condition filter |
| `GET`  | `/api/books/info/{id}`   | Admin  | Admin single-book detail                          |
| `GET`  | `/api/books/detail/{id}` | Public | Public single-book detail                         |
| `POST` | `/api/books/save`        | Admin  | Create book                                       |
| `POST` | `/api/books/update`      | Admin  | Update book                                       |
| `POST` | `/api/books/delete`      | Admin  | Batch delete                                      |

Deprecated (method body commented, kept for traceability):

| Method | Path               | Status                                             |
| ------ | ------------------ | -------------------------------------------------- |
| —      | `/api/books/list`  | [Deprecated] Merged into `/books/page`             |
| —      | `/api/books/lists` | [Deprecated] Legacy MyBatis-Plus exact-match query |
| —      | `/api/books/query` | [Deprecated] Legacy single-book exact-field query  |

### 1.3 Category Endpoints

| Method | Path                        |  Auth  | Description                  |
| ------ | --------------------------- | :----: | ---------------------------- |
| `GET`  | `/api/categories/page`      | Admin  | Paginated list               |
| `GET`  | `/api/categories/list`      | Public | Full list (dropdown options) |
| `GET`  | `/api/categories/info/{id}` | Admin  | Single category detail       |
| `POST` | `/api/categories/save`      | Admin  | Create category              |
| `POST` | `/api/categories/update`    | Admin  | Update category              |
| `POST` | `/api/categories/delete`    | Admin  | Batch delete                 |

## 2. Design Decisions (7/7 resolved)

| #   | Decision                         | Choice                                           | Rationale                                                                                                                                                                                                                                                                          |
| --- | -------------------------------- | ------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| D1  | Service architecture             | **1:1 Controller-Service mapping**               | Matches P2 file structure. Each service self-contained. Simple and sufficient for current scale.                                                                                                                                                                                   |
| D2  | Password transmission            | **Frontend SHA256 + Backend BCrypt**             | Prevents plaintext password exposure in transit. Backend unchanged — BCrypt treats any input as password string. Both login and register apply the same SHA256 transform, avoiding hash mismatch. Documented in [library-frontend/README.md](../../../library-frontend/README.md). |
| D3  | Role control                     | **SecurityConfig URL rules + JWT role claim**    | Backend-enforced via `.hasRole("ADMIN")` and `.permitAll()`. Frontend route guards are UX-only. JWT role claim cannot be forged (signed payload).                                                                                                                                  |
| D4  | Book query engine                | **Spring Data JPA Specification**                | Dynamic predicate composition (title LIKE, author LIKE, isbn LIKE, publisher LIKE, categoryId =, year =). Replaces old MyBatis-Plus QueryWrapper.                                                                                                                                  |
| D5  | Book response with category name | **Service-layer batch lookup**                   | Collect all categoryIds from current page → single `CategoryRepository.findAllById()` → Map<Long,String>. No N+1 queries.                                                                                                                                                          |
| D6  | Delete protection                | **Reference integrity check before delete**      | Category: reject if `BookRepository.countByCategoryIdIn(ids) > 0`. Book: reject if `BorrowRepository.countByBookIdIn(ids) > 0`.                                                                                                                                                    |
| D7  | Deprecated endpoints             | **Method body commented with `// [Deprecated]`** | Traceability without code removal.                                                                                                                                                                                                                                                 |

## 3. DTOs

### 3.1 BookRequest

```java
package com.library.dto;

import jakarta.validation.constraints.NotBlank;

public class BookRequest {

    @NotBlank(message = "图书编号不能为空")
    private String isbn;

    @NotBlank(message = "图书名称不能为空")
    private String title;

    private String author;
    private String publisher;
    private String year;
    private String coverImage;
    private Integer stock = 1;
    private String description;
    private Long categoryId;

    // getters & setters
}
```

### 3.2 BookResponse

```java
package com.library.dto;

public class BookResponse {

    private Long id;
    private String isbn;
    private String title;
    private String author;
    private String publisher;
    private String year;
    private String coverImage;
    private Integer stock;
    private String description;
    private Long categoryId;
    private String categoryName;   // JOIN categories.name
    private String addtime;

    // getters & setters
}
```

### 3.3 CategoryRequest

```java
package com.library.dto;

import jakarta.validation.constraints.NotBlank;

public class CategoryRequest {

    @NotBlank(message = "类别名称不能为空")
    private String name;

    // getters & setters
}
```

### 3.4 CategoryResponse

```java
package com.library.dto;

public class CategoryResponse {

    private Long id;
    private String name;
    private String addtime;

    // getters & setters
}
```

> Auth DTOs (LoginRequest, RegisterRequest, LoginResponse) already created in P2 — no changes needed.

## 4. Service Layer

### 4.1 AuthService

```
AuthService
├── login(LoginRequest) → LoginResponse
│     UserRepository.findByUsername()
│     → not found? code=1 "账号或密码错误"
│     → BCryptPasswordEncoder.matches()
│     → mismatch? code=1 "账号或密码错误"
│     → JwtProvider.generateToken(user)
│
└── register(RegisterRequest) → LoginResponse   @Transactional
      1. UserRepository.existsByUsername(studentId)
         → exists? code=1 "该账号已注册"
      2. reader = ReaderRepository.save(Reader)
      3. user = UserRepository.save(User { username=studentId, role=READER,
           password=BCrypt.encode(SHA256(password)), readerId=reader.id })
      4. JwtProvider.generateToken(user)
```

### 4.2 BookService

```
BookService
├── queryBooks(page, limit, title?, author?, publisher?, isbn?, categoryId?, year?)
│     → Page<BookResponse>
│     1. Build Specification<Book> — dynamic predicates (see §4.4)
│     2. BookRepository.findAll(spec, pageable) → Page<Book>
│     3. Collect categoryIds → CategoryRepository.findAllById() → Map<Long, String>
│     4. Map Book → BookResponse (fill categoryName)
│
├── getInfo(id) → BookResponse
│     findById → categoryRepository.findById → assemble → return
│     not found? code=1 "图书不存在"
│
├── getDetail(id) → BookResponse
│     Same as getInfo — both return BookResponse with categoryName
│
├── save(BookRequest) → void
│     BookRepository.existsByIsbn(req.isbn)
│     → exists? code=1 "图书编号已存在"
│     BookRepository.save(book)
│
├── update(id, BookRequest) → void
│     BookRepository.findById(id)
│     → not found? code=1 "图书不存在"
│     BookRepository.existsByIsbnAndIdNot(req.isbn, id)
│     → exists? code=1 "图书编号重复"
│     Update fields → BookRepository.save(book)
│
└── delete(List<Long> ids) → void
      BorrowRepository.countByBookIdIn(ids) > 0
      → code=1 "该图书存在借阅记录，无法删除"
      BookRepository.deleteAllById(ids)
```

### 4.3 CategoryService

```
CategoryService
├── getPage(page, limit) → Page<CategoryResponse>
├── getList() → List<CategoryResponse>   // Full list, no pagination
├── getInfo(id) → CategoryResponse
│     not found? code=1 "类别不存在"
├── save(CategoryRequest) → void
│     CategoryRepository.existsByName(name)
│     → exists? code=1 "类别名称已存在"
├── update(id, CategoryRequest) → void
│     CategoryRepository.findById(id)
│     → not found? code=1 "类别不存在"
│     CategoryRepository.existsByNameAndIdNot(name, id)
│     → exists? code=1 "类别名称重复"
└── delete(List<Long> ids) → void
      BookRepository.countByCategoryIdIn(ids) > 0
      → code=1 "该类别下存在图书，无法删除"
```

### 4.4 Book Query Specification

```java
private Specification<Book> buildSpec(String title, String author,
        String publisher, String isbn, Long categoryId, String year) {
    return (root, query, cb) -> {
        List<Predicate> predicates = new ArrayList<>();
        if (StringUtils.hasText(title)) {
            predicates.add(cb.like(root.get("title"), "%" + title + "%"));
        }
        if (StringUtils.hasText(author)) {
            predicates.add(cb.like(root.get("author"), "%" + author + "%"));
        }
        if (StringUtils.hasText(publisher)) {
            predicates.add(cb.like(root.get("publisher"), "%" + publisher + "%"));
        }
        if (StringUtils.hasText(isbn)) {
            predicates.add(cb.like(root.get("isbn"), "%" + isbn + "%"));
        }
        if (categoryId != null) {
            predicates.add(cb.equal(root.get("categoryId"), categoryId));
        }
        if (StringUtils.hasText(year)) {
            predicates.add(cb.equal(root.get("year"), year));
        }
        return cb.and(predicates.toArray(new Predicate[0]));
    };
}
```

All String fields use LIKE for fuzzy matching. `categoryId` and `year` use exact match. All filters are optional — empty parameters simply produce no predicate, resulting in a full-table query.

## 5. Repository Extensions

### 5.1 BookRepository

```java
@Repository
public interface BookRepository extends JpaRepository<Book, Long>,
        JpaSpecificationExecutor<Book> {

    Optional<Book> findByIsbn(String isbn);          // P2
    boolean existsByIsbn(String isbn);               // NEW — save dedup
    boolean existsByIsbnAndIdNot(String isbn, Long id); // NEW — update dedup
    long countByCategoryIdIn(List<Long> ids);        // NEW — category delete guard
}
```

### 5.2 CategoryRepository

```java
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByName(String name);       // P2
    boolean existsByName(String name);                // NEW
    boolean existsByNameAndIdNot(String name, Long id); // NEW
}
```

### 5.3 BorrowRepository

```java
@Repository
public interface BorrowRepository extends JpaRepository<Borrow, Long> {

    List<Borrow> findByReaderId(Long readerId);       // P2
    long countByReaderIdAndApproved(Long readerId, ApprovalStatus approved); // P2
    List<Borrow> findByBookId(Long bookId);           // P2
    long countByBookIdIn(List<Long> bookIds);         // NEW — book delete guard
}
```

## 6. SecurityConfig Update

Replace current `filterChain` method to add role-based authorization:

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            // Public endpoints
            .requestMatchers(
                "/swagger-ui/**", "/swagger-ui.html",
                "/v3/api-docs/**",
                "/h2-console/**",
                "/error")
                .permitAll()
            // Auth (public)
            .requestMatchers("/auth/login", "/auth/register")
                .permitAll()
            // Book public queries
            .requestMatchers(HttpMethod.GET, "/books/page",
                "/books/detail/**")
                .permitAll()
            // Category dropdown (public)
            .requestMatchers(HttpMethod.GET, "/categories/list")
                .permitAll()
            // Admin-only: all book mutations + admin queries
            .requestMatchers("/books/save", "/books/update", "/books/delete")
                .hasRole("ADMIN")
            .requestMatchers(HttpMethod.GET, "/books/info/**")
                .hasRole("ADMIN")
            // Admin-only: all category operations (except list)
            .requestMatchers("/categories/page",
                "/categories/info/**",
                "/categories/save",
                "/categories/update",
                "/categories/delete")
                .hasRole("ADMIN")
            // Everything else requires authentication
            .anyRequest().authenticated()
        )
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .headers(headers -> headers
            .frameOptions(fo -> fo.sameOrigin())
        );

    return http.build();
}
```

## 7. Request Flow Walkthrough

### 7.1 Book list page (with optional filters)

```
GET /api/books/page?page=1&limit=10&title=计算机&categoryId=3

BookController.queryBooks(params)
  → BookService.queryBooks(params)
      1. buildSpec(title="计算机", categoryId=3) → Specification<Book>
      2. BookRepository.findAll(spec, PageRequest.of(0, 10)) → Page<Book>  (1 SQL)
      3. collect categoryIds [3, 5, 7] → CategoryRepository.findAllById([3,5,7])  (1 SQL)
      4. map Book → BookResponse (fill categoryName from Map<Long,String>)
  → return { code:0, data: { list: [...], total: 47 } }
```

All filters optional. No filters → full-table scan with pagination. Category names fetched in one batch to avoid N+1.

### 7.2 Single book detail

```
GET /api/books/detail/15

BookController.getDetail(15)
  → BookService.getDetail(15)
      1. BookRepository.findById(15) → not found? throw ApiException("图书不存在")
      2. CategoryRepository.findById(book.getCategoryId())
      3. assemble BookResponse
  → return { code:0, data: { id:15, title:"算法导论", categoryName:"计算机科学", ... } }
```

`detail` (public) and `info` (admin) return identical data — the only difference is SecurityConfig role requirement on `/books/info/**`.

### 7.3 Direct URL access to book detail

```
User navigates to /book/15 → Vue Router → page component → GET /api/books/detail/15
Backend call chain identical to §7.2.
```

## 8. Password Flow

### 8.1 Register

```
Frontend                          Backend
SHA256("123456")
  → "8d969eef6eca..."             BCrypt.encode("8d969eef6eca...")
  ──(HTTPS)──>                   → "$2a$10$..."
                                  Store in users.password
```

### 8.2 Login

```
Frontend                          Backend
SHA256("123456")                  UserRepository.findByUsername()
  → "8d969eef6eca..."             BCrypt.matches("8d969eef6eca...", "$2a$10$...")
  ──(HTTPS)──>                   → true → JwtProvider.generateToken()
```

Backend is unaware of frontend hashing — BCrypt treats the SHA256 hex string as "password" and works identically. Both register and login apply the same SHA256 transform, so BCrypt.matches() produces correct results without any backend changes.

> Documented in [library-frontend/README.md](../../../library-frontend/README.md) §Design Decisions.

## 9. Error Messages

| Error                               | HttpStatus | code | msg                            |
| ----------------------------------- | :--------: | :--: | ------------------------------ |
| Login failed                        |    200     |  1   | "账号或密码错误"               |
| Duplicate studentId                 |    200     |  1   | "该账号已注册"                 |
| Book not found                      |    200     |  1   | "图书不存在"                   |
| Duplicate ISBN                      |    200     |  1   | "图书编号已存在"               |
| ISBN already used (update)          |    200     |  1   | "图书编号重复"                 |
| Book has borrow records             |    200     |  1   | "该图书存在借阅记录，无法删除" |
| Category not found                  |    200     |  1   | "类别不存在"                   |
| Duplicate category name             |    200     |  1   | "类别名称已存在"               |
| Category name already used (update) |    200     |  1   | "类别名称重复"                 |
| Category has books                  |    200     |  1   | "该类别下存在图书，无法删除"   |

All errors return HTTP 200 with `code=1`. Auth failures (missing/invalid JWT → 401, wrong role → 403) are handled by Spring Security before reaching controllers.

## 10. TEST-CASES.md Coverage

P3 controllers + services directly enable these test cases:

| Test Case | Enabled By                                                     |
| --------- | -------------------------------------------------------------- |
| BOOK-01   | BookController.save → BookService.save                         |
| BOOK-02   | BookController.queryBooks → BookService.queryBooks             |
| BOOK-03   | BookController.update → BookService.update                     |
| BOOK-04   | BookController.delete → BookService.delete                     |
| BOOK-05   | @Valid on BookRequest → validation errors                      |
| CAT-01    | CategoryController.save/update                                 |
| CAT-02    | CategoryController.list → CategoryService.getList              |
| CAT-03    | CategoryController.delete → CategoryService.delete (ref check) |
| AUTH-01   | AuthController.login (reader via studentId)                    |
| AUTH-02   | AuthController.login (admin via username=admin)                |
| AUTH-03   | AuthController.login (wrong credentials)                       |
| AUTH-04   | AuthController.register (new reader)                           |
| AUTH-05   | AuthController.register (duplicate studentId)                  |

Remaining 22 test cases (BRW/RET/RNW/PEN/NEWS/MSG/UPL/DASH) require P4-P5 controllers.

## 11. Self-Review Checklist

- [x] No TBD/TODO placeholders
- [x] All 7 design decisions documented with rationale
- [x] DTO fields match DATA-MODEL.md entity definitions
- [x] All endpoints mapped to Service methods
- [x] Password flow consistent (SHA256 frontend → BCrypt backend)
- [x] Role-based access control specified in SecurityConfig
- [x] Repository extensions sufficient for all Service methods
- [x] Delete protection checks for both Book and Category
- [x] N+1 query avoided in book listing (batch category lookup)
- [x] Specification query supports all documented filter params
- [x] Deprecated endpoints documented
- [x] Error messages specified for all business exceptions
- [x] Scope boundary clear: no Borrow/Return/Renewal/Penalty controllers
