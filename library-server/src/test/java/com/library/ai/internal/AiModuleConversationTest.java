package com.library.ai.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.ai.contract.*;
import com.library.ai.internal.adapter.persistence.AiSessionStore;
import com.library.contract.ModuleResult;
import com.library.contract.model.PageCursor;
import com.library.contract.model.Role;
import com.library.contract.security.ActorContext;
import com.library.contract.time.BusinessClock;
import com.library.identity.contract.AuthorizationCheck;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class AiModuleConversationTest {
    @Test void nextModelRequestContainsPriorCompleteTurnButNotAnotherSessionOrDuplicateCurrentQuestion() {
        var store = new MemoryStore();
        var endpoint = new CapturingEndpoint();
        var module = new AiModuleImpl(store, endpoint, (actor, capability, subject) -> null,
                () -> Instant.parse("2026-09-24T12:00:00Z"),
                new ToolExecutor(null, null, null, null, null, null, null, null, null, null, null, new ObjectMapper()),
                10, 10, 4, 2, Duration.ofSeconds(1));
        var owner = new VisitorAiOwner(new VisitorOwnerDigest("a".repeat(64)));
        var otherOwner = new VisitorAiOwner(new VisitorOwnerDigest("b".repeat(64)));
        var session = accepted(module.start(owner, null));
        var otherSession = accepted(module.start(otherOwner, null));

        endpoint.next("private other session");
        accepted(module.turn(new AiTurnCommand(otherSession.sessionId(), "another session question", null), otherOwner));
        endpoint.next("previous assistant answer");
        accepted(module.turn(new AiTurnCommand(session.sessionId(), "previous user question", null), owner));
        endpoint.next("current answer");
        accepted(module.turn(new AiTurnCommand(session.sessionId(), "current user question", null), owner));

        var messages = endpoint.requests.getLast().messages();
        assertThat(messages).contains(Map.of("role", "user", "content", "previous user question"),
                Map.of("role", "assistant", "content", "previous assistant answer"),
                Map.of("role", "user", "content", "current user question"));
        assertThat(messages.toString()).doesNotContain("another session question", "private other session");
        assertThat(messages.stream().filter(m -> "user".equals(m.get("role"))
                && "current user question".equals(m.get("content"))).count()).isEqualTo(1);
        assertThat(messages.getFirst()).containsEntry("role", "system");
        assertThat(messages.getFirst().get("content").toString()).contains("ONLINE", "DESK", "服务端", "取书");
        assertThat(messages.getFirst().get("content").toString())
                .contains("退款", "退款复核", "对账及差异处理", "当前版本不支持", "已移出的页面");
        assertThat(messages.getFirst().get("content").toString())
                .contains("预约不等于借出", "非预约日到台转人工处理", "馆员按现行规则核验", "校验未通过不得借出");
        assertThat(endpoint.requests.getLast().tools().stream().filter(t -> t.name().equals("get_title")).findFirst().orElseThrow()
                .description()).contains("详情页", "书目级");
    }

    @Test void removedSettlementCapabilitiesAreNotRegisteredAsAiTools() {
        var registry = ToolRegistry.approved();
        var readerId = com.library.contract.model.AccountId.of(77L);
        var owner = new AccountAiOwner(readerId);
        var actor = new ActorContext(readerId, Role.LIBRARIAN, Instant.parse("2026-09-24T12:00:00Z"));
        var names = registry.availableTo(owner, actor).stream().map(AiTool::name).toList();

        assertThat(names).noneMatch(name -> name.toLowerCase(Locale.ROOT).contains("refund")
                || name.toLowerCase(Locale.ROOT).contains("reconcil"));
    }

    @Test void reservationRulesAndToolSemanticsReachTheScriptedModelRequest() {
        var store = new MemoryStore();
        var endpoint = new CapturingEndpoint();
        var module = new AiModuleImpl(store, endpoint, (actor, capability, subject) -> null,
                () -> Instant.parse("2026-09-24T12:00:00Z"),
                new ToolExecutor(null, null, null, null, null, null, null, null, null, null, null, new ObjectMapper()),
                10, 10, 4, 2, Duration.ofSeconds(1));
        var accountId = com.library.contract.model.AccountId.of(42L);
        var actor = new ActorContext(accountId, Role.LIBRARIAN, Instant.parse("2026-09-24T12:00:00Z"));
        var owner = new AccountAiOwner(accountId);
        var session = accepted(module.start(owner, actor));
        endpoint.next("reader answer");
        accepted(module.turn(new AiTurnCommand(session.sessionId(), "如何预约并取书？", actor), owner));

        var request = endpoint.requests.getFirst();
        assertThat(request.messages().getFirst().get("content").toString())
                .contains("预约此书", "ONLINE", "DESK", "次一开馆日", "不保证", "不等于借出", "访客只能查询");
        assertThat(request.tools().stream().filter(t -> t.name().equals("prefill_reservation")).findFirst().orElseThrow()
                .description()).contains("不提交", "ONLINE", "DESK", "服务端裁定", "次一开馆日", "访客仅能查询", "馆员代办");
        assertThat(request.tools().stream().filter(t -> t.name().equals("open_pickup_claim")).findFirst().orElseThrow()
                .description()).contains("绑定副本", "校验通过才完成借出");
    }

    @Test void guidanceDistinguishesAdminSimulationFromRemovedRealMoneyFeatures() {
        var store = new MemoryStore();
        var endpoint = new CapturingEndpoint();
        var module = new AiModuleImpl(store, endpoint, (actor, capability, subject) -> null,
                () -> Instant.parse("2026-09-24T12:00:00Z"),
                new ToolExecutor(null, null, null, null, null, null, null, null, null, null, null, new ObjectMapper()),
                10, 10, 4, 2, Duration.ofSeconds(1));
        var adminId = com.library.contract.model.AccountId.of(43L);
        var admin = new ActorContext(adminId, Role.ADMIN, Instant.parse("2026-09-24T12:00:00Z"));
        var owner = new AccountAiOwner(adminId);
        var session = accepted(module.start(owner, admin));
        endpoint.next("当前仅提供模拟测试结果。");

        accepted(module.turn(new AiTurnCommand(session.sessionId(), "系统保留哪些支付能力？", admin), owner));

        var guidance = endpoint.requests.getFirst().messages().getFirst().get("content").toString();
        assertThat(guidance).contains("仅 ADMIN", "模拟支付调度", "不处理或扣除真实资金", "退款", "对账");
        assertThat(guidance).contains("现场借阅表单与 L2 预填仅限 LIBRARIAN",
                "读者不得进入或预填该表单", "读者普通预约能力仍保留，按现行预约规则办理");
        assertThat(guidance).contains("readerId 表示目标读者，不是权限凭据", "按馆员会话重新鉴权");
    }

    @Test void walkInPrefillIsRejectedForReadersAndRemainsAvailableToLibrarians() {
        var store = new MemoryStore();
        var endpoint = new CapturingEndpoint();
        var module = new AiModuleImpl(store, endpoint, (actor, capability, subject) -> null,
                () -> Instant.parse("2026-09-24T12:00:00Z"),
                new ToolExecutor(null, null, null, null, null, null, null, null, null, null, null, new ObjectMapper()),
                10, 10, 4, 2, Duration.ofSeconds(1));
        var args = Map.<String, Object>of("readerId", "1", "titleId", "4001", "copyId", "3001", "channel", "DESK");
        var readerId = com.library.contract.model.AccountId.of(44L);
        var reader = new ActorContext(readerId, Role.READER, Instant.parse("2026-09-24T12:00:00Z"));
        var readerOwner = new AccountAiOwner(readerId);
        var readerSession = accepted(module.start(readerOwner, reader));
        endpoint.next(new ModelEndpoint.ModelResponse("", List.of(), List.of(
                new ModelEndpoint.ToolCall("reader-walk-in", "prefill_walk_in_loan", args))));

        assertThat(code(module.turn(new AiTurnCommand(readerSession.sessionId(), "预填现场借阅", reader), readerOwner)))
                .isEqualTo("MLS-AI-004");
        assertThat(endpoint.requests.getFirst().tools()).extracting(AiTool::name)
                .doesNotContain("prefill_walk_in_loan");

        var librarianId = com.library.contract.model.AccountId.of(45L);
        var librarian = new ActorContext(librarianId, Role.LIBRARIAN, Instant.parse("2026-09-24T12:00:00Z"));
        var librarianOwner = new AccountAiOwner(librarianId);
        var librarianSession = accepted(module.start(librarianOwner, librarian));
        endpoint.next(new ModelEndpoint.ModelResponse("", List.of(), List.of(
                new ModelEndpoint.ToolCall("librarian-walk-in", "prefill_walk_in_loan", args))));
        endpoint.next(ModelEndpoint.ModelResponse.answer("已为馆员准备现场借阅草稿"));

        var turn = accepted(module.turn(new AiTurnCommand(librarianSession.sessionId(), "预填现场借阅", librarian), librarianOwner));
        var walkInTool = endpoint.requests.get(1).tools().stream()
                .filter(tool -> tool.name().equals("prefill_walk_in_loan")).findFirst().orElseThrow();
        assertThat(walkInTool.inputSchema()).contains("readerId");
        assertThat(turn.actions()).singleElement().satisfies(action -> {
            assertThat(action.level()).isEqualTo("L2");
            assertThat(action.prefill()).containsEntry("readerId", "1")
                    .containsEntry("titleId", "4001").containsEntry("copyId", "3001");
        });
        var persisted = accepted(module.events(new AiEventCriteria(librarianSession.sessionId(), PageCursor.first()),
                librarianOwner, Optional.empty()));
        assertThat(persisted.items()).filteredOn(event -> event.type().equals("ACTION_DRAFT"))
                .singleElement().satisfies(event -> assertThat(event.payload().get("prefill")).isEqualTo(args));
    }

    @Test void walkInDraftRejectsInvalidReaderIdsAndOtherToolsCannotSetTargetReader() {
        var store = new MemoryStore();
        var endpoint = new CapturingEndpoint();
        var module = new AiModuleImpl(store, endpoint, (actor, capability, subject) -> null,
                () -> Instant.parse("2026-09-24T12:00:00Z"),
                new ToolExecutor(null, null, null, null, null, null, null, null, null, null, null, new ObjectMapper()),
                10, 10, 4, 2, Duration.ofSeconds(1));
        var librarianId = com.library.contract.model.AccountId.of(46L);
        var librarian = new ActorContext(librarianId, Role.LIBRARIAN, Instant.parse("2026-09-24T12:00:00Z"));
        var librarianOwner = new AccountAiOwner(librarianId);
        var invalidTargetSession = accepted(module.start(librarianOwner, librarian));
        endpoint.next(new ModelEndpoint.ModelResponse("", List.of(), List.of(
                new ModelEndpoint.ToolCall("invalid-target", "prefill_walk_in_loan",
                        Map.of("readerId", "0", "titleId", "4001")))));

        assertThat(code(module.turn(new AiTurnCommand(invalidTargetSession.sessionId(), "预填现场借阅", librarian),
                librarianOwner))).isEqualTo("MLS-AI-004");
        var invalidEvents = accepted(module.events(new AiEventCriteria(invalidTargetSession.sessionId(), PageCursor.first()),
                librarianOwner, Optional.empty()));
        assertThat(invalidEvents.items()).extracting(AiEvent::type).doesNotContain("ACTION_DRAFT");

        var readerId = com.library.contract.model.AccountId.of(47L);
        var reader = new ActorContext(readerId, Role.READER, Instant.parse("2026-09-24T12:00:00Z"));
        var readerOwner = new AccountAiOwner(readerId);
        var renewalSession = accepted(module.start(readerOwner, reader));
        endpoint.next(new ModelEndpoint.ModelResponse("", List.of(), List.of(
                new ModelEndpoint.ToolCall("reader-renewal", "prefill_renewal",
                        Map.of("txId", "4001", "readerId", "46")))));

        assertThat(code(module.turn(new AiTurnCommand(renewalSession.sessionId(), "预填续借", reader), readerOwner)))
                .isEqualTo("MLS-AI-004");
        var renewalEvents = accepted(module.events(new AiEventCriteria(renewalSession.sessionId(), PageCursor.first()),
                readerOwner, Optional.empty()));
        assertThat(renewalEvents.items()).extracting(AiEvent::type)
                .doesNotContain("TOOL_CALL", "ACTION_DRAFT", "TOOL_RESULT");
    }

    @Test void historySkipsFailedUnansweredMessagesAndStaysWithinTheCharacterBudget() {
        var store = new MemoryStore();
        var endpoint = new CapturingEndpoint();
        var module = new AiModuleImpl(store, endpoint, (actor, capability, subject) -> null,
                () -> Instant.parse("2026-09-24T12:00:00Z"),
                new ToolExecutor(null, null, null, null, null, null, null, null, null, null, null, new ObjectMapper()),
                100, 100, 4, 2, Duration.ofSeconds(1));
        var owner = new VisitorAiOwner(new VisitorOwnerDigest("c".repeat(64)));
        var session = accepted(module.start(owner, null));
        for (int turn = 1; turn <= 7; turn++) {
            endpoint.next("answer-" + turn + "x".repeat(2_500));
            accepted(module.turn(new AiTurnCommand(session.sessionId(), "history-user-" + turn, null), owner));
        }

        endpoint.fail("retry one");endpoint.fail("retry two");endpoint.fail("retry three");
        assertThat(code(module.turn(new AiTurnCommand(session.sessionId(), "unanswered failed turn", null), owner)))
                .isEqualTo("MLS-AI-003");
        endpoint.next("recovered");
        accepted(module.turn(new AiTurnCommand(session.sessionId(), "current question", null), owner));

        var messages = endpoint.requests.getLast().messages();
        assertThat(messages.toString()).contains("history-user-7", "current question").doesNotContain("unanswered failed turn", "history-user-1");
        int historyChars = messages.subList(1, messages.size() - 1).stream()
                .mapToInt(message -> message.get("content").toString().length()).sum();
        assertThat(historyChars).isLessThanOrEqualTo(12_000);
    }

    @SuppressWarnings("unchecked")
    private String code(ModuleResult<?> result) {
        assertThat(result).isInstanceOf(ModuleResult.Rejected.class);
        return ((ModuleResult.Rejected<Object>) result).code();
    }

    @SuppressWarnings("unchecked")
    private <T> T accepted(ModuleResult<T> result) {
        assertThat(result).isInstanceOf(ModuleResult.Accepted.class);
        return ((ModuleResult.Accepted<T>) result).value();
    }

    private static final class CapturingEndpoint implements ModelEndpoint {
        private final List<ModelRequest> requests = new ArrayList<>();
        private final ArrayDeque<Object> answers = new ArrayDeque<>();
        void next(String answer) { answers.add(answer); }
        void next(ModelEndpoint.ModelResponse response) { answers.add(response); }
        void fail(String reason) { answers.add(new ModelFailure(reason)); }
        @Override public ModelResponse complete(ModelRequest request) throws ModelFailure {
            requests.add(request);
            Object next = answers.isEmpty() ? "default" : answers.remove();
            if (next instanceof ModelFailure failure) throw failure;
            if (next instanceof ModelEndpoint.ModelResponse response) return response;
            return ModelResponse.answer((String) next);
        }
    }

    private static final class MemoryStore implements AiSessionStore {
        private final Map<Long, StoredSession> sessions = new HashMap<>();
        private final Map<Long, List<Message>> messages = new HashMap<>();
        private final Map<Long, List<AiEvent>> eventLog = new HashMap<>();
        private long nextSession = 1;
        private long nextMessage = 1;
        private long nextEvent = 1;
        @Override public StoredSession create(AiSessionOwner owner, String role, Instant now) {
            long id = nextSession++;
            var session = new StoredSession(id, owner instanceof AccountAiOwner account ? account.accountId().value() : null,
                    owner instanceof VisitorAiOwner visitor ? visitor.digest().value() : null, role, "ACTIVE");
            sessions.put(id, session);
            messages.put(id, new ArrayList<>());
            eventLog.put(id, new ArrayList<>());
            return session;
        }
        @Override public Optional<StoredSession> session(long id) { return Optional.ofNullable(sessions.get(id)); }
        @Override public boolean ownedBy(StoredSession session, AiSessionOwner owner) {
            return owner instanceof AccountAiOwner account && session.accountId() != null
                    ? session.accountId() == account.accountId().value()
                    : session.visitorDigest() != null && owner instanceof VisitorAiOwner visitor
                    && session.visitorDigest().equals(visitor.digest().value());
        }
        @Override public long appendMessage(long sessionId, String role, String content, Map<String, Object> payload, Instant now) {
            long id = nextMessage++;
            messages.get(sessionId).add(new Message(id, role, content));
            return id;
        }
        @Override public List<StoredMessage> messagesBefore(long sessionId, long beforeMessageId, int limit) {
            return messages.get(sessionId).stream().filter(message -> message.id() < beforeMessageId)
                    .sorted(Comparator.comparingLong(Message::id).reversed()).limit(limit)
                    .map(message -> new StoredMessage(message.id(), message.role(), message.content())).toList();
        }
        @Override public long appendEvent(long sessionId, String type, Map<String, Object> payload, Instant now) {
            long id = nextEvent++;
            eventLog.get(sessionId).add(new AiEvent(id, type, payload, now));
            return id;
        }
        @Override public List<AiEvent> events(long sessionId, long afterId, int limit) {
            return eventLog.get(sessionId).stream().filter(event -> event.eventId() > afterId)
                    .limit(limit).toList();
        }
        @Override public boolean tryConsume(AiSessionOwner owner, LocalDate date, int limit) { return true; }
        @Override public void refund(AiSessionOwner owner, LocalDate date) { }
        @Override public int remaining(AiSessionOwner owner, LocalDate date, int limit) { return limit; }
        @Override public void status(long sessionId, String status, Instant now) { }
    }

    private record Message(long id, String role, String content) { }
}
