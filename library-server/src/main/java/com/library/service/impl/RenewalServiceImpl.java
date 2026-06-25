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
            if (borrowIds.isEmpty()) return Page.empty(pageable);
            renewalPage = renewalRepository.findAll(
                    (Specification<Renewal>) (root, query, cb) -> {
                        List<Predicate> preds = new ArrayList<>();
                        preds.add(root.get("borrowId").in(borrowIds));
                        if (approved != null)
                            preds.add(cb.equal(root.get("approved"), ApprovalStatus.valueOf(approved)));
                        return cb.and(preds.toArray(new Predicate[0]));
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
