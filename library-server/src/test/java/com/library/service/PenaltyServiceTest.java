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
        penaltyService.pay(req, null);

        Penalty p = penaltyRepository.findById(penaltyId).orElseThrow();
        assertThat(p.getPaid()).isEqualTo(PaymentStatus.是);
    }

    @Test
    void shouldRejectDuplicatePayment() {
        PayRequest req = new PayRequest();
        req.setId(penaltyId);
        penaltyService.pay(req, null);

        assertThatThrownBy(() -> penaltyService.pay(req, null))
                .isInstanceOf(ApiException.class)
                .hasMessage("该罚款已支付");
    }
}
