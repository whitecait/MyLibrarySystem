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
@AutoConfigureMockMvc
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
    @Test void itAi005_secureCookieOwnershipAndInvisibility() throws Exception {var s=startVisitor();assertThat(s.setCookie()).contains("Secure","HttpOnly","SameSite=Strict","Path=/");assertThat(s.body()).doesNotContain(s.cookie().getValue());assertThat(postMessage(s,"ok",null).getResponse().getStatus()).isEqualTo(200);var wrong=new Started(s.sessionId(),new Cookie(AiVisitorCookieAdapter.NAME,"wrong"),s.setCookie(),s.body());assertThat(getEvents(wrong,null).getResponse().getStatus()).isEqualTo(404);mvc.perform(get("/api/v1/ai/sessions/{id}/events",s.sessionId())).andExpect(status().isUnauthorized());}
    @Test void itAi006_bearerDoesNotUpgradeVisitorButNewSessionUsesAccount() throws Exception {var visitor=startVisitor();var token=login("reader-owner");var result=postMessage(visitor,"我的借阅",token);assertThat(result.getResponse().getStatus()).isEqualTo(200);assertThat(model.requests().getFirst().tools()).extracting(t->t.name()).doesNotContain("get_my_business");var account=startAccount(token);assertThat(account.body()).contains("READER");}
    @Test void itAi007_twoVisitorsShareOneAtomicDailyQuota() throws Exception {var a=startVisitor();var b=startVisitor();try(var pool=Executors.newFixedThreadPool(2)){var fa=pool.submit(()->postMessage(a,"a",null).getResponse().getStatus());var fb=pool.submit(()->postMessage(b,"b",null).getResponse().getStatus());assertThat(List.of(fa.get(),fb.get())).containsExactlyInAnyOrder(200,429);}assertThat(jdbc.queryForObject("SELECT model_calls FROM ai_visitor_quota_usage WHERE usage_date=current_date",Integer.class)).isOne();}

    private Started startVisitor(){try{var result=mvc.perform(post("/api/v1/ai/sessions").header("Idempotency-Key",UUID.randomUUID())).andExpect(status().isOk()).andReturn();var node=json.readTree(result.getResponse().getContentAsString());var raw=result.getResponse().getHeader("Set-Cookie").split("[=;]",3)[1];return new Started(node.at("/data/sessionId").asLong(),new Cookie(AiVisitorCookieAdapter.NAME,raw),result.getResponse().getHeader("Set-Cookie"),result.getResponse().getContentAsString());}catch(Exception e){throw new AssertionError(e);}}
    private Started startAccount(String token){try{var result=mvc.perform(post("/api/v1/ai/sessions").header("Idempotency-Key",UUID.randomUUID()).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn();var node=json.readTree(result.getResponse().getContentAsString());return new Started(node.at("/data/sessionId").asLong(),null,result.getResponse().getHeader("Set-Cookie"),result.getResponse().getContentAsString());}catch(Exception e){throw new AssertionError(e);}}
    private org.springframework.test.web.servlet.MvcResult postMessage(Started s,String message,String bearer){try{var request=post("/api/v1/ai/sessions/{id}/messages",s.sessionId()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(json.writeValueAsString(Map.of("message",message)));if(s.cookie()!=null)request.cookie(s.cookie());if(bearer!=null)request.header("Authorization","Bearer "+bearer);return mvc.perform(request).andReturn();}catch(Exception e){throw new AssertionError(e);}}
    private org.springframework.test.web.servlet.MvcResult getEvents(Started s,String bearer){try{var request=get("/api/v1/ai/sessions/{id}/events",s.sessionId());if(s.cookie()!=null)request.cookie(s.cookie());if(bearer!=null)request.header("Authorization","Bearer "+bearer);return mvc.perform(request).andReturn();}catch(Exception e){throw new AssertionError(e);}}
    private String login(String username){try{var token="test-token-"+UUID.randomUUID();var id=jdbc.queryForObject("INSERT INTO account(username,password_hash,display_name,role) VALUES (?,?,?,'READER') RETURNING id",Long.class,username,"x",username);var digest=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));jdbc.update("INSERT INTO auth_session(account_id,token_hash,expires_at) VALUES (?,?,now()+interval '1 day')",id,digest);return token;}catch(Exception e){throw new AssertionError(e);}}
    record Started(long sessionId,Cookie cookie,String setCookie,String body){}
}
