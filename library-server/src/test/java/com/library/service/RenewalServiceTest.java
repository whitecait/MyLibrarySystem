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

        renewalService.apply(req, null);

        Page<RenewalResponse> page = renewalService.queryPage(1, 10, readerId, null);
        assertThat(page.getContent().get(0).getApproved()).isEqualTo("是");
        Borrow b = borrowRepository.findById(borrowId).orElseThrow();
        assertThat(b.getDueDate()).isAfter(LocalDate.now().plusDays(25));
    }

    @Test
    void shouldSetPendingForSecondRenewal() {
        RenewalRequest req = new RenewalRequest();
        req.setBorrowId(borrowId);
        renewalService.apply(req, null);  // first: auto-approved
        renewalService.apply(req, null);  // second: pending

        Page<RenewalResponse> page = renewalService.queryPage(1, 10, null, null);
        List<RenewalResponse> content = page.getContent();
        assertThat(content.get(0).getApproved()).isEqualTo("待审核");
        assertThat(content.get(1).getApproved()).isEqualTo("是");
    }

    @Test
    void shouldFilterRenewalsByApprovedForAdminQuery() {
        RenewalRequest req = new RenewalRequest();
        req.setBorrowId(borrowId);
        renewalService.apply(req, null);  // first: auto-approved
        renewalService.apply(req, null);  // second: pending

        Page<RenewalResponse> pending = renewalService.queryPage(1, 10, null, "待审核");

        assertThat(pending.getTotalElements()).isEqualTo(1);
        assertThat(pending.getContent().get(0).getApproved()).isEqualTo("待审核");
    }

    @Test
    void shouldRejectRenewalForUnapprovedBorrow() {
        Borrow b = borrowRepository.findById(borrowId).orElseThrow();
        b.setApproved(ApprovalStatus.待审核);
        borrowRepository.save(b);

        RenewalRequest req = new RenewalRequest();
        req.setBorrowId(borrowId);
        assertThatThrownBy(() -> renewalService.apply(req, null))
                .isInstanceOf(ApiException.class)
                .hasMessage("该借阅记录未通过审核");
    }

    @Test
    void shouldRejectRenewalExceeding60DayCap() {
        Borrow b = borrowRepository.findById(borrowId).orElseThrow();
        b.setBorrowDate(LocalDateTime.now().minusDays(60));
        b.setDueDate(LocalDate.now());
        borrowRepository.save(b);

        RenewalRequest req = new RenewalRequest();
        req.setBorrowId(borrowId);
        assertThatThrownBy(() -> renewalService.apply(req, null))
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
        assertThatThrownBy(() -> renewalService.apply(req, null))
                .isInstanceOf(ApiException.class)
                .hasMessage("该借阅记录已归还，无法续借");
    }

    @Test
    void shouldExtendDueDateOnApprove() {
        RenewalRequest req = new RenewalRequest();
        req.setBorrowId(borrowId);
        req.setDurationDays(35);  // > 30 -> pending
        renewalService.apply(req, null);

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
