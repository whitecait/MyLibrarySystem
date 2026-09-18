package com.library.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.ai.ScriptedModelEndpoint;
import com.library.ai.contract.ModelEndpoint;
import com.library.web.AiVisitorCookieAdapter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** IT-AI-001~007：真实 PostgreSQL + MockMvc + 脚本化模型系统边界。 */
@Testcontainers
@SpringBootTest(properties={"library.ai.visitor-daily-limit=1"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE,printOnlyOnFailure=false)
@Import(ITAI001To007Test.Config.class)
class ITAI001To007Test {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres@sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3");
    @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);}
    @TestConfiguration static class Config{@Bean @Primary ScriptedModelEndpoint scripted(){return new ScriptedModelEndpoint();}}
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired ScriptedModelEndpoint model;@Autowired JdbcTemplate jdbc;
    @BeforeEach void reset(){model.reset();jdbc.update("TRUNCATE ai_event,ai_message,ai_session,ai_visitor_quota_usage,ai_quota_usage,idempotency_key RESTART IDENTITY CASCADE");}

    @Test void itAi001_toolAuthorizationRejectsBeforeAnyQueryResult(){var started=startVisitor();model.then(new ModelEndpoint.ModelResponse("",List.of(),List.of(new ModelEndpoint.ToolCall("get_my_business",Map.of("accountId","1")))));assertThat(postMessage(started,"越权",null).getResponse().getStatus()).isEqualTo(422);assertThat(model.requests()).hasSize(1);}
    @Test void itAi002_l2AndL3OnlyProduceSessionEvents() throws Exception {var account=login("reader-l2");var s=startAccount(account);model.then(new ModelEndpoint.ModelResponse("",List.of(),List.of(new ModelEndpoint.ToolCall("prefill_renewal",Map.of("txId","4001")))));model.then(ModelEndpoint.ModelResponse.answer("ok"));var result=postMessage(s,"预填",account);assertThat(result.getResponse().getStatus()).isEqualTo(200);assertThat(result.getResponse().getContentAsString()).contains("L2","4001");assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_event WHERE event_type='ACTION_DRAFT'",Integer.class)).isOne();}
    @Test void itAi003_retryQuotaAndEventsAreDurable() throws Exception {var s=startVisitor();model.fail("timeout");model.then(ModelEndpoint.ModelResponse.answer("ok"));var result=postMessage(s,"重试",null);assertThat(result.getResponse().getStatus()).isEqualTo(200);assertThat(result.getResponse().getContentAsString()).contains("\"remaining\":0");assertThat(getEvents(s,null).getResponse().getContentAsString()).contains("MODEL_RETRY","MODEL_RESPONSE");}
    @Test void itAi004_wireRequestAlwaysDisablesThinking(){var s=startVisitor();postMessage(s,"模式",null);assertThat(model.requests()).singleElement().satisfies(r->{assertThat(r.thinking()).containsEntry("type","disabled");assertThat(r.timeout()).hasSeconds(8);});}
    @Test void itAi005_secureCookieOwnershipAndInvisibility() throws Exception {var s=startVisitor();assertThat(s.setCookie()).contains("Secure","HttpOnly","SameSite=Strict","Path=/");assertThat(s.body().contains(s.cookie().getValue())).isFalse();assertThat(postMessage(s,"ok",null).getResponse().getStatus()).isEqualTo(200);var wrong=new Started(s.sessionId(),new Cookie(AiVisitorCookieAdapter.NAME,"wrong"),s.setCookie(),s.body());assertThat(getEvents(wrong,null).getResponse().getStatus()).isEqualTo(404);mvc.perform(get("/api/v1/ai/sessions/{id}/events",s.sessionId())).andExpect(status().isUnauthorized());}
    @Test void itAi006_bearerDoesNotUpgradeVisitorButNewSessionUsesAccount() throws Exception {var visitor=startVisitor();var token=login("reader-owner");var result=postMessage(visitor,"我的借阅",token);assertThat(result.getResponse().getStatus()).isEqualTo(200);assertThat(model.requests().getFirst().tools()).extracting(t->t.name()).doesNotContain("get_my_business");var account=startAccount(token);assertThat(account.body()).contains("READER");}
    @Test void itAi007_twoVisitorsShareOneAtomicDailyQuota() throws Exception {var a=startVisitor();var b=startVisitor();try(var pool=Executors.newFixedThreadPool(2)){var fa=pool.submit(()->postMessage(a,"a",null).getResponse().getStatus());var fb=pool.submit(()->postMessage(b,"b",null).getResponse().getStatus());assertThat(List.of(fa.get(),fb.get())).containsExactlyInAnyOrder(200,429);}assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_visitor_quota_usage",Integer.class)).isOne();assertThat(jdbc.queryForObject("SELECT model_calls FROM ai_visitor_quota_usage",Integer.class)).isOne();}

    @Test void p1_startAndMessageIdempotencyPreserveOwnerAndFirstOutcome() throws Exception {
        var key="same-start-key";
        var first=startVisitor(key,null);var replay=startVisitor(key,null);
        assertThat(replay.sessionId()).isEqualTo(first.sessionId());assertThat(java.security.MessageDigest.isEqual(replay.cookie().getValue().getBytes(),first.cookie().getValue().getBytes())).isTrue();
        var second=startVisitor("second-session",first.cookie());assertThat(second.sessionId()).isNotEqualTo(first.sessionId());assertThat(java.security.MessageDigest.isEqual(second.cookie().getValue().getBytes(),first.cookie().getValue().getBytes())).isTrue();
        var messageKey="same-message";var one=postMessage(first,"same",null,messageKey);var two=postMessage(first,"same",null,messageKey);
        assertThat(two.getResponse().getContentAsString()).isEqualTo(one.getResponse().getContentAsString());assertThat(model.requests()).hasSize(1);
        var conflict=postMessage(first,"different",null,messageKey);assertThat(conflict.getResponse().getStatus()).isEqualTo(409);assertThat(conflict.getResponse().getContentAsString()).contains("MLS-IDEMP-002");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_session WHERE visitor_owner_digest=?",Integer.class,sha(first.cookie().getValue()))).isEqualTo(2);
        var raw=first.cookie().getValue();assertThat(jdbc.queryForList("SELECT response_snapshot::text snapshot FROM idempotency_key").stream().noneMatch(row->String.valueOf(row.get("snapshot")).contains(raw))).isTrue();
        assertThat(jdbc.queryForList("SELECT payload::text payload FROM ai_event").stream().noneMatch(row->String.valueOf(row.get("payload")).contains(raw))).isTrue();
    }

    @Test void p1_accountIdAndConcreteSessionPathPartitionIdempotencyScope() throws Exception {
        var a=login("scope-a");var b=login("scope-b");var sharedStartKey="shared-account-start";
        var aFirst=startAccount(a,sharedStartKey);var bFirst=startAccount(b,sharedStartKey);
        assertThat(aFirst.sessionId()).isNotEqualTo(bFirst.sessionId());
        var aSecond=startAccount(a,"scope-a-second");var sharedMessageKey="shared-message-key";
        assertThat(postMessage(aFirst,"same",a,sharedMessageKey).getResponse().getStatus()).isEqualTo(200);
        assertThat(postMessage(aSecond,"same",a,sharedMessageKey).getResponse().getStatus()).isEqualTo(200);
        assertThat(model.requests()).hasSize(2);
    }

    @Test void p1_adminMayReadEventsButNeverSendIntoAnotherOwnersSession() throws Exception {
        var visitor=startVisitor();var ownerless=new Started(visitor.sessionId(),null,null,null);var admin=login("admin-events","ADMIN");var reader=login("reader-events","READER");
        assertThat(getEvents(ownerless,admin).getResponse().getStatus()).isEqualTo(200);
        assertThat(getEvents(ownerless,reader).getResponse().getStatus()).isEqualTo(404);
        assertThat(postMessage(ownerless,"admin write",admin).getResponse().getStatus()).isEqualTo(404);
        var noCredential=new Started(visitor.sessionId(),null,null,null);
        assertThat(getEvents(noCredential,null).getResponse().getStatus()).isEqualTo(401);
    }

    private Started startVisitor(){return startVisitor(UUID.randomUUID().toString(),null);}
    private Started startVisitor(String key,Cookie existing){try{var request=post("/api/v1/ai/sessions").header("Idempotency-Key",key);if(existing!=null)request.cookie(existing);var result=mvc.perform(request).andExpect(status().isOk()).andReturn();var node=json.readTree(result.getResponse().getContentAsString());var raw=result.getResponse().getHeader("Set-Cookie").split("[=;]",3)[1];return new Started(node.at("/data/sessionId").asLong(),new Cookie(AiVisitorCookieAdapter.NAME,raw),result.getResponse().getHeader("Set-Cookie"),result.getResponse().getContentAsString());}catch(Exception e){throw new AssertionError(e);}}
    private Started startAccount(String token){return startAccount(token,UUID.randomUUID().toString());}
    private Started startAccount(String token,String key){try{var result=mvc.perform(post("/api/v1/ai/sessions").header("Idempotency-Key",key).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn();var node=json.readTree(result.getResponse().getContentAsString());return new Started(node.at("/data/sessionId").asLong(),null,result.getResponse().getHeader("Set-Cookie"),result.getResponse().getContentAsString());}catch(Exception e){throw new AssertionError(e);}}
    private org.springframework.test.web.servlet.MvcResult postMessage(Started s,String message,String bearer){return postMessage(s,message,bearer,UUID.randomUUID().toString());}
    private org.springframework.test.web.servlet.MvcResult postMessage(Started s,String message,String bearer,String key){try{var request=post("/api/v1/ai/sessions/{id}/messages",s.sessionId()).header("Idempotency-Key",key).contentType("application/json").content(json.writeValueAsString(Map.of("message",message)));if(s.cookie()!=null)request.cookie(s.cookie());if(bearer!=null)request.header("Authorization","Bearer "+bearer);return mvc.perform(request).andReturn();}catch(Exception e){throw new AssertionError(e);}}
    private org.springframework.test.web.servlet.MvcResult getEvents(Started s,String bearer){try{var request=get("/api/v1/ai/sessions/{id}/events",s.sessionId());if(s.cookie()!=null)request.cookie(s.cookie());if(bearer!=null)request.header("Authorization","Bearer "+bearer);return mvc.perform(request).andReturn();}catch(Exception e){throw new AssertionError(e);}}
    private String login(String username){return login(username,"READER");}
    private String login(String username,String role){try{var token="test-token-"+UUID.randomUUID();var id=jdbc.queryForObject("INSERT INTO account(username,password_hash,display_name,role) VALUES (?,?,?,?) RETURNING id",Long.class,username,"x",username,role);var digest=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));jdbc.update("INSERT INTO auth_session(account_id,token_hash,expires_at) VALUES (?,?,now()+interval '1 day')",id,digest);return token;}catch(Exception e){throw new AssertionError(e);}}
    private String sha(String raw){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new AssertionError(e);}}
    record Started(long sessionId,Cookie cookie,String setCookie,String body){}
}
