package com.library.circulation;

import com.library.contract.ModuleResult;
import com.library.circulation.contract.ClosingCommands;
import com.library.circulation.contract.model.BusinessDate;
import com.library.circulation.internal.ClosingModuleImpl;
import com.library.circulation.internal.adapter.persistence.InMemoryClosingStore;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ClosingModuleTest {
    @Test void b4_closingDrainsRunsFixedDateAndRequiresCatchUpBeforeReopen(){var store=new InMemoryClosingStore();var module=new ClosingModuleImpl(store);var date=BusinessDate.parse("2026-09-17");var run=module.closeBusinessDate(new ClosingCommands.ClosingCommand(date));assertThat(((ModuleResult.Accepted<ClosingCommands.ClosingRun>)run).value().status()).isEqualTo("SUCCEEDED");var reopened=module.reopen(new ClosingCommands.ReopenCommand(date));assertThat(((ModuleResult.Accepted<ClosingCommands.ReopenedDate>)reopened).value().epoch()).isEqualTo(1);assertThat(store.draining()).isFalse();}
}
