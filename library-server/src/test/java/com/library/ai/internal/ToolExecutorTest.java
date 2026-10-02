package com.library.ai.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.circulation.internal.CirculationModuleImpl;
import com.library.circulation.internal.adapter.persistence.InMemoryCirculationStore;
import com.library.contract.audit.RecordingAuditAppender;
import com.library.contract.outbox.RecordingOutboxAppender;
import com.library.contract.time.FixedBusinessClock;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ToolExecutorTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void malformedTitleIdIsRejectedAsAnAiToolInputError() {
        var thrown = catchThrowable(() -> executor(null).execute("get_title", Map.of("titleId", "bad"), null));

        assertThat(thrown).isInstanceOf(ToolRegistry.ToolRejected.class)
                .hasMessageContaining("titleId");
    }

    @Test
    void copyStatusFilterRejectsDatabaseAndUnknownStatesThroughAiTool() throws Exception {
        var store = new InMemoryCirculationStore();
        var circulation = new CirculationModuleImpl(store, new RecordingOutboxAppender(),
                new RecordingAuditAppender(), new FixedBusinessClock(Instant.parse("2026-09-27T12:00:00Z")));
        var tools = executor(circulation);

        for (var status : new String[] {"SHELF_READY", "UNKNOWN"}) {
            var result = json.readTree(tools.execute("list_copies", Map.of("status", status), null));
            assertThat(result.toString()).contains("MLS-CIRC-001");
        }

        var valid = json.readTree(tools.execute("list_copies", Map.of("status", "AVAILABLE"), null));
        assertThat(valid.toString()).doesNotContain("MLS-CIRC-001");
    }

    private ToolExecutor executor(com.library.circulation.contract.CopyCommands copies) {
        return new ToolExecutor(null, null, null, copies, null, null, null, null,
                null, null, null, json);
    }
}
