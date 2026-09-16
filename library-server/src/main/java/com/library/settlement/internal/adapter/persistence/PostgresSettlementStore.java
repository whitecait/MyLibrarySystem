package com.library.settlement.internal.adapter.persistence;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PostgresSettlementStore implements SettlementStore {

    private final JdbcTemplate jdbc;

    private static final RowMapper<FineRow> FINE = (rs, i) -> new FineRow(
            rs.getLong("id"), rs.getLong("reader_id"), rs.getLong("source_tx_id"),
            rs.getLong("amount_cents"), rs.getString("status"));

    public PostgresSettlementStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
}
