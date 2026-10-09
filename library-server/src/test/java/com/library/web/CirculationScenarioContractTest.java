package com.library.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.contract.ContractScenarios;
import com.library.contract.InstanceValidator;
import com.library.contract.OpenApiDocument;
import com.library.identity.internal.PasswordHasher;
import com.library.contract.time.BusinessClock;
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

import java.util.HashSet;
import java.util.Set;
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
    @Autowired BusinessClock businessClock;

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

    @Test
    void copyStatusFilterPreservesLegalValuesAndRejectsUnknownValues() throws Exception {
        var librarianId = seedLibrarian();
        var librarianToken = loginLibrarian(librarianId);
        var titleId = seedTitleWithCopies("Copy filter " + UUID.randomUUID(), 1);

        var available = get("/api/v1/copies?titleId=" + titleId + "&status=AVAILABLE", librarianToken);
        assertThat(available.getStatusCode().value()).isEqualTo(200);
        var availableItems = json.readTree(available.getBody()).at("/data/items");
        assertThat(availableItems).hasSize(1);
        assertThat(availableItems.get(0).path("status").asText()).isEqualTo("AVAILABLE");

        var invalid = get("/api/v1/copies?titleId=" + titleId + "&status=NOT_A_COPY_STATE", librarianToken);
        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
        assertThat(json.readTree(invalid.getBody()).path("code").asText()).isEqualTo("MLS-CIRC-001");
    }

    @Test
    void reservationPickupSlotContractListsOnlyFiveOpeningHourBlocks() {
        var pickupSlotRef = contract.root()
                .at("/components/schemas/ReservationRequest/properties/pickupSlot/$ref").asText();
        assertThat(pickupSlotRef).isEqualTo("#/components/schemas/PickupSlot");
        var pickupSlotSchema = contract.root().at("/components/schemas/PickupSlot/enum");
        var expected = Set.of("09:00-10:30", "10:30-12:00", "13:00-14:30", "14:30-16:00", "16:00-17:00");

        var actual = new HashSet<String>();
        pickupSlotSchema.forEach(value -> actual.add(value.asText()));
        assertThat(actual).hasSize(5).isEqualTo(expected);
    }

    @Test
    void pickupBlocksRejectClosedHoursAndArePersistedAndReturnedUnchanged() throws Exception {
        var token = registerAndLogin("pickup-slot");
        var titleId = seedTitleWithCopies("pickup-block-" + UUID.randomUUID(), 1);
        var reservationDate = businessClock.today().plusDays(1).toString();
        var invalidSlots = java.util.List.of(
                "AM", "PM", "00:00-00:30", "08:00-09:00", "12:00-13:00", "17:00-18:00",
                "09:00-09:30", "14:00-14:30", "14:15-14:45", "23:30-00:00", "24:00-24:30",
                "09:00–10:30", "09:00-10:30 ");

        for (var invalidSlot : invalidSlots) {
            var invalid = post("/api/v1/reservations", token, key(), """
                    {"titleId":"%d","reservationDate":"%s","pickupSlot":"%s","channel":"ONLINE"}
                    """.formatted(titleId, reservationDate, invalidSlot));
            assertThat(invalid.getStatusCode().value()).as(invalidSlot).isEqualTo(400);
            assertThat(json.readTree(invalid.getBody()).path("code").asText())
                    .as(invalidSlot).isEqualTo("MLS-CIRC-001");
        }
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM borrow_transaction WHERE title_id = ?", Integer.class, titleId)).isZero();

        var slot = "13:00-14:30";
        var created = post("/api/v1/reservations", token, key(), """
                {"titleId":"%d","reservationDate":"%s","pickupSlot":"%s","channel":"ONLINE"}
                """.formatted(titleId, reservationDate, slot));
        assertThat(created.getStatusCode().value()).isEqualTo(200);
        var txId = json.readTree(created.getBody()).at("/data/txId").asText();
        assertThat(jdbc.queryForObject(
                "SELECT pickup_slot FROM borrow_transaction WHERE id = ?", String.class, Long.parseLong(txId)))
                .isEqualTo(slot);

        var readBack = get("/api/v1/me/business?group=RESERVATIONS&limit=100", token);
        assertThat(readBack.getStatusCode().value()).isEqualTo(200);
        var items = json.readTree(readBack.getBody()).at("/data/items");
        var projectedSlot = java.util.stream.StreamSupport.stream(items.spliterator(), false)
                .filter(item -> txId.equals(item.path("id").asText()))
                .map(item -> item.path("pickupSlot").asText())
                .findFirst().orElse(null);
        assertThat(projectedSlot).isEqualTo(slot);
    }

    // 场景 3：无可用副本 → WAITLIST_PENDING_CONFIRMATION（txId=null）；确认 → 入队
    @Test
    void reservationWaitlistScenarioExecutesLive() throws Exception {
        var readerToken = registerAndLogin("waitlist-reader");
        var emptyTitle = seedTitleWithCopies("无可借副本预约书", 1);
        var scenario = scenarios.byId("RESERVATION-WAITLIST-CONFIRMATION");
        // 候补路径只对非当日预约成立：当日无副本走 MANUAL_HANDLING（CirculationModuleImpl 分界）
        var futureDate = businessClock.today().plusDays(2).toString();
        var heldCopyId = jdbc.queryForObject(
                "SELECT id FROM book_copy WHERE title_id = ? LIMIT 1", Long.class, emptyTitle);
        var otherReaderId = jdbc.queryForObject("""
                INSERT INTO account (username, password_hash, display_name, role)
                VALUES (?, 'x', '候补占用者', 'READER') RETURNING id
                """, Long.class, "waitlist-holder-" + UUID.randomUUID().toString().substring(0, 8));
        jdbc.update("""
                INSERT INTO borrow_transaction(reader_id, title_id, copy_id, status, reservation_date, due_date)
                VALUES (?, ?, ?, 'LENT', ?, ?)
                """, otherReaderId, emptyTitle, heldCopyId,
                java.sql.Date.valueOf(businessClock.today().minusDays(1)),
                java.sql.Date.valueOf(businessClock.today().plusDays(6)));
        jdbc.update("UPDATE book_copy SET status = 'LENT' WHERE id = ?", heldCopyId);

        var body = """
                {"titleId":"%d","reservationDate":"%s","pickupSlot":"09:00-10:30",
                 "channel":"ONLINE","confirmWaitlist":false}
                """.formatted(emptyTitle, futureDate);
        var response = post("/api/v1/reservations", readerToken, key(), body);
        assertThat(response.getStatusCode().value())
                .isEqualTo(scenario.at("/response/httpStatus").asInt());
        var result = json.readTree(response.getBody());
        assertThat(result.path("code").asText()).isEqualTo("MLS-CIRC-S001");
        assertThat(result.at("/data/outcome").asText()).isEqualTo("WAITLIST_PENDING_CONFIRMATION");
        assertThat(result.at("/data/txId").isNull()).isTrue();
        // 保留已有借阅事务；待确认本身不新增预约业务事实。
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM borrow_transaction WHERE title_id = ?",
                Integer.class, emptyTitle)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM borrow_transaction WHERE title_id = ? AND status = 'WAITLIST'",
                Integer.class, emptyTitle)).isZero();

        var confirm = post("/api/v1/reservations", readerToken, key(),
                "{\"titleId\":\"" + emptyTitle + "\",\"reservationDate\":\"" + futureDate + "\","
                        + "\"pickupSlot\":\"09:00-10:30\",\"channel\":\"ONLINE\",\"confirmWaitlist\":true}");
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
        registerAndLogin("walkin-reader");
        long readerId = jdbc.queryForObject(
                "SELECT id FROM account WHERE username LIKE 'walkin-reader-%'", Long.class);
        var availableTitle = seedTitleWithCopies("可借书", 1);
        var emptyTitle = seedTitleWithCopies("不可借书", 0);
        var librarianId = seedLibrarian();
        var librarianToken = loginLibrarian(librarianId);

        var body = """
                {"readerId":"%d","items":[{"titleId":"%d"},{"titleId":"%d"}],"channel":"DESK"}
                """.formatted(readerId, availableTitle, emptyTitle);
        var response = post("/api/v1/walk-in-loans", librarianToken, key(), body);
        assertThat(response.getStatusCode().value()).as("借出响应: %s", response.getBody()).isEqualTo(200);
        var result = json.readTree(response.getBody());
        assertThat(result.path("code").asText()).isEqualTo("MLS-CIRC-S002");
        var items = result.at("/data/items");
        assertThat(items.size()).isEqualTo(2);
        assertThat(items.get(0).path("outcome").asText()).isEqualTo("LENT");
        assertThat(items.get(0).path("txId").isNull()).isFalse();
        assertThat(items.get(1).path("outcome").asText()).isEqualTo("REJECTED");
        assertThat(items.get(1).path("reasons").get(0).asText()).isEqualTo("COPY_NOT_AVAILABLE");
    }

    @Test
    void readerCannotUseWalkInEndpointAndRejectedAttemptsWriteNoCirculationFacts() throws Exception {
        var readerToken = registerAndLogin("walkin-blocked-reader");
        var titleId = seedTitleWithCopies("读者自助借阅关闭" + UUID.randomUUID(), 1);
        var copyId = jdbc.queryForObject(
                "SELECT id FROM book_copy WHERE title_id = ? LIMIT 1", Long.class, titleId);
        var readerId = jdbc.queryForObject(
                "SELECT id FROM account WHERE username LIKE 'walkin-blocked-reader-%'", Long.class);

        for (var channel : java.util.List.of("SELF_SERVICE", "DESK")) {
            var response = post("/api/v1/walk-in-loans", readerToken, key(), """
                    {"readerId":"%d","items":[{"titleId":"%d","copyId":"%d"}],"channel":"%s"}
                    """.formatted(readerId, titleId, copyId, channel));
            assertThat(response.getStatusCode().value()).isEqualTo(403);
            assertThat(json.readTree(response.getBody()).path("code").asText()).isEqualTo("MLS-ID-005");
        }

        assertThat(jdbc.queryForObject(
                "SELECT status FROM book_copy WHERE id = ?", String.class, copyId)).isEqualTo("SHELF_READY");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM borrow_transaction WHERE title_id = ?", Integer.class, titleId)).isZero();
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
        // 种子逾期：due_date 按业务时钟调到 5 天前。
        jdbc.update("UPDATE borrow_transaction SET due_date = ? WHERE copy_id = ? AND status = 'LENT'",
                java.sql.Date.valueOf(businessClock.today().minusDays(5)), copyId);

        var response = post("/api/v1/returns", librarianToken, key(),
                "{\"copyId\":\"%d\",\"condition\":\"INTACT\"}".formatted(copyId));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var result = json.readTree(response.getBody());
        assertThat(result.path("code").asText()).isEqualTo("MLS-CIRC-S003");
        assertThat(result.at("/data/outcome").asText()).isEqualTo("RETURNED");
        assertThat(result.at("/data/fineId").isNull()).isFalse();
        // 罚款事实（BR-0019：同归还至多一笔）
        assertThat(jdbc.queryForObject(
                "SELECT amount_cents FROM fine WHERE source_tx_id = "
                        + result.at("/data/txId").asLong(), Long.class)).isEqualTo(4950L);
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

    @Test
    void calendarProjectsPrimaryStateFromCurrentFactsWithoutReaderOrCopyDetails() throws Exception {
        var titleId = seedTitleWithCopies("calendar-facts-" + UUID.randomUUID(), 3);
        var copies = jdbc.queryForList("SELECT id FROM book_copy WHERE title_id = ? ORDER BY id", Long.class, titleId);
        var tomorrow = businessClock.today().plusDays(1);
        var reservedReader = seedCalendarReader();
        var lentReader = seedCalendarReader();
        jdbc.update("UPDATE book_copy SET status = 'RESERVED' WHERE id = ?", copies.get(1));
        jdbc.update("UPDATE book_copy SET status = 'LENT' WHERE id = ?", copies.get(2));
        var reservedTx = jdbc.queryForObject("""
                INSERT INTO borrow_transaction (reader_id, title_id, copy_id, status, reservation_date)
                VALUES (?, ?, ?, 'CONFIRMED', ?) RETURNING id
                """, Long.class, reservedReader, titleId, copies.get(1), java.sql.Date.valueOf(tomorrow));
        var lentTx = jdbc.queryForObject("""
                INSERT INTO borrow_transaction (reader_id, title_id, copy_id, status, reservation_date, due_date)
                VALUES (?, ?, ?, 'LENT', ?, ?) RETURNING id
                """, Long.class, lentReader, titleId, copies.get(2),
                java.sql.Date.valueOf(tomorrow.minusDays(3)), java.sql.Date.valueOf(tomorrow.minusDays(2)));

        var available = calendarDays(titleId, tomorrow, tomorrow.plusDays(1));
        assertThat(available.get(0).path("displayState").asText()).isEqualTo("AVAILABLE");
        assertThat(available.get(1).path("displayState").asText()).isEqualTo("AVAILABLE");
        assertThat(available.get(0).path("availability").asText()).isEqualTo("NOT_GUARANTEED");
        assertThat(available.get(1).path("availability").asText()).isEqualTo("GUARANTEED");
        jdbc.update("UPDATE book_copy SET status = 'PENDING_SHELVING' WHERE id = ?", copies.get(0));
        var occupied = calendarDays(titleId, tomorrow, tomorrow.plusDays(1));
        assertThat(occupied.get(0).path("displayState").asText()).isEqualTo("RESERVED");
        assertThat(occupied.get(1).path("displayState").asText()).isEqualTo("LENT");
        // Passing the due date does not release a currently lent copy.
        assertThat(calendarDays(titleId, tomorrow.plusDays(10), tomorrow.plusDays(10))
                .get(0).path("displayState").asText()).isEqualTo("LENT");
        jdbc.update("UPDATE borrow_transaction SET status = 'NOT_BORROWED' WHERE id = ?", reservedTx);
        jdbc.update("UPDATE book_copy SET status = 'PENDING_SHELVING' WHERE id = ?", copies.get(1));
        assertThat(calendarDays(titleId, tomorrow, tomorrow).get(0).path("displayState").asText())
                .isEqualTo("LENT");
        jdbc.update("UPDATE borrow_transaction SET status = 'RETURNED' WHERE id = ?", lentTx);
        jdbc.update("UPDATE book_copy SET status = 'PENDING_SHELVING' WHERE id = ?", copies.get(2));
        assertThat(calendarDays(titleId, tomorrow, tomorrow).get(0).path("displayState").isNull()).isTrue();
        jdbc.update("UPDATE book_copy SET status = 'SHELF_READY' WHERE id = ?", copies.get(2));
        assertThat(calendarDays(titleId, tomorrow, tomorrow).get(0).path("displayState").asText())
                .isEqualTo("AVAILABLE");
    }

    @Test
    void calendarDoesNotPresentCurrentFactsAsHistoricalAvailability() throws Exception {
        var titleId = seedTitleWithCopies("calendar-past-" + UUID.randomUUID(), 1);
        var today = businessClock.today();
        var days = calendarDays(titleId, today.minusDays(1), today);
        assertThat(days.get(0).path("displayState").isNull()).isTrue();
        assertThat(days.get(1).path("displayState").asText()).isEqualTo("AVAILABLE");
        var emptyTitle = seedTitleWithCopies("calendar-empty-" + UUID.randomUUID(), 0);
        var empty = calendarDays(emptyTitle, today.plusDays(1), today.plusDays(1)).get(0);
        assertThat(empty.path("displayState").isNull()).isTrue();
        assertThat(empty.path("availability").asText()).isEqualTo("UNAVAILABLE");
    }

    private long seedCalendarReader() {
        return jdbc.queryForObject("""
                INSERT INTO account (username, password_hash, display_name, role)
                VALUES (?, 'x', 'Calendar test', 'READER') RETURNING id
                """, Long.class, "calendar-" + UUID.randomUUID());
    }

    private JsonNode calendarDays(long titleId, java.time.LocalDate from, java.time.LocalDate to) throws Exception {
        var response = rest.getForEntity("/api/v1/titles/" + titleId
                + "/reservation-calendar?from=" + from + "&to=" + to, String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var body = json.readTree(response.getBody());
        assertThat(InstanceValidator.validate(contract.responseSchema(
                "get", "/titles/{titleId}/reservation-calendar", 200), body)).isEmpty();
        var days = body.at("/data/items");
        assertThat(days).hasSize((int) java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1);
        days.forEach(day -> {
            var fields = new HashSet<String>();
            day.fieldNames().forEachRemaining(fields::add);
            assertThat(fields).containsExactlyInAnyOrder("businessDate", "availability", "displayState");
        });
        return days;
    }

    // ---- helpers ----------------------------------------------------------------

    private String key() {
        return "b2-" + UUID.randomUUID();
    }

    private static String time(int minutes) {
        return "%02d:%02d".formatted(minutes / 60, minutes % 60);
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
