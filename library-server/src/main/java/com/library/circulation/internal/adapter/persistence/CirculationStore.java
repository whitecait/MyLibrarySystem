package com.library.circulation.internal.adapter.persistence;

import com.library.circulation.contract.model.GuaranteeType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * CIRCULATION 持久化内部缝（MLS-DOC-MD-0004 §4）：副本/事务条件更新、
 * FIFO 候补、统一锁序（闭馆控制→预约队列→借阅事务→副本→罚款/订单→Outbox）。
 */
public interface CirculationStore {

    // ---- 副本 ---------------------------------------------------------------

    boolean copyNoExists(long titleId, String copyNo);

    long insertCopy(long titleId, String copyNo, LocalDate loanEligibleOn);

    Optional<CopyRow> copyById(long copyId);

    List<CopyRow> listCopies(Long titleId, String status, long afterId, int limit);

    List<CopyRow> availableCopies(long titleId, int limit);

    /** Available rows whose eligibility date has arrived; reservation allocation uses availableCopies instead. */
    List<CopyRow> loanableCopies(long titleId, LocalDate businessDate, int limit);

    /** CAS 状态迁移；期望状态或版本不匹配返回 null（不自动重试，DD-0004 §2）。 */
    Long transitionCopy(long copyId, String expectedStatus, String nextStatus, long version);

    /** 状态与最早可借业务日原子迁移；仅用于正常归还/释放与 RESTORE 的日期赋值。 */
    Long transitionCopyAndSetLoanEligibleOn(long copyId, String expectedStatus, String nextStatus,
                                            long version, LocalDate loanEligibleOn);

    /** 所有实际借出入口共用的副本 CAS；业务日未到时返回 null。 */
    Long transitionCopyToLent(long copyId, String expectedStatus, long version, LocalDate businessDate);

    // ---- 借阅事务 --------------------------------------------------------------

    /** 同读者同书目未终结事务存在性（BR-0020 部分唯一索引的用例层预检）。 */
    boolean hasOpenTransactionForReaderTitle(long readerId, long titleId);

    void lockReaderActivity(long readerId);

    /** Serializes reservations and renewals for the same title within the active transaction. */
    void lockTitleReservations(long titleId);

    int countActiveLoansForReader(long readerId);

    int countActiveReservationsForReader(long readerId);

    boolean hasOverdueLentForReader(long readerId, LocalDate onDate);

    long insertTransaction(NewTransaction candidate);

    Optional<TxRow> txById(long txId);

    Optional<TxRow> openLentByCopy(long copyId);

    /** 候补 FIFO（queued_at, id；ix_borrow_tx_title_waitlist 锚点）。 */
    List<TxRow> waitlistByTitle(long titleId, int limit);

    /** 书目级有效预约（候补、已确认）中的最早预约日，用于约束续借期限。 */
    Optional<LocalDate> earliestValidReservationDateForTitle(long titleId);

    /** 事务 CAS 状态迁移；期望状态或版本不匹配返回 null。 */
    Long transitionTransaction(long txId, String expectedStatus, String nextStatus, long version);

    /** 续借 CAS：到期日推进 + renewal_count+1；版本不匹配返回 null。 */

    /** 人工处置借出/确认后补绑副本（不改状态与版本）。 */
    void attachCopy(long txId, Long copyId);

    /** 人工处置借出后推进到期日。 */
    void setDueDate(long txId, LocalDate dueDate);

    LocalDate loanStartedDate(long txId);

    void setLoanStartedDate(long txId, LocalDate startDate);

    Long renewTransaction(long txId, LocalDate newDueDate, long version);

    // ---- 统计 ------------------------------------------------------------------

    int countAvailableCopies(long titleId);

    int countCopies(long titleId);

    int countBookableCopies(long titleId);

    /** Read one database snapshot for the displayed range; no per-reader data leaves this seam. */
    List<CalendarFacts> calendarFacts(long titleId, LocalDate from, LocalDate to);

    int countExpectedForDate(LocalDate date);

    int countManualForDate(LocalDate date);

    List<TxRow> myTransactions(long readerId, List<String> statuses, long afterId, int limit);

    record CalendarFacts(LocalDate businessDate, int totalCopies, int availableCopies,
                         int guaranteeReservations, boolean hasConfirmedReservation, boolean hasLentCopy) {
    }

    record CopyRow(long id, long titleId, String copyNo, String status, long version) {
    }

    record TxRow(long id, long readerId, long titleId, Long copyId, String status,
                 LocalDate reservationDate, String pickupSlot, LocalDate dueDate,
                 int renewalCount, long version, Instant createdAt) {
    }

    record NewTransaction(long readerId, long titleId, Long copyId, String status,
                          LocalDate reservationDate, String pickupSlot, LocalDate dueDate,
                          GuaranteeType guaranteeType) {
        /**
         * Compatibility path for non-reservation or legacy fixtures whose creation source is unknown.
         */
        public NewTransaction(long readerId, long titleId, Long copyId, String status,
                              LocalDate reservationDate, String pickupSlot, LocalDate dueDate) {
            this(readerId, titleId, copyId, status, reservationDate, pickupSlot, dueDate, null);
        }
    }
}
