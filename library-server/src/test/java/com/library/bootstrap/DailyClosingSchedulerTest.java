package com.library.bootstrap;

import com.library.circulation.contract.ClosingCommands;
import com.library.circulation.contract.ClosingStatusQuery;
import com.library.contract.ModuleResult;
import com.library.contract.time.BusinessClock;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DailyClosingSchedulerTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);
    private static final BusinessClock CLOCK = new BusinessClock() {
        @Override public Instant now() { return Instant.parse("2026-10-08T04:00:00Z"); }
        @Override public LocalDate today() { return TODAY; }
    };

    @Test
    void startupChecksAndClosesMissingDatesBeforeReopening() {
        var closing = new RecordingClosing(TODAY.minusDays(2), TODAY.minusDays(1));
        try (var context = new GenericApplicationContext()) {
            context.registerBean(DailyClosingScheduler.class,
                    () -> new DailyClosingScheduler(closing, closing, CLOCK));
            context.refresh();
            assertThat(closing.actions).containsExactly("close:2026-10-06", "close:2026-10-07", "reopen:2026-10-07");
        }
    }

    @Test
    void scheduledCloseAutomaticallyReopensAfterAllDatesSucceed() {
        var closing = new RecordingClosing(TODAY.minusDays(2), TODAY.minusDays(1));
        new DailyClosingScheduler(closing, closing, CLOCK).closePreviousBusinessDate();
        assertThat(closing.actions).containsExactly("close:2026-10-06", "close:2026-10-07", "reopen:2026-10-07");
    }

    @Test
    void failedBatchDoesNotProcessLaterDatesOrReopen() {
        var closing = new RecordingClosing(TODAY.minusDays(2), TODAY.minusDays(1));
        closing.rejectedDate = TODAY.minusDays(2);
        new DailyClosingScheduler(closing, closing, CLOCK).closePreviousBusinessDate();
        assertThat(closing.actions).containsExactly("close:2026-10-06");
    }

    @Test
    void incompleteBatchDoesNotReopen() {
        var closing = new RecordingClosing(TODAY.minusDays(1));
        closing.batchStatus = "MANUAL";
        new DailyClosingScheduler(closing, closing, CLOCK).closePreviousBusinessDate();
        assertThat(closing.actions).containsExactly("close:2026-10-07");
    }

    @Test
    void repeatedChecksDoNotRerunSuccessfulBatches() {
        var closing = new RecordingClosing(TODAY.minusDays(1));
        var scheduler = new DailyClosingScheduler(closing, closing, CLOCK);
        scheduler.closePreviousBusinessDate();
        scheduler.closePreviousBusinessDate();
        assertThat(closing.actions).containsExactly("close:2026-10-07", "reopen:2026-10-07", "reopen:2026-10-07");
    }

    @Test
    void alreadySettledIsolationCanReopenWithoutRerunningBatches() {
        var closing = new RecordingClosing();
        new DailyClosingScheduler(closing, closing, CLOCK).closePreviousBusinessDate();
        assertThat(closing.actions).containsExactly("reopen:2026-10-07");
    }

    @Test
    void reopenFailureLeavesTheRecoveryOutcomeClosed() {
        var closing = new RecordingClosing(TODAY.minusDays(1));
        closing.rejectReopen = true;
        new DailyClosingScheduler(closing, closing, CLOCK).closePreviousBusinessDate();
        assertThat(closing.open).isFalse();
    }

    @Test
    void startupDoesNotRetryABatchAlreadyAssignedToManualRecovery() {
        var closing = new RecordingClosing(TODAY.minusDays(2), TODAY.minusDays(1));
        closing.manualDate = TODAY.minusDays(2);
        new DailyClosingScheduler(closing, closing, CLOCK).closePreviousBusinessDate();
        assertThat(closing.actions).isEmpty();
    }

    @Test
    void catchUpCrossingMidnightIncludesTheNewlyDueDateBeforeReopening() {
        var now = new AtomicReference<>(Instant.parse("2026-10-07T15:59:59Z"));
        BusinessClock changingClock = now::get;
        var closing = new RecordingClosing(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7));
        closing.afterClose = () -> now.set(Instant.parse("2026-10-07T16:00:01Z"));
        new DailyClosingScheduler(closing, closing, changingClock).closePreviousBusinessDate();
        assertThat(closing.actions).containsExactly("close:2026-10-06", "close:2026-10-07", "reopen:2026-10-07");
    }

    @Test
    void midnightDuringReopenIsCheckedBeforeStartupCompletes() {
        var now = new AtomicReference<>(Instant.parse("2026-10-07T15:59:59Z"));
        BusinessClock changingClock = now::get;
        var closing = new RecordingClosing(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7));
        closing.afterReopen = () -> now.set(Instant.parse("2026-10-07T16:00:01Z"));
        new DailyClosingScheduler(closing, closing, changingClock).closePreviousBusinessDate();
        assertThat(closing.actions).containsExactly("close:2026-10-06", "reopen:2026-10-06",
                "close:2026-10-07", "reopen:2026-10-07");
    }

    private static final class RecordingClosing implements ClosingCommands, ClosingStatusQuery {
        private final TreeSet<LocalDate> missing = new TreeSet<>();
        private final List<String> actions = new ArrayList<>();
        private LocalDate rejectedDate;
        private String batchStatus = "SUCCEEDED";
        private boolean rejectReopen;
        private boolean open;
        private LocalDate manualDate;
        private Runnable afterClose = () -> {};
        private Runnable afterReopen = () -> {};

        private RecordingClosing(LocalDate... dates) { missing.addAll(List.of(dates)); }
        @Override public Optional<LocalDate> firstUnclosedDateThrough(LocalDate date) {
            return missing.stream().filter(d -> !d.isAfter(date)).findFirst();
        }
        public boolean requiresManualRecovery(LocalDate date) { return date.equals(manualDate); }
        @Override public ModuleResult<ClosingRun> closeBusinessDate(ClosingCommand command) {
            var date = command.businessDate().value();
            actions.add("close:" + date);
            if (date.equals(rejectedDate)) return ModuleResult.Rejected.of("MLS-CIRC-014", "batch failed");
            if ("SUCCEEDED".equals(batchStatus)) missing.remove(date);
            afterClose.run();
            return ModuleResult.Accepted.of(new ClosingRun(date.toEpochDay(), batchStatus));
        }
        @Override public ModuleResult<ReopenedDate> reopen(ReopenCommand command) {
            actions.add("reopen:" + command.throughDate().value());
            assertThat(missing.headSet(command.throughDate().value(), true)).isEmpty();
            if (rejectReopen) return ModuleResult.Rejected.of("MLS-CIRC-008", "reopen rejected");
            open = true;
            afterReopen.run();
            return ModuleResult.Accepted.of(new ReopenedDate("REOPENED", 1));
        }
    }
}
