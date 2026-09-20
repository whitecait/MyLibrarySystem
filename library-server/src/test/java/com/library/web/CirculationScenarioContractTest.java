package com.library.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.contract.ContractScenarios;
import com.library.contract.InstanceValidator;
import com.library.contract.OpenApiDocument;
import com.library.identity.internal.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.junit.jupiter.Container;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B2 契约接合与联调门（后端侧）：
 * RESERVATION-WAITLIST-CONFIRMATION、WALKIN-MIXED-BATCH、RETURN-WITH-FINE 活体执行；
 * IT-QUERY-001 到馆统计/我的业务组合与权限。
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CirculationScenarioContractTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            "postgres@sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;

    private final OpenApiDocument contract = new OpenApiDocument();
    private final ContractScenarios scenarios = new ContractScenarios();
    private final ObjectMapper json = new ObjectMapper();

    private String registerAndLogin(String name) throws Exception {
        var username = name + "-" + UUID.randomUUID().toString().substring(0, 8);
        post("/api/v1/auth/registrations", null, key(),
                "{\"username\":\"%s\",\"password\":\"password123\",\"displayName\":\"%s\"}"
                        .formatted(username, name));
        var session = post("/api/v1/auth/sessions", null, key(),
                "{\"username\":\"%s\",\"password\":\"password123\"}".formatted(username));
        return json.readTree(session.getBody()).at("/data/accessToken").asText();
    }

    private long seedLibrarian() {
        var username = "lib-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO account (username, password_hash, display_name, role)
                VALUES (?, ?, ?, 'LIBRARIAN')
                """, username, PasswordHasher.hash("password123"), username);
        return jdbc.queryForObject("SELECT id FROM account WHERE username = ?", Long.class, username);
    }

    private String loginLibrarian(long librarianId) throws Exception {
        var username = jdbc.queryForObject(
                "SELECT username FROM account WHERE id = ?", String.class, librarianId);
        var session = post("/api/v1/auth/sessions", null, key(),
                "{\"username\":\"%s\",\"password\":\"password123\"}".formatted(username));
        return json.readTree(session.getBody()).at("/data/accessToken").asText();
    }

    private long seedTitleWithCopies(String title, int shelvedCopies) {
        var titleId = jdbc.queryForObject("""
                INSERT INTO book_title (title, price_cents) VALUES (?, 9900) RETURNING id
                """, Long.class, title);
        for (int i = 0; i < shelvedCopies; i++) {
            jdbc.update("""
                    INSERT INTO book_copy (title_id, copy_no, status) VALUES (?, ?, 'SHELF_READY')
                    """, titleId, "ITC-" + titleId + "-" + i);
        }
        return titleId;
    }

    // 场景 3：无可用副本 → WAITLIST_PENDING_CONFIRMATION（txId=null）；确认 → 入队
    @Test
    void reservationWaitlistScenarioExecutesLive() throws Exception {
        var readerToken = registerAndLogin("waitlist-reader");
        var emptyTitle = seedTitleWithCopies("无副本预约书", 0);
        var scenario = scenarios.byId("RESERVATION-WAITLIST-CONFIRMATION");
        // 候补路径只对非当日预约成立：当日无副本走 MANUAL_HANDLING（CirculationModuleImpl 分界）
        var futureDate = java.time.LocalDate.now().plusDays(2).toString();

        var body = """
                {"titleId":"%d","reservationDate":"%s","pickupSlot":"AM",
                 "channel":"ONLINE","confirmWaitlist":false}
                """.formatted(emptyTitle, futureDate);
        var response = post("/api/v1/reservations", readerToken, key(), body);
        assertThat(response.getStatusCode().value())
                .isEqualTo(scenario.at("/response/httpStatus").asInt());
        var result = json.readTree(response.getBody());
        assertThat(result.path("code").asText()).isEqualTo("MLS-CIRC-S001");
        assertThat(result.at("/data/outcome").asText()).isEqualTo("WAITLIST_PENDING_CONFIRMATION");
        assertThat(result.at("/data/txId").isNull()).isTrue();
        // 无业务事实：该书目无任何事务
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM borrow_transaction WHERE title_id = ?",
                Integer.class, emptyTitle)).isZero();

        var confirm = post("/api/v1/reservations", readerToken, key(),
                "{\"titleId\":\"" + emptyTitle + "\",\"reservationDate\":\"" + futureDate + "\","
                        + "\"pickupSlot\":\"AM\",\"channel\":\"ONLINE\",\"confirmWaitlist\":true}");
        var confirmed = json.readTree(confirm.getBody());
        assertThat(confirmed.at("/data/outcome").asText())
                .as("确认响应: %s", confirm.getBody()).isEqualTo("WAITLIST_ENQUEUED");
        assertThat(confirmed.at("/data/txId").isNull()).isFalse();
        // 业务事实已创建（WAITLIST 行）
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM borrow_transaction WHERE title_id = ? AND status = 'WAITLIST'",
                Integer.class, emptyTitle)).isEqualTo(1);
    }

    // 场景 4：现场逐册借阅——一册可借、一册无副本，混合结果逐册保留
    @Test
    void walkInMixedBatchScenarioExecutesLive() throws Exception {
        var readerToken = registerAndLogin("walkin-reader");
        long readerId = jdbc.queryForObject(
                "SELECT id FROM account WHERE username LIKE 'walkin-reader-%'", Long.class);
        var availableTitle = seedTitleWithCopies("可借书", 1);
        var emptyTitle = seedTitleWithCopies("不可借书", 0);
        seedLibrarian();

        var body = """
                {"items":[{"titleId":"%d"},{"titleId":"%d"}],"channel":"SELF_SERVICE"}
                """.formatted(availableTitle, emptyTitle);
        var response = post("/api/v1/walk-in-loans", readerToken, key(), body);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var result = json.readTree(response.getBody());
        assertThat(result.path("code").asText()).isEqualTo("MLS-CIRC-S002");
        var items = result.at("/data/items");
        assertThat(items.size()).isEqualTo(2);
        assertThat(items.get(0).path("outcome").asText()).isEqualTo("LENT");
        assertThat(items.get(0).path("txId").isNull()).isFalse();
        assertThat(items.get(1).path("outcome").asText()).isEqualTo("REJECTED");
        assertThat(items.get(1).path("reasons").get(0).asText()).isEqualTo("COPY_NOT_AVAILABLE");
    }

    // 场景 5：借出后逾期归还 → 罚款形成（流通+罚款+Outbox 同事务）；重复归还 409
    @Test
    void returnWithFineScenarioExecutesLive() throws Exception {
        var readerToken = registerAndLogin("return-reader");
        long readerId = jdbc.queryForObject(
                "SELECT id FROM account WHERE username LIKE 'return-reader-%'", Long.class);
        var librarianId = seedLibrarian();
        var librarianToken = loginLibrarian(librarianId);
        var titleId = seedTitleWithCopies("逾期归还书", 1);
        var copyId = jdbc.queryForObject(
                "SELECT id FROM book_copy WHERE title_id = ? LIMIT 1", Long.class, titleId);

        // 馆员代办借出（onBehalfOf）
        var loan = post("/api/v1/walk-in-loans", librarianToken, key(),
                "{\"readerId\":\"" + readerId + "\",\"items\":[{\"titleId\":\"" + titleId
                        + "\",\"copyId\":\"" + copyId + "\"}],\"channel\":\"DESK\"}");
        assertThat(loan.getStatusCode().value())
                .as("借出响应: %s", loan.getBody()).isEqualTo(200);
        // 种子逾期：due_date 调到 5 天前
        jdbc.update("UPDATE borrow_transaction SET due_date = current_date - 5 "
                + "WHERE copy_id = ? AND status = 'LENT'", copyId);

        var response = post("/api/v1/returns", librarianToken, key(),
                "{\"copyId\":\"%d\",\"condition\":\"INTACT\"}".formatted(copyId));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var result = json.readTree(response.getBody());
        assertThat(result.path("code").asText()).isEqualTo("MLS-CIRC-S003");
        assertThat(result.at("/data/outcome").asText()).isEqualTo("RETURNED");
        assertThat(result.at("/data/fineId").isNull()).isFalse(); // 逾期 5 天 × 100 分
        // 罚款事实（BR-0019：同归还至多一笔）
        assertThat(jdbc.queryForObject(
                "SELECT amount_cents FROM fine WHERE source_tx_id = "
                        + result.at("/data/txId").asLong(), Long.class)).isEqualTo(500L);
        // 重复归还 → 409 MLS-CIRC-006
        var duplicate = post("/api/v1/returns", librarianToken, key(),
                "{\"copyId\":\"%d\",\"condition\":\"INTACT\"}".formatted(copyId));
        assertThat(duplicate.getStatusCode().value()).isEqualTo(409);
        assertThat(json.readTree(duplicate.getBody()).path("code").asText()).isEqualTo("MLS-CIRC-006");
        // Outbox 事件同事务登记
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = 'COPY_RETURNED'",
                Integer.class)).isGreaterThanOrEqualTo(1);
    }

    // IT-QUERY-001：到馆统计四项口径、我的业务组合、权限（READER 访问统计 → 403）
    @Test
    void queryCompositionAndPermissions() throws Exception {
        var readerToken = registerAndLogin("query-reader");
        long readerId = jdbc.queryForObject(
                "SELECT id FROM account WHERE username LIKE 'query-reader-%'", Long.class);
        var librarianId = seedLibrarian();
        var librarianToken = loginLibrarian(librarianId);
        var titleId = seedTitleWithCopies("查询口径书", 2);
        var copyIds = jdbc.queryForList(
                "SELECT id FROM book_copy WHERE title_id = ? ORDER BY id", Long.class, titleId);
        var copyId = copyIds.get(0);
        // 当日 CONFIRMED 预约 → expected/pending
        jdbc.update("""
                INSERT INTO borrow_transaction (reader_id, title_id, copy_id, status, reservation_date)
                VALUES (?, ?, ?, 'CONFIRMED', current_date)
                """, readerId, titleId, copyId);
        jdbc.update("UPDATE book_copy SET status = 'RESERVED' WHERE id = ?", copyId);
        // DEF-M6-002 回归锚点：LENT 行 kind 必须为 LOAN。
        // 在途状态 (reader_id,title_id) 唯一，LENT 行需挂另一书目。
        var lentTitleId = seedTitleWithCopies("查询口径书-借出", 1);
        var lentCopyId = jdbc.queryForObject(
                "SELECT id FROM book_copy WHERE title_id = ? LIMIT 1", Long.class, lentTitleId);
        jdbc.update("""
                INSERT INTO borrow_transaction (reader_id, title_id, copy_id, status, reservation_date)
                VALUES (?, ?, ?, 'LENT', current_date)
                """, readerId, lentTitleId, lentCopyId);
        jdbc.update("UPDATE book_copy SET status = 'LENT' WHERE id = ?", lentCopyId);

        var today = java.time.LocalDate.now().toString();
        var dashboard = get("/api/v1/dashboard/arrivals?businessDate=" + today, librarianToken);
        assertThat(dashboard.getStatusCode().value()).isEqualTo(200);
        var counts = json.readTree(dashboard.getBody()).at("/data");
        assertThat(counts.path("expected").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(counts.path("pending").asInt()).isGreaterThanOrEqualTo(1);

        var forbidden = get("/api/v1/dashboard/arrivals?businessDate=" + today, readerToken);
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);

        var myBusiness = get("/api/v1/me/business", readerToken);
        assertThat(myBusiness.getStatusCode().value()).isEqualTo(200);
        var items = json.readTree(myBusiness.getBody()).at("/data/items");
        assertThat(items.size()).isGreaterThanOrEqualTo(2);
        // 行按 id 倒序：LENT 行后插入，居首
        assertThat(items.get(0).path("kind").asText()).isEqualTo("LOAN");
        assertThat(items.get(0).path("status").asText()).isEqualTo("LENT");
        var kinds = new java.util.ArrayList<String>();
        items.forEach(i -> kinds.add(i.path("kind").asText()));
        assertThat(kinds).contains("RESERVATION");
    }

    // ---- helpers ----------------------------------------------------------------

    private String key() {
        return "b2-" + UUID.randomUUID();
    }

    private ResponseEntity<String> post(String path, String bearer, String idempotencyKey, String body) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        if (bearer != null) {
            headers.setBearerAuth(bearer);
        }
        return rest.postForEntity(path, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> get(String path, String bearer) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(bearer);
        return rest.exchange(path, org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }
}
