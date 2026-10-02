package com.library.catalog.internal.adapter.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PostgresCatalogStore implements CatalogStore {

    private final JdbcTemplate jdbc;

    private static final RowMapper<TitleRow> ROW = (rs, i) -> new TitleRow(
            rs.getLong("id"), rs.getString("title"), rs.getString("author"),
            rs.getString("publisher"), rs.getString("category"), rs.getString("isbn"),
            rs.getObject("publish_year") == null ? null : rs.getInt("publish_year"),
            rs.getLong("price_cents"), rs.getLong("version"), rs.getString("availability"));

    private static final String TITLE_WITH_AVAILABILITY = """
            SELECT t.id, t.title, t.author, t.publisher, t.category, t.isbn, t.publish_year,
                   t.price_cents, t.version,
                   CASE WHEN EXISTS (SELECT 1 FROM book_copy c WHERE c.title_id = t.id
                                      AND c.status = 'SHELF_READY') THEN 'AVAILABLE'
                        WHEN EXISTS (SELECT 1 FROM book_copy c WHERE c.title_id = t.id) THEN 'UNAVAILABLE'
                        ELSE 'UNKNOWN' END AS availability
            FROM book_title t
            """;

    public PostgresCatalogStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<TitleRow> byId(long titleId) {
        return jdbc.query(TITLE_WITH_AVAILABILITY + " WHERE t.id = ?", ROW, titleId).stream().findFirst();
    }

    @Override
    public boolean isbnExistsExcept(long titleId, String isbn) {
        var count = jdbc.queryForObject(
                "SELECT count(*) FROM book_title WHERE isbn = ? AND id <> ?", Integer.class, isbn, titleId);
        return count != null && count > 0;
    }

    @Override
    public List<TitleRow> searchByQuery(String q, String category, String availability,
                                        Double afterSimilarity, long afterId, int limit) {
        var sql = TITLE_WITH_AVAILABILITY.replace("FROM book_title t",
                        "     , similarity(t.title, ?) AS sim\nFROM book_title t") + """
                WHERE (t.title % ? OR t.title ILIKE '%' || ? || '%')
                  AND (?::text IS NULL OR t.category = ?::text)
                  AND (?::text IS NULL OR CASE
                       WHEN EXISTS (SELECT 1 FROM book_copy c WHERE c.title_id = t.id
                                      AND c.status = 'SHELF_READY') THEN 'AVAILABLE'
                       WHEN EXISTS (SELECT 1 FROM book_copy c WHERE c.title_id = t.id) THEN 'UNAVAILABLE'
                       ELSE 'UNKNOWN' END = ?::text)
                  AND ((?::float8 IS NULL AND t.id > ?::bigint)
                       OR (?::float8 IS NOT NULL
                           AND (similarity(t.title, ?), t.id) < (?::float8, ?::bigint)))
                ORDER BY sim DESC NULLS LAST, t.id ASC
                LIMIT ?
                """;
        return jdbc.query(sql, ROW, q, q, q, category, category, availability, availability,
                afterSimilarity, afterId, afterSimilarity, q, afterSimilarity, afterId, limit);
    }

    @Override
    public List<TitleRow> list(String category, String availability, long afterId, int limit) {
        var sql = TITLE_WITH_AVAILABILITY + """
                WHERE (?::text IS NULL OR t.category = ?::text)
                  AND (?::text IS NULL OR CASE
                       WHEN EXISTS (SELECT 1 FROM book_copy c WHERE c.title_id = t.id
                                      AND c.status = 'SHELF_READY') THEN 'AVAILABLE'
                       WHEN EXISTS (SELECT 1 FROM book_copy c WHERE c.title_id = t.id) THEN 'UNAVAILABLE'
                       ELSE 'UNKNOWN' END = ?::text)
                  AND t.id > ?
                ORDER BY t.id ASC
                LIMIT ?
                """;
        return jdbc.query(sql, ROW, category, category, availability, availability, afterId, limit);
    }

    @Override
    public long insert(NewTitle candidate) {
        return jdbc.queryForObject("""
                INSERT INTO book_title (isbn, title, author, publisher, category, publish_year, price_cents)
                VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, candidate.isbn(), candidate.title(), candidate.authorsJoined(),
                candidate.publisher(), candidate.category(), candidate.publishYear(), candidate.priceCents());
    }

    @Override
    public Long update(TitleUpdate u) {
        List<Long> updated = jdbc.queryForList("""
                UPDATE book_title SET
                    isbn        = COALESCE(?, isbn),
                    title       = COALESCE(?, title),
                    author      = COALESCE(?, author),
                    publisher   = COALESCE(?, publisher),
                    category    = COALESCE(?, category),
                    publish_year = COALESCE(?, publish_year),
                    price_cents = COALESCE(?, price_cents),
                    version = version + 1, updated_at = now()
                WHERE id = ? AND version = ?
                RETURNING version
                """, Long.class, u.isbn(), u.title(), u.authorsJoined(), u.category(),
                u.publisher(), u.publishYear(), u.priceCents(), u.titleId(), u.version());
        return updated.isEmpty() ? null : updated.get(0);
    }

    @Override
    public boolean delete(long titleId, long version) {
        return jdbc.update("DELETE FROM book_title WHERE id = ? AND version = ?", titleId, version) == 1;
    }

    @Override
    public boolean hasCopies(long titleId) {
        var count = jdbc.queryForObject(
                "SELECT count(*) FROM book_copy WHERE title_id = ?", Integer.class, titleId);
        return count != null && count > 0;
    }

    @Override
    public boolean hasTransactions(long titleId) {
        var count = jdbc.queryForObject(
                "SELECT count(*) FROM borrow_transaction WHERE title_id = ?", Integer.class, titleId);
        return count != null && count > 0;
    }

    @Override
    public boolean hasContent(long titleId) {
        var count = jdbc.queryForObject(
                "SELECT count(*) FROM book_title_content_page WHERE title_id = ?", Integer.class, titleId);
        return count != null && count > 0;
    }

    @Override
    public List<ContentPageRow> contentPages(long titleId, int afterPageNo, int limit) {
        return jdbc.query("""
                SELECT page_no, content_type, text FROM book_title_content_page
                WHERE title_id = ? AND page_no > ? ORDER BY page_no LIMIT ?
                """, (rs, i) -> new ContentPageRow(rs.getInt("page_no"), rs.getString("content_type"),
                rs.getString("text")), titleId, afterPageNo, limit);
    }
}
