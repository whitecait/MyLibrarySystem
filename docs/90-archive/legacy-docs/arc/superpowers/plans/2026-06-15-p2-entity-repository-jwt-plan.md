> **Archive Status:** Historical Reference
> **Original Path:** `docs/arc/superpowers/plans/2026-06-15-p2-entity-repository-jwt-plan.md`
> **Archived On:** `2026-07-13`
> **Historical Purpose:** 返工前契约或实现记录
> **Superseded By:** `docs/README.md`
> **Authority:** 非规范性；不得作为当前需求、接口、数据或验收契约

# P2 — Entity + Repository + JWT Security Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Status:** Closed / Historical execution plan. P2 was implemented on 2026-06-15. Any unchecked task boxes below are archival and do not represent current pending work. Current contracts are maintained in the README files and baseline docs under `docs/`.

**Goal:** Build 19 new files (5 entities, 4 enums, 5 repositories, 3 DTOs, 2 security classes) + update SecurityConfig for stateless JWT auth.

**Architecture:** Spring Data JPA entities with Lombok, Spring Data repositories with derived query methods, jjwt 0.12.6 for token generation/validation, OncePerRequestFilter for auth interception. No controllers or services.

**Tech Stack:** Spring Boot 3.5, Spring Data JPA, Spring Security, jjwt 0.12.6, Lombok, H2 (dev), Java 17

**Spec:** [2026-06-15-p2-entity-repository-jwt-design.md](../specs/2026-06-15-p2-entity-repository-jwt-design.md)

> **Execution record:** Implemented (2026-06-15) — `./mvnw compile` BUILD SUCCESS, 26 source files

---

### Task 1: Create Enum Classes

**Files:**
- Create: `library-server/src/main/java/com/library/entity/enums/UserRole.java`
- Create: `library-server/src/main/java/com/library/entity/enums/ApprovalStatus.java`
- Create: `library-server/src/main/java/com/library/entity/enums/PaymentStatus.java`
- Create: `library-server/src/main/java/com/library/entity/enums/Gender.java`

- [ ] **Step 1: Create UserRole enum**

```java
package com.library.entity.enums;

public enum UserRole {
    ADMIN,
    READER
}
```

- [ ] **Step 2: Create ApprovalStatus enum**

```java
package com.library.entity.enums;

public enum ApprovalStatus {
    是,
    否
}
```

- [ ] **Step 3: Create PaymentStatus enum**

```java
package com.library.entity.enums;

public enum PaymentStatus {
    是,
    否
}
```

- [ ] **Step 4: Create Gender enum**

```java
package com.library.entity.enums;

public enum Gender {
    男,
    女
}
```

- [ ] **Step 5: Commit**

```bash
git add library-server/src/main/java/com/library/entity/enums/
git commit -m "feat(p2): add enum classes — UserRole, ApprovalStatus, PaymentStatus, Gender"
```

---

### Task 2: Create Category Entity

**Files:**
- Create: `library-server/src/main/java/com/library/entity/Category.java`

- [ ] **Step 1: Write Category.java**

```java
package com.library.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "addtime", updatable = false)
    private LocalDateTime addtime;

    @PrePersist
    protected void onCreate() {
        this.addtime = LocalDateTime.now();
    }

    public Category() {}

    public Category(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDateTime getAddtime() { return addtime; }
}
```

- [ ] **Step 2: Commit**

```bash
git add library-server/src/main/java/com/library/entity/Category.java
git commit -m "feat(p2): add Category entity"
```

---

### Task 3: Create Reader Entity

**Files:**
- Create: `library-server/src/main/java/com/library/entity/Reader.java`

- [ ] **Step 1: Write Reader.java**

```java
package com.library.entity;

import com.library.entity.enums.Gender;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "readers")
public class Reader {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", unique = true, nullable = false, length = 20)
    private String studentId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 4)
    private Gender gender;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "birth_date", length = 20)
    private String birthDate;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "avatar", length = 200)
    private String avatar;

    @Column(name = "addtime", updatable = false)
    private LocalDateTime addtime;

    @PrePersist
    protected void onCreate() {
        this.addtime = LocalDateTime.now();
    }

    public Reader() {}

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getBirthDate() { return birthDate; }
    public void setBirthDate(String birthDate) { this.birthDate = birthDate; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public LocalDateTime getAddtime() { return addtime; }
}
```

- [ ] **Step 2: Commit**

```bash
git add library-server/src/main/java/com/library/entity/Reader.java
git commit -m "feat(p2): add Reader entity (profile only, auth in User)"
```

---

### Task 4: Create User Entity

**Files:**
- Create: `library-server/src/main/java/com/library/entity/User.java`

- [ ] **Step 1: Write User.java**

