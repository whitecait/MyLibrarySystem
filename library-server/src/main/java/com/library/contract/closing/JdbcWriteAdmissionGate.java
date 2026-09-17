package com.library.contract.closing;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class JdbcWriteAdmissionGate implements WriteAdmissionGate {
    private final JdbcTemplate jdbc;
    public JdbcWriteAdmissionGate(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Override public boolean acquire(String path){if(path.startsWith("/api/v1/closing/"))return true;try{var rows=jdbc.queryForList("SELECT c.status,a.draining FROM closing_control c JOIN closing_admission a ON a.scope=c.scope WHERE c.scope='GLOBAL' FOR SHARE OF c,a NOWAIT");if(rows.isEmpty())return false;var row=rows.getFirst();return "OPEN".equals(row.get("status"))&&!Boolean.TRUE.equals(row.get("draining"));}catch(DataAccessException e){return false;}}
}
