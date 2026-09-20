package com.library.query.internal.adapter.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Repository
public class PostgresQueryStore implements QueryStore {

    private final JdbcTemplate jdbc;

    private static final RowMapper<MyBusinessRow> BUSINESS = (rs, i) -> new MyBusinessRow(
            rs.getString("kind"), rs.getLong("id"), rs.getString("status"),
            rs.getString("title"),
            rs.getDate("business_date") == null ? null : rs.getDate("business_date").toLocalDate(),
            rs.getObject("amount_cents") == null ? null : rs.getLong("amount_cents"));

    public PostgresQueryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ArrivalCounts arrivals(LocalDate date) {
        return jdbc.query("""
                SELECT count(*) FILTER (WHERE status IN ('CONFIRMED', 'LENT', 'MANUAL_HANDLING')) AS expected,
                       count(*) FILTER (WHERE status = 'LENT')                     AS arrived,
                       count(*) FILTER (WHERE status = 'CONFIRMED')                AS pending,
                       count(*) FILTER (WHERE status = 'MANUAL_HANDLING')          AS exceptions
                FROM borrow_transaction WHERE reservation_date = ?
                """, (rs, i) -> new ArrivalCounts(rs.getInt("expected"), rs.getInt("arrived"),
                rs.getInt("pending"), rs.getInt("exceptions")), Date.valueOf(date))
                .stream().findFirst().orElse(new ArrivalCounts(0, 0, 0, 0));
    }

    @Override
    public List<MyBusinessRow> myBusiness(long readerId, List<String> kinds, String status,
                                          long beforeId, int limit) {
        var wanted = kinds == null || kinds.isEmpty()
                ? List.of("RESERVATION", "LOAN", "FINE") : kinds;
        var rows = new ArrayList<MyBusinessRow>();
        if (wanted.contains("RESERVATION") || wanted.contains("LOAN")) {
            var statuses = new ArrayList<String>();
            if (wanted.contains("RESERVATION")) {
                statuses.addAll(List.of("AWAIT", "CONFIRMED", "WAITLIST", "MANUAL_HANDLING", "NOT_BORROWED"));
            }
            if (wanted.contains("LOAN")) {
                statuses.addAll(List.of("LENT", "RETURNED", "RETURN_ABNORMAL"));
            }
            rows.addAll(jdbc.query("""
                    SELECT CASE WHEN t.status IN ('LENT', 'RETURNED', 'RETURN_ABNORMAL')
                                THEN 'LOAN' ELSE 'RESERVATION' END AS kind,
                           t.id, t.status, bt.title AS title,
                           t.reservation_date AS business_date, NULL::bigint AS amount_cents
                    FROM borrow_transaction t
                    LEFT JOIN book_title bt ON bt.id = t.title_id
                    WHERE t.reader_id = ? AND t.id < ? AND t.status = ANY(?)
                    """, BUSINESS, readerId, beforeId,
                    statuses.toArray(new String[0])));
        }
        if (wanted.contains("FINE")) {
            rows.addAll(jdbc.query("""
                    SELECT 'FINE' AS kind, f.id, CASE f.status WHEN 'DUE' THEN 'UNPAID' ELSE f.status END AS status,
                           NULL AS title, NULL::date AS business_date, f.amount_cents
                    FROM fine f WHERE f.reader_id = ? AND f.id < ?
                    """, BUSINESS, readerId, beforeId));
        }
        var filtered = rows.stream()
                .filter(r -> status == null || status.isBlank() || status.equals(r.status()))
                .sorted(java.util.Comparator.comparingLong(MyBusinessRow::id).reversed())
                .limit(limit)
                .toList();
        return filtered;
    }
}
