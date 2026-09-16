package com.library.circulation.internal.adapter.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class PostgresCirculationStore implements CirculationStore {

    private final JdbcTemplate jdbc;

    private static final RowMapper<CopyRow> COPY = (rs, i) -> new CopyRow(
            rs.getLong("id"), rs.getLong("title_id"), rs.getString("copy_no"),
            rs.getString("status"), rs.getLong("version"));

    private static final RowMapper<TxRow> TX = (rs, i) -> new TxRow(
            rs.getLong("id"), rs.getLong("reader_id"), rs.getLong("title_id"),
            rs.getObject("copy_id") == null ? null : rs.getLong("copy_id"),
            rs.getString("status"),
            rs.getDate("reservation_date").toLocalDate(),
            rs.getString("pickup_slot"),
            rs.getDate("due_date") == null ? null : rs.getDate("due_date").toLocalDate(),
            rs.getInt("renewal_count"), rs.getLong("version"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant());

    public PostgresCirculationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean copyNoExists(long titleId, String copyNo) {
        var count = jdbc.queryForObject(
                "SELECT count(*) FROM book_copy WHERE title_id = ? AND copy_no = ?",
                Integer.class, titleId, copyNo);
        return count != null && count > 0;
    }

    @Override
    public long insertCopy(long titleId, String copyNo) {
        return jdbc.queryForObject("""
                INSERT INTO book_copy (title_id, copy_no) VALUES (?, ?) RETURNING id
                """, Long.class, titleId, copyNo);
    }

    @Override
    public Optional<CopyRow> copyById(long copyId) {
        return jdbc.query("SELECT * FROM book_copy WHERE id = ?", COPY, copyId).stream().findFirst();
    }

    @Override
    public List<CopyRow> listCopies(Long titleId, String status, long afterId, int limit) {
        var sql = new StringBuilder("SELECT * FROM book_copy WHERE id > ?");
        if (titleId != null) {
            sql.append(" AND title_id = ?");
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
        }
        sql.append(" ORDER BY id LIMIT ?");
        var args = new java.util.ArrayList<Object>();
        args.add(afterId);
        if (titleId != null) {
            args.add(titleId);
        }
        if (status != null && !status.isBlank()) {
            args.add(status);
        }
        args.add(limit);
        return jdbc.query(sql.toString(), COPY, args.toArray());
    }

    @Override
    public List<CopyRow> availableCopies(long titleId, int limit) {
        return jdbc.query("""
                SELECT * FROM book_copy WHERE title_id = ? AND status = 'SHELF_READY'
                ORDER BY id LIMIT ?
                """, COPY, titleId, limit);
    }

    @Override
    public Long transitionCopy(long copyId, String expectedStatus, String nextStatus, long version) {
        List<Long> updated = jdbc.queryForList("""
                UPDATE book_copy SET status = ?, version = version + 1, updated_at = now()
                WHERE id = ? AND status = ? AND version = ?
                RETURNING version
                """, Long.class, nextStatus, copyId, expectedStatus, version);
        return updated.isEmpty() ? null : updated.get(0);
    }

    @Override
    public boolean hasOpenTransactionForReaderTitle(long readerId, long titleId) {
        var count = jdbc.queryForObject("""
                SELECT count(*) FROM borrow_transaction
                WHERE reader_id = ? AND title_id = ?
                  AND status IN ('AWAIT', 'CONFIRMED', 'MANUAL_HANDLING', 'WAITLIST', 'LENT')
                """, Integer.class, readerId, titleId);
        return count != null && count > 0;
    }

    @Override
    public long insertTransaction(NewTransaction candidate) {
        return jdbc.queryForObject("""
                INSERT INTO borrow_transaction (reader_id, title_id, copy_id, status,
                                                reservation_date, pickup_slot, due_date)
                VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, candidate.readerId(), candidate.titleId(), candidate.copyId(),
                candidate.status(), Date.valueOf(candidate.reservationDate()), candidate.pickupSlot(),
                candidate.dueDate() == null ? null : Date.valueOf(candidate.dueDate()));
    }

    @Override
    public Optional<TxRow> txById(long txId) {
        return jdbc.query("SELECT * FROM borrow_transaction WHERE id = ?", TX, txId).stream().findFirst();
    }

    @Override
    public Optional<TxRow> openLentByCopy(long copyId) {
        return jdbc.query("""
                SELECT * FROM borrow_transaction WHERE copy_id = ? AND status = 'LENT'
                """, TX, copyId).stream().findFirst();
    }

    @Override
    public List<TxRow> waitlistByTitle(long titleId, int limit) {
        return jdbc.query("""
                SELECT * FROM borrow_transaction WHERE title_id = ? AND status = 'WAITLIST'
                ORDER BY queued_at ASC, id ASC LIMIT ?
                """, TX, titleId, limit);
    }

    @Override
    public Long transitionTransaction(long txId, String expectedStatus, String nextStatus, long version) {
        List<Long> updated = jdbc.queryForList("""
                UPDATE borrow_transaction SET status = ?, version = version + 1
                WHERE id = ? AND status = ? AND version = ?
                RETURNING version
                """, Long.class, nextStatus, txId, expectedStatus, version);
        return updated.isEmpty() ? null : updated.get(0);
    }

    @Override
    public Long renewTransaction(long txId, LocalDate newDueDate, long version) {
        List<Long> updated = jdbc.queryForList("""
                UPDATE borrow_transaction SET due_date = ?, renewal_count = renewal_count + 1,
                       version = version + 1
                WHERE id = ? AND status = 'LENT' AND version = ?
                RETURNING version
                """, Long.class, Date.valueOf(newDueDate), txId, version);
        return updated.isEmpty() ? null : updated.get(0);
    }

    @Override
    public void attachCopy(long txId, Long copyId) {
        jdbc.update("UPDATE borrow_transaction SET copy_id = ? WHERE id = ?", copyId, txId);
    }

    @Override
    public void setDueDate(long txId, LocalDate dueDate) {
        jdbc.update("UPDATE borrow_transaction SET due_date = ? WHERE id = ?",
                Date.valueOf(dueDate));
    }

    @Override
    public int countAvailableCopies(long titleId) {
        var count = jdbc.queryForObject("""
                SELECT count(*) FROM book_copy WHERE title_id = ? AND status = 'SHELF_READY'
                """, Integer.class, titleId);
        return count == null ? 0 : count;
    }

    @Override
    public int countCopies(long titleId) {
        var count = jdbc.queryForObject(
                "SELECT count(*) FROM book_copy WHERE title_id = ?", Integer.class, titleId);
        return count == null ? 0 : count;
    }

    @Override
    public int countConfirmedForDate(long titleId, LocalDate date) {
        var count = jdbc.queryForObject("""
                SELECT count(*) FROM borrow_transaction
                WHERE title_id = ? AND reservation_date = ? AND status IN ('CONFIRMED', 'LENT')
                """, Integer.class, titleId, Date.valueOf(date));
        return count == null ? 0 : count;
    }

    @Override
    public int countExpectedForDate(LocalDate date) {
        var count = jdbc.queryForObject("""
                SELECT count(*) FROM borrow_transaction
                WHERE reservation_date = ? AND status IN ('CONFIRMED', 'LENT', 'MANUAL_HANDLING')
                """, Integer.class, Date.valueOf(date));
        return count == null ? 0 : count;
    }

    @Override
    public int countManualForDate(LocalDate date) {
        var count = jdbc.queryForObject("""
                SELECT count(*) FROM borrow_transaction
                WHERE reservation_date = ? AND status = 'MANUAL_HANDLING'
                """, Integer.class, Date.valueOf(date));
        return count == null ? 0 : count;
    }

    @Override
    public List<TxRow> myTransactions(long readerId, List<String> statuses, long afterId, int limit) {
        var placeholders = String.join(",", java.util.Collections.nCopies(statuses.size(), "?"));
        var sql = "SELECT * FROM borrow_transaction WHERE reader_id = ? AND id > ? AND status IN ("
                + placeholders + ") ORDER BY id DESC LIMIT ?";
        var args = new java.util.ArrayList<Object>();
        args.add(readerId);
        args.add(afterId);
        args.addAll(statuses);
        args.add(limit);
        return jdbc.query(sql, TX, args.toArray());
    }
}
