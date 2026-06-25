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
        bookId = book.getId();

        Borrow borrow = new Borrow();
        borrow.setReaderId(readerId); borrow.setBookId(bookId);
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
        // Approve the first return
        Page<ReturnResponse> page = returnService.queryPage(1, 10, null, null);
        Long retId = page.getContent().get(0).getId();
        BatchApproval ba = new BatchApproval();
        ba.setIds(List.of(retId));
        ba.setApproved("是");
        returnService.approveBatch(ba);

        // Try to return again
        assertThatThrownBy(() -> returnService.apply(req))
                .isInstanceOf(ApiException.class)
                .hasMessage("该借阅记录已归还");
    }

    @Test
    void shouldRestoreStockAndGenerateFineOnOverdueReturn() {
        ReturnRequest req = new ReturnRequest();
        req.setBorrowId(borrowId);
        returnService.apply(req);

        Page<ReturnResponse> page = returnService.queryPage(1, 10, null, null);
        Long retId = page.getContent().get(0).getId();
        BatchApproval ba = new BatchApproval();
        ba.setIds(List.of(retId));
        ba.setApproved("是");
        returnService.approveBatch(ba);

        // Stock restored
        assertThat(bookRepository.findById(bookId).orElseThrow().getStock()).isEqualTo(6);
        // Fine generated (3 days overdue)
        long fineCount = penaltyRepository.countByReaderIdAndPaid(readerId, PaymentStatus.否);
        assertThat(fineCount).isEqualTo(1);
    }
}
