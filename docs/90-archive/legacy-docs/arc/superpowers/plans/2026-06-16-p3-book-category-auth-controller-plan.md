> **Archive Status:** Historical Reference
> **Original Path:** `docs/arc/superpowers/plans/2026-06-16-p3-book-category-auth-controller-plan.md`
> **Archived On:** `2026-07-13`
> **Historical Purpose:** 返工前契约或实现记录
> **Superseded By:** `docs/README.md`
> **Authority:** 非规范性；不得作为当前需求、接口、数据或验收契约

# P3 — Book & Category CRUD + Auth Controller Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Status:** Closed / Historical execution plan. P3 has been implemented and superseded by the current P4 backend baseline. Any unchecked task boxes below are archival and do not represent current pending work. Current contracts are maintained in the README files and baseline docs under `docs/`.

**Goal:** Build 15 new files (3 controllers, 3 service interfaces, 3 service implementations, 4 DTOs, 2 repository extensions) + update SecurityConfig for role-based access, enabling full CRUD for books, categories, and unified auth login/register.

**Architecture:** 1:1 Controller-Service mapping. AuthService handles login (BCrypt verify + JWT issue) and register (Reader + User creation in transaction). BookService uses Spring Data JPA Specification for dynamic multi-condition queries with batch category-name lookup. CategoryService handles CRUD with reference-integrity delete guards. SecurityConfig enforces ADMIN/READER/Public three-tier access.

**Tech Stack:** Spring Boot 3.5, Spring Data JPA, Spring Security, jjwt 0.12.6, BCrypt, Lombok (optional, not used in P2 entities), H2 (dev), Java 17

**Spec:** [2026-06-16-p3-book-category-auth-controller-design.md](../specs/2026-06-16-p3-book-category-auth-controller-design.md)

---

### Task 1: Create Book DTOs

**Files:**
- Create: `library-server/src/main/java/com/library/dto/BookRequest.java`
- Create: `library-server/src/main/java/com/library/dto/BookResponse.java`

- [ ] **Step 1: Write BookRequest.java**

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

    public BookRequest() {}

    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }
    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }
    public String getCoverImage() { return coverImage; }
    public void setCoverImage(String coverImage) { this.coverImage = coverImage; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
}
```

- [ ] **Step 2: Write BookResponse.java**

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
    private String categoryName;
    private String addtime;

    public BookResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }
    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }
    public String getCoverImage() { return coverImage; }
    public void setCoverImage(String coverImage) { this.coverImage = coverImage; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getAddtime() { return addtime; }
    public void setAddtime(String addtime) { this.addtime = addtime; }
}
```

- [ ] **Step 3: Commit**

```bash
git add library-server/src/main/java/com/library/dto/BookRequest.java library-server/src/main/java/com/library/dto/BookResponse.java
git commit -m "feat(p3): add BookRequest and BookResponse DTOs"
```

---

### Task 2: Create Category DTOs

**Files:**
- Create: `library-server/src/main/java/com/library/dto/CategoryRequest.java`
- Create: `library-server/src/main/java/com/library/dto/CategoryResponse.java`

- [ ] **Step 1: Write CategoryRequest.java**

```java
package com.library.dto;

import jakarta.validation.constraints.NotBlank;

public class CategoryRequest {

    @NotBlank(message = "类别名称不能为空")
    private String name;

    public CategoryRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
```

- [ ] **Step 2: Write CategoryResponse.java**

```java
package com.library.dto;

public class CategoryResponse {

    private Long id;
    private String name;
    private String addtime;

    public CategoryResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddtime() { return addtime; }
    public void setAddtime(String addtime) { this.addtime = addtime; }
}
```

- [ ] **Step 3: Commit**

```bash
git add library-server/src/main/java/com/library/dto/CategoryRequest.java library-server/src/main/java/com/library/dto/CategoryResponse.java
git commit -m "feat(p3): add CategoryRequest and CategoryResponse DTOs"
```

---

### Task 3: Extend BookRepository

**Files:**
- Modify: `library-server/src/main/java/com/library/repository/BookRepository.java`

- [ ] **Step 1: Read current BookRepository.java to confirm existing content**

The current file at `library-server/src/main/java/com/library/repository/BookRepository.java` should contain:

