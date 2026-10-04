package com.library.circulation.internal.adapter.persistence;

import com.library.circulation.contract.model.GuaranteeType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 测试用 CirculationStore Adapter（单元层）：状态迁移/竞争 CAS 语义的内存模拟。
 * PostgreSQL 行锁与部分唯一索引并发语义由 IT-CIRC 真库证明。
 */
public class InMemoryCirculationStore implements CirculationStore {

    private final Map<Long, CopyRow> copies = new HashMap<>();
    private final Map<Long, LocalDate> loanEligibleDates = new HashMap<>();
    private final Map<Long, TxRow> txs = new HashMap<>();
    private final Map<Long, GuaranteeType> guaranteeTypes = new HashMap<>();
    private final Map<Long, LocalDate> loanStartedDates = new HashMap<>();
    private long copySequence;
    private long txSequence;

    @Override
    public boolean copyNoExists(long titleId, String copyNo) {
        return copies.values().stream()
                .anyMatch(c -> c.titleId() == titleId && c.copyNo().equals(copyNo));
    }

    @Override
    public long insertCopy(long titleId, String copyNo, LocalDate loanEligibleOn) {
        var id = ++copySequence;
        copies.put(id, new CopyRow(id, titleId, copyNo, "PENDING_SHELVING", 0));
        loanEligibleDates.put(id, loanEligibleOn);
        return id;
    }

    @Override
    public Optional<CopyRow> copyById(long copyId) {
        return Optional.ofNullable(copies.get(copyId));
    }

    @Override
    public List<CopyRow> listCopies(Long titleId, String status, long afterId, int limit) {
        return copies.values().stream()
                .filter(c -> c.id() > afterId)
                .filter(c -> titleId == null || c.titleId() == titleId)
                .filter(c -> status == null || c.status().equals(status))
                .sorted(java.util.Comparator.comparingLong(CopyRow::id))
                .limit(limit).toList();
    }

    @Override
    public List<CopyRow> availableCopies(long titleId, int limit) {
        return copies.values().stream()
                .filter(c -> c.titleId() == titleId && "SHELF_READY".equals(c.status()))
                .sorted(java.util.Comparator.comparingLong(CopyRow::id))
                .limit(limit).toList();
    }

    @Override
    public List<CopyRow> loanableCopies(long titleId, LocalDate businessDate, int limit) {
        return copies.values().stream()
                .filter(c -> c.titleId() == titleId && "SHELF_READY".equals(c.status()))
                .filter(c -> loanEligibleDates.get(c.id()) != null
                        && !loanEligibleDates.get(c.id()).isAfter(businessDate))
                .sorted(java.util.Comparator.comparingLong(CopyRow::id))
                .limit(limit).toList();
    }

    @Override
    public Long transitionCopy(long copyId, String expectedStatus, String nextStatus, long version) {
        var copy = copies.get(copyId);
        if (copy == null || !copy.status().equals(expectedStatus) || copy.version() != version) {
            return null;
        }
        var updated = new CopyRow(copy.id(), copy.titleId(), copy.copyNo(), nextStatus, version + 1);
        copies.put(copyId, updated);
        return updated.version();
    }

    @Override
    public Long transitionCopyAndSetLoanEligibleOn(long copyId, String expectedStatus, String nextStatus,
                                                   long version, LocalDate loanEligibleOn) {
        var updated = transitionCopy(copyId, expectedStatus, nextStatus, version);
        if (updated != null) {
            loanEligibleDates.put(copyId, loanEligibleOn);
        }
        return updated;
    }

    @Override
    public Long transitionCopyToLent(long copyId, String expectedStatus, long version, LocalDate businessDate) {
        var loanEligibleOn = loanEligibleDates.get(copyId);
        if (loanEligibleOn == null || loanEligibleOn.isAfter(businessDate)) {
            return null;
        }
        return transitionCopy(copyId, expectedStatus, "LENT", version);
    }

    /** Unit fixtures may mark a copy as historically eligible without advancing the shared clock. */
    public void setLoanEligibleOnForTest(long copyId, LocalDate loanEligibleOn) {
        if (!copies.containsKey(copyId)) {
            throw new IllegalArgumentException("unknown copy id: " + copyId);
        }
        loanEligibleDates.put(copyId, loanEligibleOn);
    }

    @Override
    public boolean hasOpenTransactionForReaderTitle(long readerId, long titleId) {
        return txs.values().stream().anyMatch(t -> t.readerId() == readerId && t.titleId() == titleId
                && List.of("AWAIT", "CONFIRMED", "MANUAL_HANDLING", "WAITLIST", "LENT").contains(t.status()));
    }

    @Override
    public void lockReaderActivity(long readerId) {
        // Single-threaded unit adapter; PostgreSQL integration tests cover advisory locking.
    }

    @Override
    public void lockTitleReservations(long titleId) {
        // Single-threaded unit adapter; PostgreSQL integration tests cover advisory locking.
    }

    @Override
    public int countActiveLoansForReader(long readerId) {
        return (int) txs.values().stream()
                .filter(t -> t.readerId() == readerId && "LENT".equals(t.status())).count();
    }

    @Override
    public int countActiveReservationsForReader(long readerId) {
        return (int) txs.values().stream().filter(t -> t.readerId() == readerId
                && List.of("AWAIT", "CONFIRMED", "MANUAL_HANDLING", "WAITLIST")
                .contains(t.status())).count();
    }

    @Override
    public boolean hasOverdueLentForReader(long readerId, LocalDate onDate) {
        return txs.values().stream().anyMatch(t -> t.readerId() == readerId && "LENT".equals(t.status())
                && t.dueDate() != null && t.dueDate().isBefore(onDate));
    }

