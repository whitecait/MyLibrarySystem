package com.library.circulation.internal.adapter.persistence;

import com.library.circulation.contract.CirculationReadSnapshots;
import com.library.catalog.contract.model.TitleId;
import com.library.circulation.contract.model.BusinessDate;
import com.library.circulation.contract.model.CopyId;
import com.library.circulation.contract.model.GuaranteeType;
import com.library.circulation.contract.model.TxId;
import com.library.identity.contract.model.ReaderId;
import com.library.contract.ModuleResult;
import com.library.contract.InfrastructureFailure;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

@Repository
public class PostgresCirculationReadSnapshots implements CirculationReadSnapshots {
    private final JdbcTemplate jdbc;
    public PostgresCirculationReadSnapshots(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public ModuleResult<ArrivalCounts> arrivals(BusinessDate date) {
        try {
            return ModuleResult.Accepted.of(jdbc.queryForObject("""
                    SELECT count(*) FILTER (WHERE status IN ('CONFIRMED','LENT','MANUAL_HANDLING')) AS expected,
                           count(*) FILTER (WHERE status='LENT') AS arrived,
                           count(*) FILTER (WHERE status='CONFIRMED') AS pending,
                           count(*) FILTER (WHERE status='MANUAL_HANDLING') AS exceptions
                    FROM borrow_transaction WHERE reservation_date=?
                    """, (rs, i) -> new ArrivalCounts(rs.getInt("expected"), rs.getInt("arrived"),
                    rs.getInt("pending"), rs.getInt("exceptions")), Date.valueOf(date.value())));
        } catch (DataAccessException failure) {
            throw new InfrastructureFailure("到馆计数读取失败", failure);
        }
    }

    @Override
    public ModuleResult<List<TransactionSnapshot>> readerTransactions(ReaderId reader, List<String> kinds,
                                                         List<String> requestedStatuses,
                                                         TxId before, int limit) {
        var statuses = new ArrayList<String>();
        if (kinds.contains("RESERVATION")) {
            statuses.addAll(List.of("AWAIT", "CONFIRMED", "WAITLIST", "MANUAL_HANDLING", "NOT_BORROWED"));
        }
        if (kinds.contains("LOAN")) { statuses.addAll(List.of("LENT", "RETURNED", "RETURN_ABNORMAL")); }
        if (requestedStatuses != null) {
            statuses.retainAll(requestedStatuses);
        }
        if (statuses.isEmpty()) { return ModuleResult.Accepted.of(List.of()); }
        try {
            return ModuleResult.Accepted.of(jdbc.query("""
                    SELECT id, title_id, status, reservation_date, pickup_slot, copy_id, guarantee_type,
                           CASE WHEN status IN ('LENT','RETURNED','RETURN_ABNORMAL')
                                THEN 'LOAN' ELSE 'RESERVATION' END AS kind
                    FROM borrow_transaction
                    WHERE reader_id=? AND id<=? AND status=ANY(?)
                    ORDER BY id DESC LIMIT ?
                    """, (rs, i) -> new TransactionSnapshot(rs.getString("kind"), TxId.of(rs.getLong("id")),
                    rs.getString("status"), TitleId.of(rs.getLong("title_id")),
                    rs.getDate("reservation_date") == null ? null : BusinessDate.of(rs.getDate("reservation_date").toLocalDate()),
                    rs.getString("pickup_slot"),
                    rs.getObject("copy_id") == null ? null : CopyId.of(rs.getLong("copy_id")),
                    rs.getString("guarantee_type") == null ? null
                            : GuaranteeType.valueOf(rs.getString("guarantee_type"))),
                    reader.value(), before.value(), statuses.toArray(new String[0]), limit));
        } catch (DataAccessException failure) {
            throw new InfrastructureFailure("本人业务快照读取失败", failure);
        }
    }
}
