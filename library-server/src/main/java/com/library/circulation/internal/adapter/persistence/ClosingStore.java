package com.library.circulation.internal.adapter.persistence;

import java.time.LocalDate;
import java.util.Optional;
import java.util.OptionalLong;

public interface ClosingStore {
    Optional<LocalDate> firstUnclosedDateThrough(LocalDate inclusiveDate);
    boolean requiresManualRecovery(LocalDate date);
    void beginDraining(LocalDate date);
    String isolateAndRun(LocalDate date);
    Failure markFailure(LocalDate date);
    boolean caughtUpThrough(LocalDate date);
    OptionalLong reopen(LocalDate requestedThroughDate);
    record Failure(int attempts,int retryBudget,String status) {}
}
