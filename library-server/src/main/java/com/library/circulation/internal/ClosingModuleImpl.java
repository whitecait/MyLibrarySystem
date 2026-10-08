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
    @Override public boolean requiresManualRecovery(java.time.LocalDate date){return store.requiresManualRecovery(date);}
    @Override public synchronized ModuleResult<ClosingRun> closeBusinessDate(ClosingCommand c){
        var date=c.businessDate().value();
        store.beginDraining(date);
        while(true){
            try{
                var status=store.isolateAndRun(date);
                return ModuleResult.Accepted.of(new ClosingRun(date.toEpochDay(),status));
            }catch(RuntimeException failure){
                log.warn("闭馆批次失败 businessDate={} cause={}", date, failure.toString());
                var state=store.markFailure(date);
                if("MANUAL".equals(state.status()))return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_014.code(),"闭馆批次失败，已转人工");
            }
        }
    }
    @Override public synchronized ModuleResult<ReopenedDate> reopen(ReopenCommand c){
        var through=c.throughDate().value();
        var today=clock.today();
        if(through.isAfter(today))return reopenRejected();
        var latestDueDate=today.minusDays(1);
        var effectiveThroughDate=through.isAfter(latestDueDate)?through:latestDueDate;
        if(!store.caughtUpThrough(through)||!store.caughtUpThrough(effectiveThroughDate))return reopenRejected();
        var epoch=store.reopen(through);
        if(epoch.isEmpty())return reopenRejected();
        return ModuleResult.Accepted.of(new ReopenedDate("REOPENED",epoch.getAsLong()));
    }

    private ModuleResult<ReopenedDate> reopenRejected(){
        return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_008.code(),"闭馆批次尚未追平或营业日期位于未来");
    }
}