```java
package com.library.repository;

import com.library.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {
    Optional<Book> findByIsbn(String isbn);
}
```

- [ ] **Step 2: Replace BookRepository.java with extended version**

```java
package com.library.repository;

import com.library.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long>,
        JpaSpecificationExecutor<Book> {

    Optional<Book> findByIsbn(String isbn);

    boolean existsByIsbn(String isbn);

    boolean existsByIsbnAndIdNot(String isbn, Long id);

    long countByCategoryIdIn(List<Long> categoryIds);
}
```

- [ ] **Step 3: Commit**

```bash
git add library-server/src/main/java/com/library/repository/BookRepository.java
git commit -m "feat(p3): extend BookRepository — JpaSpecificationExecutor + exists/count methods"
```

---

### Task 4: Extend CategoryRepository

**Files:**
- Modify: `library-server/src/main/java/com/library/repository/CategoryRepository.java`

- [ ] **Step 1: Replace CategoryRepository.java with extended version**

```java
package com.library.repository;

import com.library.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);
}
```

- [ ] **Step 2: Commit**

```bash
git add library-server/src/main/java/com/library/repository/CategoryRepository.java
git commit -m "feat(p3): extend CategoryRepository — existsByName, existsByNameAndIdNot"
```

---

### Task 5: Extend BorrowRepository

**Files:**
- Modify: `library-server/src/main/java/com/library/repository/BorrowRepository.java`

- [ ] **Step 1: Read current BorrowRepository.java to confirm existing content**

The current file at `library-server/src/main/java/com/library/repository/BorrowRepository.java` should contain:

```java
package com.library.repository;

import com.library.entity.Borrow;
import com.library.entity.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BorrowRepository extends JpaRepository<Borrow, Long> {
    List<Borrow> findByReaderId(Long readerId);
    long countByReaderIdAndApproved(Long readerId, ApprovalStatus approved);
    List<Borrow> findByBookId(Long bookId);
}
```

- [ ] **Step 2: Replace BorrowRepository.java — add countByBookIdIn**

```java
package com.library.repository;

import com.library.entity.Borrow;
import com.library.entity.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BorrowRepository extends JpaRepository<Borrow, Long> {

    List<Borrow> findByReaderId(Long readerId);

    long countByReaderIdAndApproved(Long readerId, ApprovalStatus approved);

    List<Borrow> findByBookId(Long bookId);

    long countByBookIdIn(List<Long> bookIds);
}
```

- [ ] **Step 3: Commit**

```bash
git add library-server/src/main/java/com/library/repository/BorrowRepository.java
git commit -m "feat(p3): extend BorrowRepository — countByBookIdIn for delete guard"
```

---

### Task 6: Create AuthService

**Files:**
- Create: `library-server/src/main/java/com/library/service/AuthService.java`
- Create: `library-server/src/main/java/com/library/service/impl/AuthServiceImpl.java`

- [ ] **Step 1: Write AuthService interface**

```java
package com.library.service;

import com.library.dto.LoginRequest;
import com.library.dto.LoginResponse;
import com.library.dto.RegisterRequest;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    LoginResponse register(RegisterRequest request);
}
```

- [ ] **Step 2: Write AuthServiceImpl.java**

```java
package com.library.service.impl;

import com.library.dto.LoginRequest;
import com.library.dto.LoginResponse;
import com.library.dto.RegisterRequest;
import com.library.entity.Reader;
import com.library.entity.User;
import com.library.entity.enums.Gender;
import com.library.entity.enums.UserRole;
import com.library.exception.ApiException;
import com.library.repository.ReaderRepository;
import com.library.repository.UserRepository;
import com.library.security.JwtProvider;
import com.library.service.AuthService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final ReaderRepository readerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public AuthServiceImpl(UserRepository userRepository,
                           ReaderRepository readerRepository,
                           PasswordEncoder passwordEncoder,
                           JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.readerRepository = readerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ApiException("账号或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ApiException("账号或密码错误");
        }

        String token = jwtProvider.generateToken(user);
        return new LoginResponse(token, user.getId(), user.getUsername(),
                user.getRole(), user.getReaderId());
    }

    @Override
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        String studentId = request.getStudentId();

        if (userRepository.existsByUsername(studentId)) {
            throw new ApiException("该账号已注册");
        }

        // 1. Create Reader (profile only, no password)
        Reader reader = new Reader();
        reader.setStudentId(studentId);
        reader.setName(request.getName());
        reader.setDepartment(request.getDepartment());
        reader.setBirthDate(request.getBirthDate());
        reader.setPhone(request.getPhone());
        if (request.getGender() != null && !request.getGender().isEmpty()) {
            reader.setGender(Gender.valueOf(request.getGender()));
        }
        reader = readerRepository.save(reader);

        // 2. Create User (auth, linked to Reader)
        User user = new User();
        user.setUsername(studentId);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(UserRole.READER);
        user.setReaderId(reader.getId());
        user = userRepository.save(user);

        // 3. Issue JWT
        String token = jwtProvider.generateToken(user);
        return new LoginResponse(token, user.getId(), user.getUsername(),
                user.getRole(), user.getReaderId());
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add library-server/src/main/java/com/library/service/AuthService.java library-server/src/main/java/com/library/service/impl/AuthServiceImpl.java
git commit -m "feat(p3): add AuthService — login (BCrypt+JWT) and register (Reader+User @Transactional)"
```

