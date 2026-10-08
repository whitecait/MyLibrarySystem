package com.library.bootstrap;

import com.library.contract.ModuleResult;
import com.library.contract.time.BusinessClock;
import com.library.circulation.contract.ClosingCommands;
import com.library.circulation.contract.ClosingStatusQuery;
import com.library.circulation.contract.model.BusinessDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Check missed dates before startup completes and reopen only after every due batch succeeds. */
@Component
@ConditionalOnProperty(prefix = "library.closing", name = "daily-enabled", havingValue = "true")
public final class DailyClosingScheduler implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(DailyClosingScheduler.class);

    private final ClosingCommands closing;
    private final ClosingStatusQuery status;
    private final BusinessClock clock;

    public DailyClosingScheduler(ClosingCommands closing, ClosingStatusQuery status, BusinessClock clock) {
        this.closing = closing;
        this.status = status;
        this.clock = clock;
    }

    @Override
    public void afterSingletonsInstantiated() {
        log.info("Startup daily close check started");
        closePreviousBusinessDate();
    }

    @Scheduled(cron = "${library.closing.daily-cron:0 0 0 * * *}",
            zone = "${library.closing.time-zone:Asia/Shanghai}")
    public synchronized void closePreviousBusinessDate() {
        while (true) {
            var yesterday = clock.today().minusDays(1);
            var next = status.firstUnclosedDateThrough(yesterday);
            if (next.isEmpty()) {
                // Catch-up can cross midnight before startup has registered the next cron trigger.
                if (!yesterday.equals(clock.today().minusDays(1))) continue;
                var reopened = closing.reopen(new ClosingCommands.ReopenCommand(BusinessDate.of(yesterday)));
                if (reopened instanceof ModuleResult.Rejected<ClosingCommands.ReopenedDate> rejected) {
                    log.error("Automatic reopen rejected throughBusinessDate={} code={} reasons={}; administrator recovery is available",
                            yesterday, rejected.code(), rejected.reasons());
                    return;
                }
                if (!yesterday.equals(clock.today().minusDays(1))) continue;
                log.info("Daily close caught up through businessDate={}; ordinary writes reopened automatically", yesterday);
                return;
            }

            var date = next.get();
            if (status.requiresManualRecovery(date)) {
                log.error("Automatic daily close stopped businessDate={}; retry budget exhausted, administrator recovery is required", date);
                return;
            }
            var outcome = closing.closeBusinessDate(new ClosingCommands.ClosingCommand(BusinessDate.of(date)));
            if (outcome instanceof ModuleResult.Rejected<ClosingCommands.ClosingRun> rejected) {
                log.error("Daily close rejected businessDate={} code={} reasons={}", date, rejected.code(), rejected.reasons());
                return;
            }
            var run = ((ModuleResult.Accepted<ClosingCommands.ClosingRun>) outcome).value();
            if (!"SUCCEEDED".equals(run.status())) {
                log.error("Daily close did not succeed businessDate={} status={}", date, run.status());
                return;
            }
            log.info("Daily close completed businessDate={} status={}", date, run.status());
        }
    }
}
