package com.library.circulation.internal.adapter.persistence;

import com.library.circulation.internal.SettlementAllocation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Date;
import java.time.LocalDate;

@Repository
public class PostgresClosingStore implements ClosingStore {
    private final JdbcTemplate jdbc;
    private final SettlementAllocation settlement;
    public PostgresClosingStore(JdbcTemplate jdbc, SettlementAllocation settlement){this.jdbc=jdbc;this.settlement=settlement;}
    @Override public void beginDraining(LocalDate date){jdbc.update("UPDATE closing_admission SET draining=TRUE,updated_at=now() WHERE scope='GLOBAL'");}
    /** 隔离事务（提交即逻辑 T）：未到馆处理、清点、结算三阶段与事件在同一事务（DD-0004 §1、SC-Q5）。 */
    @Override @Transactional public String isolateAndRun(LocalDate date){
        var day=Date.valueOf(date);
        jdbc.queryForObject("SELECT version FROM closing_control WHERE scope='GLOBAL' FOR UPDATE",Long.class);
        jdbc.update("UPDATE closing_control SET status='ISOLATED', \"current_date\"=?, version=version+1, updated_at=now() WHERE scope='GLOBAL'",day);
        jdbc.update("INSERT INTO closing_batch(business_date,status,attempts) VALUES (?,'PENDING',0) ON CONFLICT(business_date) DO UPDATE SET status='RETRYING',updated_at=now()",day);
        // 未到馆处理（一）：未取书的 CONFIRMED 预约释放绑定副本，回到可借候选池
        // （SET 列表中的 version 必须限定到 c：UPDATE…FROM 的命名空间同时含 borrow_transaction.version，
        //   未限定会报 ambiguous_column）
        jdbc.update("UPDATE book_copy c SET status='SHELF_READY',version=c.version+1,updated_at=now() FROM borrow_transaction t WHERE t.copy_id=c.id AND t.reservation_date=? AND t.status='CONFIRMED' AND c.status='RESERVED'",day);
        // 未到馆处理（二）：未取书的 CONFIRMED 预约以未借出结束；该路径不产生站内通知（AT-0002 §2.15、§2.23）
        jdbc.update("UPDATE borrow_transaction SET status='NOT_BORROWED',version=version+1,updated_at=now() WHERE reservation_date=? AND status='CONFIRMED'",day);
        // 结算阶段：到期候补按队头先到先得分配（WAITLIST_ASSIGNED）或到期清退（WAITLIST_EXPIRED），
        // 事件与业务事实同事务（DEF-M6-012；分配不清退、清退不分配的互斥由两条更新语句的
        // WHERE status='WAITLIST' 条件保证）
        settlement.assignAndExpireDueWaitlists(date);
        jdbc.update("UPDATE closing_batch SET status='SUCCEEDED',attempts=attempts+1,version=version+1,updated_at=now() WHERE business_date=?",day);
        return "SUCCEEDED";
    }
    @Override public Failure markFailure(LocalDate date){jdbc.update("INSERT INTO closing_batch(business_date,status,attempts) VALUES (?,'FAILED',1) ON CONFLICT(business_date) DO UPDATE SET attempts=closing_batch.attempts+1,status='FAILED',updated_at=now()",Date.valueOf(date));var row=jdbc.queryForMap("SELECT b.attempts,c.retry_budget FROM closing_batch b CROSS JOIN closing_control c WHERE b.business_date=? AND c.scope='GLOBAL'",Date.valueOf(date));var attempts=((Number)row.get("attempts")).intValue();var budget=((Number)row.get("retry_budget")).intValue();var status=attempts>budget?"MANUAL":"RETRYING";jdbc.update("UPDATE closing_batch SET status=? WHERE business_date=?",status,Date.valueOf(date));return new Failure(attempts,budget,status);}
    @Override public boolean caughtUpThrough(LocalDate date){var count=jdbc.queryForObject("SELECT count(*) FROM closing_batch WHERE business_date<=? AND status<>'SUCCEEDED'",Integer.class,Date.valueOf(date));return count!=null&&count==0;}
    @Override @Transactional public long reopen(LocalDate date){var version=jdbc.queryForObject("UPDATE closing_control SET status='REOPENED',\"current_date\"=?,version=version+1,updated_at=now() WHERE scope='GLOBAL' RETURNING version",Long.class,Date.valueOf(date));jdbc.update("UPDATE closing_admission SET draining=FALSE,updated_at=now() WHERE scope='GLOBAL'");return version==null?1:version;}
}
