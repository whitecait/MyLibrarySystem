package com.library.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Real PostgreSQL and HTTP coverage for the self-only, three-group business projection. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "library.outbox.dispatch-enabled=false")
class ITMyBusinessProjectionTest {

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
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void groupsAreExclusivePaginatedSelfScopedAndLegacyKindsRemainAvailable() throws Exception {
        var suffix = Long.toString(System.nanoTime());
        var ownerName = "my-business-owner-" + suffix;
        var otherName = "my-business-other-" + suffix;
        var librarianName = "my-business-librarian-" + suffix;
        var ownerToken = registerAndLogin(ownerName);
        var otherToken = registerAndLogin(otherName);
        var librarianToken = registerAndLogin(librarianName);
        jdbc.update("UPDATE account SET role='LIBRARIAN' WHERE username=?", librarianName);
        var librarianLogin = post("/api/v1/auth/sessions", null, "my-business-login-librarian-" + librarianName,
                "{\"username\":\"%s\",\"password\":\"password123\"}".formatted(librarianName));
        assertThat(librarianLogin.getStatusCode().value()).isEqualTo(200);
        librarianToken = json.readTree(librarianLogin.getBody()).at("/data/accessToken").asText();
        var ownerId = accountId(ownerName);
        var otherId = accountId(otherName);
        var reservationDate = businessClock.today().plusDays(1);

        var directTitle = seedTitle("my-business-direct-" + suffix);
        var directCopy = seedCopy(directTitle, "DIRECT-" + suffix, "SHELF_READY");
        var direct = reserve(ownerToken, directTitle, reservationDate, "PM", false, suffix + "-direct");
        assertThat(direct.path("outcome").asText()).isEqualTo("CONFIRMED");
        var directTxId = Long.parseLong(direct.path("txId").asText());
        assertThat(jdbc.queryForObject("SELECT guarantee_type FROM borrow_transaction WHERE id=?",
                String.class, directTxId)).isEqualTo("DIRECT_CONFIRMATION");

        var waitingTitle = seedTitle("my-business-waiting-" + suffix);
        var waitingCopy = seedCopy(waitingTitle, "WAIT-" + suffix, "PENDING_SHELVING");
        var waiting = reserve(ownerToken, waitingTitle, reservationDate.plusDays(1), "AM", true,
                suffix + "-waiting");
        assertThat(waiting.path("outcome").asText()).isEqualTo("WAITLIST_ENQUEUED");
        var waitingTxId = Long.parseLong(waiting.path("txId").asText());
        assertThat(jdbc.queryForObject("SELECT guarantee_type FROM borrow_transaction WHERE id=?",
                String.class, waitingTxId)).isEqualTo("WAITLIST");
        var shelved = patch("/api/v1/copies/" + waitingCopy, librarianToken, suffix + "-shelve",
                "{\"action\":\"SHELVE\",\"version\":0}");
        assertThat(shelved.getStatusCode().value()).isEqualTo(200);
        assertThat(json.readTree(shelved.getBody()).at("/data/status").asText()).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForMap("SELECT status, copy_id, guarantee_type FROM borrow_transaction WHERE id=?",
                waitingTxId)).containsEntry("status", "CONFIRMED")
                .containsEntry("guarantee_type", "WAITLIST");
        assertThat(((Number) jdbc.queryForMap("SELECT copy_id FROM borrow_transaction WHERE id=?",
                waitingTxId).get("copy_id")).longValue()).isEqualTo(waitingCopy);

        // Fixed high IDs make pagination and the same-number cross-kind cursor deterministic.
        var lentOne = seedTransaction(99001, ownerId, "LENT", reservationDate, true);
        var lentTwo = seedTransaction(99002, ownerId, "LENT", reservationDate, true);
        var awaiting = seedTransaction(99003, ownerId, "AWAIT", reservationDate, false);
        var manual = seedTransaction(99004, ownerId, "MANUAL_HANDLING", reservationDate, false);
        var returned = seedTransaction(99005, ownerId, "RETURNED", reservationDate, true);
        var notBorrowed = seedTransaction(99006, ownerId, "NOT_BORROWED", reservationDate, false);
        var abnormal = seedTransaction(99007, ownerId, "RETURN_ABNORMAL", reservationDate, true);
        var foreignLoan = seedTransaction(99008, otherId, "LENT", reservationDate, true);
        var unassignedWaitlist = seedTransaction(99000, ownerId, "WAITLIST", reservationDate, false);

