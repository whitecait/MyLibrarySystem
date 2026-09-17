package com.library.ai.internal;

import com.library.ai.contract.AiSessionOwner;
import com.library.ai.contract.AiTool;
import com.library.ai.contract.VisitorAiOwner;
import com.library.contract.model.Role;
import com.library.contract.security.ActorContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 批准工具白名单。这里只登记只读查询、L2 预填和 L3 页面入口，不持有业务写接口。 */
public final class ToolRegistry {
    private final Map<String, AiTool> tools;

    private ToolRegistry(List<AiTool> entries) {
        var indexed = new LinkedHashMap<String, AiTool>();
        entries.forEach(tool -> indexed.put(tool.name(), tool));
        this.tools = Map.copyOf(indexed);
    }

    public static ToolRegistry approved() {
        var publicRoles = Set.of("VISITOR", "READER", "LIBRARIAN", "ADMIN");
        return new ToolRegistry(List.of(
                l1("search_titles", "PUBLIC", publicRoles), l1("get_title", "PUBLIC", publicRoles),
                l1("get_reading_content", "PUBLIC", publicRoles),
                l1("get_reservation_calendar", "PUBLIC", publicRoles),
                l1("list_accounts", "ADMIN", Set.of("ADMIN")),
                l1("list_copies", "LIBRARIAN", Set.of("LIBRARIAN")),
                l1("list_fines", "READER_SELF_OR_LIBRARIAN", Set.of("READER", "LIBRARIAN")),
                l1("get_order", "READER_SELF_OR_LIBRARIAN", Set.of("READER", "LIBRARIAN")),
                l1("list_reviews", "LIBRARIAN_OR_ADMIN", Set.of("LIBRARIAN", "ADMIN")),
                l1("get_arrival_dashboard", "LIBRARIAN", Set.of("LIBRARIAN")),
                l1("get_my_business", "READER", Set.of("READER")),
                l1("list_notifications", "AUTHENTICATED_SELF", Set.of("READER", "LIBRARIAN", "ADMIN")),
                l1("list_audit_events", "LIBRARIAN_OR_ADMIN", Set.of("LIBRARIAN", "ADMIN")),
                l1("list_ai_events", "SESSION_OWNER_OR_ADMIN", Set.of("READER", "LIBRARIAN", "ADMIN")),
                action("prefill_reservation", "READER", "L2", Set.of("READER")),
                action("prefill_renewal", "READER", "L2", Set.of("READER")),
                action("prefill_walk_in_loan", "LIBRARIAN", "L2", Set.of("LIBRARIAN")),
                action("prefill_return", "LIBRARIAN", "L2", Set.of("LIBRARIAN")),
                action("open_account_maintenance", "ADMIN", "L3", Set.of("ADMIN")),
                action("open_settlement", "READER", "L3", Set.of("READER", "LIBRARIAN")),
                action("open_review", "LIBRARIAN_OR_ADMIN", "L3", Set.of("LIBRARIAN", "ADMIN"))));
    }

    private static AiTool l1(String name, String capability, Set<String> roles) {
        return new AiTool(name, capability, "{\"type\":\"object\"}", "query:" + name, "L1", roles);
    }
    private static AiTool action(String name, String capability, String level, Set<String> roles) {
        return new AiTool(name, capability, "{\"type\":\"object\"}", "action:" + name, level, roles);
    }

    public List<AiTool> availableTo(AiSessionOwner owner, ActorContext actor) {
        String role = owner instanceof VisitorAiOwner ? "VISITOR" : actor == null ? "" : actor.role().name();
        return tools.values().stream().filter(tool -> tool.roles().contains(role)).toList();
    }

    public AiTool require(String name) {
        var tool = tools.get(name);
        if (tool == null) throw new ToolRejected("未登记工具");
        return tool;
    }

    public static final class ToolRejected extends RuntimeException {
        public ToolRejected(String message) { super(message); }
    }
}
