package com.library.ai.internal.adapter.persistence;

import com.library.ai.contract.AiEvent;
import com.library.ai.contract.AiSessionOwner;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface AiSessionStore {
    StoredSession create(AiSessionOwner owner, String role, Instant now);
    Optional<StoredSession> session(long id);
    boolean ownedBy(StoredSession session, AiSessionOwner owner);
    long appendMessage(long sessionId, String role, String content, Map<String, Object> payload, Instant now);
    List<StoredMessage> messagesBefore(long sessionId, long beforeMessageId, int limit);
    long appendEvent(long sessionId, String type, Map<String, Object> payload, Instant now);
    List<AiEvent> events(long sessionId, long afterId, int limit);
    boolean tryConsume(AiSessionOwner owner, LocalDate date, int limit);
    void refund(AiSessionOwner owner, LocalDate date);
    int remaining(AiSessionOwner owner, LocalDate date, int limit);
    void status(long sessionId, String status, Instant now);

    record StoredSession(long id, Long accountId, String visitorDigest, String role, String status) {}
    record StoredMessage(long id, String role, String content) {}
}
