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
        Page<BorrowReturn> retPage;
        if (readerId != null) {
            List<Long> borrowIds = borrowRepository.findByReaderId(readerId).stream()
                    .map(Borrow::getId).collect(Collectors.toList());
            if (borrowIds.isEmpty()) return Page.empty(pageable);
            retPage = returnRepository.findAll(
                    (Specification<BorrowReturn>) (root, query, cb) -> {
                        List<Predicate> preds = new ArrayList<>();
                        preds.add(root.get("borrowId").in(borrowIds));
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

        // Batch JOIN: avoid N+1 in toResponse
        List<Long> borrowIds = retPage.map(BorrowReturn::getBorrowId).stream()
                .distinct().collect(Collectors.toList());
        Map<Long, Borrow> borrowMap = borrowRepository.findAllById(borrowIds).stream()
                .collect(Collectors.toMap(Borrow::getId, b -> b));
        List<Long> readerIds = borrowMap.values().stream()
                .map(Borrow::getReaderId).distinct().collect(Collectors.toList());
        List<Long> bookIds = borrowMap.values().stream()
                .map(Borrow::getBookId).distinct().collect(Collectors.toList());
        Map<Long, Reader> readerMap = readerRepository.findAllById(readerIds).stream()
                .collect(Collectors.toMap(Reader::getId, r -> r));
        Map<Long, Book> bookMap = bookRepository.findAllById(bookIds).stream()
                .collect(Collectors.toMap(Book::getId, b -> b));

        return retPage.map(r -> toResponse(r, borrowMap, readerMap, bookMap));
    }

    @Override
    @Transactional
    public void apply(ReturnRequest req, User currentUser) {
        Borrow borrow = borrowRepository.findById(req.getBorrowId())
                .orElseThrow(() -> new ApiException("借阅记录不存在"));
        if (borrow.getApproved() != ApprovalStatus.是) {
            throw new ApiException("该借阅记录未通过审核");
        }
        // Reader can only return own borrows; admin can return any
        if (currentUser != null && currentUser.getRole() == com.library.entity.enums.UserRole.READER) {
            if (!borrow.getReaderId().equals(currentUser.getReaderId())) {
                throw new ApiException("无权操作他人的借阅记录");
            }
        }
        if (returnRepository.existsByBorrowIdAndApproved(req.getBorrowId(), ApprovalStatus.是)) {
            throw new ApiException("该借阅记录已归还");
        }
        if (returnRepository.existsByBorrowIdAndApproved(req.getBorrowId(), ApprovalStatus.待审核)) {
            throw new ApiException("该借阅记录已有归还申请");
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
                if (returnRepository.existsByBorrowIdAndApproved(ret.getBorrowId(), ApprovalStatus.是)) {
                    ret.setApproved(ApprovalStatus.否);
                    ret.setReply("该借阅记录已归还");
                    continue;
                }
                ret.setApproved(ApprovalStatus.是);
                ret.setReturnDate(LocalDateTime.now());
                Borrow borrow = borrowRepository.findById(ret.getBorrowId()).orElseThrow();
                Book book = bookRepository.findById(borrow.getBookId()).orElseThrow();
                book.setStock(book.getStock() + 1);
                bookRepository.save(book);

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
    @Transactional
    public void delete(List<Long> ids) { returnRepository.deleteAllById(ids); }

    private ReturnResponse toResponse(BorrowReturn ret, Map<Long, Borrow> borrowMap,
                                       Map<Long, Reader> readerMap, Map<Long, Book> bookMap) {
        Borrow b = borrowMap.get(ret.getBorrowId());
        Reader r = b != null ? readerMap.get(b.getReaderId()) : null;
        Book bk = b != null ? bookMap.get(b.getBookId()) : null;

        ReturnResponse resp = new ReturnResponse();
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
        long count = penaltyRepository.countByFineDate(LocalDate.now()) + 1;
        return String.format("FN-%s-%03d", date, count);
    }
}
