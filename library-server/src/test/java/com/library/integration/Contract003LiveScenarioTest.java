package com.library.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.library.ai.ScriptedModelEndpoint;
import com.library.ai.contract.ModelEndpoint;
import com.library.contract.ContractScenarios;
import com.library.contract.InstanceValidator;
import com.library.contract.OpenApiDocument;
import com.library.contract.time.BusinessClock;
import com.library.web.AiVisitorCookieAdapter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * CONTRACT-003 live gate: every approved shared scenario traverses a real Spring MVC
 * controller and the PostgreSQL adapters. Only the external model-provider boundary is
 * deterministic; no controller or application service is stubbed.
 */
@Testcontainers
@SpringBootTest(properties = {
        "library.ai.visitor-daily-limit=2",
        "library.ai.account-daily-limit=2"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
@Import(Contract003LiveScenarioTest.Config.class)
class Contract003LiveScenarioTest {

    private static final String POSTGRES_IMAGE =
            "postgres@sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withLabel("com.mylibrary.p1-stage", "p1-core-20260926");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        ScriptedModelEndpoint scriptedModelEndpoint() {
            return new ScriptedModelEndpoint();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ScriptedModelEndpoint model;

    private final ContractScenarios scenarios = new ContractScenarios();
    private final OpenApiDocument openApi = new OpenApiDocument();
    private final Set<String> executed = new LinkedHashSet<>();

    private VisitorSession visitor;
    private String readerToken;
    private AccountSession accountAiSession;

    @Test
    void allFourteenSharedScenariosExecuteThroughLiveControllers() throws Exception {
        model.reset();
        var registry = new LinkedHashMap<String, ScenarioExecution>();
        registry.put("AUTH-REGISTER-SUCCESS", this::registerReader);
        registry.put("CATALOG-PUBLIC-SEARCH", this::searchCatalog);
        registry.put("RESERVATION-WAITLIST-CONFIRMATION", this::reservationNeedsWaitlistConfirmation);
        registry.put("WALKIN-MIXED-BATCH", this::walkInBatchPreservesMixedOutcomes);
        registry.put("RETURN-WITH-FINE", this::returnCreatesFine);
        registry.put("ORDER-CONCURRENT-CONFLICT", this::secondOrderConflicts);
        registry.put("REVIEW-TARGET-CHANGED", this::changedReviewTargetConflicts);
        registry.put("AI-VISITOR-SESSION-START", this::startVisitorSession);
        registry.put("AI-VISITOR-COOKIE-CONTINUES-SESSION", this::visitorCookieContinuesSession);
        registry.put("AI-VISITOR-WRONG-COOKIE-INVISIBLE", this::wrongVisitorCookieIsInvisible);
        registry.put("AI-VISITOR-LOGIN-DOES-NOT-UPGRADE", this::loginDoesNotUpgradeVisitorSession);
        registry.put("AI-VISITORS-SHARE-GLOBAL-QUOTA", this::visitorsShareGlobalQuota);
        registry.put("AI-L2-PREFILL", this::accountSessionProducesL2Prefill);
        registry.put("AI-QUOTA-EXHAUSTED", this::accountQuotaExhaustionSkipsModel);

        var approvedIds = new LinkedHashSet<String>();
        scenarios.all().forEach(scenario -> approvedIds.add(scenario.path("id").asText()));
        assertThat(registry.keySet()).containsExactlyElementsOf(approvedIds);

        for (var entry : registry.entrySet()) {
            entry.getValue().execute(entry.getKey());
        }
        assertThat(executed).containsExactlyElementsOf(approvedIds);
    }

    private void registerReader(String id) throws Exception {
        var scenario = scenarios.byId(id);
        var body = ((ObjectNode) scenario.at("/request/body").deepCopy())
                .put("username", "contract-reader-" + UUID.randomUUID());
        execute(id, post("/api/v1/auth/registrations")
                        .header("Idempotency-Key", header(scenario, "Idempotency-Key"))
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()),
                "/api/v1/auth/registrations", body, Set.of("Idempotency-Key"), Set.of());
    }

    private void searchCatalog(String id) throws Exception {
        jdbc.update("INSERT INTO book_title(title,author,category,price_cents) VALUES (?,?,?,?)",
                "database systems", "contract author", "计算机", 9900);
        var scenario = scenarios.byId(id);
        execute(id, get(scenario.at("/request/path").asText()), scenario.at("/request/path").asText(),
                null, Set.of(), Set.of());
    }

    private void reservationNeedsWaitlistConfirmation(String id) throws Exception {
        var reader = account("reservation", "READER");
        long title = title("waitlist title");
        copy(title, "LENT");
        var scenario = scenarios.byId(id);
        var body = ((ObjectNode) scenario.at("/request/body").deepCopy())
                .put("titleId", Long.toString(title))
                .put("reservationDate", LocalDate.now(BusinessClock.ZONE).plusDays(1).toString())
                .put("confirmWaitlist", false);
        execute(id, authenticatedPost("/api/v1/reservations", scenario, reader.token(), body),
                "/api/v1/reservations", body, Set.of("Idempotency-Key", "Authorization"), Set.of("bearerAuth"));
    }

    private void walkInBatchPreservesMixedOutcomes(String id) throws Exception {
        var reader = account("walkin", "READER");
        var librarian = account("walkin-librarian", "LIBRARIAN");
        long availableTitle = title("available title");
        long availableCopy = copy(availableTitle, "SHELF_READY");
        long unavailableTitle = title("unavailable title");
        long unavailableCopy = copy(unavailableTitle, "MAINTENANCE");
        var scenario = scenarios.byId(id);
        var body = (ObjectNode) scenario.at("/request/body").deepCopy();
        body.put("readerId", Long.toString(reader.id()));
        ((ObjectNode) body.at("/items/0")).put("titleId", Long.toString(availableTitle))
                .put("copyId", Long.toString(availableCopy));
        ((ObjectNode) body.at("/items/1")).put("titleId", Long.toString(unavailableTitle))
                .put("copyId", Long.toString(unavailableCopy));
        execute(id, authenticatedPost("/api/v1/walk-in-loans", scenario, librarian.token(), body),
                "/api/v1/walk-in-loans", body, Set.of("Idempotency-Key", "Authorization"), Set.of("bearerAuth"));
    }

    private void returnCreatesFine(String id) throws Exception {
        var reader = account("return-reader", "READER");
        var librarian = account("return-librarian", "LIBRARIAN");
        long title = title("overdue return title");
        long copy = copy(title, "LENT");
        jdbc.update("""
                INSERT INTO borrow_transaction(reader_id,title_id,copy_id,status,reservation_date,due_date)
                VALUES (?,?,?,'LENT',current_date-35,current_date-5)
                """, reader.id(), title, copy);
        var scenario = scenarios.byId(id);
        var body = ((ObjectNode) scenario.at("/request/body").deepCopy()).put("copyId", Long.toString(copy));
        execute(id, authenticatedPost("/api/v1/returns", scenario, librarian.token(), body),
                "/api/v1/returns", body, Set.of("Idempotency-Key", "Authorization"), Set.of("bearerAuth"));
    }

    private void secondOrderConflicts(String id) throws Exception {
        var reader = account("order-reader", "READER");
        long fine = fineWithOccupiedOrder(reader.id(), "order");
        long existingOrder = jdbc.queryForObject("SELECT id FROM payment_order WHERE fine_id=?", Long.class, fine);
        var scenario = scenarios.byId(id);
        var path = "/api/v1/fines/" + fine + "/orders";
        execute(id, post(path).header("Authorization", "Bearer " + reader.token())
                        .header("Idempotency-Key", header(scenario, "Idempotency-Key")),
                path, null, Set.of("Idempotency-Key", "Authorization"), Set.of("bearerAuth"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_order WHERE fine_id=?", Integer.class, fine))
                .isOne();
        assertThat(jdbc.queryForObject("SELECT id FROM payment_order WHERE fine_id=?", Long.class, fine))
                .isEqualTo(existingOrder);
    }

    private void changedReviewTargetConflicts(String id) throws Exception {
        var target = account("review-owner", "READER");
        var applicant = account("review-applicant", "LIBRARIAN");
        var admin = account("review-admin", "ADMIN");
        long review = jdbc.queryForObject("""
                INSERT INTO review_request(applicant_id,target_type,target_id,reason,action_type,
                                           action_payload,target_version)
                VALUES (?,'ACCOUNT_CHANGE',?,'contract fixture','SUSPEND','{}'::jsonb,0)
                RETURNING id
                """, Long.class, applicant.id(), target.id());
        jdbc.update("UPDATE account SET status='SUSPENDED', version=version+1 WHERE id=?", target.id());
        var scenario = scenarios.byId(id);
        var path = "/api/v1/reviews/" + review + "/decision";
        var body = scenario.at("/request/body");
        execute(id, authenticatedPost(path, scenario, admin.token(), body), path, body,
                Set.of("Idempotency-Key", "Authorization"), Set.of("bearerAuth"));
        assertThat(jdbc.queryForObject("SELECT decision FROM review_request WHERE id=?", String.class, review))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT version FROM account WHERE id=?", Long.class, target.id()))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT status FROM account WHERE id=?", String.class, target.id()))
                .isEqualTo("SUSPENDED");
    }

    private void startVisitorSession(String id) throws Exception {
        var scenario = scenarios.byId(id);
        var result = execute(id, post("/api/v1/ai/sessions")
                        .header("Idempotency-Key", header(scenario, "Idempotency-Key")),
                "/api/v1/ai/sessions", null, Set.of("Idempotency-Key"), Set.of());
        var response = json.readTree(result.getResponse().getContentAsString());
        var setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("Secure", "HttpOnly", "SameSite=Strict", "Path=/");
        var raw = setCookie.split("[=;]", 3)[1];
        assertThat(result.getResponse().getContentAsString()).doesNotContain(raw);
        visitor = new VisitorSession(response.at("/data/sessionId").asLong(),
                new Cookie(AiVisitorCookieAdapter.NAME, raw));
    }

    private void visitorCookieContinuesSession(String id) throws Exception {
        requireVisitor();
        var scenario = scenarios.byId(id);
        model.then(new ModelEndpoint.ModelResponse("以下为公开馆藏结果", List.of("title:2001"), List.of()));
        var path = "/api/v1/ai/sessions/" + visitor.id() + "/messages";
        execute(id, post(path).cookie(visitor.cookie())
                        .header("Idempotency-Key", header(scenario, "Idempotency-Key"))
                        .contentType(MediaType.APPLICATION_JSON).content(scenario.at("/request/body").toString()),
                path, scenario.at("/request/body"), Set.of("Cookie", "Idempotency-Key"), Set.of("aiVisitorCookie"));
        assertThat(model.requests().getLast().tools()).allMatch(tool -> "L1".equals(tool.level()));
    }

    private void wrongVisitorCookieIsInvisible(String id) throws Exception {
        requireVisitor();
        var scenario = scenarios.byId(id);
        var path = "/api/v1/ai/sessions/" + visitor.id() + "/events?limit=20";
        execute(id, get(path).cookie(new Cookie(AiVisitorCookieAdapter.NAME, "wrong-contract-credential")),
                path, null, Set.of("Cookie"), Set.of("aiVisitorCookie"));
    }

    private void loginDoesNotUpgradeVisitorSession(String id) throws Exception {
        requireVisitor();
        readerToken = account("ai-reader", "READER").token();
        var scenario = scenarios.byId(id);
        model.then(ModelEndpoint.ModelResponse.answer("访客会话不能查询本人借阅"));
        var path = "/api/v1/ai/sessions/" + visitor.id() + "/messages";
        execute(id, post(path).cookie(visitor.cookie()).header("Authorization", "Bearer " + readerToken)
                        .header("Idempotency-Key", header(scenario, "Idempotency-Key"))
                        .contentType(MediaType.APPLICATION_JSON).content(scenario.at("/request/body").toString()),
                path, scenario.at("/request/body"), Set.of("Cookie", "Authorization", "Idempotency-Key"),
                Set.of("aiVisitorCookie", "bearerAuth"));
        assertThat(model.requests().getLast().tools()).extracting(tool -> tool.name())
                .doesNotContain("get_my_business", "prefill_renewal");
    }

    private void visitorsShareGlobalQuota(String id) throws Exception {
        var second = startAdditionalVisitor();
        var before = model.requests().size();
        var scenario = scenarios.byId(id);
        var path = "/api/v1/ai/sessions/" + second.id() + "/messages";
        execute(id, post(path).cookie(second.cookie())
                        .header("Idempotency-Key", header(scenario, "Idempotency-Key"))
                        .contentType(MediaType.APPLICATION_JSON).content(scenario.at("/request/body").toString()),
                path, scenario.at("/request/body"), Set.of("Cookie", "Idempotency-Key"), Set.of("aiVisitorCookie"));
        assertThat(model.requests()).hasSize(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_visitor_quota_usage", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT model_calls FROM ai_visitor_quota_usage", Integer.class)).isEqualTo(2);
    }

    private void accountSessionProducesL2Prefill(String id) throws Exception {
        assertThat(readerToken).isNotBlank();
        accountAiSession = startAccountSession(readerToken);
        var scenario = scenarios.byId(id);
        model.then(new ModelEndpoint.ModelResponse("", List.of(),
                List.of(new ModelEndpoint.ToolCall("contract-l2", "prefill_renewal", Map.of("txId", "4001")))));
        model.then(ModelEndpoint.ModelResponse.answer("请核对后提交"));
        var path = "/api/v1/ai/sessions/" + accountAiSession.id() + "/messages";
        int businessFactsBefore = jdbc.queryForObject("SELECT count(*) FROM borrow_transaction", Integer.class);
        var result = execute(id, post(path).header("Authorization", "Bearer " + readerToken)
                        .header("Idempotency-Key", header(scenario, "Idempotency-Key"))
                        .contentType(MediaType.APPLICATION_JSON).content(scenario.at("/request/body").toString()),
                path, scenario.at("/request/body"), Set.of("Authorization", "Idempotency-Key"), Set.of("bearerAuth"));
        assertThat(json.readTree(result.getResponse().getContentAsString()).at("/data/actions/0/level").asText())
                .isEqualTo("L2");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM borrow_transaction", Integer.class))
                .isEqualTo(businessFactsBefore);
    }

    private void accountQuotaExhaustionSkipsModel(String id) throws Exception {
        assertThat(accountAiSession).isNotNull();
        var before = model.requests().size();
        var scenario = scenarios.byId(id);
        var path = "/api/v1/ai/sessions/" + accountAiSession.id() + "/messages";
        execute(id, post(path).header("Authorization", "Bearer " + readerToken)
                        .header("Idempotency-Key", header(scenario, "Idempotency-Key"))
                        .contentType(MediaType.APPLICATION_JSON).content(scenario.at("/request/body").toString()),
                path, scenario.at("/request/body"), Set.of("Authorization", "Idempotency-Key"), Set.of("bearerAuth"));
        assertThat(model.requests()).hasSize(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_event WHERE session_id=? AND event_type='QUOTA_BLOCKED'",
                Integer.class, accountAiSession.id())).isOne();
    }

    private MvcResult execute(String id, MockHttpServletRequestBuilder request, String actualPath,
                              JsonNode requestBody, Set<String> suppliedHeaders,
                              Set<String> suppliedSecuritySchemes) throws Exception {
        var scenario = scenarios.byId(id);
        var method = scenario.at("/request/method").asText().toLowerCase();
        var contractPath = contractPath(actualPath);
        var actualOperation = openApi.operation(method, contractPath);
        var scenarioOperation = openApi.operation(method, scenarios.operationPath(scenario));
        assertThat(actualOperation).as("%s operation", id).isNotNull();
        assertThat(actualOperation.path("operationId").asText()).as("%s operation binding", id)
                .isEqualTo(scenarioOperation.path("operationId").asText());
        assertThat(method).isEqualTo(scenario.at("/request/method").asText().toLowerCase());

        assertThat(suppliedHeaders).containsAll(openApi.requiredHeaders(method, contractPath));
        assertSecuritySatisfied(id, method, contractPath, suppliedSecuritySchemes);
        var requestSchema = openApi.requestSchema(method, contractPath);
        if (requestBody != null) {
            assertThat(requestSchema).as("%s request schema", id).isNotNull();
            assertThat(InstanceValidator.validate(requestSchema, requestBody))
                    .as("%s request violates OpenAPI", id).isEmpty();
        } else {
            assertThat(requestSchema).as("%s must not invent a request body", id).isNull();
        }

        var result = mvc.perform(request).andReturn();
        int expectedStatus = scenario.at("/response/httpStatus").asInt();
        assertThat(result.getResponse().getStatus()).as("%s status: %s", id,
                result.getResponse().getContentAsString()).isEqualTo(expectedStatus);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        var responseBody = json.readTree(result.getResponse().getContentAsString());
        assertThat(responseBody.path("code").asText()).as("%s code", id)
                .isEqualTo(scenario.at("/response/body/code").asText());
        var responseSchema = openApi.responseSchema(method, contractPath, expectedStatus);
        assertThat(responseSchema).as("%s response schema", id).isNotNull();
        assertThat(InstanceValidator.validate(responseSchema, responseBody))
                .as("%s response violates OpenAPI", id).isEmpty();
        scenario.at("/response/headers").fieldNames().forEachRemaining(header ->
                assertThat(result.getResponse().getHeader(header)).as("%s response header %s", id, header).isNotBlank());
        executed.add(id);
        return result;
    }

    private void assertSecuritySatisfied(String id, String method, String path, Set<String> supplied) {
        var operation = openApi.operation(method, path);
        var security = operation.has("security") ? operation.get("security") : openApi.root().get("security");
        assertThat(security).as("%s security declaration", id).isNotNull();
        if (security.isArray() && security.isEmpty()) {
            return;
        }
        var satisfied = false;
        for (var requirement : security) {
            if (requirement.isObject() && requirement.isEmpty()) {
                satisfied = true;
                break;
            }
            var names = new LinkedHashSet<String>();
            requirement.fieldNames().forEachRemaining(names::add);
            if (supplied.containsAll(names)) {
                satisfied = true;
                break;
            }
        }
        assertThat(satisfied).as("%s must supply one declared OpenAPI security alternative; supplied=%s",
                id, supplied).isTrue();
    }

    private MockHttpServletRequestBuilder authenticatedPost(String path, JsonNode scenario, String token,
                                                            JsonNode body) {
        return post(path).header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", header(scenario, "Idempotency-Key"))
                .contentType(MediaType.APPLICATION_JSON).content(body.toString());
    }

    private VisitorSession startAdditionalVisitor() throws Exception {
        var result = mvc.perform(post("/api/v1/ai/sessions")
                .header("Idempotency-Key", "fixture-ai-start-" + UUID.randomUUID())).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var id = json.readTree(result.getResponse().getContentAsString()).at("/data/sessionId").asLong();
        var raw = result.getResponse().getHeader("Set-Cookie").split("[=;]", 3)[1];
        return new VisitorSession(id, new Cookie(AiVisitorCookieAdapter.NAME, raw));
    }

    private AccountSession startAccountSession(String token) throws Exception {
        var result = mvc.perform(post("/api/v1/ai/sessions")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "fixture-account-start-" + UUID.randomUUID())).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return new AccountSession(json.readTree(result.getResponse().getContentAsString())
                .at("/data/sessionId").asLong());
    }

    private Account account(String prefix, String role) {
        try {
            var username = prefix + "-" + UUID.randomUUID();
            var token = "contract-token-" + UUID.randomUUID();
            long id = jdbc.queryForObject("""
                    INSERT INTO account(username,password_hash,display_name,role)
                    VALUES (?,?,?,?) RETURNING id
                    """, Long.class, username, "fixture-only", username, role);
            jdbc.update("INSERT INTO auth_session(account_id,token_hash,expires_at) VALUES (?,?,now()+interval '1 day')",
                    id, sha256(token));
            return new Account(id, token);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private long title(String name) {
        return jdbc.queryForObject("INSERT INTO book_title(title,price_cents) VALUES (?,9900) RETURNING id",
                Long.class, name + "-" + UUID.randomUUID());
    }

    private long copy(long title, String status) {
        return jdbc.queryForObject("""
                INSERT INTO book_copy(title_id,copy_no,status) VALUES (?,?,?) RETURNING id
                """, Long.class, title, "ct-" + UUID.randomUUID().toString().substring(0, 12), status);
    }

    private long fineWithOccupiedOrder(long reader, String prefix) {
        long title = title(prefix + " fine title");
        long copy = copy(title, "SHELF_READY");
        long transaction = jdbc.queryForObject("""
                INSERT INTO borrow_transaction(reader_id,title_id,copy_id,status,reservation_date,due_date)
                VALUES (?,?,?,'RETURNED',current_date-40,current_date-10) RETURNING id
                """, Long.class, reader, title, copy);
        long fine = jdbc.queryForObject("""
                INSERT INTO fine(reader_id,source_tx_id,amount_cents) VALUES (?,?,500) RETURNING id
                """, Long.class, reader, transaction);
        jdbc.update("INSERT INTO payment_order(fine_id,expires_at) VALUES (?,now()+interval '15 minutes')", fine);
        return fine;
    }

    private String header(JsonNode scenario, String name) {
        return scenario.at("/request/headers").path(name).asText();
    }

    private String contractPath(String fullPath) {
        var withoutPrefix = fullPath.replaceFirst("^/api/v1", "");
        int query = withoutPrefix.indexOf('?');
        return query < 0 ? withoutPrefix : withoutPrefix.substring(0, query);
    }

    private String sha256(String raw) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(StandardCharsets.UTF_8)));
    }

    private void requireVisitor() {
        assertThat(visitor).as("visitor scenario dependency").isNotNull();
    }

    @FunctionalInterface
    private interface ScenarioExecution {
        void execute(String id) throws Exception;
    }

    private record Account(long id, String token) {}
    private record VisitorSession(long id, Cookie cookie) {}
    private record AccountSession(long id) {}
}
