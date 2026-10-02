package com.library.ai.internal;

import com.library.ai.contract.AccountAiOwner;
import com.library.ai.contract.AiTool;
import com.library.ai.contract.VisitorAiOwner;
import com.library.ai.contract.VisitorOwnerDigest;
import com.library.contract.model.AccountId;
import com.library.contract.model.Role;
import com.library.contract.security.ActorContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolRegistryTest {
    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");

    @Test
    void onlyLibrarianWalkInSchemaAcceptsRootTargetReaderId() {
        var registry = ToolRegistry.approved();
        var librarian = actor(100L, Role.LIBRARIAN);
        var owner = new AccountAiOwner(librarian.accountId());
        var tool = walkInTool(registry, owner, librarian);
        var arguments = walkInArguments();
        arguments.put("readerId", "1");

        assertThat(tool.inputSchema()).contains("\"readerId\"");
        assertThatCode(() -> registry.validate(tool, arguments, librarian)).doesNotThrowAnyException();
    }

    @Test
    void targetReaderIdIsRejectedForOtherRolesAndVisitorSessions() {
        var registry = ToolRegistry.approved();
        var tool = registry.require("prefill_walk_in_loan");
        var arguments = walkInArguments();
        arguments.put("readerId", "1");
        var reader = actor(101L, Role.READER);
        var admin = actor(102L, Role.ADMIN);

        assertThat(registry.availableTo(new AccountAiOwner(reader.accountId()), reader))
                .extracting(AiTool::name).doesNotContain("prefill_walk_in_loan");
        assertThat(registry.availableTo(new AccountAiOwner(admin.accountId()), admin))
                .extracting(AiTool::name).doesNotContain("prefill_walk_in_loan");
        assertThat(registry.availableTo(new VisitorAiOwner(new VisitorOwnerDigest("a".repeat(64))), null))
                .extracting(AiTool::name).doesNotContain("prefill_walk_in_loan");

        assertRejected(registry, tool, arguments, reader);
        assertRejected(registry, tool, arguments, admin);
        assertRejected(registry, tool, arguments, null);
    }

    @Test
    void controlledIdentityAndBusinessFieldsRemainRejectedForLibrarianWalkIn() {
        var registry = ToolRegistry.approved();
        var librarian = actor(103L, Role.LIBRARIAN);
        var tool = walkInTool(registry, new AccountAiOwner(librarian.accountId()), librarian);
        var nestedReaderId = validWalkInArguments();
        nestedReaderId.put("nested", Map.of("readerId", "2"));
        var role = validWalkInArguments();
        role.put("role", "ADMIN");
        var accountId = validWalkInArguments();
        accountId.put("accountId", "2");
        var amountCents = validWalkInArguments();
        amountCents.put("amountCents", 1);
        var version = validWalkInArguments();
        version.put("version", 1);

        for (var arguments : java.util.List.of(nestedReaderId, role, accountId, amountCents, version)) {
            assertThatThrownBy(() -> registry.validate(tool, arguments, librarian))
                    .as("arguments %s", arguments)
                    .isInstanceOf(ToolRegistry.ToolRejected.class);
        }
    }

    @Test
    void walkInTargetReaderIdMustStillMatchTheApprovedIdFormat() {
        var registry = ToolRegistry.approved();
        var librarian = actor(104L, Role.LIBRARIAN);
        var tool = walkInTool(registry, new AccountAiOwner(librarian.accountId()), librarian);
        var arguments = validWalkInArguments();
        arguments.put("readerId", "0");

        assertThatThrownBy(() -> registry.validate(tool, arguments, librarian))
                .isInstanceOf(ToolRegistry.ToolRejected.class)
                .hasMessageContaining("$.readerId");
    }

    private AiTool walkInTool(ToolRegistry registry, AccountAiOwner owner, ActorContext actor) {
        return registry.availableTo(owner, actor).stream()
                .filter(tool -> tool.name().equals("prefill_walk_in_loan"))
                .findFirst().orElseThrow();
    }

    private Map<String, Object> walkInArguments() {
        var arguments = new LinkedHashMap<String, Object>();
        arguments.put("titleId", "2");
        return arguments;
    }

    private Map<String, Object> validWalkInArguments() {
        var arguments = walkInArguments();
        arguments.put("readerId", "1");
        return arguments;
    }

    private ActorContext actor(long id, Role role) {
        return new ActorContext(AccountId.of(id), role, NOW);
    }

    private void assertRejected(ToolRegistry registry, AiTool tool, Map<String, Object> arguments,
            ActorContext actor) {
        assertThatThrownBy(() -> registry.validate(tool, arguments, actor))
                .isInstanceOf(ToolRegistry.ToolRejected.class);
    }
}