        var fineId = seedFine(99007, ownerId, returned, 720L);
        jdbc.update("INSERT INTO payment_order (fine_id, status, expires_at) VALUES (?, 'OCCUPIED', now() + interval '1 day')",
                fineId);

        jdbc.update("INSERT INTO notification (reader_id, kind, biz_ref, reservation_date) VALUES (?, 'VISIT', ?, ?)",
                ownerId, directTxId, Date.valueOf(reservationDate));
        jdbc.update("INSERT INTO notification (reader_id, kind, biz_ref, reservation_date) VALUES (?, 'NO_VISIT', ?, ?)",
                ownerId, notBorrowed, Date.valueOf(reservationDate));
        // This transaction shares its numeric ID with a fine; only the transaction row owns the notification.
        jdbc.update("INSERT INTO notification (reader_id, kind, biz_ref, reservation_date) VALUES (?, 'VISIT', ?, ?)",
                ownerId, abnormal, Date.valueOf(reservationDate));
        // A notification owned by another reader cannot enter the owner's transaction projection.
        jdbc.update("INSERT INTO notification (reader_id, kind, biz_ref, reservation_date) VALUES (?, 'VISIT', ?, ?)",
                otherId, directTxId, Date.valueOf(reservationDate));

        var currentPageOne = get("/api/v1/me/business?group=CURRENT_LOANS&limit=1", ownerToken);
        assertThat(currentPageOne.getStatusCode().value()).isEqualTo(200);
        var currentFirst = items(currentPageOne);
        assertThat(currentFirst).hasSize(1);
        assertThat(currentFirst.get(0).path("status").asText()).isEqualTo("LENT");
        var currentCursor = json.readTree(currentPageOne.getBody()).at("/data/nextCursor").asText();
        assertThat(currentCursor).isNotBlank();
        var currentPageTwo = items(get("/api/v1/me/business?group=CURRENT_LOANS&limit=1&cursor="
                + currentCursor, ownerToken));
        assertThat(currentPageTwo).hasSize(1);
        assertThat(currentPageTwo.get(0).path("status").asText()).isEqualTo("LENT");
        assertThat(Set.of(currentFirst.get(0).path("id").asText(), currentPageTwo.get(0).path("id").asText()))
                .containsExactlyInAnyOrder(Long.toString(lentOne), Long.toString(lentTwo));

