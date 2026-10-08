package com.library.circulation.contract;

import java.time.LocalDate;
import java.util.Optional;

/** Finds the earliest missing or unsuccessful close date through an inclusive target date. */
public interface ClosingStatusQuery {
    Optional<LocalDate> firstUnclosedDateThrough(LocalDate inclusiveDate);

    /** True when this date has exhausted automatic recovery and needs controlled manual retry. */
    boolean requiresManualRecovery(LocalDate businessDate);
}
