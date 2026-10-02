package com.library.settlement.contract;

import java.util.List;

/** Read projection for the existing combined business list; no settlement commands are exposed. */
public interface SettlementReadSnapshots {
    List<FineSnapshot> readerFines(long readerId, String status, long beforeId, int limit);
    record FineSnapshot(long id, String status, long amountCents) {}
}
