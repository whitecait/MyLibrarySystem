package com.library.service;

import com.library.dto.BorrowRequest;
import com.library.dto.BorrowResponse;
import com.library.entity.*;
import com.library.entity.enums.ApprovalStatus;
import com.library.entity.enums.Gender;
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
    @Autowired private BorrowReturnRepository borrowReturnRepository;
    @Autowired private TestAuthHelper authHelper;

    private Reader reader;
    private Book book;
    private User readerUser;

    @BeforeEach
    void setUp() {
        reader = new Reader();
        reader.setStudentId("2021001");
        reader.setName("测试读者");
        reader.setGender(Gender.男);
        reader.setDepartment("计算机系");
        reader.setPhone("13800000001");
        reader = readerRepository.save(reader);

        readerUser = new User();
        readerUser.setUsername("2021001");
        readerUser.setPassword("ignored");
        readerUser.setRole(UserRole.READER);
        readerUser.setReaderId(reader.getId());
        readerUser = userRepository.save(readerUser);

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

        Book updated = bookRepository.findById(book.getId()).orElseThrow();
        assertThat(updated.getStock()).isEqualTo(4);
    }

    @Test
    void shouldSetPendingWhenDurationExceedsAutoApprove() {
        BorrowRequest req = new BorrowRequest();
        req.setBookId(book.getId());
        req.setReaderId(reader.getId());
        req.setDurationDays(31);

        borrowService.apply(req, readerUser);

        Page<BorrowResponse> page = borrowService.queryPage(1, 10, reader.getId(), null, null);
        assertThat(page.getContent().get(0).getApproved()).isEqualTo("待审核");
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

    @Test
    void shouldAllowBorrowSameBookAfterApprovedReturn() {
        BorrowRequest req = new BorrowRequest();
        req.setBookId(book.getId());
        req.setReaderId(reader.getId());
        borrowService.apply(req, readerUser);

        Long firstBorrowId = borrowService.queryPage(1, 10, reader.getId(), null, null)
                .getContent().get(0).getId();
        BorrowReturn ret = new BorrowReturn();
        ret.setBorrowId(firstBorrowId);
        ret.setApproved(ApprovalStatus.是);
        borrowReturnRepository.save(ret);

        Book returnedBook = bookRepository.findById(book.getId()).orElseThrow();
        returnedBook.setStock(returnedBook.getStock() + 1);
        bookRepository.save(returnedBook);

        borrowService.apply(req, readerUser);

        Page<BorrowResponse> page = borrowService.queryPage(1, 10, reader.getId(), book.getId(), "是");
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(bookRepository.findById(book.getId()).orElseThrow().getStock()).isEqualTo(4);
    }
}
