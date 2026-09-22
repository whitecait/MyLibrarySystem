package com.library.circulation.internal;

import com.library.contract.audit.AuditAppender;
import com.library.contract.audit.AuditEventTypes;
import com.library.contract.audit.BusinessOutcome;
import com.library.contract.outbox.OutboxAppender;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/**
 * 闭馆结算的候补分配与清退（DEF-M6-012；AT-0002 §1.28、§2.15、§2.23）。
 *
 * <p>在闭馆隔离事务内执行（由 {@code PostgresClosingStore.isolateAndRun} 在完成未到馆处理与
 * 清点之后调用）：到期候补（`reservation_date <= 对象业务日`）按 `(reservation_date, queued_at, id)`
 * 队头先到先得绑定可用副本并置 `CONFIRMED`，登记 `WAITLIST_ASSIGNED`；无可分配副本者清退为
 * `NOT_BORROWED`，登记 `WAITLIST_EXPIRED`。两类事件、审计与业务事实同事务（DD-0004 §1），
 * 每个到期候补至多登记其一，闭馆后队列无到期滞留项。
 *
 * <p>分配与清退都不重写已终态行：更新语句带 `WHERE status='WAITLIST'`，影响 0 行即跳过。
 */
@Component
public class SettlementAllocation {

    private static final String AGG_TYPE = "CIRCULATION_TX";

    private final JdbcTemplate jdbc;
    private final OutboxAppender outbox;
    private final AuditAppender audit;

    public SettlementAllocation(JdbcTemplate jdbc, OutboxAppender outbox, AuditAppender audit) {
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.audit = audit;
    }

    /**
     * 对 {@code businessDate} 到期的候补执行分配与清退。
     *
     * @return 获分配件数（其余为清退件数，可由调用方按到期总数核算）
     */
    public int assignAndExpireDueWaitlists(LocalDate businessDate) {
        var assigned = 0;
        for (var candidate : dueWaitlists(businessDate)) {
            var copyId = bindFirstAvailableCopy(candidate.titleId());
            if (copyId != null && transitionToConfirmed(candidate.txId(), copyId)) {
                record(candidate, AuditEventTypes.WAITLIST_ASSIGNED, "ASSIGNED");
                assigned++;
                continue;
            }
            if (copyId != null) {
                // 事务状态已变化（并发终结）：释放刚绑定的副本，回到可借
                releaseCopy(copyId);
            }
            if (expire(candidate.txId())) {
                record(candidate, AuditEventTypes.WAITLIST_EXPIRED, "EXPIRED");
            }
        }
        return assigned;
    }

    private List<DueWaitlist> dueWaitlists(LocalDate businessDate) {
        return jdbc.query("""
                SELECT id, reader_id, title_id, reservation_date
                FROM borrow_transaction
                WHERE status='WAITLIST' AND reservation_date <= ?
                ORDER BY reservation_date, queued_at, id
                """, (rs, i) -> new DueWaitlist(rs.getLong("id"), rs.getLong("reader_id"),
                rs.getLong("title_id"), rs.getDate("reservation_date").toLocalDate()),
                Date.valueOf(businessDate));
    }

    private Long bindFirstAvailableCopy(long titleId) {
        var bound = jdbc.query("""
                UPDATE book_copy SET status='RESERVED', version=version+1, updated_at=now()
                WHERE id = (SELECT id FROM book_copy WHERE title_id=? AND status='SHELF_READY'
                            ORDER BY id LIMIT 1)
                RETURNING id
                """, (rs, i) -> rs.getLong(1), titleId);
        return bound.isEmpty() ? null : bound.getFirst();
    }

    private boolean transitionToConfirmed(long txId, long copyId) {
        return jdbc.update("""
                UPDATE borrow_transaction SET status='CONFIRMED', copy_id=?, version=version+1,
                                             updated_at=now()
                WHERE id=? AND status='WAITLIST'
                """, copyId, txId) == 1;
    }

    private void releaseCopy(long copyId) {
        jdbc.update("""
                UPDATE book_copy SET status='SHELF_READY', version=version+1, updated_at=now()
                WHERE id=? AND status='RESERVED'
                """, copyId);
    }

    private boolean expire(long txId) {
        return jdbc.update("""
                UPDATE borrow_transaction SET status='NOT_BORROWED', version=version+1, updated_at=now()
                WHERE id=? AND status='WAITLIST'
                """, txId) == 1;
    }

    /** 通知的关联业务对象为预约事务 id，排序锚点为该事务的预约日期（AT-0002 §2.27）。 */
    private void record(DueWaitlist candidate, String eventType, String outcome) {
        outbox.append(AGG_TYPE, candidate.txId(), eventType,
                java.util.Map.of("readerId", candidate.readerId(),
                        "reservationDate", candidate.reservationDate().toString()));
        // 自动判定：无操作者，actor_id 记为 0（闭馆由 SYSTEM 触发，非人工例外）
        audit.appendWithBusinessFact(eventType, null,
                new BusinessOutcome(candidate.txId(), outcome));
    }

    private record DueWaitlist(long txId, long readerId, long titleId, LocalDate reservationDate) {
    }
}
