package com.library.settlement.contract;

import com.library.contract.ModuleResult;
import com.library.circulation.contract.model.BusinessDate;

import java.util.List;
import java.util.Map;

public interface ReconciliationCommands {
    ModuleResult<Reconciliation> reconcile(ReconciliationCommand command);

    record ReconciliationCommand(BusinessDate businessDate) {}
    record Reconciliation(long reconciliationId, Map<String, Integer> counts, List<String> exceptions) {}
}