---

### Task 7: Create BookService

**Files:**
- Create: `library-server/src/main/java/com/library/service/BookService.java`
- Create: `library-server/src/main/java/com/library/service/impl/BookServiceImpl.java`

- [ ] **Step 1: Write BookService interface**

```java
package com.library.service;

import com.library.dto.BookRequest;
import com.library.dto.BookResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public interface BookService {

    Page<BookResponse> queryBooks(int page, int limit,
            String title, String author, String publisher,
            String isbn, Long categoryId, String year);

    BookResponse getInfo(Long id);

    BookResponse getDetail(Long id);

    void save(BookRequest request);

    void update(Long id, BookRequest request);

    void delete(List<Long> ids);
}
```

- [ ] **Step 2: Write BookServiceImpl.java**

```java
package com.library.service.impl;

import com.library.dto.BookRequest;
import com.library.dto.BookResponse;
import com.library.entity.Book;
import com.library.entity.Category;
import com.library.entity.Borrow;
import com.library.exception.ApiException;
import com.library.repository.BookRepository;
import com.library.repository.BorrowRepository;
import com.library.repository.CategoryRepository;
import com.library.service.BookService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BookServiceImpl implements BookService {

    private final BookRepository bookRepository;
    private final CategoryRepository categoryRepository;
    private final BorrowRepository borrowRepository;

    public BookServiceImpl(BookRepository bookRepository,
                           CategoryRepository categoryRepository,
                           BorrowRepository borrowRepository) {
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
        this.borrowRepository = borrowRepository;
    }

    @Override
    public Page<BookResponse> queryBooks(int page, int limit,
            String title, String author, String publisher,
            String isbn, Long categoryId, String year) {
        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("id").descending());
        Specification<Book> spec = buildSpec(title, author, publisher, isbn, categoryId, year);
        Page<Book> bookPage = bookRepository.findAll(spec, pageable);

        // Batch lookup category names (avoid N+1)
        List<Long> categoryIds = bookPage.getContent().stream()
                .map(Book::getCategoryId)
                .filter(cid -> cid != null)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> categoryNameMap = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));

        return bookPage.map(book -> toResponse(book, categoryNameMap));
    }

    @Override
    public BookResponse getInfo(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ApiException("图书不存在"));
        String categoryName = null;
        if (book.getCategoryId() != null) {
            categoryName = categoryRepository.findById(book.getCategoryId())
                    .map(Category::getName).orElse(null);
        }
        return toResponse(book, categoryName);
    }

    @Override
    public BookResponse getDetail(Long id) {
        return getInfo(id); // same logic, different auth level in SecurityConfig
    }

    @Override
    public void save(BookRequest request) {
        if (bookRepository.existsByIsbn(request.getIsbn())) {
            throw new ApiException("图书编号已存在");
        }
        Book book = new Book();
        applyRequest(book, request);
        bookRepository.save(book);
    }

    @Override
    public void update(Long id, BookRequest request) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ApiException("图书不存在"));
        if (bookRepository.existsByIsbnAndIdNot(request.getIsbn(), id)) {
            throw new ApiException("图书编号重复");
        }
        applyRequest(book, request);
        bookRepository.save(book);
    }

    @Override
    public void delete(List<Long> ids) {
        long count = borrowRepository.countByBookIdIn(ids);
        if (count > 0) {
            throw new ApiException("该图书存在借阅记录，无法删除");
        }
        bookRepository.deleteAllById(ids);
    }

    // ---- private helpers ----

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

    private BookResponse toResponse(Book book, Map<Long, String> categoryNameMap) {
        String categoryName = null;
        if (book.getCategoryId() != null) {
            categoryName = categoryNameMap.get(book.getCategoryId());
        }
        return toResponse(book, categoryName);
    }

    private BookResponse toResponse(Book book, String categoryName) {
        BookResponse r = new BookResponse();
        r.setId(book.getId());
        r.setIsbn(book.getIsbn());
        r.setTitle(book.getTitle());
        r.setAuthor(book.getAuthor());
        r.setPublisher(book.getPublisher());
        r.setYear(book.getYear());
        r.setCoverImage(book.getCoverImage());
        r.setStock(book.getStock());
        r.setDescription(book.getDescription());
        r.setCategoryId(book.getCategoryId());
        r.setCategoryName(categoryName);
        r.setAddtime(book.getAddtime() != null ? book.getAddtime().toString() : null);
        return r;
    }

    private void applyRequest(Book book, BookRequest req) {
        book.setIsbn(req.getIsbn());
        book.setTitle(req.getTitle());
        book.setAuthor(req.getAuthor());
        book.setPublisher(req.getPublisher());
        book.setYear(req.getYear());
        book.setCoverImage(req.getCoverImage());
        book.setStock(req.getStock() != null ? req.getStock() : 1);
        book.setDescription(req.getDescription());
        book.setCategoryId(req.getCategoryId());
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add library-server/src/main/java/com/library/service/BookService.java library-server/src/main/java/com/library/service/impl/BookServiceImpl.java
git commit -m "feat(p3): add BookService — Specification query, batch category lookup, delete guard"
```

