package com.library.circulation.contract;

import com.library.contract.ModuleResult;
import com.library.circulation.contract.model.BusinessDate;

public interface ClosingCommands {
    ModuleResult<ClosingRun> closeBusinessDate(ClosingCommand command);
    ModuleResult<ReopenedDate> reopen(ReopenCommand command);
    record ClosingCommand(BusinessDate businessDate) {}
    record ReopenCommand(BusinessDate throughDate) {}
    record ClosingRun(long batchId,String status) {}
    record ReopenedDate(String status,long epoch) {}
}
