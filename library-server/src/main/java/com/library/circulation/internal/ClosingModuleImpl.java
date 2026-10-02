package com.library.circulation.internal;

import com.library.contract.ModuleResult;
import com.library.contract.error.StableErrorCode;
import com.library.circulation.contract.ClosingCommands;
import com.library.circulation.contract.ClosingStatusQuery;
import com.library.contract.time.BusinessClock;
import com.library.circulation.internal.adapter.persistence.ClosingStore;
import org.springframework.stereotype.Component;

@Component
public final class ClosingModuleImpl implements ClosingCommands, ClosingStatusQuery {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ClosingModuleImpl.class);
    private final ClosingStore store;
    private final BusinessClock clock;
    public ClosingModuleImpl(ClosingStore store, BusinessClock clock){this.store=store;this.clock=clock;}
    @Override public java.util.Optional<java.time.LocalDate> firstUnclosedDateThrough(java.time.LocalDate date){return store.firstUnclosedDateThrough(date);}
    @Override public ModuleResult<ClosingRun> closeBusinessDate(ClosingCommand c){var date=c.businessDate().value();store.beginDraining(date);while(true){try{var status=store.isolateAndRun(date);return ModuleResult.Accepted.of(new ClosingRun(date.toEpochDay(),status));}catch(RuntimeException failure){log.warn("闭馆批次失败 businessDate={} cause={}", date, failure.toString());var state=store.markFailure(date);if("MANUAL".equals(state.status()))return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_014.code(),"闭馆批次失败，已转人工");}}}
    @Override public ModuleResult<ReopenedDate> reopen(ReopenCommand c){var through=c.throughDate().value();if(through.isAfter(clock.today())||!store.caughtUpThrough(through))return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_008.code(),"闭馆批次尚未追平或营业日期位于未来");return ModuleResult.Accepted.of(new ReopenedDate("REOPENED",store.reopen(through)));}
}
