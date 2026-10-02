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
                action("prefill_walk_in_loan", "LIBRARIAN", "L2", Set.of("LIBRARIAN"), fields(id("readerId"),id("titleId"),id("copyId"),text("channel")), required("titleId")),
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
                navigation("open_review_request", "LIBRARIAN", Set.of("LIBRARIAN")),
                navigation("open_review_decision", "ADMIN", Set.of("ADMIN")),
                navigation("open_notification_read", "AUTHENTICATED_SELF", Set.of("READER", "LIBRARIAN", "ADMIN"))));
    }

    private static AiTool l1(String name, String capability, Set<String> roles, String properties, String required) {
        return new AiTool(name, capability, schema(properties,required), "query:" + name, "L1", roles,
                "只读查询，不提交或改变业务状态。" + switch (name) {
                    case "search_titles" -> "搜索公开书目；书目可借状态不能保证预约成功或实体副本可用。";
                    case "get_title" -> "读取书目详情。详情页提供“预约此书”表单；availability 仅为书目级展示，不保证预约成功或有副本可借。";
                    case "get_reservation_calendar" -> "查询书目预约日期状态；日历结果不代表预约请求已受理或预约成功。";
                    default -> "结果用于回答用户问题，业务提交须由现行业务页面完成。";
                });
    }
    private static AiTool action(String name, String capability, String level, Set<String> roles, String properties, String required) {
        String description = "L2".equals(level)
                ? "仅预填现有业务表单，不提交请求；须由用户在现有页面核对并提交。"
                : "仅引导至现有业务办理入口，不提交请求或产生业务事实。";
        if ("prefill_reservation".equals(name)) {
            description += "书目详情页有“预约此书”表单，支持 ONLINE 线上与 DESK 到台渠道。提交需读者本人登录，或馆员代办；访客仅能查询、不能提交。预约是否成立由服务端裁定，不得承诺成功。ONLINE 日期最早为次一开馆日，DESK 可当日。预约不会直接借出。";
        } else if ("prefill_walk_in_loan".equals(name)) {
            description += "仅馆员可使用现场借阅表单预填；readerId 表示目标读者，不是权限凭据，提交时服务端仍按馆员会话重新鉴权。预填不代表现场借阅已提交或已借出；读者没有现场借阅自助入口。";
        } else if ("open_pickup_claim".equals(name)) {
            description += "到馆取书后须由现有流程校验预约已绑定副本，校验通过才完成借出。";
        }
        return new AiTool(name, capability, schema(properties,required), "action:" + name, level, roles, description);
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

    public void validate(AiTool tool,Map<String,Object> arguments,ActorContext actor){
        try {
            var value=JSON.valueToTree(arguments);
            if(JSON.writeValueAsBytes(value).length>MAX_ARGUMENT_BYTES)throw new ToolRejected("工具参数过大");
            // The walk-in form's root readerId selects a target; write authorization still comes from the actor session.
            boolean allowWalkInTargetReader = "prefill_walk_in_loan".equals(tool.name())
                    && actor != null && actor.role() == Role.LIBRARIAN;
            rejectControlledFields(value,true,allowWalkInTargetReader);
            validateNode(value,JSON.readTree(tool.inputSchema()),0,"$");
        } catch(ToolRejected rejected){throw rejected;}
        catch(Exception failure){throw new ToolRejected("工具参数非法");}
    }

    private void rejectControlledFields(JsonNode value,boolean root,boolean allowWalkInTargetReader){
        if(value.isObject())value.fields().forEachRemaining(entry->{
            boolean allowedTargetReader = root && allowWalkInTargetReader && "readerId".equals(entry.getKey());
            if(Set.of("role","accountId","readerId","amountCents","version").contains(entry.getKey())
                    && !allowedTargetReader)
                throw new ToolRejected("模型参数包含服务端受控字段");
            rejectControlledFields(entry.getValue(),false,allowWalkInTargetReader);
        });
        else if(value.isArray())value.forEach(child->rejectControlledFields(child,false,allowWalkInTargetReader));
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
