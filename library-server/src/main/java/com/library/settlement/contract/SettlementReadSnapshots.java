package com.library.settlement.contract;

import com.library.identity.contract.model.ReaderId;
import com.library.settlement.contract.model.FineId;
import com.library.settlement.contract.model.MoneyCents;

import com.library.contract.ModuleResult;

import java.util.List;

/** Read projection for the existing combined business list; no settlement commands are exposed. */
public interface SettlementReadSnapshots {
    ModuleResult<List<FineSnapshot>> readerFines(ReaderId readerId, String status, FineId atOrBeforeId, int limit);
    record FineSnapshot(FineId id, String status, MoneyCents amountCents) {}
}