---

### Task 8: Create CategoryService

**Files:**
- Create: `library-server/src/main/java/com/library/service/CategoryService.java`
- Create: `library-server/src/main/java/com/library/service/impl/CategoryServiceImpl.java`

- [ ] **Step 1: Write CategoryService interface**

```java
package com.library.service;

import com.library.dto.CategoryRequest;
import com.library.dto.CategoryResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public interface CategoryService {

    Page<CategoryResponse> getPage(int page, int limit);

    List<CategoryResponse> getList();

    CategoryResponse getInfo(Long id);

    void save(CategoryRequest request);

    void update(Long id, CategoryRequest request);

    void delete(List<Long> ids);
}
```

- [ ] **Step 2: Write CategoryServiceImpl.java**

```java
package com.library.service.impl;

import com.library.dto.CategoryRequest;
import com.library.dto.CategoryResponse;
import com.library.entity.Category;
import com.library.exception.ApiException;
import com.library.repository.BookRepository;
import com.library.repository.CategoryRepository;
import com.library.service.CategoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final BookRepository bookRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository,
                               BookRepository bookRepository) {
        this.categoryRepository = categoryRepository;
        this.bookRepository = bookRepository;
    }

    @Override
    public Page<CategoryResponse> getPage(int page, int limit) {
        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("id").descending());
        return categoryRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    public List<CategoryResponse> getList() {
        return categoryRepository.findAll(Sort.by("id").ascending())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryResponse getInfo(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException("类别不存在"));
        return toResponse(category);
    }

    @Override
    public void save(CategoryRequest request) {
        if (categoryRepository.existsByName(request.getName())) {
            throw new ApiException("类别名称已存在");
        }
        Category category = new Category(request.getName());
        categoryRepository.save(category);
    }

    @Override
    public void update(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException("类别不存在"));
        if (categoryRepository.existsByNameAndIdNot(request.getName(), id)) {
            throw new ApiException("类别名称重复");
        }
        category.setName(request.getName());
        categoryRepository.save(category);
    }

    @Override
    public void delete(List<Long> ids) {
        long count = bookRepository.countByCategoryIdIn(ids);
        if (count > 0) {
            throw new ApiException("该类别下存在图书，无法删除");
        }
        categoryRepository.deleteAllById(ids);
    }

    private CategoryResponse toResponse(Category category) {
        CategoryResponse r = new CategoryResponse();
        r.setId(category.getId());
        r.setName(category.getName());
        r.setAddtime(category.getAddtime() != null ? category.getAddtime().toString() : null);
        return r;
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add library-server/src/main/java/com/library/service/CategoryService.java library-server/src/main/java/com/library/service/impl/CategoryServiceImpl.java
git commit -m "feat(p3): add CategoryService — CRUD with delete guard via BookRepository ref check"
```

