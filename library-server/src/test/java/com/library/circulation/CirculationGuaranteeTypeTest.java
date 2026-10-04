package com.library.circulation;

import com.library.catalog.contract.model.TitleId;
import com.library.contract.ModuleResult;
import com.library.contract.audit.RecordingAuditAppender;
import com.library.contract.model.AccountId;
import com.library.contract.model.Role;
import com.library.contract.outbox.RecordingOutboxAppender;
import com.library.contract.security.ActorContext;
import com.library.contract.time.FixedBusinessClock;
import com.library.circulation.contract.LoanCommands;
import com.library.circulation.contract.ReservationCommands;
import com.library.circulation.contract.model.BusinessDate;
import com.library.circulation.contract.model.CopyId;
import com.library.circulation.contract.model.GuaranteeType;
import com.library.circulation.internal.CirculationModuleImpl;
import com.library.circulation.internal.adapter.persistence.CirculationStore;
import com.library.circulation.internal.adapter.persistence.InMemoryCirculationStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CirculationGuaranteeTypeTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);

    private final InMemoryCirculationStore store = new InMemoryCirculationStore();
    private final ActorContext reader = new ActorContext(AccountId.of(1L), Role.READER,
            Instant.parse("2026-09-16T08:00:00Z"));
    private final CirculationModuleImpl module = new CirculationModuleImpl(store,
            new RecordingOutboxAppender(), new RecordingAuditAppender(),
            new FixedBusinessClock(Instant.parse("2026-09-27T12:00:00Z")));

    @Test
    void reservationCreationRecordsItsOriginAndLaterStatusChangesKeepIt() {
        var directTitle = TitleId.of(10L);
        seedCopy(directTitle.value(), "DIRECT", "SHELF_READY");
        var direct = reserve(directTitle, reader, false);
        assertThat(store.guaranteeTypeForTest(direct.txId().value()))
                .isEqualTo(GuaranteeType.DIRECT_CONFIRMATION);

        var waitingTitle = TitleId.of(11L);
        seedCopy(waitingTitle.value(), "WAITLIST", "LENT");
        var waiting = reserve(waitingTitle, readerOf(2L), false);
        assertThat(waiting.outcome()).isEqualTo("WAITLIST_ENQUEUED");
        assertThat(store.guaranteeTypeForTest(waiting.txId().value())).isEqualTo(GuaranteeType.WAITLIST);

        var waitlistedRow = store.txById(waiting.txId().value()).orElseThrow();
        assertThat(store.transitionTransaction(waitlistedRow.id(), "WAITLIST", "CONFIRMED",
                waitlistedRow.version())).isNotNull();
        assertThat(store.guaranteeTypeForTest(waiting.txId().value()))
                .as("allocation changes status but keeps the creation-time type")
                .isEqualTo(GuaranteeType.WAITLIST);

        var directRow = store.txById(direct.txId().value()).orElseThrow();
        assertThat(store.transitionTransaction(directRow.id(), "CONFIRMED", "NOT_BORROWED",
                directRow.version())).isNotNull();
        assertThat(store.guaranteeTypeForTest(direct.txId().value()))
                .isEqualTo(GuaranteeType.DIRECT_CONFIRMATION);
    }

    @Test
    void compatibilityConstructorDoesNotInventAGuaranteeType() {
        var candidate = new CirculationStore.NewTransaction(1L, 10L, null, "MANUAL_HANDLING",
                TODAY, "AM", null);
        assertThat(candidate.guaranteeType()).isNull();

        var txId = store.insertTransaction(candidate);
        assertThat(store.guaranteeTypeForTest(txId)).isNull();
    }

    @Test
    void walkInLoanCreationLeavesGuaranteeTypeUnknown() {
        var title = TitleId.of(12L);
        var copyId = store.insertCopy(title.value(), "WALK-IN", TODAY);
        assertThat(store.transitionCopy(copyId, "PENDING_SHELVING", "SHELF_READY", 0L)).isEqualTo(1L);
        var librarian = new ActorContext(AccountId.of(2L), Role.LIBRARIAN, reader.authenticatedAt());

        var result = module.lend(new LoanCommands.WalkInLoanCommand(1L,
                List.of(new LoanCommands.WalkInItem(title, CopyId.of(copyId))), "DESK", librarian));
        assertThat(result).isInstanceOf(ModuleResult.Accepted.class);
        var item = ((ModuleResult.Accepted<LoanCommands.LoanBatch>) result).value().items().getFirst();
        assertThat(item.outcome()).isEqualTo("LENT");
        assertThat(store.guaranteeTypeForTest(item.txId().value())).isNull();
    }

    private ReservationCommands.Reservation reserve(TitleId title, ActorContext actor, boolean confirmWaitlist) {
        var result = module.reserve(new ReservationCommands.ReservationCommand(
                actor.accountId().value(), title, BusinessDate.of(TODAY), "AM", "DESK", confirmWaitlist, actor));
        assertThat(result).isInstanceOf(ModuleResult.Accepted.class);
        return ((ModuleResult.Accepted<ReservationCommands.Reservation>) result).value();
    }

    private ActorContext readerOf(long id) {
        return new ActorContext(AccountId.of(id), Role.READER, reader.authenticatedAt());
    }

    private void seedCopy(long titleId, String copyNo, String status) {
        var copyId = store.insertCopy(titleId, copyNo, TODAY);
        assertThat(store.transitionCopy(copyId, "PENDING_SHELVING", status, 0L)).isEqualTo(1L);
    }
}