        var reservationPageOneResponse = get("/api/v1/me/business?group=RESERVATIONS&limit=1", ownerToken);
        var reservationPageOne = items(reservationPageOneResponse);
        assertThat(reservationPageOne).hasSize(1);
        var reservationCursor = json.readTree(reservationPageOneResponse.getBody()).at("/data/nextCursor").asText();
        assertThat(reservationCursor).isNotBlank();
        var reservationPageTwo = items(get("/api/v1/me/business?group=RESERVATIONS&limit=1&cursor="
                + reservationCursor, ownerToken));
        assertThat(reservationPageTwo).hasSize(1);
        assertThat(reservationPageOne.get(0).path("id").asText())
                .isNotEqualTo(reservationPageTwo.get(0).path("id").asText());
        var reservations = items(get("/api/v1/me/business?group=RESERVATIONS&limit=100", ownerToken));
        reservations.forEach(item -> assertThat(item.has("guaranteeType")).isTrue());
        assertThat(statuses(reservations)).as("reservation rows: %s", reservations).containsExactlyInAnyOrder(
                "CONFIRMED", "WAITLIST", "AWAIT", "MANUAL_HANDLING");
        var reservationIds = ids(reservations);
        assertThat(reservationIds).contains(reservationPageOne.get(0).path("id").asText(),
                reservationPageTwo.get(0).path("id").asText());
        assertThat(reservationIds).contains(Long.toString(directTxId), Long.toString(waitingTxId),
                Long.toString(awaiting), Long.toString(manual), Long.toString(unassignedWaitlist));
        var reservationStateById = reservations.stream().collect(java.util.stream.Collectors.toMap(
                item -> item.path("id").asText(),
                item -> Map.of("status", item.path("status").asText(),
                        "guaranteeType", item.path("guaranteeType").asText("null"))));
        assertThat(reservationStateById)
                .containsEntry(Long.toString(directTxId),
                        Map.of("status", "CONFIRMED", "guaranteeType", "DIRECT_CONFIRMATION"))
                .containsEntry(Long.toString(waitingTxId),
                        Map.of("status", "CONFIRMED", "guaranteeType", "WAITLIST"));
        var directItem = byId(reservations, directTxId);
        assertThat(directItem.path("guaranteeType").asText()).isEqualTo("DIRECT_CONFIRMATION");
        assertThat(directItem.path("reservationDate").asText()).isEqualTo(reservationDate.toString());
        assertThat(directItem.path("reservationDate").isTextual()).isTrue();
        assertThat(directItem.path("pickupSlot").asText()).isEqualTo("PM");
        assertThat(directItem.path("copyId").asText()).isEqualTo(Long.toString(directCopy));
        assertThat(directItem.path("copyId").isTextual()).isTrue();
        assertThat(directItem.path("notifications")).hasSize(1);
        var visit = directItem.path("notifications").get(0);
        assertThat(visit.path("type").asText()).isEqualTo("VISIT");
        assertThat(visit.path("read").asBoolean()).isFalse();
        assertThat(visit.path("reservationDate").asText()).isEqualTo(reservationDate.toString());
        assertThat(visit.path("notificationId").isTextual()).isTrue();
        assertThat(visit.path("notificationId").asText()).isNotBlank();
        assertThat(visit.path("createdAt").asText()).isNotBlank();
        var waitingItem = byId(reservations, waitingTxId);
        assertThat(waitingItem.path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(waitingItem.path("guaranteeType").asText()).isEqualTo("WAITLIST");
        assertThat(waitingItem.path("copyId").asText()).isEqualTo(Long.toString(waitingCopy));
        assertThat(waitingItem.path("pickupSlot").asText()).isEqualTo("AM");
        assertThat(waitingItem.path("notifications")).isEmpty();
        var unknownOldItem = byId(reservations, awaiting);
        assertThat(unknownOldItem.has("guaranteeType")).isTrue();
        assertThat(unknownOldItem.path("guaranteeType").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT guarantee_type FROM borrow_transaction WHERE id=?",
                String.class, awaiting)).isNull();
        var unknownWaitlistItem = byId(reservations, unassignedWaitlist);
        assertThat(unknownWaitlistItem.has("guaranteeType")).isTrue();
        assertThat(unknownWaitlistItem.path("guaranteeType").isNull()).isTrue();
        assertThat(unknownWaitlistItem.has("copyId")).isTrue();
        assertThat(unknownWaitlistItem.path("copyId").isNull()).isTrue();
        assertThat(unknownWaitlistItem.path("reservationDate").asText()).isEqualTo(reservationDate.toString());
        var filteredWaiting = items(get("/api/v1/me/business?group=RESERVATIONS&status=WAITLIST", ownerToken));
        assertThat(ids(filteredWaiting)).containsExactly(Long.toString(unassignedWaitlist));

