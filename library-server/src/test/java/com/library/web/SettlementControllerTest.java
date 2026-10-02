package com.library.web;

import com.library.contract.ModuleResult;
import com.library.contract.audit.RecordingAuditAppender;
import com.library.contract.model.AccountId;
import com.library.contract.model.Role;
import com.library.contract.security.ActorContext;
import com.library.settlement.contract.FineQuery;
import com.library.web.support.Actors;
import com.library.web.support.ApiResponses;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementControllerTest {

    @Test
    void librarianCanListFinesWhenTheOptionalReaderFilterIsOmitted() {
        var criteria = new AtomicReference<FineQuery.FineCriteria>();
        var controller = controller(query -> {
            criteria.set(query);
            return ModuleResult.Accepted.of(new FineQuery.FinePage(List.of(), null));
        });
        var request = request(21L, Role.LIBRARIAN);

        var response = controller.listFines(request, null, null, null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(criteria.get().readerId()).isNull();
    }

    @Test
    void librarianReaderFilterIsPassedThroughToTheFineQuery() {
        var criteria = new AtomicReference<FineQuery.FineCriteria>();
        var controller = controller(query -> {
            criteria.set(query);
            return ModuleResult.Accepted.of(new FineQuery.FinePage(List.of(), null));
        });

        var response = controller.listFines(request(21L, Role.LIBRARIAN), 34L, null, null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(criteria.get().readerId()).isEqualTo(34L);
    }

    @Test
    void readerCannotUseTheOptionalFilterToSelectAnotherReadersFines() {
        var criteria = new AtomicReference<FineQuery.FineCriteria>();
        var controller = controller(query -> {
            criteria.set(query);
            return ModuleResult.Accepted.of(new FineQuery.FinePage(List.of(), null));
        });

        var response = controller.listFines(request(21L, Role.READER), 34L, null, null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(criteria.get().readerId()).isEqualTo(21L);
    }

    private SettlementController controller(FineQuery fines) {
        return new SettlementController(fines, null, null, null, null,
                new ApiResponses(new RecordingAuditAppender()));
    }

    private MockHttpServletRequest request(long accountId, Role role) {
        var request = new MockHttpServletRequest();
        request.setAttribute(Actors.ACTOR_ATTRIBUTE,
                new ActorContext(AccountId.of(accountId), role, Instant.parse("2026-09-30T12:00:00Z")));
        return request;
    }
}
