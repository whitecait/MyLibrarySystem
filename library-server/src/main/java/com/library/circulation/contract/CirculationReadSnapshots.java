package com.library.circulation.contract;

import com.library.catalog.contract.model.TitleId;
import com.library.circulation.contract.model.BusinessDate;
import com.library.circulation.contract.model.TxId;
import com.library.identity.contract.model.ReaderId;
import com.library.contract.ModuleResult;

import java.util.List;

/** Narrow read projections owned by CIRCULATION; callers do not interpret its tables. */
public interface CirculationReadSnapshots {
    /** Existing basic transaction counts, not the deferred complete arrival-person statistics. */
    ModuleResult<ArrivalCounts> arrivals(BusinessDate date);
    ModuleResult<List<TransactionSnapshot>> readerTransactions(ReaderId readerId, List<String> kinds, String status,
                                                 TxId atOrBeforeId, int limit);
    record ArrivalCounts(int expected, int arrived, int pending, int exceptions) {}
    record TransactionSnapshot(String kind, TxId id, String status, TitleId titleId,
                               BusinessDate businessDate) {}
}
