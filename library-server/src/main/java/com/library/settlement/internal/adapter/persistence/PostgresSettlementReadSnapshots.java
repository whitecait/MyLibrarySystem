package com.library.settlement.internal.adapter.persistence;

import com.library.settlement.contract.SettlementReadSnapshots;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PostgresSettlementReadSnapshots implements SettlementReadSnapshots {
    private final JdbcTemplate jdbc;
    public PostgresSettlementReadSnapshots(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override
    public List<FineSnapshot> readerFines(long reader, String status, long before, int limit) {
        // Preserve the existing combined-list projection; authoritative fine states remain here.
        return jdbc.query("""
                SELECT id, CASE status WHEN 'DUE' THEN 'UNPAID' ELSE status END AS visible_status,
                       amount_cents
                FROM fine WHERE reader_id=? AND id<?
                  AND (CAST(? AS text) IS NULL OR CASE status WHEN 'DUE' THEN 'UNPAID' ELSE status END=?)
                ORDER BY id DESC LIMIT ?
                """, (rs, i) -> new FineSnapshot(rs.getLong("id"), rs.getString("visible_status"),
                rs.getLong("amount_cents")), reader, before, status, status, limit);
    }
}
