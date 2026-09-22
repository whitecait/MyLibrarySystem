package com.library.contract.outbox;

import java.util.ArrayList;
import java.util.List;

/** 测试用 Outbox 登记桩：记录登记调用并分配递增 id，不做持久化。 */
public final class RecordingOutboxAppender implements OutboxAppender {

    private final List<Entry> entries = new ArrayList<>();
    private long sequence;

    @Override
    public long append(String aggType, long aggId, String eventType, Object payload) {
        var id = ++sequence;
        entries.add(new Entry(id, aggType, aggId, eventType, payload));
        return id;
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public record Entry(long eventId, String aggType, long aggId, String eventType, Object payload) {
    }
}
