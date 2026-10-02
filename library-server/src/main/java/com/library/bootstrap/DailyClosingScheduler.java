package com.library.bootstrap;

import com.library.contract.ModuleResult;
import com.library.contract.time.BusinessClock;
import com.library.circulation.contract.ClosingCommands;
import com.library.circulation.contract.ClosingStatusQuery;
import com.library.circulation.contract.model.BusinessDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Deployment-controlled daily close trigger; reopening remains an explicit administrator action. */
@Component
@ConditionalOnProperty(prefix = "library.closing", name = "daily-enabled", havingValue = "true")
public final class DailyClosingScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyClosingScheduler.class);

    private final ClosingCommands closing;
    private final ClosingStatusQuery status;
    private final BusinessClock clock;

    public DailyClosingScheduler(ClosingCommands closing, ClosingStatusQuery status, BusinessClock clock) {
        this.closing = closing;
        this.status = status;
        this.clock = clock;
    }

    @Scheduled(cron = "${library.closing.daily-cron:0 0 0 * * *}",
            zone = "${library.closing.time-zone:Asia/Shanghai}")
    public void closePreviousBusinessDate() {
        var today = clock.today();
        var yesterday = today.minusDays(1);
        var next = status.firstUnclosedDateThrough(yesterday);
        while (next.isPresent()) {
            var date = next.get();
            var outcome = closing.closeBusinessDate(new ClosingCommands.ClosingCommand(BusinessDate.of(date)));
            if (outcome instanceof ModuleResult.Rejected<ClosingCommands.ClosingRun> rejected) {
                log.error("Scheduled daily close rejected businessDate={} code={} reasons={}",
                        date, rejected.code(), rejected.reasons());
                return;
            }
            var run = ((ModuleResult.Accepted<ClosingCommands.ClosingRun>) outcome).value();
            if (!"SUCCEEDED".equals(run.status())) {
                log.error("Scheduled daily close did not succeed businessDate={} status={}", date, run.status());
                return;
            }
            log.info("Scheduled daily close completed businessDate={} status={}", date, run.status());
            next = status.firstUnclosedDateThrough(yesterday);
        }

        log.info("Scheduled daily close caught up through businessDate={}; administrator reopen is required",
                yesterday);
    }
}
