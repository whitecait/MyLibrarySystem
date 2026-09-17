package com.library.circulation.internal.adapter.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Date;
import java.time.LocalDate;

@Repository
public class PostgresClosingStore implements ClosingStore {
    private final JdbcTemplate jdbc;
    public PostgresClosingStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Override public void beginDraining(LocalDate date){jdbc.update("UPDATE closing_admission SET draining=TRUE,updated_at=now() WHERE scope='GLOBAL'");}
    @Override @Transactional public String isolateAndRun(LocalDate date){jdbc.queryForObject("SELECT version FROM closing_control WHERE scope='GLOBAL' FOR UPDATE",Long.class);jdbc.update("UPDATE closing_control SET status='ISOLATED', current_date=?, version=version+1, updated_at=now() WHERE scope='GLOBAL'",Date.valueOf(date));jdbc.update("INSERT INTO closing_batch(business_date,status,attempts) VALUES (?,'PENDING',0) ON CONFLICT(business_date) DO UPDATE SET status='RETRYING',updated_at=now()",Date.valueOf(date));jdbc.update("UPDATE book_copy c SET status='SHELF_READY',version=version+1,updated_at=now() FROM borrow_transaction t WHERE t.copy_id=c.id AND t.reservation_date=? AND t.status='CONFIRMED' AND c.status='RESERVED'",Date.valueOf(date));jdbc.update("UPDATE borrow_transaction SET status='NOT_BORROWED',version=version+1,updated_at=now() WHERE reservation_date=? AND status IN ('CONFIRMED','WAITLIST')",Date.valueOf(date));jdbc.update("UPDATE closing_batch SET status='SUCCEEDED',attempts=attempts+1,version=version+1,updated_at=now() WHERE business_date=?",Date.valueOf(date));return "SUCCEEDED";}
    @Override public Failure markFailure(LocalDate date){jdbc.update("INSERT INTO closing_batch(business_date,status,attempts) VALUES (?,'FAILED',1) ON CONFLICT(business_date) DO UPDATE SET attempts=closing_batch.attempts+1,status='FAILED',updated_at=now()",Date.valueOf(date));var row=jdbc.queryForMap("SELECT b.attempts,c.retry_budget FROM closing_batch b CROSS JOIN closing_control c WHERE b.business_date=? AND c.scope='GLOBAL'",Date.valueOf(date));var attempts=((Number)row.get("attempts")).intValue();var budget=((Number)row.get("retry_budget")).intValue();var status=attempts>budget?"MANUAL":"RETRYING";jdbc.update("UPDATE closing_batch SET status=? WHERE business_date=?",status,Date.valueOf(date));return new Failure(attempts,budget,status);}
    @Override public boolean caughtUpThrough(LocalDate date){var count=jdbc.queryForObject("SELECT count(*) FROM closing_batch WHERE business_date<=? AND status<>'SUCCEEDED'",Integer.class,Date.valueOf(date));return count!=null&&count==0;}
    @Override @Transactional public long reopen(LocalDate date){var version=jdbc.queryForObject("UPDATE closing_control SET status='REOPENED',current_date=?,version=version+1,updated_at=now() WHERE scope='GLOBAL' RETURNING version",Long.class,Date.valueOf(date));jdbc.update("UPDATE closing_admission SET draining=FALSE,updated_at=now() WHERE scope='GLOBAL'");return version==null?1:version;}
}
