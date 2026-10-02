package com.library.circulation.contract;

import java.time.LocalDate;
import java.util.OptionalLong;

/** Reader-scoped circulation facts used to serialize and evaluate loan eligibility. */
public interface ReaderCirculationQuery {

    /** Acquires a transaction advisory lock. Call inside the same UnitOfWork as the write. */
    void lockReader(long readerId);

    /** Acquires the title reservation queue lock; call after the reader lock in the same UnitOfWork. */
    void lockTitleReservations(long titleId);

    ReaderActivity activity(long readerId, LocalDate onDate);

    OptionalLong readerForTransaction(long txId);

    OptionalLong titleForTransaction(long txId);

    OptionalLong readerForOpenCopy(long copyId);

    record ReaderActivity(int activeLoanCount, int activeReservationCount, boolean hasOverdueLoan) {}
}