```java
package com.library.entity;

import com.library.entity.enums.UserRole;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", unique = true, nullable = false, length = 50)
    private String username;

    @Column(name = "password", nullable = false, length = 200)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private UserRole role;

    @Column(name = "reader_id", nullable = true)
    private Long readerId;

    @Column(name = "addtime", updatable = false)
    private LocalDateTime addtime;

    @PrePersist
    protected void onCreate() {
        this.addtime = LocalDateTime.now();
    }

    public User() {}

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    public Long getReaderId() { return readerId; }
    public void setReaderId(Long readerId) { this.readerId = readerId; }
    public LocalDateTime getAddtime() { return addtime; }
}
```

- [ ] **Step 2: Commit**

```bash
git add library-server/src/main/java/com/library/entity/User.java
git commit -m "feat(p2): add User entity (unified auth, readerId FK nullable)"
```

---

### Task 5: Create Book Entity

**Files:**
- Create: `library-server/src/main/java/com/library/entity/Book.java`

- [ ] **Step 1: Write Book.java**

```java
package com.library.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "isbn", length = 50)
    private String isbn;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "author", length = 100)
    private String author;

    @Column(name = "publisher", length = 100)
    private String publisher;

    @Column(name = "year", length = 10)
    private String year;

    @Column(name = "cover_image", length = 200)
    private String coverImage;

    @Column(name = "stock", nullable = false)
    private Integer stock = 1;

    @Lob
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "addtime", updatable = false)
    private LocalDateTime addtime;

    @PrePersist
    protected void onCreate() {
        this.addtime = LocalDateTime.now();
    }

    public Book() {}

    // Getters and setters
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
    public LocalDateTime getAddtime() { return addtime; }
}
```

- [ ] **Step 2: Commit**

```bash
git add library-server/src/main/java/com/library/entity/Book.java
git commit -m "feat(p2): add Book entity with categoryId FK"
```

---

### Task 6: Create Borrow Entity

**Files:**
- Create: `library-server/src/main/java/com/library/entity/Borrow.java`

- [ ] **Step 1: Write Borrow.java**

```java
package com.library.entity;

import com.library.entity.enums.ApprovalStatus;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "borrows")
public class Borrow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reader_id", nullable = false)
    private Long readerId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "borrow_date")
    private LocalDateTime borrowDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "approved", length = 4)
    private ApprovalStatus approved;

    @Column(name = "reply", length = 500)
    private String reply;

    @Column(name = "addtime", updatable = false)
    private LocalDateTime addtime;

    @PrePersist
    protected void onCreate() {
        this.addtime = LocalDateTime.now();
    }

    public Borrow() {}

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getReaderId() { return readerId; }
    public void setReaderId(Long readerId) { this.readerId = readerId; }
    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public LocalDateTime getBorrowDate() { return borrowDate; }
    public void setBorrowDate(LocalDateTime borrowDate) { this.borrowDate = borrowDate; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public ApprovalStatus getApproved() { return approved; }
    public void setApproved(ApprovalStatus approved) { this.approved = approved; }
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
    public LocalDateTime getAddtime() { return addtime; }
}
```

- [ ] **Step 2: Commit**

```bash
git add library-server/src/main/java/com/library/entity/Borrow.java
git commit -m "feat(p2): add Borrow entity (3NF, dueDate field)"
```

---

### Task 7: Create Repository Interfaces

**Files:**
- Create: `library-server/src/main/java/com/library/repository/UserRepository.java`
- Create: `library-server/src/main/java/com/library/repository/ReaderRepository.java`
- Create: `library-server/src/main/java/com/library/repository/BookRepository.java`
- Create: `library-server/src/main/java/com/library/repository/CategoryRepository.java`
- Create: `library-server/src/main/java/com/library/repository/BorrowRepository.java`

- [ ] **Step 1: Write all 5 repository interfaces**

```java
// ---- UserRepository.java ----
package com.library.repository;

import com.library.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
}
```

```java
// ---- ReaderRepository.java ----
package com.library.repository;

import com.library.entity.Reader;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ReaderRepository extends JpaRepository<Reader, Long> {
    Optional<Reader> findByStudentId(String studentId);
    boolean existsByStudentId(String studentId);
}
```

```java
// ---- BookRepository.java ----
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

```java
// ---- CategoryRepository.java ----
package com.library.repository;

import com.library.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByName(String name);
}
```

```java
// ---- BorrowRepository.java ----
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

- [ ] **Step 2: Commit**

```bash
git add library-server/src/main/java/com/library/repository/
git commit -m "feat(p2): add 5 repository interfaces"
```

---

### Task 8: Create DTOs

**Files:**
- Create: `library-server/src/main/java/com/library/dto/LoginRequest.java`
- Create: `library-server/src/main/java/com/library/dto/RegisterRequest.java`
- Create: `library-server/src/main/java/com/library/dto/LoginResponse.java`

- [ ] **Step 1: Write LoginRequest.java**

```java
package com.library.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    public LoginRequest() {}

    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
```

- [ ] **Step 2: Write RegisterRequest.java**

```java
package com.library.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class RegisterRequest {

    @NotBlank(message = "学号不能为空")
    private String studentId;

    @NotBlank(message = "姓名不能为空")
    private String name;

    @NotBlank(message = "密码不能为空")
    private String password;

    private String gender;

    private String department;

    private String birthDate;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    public RegisterRequest() {}

    // Getters and setters
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getBirthDate() { return birthDate; }
    public void setBirthDate(String birthDate) { this.birthDate = birthDate; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
```

