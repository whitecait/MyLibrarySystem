package com.library.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DEF-M6-007 回归：复核三端点控制器层角色矩阵。
 * 契约（openapi.yaml x-permission）：GET /reviews＝LIBRARIAN_OR_ADMIN、
 * POST /reviews＝LIBRARIAN、POST /reviews/{id}/decision＝ADMIN（AT-0002 §2.3 R5）。
 * 无权组合仍断言 403/MLS-ID-005；已下线的退款复核按角色门后明确返回 422，且不写入。
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DEFM6007ReviewRoleMatrixTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            "postgres@sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3")
            .withLabel("com.mylibrary.p1-stage", "p1-core-20260926");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void roleMatrixOverThreeReviewEndpoints() throws Exception {
        var readerToken = registerAndLogin("def007-reader-" + System.nanoTime());
        var librarianName = "def007-librarian-" + System.nanoTime();
        var librarianToken = seedAndLogin(librarianName, "LIBRARIAN");
        var adminToken = seedAndLogin("def007-admin-" + System.nanoTime(), "ADMIN");
        long librarianId = jdbc.queryForObject(
                "SELECT id FROM account WHERE username=?", Long.class, librarianName);
        long pendingReview = seedPendingRefundReview(librarianId);

        // 匿名访问保持 401：补角色检查不得改变认证先行顺序
        var anonymous = get("/api/v1/reviews", null);
        assertThat(anonymous.getStatusCode().value()).isEqualTo(401);
        assertThat(code(anonymous)).isEqualTo("MLS-ID-001");

        // GET /reviews：READER 403；LIBRARIAN/ADMIN 200
        assertForbidden(get("/api/v1/reviews", readerToken));
        assertThat(get("/api/v1/reviews", librarianToken).getStatusCode().value()).isEqualTo(200);
        assertThat(get("/api/v1/reviews", adminToken).getStatusCode().value()).isEqualTo(200);

        // POST /reviews：READER/ADMIN 403；LIBRARIAN 200 且复核成立
        String request = "{\"targetType\":\"REFUND\",\"targetId\":\"1\",\"action\":\"REFUND\","
                + "\"payload\":{\"amountCents\":100},\"reason\":\"DEF-M6-007 矩阵\"}";
        assertForbidden(post("/api/v1/reviews", readerToken, "def007-post-reader", request));
        int pendingBefore = jdbc.queryForObject("SELECT count(*) FROM review_request", Integer.class);
        var librarianRequest = post("/api/v1/reviews", librarianToken, "def007-post-librarian", request);
        assertThat(librarianRequest.getStatusCode().value()).isEqualTo(422);
        assertThat(code(librarianRequest)).isEqualTo("MLS-PAY-009");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM review_request", Integer.class))
                .isEqualTo(pendingBefore);
        assertForbidden(post("/api/v1/reviews", adminToken, "def007-post-admin", request));

        // POST /reviews/{id}/decision：READER/LIBRARIAN 403 且复核仍 PENDING；ADMIN 200 并落地决定
        String approve = "{\"decision\":\"APPROVED\",\"reason\":\"越权尝试必须被拒\"}";
        assertForbidden(post("/api/v1/reviews/" + pendingReview + "/decision",
                readerToken, "def007-dec-reader", approve));
        assertThat(decisionOf(pendingReview)).isEqualTo("PENDING");
        assertForbidden(post("/api/v1/reviews/" + pendingReview + "/decision",
                librarianToken, "def007-dec-librarian", approve));
        assertThat(decisionOf(pendingReview)).isEqualTo("PENDING");
        var adminDecision = post("/api/v1/reviews/" + pendingReview + "/decision",
                adminToken, "def007-dec-admin", "{\"decision\":\"APPROVED\",\"reason\":\"旧退款申请不得生效\"}");
        assertThat(adminDecision.getStatusCode().value()).isEqualTo(422);
        assertThat(code(adminDecision)).isEqualTo("MLS-PAY-009");
        assertThat(decisionOf(pendingReview)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refund_record", Integer.class)).isZero();
    }

    @Test
    void retiredWritePathsRejectEveryRoleAndCreateNoBusinessRows() throws Exception {
        var readerToken = registerAndLogin("def007-retired-reader-" + System.nanoTime());
        var librarianName = "def007-retired-librarian-" + System.nanoTime();
        var librarianToken = seedAndLogin(librarianName, "LIBRARIAN");
        var adminToken = seedAndLogin("def007-retired-admin-" + System.nanoTime(), "ADMIN");
        long librarianId = jdbc.queryForObject(
                "SELECT id FROM account WHERE username=?", Long.class, librarianName);
        long pendingReview = seedPendingRefundReview(librarianId);
        long pendingException = jdbc.queryForObject("""
                INSERT INTO review_request(applicant_id,target_type,target_id,reason,action_type,
                                           action_payload,target_version)
                VALUES (?,'CIRCULATION_EXCEPTION',1,'legacy exception','OVERRIDE','{}'::jsonb,1)
                RETURNING id
                """, Long.class, librarianId);
        String[] actors = {null, readerToken, librarianToken, adminToken};
        int refundsBefore = jdbc.queryForObject("SELECT count(*) FROM refund_record", Integer.class);
        int reconciliationsBefore = jdbc.queryForObject("SELECT count(*) FROM reconciliation_record", Integer.class);

        for (var actor : actors) {
            var refund = postWithoutBodyOrKey("/api/v1/orders/9001/refunds", actor);
            assertThat(refund.getStatusCode().value()).isEqualTo(422);
            assertThat(code(refund)).isEqualTo("MLS-PAY-009");
            var reconciliation = postWithoutBodyOrKey("/api/v1/reconciliations", actor);
            assertThat(reconciliation.getStatusCode().value()).isEqualTo(422);
            assertThat(code(reconciliation)).isEqualTo("MLS-PAY-009");
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM refund_record", Integer.class))
                .isEqualTo(refundsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reconciliation_record", Integer.class))
                .isEqualTo(reconciliationsBefore);

        int reviewsBefore = jdbc.queryForObject("SELECT count(*) FROM review_request", Integer.class);
        var refundRequest = "{\"targetType\":\"REFUND\",\"targetId\":\"1\",\"action\":\"REFUND\","
                + "\"payload\":{},\"reason\":\"disabled\"}";
        var refundResponse = post("/api/v1/reviews", librarianToken,
                "def007-retired-refund-review", refundRequest);
        assertThat(refundResponse.getStatusCode().value()).isEqualTo(422);
        assertThat(code(refundResponse)).isEqualTo("MLS-PAY-009");
        var exceptionRequest = "{\"targetType\":\"CIRCULATION_EXCEPTION\",\"targetId\":\"1\","
                + "\"action\":\"OVERRIDE\",\"payload\":{},\"reason\":\"disabled\"}";
        var exceptionResponse = post("/api/v1/reviews", librarianToken,
                "def007-retired-exception-review", exceptionRequest);
        assertThat(exceptionResponse.getStatusCode().value()).isEqualTo(422);
        assertThat(code(exceptionResponse)).isEqualTo("MLS-REV-003");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM review_request", Integer.class))
                .isEqualTo(reviewsBefore);

        var oldRefundDecision = post("/api/v1/reviews/" + pendingReview + "/decision", adminToken,
                "def007-retired-old-refund", "{\"decision\":\"APPROVED\",\"reason\":\"legacy\"}");
        assertThat(oldRefundDecision.getStatusCode().value()).isEqualTo(422);
        assertThat(code(oldRefundDecision)).isEqualTo("MLS-PAY-009");
        assertThat(decisionOf(pendingReview)).isEqualTo("PENDING");

        var oldExceptionDecision = post("/api/v1/reviews/" + pendingException + "/decision", adminToken,
                "def007-retired-old-exception", "{\"decision\":\"APPROVED\",\"reason\":\"legacy\"}");
        assertThat(oldExceptionDecision.getStatusCode().value()).isEqualTo(422);
        assertThat(code(oldExceptionDecision)).isEqualTo("MLS-REV-003");
        assertThat(decisionOf(pendingException)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refund_record", Integer.class))
                .isEqualTo(refundsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reconciliation_record", Integer.class))
                .isEqualTo(reconciliationsBefore);
    }

    @Test
    void accountChangeApprovalAndAuditCommitOrRollBackTogether() throws Exception {
        var readerName = "p1-account-reader-" + System.nanoTime();
        var readerToken = registerAndLogin(readerName);
        var librarianToken = seedAndLogin("p1-account-librarian-" + System.nanoTime(), "LIBRARIAN");
        var adminToken = seedAndLogin("p1-account-admin-" + System.nanoTime(), "ADMIN");
        long readerId = jdbc.queryForObject("SELECT id FROM account WHERE username=?", Long.class, readerName);
        var requestKey = "p1-account-suspend-" + System.nanoTime();

        var request = post("/api/v1/accounts/" + readerId + "/change-requests", librarianToken,
                requestKey, "{\"action\":\"SUSPEND\",\"payload\":{},\"reason\":\"P1 原子性回归\"}");
        assertThat(request.getStatusCode().value()).isEqualTo(200);
        long reviewId = json.readTree(request.getBody()).at("/data/reviewId").asLong();
        assertThat(jdbc.queryForObject("SELECT status FROM account WHERE id=?", String.class, readerId))
                .isEqualTo("ACTIVE");
        assertThat(decisionOf(reviewId)).isEqualTo("PENDING");

        var approve = "{\"decision\":\"APPROVED\",\"reason\":\"核验后停用\"}";
        assertForbidden(post("/api/v1/reviews/" + reviewId + "/decision", readerToken,
                "p1-reader-cannot-approve-" + reviewId, approve));
        assertForbidden(post("/api/v1/reviews/" + reviewId + "/decision", librarianToken,
                "p1-librarian-cannot-approve-" + reviewId, approve));
        assertThat(jdbc.queryForObject("SELECT status FROM account WHERE id=?", String.class, readerId))
                .isEqualTo("ACTIVE");
        assertThat(decisionOf(reviewId)).isEqualTo("PENDING");

        jdbc.execute("""
                CREATE FUNCTION p1_reject_review_decision_audit() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.event_type = 'REVIEW_DECIDED' THEN
                        RAISE EXCEPTION 'P1 injected review audit failure';
                    END IF;
                    RETURN NEW;
                END $$
                """);
        jdbc.execute("""
                CREATE TRIGGER p1_reject_review_decision_audit
                BEFORE INSERT ON audit_event FOR EACH ROW
                EXECUTE FUNCTION p1_reject_review_decision_audit()
                """);
        var failedDecision = post("/api/v1/reviews/" + reviewId + "/decision", adminToken,
                "p1-approve-suspend-" + reviewId, approve);
        assertThat(failedDecision.getStatusCode().value()).isEqualTo(500);
        assertThat(jdbc.queryForObject("SELECT status FROM account WHERE id=?", String.class, readerId))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT version FROM account WHERE id=?", Long.class, readerId)).isZero();
        assertThat(decisionOf(reviewId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_event
                WHERE event_type='REVIEW_DECIDED' AND detail->>'relatedId'=?
                """, Integer.class, Long.toString(reviewId))).isZero();
        jdbc.execute("DROP TRIGGER p1_reject_review_decision_audit ON audit_event");
        jdbc.execute("DROP FUNCTION p1_reject_review_decision_audit()");

        var approved = post("/api/v1/reviews/" + reviewId + "/decision", adminToken,
                "p1-approve-suspend-" + reviewId, approve);
        assertThat(approved.getStatusCode().value()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT status FROM account WHERE id=?", String.class, readerId))
                .isEqualTo("SUSPENDED");
        assertThat(jdbc.queryForObject("SELECT version FROM account WHERE id=?", Long.class, readerId)).isEqualTo(1);
        assertThat(decisionOf(reviewId)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_event
                WHERE event_type='REVIEW_DECIDED' AND detail->>'relatedId'=?
                """, Integer.class, Long.toString(reviewId))).isOne();

        var duplicate = post("/api/v1/reviews/" + reviewId + "/decision", adminToken,
                "p1-duplicate-decision-" + reviewId, approve);
        assertThat(duplicate.getStatusCode().value()).isEqualTo(422);
        assertThat(code(duplicate)).isEqualTo("MLS-REV-002");
        assertThat(jdbc.queryForObject("SELECT version FROM account WHERE id=?", Long.class, readerId)).isEqualTo(1);

        var restoreReview = requestAccountChange(librarianToken, readerId, "RESTORE", "{}", "恢复账户");
        decide(adminToken, restoreReview, "APPROVED", "恢复");
        assertThat(jdbc.queryForObject("SELECT status FROM account WHERE id=?", String.class, readerId))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT version FROM account WHERE id=?", Long.class, readerId)).isEqualTo(2);

        var roleReview = requestAccountChange(librarianToken, readerId, "CHANGE_ROLE",
                "{\"role\":\"LIBRARIAN\"}", "调整职能");
        decide(adminToken, roleReview, "APPROVED", "调整职能");
        assertThat(jdbc.queryForObject("SELECT role FROM account WHERE id=?", String.class, readerId))
                .isEqualTo("LIBRARIAN");
        assertThat(jdbc.queryForObject("SELECT version FROM account WHERE id=?", Long.class, readerId)).isEqualTo(3);

        var rejectName = "p1-account-reject-reader-" + System.nanoTime();
        registerAndLogin(rejectName);
        long rejectReaderId = jdbc.queryForObject("SELECT id FROM account WHERE username=?", Long.class, rejectName);
        var rejectReview = requestAccountChange(librarianToken, rejectReaderId, "SUSPEND", "{}", "待拒绝");
        decide(adminToken, rejectReview, "REJECTED", "不予停用");
        assertThat(jdbc.queryForObject("SELECT status FROM account WHERE id=?", String.class, rejectReaderId))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT version FROM account WHERE id=?", Long.class, rejectReaderId)).isZero();
        assertThat(decisionOf(rejectReview)).isEqualTo("REJECTED");
    }

    private long requestAccountChange(String librarianToken, long accountId, String action,
                                     String payload, String reason) throws Exception {
        var body = "{\"action\":\"%s\",\"payload\":%s,\"reason\":\"%s\"}"
                .formatted(action, payload, reason);
        var response = post("/api/v1/accounts/" + accountId + "/change-requests", librarianToken,
                "p1-account-request-" + action + "-" + System.nanoTime(), body);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return json.readTree(response.getBody()).at("/data/reviewId").asLong();
    }

    private void decide(String adminToken, long reviewId, String decision, String reason) throws Exception {
        var response = post("/api/v1/reviews/" + reviewId + "/decision", adminToken,
                "p1-account-decision-" + reviewId, "{\"decision\":\"%s\",\"reason\":\"%s\"}"
                        .formatted(decision, reason));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    private String registerAndLogin(String username) throws Exception {
        var register = post("/api/v1/auth/registrations", null, "def007-reg-" + username,
                "{\"username\":\"%s\",\"password\":\"password123\",\"displayName\":\"%s\"}"
                        .formatted(username, username));
        assertThat(register.getStatusCode().value()).isEqualTo(200);
        return login(username);
    }

    private String seedAndLogin(String username, String role) throws Exception {
        jdbc.update("""
                INSERT INTO account (username, password_hash, display_name, role)
                VALUES (?, ?, ?, ?)
                """, username, PasswordHasher.hash("password123"), username, role);
        return login(username);
    }

    private String login(String username) throws Exception {
        var session = post("/api/v1/auth/sessions", null, "def007-login-" + username,
                "{\"username\":\"%s\",\"password\":\"password123\"}".formatted(username));
        assertThat(session.getStatusCode().value()).isEqualTo(200);
        return json.readTree(session.getBody()).at("/data/accessToken").asText();
    }

    private long seedPendingRefundReview(long applicantId) {
        return jdbc.queryForObject("""
                INSERT INTO review_request(applicant_id,target_type,target_id,reason,action_type,
                                           action_payload,target_version)
                VALUES (?,'REFUND',1,'DEF-M6-007 矩阵种子','REFUND','{"amountCents":100}'::jsonb,1)
                RETURNING id
                """, Long.class, applicantId);
    }

    private String decisionOf(long reviewId) {
        return jdbc.queryForObject(
                "SELECT decision FROM review_request WHERE id=?", String.class, reviewId);
    }

    private void assertForbidden(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(code(response)).isEqualTo("MLS-ID-005");
    }

    private String code(ResponseEntity<String> response) throws Exception {
        return json.readTree(response.getBody()).path("code").asText();
    }

    private ResponseEntity<String> post(String path, String bearer, String idempotencyKey, String body) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        if (bearer != null) {
            headers.setBearerAuth(bearer);
        }
        return rest.exchange(path, org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> get(String path, String bearer) {
        var headers = new HttpHeaders();
        if (bearer != null) {
            headers.setBearerAuth(bearer);
        }
        return rest.exchange(path, org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> postWithoutBodyOrKey(String path, String bearer) {
        var headers = new HttpHeaders();
        if (bearer != null) {
            headers.setBearerAuth(bearer);
        }
        return rest.exchange(path, org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(headers), String.class);
    }
}
