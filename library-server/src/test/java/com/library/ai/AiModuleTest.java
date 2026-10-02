package com.library.ai;

import com.library.ai.contract.*;
import com.library.application.assistant.AssistantUseCases;
import com.library.contract.ModuleResult;
import com.library.contract.model.AccountId;
import com.library.contract.model.PageCursor;
import com.library.contract.model.Role;
import com.library.contract.security.ActorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** UT-AI-001~014：通过 AssistantUseCases/AiModule 公共 seam 观察行为。 */
@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@Import(AiModuleTest.ScriptConfig.class)
class AiModuleTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres@sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3");
    @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);}
    @TestConfiguration static class ScriptConfig { @Bean @Primary ScriptedModelEndpoint scripted(){return new ScriptedModelEndpoint();} }
    @Autowired AssistantUseCases assistant; @Autowired ScriptedModelEndpoint model; @Autowired JdbcTemplate jdbc;
    @BeforeEach void reset(){model.reset();jdbc.update("TRUNCATE ai_event,ai_message,ai_session,ai_visitor_quota_usage,ai_quota_usage RESTART IDENTITY CASCADE");}

    @Test void utAi001_visitorReceivesAndExecutesOnlyPublicL1Tools(){jdbc.update("INSERT INTO book_title(title,price_cents) VALUES ('Open Catalog',100)");var owner=visitor('a');var session=accepted(assistant.start(owner,null));model.then(new ModelEndpoint.ModelResponse("",List.of(),List.of(new ModelEndpoint.ToolCall("provider-call-1","search_titles",Map.of("q","Open")))));model.then(ModelEndpoint.ModelResponse.answer("found"));accepted(assistant.turn(session.sessionId(),"开放馆藏",owner,null));assertThat(model.requests().getFirst().tools()).extracting(AiTool::name).containsExactlyInAnyOrder("search_titles","get_title","get_reading_content","get_reservation_calendar");assertThat(model.requests().getFirst().tools()).allMatch(t->t.level().equals("L1"));assertThat(model.requests().getFirst().tools()).allSatisfy(t->assertThat(t.inputSchema()).contains("\"additionalProperties\":false"));var messages=model.requests().get(1).messages();assertThat(messages.toString()).contains("Open Catalog","provider-call-1","tool_call_id");var assistant=(Map<String,Object>)messages.get(2);var function=(Map<String,Object>)((Map<String,Object>)((List<?>)assistant.get("tool_calls")).getFirst()).get("function");assertThat(function.get("arguments")).isInstanceOf(String.class).isEqualTo("{\"q\":\"Open\"}");}
    @Test void utAi002_eachAccountRoleReceivesItsCompleteApprovedMatrix(){
        var reader=actor(Role.READER);var librarian=actor(Role.LIBRARIAN);var admin=actor(Role.ADMIN);
        turn(reader);turn(librarian);turn(admin);
        assertThat(model.requests().get(0).tools()).extracting(AiTool::name).containsExactlyInAnyOrder(
                "search_titles","get_title","get_reading_content","get_reservation_calendar",
                "list_fines","get_order","get_my_business","list_notifications",
                "prefill_reservation","prefill_renewal",
                "open_reservation_cancellation","open_order_creation","open_order_close","open_notification_read");
        assertThat(model.requests().get(1).tools()).extracting(AiTool::name).containsExactlyInAnyOrder(
                "search_titles","get_title","get_reading_content","get_reservation_calendar",
                "list_copies","list_fines","get_order","list_reviews","get_arrival_dashboard",
                "list_notifications","list_audit_events",
                "prefill_reservation","prefill_walk_in_loan","prefill_return",
                "open_account_change","open_password_reset","open_reader_status_change",
                "open_title_creation","open_title_update","open_title_removal",
                "open_copy_creation","open_copy_state_change","open_reservation_cancellation",
                "open_pickup_claim","open_manual_action","open_order_close",
                "open_review_request","open_notification_read");
        assertThat(model.requests().get(2).tools()).extracting(AiTool::name).containsExactlyInAnyOrder(
                "search_titles","get_title","get_reading_content","get_reservation_calendar",
                "list_accounts","list_reviews","list_notifications","list_audit_events",
                "open_password_reset","open_reader_status_change","open_closing_reopen",
                "open_review_decision","open_notification_read");
        assertThat(model.requests()).allSatisfy(request -> {
            assertThat(request.tools()).extracting(AiTool::name)
                    .doesNotContain("list_ai_events","start_ai_session","send_ai_message");
            request.tools().forEach(tool -> {
                if (tool.name().startsWith("prefill_")) assertThat(tool.level()).isEqualTo("L2");
                else if (tool.name().startsWith("open_")) {
                    assertThat(tool.level()).isEqualTo("L3");
                    assertThat(tool.inputSchema()).contains("\"properties\":{}");
                } else assertThat(tool.level()).isEqualTo("L1");
            });
        });
    }
    @Test void utAi003_l2CreatesPrefillWithoutBusinessWrite(){var actor=actor(Role.READER);var owner=new AccountAiOwner(actor.accountId());var s=accepted(assistant.start(owner,actor));model.then(new ModelEndpoint.ModelResponse("核对",List.of(),List.of(new ModelEndpoint.ToolCall("prefill_renewal",Map.of("txId","4001")))));model.then(ModelEndpoint.ModelResponse.answer("请核对后提交"));var turn=accepted(assistant.turn(s.sessionId(),"预填续借",owner,actor));assertThat(turn.actions()).singleElement().satisfies(a->{assertThat(a.level()).isEqualTo("L2");assertThat(a.prefill()).containsEntry("txId","4001");});assertThat(eventTypes(s.sessionId(),owner,null)).contains("ACTION_DRAFT").doesNotContain("BUSINESS_WRITE");}
    @Test void utAi004_nonPrefillWritesBecomeL3NavigationWithoutBusinessValues(){var actor=actor(Role.ADMIN);var owner=new AccountAiOwner(actor.accountId());var s=accepted(assistant.start(owner,actor));model.then(new ModelEndpoint.ModelResponse("",List.of(),List.of(new ModelEndpoint.ToolCall("open_reader_status_change",Map.of()))));model.then(ModelEndpoint.ModelResponse.answer("请前往页面办理"));assertThat(accepted(assistant.turn(s.sessionId(),"停用账户",owner,actor)).actions()).singleElement().satisfies(action->{assertThat(action.level()).isEqualTo("L3");assertThat(action.prefill()).isEmpty();});}
    @Test void utAi005_unknownToolIsRejectedBeforeExecution(){assertToolRejected(new ModelEndpoint.ToolCall("drop_database",Map.of()));}
    @Test void utAi006_roleForbiddenToolIsRejectedBeforeExecution(){var owner=visitor('b');var s=accepted(assistant.start(owner,null));model.then(new ModelEndpoint.ModelResponse("",List.of(),List.of(new ModelEndpoint.ToolCall("get_my_business",Map.of()))));assertThat(code(assistant.turn(s.sessionId(),"我的借阅",owner,null))).isEqualTo("MLS-AI-004");}
    @Test void utAi007_modelControlledSubjectAmountAndVersionAreRejected(){var actor=actor(Role.READER);var owner=new AccountAiOwner(actor.accountId());var s=accepted(assistant.start(owner,actor));model.then(new ModelEndpoint.ModelResponse("",List.of(),List.of(new ModelEndpoint.ToolCall("prefill_renewal",Map.of("accountId","999","amountCents",1,"version",9)))));assertThat(code(assistant.turn(s.sessionId(),"绕过",owner,actor))).isEqualTo("MLS-AI-004");}
    @Test void utAi007_recursiveUnknownAndControlledFieldsAreRejectedBeforeToolPersistence(){var actor=actor(Role.READER);var owner=new AccountAiOwner(actor.accountId());var s=accepted(assistant.start(owner,actor));model.then(new ModelEndpoint.ModelResponse("",List.of(),List.of(new ModelEndpoint.ToolCall("prefill_renewal",Map.of("txId","4001","nested",Map.of("readerId","999"))))));assertThat(code(assistant.turn(s.sessionId(),"嵌套绕过",owner,actor))).isEqualTo("MLS-AI-004");assertThat(eventTypes(s.sessionId(),owner,null)).doesNotContain("TOOL_CALL","ACTION_DRAFT","TOOL_RESULT");}
    @Test void utAi007_missingRequiredAndOversizedArgumentsAreRejected(){var actor=actor(Role.READER);var owner=new AccountAiOwner(actor.accountId());var first=accepted(assistant.start(owner,actor));model.then(new ModelEndpoint.ModelResponse("",List.of(),List.of(new ModelEndpoint.ToolCall("prefill_renewal",Map.of()))));assertThat(code(assistant.turn(first.sessionId(),"缺参数",owner,actor))).isEqualTo("MLS-AI-004");var second=accepted(assistant.start(owner,actor));model.then(new ModelEndpoint.ModelResponse("",List.of(),List.of(new ModelEndpoint.ToolCall("prefill_renewal",Map.of("txId","9".repeat(9000))))));assertThat(code(assistant.turn(second.sessionId(),"过大",owner,actor))).isEqualTo("MLS-AI-004");}
    @Test void utAi008_successPersistsGranularEvents(){var owner=visitor('c');var s=accepted(assistant.start(owner,null));accepted(assistant.turn(s.sessionId(),"开放馆藏",owner,null));assertThat(eventTypes(s.sessionId(),owner,null)).contains("SESSION_STARTED","USER_MESSAGE","MODEL_REQUEST","MODEL_RESPONSE","ASSISTANT_MESSAGE");}
    @Test void utAi009_failuresRetryTwiceAndDoNotConsumeQuota(){var owner=visitor('d');var s=accepted(assistant.start(owner,null));model.fail("timeout-1");model.fail("timeout-2");model.then(ModelEndpoint.ModelResponse.answer("ok"));var turn=accepted(assistant.turn(s.sessionId(),"重试",owner,null));assertThat(turn.quota().remaining()).isEqualTo(9);assertThat(model.requests()).hasSize(3);assertThat(eventTypes(s.sessionId(),owner,null)).contains("MODEL_RETRY");}
    @Test void utAi010_terminalModelFailureReturnsStableCodeAndNoQuotaCharge(){var owner=visitor('e');var s=accepted(assistant.start(owner,null));model.fail("1");model.fail("2");model.fail("3");assertThat(code(assistant.turn(s.sessionId(),"失败",owner,null))).isEqualTo("MLS-AI-003");assertThat(accepted(assistant.events(new AiEventCriteria(s.sessionId(),PageCursor.first()),owner,null)).items()).extracting(AiEvent::type).contains("MODEL_FAILURE");}
    @Test void utAi011_fourthRoundFinalAnswerIsNotMarkedMaxTurns(){var owner=visitor('f');var s=accepted(assistant.start(owner,null));for(int i=0;i<3;i++)model.then(new ModelEndpoint.ModelResponse("round",List.of(),List.of(new ModelEndpoint.ToolCall("search_titles",Map.of("q","x")))));model.then(ModelEndpoint.ModelResponse.answer("final"));var turn=accepted(assistant.turn(s.sessionId(),"循环",owner,null));assertThat(model.requests()).hasSize(4);assertThat(turn.answer()).isEqualTo("final");assertThat(turn.quota().remaining()).isEqualTo(6);assertThat(eventTypes(s.sessionId(),owner,null)).doesNotContain("TERMINATED");}
    @Test void utAi011_fourthRoundToolCallTerminatesWithoutFifthRequest(){var owner=visitor('7');var s=accepted(assistant.start(owner,null));for(int i=0;i<4;i++)model.then(new ModelEndpoint.ModelResponse("round",List.of(),List.of(new ModelEndpoint.ToolCall("search_titles",Map.of("q","x")))));accepted(assistant.turn(s.sessionId(),"耗尽",owner,null));assertThat(model.requests()).hasSize(4);assertThat(eventTypes(s.sessionId(),owner,null)).contains("TERMINATED");assertThat(jdbc.queryForObject("SELECT status FROM ai_session WHERE id=?",String.class,s.sessionId())).isEqualTo("MAX_TURNS");}
    @Test void utAi012_accountSessionRequiresSameCurrentAccount(){var actor=actor(Role.READER);var owner=new AccountAiOwner(actor.accountId());var s=accepted(assistant.start(owner,actor));var other=actor(Role.READER);assertThat(code(assistant.turn(s.sessionId(),"越权",new AccountAiOwner(other.accountId()),other))).isEqualTo("MLS-AI-005");}
    @Test void utAi013_wrongVisitorCredentialAndBearerDoNotUpgradeSession(){var owner=visitor('1');var s=accepted(assistant.start(owner,null));var actor=actor(Role.READER);assertThat(code(assistant.turn(s.sessionId(),"wrong",visitor('2'),actor))).isEqualTo("MLS-AI-005");accepted(assistant.turn(s.sessionId(),"仍是访客",owner,actor));assertThat(model.requests().getFirst().tools()).extracting(AiTool::name).doesNotContain("get_my_business");}
    @Test void utAi014_accountAndVisitorUseSeparateDefaultTenCallPools(){var visitor=visitor('9');var vs=accepted(assistant.start(visitor,null));var actor=actor(Role.READER);var account=new AccountAiOwner(actor.accountId());var as=accepted(assistant.start(account,actor));assertThat(accepted(assistant.turn(vs.sessionId(),"v",visitor,null)).quota().remaining()).isEqualTo(9);assertThat(accepted(assistant.turn(as.sessionId(),"a",account,actor)).quota().remaining()).isEqualTo(9);}
    @Test void utAi015_nextTurnSendsPriorCompleteTurnAndCurrentUserOnlyOnce(){
        var owner=visitor('0');var session=accepted(assistant.start(owner,null));
        model.then(ModelEndpoint.ModelResponse.answer("previous assistant answer"));
        accepted(assistant.turn(session.sessionId(),"previous user question",owner,null));
        model.then(ModelEndpoint.ModelResponse.answer("current assistant answer"));
        accepted(assistant.turn(session.sessionId(),"current user question",owner,null));
        var messages=model.requests().get(1).messages();
        assertThat(messages).contains(Map.of("role","user","content","previous user question"),
                Map.of("role","assistant","content","previous assistant answer"),
                Map.of("role","user","content","current user question"));
        assertThat(messages.stream().filter(m->"user".equals(m.get("role"))&&"current user question".equals(m.get("content"))).count()).isEqualTo(1);
    }
    @Test void utAi015_historyIsSessionScopedAndDropsAnUnansweredFailedTurn(){
        var owner=visitor('3');var first=accepted(assistant.start(owner,null));
        model.then(ModelEndpoint.ModelResponse.answer("private-session-answer"));
        accepted(assistant.turn(first.sessionId(),"private-session-question",owner,null));
        var second=accepted(assistant.start(owner,null));
        model.then(ModelEndpoint.ModelResponse.answer("isolated answer"));
        accepted(assistant.turn(second.sessionId(),"isolated current",owner,null));
        var isolated=model.requests().get(1).messages().toString();
        assertThat(isolated).contains("isolated current").doesNotContain("private-session-question","private-session-answer");

        model.fail("failure one");model.fail("failure two");model.fail("failure three");
        assertThat(code(assistant.turn(second.sessionId(),"unanswered failed turn",owner,null))).isEqualTo("MLS-AI-003");
        model.then(ModelEndpoint.ModelResponse.answer("recovered"));
        accepted(assistant.turn(second.sessionId(),"after failure",owner,null));
        var recovered=model.requests().getLast().messages().toString();
        assertThat(recovered).contains("after failure").doesNotContain("unanswered failed turn");
    }

    private void assertToolRejected(ModelEndpoint.ToolCall call){var owner=visitor('8');var s=accepted(assistant.start(owner,null));model.then(new ModelEndpoint.ModelResponse("",List.of(),List.of(call)));assertThat(code(assistant.turn(s.sessionId(),"bad",owner,null))).isEqualTo("MLS-AI-004");}
    private void turn(ActorContext actor){var owner=new AccountAiOwner(actor.accountId());var s=accepted(assistant.start(owner,actor));accepted(assistant.turn(s.sessionId(),"matrix",owner,actor));}
    private List<String> eventTypes(long id,AiSessionOwner owner,ActorContext admin){return accepted(assistant.events(new AiEventCriteria(id,PageCursor.of(null,100)),owner,admin)).items().stream().map(AiEvent::type).toList();}
    private ActorContext actor(Role role){long id=jdbc.queryForObject("INSERT INTO account(username,password_hash,display_name,role) VALUES (?,?,?,?) RETURNING id",Long.class,UUID.randomUUID().toString(),"x",role.name(),role.name());return new ActorContext(AccountId.of(id),role,Instant.now());}
    private VisitorAiOwner visitor(char c){return new VisitorAiOwner(new VisitorOwnerDigest(String.valueOf(c).repeat(64)));}
    @SuppressWarnings("unchecked") private <T>T accepted(ModuleResult<T> result){assertThat(result).isInstanceOf(ModuleResult.Accepted.class);return ((ModuleResult.Accepted<T>)result).value();}
    private String code(ModuleResult<?> result){assertThat(result).isInstanceOf(ModuleResult.Rejected.class);return ((ModuleResult.Rejected<?>)result).code();}
}
