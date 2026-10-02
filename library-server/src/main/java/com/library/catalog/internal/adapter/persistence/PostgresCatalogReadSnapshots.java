package com.library.catalog.internal.adapter.persistence;

import com.library.catalog.contract.CatalogReadSnapshots;
import com.library.catalog.contract.model.TitleId;
import com.library.contract.ModuleResult;
import com.library.contract.InfrastructureFailure;
import org.springframework.dao.DataAccessException;
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
    public ModuleResult<Map<TitleId, String>> titleNames(List<TitleId> ids) {
        if (ids.isEmpty()) { return ModuleResult.Accepted.of(Map.of()); }
        try {
            var names = new HashMap<TitleId, String>();
            jdbc.query("SELECT id, title FROM book_title WHERE id=ANY(?)",
                    (org.springframework.jdbc.core.RowCallbackHandler) rs -> names.put(TitleId.of(rs.getLong("id")), rs.getString("title")),
                    (Object) ids.stream().map(TitleId::value).toArray(Long[]::new));
            return ModuleResult.Accepted.of(Map.copyOf(names));
        } catch (DataAccessException failure) {
            throw new InfrastructureFailure("书名快照读取失败", failure);
        }
    }
}