    @Override
    public long insertTransaction(NewTransaction candidate) {
        var id = ++txSequence;
        guaranteeTypes.put(id, candidate.guaranteeType());
        txs.put(id, new TxRow(id, candidate.readerId(), candidate.titleId(), candidate.copyId(),
                candidate.status(), candidate.reservationDate(), candidate.pickupSlot(),
                candidate.dueDate(), 0, 0, Instant.now()));
        if ("LENT".equals(candidate.status())) {
            loanStartedDates.put(id, candidate.reservationDate());
        }
        return id;
    }

    /** Unit-test inspection hook for creation-time metadata. */
    public GuaranteeType guaranteeTypeForTest(long txId) {
        return guaranteeTypes.get(txId);
    }

    @Override
    public Optional<TxRow> txById(long txId) {
        return Optional.ofNullable(txs.get(txId));
    }

    @Override
    public Optional<TxRow> openLentByCopy(long copyId) {
        return txs.values().stream()
                .filter(t -> t.copyId() != null && t.copyId() == copyId && "LENT".equals(t.status()))
                .findFirst();
    }

    @Override
    public List<TxRow> waitlistByTitle(long titleId, int limit) {
        return txs.values().stream()
                .filter(t -> t.titleId() == titleId && "WAITLIST".equals(t.status()))
                .sorted(java.util.Comparator.comparingLong(TxRow::id))
                .limit(limit).toList();
    }

    @Override
    public Optional<LocalDate> earliestValidReservationDateForTitle(long titleId) {
        return txs.values().stream()
                .filter(t -> t.titleId() == titleId)
                .filter(t -> List.of("CONFIRMED", "WAITLIST").contains(t.status()))
                .map(TxRow::reservationDate)
                .filter(java.util.Objects::nonNull)
                .min(LocalDate::compareTo);
    }

    @Override
    public Long transitionTransaction(long txId, String expectedStatus, String nextStatus, long version) {
        var tx = txs.get(txId);
        if (tx == null || !tx.status().equals(expectedStatus) || tx.version() != version) {
            return null;
        }
        txs.put(txId, new TxRow(tx.id(), tx.readerId(), tx.titleId(), tx.copyId(), nextStatus,
                tx.reservationDate(), tx.pickupSlot(), tx.dueDate(), tx.renewalCount(), version + 1,
                tx.createdAt()));
        return version + 1;
    }

    @Override
    public Long renewTransaction(long txId, LocalDate newDueDate, long version) {
        var tx = txs.get(txId);
        if (tx == null || !"LENT".equals(tx.status()) || tx.version() != version) {
            return null;
        }
        txs.put(txId, new TxRow(tx.id(), tx.readerId(), tx.titleId(), tx.copyId(), "LENT",
                tx.reservationDate(), tx.pickupSlot(), newDueDate, tx.renewalCount() + 1, version + 1,
                tx.createdAt()));
        return version + 1;
    }

    @Override
    public void attachCopy(long txId, Long copyId) {
        var tx = txs.get(txId);
        if (tx != null) {
            txs.put(txId, new TxRow(tx.id(), tx.readerId(), tx.titleId(), copyId, tx.status(),
                    tx.reservationDate(), tx.pickupSlot(), tx.dueDate(), tx.renewalCount(),
                    tx.version(), tx.createdAt()));
        }
    }

    @Override
    public void setDueDate(long txId, LocalDate dueDate) {
        var tx = txs.get(txId);
        if (tx != null) {
            txs.put(txId, new TxRow(tx.id(), tx.readerId(), tx.titleId(), tx.copyId(), tx.status(),
                    tx.reservationDate(), tx.pickupSlot(), dueDate, tx.renewalCount(),
                    tx.version(), tx.createdAt()));
        }
    }

    @Override
    public LocalDate loanStartedDate(long txId) {
        return loanStartedDates.get(txId);
    }

    @Override
    public void setLoanStartedDate(long txId, LocalDate startDate) {
        loanStartedDates.put(txId, startDate);
    }

    @Override
    public int countAvailableCopies(long titleId) {
        return (int) copies.values().stream()
                .filter(c -> c.titleId() == titleId && "SHELF_READY".equals(c.status())).count();
    }

    @Override
    public int countCopies(long titleId) {
        return (int) copies.values().stream().filter(c -> c.titleId() == titleId).count();
    }

    @Override
    public int countBookableCopies(long titleId) {
        return (int) copies.values().stream().filter(c -> c.titleId() == titleId
                && List.of("SHELF_READY", "LENT", "PENDING_SHELVING").contains(c.status())).count();
    }

    @Override
    public int countConfirmedForDate(long titleId, LocalDate date) {
        return (int) txs.values().stream()
                .filter(t -> t.titleId() == titleId && date.equals(t.reservationDate())
                        && List.of("CONFIRMED", "LENT").contains(t.status())).count();
    }

    @Override
    public int countExpectedForDate(LocalDate date) {
        return (int) txs.values().stream()
                .filter(t -> date.equals(t.reservationDate())
                        && List.of("CONFIRMED", "LENT", "MANUAL_HANDLING").contains(t.status())).count();
    }

    @Override
    public int countManualForDate(LocalDate date) {
        return (int) txs.values().stream()
                .filter(t -> date.equals(t.reservationDate()) && "MANUAL_HANDLING".equals(t.status())).count();
    }

    @Override
    public List<TxRow> myTransactions(long readerId, List<String> statuses, long afterId, int limit) {
        return new ArrayList<>(txs.values()).stream()
                .filter(t -> t.readerId() == readerId && t.id() < afterId
                        && statuses.contains(t.status()))
                .sorted(java.util.Comparator.comparingLong(TxRow::id).reversed())
                .limit(limit).toList();
    }
}
