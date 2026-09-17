package com.library.delivery.contract;

import com.library.contract.ModuleResult;
import com.library.contract.model.PageCursor;
import com.library.contract.security.ActorContext;
import java.time.Instant;
import java.util.List;

public interface AuditQuery {
    ModuleResult<AuditPage> audit(AuditCriteria query, ActorContext actor);
    record AuditCriteria(Long relatedId,Long actorId,String eventType,PageCursor page) {}
    record AuditEvent(long eventId,String eventType,Long actorId,Long relatedId,String outcome,Instant occurredAt) {}
    record AuditPage(List<AuditEvent> items,String nextCursor) {}
}