---

### Task 9: Create AuthController

**Files:**
- Create: `library-server/src/main/java/com/library/controller/AuthController.java`

- [ ] **Step 1: Write AuthController.java**

```java
package com.library.controller;

import com.library.common.ApiResponse;
import com.library.dto.LoginRequest;
import com.library.dto.LoginResponse;
import com.library.dto.RegisterRequest;
import com.library.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        LoginResponse response = authService.register(request);
        return ApiResponse.success(response);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add library-server/src/main/java/com/library/controller/AuthController.java
git commit -m "feat(p3): add AuthController — /auth/login and /auth/register"
```

---

### Task 10: Create BookController

**Files:**
- Create: `library-server/src/main/java/com/library/controller/BookController.java`

- [ ] **Step 1: Write BookController.java**

```java
package com.library.controller;

import com.library.common.ApiResponse;
import com.library.dto.BookRequest;
import com.library.dto.BookResponse;
import com.library.service.BookService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String publisher,
            @RequestParam(required = false) String isbn,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String year) {
        Page<BookResponse> result = bookService.queryBooks(
                page, limit, title, author, publisher, isbn, categoryId, year);
        return ApiResponse.success(Map.of("list", result.getContent(), "total", result.getTotalElements()));
    }

    @GetMapping("/info/{id}")
    public ApiResponse<BookResponse> info(@PathVariable Long id) {
        return ApiResponse.success(bookService.getInfo(id));
    }

    @GetMapping("/detail/{id}")
    public ApiResponse<BookResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(bookService.getDetail(id));
    }

    @PostMapping("/save")
    public ApiResponse<Void> save(@Valid @RequestBody BookRequest request) {
        bookService.save(request);
        return ApiResponse.success(null);
    }

    @PostMapping("/update")
    public ApiResponse<Void> update(@RequestParam Long id, @Valid @RequestBody BookRequest request) {
        bookService.update(id, request);
        return ApiResponse.success(null);
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody List<Long> ids) {
        bookService.delete(ids);
        return ApiResponse.success(null);
    }

    // [已停用] 合并入 /books/page — 保留前端兼容性参考
    // @Deprecated
    // @GetMapping("/list")
    // public ApiResponse<Map<String, Object>> list(...) { ... }

    // [已停用] 旧 MyBatis-Plus 精确匹配查询
    // @Deprecated
    // @GetMapping("/lists")
    // public ApiResponse<?> lists(...) { ... }

    // [已停用] 旧精确字段单本查询
    // @Deprecated
    // @GetMapping("/query")
    // public ApiResponse<?> query(...) { ... }
}
```

- [ ] **Step 2: Commit**

```bash
git add library-server/src/main/java/com/library/controller/BookController.java
git commit -m "feat(p3): add BookController — 6 active endpoints, 3 deprecated (commented)"
```

---

### Task 11: Create CategoryController

**Files:**
- Create: `library-server/src/main/java/com/library/controller/CategoryController.java`

- [ ] **Step 1: Write CategoryController.java**

```java
package com.library.controller;

import com.library.common.ApiResponse;
import com.library.dto.CategoryRequest;
import com.library.dto.CategoryResponse;
import com.library.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit) {
        Page<CategoryResponse> result = categoryService.getPage(page, limit);
        return ApiResponse.success(Map.of("list", result.getContent(), "total", result.getTotalElements()));
    }

    @GetMapping("/list")
    public ApiResponse<List<CategoryResponse>> list() {
        return ApiResponse.success(categoryService.getList());
    }

    @GetMapping("/info/{id}")
    public ApiResponse<CategoryResponse> info(@PathVariable Long id) {
        return ApiResponse.success(categoryService.getInfo(id));
    }

    @PostMapping("/save")
    public ApiResponse<Void> save(@Valid @RequestBody CategoryRequest request) {
        categoryService.save(request);
        return ApiResponse.success(null);
    }

    @PostMapping("/update")
    public ApiResponse<Void> update(@RequestParam Long id, @Valid @RequestBody CategoryRequest request) {
        categoryService.update(id, request);
        return ApiResponse.success(null);
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody List<Long> ids) {
        categoryService.delete(ids);
        return ApiResponse.success(null);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add library-server/src/main/java/com/library/controller/CategoryController.java
git commit -m "feat(p3): add CategoryController — 6 endpoints (page/list/info/save/update/delete)"
```

