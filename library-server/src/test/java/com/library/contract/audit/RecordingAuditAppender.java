package com.library.contract.audit;

import java.util.ArrayList;
import java.util.List;

/** 测试用审计登记桩：记录全部审计调用，不做持久化。 */
public final class RecordingAuditAppender implements AuditAppender {

    private final List<Entry> entries = new ArrayList<>();

    @Override
    public void append(String eventType, Long actorId, Object detail) {
        entries.add(new Entry(eventType, actorId, detail, false));
    }

    @Override
    public void appendWithBusinessFact(String eventType, Long actorId, Object detail) {
        entries.add(new Entry(eventType, actorId, detail, true));
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public record Entry(String eventType, Long actorId, Object detail, boolean withBusinessFact) {
    }
}
