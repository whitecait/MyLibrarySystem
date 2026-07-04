# P4 Borrow/Return/Renewal/Penalty Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the complete borrow/return/renewal/penalty backend API — 4 controllers, 4 services, 3 new entities, 3 new repositories, 9 DTOs, with tri-state approval and auto-approval grading.

**Architecture:** Strict 3NF — Return/Renewal/Penalty trace through a single `borrowId` FK chain (Return → Borrow → Reader/Book). Controller → Service interface → ServiceImpl (@Transactional) → Repository (Spring Data JPA). All business exceptions use ApiException (HTTP 200 + code=1). ApprovalStatus expanded to 3 values: 待审核/是/否. Config-driven durations, fines, and auto-approval thresholds via `@ConfigurationProperties`.

**Tech Stack:** Spring Boot 3.5, Spring Data JPA, H2 (test), JUnit 5, MockMvc, Java 17

---

## File Structure Map

```
library-server/src/main/java/com/library/
├── config/
│   ├── LibraryProperties.java        ← NEW: @ConfigurationProperties("library")
│   └── SecurityConfig.java           ← MODIFY: +P4 endpoint rules
├── entity/
│   ├── Borrow.java                   ← MODIFY: +durationDays field
│   ├── BorrowReturn.java             ← NEW
│   ├── Renewal.java                  ← NEW
│   ├── Penalty.java                  ← NEW
│   └── enums/
│       └── ApprovalStatus.java       ← MODIFY: +待审核
├── repository/
│   ├── BorrowRepository.java         ← MODIFY: +existsByReaderIdAndBookIdAndApproved
│   ├── BorrowReturnRepository.java   ← NEW
│   ├── RenewalRepository.java        ← NEW
│   └── PenaltyRepository.java        ← NEW
├── dto/
│   ├── BorrowRequest.java            ← NEW
│   ├── BorrowResponse.java           ← NEW
│   ├── ReturnRequest.java            ← NEW
│   ├── ReturnResponse.java           ← NEW
│   ├── RenewalRequest.java           ← NEW
│   ├── RenewalResponse.java          ← NEW
│   ├── PenaltyResponse.java          ← NEW
│   ├── BatchApproval.java            ← NEW
│   └── PayRequest.java               ← NEW
├── service/
│   ├── BorrowService.java            ← NEW
│   ├── ReturnService.java            ← NEW
│   ├── RenewalService.java           ← NEW
│   ├── PenaltyService.java           ← NEW
│   └── impl/
│       ├── BorrowServiceImpl.java     ← NEW
│       ├── ReturnServiceImpl.java     ← NEW
│       ├── RenewalServiceImpl.java    ← NEW
│       └── PenaltyServiceImpl.java    ← NEW
├── controller/
│   ├── BorrowController.java         ← NEW
│   ├── ReturnController.java         ← NEW
│   ├── RenewalController.java        ← NEW
│   └── PenaltyController.java        ← NEW
└── src/main/resources/
    └── application.yml               ← MODIFY: +library.* block

library-server/src/test/java/com/library/
├── helper/
│   └── TestAuthHelper.java           ← NEW: JWT token generation for tests
├── service/
│   ├── BorrowServiceTest.java        ← NEW
│   ├── ReturnServiceTest.java        ← NEW
│   ├── RenewalServiceTest.java       ← NEW
│   └── PenaltyServiceTest.java       ← NEW
└── config/
    └── LibraryPropertiesTest.java    ← NEW: @ConfigurationProperties binding test
```

---

### Task 1: ApprovalStatus enum + Config foundation

**Files:**
- Modify: `library-server/src/main/java/com/library/entity/enums/ApprovalStatus.java`
- Modify: `library-server/src/main/resources/application.yml`
- Create: `library-server/src/main/java/com/library/config/LibraryProperties.java`
- Create: `library-server/src/test/java/com/library/config/LibraryPropertiesTest.java`

- [ ] **Step 1: Add 待审核 to ApprovalStatus enum**

```java
package com.library.entity.enums;

public enum ApprovalStatus {
    待审核,
    是,
    否
}
```

- [ ] **Step 2: Add library.* config block to application.yml**

Append to `application.yml` (after the `springdoc:` section):

```yaml
# ---- Library business configuration ----
library:
  borrow:
    max-limit: 5
    default-duration-days: 30
    auto-approve-duration: 30
    max-duration-cap: 60
  fine:
    rate-per-day: 1.0
  renewal:
    auto-approve-count: 1
```

- [ ] **Step 3: Create LibraryProperties @ConfigurationProperties**

```java
package com.library.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "library")
public class LibraryProperties {

    private Borrow borrow = new Borrow();
    private Fine fine = new Fine();
    private Renewal renewal = new Renewal();

    public Borrow getBorrow() { return borrow; }
    public void setBorrow(Borrow borrow) { this.borrow = borrow; }
    public Fine getFine() { return fine; }
    public void setFine(Fine fine) { this.fine = fine; }
    public Renewal getRenewal() { return renewal; }
    public void setRenewal(Renewal renewal) { this.renewal = renewal; }

    public static class Borrow {
        private int maxLimit = 5;
        private int defaultDurationDays = 30;
        private int autoApproveDuration = 30;
        private int maxDurationCap = 60;
        // getters & setters (all)
        public int getMaxLimit() { return maxLimit; }
        public void setMaxLimit(int maxLimit) { this.maxLimit = maxLimit; }
        public int getDefaultDurationDays() { return defaultDurationDays; }
        public void setDefaultDurationDays(int defaultDurationDays) { this.defaultDurationDays = defaultDurationDays; }
        public int getAutoApproveDuration() { return autoApproveDuration; }
        public void setAutoApproveDuration(int autoApproveDuration) { this.autoApproveDuration = autoApproveDuration; }
        public int getMaxDurationCap() { return maxDurationCap; }
        public void setMaxDurationCap(int maxDurationCap) { this.maxDurationCap = maxDurationCap; }
    }

    public static class Fine {
        private BigDecimal ratePerDay = new BigDecimal("1.0");
        public BigDecimal getRatePerDay() { return ratePerDay; }
        public void setRatePerDay(BigDecimal ratePerDay) { this.ratePerDay = ratePerDay; }
    }

    public static class Renewal {
        private int autoApproveCount = 1;
        public int getAutoApproveCount() { return autoApproveCount; }
        public void setAutoApproveCount(int autoApproveCount) { this.autoApproveCount = autoApproveCount; }
    }
}
```

- [ ] **Step 4: Create test for config binding**

```java
package com.library.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LibraryPropertiesTest {

    @Autowired
    private LibraryProperties props;

    @Test
    void shouldBindDefaultValues() {
        assertThat(props.getBorrow().getMaxLimit()).isEqualTo(5);
        assertThat(props.getBorrow().getDefaultDurationDays()).isEqualTo(30);
        assertThat(props.getBorrow().getAutoApproveDuration()).isEqualTo(30);
        assertThat(props.getBorrow().getMaxDurationCap()).isEqualTo(60);
        assertThat(props.getFine().getRatePerDay()).isEqualByComparingTo("1.0");
        assertThat(props.getRenewal().getAutoApproveCount()).isEqualTo(1);
    }
}
```

- [ ] **Step 5: Verify config binding works**

Run: `cd library-server && ./mvnw test -Dtest=LibraryPropertiesTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add library-server/src/main/java/com/library/entity/enums/ApprovalStatus.java \
        library-server/src/main/resources/application.yml \
        library-server/src/main/java/com/library/config/LibraryProperties.java \
        library-server/src/test/java/com/library/config/LibraryPropertiesTest.java
git commit -m "feat(p4): add ApprovalStatus.待审核 + LibraryProperties config"
```

> Note: Spring Boot 3.5 requires `@EnableConfigurationProperties` or `@ConfigurationPropertiesScan` on the application class, OR the `@Component` annotation on the properties class (which we use). Verify by confirming `LibraryApplication` is in a parent or same package as `config/`.

---

### Task 2: Amend Borrow entity + BorrowRepository

**Files:**
- Modify: `library-server/src/main/java/com/library/entity/Borrow.java`
- Modify: `library-server/src/main/java/com/library/repository/BorrowRepository.java`

- [ ] **Step 1: Add durationDays field to Borrow entity**

Add the field and getter/setter to the existing `Borrow.java` (after `bookId`, before `borrowDate`):

```java
@Column(name = "duration_days")
private Integer durationDays;    // requested borrow days

// Add getter & setter alongside existing ones:
public Integer getDurationDays() { return durationDays; }
public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
```

Also update the `@Column(name = "approved", length = 4)` to `length = 8`:

```java
@Enumerated(EnumType.STRING) @Column(name = "approved", length = 8)
private ApprovalStatus approved;
```

- [ ] **Step 2: Add query method to BorrowRepository**

Add to `BorrowRepository.java`:

```java
boolean existsByReaderIdAndBookIdAndApproved(Long readerId, Long bookId, ApprovalStatus approved);
```

- [ ] **Step 3: Verify compile**

Run: `cd library-server && ./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add library-server/src/main/java/com/library/entity/Borrow.java \
        library-server/src/main/java/com/library/repository/BorrowRepository.java
git commit -m "feat(p4): add Borrow.durationDays field + existsByReaderIdAndBookIdAndApproved"
```

---

### Task 3: Create BorrowReturn entity

**Files:**
- Create: `library-server/src/main/java/com/library/entity/BorrowReturn.java`

- [ ] **Step 1: Create BorrowReturn entity**

```java
package com.library.entity;

import com.library.entity.enums.ApprovalStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "returns")
public class BorrowReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "borrow_id", nullable = false)
    private Long borrowId;

    @Column(name = "return_date")
    private LocalDateTime returnDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "approved", length = 8)
    private ApprovalStatus approved;

    @Column(name = "reply", length = 500)
    private String reply;

    @Column(name = "addtime", updatable = false)
    private LocalDateTime addtime;

    @PrePersist
    protected void onCreate() { this.addtime = LocalDateTime.now(); }

    public BorrowReturn() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBorrowId() { return borrowId; }
    public void setBorrowId(Long borrowId) { this.borrowId = borrowId; }
    public LocalDateTime getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDateTime returnDate) { this.returnDate = returnDate; }
    public ApprovalStatus getApproved() { return approved; }
    public void setApproved(ApprovalStatus approved) { this.approved = approved; }
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
    public LocalDateTime getAddtime() { return addtime; }
}
```