---

### Task 12: Update SecurityConfig — Role-Based Access

**Files:**
- Modify: `library-server/src/main/java/com/library/config/SecurityConfig.java`

- [ ] **Step 1: Read current SecurityConfig.java to confirm existing content**

The current file is at `library-server/src/main/java/com/library/config/SecurityConfig.java`.

- [ ] **Step 2: Replace SecurityConfig.java with role-based rules**

```java
package com.library.config;

import com.library.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ---- Public: infrastructure ----
                        .requestMatchers(
                                "/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/h2-console/**",
                                "/error")
                                .permitAll()
                        // ---- Public: auth ----
                        .requestMatchers("/auth/login", "/auth/register")
                                .permitAll()
                        // ---- Public: book browsing ----
                        .requestMatchers(HttpMethod.GET, "/books/page",
                                "/books/detail/**")
                                .permitAll()
                        // ---- Public: category dropdown ----
                        .requestMatchers(HttpMethod.GET, "/categories/list")
                                .permitAll()
                        // ---- Admin: book management ----
                        .requestMatchers("/books/save", "/books/update", "/books/delete")
                                .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/books/info/**")
                                .hasRole("ADMIN")
                        // ---- Admin: category management (except list) ----
                        .requestMatchers("/categories/page",
                                "/categories/info/**",
                                "/categories/save",
                                "/categories/update",
                                "/categories/delete")
                                .hasRole("ADMIN")
                        // ---- Everything else requires authentication ----
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .headers(headers -> headers
                        .frameOptions(fo -> fo.sameOrigin())
                );

        return http.build();
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add library-server/src/main/java/com/library/config/SecurityConfig.java
git commit -m "feat(p3): update SecurityConfig — role-based access (Public/Admin/Authenticated)"
```

---

### Task 13: Compile and Verify

- [ ] **Step 1: Verify file count**

```bash
find library-server/src/main/java/com/library -name "*.java" -type f | wc -l
```

Expected: 41 (26 Phase 1+2 files + 15 Phase 3 files)

- [ ] **Step 2: Run Maven compile**

```bash
cd library-server && ./mvnw compile
```

Expected output:
```
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

All 15 new files + 4 modified files compile without errors.

- [ ] **Step 3: Commit (if any compile fixes were applied)**

```bash
git add -A
git diff --cached --stat
# Only commit if compile fixes were applied
```

---

## Verification Checklist

After implementation, verify against the design spec:

- [ ] 3 controllers in `controller/`: AuthController, BookController, CategoryController
- [ ] 3 service interfaces in `service/`: AuthService, BookService, CategoryService
- [ ] 3 service implementations in `service/impl/`
- [ ] 4 DTOs: BookRequest, BookResponse, CategoryRequest, CategoryResponse
- [ ] BookRepository extends JpaSpecificationExecutor + 3 new methods
- [ ] CategoryRepository has existsByName + existsByNameAndIdNot
- [ ] BorrowRepository has countByBookIdIn
- [ ] AuthController: login (BCrypt verify + JWT) and register (@Transactional Reader+User)
- [ ] BookController: 6 active endpoints + 3 deprecated (commented)
- [ ] CategoryController: 6 endpoints, list() returns full unpaginated result
- [ ] BookService.queryBooks() uses Specification for dynamic filter composition
- [ ] BookService batch-looks-up category names (Map<Long,String>) to avoid N+1
- [ ] CategoryService.delete() checks BookRepository.countByCategoryIdIn()
- [ ] BookService.delete() checks BorrowRepository.countByBookIdIn()
- [ ] SecurityConfig enforces Public/Admin/Authenticated three-tier rules
- [ ] Deprecated endpoints have `// [已停用]` comment but no active method body
- [ ] `./mvnw compile` passes with BUILD SUCCESS
