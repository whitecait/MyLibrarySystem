package com.library.catalog.internal.adapter.persistence;

import com.library.catalog.contract.CatalogReadSnapshots;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class PostgresCatalogReadSnapshots implements CatalogReadSnapshots {
    private final JdbcTemplate jdbc;
    public PostgresCatalogReadSnapshots(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override
    public Map<Long, String> titleNames(List<Long> ids) {
        if (ids.isEmpty()) { return Map.of(); }
        var names = new HashMap<Long, String>();
        jdbc.query("SELECT id, title FROM book_title WHERE id=ANY(?)",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> names.put(rs.getLong("id"), rs.getString("title")),
                (Object) ids.toArray(new Long[0]));
        return Map.copyOf(names);
    }
}
