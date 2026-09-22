package com.library.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.application.loan.LoanUseCases;
import com.library.application.payment.PaymentSimulatorPort;
import com.library.application.returning.ReturnUseCases;
import com.library.catalog.contract.model.TitleId;
import com.library.circulation.contract.LoanCommands;
import com.library.circulation.contract.ReservationCommands;
import com.library.circulation.contract.ReturnCommands;
import com.library.circulation.contract.model.BusinessDate;
import com.library.circulation.contract.model.CopyId;
import com.library.circulation.contract.model.TxId;
import com.library.contract.ModuleResult;
import com.library.contract.model.AccountId;
import com.library.contract.model.Role;
import com.library.contract.security.ActorContext;
import com.library.identity.internal.PasswordHasher;
import com.library.review.contract.ReviewCommand;
import com.library.review.contract.ReviewDeciding;
import com.library.review.contract.ReviewRequesting;
import com.library.review.contract.ReviewedAction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DEF-M6-011 成功路径审计：借阅、归还、续借、预约、复核发起与决定、模拟支付调度在各成功用例
 * 落 audit_event，并经 `GET /audit-events` 按关联对象与操作者反查责任链（AT-0002 §2.3 R5、§2.14）。
 * 断言口径：每个事件一条审计行，detail 携带 relatedId 与 outcome，actor_id 为真实操作者。
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ITDefM6011AuditTrailTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            "postgres@sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired LoanUseCases loans;
    @Autowired ReturnUseCases returns;
    @Autowired ReviewRequesting reviews;
    @Autowired ReviewDeciding decisions;
    @Autowired PaymentSimulatorPort simulator;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate rest;

    private final ObjectMapper json = new ObjectMapper();

    // 预约 → 取书 → 续借 → 归还（计罚）全链：每步一条成功审计，actor 为当步操作者
    @Test
    void defM6011_reservationClaimRenewalAndReturnAreAudited() {
        var reader = seedAccount("def011-reader-" + System.nanoTime(), "READER");
        var librarian = seedAccount("def011-lib-" + System.nanoTime(), "LIBRARIAN");
        var title = seedTitle("m6011a-");
        var copyId = seedCopy(title, "DEF011-A1");
        var readerActor = actor(reader, Role.READER);
        var librarianActor = actor(librarian, Role.LIBRARIAN);

        var reserved = accepted(loans.reserve(readerActor, new ReservationCommands.ReservationCommand(
                reader, TitleId.of(title), BusinessDate.of(LocalDate.now().plusDays(1)), "AM",
                "ONLINE", true, readerActor)));
        var txId = reserved.txId().value();
        assertAudit("RESERVATION_DECIDED", txId, reader, "CONFIRMED");

        accepted(loans.claim(librarianActor, new LoanCommands.ClaimPickup(
                reserved.txId(), CopyId.of(copyId), librarianActor)));
        assertAudit("PICKUP_CLAIMED", txId, librarian, "CLAIMED");

        accepted(loans.renew(readerActor, new LoanCommands.RenewalCommand(reserved.txId(), null,
                readerActor)));
        assertAudit("LOAN_RENEWED", txId, reader, "RENEWED");

        var returned = accepted(returns.returnCopy(librarianActor, new ReturnCommands.ReturnCommand(
                CopyId.of(copyId), "LOST", librarianActor)));
        assertAudit("COPY_RETURNED", txId, librarian, "RETURN_ABNORMAL");
        assertThat(returned.fineId()).isNotNull();
        assertAudit("FINE_CREATED", returned.fineId(), librarian, "CREATED");

        // 责任链反查：按关联对象取回该事务的全部成功审计，按操作者区分读者与馆员
        var byTx = getAuditEvents("/api/v1/audit-events?relatedId=" + txId,
                login(seedAndLoginUsername(librarian)));
        assertThat(byTx).extracting(node -> node.get("eventType").asText())
                .contains("RESERVATION_DECIDED", "PICKUP_CLAIMED", "LOAN_RENEWED", "COPY_RETURNED");
        assertThat(byTx).filteredOn(node -> node.get("eventType").asText().equals("PICKUP_CLAIMED"))
                .singleElement()
                .satisfies(node -> {
                    assertThat(node.get("actorId").asText()).isEqualTo(Long.toString(librarian));
                    assertThat(node.get("outcome").asText()).isEqualTo("CLAIMED");
                });
    }

    // 现场借阅批次：每个成功册一条审计（relatedId 为册事务 id）
    @Test
    void defM6011_walkInLoanIsAuditedPerLentCopy() {
        var reader = seedAccount("def011-lend-" + System.nanoTime(), "READER");
        var librarian = seedAccount("def011-lendlib-" + System.nanoTime(), "LIBRARIAN");
        var title = seedTitle("m6011b-");
        var otherTitle = seedTitle("m6011c-");
        var firstCopy = seedCopy(title, "DEF011-B1");
        var secondCopy = seedCopy(otherTitle, "DEF011-B2");
        var librarianActor = actor(librarian, Role.LIBRARIAN);

        var batch = accepted(loans.lend(librarianActor, new LoanCommands.WalkInLoanCommand(
                reader,
                List.of(new LoanCommands.WalkInItem(TitleId.of(title), CopyId.of(firstCopy)),
                        new LoanCommands.WalkInItem(TitleId.of(otherTitle), CopyId.of(secondCopy))),
                "DESK", librarianActor)));
        var lentTxIds = batch.items().stream().filter(item -> "LENT".equals(item.outcome()))
                .map(item -> item.txId().value()).toList();
        assertThat(lentTxIds).hasSize(2);
        for (var txId : lentTxIds) {
            assertAudit("WALKIN_LOAN_BATCH", txId, librarian, "LENT");
        }
    }

    // 复核：发起与决定分别为不同操作者，可按 actorId 与 eventType 反查
    @Test
    void defM6011_reviewRequestAndDecisionAreTraceableByActor() {
        var librarian = seedAccount("def011-revlib-" + System.nanoTime(), "LIBRARIAN");
        var admin = seedAccount("def011-admin-" + System.nanoTime(), "ADMIN");

        var review = accepted(reviews.request(new ReviewCommand(librarian, "REFUND", 1L, "REFUND",
                Map.of("amountCents", 100), "DEF-M6-011 审计", 1L)));
        assertAudit("REVIEW_REQUESTED", review.reviewId(), librarian, "PENDING");

        accepted(decisions.decide(new ReviewDeciding.DecideReview(review.reviewId(), "REJECTED",
                        "越权拒绝", admin),
                new ReviewedAction() {
                    @Override
                    public long currentVersion() {
                        return 1L;
                    }

                    @Override
                    public ModuleResult<String> execute(Map<String, Object> payload) {
                        return ModuleResult.Accepted.of("NOOP");
                    }
                }));
        assertAudit("REVIEW_DECIDED", review.reviewId(), admin, "REJECTED");

        var byReview = getAuditEvents("/api/v1/audit-events?relatedId=" + review.reviewId()
                + "&eventType=REVIEW_DECIDED", login(seedAndLoginUsername(admin)));
        assertThat(byReview).singleElement().satisfies(node -> {
            assertThat(node.get("actorId").asText()).isEqualTo(Long.toString(admin));
            assertThat(node.get("outcome").asText()).isEqualTo("REJECTED");
        });
    }

    // 模拟支付调度：操作者为发起调度的账户，关联对象为订单 id
    @Test
    void defM6011_simulatedPaymentScheduleIsAudited() {
        var admin = seedAccount("def011-payadmin-" + System.nanoTime(), "ADMIN");
        // 仅需一个正整数订单标识：审计的关联对象即该 id（无订单外键约束）
        var orderId = 9_000_000L + (System.nanoTime() % 1_000_000L);

        var scheduled = simulator.schedule(new PaymentSimulatorPort.Command(orderId, "FAILURE", 0, 0,
                admin));
        assertThat(scheduled).isInstanceOf(ModuleResult.Accepted.class);
        assertAudit("SIMULATION_SCHEDULED", orderId, admin, "SCHEDULED");
    }

    // 预约取消与人工处置（人工例外）：两类同样各落一条成功审计
    @Test
    void defM6011_reservationCancelAndManualActionAreAudited() {
        var reader = seedAccount("def011-cancel-" + System.nanoTime(), "READER");
        var librarian = seedAccount("def011-manuallib-" + System.nanoTime(), "LIBRARIAN");
        var title = seedTitle("m6011d-");
        var manualTitle = seedTitle("m6011e-");
        seedCopy(title, "DEF011-D1");
        var readerActor = actor(reader, Role.READER);
        var librarianActor = actor(librarian, Role.LIBRARIAN);

        var reserved = accepted(loans.reserve(readerActor, new ReservationCommands.ReservationCommand(
                reader, TitleId.of(title), BusinessDate.of(LocalDate.now().plusDays(1)), "AM",
                "ONLINE", true, readerActor)));
        accepted(loans.cancel(readerActor, new ReservationCommands.CancelReservation(
                reserved.txId(), "改期", readerActor)));
        assertAudit("RESERVATION_CANCELLED", reserved.txId().value(), reader, "CANCELLED");

        var manualTx = seedManualHandlingTransaction(reader, manualTitle);
        accepted(loans.applyManualAction(librarianActor, new LoanCommands.ManualAction(
                TxId.of(manualTx), "REJECT", "读者无法到馆", librarianActor)));
        assertAudit("MANUAL_ACTION_APPLIED", manualTx, librarian, "REJECTED");
    }

    private void assertAudit(String eventType, long relatedId, long actorId, String outcome) {
        var rows = jdbc.queryForList("""
                SELECT actor_id, detail->>'relatedId' AS related, detail->>'outcome' AS outcome
                FROM audit_event WHERE event_type=? AND detail->>'relatedId'=?
                """, eventType, Long.toString(relatedId));
        assertThat(rows).as("%s 的审计行（relatedId=%s）", eventType, relatedId).isNotEmpty();
        assertThat(rows).anySatisfy(row -> {
            assertThat(((Number) row.get("actor_id")).longValue()).isEqualTo(actorId);
            assertThat(row.get("outcome")).isEqualTo(outcome);
        });
    }

    private String seedAndLoginUsername(long accountId) {
        return jdbc.queryForObject("SELECT username FROM account WHERE id=?", String.class, accountId);
    }

    private List<com.fasterxml.jackson.databind.JsonNode> getAuditEvents(String path, String bearer) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(bearer);
        var response = rest.exchange(path, org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        try {
            var items = json.readTree(response.getBody()).path("data").path("items");
            assertThat(items.isArray()).isTrue();
            var nodes = new java.util.ArrayList<com.fasterxml.jackson.databind.JsonNode>();
            items.forEach(nodes::add);
            return nodes;
        } catch (Exception parseFailure) {
            throw new IllegalStateException("审计查询响应不可解析", parseFailure);
        }
    }

    private String login(String username) {
        var headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "def011-login-" + username);
        var response = rest.exchange("/api/v1/auth/sessions", org.springframework.http.HttpMethod.POST,
                new HttpEntity<>("{\"username\":\"%s\",\"password\":\"password123\"}"
                        .formatted(username), headers), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        try {
            return json.readTree(response.getBody()).at("/data/accessToken").asText();
        } catch (Exception parseFailure) {
            throw new IllegalStateException("登录响应不可解析", parseFailure);
        }
    }

    private long seedAccount(String username, String role) {
        return jdbc.queryForObject("""
                INSERT INTO account (username, password_hash, display_name, role)
                VALUES (?, ?, ?, ?) RETURNING id
                """, Long.class, username, PasswordHasher.hash("password123"), username, role);
    }

    private long seedTitle(String isbnPrefix) {
        return jdbc.queryForObject("""
                INSERT INTO book_title (isbn, title, price_cents) VALUES (?, 'DEF-M6-011 书目', 9900)
                RETURNING id
                """, Long.class, isbnPrefix + (System.nanoTime() % 100_000_000L));
    }

    private long seedCopy(long titleId, String copyNo) {
        return jdbc.queryForObject("""
                INSERT INTO book_copy (title_id, copy_no, status) VALUES (?, ?, 'SHELF_READY')
                RETURNING id
                """, Long.class, titleId, copyNo);
    }

    private long seedManualHandlingTransaction(long readerId, long titleId) {
        return jdbc.queryForObject("""
                INSERT INTO borrow_transaction (reader_id, title_id, status, reservation_date,
                                                pickup_slot)
                VALUES (?, ?, 'MANUAL_HANDLING', current_date, 'AM')
                RETURNING id
                """, Long.class, readerId, titleId);
    }

    @SuppressWarnings("unchecked")
    private static <T> T accepted(ModuleResult<T> result) {
        assertThat(result).isInstanceOf(ModuleResult.Accepted.class);
        return ((ModuleResult.Accepted<T>) result).value();
    }

    private static ActorContext actor(long id, Role role) {
        return new ActorContext(AccountId.of(id), role, Instant.parse("2026-09-22T08:00:00Z"));
    }
}
