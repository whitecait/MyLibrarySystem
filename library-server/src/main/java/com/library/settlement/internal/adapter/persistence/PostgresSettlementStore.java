package com.library.settlement.internal.adapter.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.contract.InfrastructureFailure;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class PostgresSettlementStore implements SettlementStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    private static final RowMapper<FineRow> FINE = (rs, i) -> new FineRow(
            rs.getLong("id"), rs.getLong("reader_id"), rs.getLong("source_tx_id"),
            rs.getLong("amount_cents"), rs.getString("status"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant());

    private static final RowMapper<OrderRow> ORDER = (rs, i) -> new OrderRow(
            rs.getLong("id"), rs.getLong("fine_id"), rs.getLong("amount_cents"),
            rs.getString("status"), rs.getObject("expires_at", OffsetDateTime.class).toInstant(),rs.getLong("version"));

    public PostgresSettlementStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public Optional<FineRow> fineBySourceTx(long sourceTxId) {
        return jdbc.query("SELECT * FROM fine WHERE source_tx_id = ?", FINE, sourceTxId)
                .stream().findFirst();
    }

    @Override
    public long insertFine(NewFine candidate) {
        try {
            return jdbc.queryForObject("""
                    INSERT INTO fine (reader_id, source_tx_id, amount_cents)
                    VALUES (?, ?, ?) RETURNING id
                    """, Long.class, candidate.readerId(), candidate.sourceTxId(), candidate.amountCents());
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(source_tx_id)：同归还至多一笔罚款（BR-0019）；幂等重入返回既有行
            return jdbc.queryForObject("SELECT id FROM fine WHERE source_tx_id = ?",
                    Long.class, candidate.sourceTxId());
        }
    }

    @Override
    public Optional<DebtSummary> debtFor(long readerId) {
        return jdbc.query("""
                SELECT count(*) AS cnt, COALESCE(sum(amount_cents), 0) AS total
                FROM fine WHERE reader_id = ? AND status = 'DUE'
                """, (rs, i) -> new DebtSummary(rs.getInt("cnt"), rs.getLong("total")), readerId)
                .stream().findFirst();
    }

    @Override
    public Optional<FineRow> fineById(long fineId) {
        return jdbc.query("SELECT * FROM fine WHERE id = ?", FINE, fineId).stream().findFirst();
    }

    @Override
    public List<FineRow> listFines(Long readerId, String status, long afterId, int limit) {
        var args = new java.util.ArrayList<Object>();
        var sql = new StringBuilder("SELECT * FROM fine WHERE id > ?");
        args.add(afterId);
        if (readerId != null) { sql.append(" AND reader_id = ?"); args.add(readerId); }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add("UNPAID".equals(status) ? "DUE" : "PAID".equals(status) ? "SETTLED" : status);
        }
        sql.append(" ORDER BY id LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), FINE, args.toArray());
    }

    @Override public Optional<OrderRow> orderById(long id) { return jdbc.query("SELECT o.*, f.amount_cents FROM payment_order o JOIN fine f ON f.id=o.fine_id WHERE o.id=?", ORDER, id).stream().findFirst(); }
    @Override public Optional<OrderRow> unreleasedOrder(long fineId) { return jdbc.query("SELECT o.*, f.amount_cents FROM payment_order o JOIN fine f ON f.id=o.fine_id WHERE o.fine_id=? AND o.status='OCCUPIED'", ORDER, fineId).stream().findFirst(); }
    @Override public long insertOrder(long fineId,long amount,Instant expiresAt) { return jdbc.queryForObject("INSERT INTO payment_order(fine_id,expires_at) VALUES (?,?) RETURNING id",Long.class,fineId,java.sql.Timestamp.from(expiresAt)); }
    @Override public boolean transitionOrder(long id,String expected,String next,Instant at) { var column="PAID".equals(next)?"paid_at":"closed_at"; return jdbc.update("UPDATE payment_order SET status=?, "+column+"=?, version=version+1 WHERE id=? AND status=?",next,java.sql.Timestamp.from(at),id,expected)==1; }
    @Override public Optional<CallbackRow> callback(String eventId) { return jdbc.query("SELECT * FROM payment_callback_event WHERE event_id=?",(rs,i)->new CallbackRow(rs.getString("event_id"),rs.getLong("order_id"),rs.getString("result"),rs.getObject("occurred_at",OffsetDateTime.class).toInstant(),rs.getString("outcome")),eventId).stream().findFirst(); }
    @Override public void insertCallback(CallbackRow c) { jdbc.update("INSERT INTO payment_callback_event(event_id,order_id,result,occurred_at,outcome) VALUES (?,?,?,?,?)",c.eventId(),c.orderId(),c.result(),java.sql.Timestamp.from(c.occurredAt()),c.outcome()); }
    @Override public void settleFine(long fineId,Instant at) { jdbc.update("UPDATE fine SET status='SETTLED', settled_at=?, version=version+1 WHERE id=? AND status='DUE'",java.sql.Timestamp.from(at),fineId); }
    @Override public long insertRefund(long orderId,long amount,long reviewId) { return jdbc.queryForObject("INSERT INTO refund_record(order_id,amount_cents,status,review_request_id) VALUES (?,?,'APPROVED',?) RETURNING id",Long.class,orderId,amount,reviewId); }
    @Override public Optional<ReconciliationRow> reconciliation(LocalDate d) { return jdbc.query("SELECT * FROM reconciliation_record WHERE business_date=?",(rs,i)->new ReconciliationRow(rs.getLong("id"),rs.getDate("business_date").toLocalDate(),readMap(rs.getString("counts")),readList(rs.getString("exceptions"))),java.sql.Date.valueOf(d)).stream().findFirst(); }
    @Override public long insertReconciliation(LocalDate d,Map<String,Integer> counts,List<String> exceptions) { try { return jdbc.queryForObject("INSERT INTO reconciliation_record(business_date,counts,exceptions) VALUES (?,?::jsonb,?::jsonb) RETURNING id",Long.class,java.sql.Date.valueOf(d),mapper.writeValueAsString(counts),mapper.writeValueAsString(exceptions)); } catch(Exception e){ throw new InfrastructureFailure("对账记录序列化失败",e); } }

    private Map<String,Integer> readMap(String json) { try { return mapper.readValue(json,new TypeReference<>(){}); } catch(Exception e){ throw new InfrastructureFailure("对账计数解析失败",e); } }
    private List<String> readList(String json) { try { return mapper.readValue(json,new TypeReference<>(){}); } catch(Exception e){ throw new InfrastructureFailure("对账异常解析失败",e); } }
}
