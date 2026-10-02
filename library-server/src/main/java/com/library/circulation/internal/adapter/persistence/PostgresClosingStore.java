package com.library.circulation.internal.adapter.persistence;

import com.library.circulation.internal.SettlementAllocation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class PostgresClosingStore implements ClosingStore {
    private final JdbcTemplate jdbc;
    private final SettlementAllocation settlement;
    public PostgresClosingStore(JdbcTemplate jdbc, SettlementAllocation settlement){this.jdbc=jdbc;this.settlement=settlement;}
    @Override public Optional<LocalDate> firstUnclosedDateThrough(LocalDate inclusiveDate){
        var baseline=jdbc.query("SELECT first_business_date FROM closing_control WHERE scope='GLOBAL'",rs->{if(!rs.next())return (LocalDate)null;var date=rs.getDate(1);return date==null?null:date.toLocalDate();});
        if(baseline==null)baseline=jdbc.query("SELECT min(business_date) FROM closing_batch",rs->{if(!rs.next())return (LocalDate)null;var date=rs.getDate(1);return date==null?null:date.toLocalDate();});
        if(baseline==null)return Optional.of(inclusiveDate);
        if(baseline.isAfter(inclusiveDate))return Optional.of(inclusiveDate);
        return jdbc.query("""
                SELECT calendar.day::date
                FROM generate_series(?::date, ?::date, interval '1 day') AS calendar(day)
                LEFT JOIN closing_batch b ON b.business_date=calendar.day::date
                WHERE b.business_date IS NULL OR b.status<>'SUCCEEDED'
                ORDER BY calendar.day LIMIT 1
                """,rs->rs.next()?Optional.of(rs.getDate(1).toLocalDate()):Optional.empty(),
                Date.valueOf(baseline),Date.valueOf(inclusiveDate));
    }
    @Override public void beginDraining(LocalDate date){jdbc.update("UPDATE closing_admission SET draining=TRUE,updated_at=now() WHERE scope='GLOBAL'");}
    /** 隔离事务（提交即逻辑 T）：未到馆处理、清点、结算三阶段与事件在同一事务（DD-0004 §1、SC-Q5）。 */
    @Override @Transactional public String isolateAndRun(LocalDate date){
        var day=Date.valueOf(date);
        jdbc.queryForObject("SELECT version FROM closing_control WHERE scope='GLOBAL' FOR UPDATE",Long.class);
        jdbc.update("UPDATE closing_control SET status='ISOLATED', \"current_date\"=?, first_business_date=CASE WHEN first_business_date IS NULL OR first_business_date>? THEN ? ELSE first_business_date END, version=version+1, updated_at=now() WHERE scope='GLOBAL'",day,day,day);
        jdbc.update("INSERT INTO closing_batch(business_date,status,attempts) VALUES (?,'PENDING',0) ON CONFLICT(business_date) DO UPDATE SET status='RETRYING',updated_at=now()",day);
        // 未到馆处理（一）：先释放到待清点；本批闭馆会在清点阶段转为可借。
        var releasedNoShowCopies = jdbc.query("""
                UPDATE book_copy c
                SET status='PENDING_SHELVING', loan_eligible_on=?,
                    version=c.version+1, updated_at=now()
                FROM borrow_transaction t
                WHERE t.copy_id=c.id AND t.reservation_date=? AND t.status='CONFIRMED'
                  AND c.status='RESERVED'
                RETURNING c.id
                """, (rs, row) -> rs.getLong(1), Date.valueOf(date.plusDays(1)), day);
        // 未到馆处理（二）：未取书的 CONFIRMED 预约以未借出结束；该路径不产生站内通知（AT-0002 §2.15、§2.23）
        jdbc.update("UPDATE borrow_transaction SET status='NOT_BORROWED',version=version+1,updated_at=now() WHERE reservation_date=? AND status='CONFIRMED'",day);
        // 清点阶段：只清点闭馆业务日及之前已进入待清点的副本。补处理旧闭馆日时，
        // 今日新归还或释放的副本 updated_at 晚于该业务日，仍保持隔离；本批释放的未到馆副本
        // 即使属于补处理日期，也通过本事务刚释放的 id 在本阶段完成清点。
        jdbc.update("""
                UPDATE book_copy
                SET status='SHELF_READY', version=version+1, updated_at=now()
                WHERE status='PENDING_SHELVING'
                  AND (updated_at AT TIME ZONE 'Asia/Shanghai')::date <= ?
                """, day);
        clearCopiesReleasedByThisBatch(releasedNoShowCopies);
        // 结算阶段：到期候补按队头先到先得分配（WAITLIST_ASSIGNED）或到期清退（WAITLIST_EXPIRED），
        // 事件与业务事实同事务（DEF-M6-012；分配不清退、清退不分配的互斥由两条更新语句的
        // WHERE status='WAITLIST' 条件保证）
        settlement.assignAndExpireDueWaitlists(date);
        jdbc.update("UPDATE closing_batch SET status='SUCCEEDED',attempts=attempts+1,version=version+1,updated_at=now() WHERE business_date=?",day);
        return "SUCCEEDED";
    }
    private void clearCopiesReleasedByThisBatch(List<Long> copyIds) {
        if (copyIds.isEmpty()) return;
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            var ids = connection.createArrayOf("int8", copyIds.toArray());
            try (var statement = connection.prepareStatement("""
                    UPDATE book_copy
                    SET status='SHELF_READY', version=version+1, updated_at=now()
                    WHERE id = ANY (?) AND status='PENDING_SHELVING'
                    """)) {
                statement.setArray(1, ids);
                statement.executeUpdate();
            } finally {
                ids.free();
            }
            return null;
        });
    }
    @Override public Failure markFailure(LocalDate date){jdbc.update("INSERT INTO closing_batch(business_date,status,attempts) VALUES (?,'FAILED',1) ON CONFLICT(business_date) DO UPDATE SET attempts=closing_batch.attempts+1,status='FAILED',updated_at=now()",Date.valueOf(date));var row=jdbc.queryForMap("SELECT b.attempts,c.retry_budget FROM closing_batch b CROSS JOIN closing_control c WHERE b.business_date=? AND c.scope='GLOBAL'",Date.valueOf(date));var attempts=((Number)row.get("attempts")).intValue();var budget=((Number)row.get("retry_budget")).intValue();var status=attempts>budget?"MANUAL":"RETRYING";jdbc.update("UPDATE closing_batch SET status=? WHERE business_date=?",status,Date.valueOf(date));return new Failure(attempts,budget,status);}
    @Override public boolean caughtUpThrough(LocalDate date){return firstUnclosedDateThrough(date).isEmpty();}
    @Override @Transactional public long reopen(LocalDate date){var version=jdbc.queryForObject("UPDATE closing_control SET status='REOPENED',\"current_date\"=?,version=version+1,updated_at=now() WHERE scope='GLOBAL' RETURNING version",Long.class,Date.valueOf(date));jdbc.update("UPDATE closing_admission SET draining=FALSE,updated_at=now() WHERE scope='GLOBAL'");return version==null?1:version;}
}
