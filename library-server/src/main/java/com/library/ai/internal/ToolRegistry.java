package com.library.ai.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_ARGUMENT_BYTES = 8192;
    private static final int MAX_DEPTH = 4;
    private final Map<String, AiTool> tools;

    private ToolRegistry(List<AiTool> entries) {
        var indexed = new LinkedHashMap<String, AiTool>();
        entries.forEach(tool -> indexed.put(tool.name(), tool));
        this.tools = Map.copyOf(indexed);
    }

    public static ToolRegistry approved() {
        var publicRoles = Set.of("VISITOR", "READER", "LIBRARIAN", "ADMIN");
        return new ToolRegistry(List.of(
                l1("search_titles", "PUBLIC", publicRoles, fields(text("q"),text("category"),cursor(),limit()), none()),
                l1("get_title", "PUBLIC", publicRoles, fields(id("titleId")), required("titleId")),
                l1("get_reading_content", "PUBLIC", publicRoles, fields(id("titleId"),cursor(),limit()), required("titleId")),
                l1("get_reservation_calendar", "PUBLIC", publicRoles, fields(id("titleId"),date("from"),date("to")), required("titleId")),
                l1("list_accounts", "ADMIN", Set.of("ADMIN"), fields(text("role"),text("status"),cursor(),limit()), none()),
                l1("list_copies", "LIBRARIAN", Set.of("LIBRARIAN"), fields(id("titleId"),text("status"),cursor(),limit()), none()),
                l1("list_fines", "READER_SELF_OR_LIBRARIAN", Set.of("READER", "LIBRARIAN"), fields(text("status"),cursor(),limit()), none()),
                l1("get_order", "READER_SELF_OR_LIBRARIAN", Set.of("READER", "LIBRARIAN"), fields(id("orderId")), required("orderId")),
                l1("list_reviews", "LIBRARIAN_OR_ADMIN", Set.of("LIBRARIAN", "ADMIN"), fields(text("status"),cursor(),limit()), none()),
                l1("get_arrival_dashboard", "LIBRARIAN", Set.of("LIBRARIAN"), fields(date("businessDate")), none()),
                l1("get_my_business", "READER", Set.of("READER"), fields(text("kind"),text("status"),cursor(),limit()), none()),
                l1("list_notifications", "AUTHENTICATED_SELF", Set.of("READER", "LIBRARIAN", "ADMIN"), fields(bool("unreadOnly"),cursor(),limit()), none()),
                l1("list_audit_events", "LIBRARIAN_OR_ADMIN", Set.of("LIBRARIAN", "ADMIN"), fields(id("relatedId"),text("eventType"),cursor(),limit()), none()),
                action("prefill_reservation", "READER_SELF_OR_LIBRARIAN", "L2", Set.of("READER", "LIBRARIAN"), fields(id("titleId"),date("reservationDate"),text("pickupSlot"),text("channel")), required("titleId")),
                action("prefill_renewal", "READER_SELF", "L2", Set.of("READER"), fields(id("txId")), required("txId")),
                action("prefill_walk_in_loan", "READER_SELF_OR_LIBRARIAN", "L2", Set.of("READER", "LIBRARIAN"), fields(id("titleId"),id("copyId"),text("channel")), required("titleId")),
                action("prefill_return", "LIBRARIAN", "L2", Set.of("LIBRARIAN"), fields(id("copyId"),text("condition")), required("copyId")),
                navigation("open_account_change", "LIBRARIAN", Set.of("LIBRARIAN")),
                navigation("open_password_reset", "LIBRARIAN_OR_ADMIN", Set.of("LIBRARIAN", "ADMIN")),
                navigation("open_reader_status_change", "LIBRARIAN_OR_ADMIN", Set.of("LIBRARIAN", "ADMIN")),
                navigation("open_title_creation", "LIBRARIAN", Set.of("LIBRARIAN")),
                navigation("open_title_update", "LIBRARIAN", Set.of("LIBRARIAN")),
                navigation("open_title_removal", "LIBRARIAN", Set.of("LIBRARIAN")),
                navigation("open_copy_creation", "LIBRARIAN", Set.of("LIBRARIAN")),
                navigation("open_copy_state_change", "LIBRARIAN", Set.of("LIBRARIAN")),
                navigation("open_reservation_cancellation", "READER_SELF_OR_LIBRARIAN", Set.of("READER", "LIBRARIAN")),
                navigation("open_pickup_claim", "LIBRARIAN", Set.of("LIBRARIAN")),
                navigation("open_manual_action", "LIBRARIAN", Set.of("LIBRARIAN")),
                navigation("open_closing_reopen", "ADMIN", Set.of("ADMIN")),
                navigation("open_order_creation", "READER_SELF", Set.of("READER")),
                navigation("open_order_close", "READER_SELF_OR_LIBRARIAN_OR_SYSTEM", Set.of("READER", "LIBRARIAN")),
                navigation("open_refund_request", "LIBRARIAN_THEN_ADMIN_REVIEW", Set.of("LIBRARIAN")),
                navigation("open_reconciliation", "SYSTEM_OR_ADMIN", Set.of("ADMIN")),
                navigation("open_review_request", "LIBRARIAN", Set.of("LIBRARIAN")),
                navigation("open_review_decision", "ADMIN", Set.of("ADMIN")),
                navigation("open_notification_read", "AUTHENTICATED_SELF", Set.of("READER", "LIBRARIAN", "ADMIN"))));
    }

    private static AiTool l1(String name, String capability, Set<String> roles, String properties, String required) {
        return new AiTool(name, capability, schema(properties,required), "query:" + name, "L1", roles);
    }
    private static AiTool action(String name, String capability, String level, Set<String> roles, String properties, String required) {
        return new AiTool(name, capability, schema(properties,required), "action:" + name, level, roles);
    }
    private static AiTool navigation(String name, String capability, Set<String> roles) {
        return action(name, capability, "L3", roles, fields(), none());
    }

    private static String schema(String properties,String required){return "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{"+properties+"},\"required\":"+required+"}";}
    private static String fields(String... values){return String.join(",",values);}
    private static String required(String... names){return "["+java.util.Arrays.stream(names).map(n->"\""+n+"\"").collect(java.util.stream.Collectors.joining(","))+"]";}
    private static String none(){return "[]";}
    private static String text(String name){return "\""+name+"\":{\"type\":\"string\",\"maxLength\":200}";}
    private static String date(String name){return "\""+name+"\":{\"type\":\"string\",\"maxLength\":10,\"pattern\":\"^[0-9]{4}-[0-9]{2}-[0-9]{2}$\"}";}
    private static String cursor(){return "\"cursor\":{\"type\":\"string\",\"maxLength\":20,\"pattern\":\"^[0-9]+$\"}";}
    private static String id(String name){return "\""+name+"\":{\"type\":\"string\",\"maxLength\":20,\"pattern\":\"^[1-9][0-9]*$\"}";}
    private static String limit(){return "\"limit\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":100}";}
    private static String bool(String name){return "\""+name+"\":{\"type\":\"boolean\"}";}

    public List<AiTool> availableTo(AiSessionOwner owner, ActorContext actor) {
        String role = owner instanceof VisitorAiOwner ? "VISITOR" : actor == null ? "" : actor.role().name();
        return tools.values().stream().filter(tool -> tool.roles().contains(role)).toList();
    }

    public AiTool require(String name) {
        var tool = tools.get(name);
        if (tool == null) throw new ToolRejected("未登记工具");
        return tool;
    }

    public void validate(AiTool tool,Map<String,Object> arguments){
        try {
            var value=JSON.valueToTree(arguments);
            if(JSON.writeValueAsBytes(value).length>MAX_ARGUMENT_BYTES)throw new ToolRejected("工具参数过大");
            rejectControlledFields(value);
            validateNode(value,JSON.readTree(tool.inputSchema()),0,"$");
        } catch(ToolRejected rejected){throw rejected;}
        catch(Exception failure){throw new ToolRejected("工具参数非法");}
    }

    private void rejectControlledFields(JsonNode value){
        if(value.isObject())value.fields().forEachRemaining(entry->{
            if(Set.of("role","accountId","readerId","amountCents","version").contains(entry.getKey()))
                throw new ToolRejected("模型参数包含服务端受控字段");
            rejectControlledFields(entry.getValue());
        });
        else if(value.isArray())value.forEach(this::rejectControlledFields);
    }

    private void validateNode(JsonNode value,JsonNode schema,int depth,String path){
        if(depth>MAX_DEPTH)throw new ToolRejected("工具参数嵌套过深");
        var type=schema.path("type").asText();
        switch(type){
            case "object" -> {
                if(!value.isObject())throw new ToolRejected(path+" 必须是对象");
                var properties=schema.path("properties");
                schema.path("required").forEach(name->{if(!value.has(name.asText()))throw new ToolRejected(path+" 缺少 "+name.asText());});
                value.fieldNames().forEachRemaining(name->{
                    if(!properties.has(name)&&!schema.path("additionalProperties").asBoolean(true))throw new ToolRejected(path+" 含未批准字段 "+name);
                    if(properties.has(name))validateNode(value.get(name),properties.get(name),depth+1,path+"."+name);
                });
            }
            case "array" -> {if(!value.isArray()||value.size()>schema.path("maxItems").asInt(50))throw new ToolRejected(path+" 数组非法");value.forEach(v->validateNode(v,schema.path("items"),depth+1,path+"[]"));}
            case "string" -> {if(!value.isTextual()||value.textValue().length()>schema.path("maxLength").asInt(200)
                    ||(schema.has("pattern")&&!value.textValue().matches(schema.path("pattern").asText())))throw new ToolRejected(path+" 字符串非法");}
            case "integer" -> {if(!value.isIntegralNumber()||value.longValue()<schema.path("minimum").asLong(Long.MIN_VALUE)||value.longValue()>schema.path("maximum").asLong(Long.MAX_VALUE))throw new ToolRejected(path+" 整数非法");}
            case "boolean" -> {if(!value.isBoolean())throw new ToolRejected(path+" 布尔值非法");}
            default -> throw new ToolRejected(path+" schema 未批准");
        }
    }

    public static final class ToolRejected extends RuntimeException {
        public ToolRejected(String message) { super(message); }
    }
}
