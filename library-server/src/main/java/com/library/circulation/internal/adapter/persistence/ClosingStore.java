package com.library.circulation.internal.adapter.persistence;

import java.time.LocalDate;

public interface ClosingStore {
    void beginDraining(LocalDate date);
    String isolateAndRun(LocalDate date);
    Failure markFailure(LocalDate date);
    boolean caughtUpThrough(LocalDate date);
    long reopen(LocalDate date);
    record Failure(int attempts,int retryBudget,String status) {}
}
