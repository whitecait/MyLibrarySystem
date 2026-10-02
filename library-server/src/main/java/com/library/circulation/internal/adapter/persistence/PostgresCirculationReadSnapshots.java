package com.library.circulation.internal.adapter.persistence;

import com.library.circulation.contract.CirculationReadSnapshots;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Repository
public class PostgresCirculationReadSnapshots implements CirculationReadSnapshots {
    private final JdbcTemplate jdbc;
    public PostgresCirculationReadSnapshots(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public ArrivalCounts arrivals(LocalDate date) {
        return jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE status IN ('CONFIRMED','LENT','MANUAL_HANDLING')) AS expected,
                       count(*) FILTER (WHERE status='LENT') AS arrived,
                       count(*) FILTER (WHERE status='CONFIRMED') AS pending,
                       count(*) FILTER (WHERE status='MANUAL_HANDLING') AS exceptions
                FROM borrow_transaction WHERE reservation_date=?
                """, (rs, i) -> new ArrivalCounts(rs.getInt("expected"), rs.getInt("arrived"),
                rs.getInt("pending"), rs.getInt("exceptions")), Date.valueOf(date));
    }

    @Override
    public List<TransactionSnapshot> readerTransactions(long reader, List<String> kinds,
                                                         String status, long before, int limit) {
        var statuses = new ArrayList<String>();
        if (kinds.contains("RESERVATION")) {
            statuses.addAll(List.of("AWAIT", "CONFIRMED", "WAITLIST", "MANUAL_HANDLING", "NOT_BORROWED"));
        }
        if (kinds.contains("LOAN")) { statuses.addAll(List.of("LENT", "RETURNED", "RETURN_ABNORMAL")); }
        if (statuses.isEmpty()) { return List.of(); }
        return jdbc.query("""
                SELECT id, title_id, status, reservation_date,
                       CASE WHEN status IN ('LENT','RETURNED','RETURN_ABNORMAL')
                            THEN 'LOAN' ELSE 'RESERVATION' END AS kind
                FROM borrow_transaction
                WHERE reader_id=? AND id<? AND status=ANY(?)
                  AND (CAST(? AS text) IS NULL OR status=?)
                ORDER BY id DESC LIMIT ?
                """, (rs, i) -> new TransactionSnapshot(rs.getString("kind"), rs.getLong("id"),
                rs.getString("status"), rs.getLong("title_id"),
                rs.getDate("reservation_date") == null ? null : rs.getDate("reservation_date").toLocalDate()),
                reader, before, statuses.toArray(new String[0]), status, status, limit);
    }
}
