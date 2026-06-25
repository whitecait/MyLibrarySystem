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