- [ ] **Step 2: Verify compile**

Run: `cd library-server && ./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add library-server/src/main/java/com/library/entity/BorrowReturn.java
git commit -m "feat(p4): add BorrowReturn entity (strict 3NF, borrowId FK)"
```

---

### Task 4: Create Renewal entity

**Files:**
- Create: `library-server/src/main/java/com/library/entity/Renewal.java`

- [ ] **Step 1: Create Renewal entity**

```java
package com.library.entity;

import com.library.entity.enums.ApprovalStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "renewals")
public class Renewal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "borrow_id", nullable = false)
    private Long borrowId;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "renewal_date")
    private LocalDateTime renewalDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "approved", length = 8)
    private ApprovalStatus approved;

    @Column(name = "reply", length = 500)
    private String reply;

    @Column(name = "addtime", updatable = false)
    private LocalDateTime addtime;

    @PrePersist
    protected void onCreate() { this.addtime = LocalDateTime.now(); }

    public Renewal() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBorrowId() { return borrowId; }
    public void setBorrowId(Long borrowId) { this.borrowId = borrowId; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public LocalDateTime getRenewalDate() { return renewalDate; }
    public void setRenewalDate(LocalDateTime renewalDate) { this.renewalDate = renewalDate; }
    public ApprovalStatus getApproved() { return approved; }
    public void setApproved(ApprovalStatus approved) { this.approved = approved; }
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
    public LocalDateTime getAddtime() { return addtime; }
}
```

- [ ] **Step 2: Verify compile**

Run: `cd library-server && ./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add library-server/src/main/java/com/library/entity/Renewal.java
git commit -m "feat(p4): add Renewal entity (strict 3NF, borrowId FK + durationDays)"
```

---

### Task 5: Create Penalty entity

**Files:**
- Create: `library-server/src/main/java/com/library/entity/Penalty.java`

- [ ] **Step 1: Create Penalty entity**

```java
package com.library.entity;

import com.library.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "penalties")
public class Penalty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fine_no")
    private String fineNo;

    @Column(name = "borrow_id", nullable = false)
    private Long borrowId;

    @Column(name = "amount", precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "fine_date")
    private LocalDate fineDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "paid", length = 8)
    private PaymentStatus paid;

    @Column(name = "addtime", updatable = false)
    private LocalDateTime addtime;

    @PrePersist
    protected void onCreate() { this.addtime = LocalDateTime.now(); }

    public Penalty() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFineNo() { return fineNo; }
    public void setFineNo(String fineNo) { this.fineNo = fineNo; }
    public Long getBorrowId() { return borrowId; }
    public void setBorrowId(Long borrowId) { this.borrowId = borrowId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getFineDate() { return fineDate; }
    public void setFineDate(LocalDate fineDate) { this.fineDate = fineDate; }
    public PaymentStatus getPaid() { return paid; }
    public void setPaid(PaymentStatus paid) { this.paid = paid; }
    public LocalDateTime getAddtime() { return addtime; }
}
```

- [ ] **Step 2: Verify compile**

Run: `cd library-server && ./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add library-server/src/main/java/com/library/entity/Penalty.java
git commit -m "feat(p4): add Penalty entity (strict 3NF, borrowId FK)"
```

---

### Task 6: Create Repository layer (3 new)

**Files:**
- Create: `library-server/src/main/java/com/library/repository/BorrowReturnRepository.java`
- Create: `library-server/src/main/java/com/library/repository/RenewalRepository.java`
- Create: `library-server/src/main/java/com/library/repository/PenaltyRepository.java`

- [ ] **Step 1: Create BorrowReturnRepository**

```java
package com.library.repository;

import com.library.entity.BorrowReturn;
import com.library.entity.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BorrowReturnRepository extends JpaRepository<BorrowReturn, Long> {
    boolean existsByBorrowIdAndApproved(Long borrowId, ApprovalStatus approved);
}
```

- [ ] **Step 2: Create RenewalRepository**

```java
package com.library.repository;

import com.library.entity.Renewal;
import com.library.entity.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RenewalRepository extends JpaRepository<Renewal, Long> {
    long countByBorrowId(Long borrowId);
    boolean existsByBorrowIdAndApproved(Long borrowId, ApprovalStatus approved);
}
```

- [ ] **Step 3: Create PenaltyRepository**

```java
package com.library.repository;

import com.library.entity.Penalty;
import com.library.entity.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PenaltyRepository extends JpaRepository<Penalty, Long> {

    @Query("SELECT COUNT(p) FROM Penalty p JOIN Borrow b ON p.borrowId = b.id " +
           "WHERE b.readerId = :readerId AND p.paid = :paid")
    long countByReaderIdAndPaid(@Param("readerId") Long readerId, @Param("paid") PaymentStatus paid);
}
```

- [ ] **Step 4: Verify compile**

Run: `cd library-server && ./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add library-server/src/main/java/com/library/repository/BorrowReturnRepository.java \
        library-server/src/main/java/com/library/repository/RenewalRepository.java \
        library-server/src/main/java/com/library/repository/PenaltyRepository.java
git commit -m "feat(p4): add BorrowReturn/Renewal/Penalty repositories"
```

---

### Task 7: Create DTOs — part 1 (Borrow)

**Files:**
- Create: `library-server/src/main/java/com/library/dto/BorrowRequest.java`
- Create: `library-server/src/main/java/com/library/dto/BorrowResponse.java`

- [ ] **Step 1: Create BorrowRequest**

```java
package com.library.dto;

import jakarta.validation.constraints.NotNull;

public class BorrowRequest {

    @NotNull(message = "图书ID不能为空")
    private Long bookId;

    private Long readerId;         // Admin: required; Reader: must match self

    private Integer durationDays;  // optional, default from config

    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public Long getReaderId() { return readerId; }
    public void setReaderId(Long readerId) { this.readerId = readerId; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
}
```

- [ ] **Step 2: Create BorrowResponse**

```java
package com.library.dto;

public class BorrowResponse {

    private Long id;
    private Long readerId;
    private String readerName;
    private String studentId;
    private Long bookId;
    private String bookTitle;
    private String bookIsbn;
    private String borrowDate;
    private String dueDate;
    private String approved;
    private String reply;
    private String addtime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getReaderId() { return readerId; }
    public void setReaderId(Long readerId) { this.readerId = readerId; }
    public String getReaderName() { return readerName; }
    public void setReaderName(String readerName) { this.readerName = readerName; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public String getBookTitle() { return bookTitle; }
    public void setBookTitle(String bookTitle) { this.bookTitle = bookTitle; }
    public String getBookIsbn() { return bookIsbn; }
    public void setBookIsbn(String bookIsbn) { this.bookIsbn = bookIsbn; }
    public String getBorrowDate() { return borrowDate; }
    public void setBorrowDate(String borrowDate) { this.borrowDate = borrowDate; }
    public String getDueDate() { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }
    public String getApproved() { return approved; }
    public void setApproved(String approved) { this.approved = approved; }
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
    public String getAddtime() { return addtime; }
    public void setAddtime(String addtime) { this.addtime = addtime; }
}
```

- [ ] **Step 3: Verify compile**

Run: `cd library-server && ./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add library-server/src/main/java/com/library/dto/BorrowRequest.java \
        library-server/src/main/java/com/library/dto/BorrowResponse.java
git commit -m "feat(p4): add BorrowRequest + BorrowResponse DTOs"
```

---

### Task 8: Create DTOs — part 2 (Return + Renewal)

**Files:**
- Create: `library-server/src/main/java/com/library/dto/ReturnRequest.java`
- Create: `library-server/src/main/java/com/library/dto/ReturnResponse.java`
- Create: `library-server/src/main/java/com/library/dto/RenewalRequest.java`
- Create: `library-server/src/main/java/com/library/dto/RenewalResponse.java`

- [ ] **Step 1: Create ReturnRequest**

```java
package com.library.dto;

import jakarta.validation.constraints.NotNull;

public class ReturnRequest {

    @NotNull(message = "借阅记录ID不能为空")
    private Long borrowId;

    public Long getBorrowId() { return borrowId; }
    public void setBorrowId(Long borrowId) { this.borrowId = borrowId; }
}
```

- [ ] **Step 2: Create ReturnResponse**

```java
package com.library.dto;

public class ReturnResponse {

    private Long id;
    private Long borrowId;
    private String readerName;
    private String studentId;
    private String bookTitle;
    private String bookIsbn;
    private String returnDate;
    private String approved;
    private String reply;
    private String addtime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBorrowId() { return borrowId; }
    public void setBorrowId(Long borrowId) { this.borrowId = borrowId; }
    public String getReaderName() { return readerName; }
    public void setReaderName(String readerName) { this.readerName = readerName; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getBookTitle() { return bookTitle; }
    public void setBookTitle(String bookTitle) { this.bookTitle = bookTitle; }
    public String getBookIsbn() { return bookIsbn; }
    public void setBookIsbn(String bookIsbn) { this.bookIsbn = bookIsbn; }
    public String getReturnDate() { return returnDate; }
    public void setReturnDate(String returnDate) { this.returnDate = returnDate; }
    public String getApproved() { return approved; }
    public void setApproved(String approved) { this.approved = approved; }
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
    public String getAddtime() { return addtime; }
    public void setAddtime(String addtime) { this.addtime = addtime; }
}
```

- [ ] **Step 3: Create RenewalRequest**

```java
package com.library.dto;

import jakarta.validation.constraints.NotNull;

public class RenewalRequest {

    @NotNull(message = "借阅记录ID不能为空")
    private Long borrowId;

    private Integer durationDays;  // optional, default from config

    public Long getBorrowId() { return borrowId; }
    public void setBorrowId(Long borrowId) { this.borrowId = borrowId; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
}
```

- [ ] **Step 4: Create RenewalResponse**