        var historyPageOneResponse = get("/api/v1/me/business?group=HISTORY&limit=1", ownerToken);
        var historyPageOne = items(historyPageOneResponse);
        assertThat(historyPageOne).hasSize(1);
        var historyCursor = json.readTree(historyPageOneResponse.getBody()).at("/data/nextCursor").asText();
        assertThat(historyCursor).isNotBlank();
        var historyPageTwo = items(get("/api/v1/me/business?group=HISTORY&limit=1&cursor="
                + historyCursor, ownerToken));
        assertThat(historyPageTwo).hasSize(1);
        var history = items(get("/api/v1/me/business?group=HISTORY&limit=100", ownerToken));
        history.forEach(item -> assertThat(item.has("guaranteeType")).isTrue());
        assertThat(statuses(history)).containsExactlyInAnyOrder("RETURNED", "NOT_BORROWED", "RETURN_ABNORMAL");
        var notBorrowedItem = byId(history, notBorrowed);
        assertThat(notBorrowedItem.path("notifications")).hasSize(1);
        assertThat(notBorrowedItem.path("notifications").get(0).path("type").asText()).isEqualTo("NO_VISIT");
        assertThat(notBorrowedItem.path("notifications").get(0).path("read").asBoolean()).isFalse();
        var abnormalItem = byId(history, abnormal);
        assertThat(abnormalItem.path("notifications")).hasSize(1);
        assertThat(abnormalItem.path("notifications").get(0).path("type").asText()).isEqualTo("VISIT");

        assertThat(reservationIds).doesNotContain(Long.toString(lentOne), Long.toString(returned),
                Long.toString(notBorrowed), Long.toString(foreignLoan));
        assertThat(ids(currentFirst)).doesNotContain(Long.toString(directTxId), Long.toString(foreignLoan));
        assertThat(ids(history)).doesNotContain(Long.toString(directTxId), Long.toString(foreignLoan));
        assertThat(Set.of(currentFirst.get(0).path("id").asText(), currentPageTwo.get(0).path("id").asText()))
                .doesNotContain(Long.toString(foreignLoan));

        var conflictingFilters = get("/api/v1/me/business?group=HISTORY&kind=LOAN", ownerToken);
        assertThat(conflictingFilters.getStatusCode().value()).isEqualTo(400);
        assertThat(json.readTree(conflictingFilters.getBody()).path("code").asText()).isEqualTo("MLS-QUERY-001");