- [ ] **Step 3: Write LoginResponse.java**

```java
package com.library.dto;

import com.library.entity.enums.UserRole;

public class LoginResponse {

    private String token;
    private Long userId;
    private String username;
    private UserRole role;
    private Long readerId;

    public LoginResponse() {}

    public LoginResponse(String token, Long userId, String username, UserRole role, Long readerId) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.readerId = readerId;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    public Long getReaderId() { return readerId; }
    public void setReaderId(Long readerId) { this.readerId = readerId; }
}
```

- [ ] **Step 4: Commit**

```bash
git add library-server/src/main/java/com/library/dto/
git commit -m "feat(p2): add DTOs — LoginRequest, RegisterRequest, LoginResponse"
```

---

### Task 9: Create JwtProvider

**Files:**
- Create: `library-server/src/main/java/com/library/security/JwtProvider.java`

- [ ] **Step 1: Write JwtProvider.java**

```java
package com.library.security;

import com.library.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtProvider {

    private final SecretKey key;
    private final long expirationMs;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .claim("readerId", user.getReaderId())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add library-server/src/main/java/com/library/security/JwtProvider.java
git commit -m "feat(p2): add JwtProvider — token generation and validation"
```

---

### Task 10: Create JwtAuthFilter

**Files:**
- Create: `library-server/src/main/java/com/library/security/JwtAuthFilter.java`

- [ ] **Step 1: Write JwtAuthFilter.java**

```java
package com.library.security;

import com.library.entity.User;
import com.library.entity.enums.UserRole;
import com.library.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtProvider jwtProvider, UserRepository userRepository) {
        this.jwtProvider = jwtProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null && jwtProvider.validateToken(token)) {
            Claims claims = jwtProvider.getClaims(token);
            String username = claims.get("username", String.class);
            String roleName = claims.get("role", String.class);

            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                UserRole role = UserRole.valueOf(roleName);

                List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + role.name())
                );

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(user, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add library-server/src/main/java/com/library/security/JwtAuthFilter.java
git commit -m "feat(p2): add JwtAuthFilter — stateless JWT authentication"
```

---

### Task 11: Update SecurityConfig

**Files:**
- Modify: `library-server/src/main/java/com/library/config/SecurityConfig.java`

- [ ] **Step 1: Replace SecurityConfig.java**

Replace the entire contents of `SecurityConfig.java` with:

```java
package com.library.config;

import com.library.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
                        // Public endpoints
                        .requestMatchers(
                                "/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/h2-console/**",
                                "/error")
                                .permitAll()
                        // Auth endpoints (P3 — controllers not yet created)
                        .requestMatchers("/auth/login", "/auth/register")
                                .permitAll()
                        // Book public listing (P3)
                        .requestMatchers("/books/list")
                                .permitAll()
                        // Everything else requires authentication
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

- [ ] **Step 2: Commit**

```bash
git add library-server/src/main/java/com/library/config/SecurityConfig.java
git commit -m "feat(p2): wire JwtAuthFilter into SecurityConfig"
```

---

### Task 12: Compile and Verify

- [ ] **Step 1: Run Maven compile**

```bash
cd library-server && ./mvnw compile
```

Expected output:
```
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```
All 19 new files + SecurityConfig compile without errors. Hibernate should not report mapping issues since `ddl-auto: update` is only active at runtime.

- [ ] **Step 2: Verify file count**

```bash
find library-server/src/main/java/com/library -name "*.java" -type f | wc -l
```

Expected: 26 (7 Phase 1 files + 19 Phase 2 files)

- [ ] **Step 3: Commit (if any minor fixes were needed)**

```bash
git add -A
git diff --cached --stat
# Only commit if compile fixes were applied
```

---

## Verification Checklist

After implementation, verify against the design spec:

- [ ] 5 entities in `entity/`: User, Reader, Book, Category, Borrow
- [ ] 4 enums in `entity/enums/`: UserRole, ApprovalStatus, PaymentStatus, Gender
- [ ] 5 repositories in `repository/`: all extend JpaRepository with correct custom methods
- [ ] 3 DTOs in `dto/`: LoginRequest, RegisterRequest, LoginResponse
- [ ] 2 security classes: JwtProvider + JwtAuthFilter
- [ ] SecurityConfig updated: JwtAuthFilter wired, public paths configured
- [ ] All entities use `@Enumerated(STRING)` for enum fields
- [ ] All entities use English table names via `@Table`
- [ ] User.readerId is nullable
- [ ] Reader has no password field
- [ ] Borrow has no readerName/studentId/bookIsbn/bookTitle (3NF)
- [ ] Borrow has dueDate field
- [ ] Book.categoryId is Long (not String)
- [ ] JWT payload includes sub, username, role, readerId
- [ ] `./mvnw compile` passes with BUILD SUCCESS