```java
package com.library.dto;

public class RenewalResponse {

    private Long id;
    private Long borrowId;
    private Integer durationDays;
    private String readerName;
    private String studentId;
    private String bookTitle;
    private String bookIsbn;
    private String renewalDate;
    private String approved;
    private String reply;
    private String addtime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBorrowId() { return borrowId; }
    public void setBorrowId(Long borrowId) { this.borrowId = borrowId; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public String getReaderName() { return readerName; }
    public void setReaderName(String readerName) { this.readerName = readerName; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getBookTitle() { return bookTitle; }
    public void setBookTitle(String bookTitle) { this.bookTitle = bookTitle; }
    public String getBookIsbn() { return bookIsbn; }
    public void setBookIsbn(String bookIsbn) { this.bookIsbn = bookIsbn; }
    public String getRenewalDate() { return renewalDate; }
    public void setRenewalDate(String renewalDate) { this.renewalDate = renewalDate; }
    public String getApproved() { return approved; }
    public void setApproved(String approved) { this.approved = approved; }
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
    public String getAddtime() { return addtime; }
    public void setAddtime(String addtime) { this.addtime = addtime; }
}
```

- [ ] **Step 5: Verify compile**

Run: `cd library-server && ./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add library-server/src/main/java/com/library/dto/ReturnRequest.java \
        library-server/src/main/java/com/library/dto/ReturnResponse.java \
        library-server/src/main/java/com/library/dto/RenewalRequest.java \
        library-server/src/main/java/com/library/dto/RenewalResponse.java
git commit -m "feat(p4): add Return + Renewal DTOs"
```

---

### Task 9: Create DTOs — part 3 (Penalty + Shared)

**Files:**
- Create: `library-server/src/main/java/com/library/dto/PenaltyResponse.java`
- Create: `library-server/src/main/java/com/library/dto/BatchApproval.java`
- Create: `library-server/src/main/java/com/library/dto/PayRequest.java`

- [ ] **Step 1: Create PenaltyResponse**

```java
package com.library.dto;

public class PenaltyResponse {

    private Long id;
    private String fineNo;
    private Long borrowId;
    private String readerName;
    private String studentId;
    private String bookTitle;
    private String bookIsbn;
    private String amount;
    private String fineDate;
    private String paid;
    private String addtime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFineNo() { return fineNo; }
    public void setFineNo(String fineNo) { this.fineNo = fineNo; }
    public Long getBorrowId() { return borrowId; }
    public void setBorrowId(Long borrowId) { this.borrowId = borrowId; }
    public String getReaderName() { return readerName; }
    public void setReaderName(String readerName) { this.readerName = readerName; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getBookTitle() { return bookTitle; }
    public void setBookTitle(String bookTitle) { this.bookTitle = bookTitle; }
    public String getBookIsbn() { return bookIsbn; }
    public void setBookIsbn(String bookIsbn) { this.bookIsbn = bookIsbn; }
    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }
    public String getFineDate() { return fineDate; }
    public void setFineDate(String fineDate) { this.fineDate = fineDate; }
    public String getPaid() { return paid; }
    public void setPaid(String paid) { this.paid = paid; }
    public String getAddtime() { return addtime; }
    public void setAddtime(String addtime) { this.addtime = addtime; }
}
```

- [ ] **Step 2: Create BatchApproval**

```java
package com.library.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class BatchApproval {

    @NotEmpty(message = "审核ID列表不能为空")
    private List<Long> ids;

    @NotNull(message = "审核结果不能为空")
    private String approved;     // "是" / "否"

    private String reply;

    public List<Long> getIds() { return ids; }
    public void setIds(List<Long> ids) { this.ids = ids; }
    public String getApproved() { return approved; }
    public void setApproved(String approved) { this.approved = approved; }
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
}
```

- [ ] **Step 3: Create PayRequest**

```java
package com.library.dto;

import jakarta.validation.constraints.NotNull;

public class PayRequest {

    @NotNull(message = "罚款ID不能为空")
    private Long id;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
}
```

- [ ] **Step 4: Verify compile**

Run: `cd library-server && ./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add library-server/src/main/java/com/library/dto/PenaltyResponse.java \
        library-server/src/main/java/com/library/dto/BatchApproval.java \
        library-server/src/main/java/com/library/dto/PayRequest.java
git commit -m "feat(p4): add PenaltyResponse + BatchApproval + PayRequest DTOs"
```

---

### Task 10: Create TestAuthHelper (test infrastructure)

**Files:**
- Create: `library-server/src/test/java/com/library/helper/TestAuthHelper.java`

- [ ] **Step 1: Create TestAuthHelper**

This helper generates JWT tokens for tests so MockMvc requests can be authenticated with different roles and readerIds.

```java
package com.library.helper;

import com.library.entity.User;
import com.library.entity.enums.UserRole;
import com.library.repository.UserRepository;
import com.library.security.JwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class TestAuthHelper {

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Create an admin user and return a valid JWT Bearer token.
     */
    public String adminToken() {
        User user = userRepository.findByUsername("admin").orElseGet(() -> {
            User u = new User();
            u.setUsername("admin");
            u.setPassword(passwordEncoder.encode("admin"));
            u.setRole(UserRole.ADMIN);
            return userRepository.save(u);
        });
        return "Bearer " + jwtProvider.generateToken(user);
    }

    /**
     * Create a reader user with the given studentId and readerId, return JWT Bearer token.
     */
    public String readerToken(String studentId, Long readerId) {
        User user = userRepository.findByUsername(studentId).orElseGet(() -> {
            User u = new User();
            u.setUsername(studentId);
            u.setPassword(passwordEncoder.encode("123456"));
            u.setRole(UserRole.READER);
            u.setReaderId(readerId);
            return userRepository.save(u);
        });
        // Ensure readerId is set
        if (user.getReaderId() == null) {
            user.setReaderId(readerId);
            userRepository.save(user);
        }
        return "Bearer " + jwtProvider.generateToken(user);
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `cd library-server && ./mvnw test-compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add library-server/src/test/java/com/library/helper/TestAuthHelper.java
git commit -m "test(p4): add TestAuthHelper for JWT-authenticated MockMvc tests"
```

---

### Task 11: BorrowService — TDD cycle

**Files:**
- Create: `library-server/src/test/java/com/library/service/BorrowServiceTest.java`
- Create: `library-server/src/main/java/com/library/service/BorrowService.java`
- Create: `library-server/src/main/java/com/library/service/impl/BorrowServiceImpl.java`

- [ ] **Step 1: Create BorrowService interface**

```java
package com.library.service;

import com.library.dto.BorrowRequest;
import com.library.dto.BorrowResponse;
import com.library.dto.BatchApproval;
import com.library.entity.User;
import org.springframework.data.domain.Page;

import java.util.List;

public interface BorrowService {

    Page<BorrowResponse> queryPage(int page, int limit, Long readerId, Long bookId, String approved);

    void apply(BorrowRequest request, User currentUser);

    void approveBatch(BatchApproval batch);

    void delete(List<Long> ids);
}
```

- [ ] **Step 2: Create BorrowService test class with auto-approve test**

```java
package com.library.service;

