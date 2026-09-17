package com.library.settlement.contract;

import com.library.contract.ModuleResult;
import com.library.contract.model.PageCursor;

import java.time.Instant;
import java.util.List;

public interface FineQuery {
    ModuleResult<FinePage> fines(FineCriteria query);

    record FineCriteria(Long readerId, String status, PageCursor page) {}
    record FineView(long fineId, long readerId, long amountCents, String status, Instant createdAt) {}
    record FinePage(List<FineView> items, String nextCursor) {}
}
