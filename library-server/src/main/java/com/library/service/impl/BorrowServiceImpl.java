package com.library.service.impl;

import com.library.config.LibraryProperties;
import com.library.dto.BatchApproval;
import com.library.dto.BorrowRequest;
import com.library.dto.BorrowResponse;
import com.library.entity.*;
import com.library.entity.enums.ApprovalStatus;
import com.library.entity.enums.PaymentStatus;
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
    private final BorrowReturnRepository borrowReturnRepository;
    private final BookRepository bookRepository;
    private final ReaderRepository readerRepository;
    private final PenaltyRepository penaltyRepository;
    private final LibraryProperties props;

    public BorrowServiceImpl(BorrowRepository borrowRepository,
                             BorrowReturnRepository borrowReturnRepository,
                             BookRepository bookRepository,
                             ReaderRepository readerRepository,
                             PenaltyRepository penaltyRepository,
                             LibraryProperties props) {
        this.borrowRepository = borrowRepository;
        this.borrowReturnRepository = borrowReturnRepository;
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

        Book book = bookRepository.findById(req.getBookId())
                .orElseThrow(() -> new ApiException("图书不存在"));
        if (book.getStock() <= 0) throw new ApiException("图书库存不足");
        if (hasActiveBorrowForBook(readerId, req.getBookId())) {
            throw new ApiException("该图书您已有进行中的借阅记录");
        }

        int actualDays = req.getDurationDays() != null
                ? Math.min(req.getDurationDays(), props.getBorrow().getAutoApproveDuration())
                : props.getBorrow().getDefaultDurationDays();

        boolean hasUnpaidFines = penaltyRepository.countByReaderIdAndPaid(readerId, PaymentStatus.否) > 0;
        boolean atLimit = countActiveBorrows(readerId) >= props.getBorrow().getMaxLimit();
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
    public void approveBatch(BatchApproval batch) {
        ApprovalStatus targetStatus = ApprovalStatus.valueOf(batch.getApproved());
        for (Long id : batch.getIds()) {
            Borrow borrow = borrowRepository.findById(id).orElse(null);
            if (borrow == null || borrow.getApproved() != ApprovalStatus.待审核) continue;

            if (targetStatus == ApprovalStatus.是) {
                Book book = bookRepository.findById(borrow.getBookId()).orElseThrow();
                if (book.getStock() <= 0) throw new ApiException("图书库存不足");
                borrow.setApproved(ApprovalStatus.是);
                borrow.setBorrowDate(LocalDateTime.now());
                int actualDays = borrow.getDurationDays() != null
                        ? Math.min(borrow.getDurationDays(), props.getBorrow().getAutoApproveDuration())
                        : props.getBorrow().getDefaultDurationDays();
                borrow.setDueDate(LocalDate.now().plusDays(actualDays));
                book.setStock(book.getStock() - 1);
                bookRepository.save(book);
                borrowRepository.save(borrow);
            } else {
                borrow.setApproved(ApprovalStatus.否);
                borrow.setReply(batch.getReply());
                borrowRepository.save(borrow);
            }
        }
    }

    @Override
    @Transactional
    public void delete(List<Long> ids) {
        borrowRepository.deleteAllById(ids);
    }

    private boolean hasActiveBorrowForBook(Long readerId, Long bookId) {
        return borrowRepository.findByReaderId(readerId).stream()
                .filter(b -> b.getApproved() == ApprovalStatus.是)
                .filter(b -> b.getBookId().equals(bookId))
                .anyMatch(this::isNotReturned);
    }

    private long countActiveBorrows(Long readerId) {
        return borrowRepository.findByReaderId(readerId).stream()
                .filter(b -> b.getApproved() == ApprovalStatus.是)
                .filter(this::isNotReturned)
                .count();
    }

    private boolean isNotReturned(Borrow borrow) {
        return !borrowReturnRepository.existsByBorrowIdAndApproved(borrow.getId(), ApprovalStatus.是);
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
