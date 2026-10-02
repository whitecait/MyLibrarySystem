package com.library.settlement.internal.adapter.persistence;

import com.library.settlement.contract.SettlementReadSnapshots;
import com.library.identity.contract.model.ReaderId;
import com.library.settlement.contract.model.FineId;
import com.library.settlement.contract.model.MoneyCents;
import com.library.contract.ModuleResult;
import com.library.contract.InfrastructureFailure;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PostgresSettlementReadSnapshots implements SettlementReadSnapshots {
    private final JdbcTemplate jdbc;
    public PostgresSettlementReadSnapshots(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override
    public ModuleResult<List<FineSnapshot>> readerFines(ReaderId reader, String status, FineId before, int limit) {
        // Preserve the existing combined-list projection; authoritative fine states remain here.
        try {
            return ModuleResult.Accepted.of(jdbc.query("""
                    SELECT id, CASE status WHEN 'DUE' THEN 'UNPAID' ELSE status END AS visible_status,
                           amount_cents
                    FROM fine WHERE reader_id=? AND id<=?
                      AND (CAST(? AS text) IS NULL OR CASE status WHEN 'DUE' THEN 'UNPAID' ELSE status END=?)
                    ORDER BY id DESC LIMIT ?
                    """, (rs, i) -> new FineSnapshot(FineId.of(rs.getLong("id")), rs.getString("visible_status"),
                    MoneyCents.of(rs.getLong("amount_cents"))), reader.value(), before.value(), status, status, limit));
        } catch (DataAccessException failure) {
            throw new InfrastructureFailure("本人业务快照读取失败", failure);
        }
    }
}
