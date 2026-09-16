package com.library.circulation.internal.adapter.persistence;

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

    long insertCopy(long titleId, String copyNo);

    Optional<CopyRow> copyById(long copyId);

    List<CopyRow> listCopies(Long titleId, String status, long afterId, int limit);

    List<CopyRow> availableCopies(long titleId, int limit);

    /** CAS 状态迁移；期望状态或版本不匹配返回 null（不自动重试，DD-0004 §2）。 */
    Long transitionCopy(long copyId, String expectedStatus, String nextStatus, long version);

    // ---- 借阅事务 --------------------------------------------------------------

    /** 同读者同书目未终结事务存在性（BR-0020 部分唯一索引的用例层预检）。 */
    boolean hasOpenTransactionForReaderTitle(long readerId, long titleId);

    long insertTransaction(NewTransaction candidate);

    Optional<TxRow> txById(long txId);

    Optional<TxRow> openLentByCopy(long copyId);

    /** 候补 FIFO（queued_at, id；ix_borrow_tx_title_waitlist 锚点）。 */
    List<TxRow> waitlistByTitle(long titleId, int limit);

    /** 事务 CAS 状态迁移；期望状态或版本不匹配返回 null。 */
    Long transitionTransaction(long txId, String expectedStatus, String nextStatus, long version);

    /** 续借 CAS：到期日推进 + renewal_count+1；版本不匹配返回 null。 */

    /** 人工处置借出/确认后补绑副本（不改状态与版本）。 */
    void attachCopy(long txId, Long copyId);

    /** 人工处置借出后推进到期日。 */
    void setDueDate(long txId, LocalDate dueDate);
    Long renewTransaction(long txId, LocalDate newDueDate, long version);

    // ---- 统计 ------------------------------------------------------------------

    int countAvailableCopies(long titleId);

    int countCopies(long titleId);

    int countConfirmedForDate(long titleId, LocalDate date);

    int countExpectedForDate(LocalDate date);

    int countManualForDate(LocalDate date);

    List<TxRow> myTransactions(long readerId, List<String> statuses, long afterId, int limit);

    record CopyRow(long id, long titleId, String copyNo, String status, long version) {
    }

    record TxRow(long id, long readerId, long titleId, Long copyId, String status,
                 LocalDate reservationDate, String pickupSlot, LocalDate dueDate,
                 int renewalCount, long version, Instant createdAt) {
    }

    record NewTransaction(long readerId, long titleId, Long copyId, String status,
                          LocalDate reservationDate, String pickupSlot, LocalDate dueDate) {
    }
}