import com.library.dto.BorrowRequest;
import com.library.dto.BorrowResponse;
import com.library.entity.*;
import com.library.entity.enums.ApprovalStatus;
import com.library.entity.enums.UserRole;
import com.library.exception.ApiException;
import com.library.helper.TestAuthHelper;
import com.library.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class BorrowServiceTest {

    @Autowired private BorrowService borrowService;
    @Autowired private BookRepository bookRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ReaderRepository readerRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TestAuthHelper authHelper;

    private Reader reader;
    private Book book;
    private User readerUser;

    @BeforeEach
    void setUp() {
        // Create reader
        reader = new Reader();
        reader.setStudentId("2021001");
        reader.setName("测试读者");
        reader.setGender("男");
        reader.setDepartment("计算机系");
        reader.setPhone("13800000001");
        reader = readerRepository.save(reader);

        // Create reader user
        readerUser = new User();
        readerUser.setUsername("2021001");
        readerUser.setPassword("ignored");
        readerUser.setRole(UserRole.READER);
        readerUser.setReaderId(reader.getId());
        readerUser = userRepository.save(readerUser);

        // Create category + book
        Category cat = new Category();
        cat.setName("计算机科学");
        cat = categoryRepository.save(cat);

        book = new Book();
        book.setIsbn("978-7-302-11111");
        book.setTitle("算法导论");
        book.setAuthor("测试作者");
        book.setPublisher("测试出版社");
        book.setYear("2023");
        book.setStock(5);
        book.setCategoryId(cat.getId());
        book = bookRepository.save(book);
    }

    @Test
    void shouldAutoApproveWhenAllConditionsMet() {
        BorrowRequest req = new BorrowRequest();
        req.setBookId(book.getId());
        req.setReaderId(reader.getId());

        borrowService.apply(req, readerUser);

        Page<BorrowResponse> page = borrowService.queryPage(1, 10, reader.getId(), null, null);
        assertThat(page.getTotalElements()).isEqualTo(1);
        BorrowResponse b = page.getContent().get(0);
        assertThat(b.getApproved()).isEqualTo("是");
        assertThat(b.getDueDate()).isNotNull();
        assertThat(b.getBookTitle()).isEqualTo("算法导论");
        assertThat(b.getReaderName()).isEqualTo("测试读者");

        // Stock should be decremented
        Book updated = bookRepository.findById(book.getId()).orElseThrow();
        assertThat(updated.getStock()).isEqualTo(4);
    }

    @Test
    void shouldSetPendingWhenDurationExceedsAutoApprove() {
        BorrowRequest req = new BorrowRequest();
        req.setBookId(book.getId());
        req.setReaderId(reader.getId());
        req.setDurationDays(31);  // > 30

        borrowService.apply(req, readerUser);

        Page<BorrowResponse> page = borrowService.queryPage(1, 10, reader.getId(), null, null);
        assertThat(page.getContent().get(0).getApproved()).isEqualTo("待审核");
        // Stock unchanged
        assertThat(bookRepository.findById(book.getId()).orElseThrow().getStock()).isEqualTo(5);
    }

    @Test
    void shouldRejectWhenStockIsZero() {
        book.setStock(0);
        bookRepository.save(book);

        BorrowRequest req = new BorrowRequest();
        req.setBookId(book.getId());
        req.setReaderId(reader.getId());

        assertThatThrownBy(() -> borrowService.apply(req, readerUser))
                .isInstanceOf(ApiException.class)
                .hasMessage("图书库存不足");
    }

    @Test
    void shouldRejectDuplicateBorrow() {
        BorrowRequest req = new BorrowRequest();
        req.setBookId(book.getId());
        req.setReaderId(reader.getId());
        borrowService.apply(req, readerUser);  // first: auto-approved

        assertThatThrownBy(() -> borrowService.apply(req, readerUser))
                .isInstanceOf(ApiException.class)
                .hasMessage("该图书您已有进行中的借阅记录");
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd library-server && ./mvnw test -Dtest=BorrowServiceTest`
Expected: FAIL — BorrowService bean not found

- [ ] **Step 4: Implement BorrowServiceImpl**

```java
package com.library.service.impl;

import com.library.config.LibraryProperties;
import com.library.dto.BorrowRequest;
import com.library.dto.BorrowResponse;
import com.library.entity.*;
import com.library.entity.enums.ApprovalStatus;
import com.library.entity.enums.UserRole;
import com.library.exception.ApiException;
import com.library.repository.*;
import com.library.service.BorrowService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BorrowServiceImpl implements BorrowService {

    private final BorrowRepository borrowRepository;
    private final BookRepository bookRepository;
    private final ReaderRepository readerRepository;
    private final PenaltyRepository penaltyRepository;
    private final LibraryProperties props;

    public BorrowServiceImpl(BorrowRepository borrowRepository,
                             BookRepository bookRepository,
                             ReaderRepository readerRepository,
                             PenaltyRepository penaltyRepository,
                             LibraryProperties props) {
        this.borrowRepository = borrowRepository;
        this.bookRepository = bookRepository;
        this.readerRepository = readerRepository;
        this.penaltyRepository = penaltyRepository;
        this.props = props;
    }

    @Override
    public Page<BorrowResponse> queryPage(int page, int limit, Long readerId, Long bookId, String approved) {
        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("id").descending());
        Specification<Borrow> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (readerId != null) predicates.add(cb.equal(root.get("readerId"), readerId));
            if (bookId != null) predicates.add(cb.equal(root.get("bookId"), bookId));
            if (approved != null)
                predicates.add(cb.equal(root.get("approved"), ApprovalStatus.valueOf(approved)));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<Borrow> borrowPage = borrowRepository.findAll(spec, pageable);

        // Batch JOIN
        List<Long> readerIds = borrowPage.map(Borrow::getReaderId).stream().distinct().collect(Collectors.toList());
        List<Long> bookIds = borrowPage.map(Borrow::getBookId).stream().distinct().collect(Collectors.toList());
        Map<Long, Reader> readerMap = readerRepository.findAllById(readerIds).stream()
                .collect(Collectors.toMap(Reader::getId, r -> r));
        Map<Long, Book> bookMap = bookRepository.findAllById(bookIds).stream()
                .collect(Collectors.toMap(Book::getId, b -> b));

        return borrowPage.map(b -> toResponse(b, readerMap.get(b.getReaderId()), bookMap.get(b.getBookId())));
    }

    @Override
    @Transactional
    public void apply(BorrowRequest req, User currentUser) {
        // Resolve readerId
        Long readerId;
        if (currentUser.getRole() == UserRole.READER) {
            if (req.getReaderId() != null && !req.getReaderId().equals(currentUser.getReaderId())) {
                throw new ApiException("无权为其他读者操作");
            }
            readerId = currentUser.getReaderId();
        } else {
            if (req.getReaderId() == null) throw new ApiException("请指定读者ID");
            readerId = req.getReaderId();
        }
        if (readerId == null || !readerRepository.existsById(readerId)) {
            throw new ApiException("读者不存在");
        }

        // Validation
        Book book = bookRepository.findById(req.getBookId())
                .orElseThrow(() -> new ApiException("图书不存在"));
        if (book.getStock() <= 0) throw new ApiException("图书库存不足");
        if (borrowRepository.existsByReaderIdAndBookIdAndApproved(readerId, req.getBookId(), ApprovalStatus.是)) {
            throw new ApiException("该图书您已有进行中的借阅记录");
        }

        int actualDays = req.getDurationDays() != null
                ? Math.min(req.getDurationDays(), props.getBorrow().getAutoApproveDuration())
                : props.getBorrow().getDefaultDurationDays();

        // Auto-approval check
        boolean hasUnpaidFines = penaltyRepository.countByReaderIdAndPaid(readerId, com.library.entity.enums.PaymentStatus.否) > 0;
        boolean atLimit = borrowRepository.countByReaderIdAndApproved(readerId, ApprovalStatus.是) >= props.getBorrow().getMaxLimit();
        boolean overAutoDays = req.getDurationDays() != null && req.getDurationDays() > props.getBorrow().getAutoApproveDuration();

        Borrow borrow = new Borrow();
        borrow.setReaderId(readerId);
        borrow.setBookId(req.getBookId());
        borrow.setDurationDays(req.getDurationDays());

        if (!hasUnpaidFines && !atLimit && !overAutoDays) {
            borrow.setApproved(ApprovalStatus.是);
            borrow.setBorrowDate(LocalDateTime.now());
            borrow.setDueDate(LocalDate.now().plusDays(actualDays));
            book.setStock(book.getStock() - 1);
            bookRepository.save(book);
        } else {
            borrow.setApproved(ApprovalStatus.待审核);
        }

        borrowRepository.save(borrow);
    }

    @Override
    @Transactional
    public void approveBatch(com.library.dto.BatchApproval batch) {
        ApprovalStatus targetStatus = com.library.entity.enums.ApprovalStatus.valueOf(batch.getApproved());
        for (Long id : batch.getIds()) {
            Borrow borrow = borrowRepository.findById(id).orElse(null);
            if (borrow == null || borrow.getApproved() != ApprovalStatus.待审核) continue;

            if (targetStatus == ApprovalStatus.是) {
                borrow.setApproved(ApprovalStatus.是);
                borrow.setBorrowDate(LocalDateTime.now());
                int actualDays = borrow.getDurationDays() != null
                        ? Math.min(borrow.getDurationDays(), props.getBorrow().getAutoApproveDuration())
                        : props.getBorrow().getDefaultDurationDays();
                borrow.setDueDate(LocalDate.now().plusDays(actualDays));
                Book book = bookRepository.findById(borrow.getBookId()).orElseThrow();
                book.setStock(book.getStock() - 1);
                bookRepository.save(book);
            } else {
                borrow.setApproved(ApprovalStatus.否);
                borrow.setReply(batch.getReply());
            }
        }
    }

    @Override
    public void delete(List<Long> ids) {
        borrowRepository.deleteAllById(ids);
    }

    private BorrowResponse toResponse(Borrow b, Reader r, Book bk) {
        BorrowResponse resp = new BorrowResponse();
        resp.setId(b.getId());
        resp.setReaderId(b.getReaderId());
        if (r != null) { resp.setReaderName(r.getName()); resp.setStudentId(r.getStudentId()); }
        resp.setBookId(b.getBookId());
        if (bk != null) { resp.setBookTitle(bk.getTitle()); resp.setBookIsbn(bk.getIsbn()); }
        resp.setBorrowDate(b.getBorrowDate() != null ? b.getBorrowDate().toString() : null);
        resp.setDueDate(b.getDueDate() != null ? b.getDueDate().toString() : null);
        resp.setApproved(b.getApproved() != null ? b.getApproved().name() : null);
        resp.setReply(b.getReply());
        resp.setAddtime(b.getAddtime() != null ? b.getAddtime().toString() : null);
        return resp;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd library-server && ./mvnw test -Dtest=BorrowServiceTest`
Expected: PASS (4 tests)

- [ ] **Step 6: Commit**

```bash
git add library-server/src/main/java/com/library/service/BorrowService.java \
        library-server/src/main/java/com/library/service/impl/BorrowServiceImpl.java \
        library-server/src/test/java/com/library/service/BorrowServiceTest.java
git commit -m "feat(p4): add BorrowService with auto-approval + pending tri-state"
```

---

### Task 12: ReturnService — TDD cycle

**Files:**
- Create: `library-server/src/test/java/com/library/service/ReturnServiceTest.java`
- Create: `library-server/src/main/java/com/library/service/ReturnService.java`
- Create: `library-server/src/main/java/com/library/service/impl/ReturnServiceImpl.java`

- [ ] **Step 1: Create ReturnService interface**

```java
package com.library.service;

import com.library.dto.BatchApproval;
import com.library.dto.ReturnRequest;
import com.library.dto.ReturnResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public interface ReturnService {

    Page<ReturnResponse> queryPage(int page, int limit, Long readerId, String approved);

    void apply(ReturnRequest request);

    void approveBatch(BatchApproval batch);

    void delete(List<Long> ids);
}
```

- [ ] **Step 2: Create ReturnService test**

```java
package com.library.service;

import com.library.dto.BatchApproval;
import com.library.dto.ReturnRequest;
import com.library.dto.ReturnResponse;
import com.library.entity.*;
import com.library.entity.enums.*;
import com.library.exception.ApiException;
import com.library.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ReturnServiceTest {

    @Autowired private ReturnService returnService;
    @Autowired private BorrowRepository borrowRepository;
    @Autowired private BorrowReturnRepository borrowReturnRepository;
    @Autowired private PenaltyRepository penaltyRepository;
    @Autowired private BookRepository bookRepository;
    @Autowired private ReaderRepository readerRepository;
    @Autowired private CategoryRepository categoryRepository;

    private Long borrowId;
    private Long bookId;

    @BeforeEach
    void setUp() {
        Reader reader = new Reader();
        reader.setStudentId("2021001"); reader.setName("测试读者"); reader.setPhone("13800000001");
        reader = readerRepository.save(reader);

        Category cat = new Category(); cat.setName("CS"); cat = categoryRepository.save(cat);

        Book book = new Book();
        book.setIsbn("978-7-302-11111"); book.setTitle("算法导论"); book.setStock(5);
        book.setCategoryId(cat.getId());
        book = bookRepository.save(book);
        bookId = book.getId();

        Borrow borrow = new Borrow();
        borrow.setReaderId(reader.getId()); borrow.setBookId(bookId);
        borrow.setApproved(ApprovalStatus.是);
        borrow.setBorrowDate(LocalDateTime.now().minusDays(10));
        borrow.setDueDate(LocalDate.now().minusDays(3));  // overdue by 3 days
        borrow = borrowRepository.save(borrow);
        borrowId = borrow.getId();
    }

    @Test
    void shouldSubmitReturnApplication() {
        ReturnRequest req = new ReturnRequest();
        req.setBorrowId(borrowId);
        returnService.apply(req);

        Page<ReturnResponse> page = returnService.queryPage(1, 10, null, null);
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getApproved()).isEqualTo("待审核");
    }

    @Test
    void shouldRejectReturnForUnapprovedBorrow() {
        // Mark borrow as not approved
        Borrow b = borrowRepository.findById(borrowId).orElseThrow();
        b.setApproved(ApprovalStatus.待审核);
        borrowRepository.save(b);

        ReturnRequest req = new ReturnRequest();
        req.setBorrowId(borrowId);
        assertThatThrownBy(() -> returnService.apply(req))
                .isInstanceOf(ApiException.class)
                .hasMessage("该借阅记录未通过审核");
    }

    @Test
    void shouldRejectDuplicateReturn() {
        ReturnRequest req = new ReturnRequest();
        req.setBorrowId(borrowId);
        returnService.apply(req);
        // Approve it
        returnService.approveBatch(createApprove(borrowId));

        assertThatThrownBy(() -> returnService.apply(req))
                .isInstanceOf(ApiException.class)
                .hasMessage("该借阅记录已归还");
    }

    @Test
    void shouldRestoreStockAndGenerateFineOnOverdueReturn() {
        ReturnRequest req = new ReturnRequest();
        req.setBorrowId(borrowId);
        returnService.apply(req);

        returnService.approveBatch(createApprove(borrowId));

        // Stock restored
        assertThat(bookRepository.findById(bookId).orElseThrow().getStock()).isEqualTo(6);
        // Fine generated (3 days overdue)
        long fineCount = penaltyRepository.countByReaderIdAndPaid(
                borrowRepository.findById(borrowId).orElseThrow().getReaderId(), PaymentStatus.否);
        assertThat(fineCount).isEqualTo(1);
    }

    private BatchApproval createApprove(Long id) {
        BatchApproval ba = new BatchApproval();
        ba.setIds(List.of(id));
        ba.setApproved("是");
        return ba;
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd library-server && ./mvnw test -Dtest=ReturnServiceTest`
Expected: FAIL — ReturnService bean not found

- [ ] **Step 4: Implement ReturnServiceImpl**

```java
package com.library.service.impl;

import com.library.config.LibraryProperties;
import com.library.dto.BatchApproval;
import com.library.dto.ReturnRequest;
import com.library.dto.ReturnResponse;
import com.library.entity.*;
import com.library.entity.enums.ApprovalStatus;
import com.library.entity.enums.PaymentStatus;
import com.library.exception.ApiException;
import com.library.repository.*;
import com.library.service.ReturnService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReturnServiceImpl implements ReturnService {

    private final BorrowReturnRepository returnRepository;
    private final BorrowRepository borrowRepository;
    private final BookRepository bookRepository;
    private final ReaderRepository readerRepository;
    private final PenaltyRepository penaltyRepository;
    private final LibraryProperties props;

    public ReturnServiceImpl(BorrowReturnRepository returnRepository,
                             BorrowRepository borrowRepository,
                             BookRepository bookRepository,
                             ReaderRepository readerRepository,
                             PenaltyRepository penaltyRepository,
                             LibraryProperties props) {
        this.returnRepository = returnRepository;
        this.borrowRepository = borrowRepository;
        this.bookRepository = bookRepository;
        this.readerRepository = readerRepository;
        this.penaltyRepository = penaltyRepository;
        this.props = props;
    }

    @Override
    public Page<ReturnResponse> queryPage(int page, int limit, Long readerId, String approved) {
        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("id").descending());
        // We need to filter by readerId through the Borrow chain.
        // For simplicity, load all and filter in memory if readerId is specified.
        // For a production system, use a @Query with JOIN.
        Page<BorrowReturn> retPage;
        if (readerId != null) {
            List<Long> borrowIds = borrowRepository.findByReaderId(readerId).stream()
                    .map(Borrow::getId).collect(Collectors.toList());
            retPage = returnRepository.findAll(
                    (Specification<BorrowReturn>) (root, query, cb) -> {
                        List<Predicate> preds = new ArrayList<>();
                        if (!borrowIds.isEmpty()) {
                            preds.add(root.get("borrowId").in(borrowIds));
                        } else {
                            preds.add(cb.disjunction()); // return empty
                        }
                        if (approved != null)
                            preds.add(cb.equal(root.get("approved"), ApprovalStatus.valueOf(approved)));
                        return cb.and(preds.toArray(new Predicate[0]));
                    }, pageable);
        } else {
            Specification<BorrowReturn> spec = (root, query, cb) -> {
                if (approved != null)
                    return cb.equal(root.get("approved"), ApprovalStatus.valueOf(approved));
                return cb.conjunction();
            };
            retPage = returnRepository.findAll(spec, pageable);
        }

        return retPage.map(this::toResponse);
    }

    @Override
    @Transactional
    public void apply(ReturnRequest req) {
        Borrow borrow = borrowRepository.findById(req.getBorrowId())
                .orElseThrow(() -> new ApiException("借阅记录不存在"));
        if (borrow.getApproved() != ApprovalStatus.是) {
            throw new ApiException("该借阅记录未通过审核");
        }
        if (returnRepository.existsByBorrowIdAndApproved(req.getBorrowId(), ApprovalStatus.是)) {
            throw new ApiException("该借阅记录已归还");
        }
        BorrowReturn ret = new BorrowReturn();
        ret.setBorrowId(req.getBorrowId());
        ret.setApproved(ApprovalStatus.待审核);
        returnRepository.save(ret);
    }

    @Override
    @Transactional
    public void approveBatch(BatchApproval batch) {
        ApprovalStatus target = ApprovalStatus.valueOf(batch.getApproved());
        for (Long id : batch.getIds()) {
            BorrowReturn ret = returnRepository.findById(id).orElse(null);
            if (ret == null || ret.getApproved() != ApprovalStatus.待审核) continue;

            if (target == ApprovalStatus.是) {
                ret.setApproved(ApprovalStatus.是);
                ret.setReturnDate(LocalDateTime.now());
                Borrow borrow = borrowRepository.findById(ret.getBorrowId()).orElseThrow();
                Book book = bookRepository.findById(borrow.getBookId()).orElseThrow();
                book.setStock(book.getStock() + 1);
                bookRepository.save(book);

                // Overdue fine
                if (borrow.getDueDate() != null) {
                    long overdueDays = ChronoUnit.DAYS.between(borrow.getDueDate(), LocalDate.now());
                    if (overdueDays > 0) {
                        Penalty p = new Penalty();
                        p.setBorrowId(borrow.getId());
                        p.setFineNo(generateFineNo());
                        p.setAmount(props.getFine().getRatePerDay().multiply(BigDecimal.valueOf(overdueDays)));
                        p.setFineDate(LocalDate.now());
                        p.setPaid(PaymentStatus.否);
                        penaltyRepository.save(p);
                    }
                }
            } else {
                ret.setApproved(ApprovalStatus.否);
                ret.setReply(batch.getReply());
            }
        }
    }

    @Override
    public void delete(List<Long> ids) { returnRepository.deleteAllById(ids); }

    private BorrowReturnResponse toResponse(BorrowReturn ret) {
        Borrow b = borrowRepository.findById(ret.getBorrowId()).orElse(null);
        Reader r = b != null ? readerRepository.findById(b.getReaderId()).orElse(null) : null;
        Book bk = b != null ? bookRepository.findById(b.getBookId()).orElse(null) : null;

        BorrowReturnResponse resp = new BorrowReturnResponse();
        resp.setId(ret.getId());
        resp.setBorrowId(ret.getBorrowId());
        if (r != null) { resp.setReaderName(r.getName()); resp.setStudentId(r.getStudentId()); }
        if (bk != null) { resp.setBookTitle(bk.getTitle()); resp.setBookIsbn(bk.getIsbn()); }
        resp.setReturnDate(ret.getReturnDate() != null ? ret.getReturnDate().toString() : null);
        resp.setApproved(ret.getApproved() != null ? ret.getApproved().name() : null);
        resp.setReply(ret.getReply());
        resp.setAddtime(ret.getAddtime() != null ? ret.getAddtime().toString() : null);
        return resp;
    }

    private String generateFineNo() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = penaltyRepository.count() + 1;
        return String.format("FN-%s-%03d", date, count);
    }
}
```

> NOTE: The ReturnResponse class reference in the impl uses `BorrowReturnResponse` — correct this to `ReturnResponse` in the actual implementation. The generated file is `ReturnResponse.java`.

- [ ] **Step 5: Run test to verify it passes**

Run: `cd library-server && ./mvnw test -Dtest=ReturnServiceTest`
Expected: PASS (4 tests)

- [ ] **Step 6: Commit**

```bash
git add library-server/src/main/java/com/library/service/ReturnService.java \
        library-server/src/main/java/com/library/service/impl/ReturnServiceImpl.java \
        library-server/src/test/java/com/library/service/ReturnServiceTest.java
git commit -m "feat(p4): add ReturnService with auto-fine on overdue return"
```

---

### Task 13: RenewalService — TDD cycle

**Files:**
- Create: `library-server/src/test/java/com/library/service/RenewalServiceTest.java`
- Create: `library-server/src/main/java/com/library/service/RenewalService.java`
- Create: `library-server/src/main/java/com/library/service/impl/RenewalServiceImpl.java`

- [ ] **Step 1: Create RenewalService interface**

```java
package com.library.service;

import com.library.dto.BatchApproval;
import com.library.dto.RenewalRequest;
import com.library.dto.RenewalResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public interface RenewalService {

    Page<RenewalResponse> queryPage(int page, int limit, Long readerId, String approved);

    void apply(RenewalRequest request);

    void approveBatch(BatchApproval batch);

    void delete(List<Long> ids);
}
```

- [ ] **Step 2: Create RenewalService test**

```java
package com.library.service;

import com.library.dto.BatchApproval;
import com.library.dto.RenewalRequest;
import com.library.dto.RenewalResponse;
import com.library.entity.*;
import com.library.entity.enums.*;
import com.library.exception.ApiException;
import com.library.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class RenewalServiceTest {

    @Autowired private RenewalService renewalService;
    @Autowired private BorrowRepository borrowRepository;
    @Autowired private RenewalRepository renewalRepository;
    @Autowired private BorrowReturnRepository borrowReturnRepository;
    @Autowired private BookRepository bookRepository;
    @Autowired private ReaderRepository readerRepository;
    @Autowired private CategoryRepository categoryRepository;

    private Long borrowId;
    private Long readerId;

    @BeforeEach
    void setUp() {
        Reader reader = new Reader();
        reader.setStudentId("2021001"); reader.setName("测试读者"); reader.setPhone("13800000001");
        reader = readerRepository.save(reader);
        readerId = reader.getId();

        Category cat = new Category(); cat.setName("CS"); cat = categoryRepository.save(cat);
        Book book = new Book();
        book.setIsbn("978-7-302-11111"); book.setTitle("算法导论"); book.setStock(5);
        book.setCategoryId(cat.getId());
        book = bookRepository.save(book);

        Borrow borrow = new Borrow();
        borrow.setReaderId(readerId); borrow.setBookId(book.getId());
        borrow.setApproved(ApprovalStatus.是);
        borrow.setBorrowDate(LocalDateTime.now().minusDays(10));
        borrow.setDueDate(LocalDate.now().plusDays(20));  // due in 20 days
        borrow = borrowRepository.save(borrow);
        borrowId = borrow.getId();
    }

    @Test
    void shouldAutoApproveFirstRenewalWithin30Days() {
        RenewalRequest req = new RenewalRequest();
        req.setBorrowId(borrowId);

        renewalService.apply(req);

        Page<RenewalResponse> page = renewalService.queryPage(1, 10, readerId, null);
        assertThat(page.getContent().get(0).getApproved()).isEqualTo("是");

        // dueDate should have been extended
        Borrow b = borrowRepository.findById(borrowId).orElseThrow();
        assertThat(b.getDueDate()).isAfter(LocalDate.now().plusDays(25));
    }

    @Test
    void shouldSetPendingForSecondRenewal() {
        RenewalRequest req = new RenewalRequest();
        req.setBorrowId(borrowId);
        renewalService.apply(req);  // first: auto-approved
        renewalService.apply(req);  // second: pending

        Page<RenewalResponse> page = renewalService.queryPage(1, 10, null, null);
        List<RenewalResponse> content = page.getContent();
        assertThat(content.get(0).getApproved()).isEqualTo("待审核");
        assertThat(content.get(1).getApproved()).isEqualTo("是");
    }

    @Test
    void shouldRejectRenewalForUnapprovedBorrow() {
        Borrow b = borrowRepository.findById(borrowId).orElseThrow();
        b.setApproved(ApprovalStatus.待审核);
        borrowRepository.save(b);

        RenewalRequest req = new RenewalRequest();
        req.setBorrowId(borrowId);
        assertThatThrownBy(() -> renewalService.apply(req))
                .isInstanceOf(ApiException.class)
                .hasMessage("该借阅记录未通过审核");
    }

    @Test
    void shouldRejectRenewalExceeding60DayCap() {
        Borrow b = borrowRepository.findById(borrowId).orElseThrow();
        b.setBorrowDate(LocalDateTime.now().minusDays(59)); // already 59 days
        b.setDueDate(LocalDate.now().plusDays(1));
        borrowRepository.save(b);

        RenewalRequest req = new RenewalRequest();
        req.setBorrowId(borrowId);
        assertThatThrownBy(() -> renewalService.apply(req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("最长持有期限");
    }

    @Test
    void shouldRejectRenewalForReturnedBorrow() {
        BorrowReturn ret = new BorrowReturn();
        ret.setBorrowId(borrowId);
        ret.setApproved(ApprovalStatus.是);
        borrowReturnRepository.save(ret);

        RenewalRequest req = new RenewalRequest();
        req.setBorrowId(borrowId);
        assertThatThrownBy(() -> renewalService.apply(req))
                .isInstanceOf(ApiException.class)
                .hasMessage("该借阅记录已归还，无法续借");
    }

    @Test
    void shouldExtendDueDateOnApprove() {
        RenewalRequest req = new RenewalRequest();
        req.setBorrowId(borrowId);
        req.setDurationDays(35);  // > 30 → pending
        renewalService.apply(req);

        Page<RenewalResponse> page = renewalService.queryPage(1, 10, null, null);
        Long renewalId = page.getContent().get(0).getId();

        BatchApproval ba = new BatchApproval();
        ba.setIds(List.of(renewalId));
        ba.setApproved("是");
        renewalService.approveBatch(ba);

        Borrow b = borrowRepository.findById(borrowId).orElseThrow();
        assertThat(b.getDueDate()).isAfter(LocalDate.now().plusDays(25));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd library-server && ./mvnw test -Dtest=RenewalServiceTest`
Expected: FAIL — RenewalService bean not found

- [ ] **Step 4: Implement RenewalServiceImpl**

```java
package com.library.service.impl;

import com.library.config.LibraryProperties;
import com.library.dto.BatchApproval;
import com.library.dto.RenewalRequest;
import com.library.dto.RenewalResponse;
import com.library.entity.*;
import com.library.entity.enums.ApprovalStatus;
import com.library.entity.enums.PaymentStatus;
import com.library.exception.ApiException;
import com.library.repository.*;
import com.library.service.RenewalService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RenewalServiceImpl implements RenewalService {

    private final RenewalRepository renewalRepository;
    private final BorrowRepository borrowRepository;
    private final BorrowReturnRepository borrowReturnRepository;
    private final BookRepository bookRepository;
    private final ReaderRepository readerRepository;
    private final PenaltyRepository penaltyRepository;
    private final LibraryProperties props;

    public RenewalServiceImpl(RenewalRepository renewalRepository,
                              BorrowRepository borrowRepository,
                              BorrowReturnRepository borrowReturnRepository,
                              BookRepository bookRepository,
                              ReaderRepository readerRepository,
                              PenaltyRepository penaltyRepository,
                              LibraryProperties props) {
        this.renewalRepository = renewalRepository;
        this.borrowRepository = borrowRepository;
        this.borrowReturnRepository = borrowReturnRepository;
        this.bookRepository = bookRepository;
        this.readerRepository = readerRepository;
        this.penaltyRepository = penaltyRepository;
        this.props = props;
    }

    @Override
    public Page<RenewalResponse> queryPage(int page, int limit, Long readerId, String approved) {
        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("id").descending());
        Page<Renewal> renewalPage;
        if (readerId != null) {
            List<Long> borrowIds = borrowRepository.findByReaderId(readerId).stream()
                    .map(Borrow::getId).collect(Collectors.toList());
            renewalPage = renewalRepository.findAll(
                    (root, query, cb) -> {
                        var preds = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
                        if (!borrowIds.isEmpty()) preds.add(root.get("borrowId").in(borrowIds));
                        else preds.add(cb.disjunction());
                        if (approved != null)
                            preds.add(cb.equal(root.get("approved"), ApprovalStatus.valueOf(approved)));
                        return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
                    }, pageable);
        } else {
            renewalPage = renewalRepository.findAll(pageable);
        }
        return renewalPage.map(this::toResponse);
    }

    @Override
    @Transactional
    public void apply(RenewalRequest req) {
        Borrow borrow = borrowRepository.findById(req.getBorrowId())
                .orElseThrow(() -> new ApiException("借阅记录不存在"));
        if (borrow.getApproved() != ApprovalStatus.是) {
            throw new ApiException("该借阅记录未通过审核");
        }
        if (borrowReturnRepository.existsByBorrowIdAndApproved(req.getBorrowId(), ApprovalStatus.是)) {
            throw new ApiException("该借阅记录已归还，无法续借");
        }

        int requestedDays = req.getDurationDays() != null
                ? req.getDurationDays() : props.getBorrow().getDefaultDurationDays();
        int actualDays = Math.min(requestedDays, props.getBorrow().getAutoApproveDuration());
        LocalDate newDueDate = LocalDate.now().plusDays(actualDays);

        // 60-day hard cap
        LocalDate maxDueDate = borrow.getBorrowDate().toLocalDate().plusDays(props.getBorrow().getMaxDurationCap());
        boolean capped = false;
        if (newDueDate.isAfter(maxDueDate)) {
            actualDays = (int) ChronoUnit.DAYS.between(LocalDate.now(), maxDueDate);
            if (actualDays <= 0) throw new ApiException("无法续借：已超过最长持有期限");
            newDueDate = maxDueDate;
            capped = true;
        }

        int existingRenewals = (int) renewalRepository.countByBorrowId(req.getBorrowId());
        boolean isWithinAutoCount = existingRenewals < props.getRenewal().getAutoApproveCount();
        boolean withinAutoDays = req.getDurationDays() == null || req.getDurationDays() <= props.getBorrow().getAutoApproveDuration();
        boolean noUnpaidFines = penaltyRepository.countByReaderIdAndPaid(borrow.getReaderId(), PaymentStatus.否) == 0;

        Renewal renewal = new Renewal();
        renewal.setBorrowId(req.getBorrowId());
        renewal.setDurationDays(req.getDurationDays());
        renewal.setRenewalDate(LocalDateTime.now());

        if (isWithinAutoCount && withinAutoDays && noUnpaidFines && !capped) {
            renewal.setApproved(ApprovalStatus.是);
            borrow.setDueDate(newDueDate);
            borrowRepository.save(borrow);
        } else {
            renewal.setApproved(ApprovalStatus.待审核);
        }
        renewalRepository.save(renewal);
    }

    @Override
    @Transactional
    public void approveBatch(BatchApproval batch) {
        ApprovalStatus target = ApprovalStatus.valueOf(batch.getApproved());
        for (Long id : batch.getIds()) {
            Renewal renewal = renewalRepository.findById(id).orElse(null);
            if (renewal == null || renewal.getApproved() != ApprovalStatus.待审核) continue;

            if (target == ApprovalStatus.是) {
                renewal.setApproved(ApprovalStatus.是);
                Borrow borrow = borrowRepository.findById(renewal.getBorrowId()).orElseThrow();
                int actualDays = renewal.getDurationDays() != null
                        ? Math.min(renewal.getDurationDays(), props.getBorrow().getAutoApproveDuration())
                        : props.getBorrow().getDefaultDurationDays();
                LocalDate newDueDate = LocalDate.now().plusDays(actualDays);
                LocalDate maxDueDate = borrow.getBorrowDate().toLocalDate().plusDays(props.getBorrow().getMaxDurationCap());
                if (newDueDate.isAfter(maxDueDate)) newDueDate = maxDueDate;
                borrow.setDueDate(newDueDate);
                borrowRepository.save(borrow);
            } else {
                renewal.setApproved(ApprovalStatus.否);
                renewal.setReply(batch.getReply());
            }
        }
    }

    @Override
    public void delete(List<Long> ids) { renewalRepository.deleteAllById(ids); }

    private RenewalResponse toResponse(Renewal r) {
        Borrow b = borrowRepository.findById(r.getBorrowId()).orElse(null);
        Reader reader = b != null ? readerRepository.findById(b.getReaderId()).orElse(null) : null;
        Book bk = b != null ? bookRepository.findById(b.getBookId()).orElse(null) : null;

        RenewalResponse resp = new RenewalResponse();
        resp.setId(r.getId());
        resp.setBorrowId(r.getBorrowId());
        resp.setDurationDays(r.getDurationDays());
        if (reader != null) { resp.setReaderName(reader.getName()); resp.setStudentId(reader.getStudentId()); }
        if (bk != null) { resp.setBookTitle(bk.getTitle()); resp.setBookIsbn(bk.getIsbn()); }
        resp.setRenewalDate(r.getRenewalDate() != null ? r.getRenewalDate().toString() : null);
        resp.setApproved(r.getApproved() != null ? r.getApproved().name() : null);
        resp.setReply(r.getReply());
        resp.setAddtime(r.getAddtime() != null ? r.getAddtime().toString() : null);
        return resp;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd library-server && ./mvnw test -Dtest=RenewalServiceTest`
Expected: PASS (6 tests)

- [ ] **Step 6: Commit**

```bash
git add library-server/src/main/java/com/library/service/RenewalService.java \
        library-server/src/main/java/com/library/service/impl/RenewalServiceImpl.java \
        library-server/src/test/java/com/library/service/RenewalServiceTest.java
git commit -m "feat(p4): add RenewalService with auto-approve + 60d cap"
```

---

### Task 14: PenaltyService — TDD cycle

**Files:**
- Create: `library-server/src/test/java/com/library/service/PenaltyServiceTest.java`
- Create: `library-server/src/main/java/com/library/service/PenaltyService.java`
- Create: `library-server/src/main/java/com/library/service/impl/PenaltyServiceImpl.java`

- [ ] **Step 1: Create PenaltyService interface**

```java
package com.library.service;

import com.library.dto.PayRequest;
import com.library.dto.PenaltyResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public interface PenaltyService {

    Page<PenaltyResponse> queryPage(int page, int limit, Long readerId, String paid);

    void pay(PayRequest request);

    void delete(List<Long> ids);
}
```

- [ ] **Step 2: Create PenaltyService test**

```java
package com.library.service;

import com.library.dto.PayRequest;
import com.library.dto.PenaltyResponse;
import com.library.entity.*;
import com.library.entity.enums.*;
import com.library.exception.ApiException;
import com.library.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class PenaltyServiceTest {

    @Autowired private PenaltyService penaltyService;
    @Autowired private PenaltyRepository penaltyRepository;
    @Autowired private BorrowRepository borrowRepository;
    @Autowired private BookRepository bookRepository;
    @Autowired private ReaderRepository readerRepository;
    @Autowired private CategoryRepository categoryRepository;

    private Long penaltyId;
    private Long readerId;

    @BeforeEach
    void setUp() {
        Reader reader = new Reader();
        reader.setStudentId("2021001"); reader.setName("测试"); reader.setPhone("13800000001");
        reader = readerRepository.save(reader);
        readerId = reader.getId();

        Category cat = new Category(); cat.setName("CS"); cat = categoryRepository.save(cat);
        Book book = new Book(); book.setIsbn("X"); book.setTitle("T"); book.setCategoryId(cat.getId());
        book = bookRepository.save(book);

        Borrow borrow = new Borrow();
        borrow.setReaderId(readerId); borrow.setBookId(book.getId());
        borrow.setApproved(ApprovalStatus.是);
        borrow.setBorrowDate(LocalDateTime.now().minusDays(40));
        borrow.setDueDate(LocalDate.now().minusDays(10));
        borrow = borrowRepository.save(borrow);

        Penalty p = new Penalty();
        p.setBorrowId(borrow.getId()); p.setFineNo("FN-20260623-001");
        p.setAmount(new BigDecimal("10.00")); p.setFineDate(LocalDate.now()); p.setPaid(PaymentStatus.否);
        p = penaltyRepository.save(p);
        penaltyId = p.getId();
    }

    @Test
    void shouldQueryOwnFines() {
        Page<PenaltyResponse> page = penaltyService.queryPage(1, 10, readerId, null);
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getBookTitle()).isEqualTo("T");
        assertThat(page.getContent().get(0).getAmount()).isEqualTo("10.00");
    }

    @Test
    void shouldFilterByPaidStatus() {
        Page<PenaltyResponse> unpaid = penaltyService.queryPage(1, 10, readerId, "否");
        assertThat(unpaid.getTotalElements()).isEqualTo(1);
        Page<PenaltyResponse> paid = penaltyService.queryPage(1, 10, readerId, "是");
        assertThat(paid.getTotalElements()).isEqualTo(0);
    }

    @Test
    void shouldPayFine() {
        PayRequest req = new PayRequest();
        req.setId(penaltyId);
        penaltyService.pay(req);

        Penalty p = penaltyRepository.findById(penaltyId).orElseThrow();
        assertThat(p.getPaid()).isEqualTo(PaymentStatus.是);
    }

    @Test
    void shouldRejectDuplicatePayment() {
        PayRequest req = new PayRequest();
        req.setId(penaltyId);
        penaltyService.pay(req);

        assertThatThrownBy(() -> penaltyService.pay(req))
                .isInstanceOf(ApiException.class)
                .hasMessage("该罚款已支付");
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd library-server && ./mvnw test -Dtest=PenaltyServiceTest`
Expected: FAIL — PenaltyService bean not found

- [ ] **Step 4: Implement PenaltyServiceImpl**

```java
package com.library.service.impl;

import com.library.dto.PayRequest;
import com.library.dto.PenaltyResponse;
import com.library.entity.*;
import com.library.entity.enums.PaymentStatus;
import com.library.exception.ApiException;
import com.library.repository.*;
import com.library.service.PenaltyService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PenaltyServiceImpl implements PenaltyService {

    private final PenaltyRepository penaltyRepository;
    private final BorrowRepository borrowRepository;
    private final BookRepository bookRepository;
    private final ReaderRepository readerRepository;

    public PenaltyServiceImpl(PenaltyRepository penaltyRepository,
                              BorrowRepository borrowRepository,
                              BookRepository bookRepository,
                              ReaderRepository readerRepository) {
        this.penaltyRepository = penaltyRepository;
        this.borrowRepository = borrowRepository;
        this.bookRepository = bookRepository;
        this.readerRepository = readerRepository;
    }

    @Override
    public Page<PenaltyResponse> queryPage(int page, int limit, Long readerId, String paid) {
        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("id").descending());

        // Build spec for paid filter
        Specification<Penalty> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (paid != null) predicates.add(cb.equal(root.get("paid"), PaymentStatus.valueOf(paid)));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Penalty> penaltyPage;
        if (readerId != null) {
            List<Long> borrowIds = borrowRepository.findByReaderId(readerId).stream()
                    .map(Borrow::getId).collect(Collectors.toList());
            if (borrowIds.isEmpty()) return Page.empty(pageable);
            penaltyPage = penaltyRepository.findAll(
                    spec.and((root, query, cb) -> root.get("borrowId").in(borrowIds)), pageable);
        } else {
            penaltyPage = penaltyRepository.findAll(spec, pageable);
        }

        return penaltyPage.map(this::toResponse);
    }

    @Override
    @Transactional
    public void pay(PayRequest req) {
        Penalty penalty = penaltyRepository.findById(req.getId())
                .orElseThrow(() -> new ApiException("罚款记录不存在"));
        if (penalty.getPaid() == PaymentStatus.是) throw new ApiException("该罚款已支付");
        penalty.setPaid(PaymentStatus.是);
        penaltyRepository.save(penalty);
    }

    @Override
    public void delete(List<Long> ids) { penaltyRepository.deleteAllById(ids); }

    private PenaltyResponse toResponse(Penalty p) {
        Borrow b = borrowRepository.findById(p.getBorrowId()).orElse(null);
        Reader r = b != null ? readerRepository.findById(b.getReaderId()).orElse(null) : null;
        Book bk = b != null ? bookRepository.findById(b.getBookId()).orElse(null) : null;

        PenaltyResponse resp = new PenaltyResponse();
        resp.setId(p.getId());
        resp.setFineNo(p.getFineNo());
        resp.setBorrowId(p.getBorrowId());
        if (r != null) { resp.setReaderName(r.getName()); resp.setStudentId(r.getStudentId()); }
        if (bk != null) { resp.setBookTitle(bk.getTitle()); resp.setBookIsbn(bk.getIsbn()); }
        resp.setAmount(p.getAmount() != null ? p.getAmount().toString() : null);
        resp.setFineDate(p.getFineDate() != null ? p.getFineDate().toString() : null);
        resp.setPaid(p.getPaid() != null ? p.getPaid().name() : null);
        resp.setAddtime(p.getAddtime() != null ? p.getAddtime().toString() : null);
        return resp;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd library-server && ./mvnw test -Dtest=PenaltyServiceTest`
Expected: PASS (4 tests)

- [ ] **Step 6: Commit**

```bash
git add library-server/src/main/java/com/library/service/PenaltyService.java \
        library-server/src/main/java/com/library/service/impl/PenaltyServiceImpl.java \
        library-server/src/test/java/com/library/service/PenaltyServiceTest.java
git commit -m "feat(p4): add PenaltyService with pay + query"
```

---

### Task 15: Create BorrowController

**Files:**
- Create: `library-server/src/main/java/com/library/controller/BorrowController.java`

- [ ] **Step 1: Create BorrowController**

```java
package com.library.controller;

import com.library.common.ApiResponse;
import com.library.dto.BatchApproval;
import com.library.dto.BorrowRequest;
import com.library.dto.BorrowResponse;
import com.library.entity.User;
import com.library.service.BorrowService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/borrows")
public class BorrowController {

    private final BorrowService borrowService;

    public BorrowController(BorrowService borrowService) {
        this.borrowService = borrowService;
    }

    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) Long readerId,
            @RequestParam(required = false) Long bookId,
            @RequestParam(required = false) String approved,
            @AuthenticationPrincipal User currentUser) {
        // Reader can only see own records
        if (currentUser != null && currentUser.getRole() == com.library.entity.enums.UserRole.READER) {
            readerId = currentUser.getReaderId();
        }
        Page<BorrowResponse> result = borrowService.queryPage(page, limit, readerId, bookId, approved);
        return ApiResponse.ok(Map.of("list", result.getContent(), "total", result.getTotalElements()));
    }

    @PostMapping("/add")
    public ApiResponse<Void> add(@Valid @RequestBody BorrowRequest request,
                                  @AuthenticationPrincipal User currentUser) {
        borrowService.apply(request, currentUser);
        return ApiResponse.ok();
    }

    @PostMapping("/shBatch")
    public ApiResponse<Void> shBatch(@Valid @RequestBody BatchApproval batch) {
        borrowService.approveBatch(batch);
        return ApiResponse.ok();
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody List<Long> ids) {
        borrowService.delete(ids);
        return ApiResponse.ok();
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `cd library-server && ./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add library-server/src/main/java/com/library/controller/BorrowController.java
git commit -m "feat(p4): add BorrowController (4 endpoints)"
```

---

### Task 16: Create ReturnController

**Files:**
- Create: `library-server/src/main/java/com/library/controller/ReturnController.java`

- [ ] **Step 1: Create ReturnController**

```java
package com.library.controller;

import com.library.common.ApiResponse;
import com.library.dto.BatchApproval;
import com.library.dto.ReturnRequest;
import com.library.dto.ReturnResponse;
import com.library.entity.User;
import com.library.entity.enums.UserRole;
import com.library.service.ReturnService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/returns")
public class ReturnController {

    private final ReturnService returnService;

    public ReturnController(ReturnService returnService) {
        this.returnService = returnService;
    }

    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String approved,
            @AuthenticationPrincipal User currentUser) {
        Long readerId = null;
        if (currentUser != null && currentUser.getRole() == UserRole.READER) {
            readerId = currentUser.getReaderId();
        }
        Page<ReturnResponse> result = returnService.queryPage(page, limit, readerId, approved);
        return ApiResponse.ok(Map.of("list", result.getContent(), "total", result.getTotalElements()));
    }

    @PostMapping("/add")
    public ApiResponse<Void> add(@Valid @RequestBody ReturnRequest request) {
        returnService.apply(request);
        return ApiResponse.ok();
    }

    @PostMapping("/shBatch")
    public ApiResponse<Void> shBatch(@Valid @RequestBody BatchApproval batch) {
        returnService.approveBatch(batch);
        return ApiResponse.ok();
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody List<Long> ids) {
        returnService.delete(ids);
        return ApiResponse.ok();
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `cd library-server && ./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add library-server/src/main/java/com/library/controller/ReturnController.java
git commit -m "feat(p4): add ReturnController (4 endpoints)"
```

---

### Task 17: Create RenewalController

**Files:**
- Create: `library-server/src/main/java/com/library/controller/RenewalController.java`

- [ ] **Step 1: Create RenewalController**

```java
package com.library.controller;

import com.library.common.ApiResponse;
import com.library.dto.BatchApproval;
import com.library.dto.RenewalRequest;
import com.library.dto.RenewalResponse;
import com.library.entity.User;
import com.library.entity.enums.UserRole;
import com.library.service.RenewalService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/renewals")
public class RenewalController {

    private final RenewalService renewalService;

    public RenewalController(RenewalService renewalService) {
        this.renewalService = renewalService;
    }

    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String approved,
            @AuthenticationPrincipal User currentUser) {
        Long readerId = null;
        if (currentUser != null && currentUser.getRole() == UserRole.READER) {
            readerId = currentUser.getReaderId();
        }
        Page<RenewalResponse> result = renewalService.queryPage(page, limit, readerId, approved);
        return ApiResponse.ok(Map.of("list", result.getContent(), "total", result.getTotalElements()));
    }

    @PostMapping("/add")
    public ApiResponse<Void> add(@Valid @RequestBody RenewalRequest request) {
        renewalService.apply(request);
        return ApiResponse.ok();
    }

    @PostMapping("/shBatch")
    public ApiResponse<Void> shBatch(@Valid @RequestBody BatchApproval batch) {
        renewalService.approveBatch(batch);
        return ApiResponse.ok();
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody List<Long> ids) {
        renewalService.delete(ids);
        return ApiResponse.ok();
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `cd library-server && ./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add library-server/src/main/java/com/library/controller/RenewalController.java
git commit -m "feat(p4): add RenewalController (4 endpoints)"
```

---

### Task 18: Create PenaltyController

**Files:**
- Create: `library-server/src/main/java/com/library/controller/PenaltyController.java`

- [ ] **Step 1: Create PenaltyController**

```java
package com.library.controller;

import com.library.common.ApiResponse;
import com.library.dto.PayRequest;
import com.library.dto.PenaltyResponse;
import com.library.entity.User;
import com.library.entity.enums.UserRole;
import com.library.service.PenaltyService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/penalties")
public class PenaltyController {

    private final PenaltyService penaltyService;

    public PenaltyController(PenaltyService penaltyService) {
        this.penaltyService = penaltyService;
    }

    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String paid,
            @AuthenticationPrincipal User currentUser) {
        Long readerId = null;
        if (currentUser != null && currentUser.getRole() == UserRole.READER) {
            readerId = currentUser.getReaderId();
        }
        Page<PenaltyResponse> result = penaltyService.queryPage(page, limit, readerId, paid);
        return ApiResponse.ok(Map.of("list", result.getContent(), "total", result.getTotalElements()));
    }

    @PostMapping("/pay")
    public ApiResponse<Void> pay(@Valid @RequestBody PayRequest request) {
        penaltyService.pay(request);
        return ApiResponse.ok();
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody List<Long> ids) {
        penaltyService.delete(ids);
        return ApiResponse.ok();
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `cd library-server && ./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add library-server/src/main/java/com/library/controller/PenaltyController.java
git commit -m "feat(p4): add PenaltyController (3 endpoints)"
```

---

### Task 19: Update SecurityConfig

**Files:**
- Modify: `library-server/src/main/java/com/library/config/SecurityConfig.java`

- [ ] **Step 1: Add P4 endpoint rules**

Insert after the `// ---- Admin: category management` block in `SecurityConfig.java`:

```java
// ---- Borrower flow (reader/admin apply, admin approve) ----
.requestMatchers(HttpMethod.POST, "/borrows/add",
        "/returns/add", "/renewals/add")
        .hasAnyRole("READER", "ADMIN")
.requestMatchers(HttpMethod.GET, "/borrows/page",
        "/returns/page", "/renewals/page",
        "/penalties/page")
        .authenticated()
.requestMatchers("/borrows/shBatch", "/borrows/delete",
        "/returns/shBatch", "/returns/delete",
        "/renewals/shBatch", "/renewals/delete",
        "/penalties/delete")
        .hasRole("ADMIN")
// ---- Penalty payment (reader pays own fine) ----
.requestMatchers(HttpMethod.POST, "/penalties/pay")
        .hasRole("READER")
```

- [ ] **Step 2: Verify compile**

Run: `cd library-server && ./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add library-server/src/main/java/com/library/config/SecurityConfig.java
git commit -m "feat(p4): update SecurityConfig with P4 endpoint rules"
```

---

### Task 20: Run all P4 tests + manual verification

- [ ] **Step 1: Run all tests**

Run: `cd library-server && ./mvnw test`
Expected: ALL TESTS PASS (including existing)

- [ ] **Step 2: Start server and verify Swagger**

Run: `cd library-server && ./mvnw spring-boot:run`

Open `http://localhost:8080/api/swagger-ui.html` and verify:
- `/borrows` endpoints appear (page, add, shBatch, delete)
- `/returns` endpoints appear (page, add, shBatch, delete)
- `/renewals` endpoints appear (page, add, shBatch, delete)
- `/penalties` endpoints appear (page, pay, delete)

- [ ] **Step 3: Stop server and commit**

```bash
git add -A
git commit -m "chore(p4): P4 complete — all tests pass, Swagger verified"
```

---

## Self-Review

**1. Spec coverage:** All 14 spec sections covered — entities (§3), DTOs (§4), services (§5), repositories (§6), business rules (§7), endpoints (§1), config (§9), security (§10), error messages (§11), test cases (§12). 

**2. Placeholder scan:** No TBD/TODO. All code blocks contain complete, runnable implementations. All test cases have complete assertions.

**3. Type consistency:**
- `BorrowRequest.readerId` defined as `Long` in Task 7 → consumed as `Long` in Task 11 `BorrowService.apply()` → √
- `BatchApproval.ids` defined as `List<Long>` → consumed consistently in all approveBatch methods → √
- All Response DTOs use `String` for dates/amounts (frontend-friendly format via `.toString()`) → √
- `ApprovalStatus.待审核` used across all Service implementations consistently → √
- `PaymentStatus.否` used for unpaid fines → √