        var fine = items(get("/api/v1/me/business?kind=FINE", ownerToken));
        assertThat(fine).singleElement().satisfies(item -> {
            assertThat(item.path("id").asText()).isEqualTo("99007");
            assertThat(item.path("amountCents").asLong()).isEqualTo(720L);
            assertThat(item.has("guaranteeType")).isTrue();
            assertThat(item.path("guaranteeType").isNull()).isTrue();
            assertThat(item.has("reservationDate")).isTrue();
            assertThat(item.path("reservationDate").isNull()).isTrue();
            assertThat(item.has("copyId")).isTrue();
            assertThat(item.path("copyId").isNull()).isTrue();
            assertThat(item.path("notifications")).isEmpty();
        });
        var mixedFirstResponse = get("/api/v1/me/business?limit=1", ownerToken);
        var mixedFirst = items(mixedFirstResponse);
        assertThat(mixedFirst).hasSize(1);
        assertThat(mixedFirst.get(0).path("kind").asText()).isEqualTo("FINE");
        assertThat(mixedFirst.get(0).path("id").asText()).isEqualTo("99007");
        var mixedCursor = json.readTree(mixedFirstResponse.getBody()).at("/data/nextCursor").asText();
        var mixedSecond = items(get("/api/v1/me/business?limit=1&cursor=" + mixedCursor, ownerToken));
        assertThat(mixedSecond).hasSize(1);
        assertThat(mixedSecond.get(0).path("kind").asText()).isEqualTo("LOAN");
        assertThat(mixedSecond.get(0).path("id").asText()).isEqualTo("99007");
        assertThat(items(get("/api/v1/me/business?kind=ORDER", ownerToken))).isEmpty();
    }

    private JsonNode reserve(String token, long titleId, LocalDate date, String pickupSlot,
                             boolean confirmWaitlist, String key) throws Exception {
        var response = post("/api/v1/reservations", token, "my-business-reserve-" + key,
                "{\"titleId\":\"%s\",\"reservationDate\":\"%s\",\"pickupSlot\":\"%s\",\"channel\":\"ONLINE\",\"confirmWaitlist\":%s}"
                        .formatted(titleId, date, pickupSlot, confirmWaitlist));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return json.readTree(response.getBody()).path("data");
    }

    private String registerAndLogin(String username) throws Exception {
        var registered = post("/api/v1/auth/registrations", null, "my-business-register-" + username,
                "{\"username\":\"%s\",\"password\":\"password123\",\"displayName\":\"%s\"}"
                        .formatted(username, username));
        assertThat(registered.getStatusCode().value()).isEqualTo(200);
        var session = post("/api/v1/auth/sessions", null, "my-business-login-" + username,
                "{\"username\":\"%s\",\"password\":\"password123\"}".formatted(username));
        assertThat(session.getStatusCode().value()).isEqualTo(200);
        return json.readTree(session.getBody()).at("/data/accessToken").asText();
    }

    private long accountId(String username) {
        return jdbc.queryForObject("SELECT id FROM account WHERE username=?", Long.class, username);
    }

    private long seedTitle(String prefix) {
        var isbn = "MB" + Integer.toUnsignedString(prefix.hashCode(), 36)
                + Long.toString(System.nanoTime(), 36);
        return jdbc.queryForObject("INSERT INTO book_title (isbn, title, price_cents) VALUES (?, ?, 9900) RETURNING id",
                Long.class, isbn, prefix);
    }

    private long seedCopy(long titleId, String copyNo, String status) {
        return jdbc.queryForObject("INSERT INTO book_copy (title_id, copy_no, status) VALUES (?, ?, ?) RETURNING id",
                Long.class, titleId, copyNo, status);
    }

    private long seedTransaction(long id, long readerId, String status, LocalDate date, boolean bindCopy) {
        var titleId = seedTitle("my-business-tx-" + id);
        Long copyId = bindCopy ? seedCopy(titleId, "TX-" + id, status.equals("LENT") ? "LENT" : "SHELF_READY") : null;
        return jdbc.queryForObject("""
                INSERT INTO borrow_transaction (id, reader_id, title_id, copy_id, status,
                                                reservation_date, pickup_slot, due_date, loan_started_date)
                OVERRIDING SYSTEM VALUE
                VALUES (?, ?, ?, ?, ?, ?, 'PM', ?, ?)
                RETURNING id
                """, Long.class, id, readerId, titleId, copyId, status, Date.valueOf(date),
                Date.valueOf(date.plusDays(7)), Date.valueOf(date.minusDays(1)));
    }

    private long seedFine(long id, long readerId, long sourceTxId, long amountCents) {
        return jdbc.queryForObject("""
                INSERT INTO fine (id, reader_id, source_tx_id, amount_cents, status)
                OVERRIDING SYSTEM VALUE
                VALUES (?, ?, ?, ?, 'DUE')
                RETURNING id
                """, Long.class, id, readerId, sourceTxId, amountCents);
    }

    private ResponseEntity<String> get(String path, String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(path, org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> post(String path, String token, String key, String body) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);
        if (token != null) headers.setBearerAuth(token);
        return rest.exchange(path, org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> patch(String path, String token, String key, String body) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);
        headers.setBearerAuth(token);
        return rest.exchange(path, org.springframework.http.HttpMethod.PATCH,
                new HttpEntity<>(body, headers), String.class);
    }

    private java.util.List<JsonNode> items(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var items = json.readTree(response.getBody()).at("/data/items");
        assertThat(items.isArray()).isTrue();
        return java.util.stream.StreamSupport.stream(items.spliterator(), false).toList();
    }

    private Set<String> ids(java.util.List<JsonNode> items) {
        var result = new HashSet<String>();
        items.forEach(item -> result.add(item.path("id").asText()));
        return result;
    }

    private Set<String> statuses(java.util.List<JsonNode> items) {
        var result = new HashSet<String>();
        items.forEach(item -> result.add(item.path("status").asText()));
        return result;
    }

    private JsonNode byId(java.util.List<JsonNode> items, long id) {
        return items.stream().filter(item -> item.path("id").asText().equals(Long.toString(id)))
                .findFirst().orElseThrow();
    }
}
