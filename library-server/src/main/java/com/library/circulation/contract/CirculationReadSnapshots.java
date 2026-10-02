package com.library.circulation.contract;

import java.time.LocalDate;
import java.util.List;

/** Narrow read projections owned by CIRCULATION; callers do not interpret its tables. */
public interface CirculationReadSnapshots {
    /** Existing basic transaction counts, not the deferred complete arrival-person statistics. */
    ArrivalCounts arrivals(LocalDate date);
    List<TransactionSnapshot> readerTransactions(long readerId, List<String> kinds, String status,
                                                 long beforeId, int limit);
    record ArrivalCounts(int expected, int arrived, int pending, int exceptions) {}
    record TransactionSnapshot(String kind, long id, String status, long titleId,
                               LocalDate businessDate) {}
}
