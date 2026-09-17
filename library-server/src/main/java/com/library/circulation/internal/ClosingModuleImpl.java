package com.library.circulation.internal;

import com.library.contract.ModuleResult;
import com.library.contract.error.StableErrorCode;
import com.library.circulation.contract.ClosingCommands;
import com.library.circulation.internal.adapter.persistence.ClosingStore;
import org.springframework.stereotype.Component;

@Component
public final class ClosingModuleImpl implements ClosingCommands {
    private final ClosingStore store;
    public ClosingModuleImpl(ClosingStore store){this.store=store;}
    @Override public ModuleResult<ClosingRun> closeBusinessDate(ClosingCommand c){var date=c.businessDate().value();store.beginDraining(date);while(true){try{var status=store.isolateAndRun(date);return ModuleResult.Accepted.of(new ClosingRun(date.toEpochDay(),status));}catch(RuntimeException failure){var state=store.markFailure(date);if("MANUAL".equals(state.status()))return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_014.code(),"闭馆批次失败，已转人工");}}}
    @Override public ModuleResult<ReopenedDate> reopen(ReopenCommand c){if(!store.caughtUpThrough(c.throughDate().value()))return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_008.code(),"闭馆批次尚未追平");return ModuleResult.Accepted.of(new ReopenedDate("REOPENED",store.reopen(c.throughDate().value())));}
}
