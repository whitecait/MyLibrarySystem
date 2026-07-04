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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
        List<Long> readerBorrowIds = null;
        if (readerId != null) {
            readerBorrowIds = borrowRepository.findByReaderId(readerId).stream()
                    .map(Borrow::getId).collect(Collectors.toList());
            if (readerBorrowIds.isEmpty()) return Page.empty(pageable);
        }

        List<Long> finalReaderBorrowIds = readerBorrowIds;
        Specification<Renewal> spec = (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (finalReaderBorrowIds != null) {
                preds.add(root.get("borrowId").in(finalReaderBorrowIds));
            }
            if (approved != null) {
                preds.add(cb.equal(root.get("approved"), ApprovalStatus.valueOf(approved)));
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
        Page<Renewal> renewalPage = renewalRepository.findAll(spec, pageable);

        // Batch JOIN: avoid N+1 in toResponse
        List<Long> borrowIds = renewalPage.map(Renewal::getBorrowId).stream()
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

        return renewalPage.map(r -> toResponse(r, borrowMap, readerMap, bookMap));
    }

    @Override
    @Transactional
    public void apply(RenewalRequest req, User currentUser) {
        Borrow borrow = borrowRepository.findById(req.getBorrowId())
                .orElseThrow(() -> new ApiException("借阅记录不存在"));
        if (borrow.getApproved() != ApprovalStatus.是) {
            throw new ApiException("该借阅记录未通过审核");
        }
        // Reader can only renew own borrows; admin can renew any
        if (currentUser != null && currentUser.getRole() == com.library.entity.enums.UserRole.READER) {
            if (!borrow.getReaderId().equals(currentUser.getReaderId())) {
                throw new ApiException("无权操作他人的借阅记录");
            }
        }
        if (borrowReturnRepository.existsByBorrowIdAndApproved(req.getBorrowId(), ApprovalStatus.是)) {
            throw new ApiException("该借阅记录已归还，无法续借");
        }

        int requestedDays = req.getDurationDays() != null
                ? req.getDurationDays() : props.getBorrow().getDefaultDurationDays();
        int actualDays = Math.min(requestedDays, props.getBorrow().getAutoApproveDuration());
        LocalDate newDueDate = LocalDate.now().plusDays(actualDays);

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
    @Transactional
    public void delete(List<Long> ids) { renewalRepository.deleteAllById(ids); }

    private RenewalResponse toResponse(Renewal r, Map<Long, Borrow> borrowMap,
                                        Map<Long, Reader> readerMap, Map<Long, Book> bookMap) {
        Borrow b = borrowMap.get(r.getBorrowId());
        Reader reader = b != null ? readerMap.get(b.getReaderId()) : null;
        Book bk = b != null ? bookMap.get(b.getBookId()) : null;

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
