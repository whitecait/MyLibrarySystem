package com.library;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M4 出口：正式 V1 DDL 迁移测试（GOV-0006 §6.5 迁移与验证）。
 * 验证：迁移成功且幂等、核心对象存在、建单独占部分唯一索引（BR-0047/SC-Q14）、
 * 终态释放后可再建单（SC-Q15）、重复预约守卫与按归还事务去重的罚款（BR-0020/0019）。
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class V1MigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            "postgres@sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3")
            .withLabel("com.mylibrary.p1-stage", "p1-core-20260927-v10-migration");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    @Test
    void v1CoreSchemaAppliedOnceAndRevalidationIsNoOp() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM flyway_schema_history
                WHERE version = '1' AND script = 'V1__core_schema.sql' AND success = true
                """, Integer.class)).isEqualTo(1);
        var before = jdbc.queryForList(
                "SELECT version, script, checksum FROM flyway_schema_history WHERE version = '1'");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForList(
                "SELECT version, script, checksum FROM flyway_schema_history WHERE version = '1'"))
                .isEqualTo(before);
    }

    @Test
    void coreTablesAndIndexesExist() {
        var tables = jdbc.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class);
        assertThat(tables).containsAll(List.of(
                "account", "book_title", "book_copy", "borrow_transaction", "fine",
                "payment_order", "closing_control", "closing_batch", "review_request",
                "outbox_event", "idempotency_key", "notification", "audit_event",
                "ai_session", "ai_message", "ai_quota_usage"));
        // 建单独占：部分唯一索引存在且谓词正确
        var indexDef = jdbc.queryForObject("""
                SELECT indexdef FROM pg_indexes
                WHERE indexname = 'uq_payment_order_unreleased'
                """, String.class);
        assertThat(indexDef).contains("UNIQUE INDEX").contains("payment_order").contains("OCCUPIED");
        assertThat(jdbc.queryForObject("SELECT pg_get_expr(indpred, indrelid) FROM pg_index WHERE indexrelid = 'uq_payment_order_unreleased'::regclass", String.class)).contains("status").contains("OCCUPIED");
        // pg_trgm 扩展与 GIN 索引（T4 检索）
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'pg_trgm'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_indexes WHERE indexname = 'ix_book_title_trgm'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void paymentOrderExclusivityAllowsSuccessorAfterRelease() {
        long reader = insertReader("pay-reader");
        long txId = insertBorrowTx(reader);
        long fineId = insertFine(reader, txId);
        long first = insertOrder(fineId);
        // 同罚款第二个未释放订单被数据库拒绝（SC-Q14：至多一个未释放订单）
        assertThatThrownBy(() -> insertOrder(fineId))
                .isInstanceOf(DataIntegrityViolationException.class);
        // 支付尝试超时不释放（保持 OCCUPIED）——直接验证关闭/释放语义：
        // 关闭事务提交成功（OCCUPIED→CLOSED）后释放，才允许继任订单
        jdbc.update("UPDATE payment_order SET status = 'CLOSED', closed_at = now() WHERE id = ?", first);
        long successor = insertOrder(fineId);
        // 继任订单支付成功结清罚款（PAID 终态）
        jdbc.update("UPDATE payment_order SET status = 'PAID', paid_at = now() WHERE id = ?", successor);
        jdbc.update("UPDATE fine SET status = 'SETTLED', settled_at = now() WHERE id = ?", fineId);
        // 已结清罚款不得再建单：入库即被 CHECK/唯一索引之外的业务守卫拒绝——
        // 数据库层不阻止 CLOSED/PAID 行插入，但 DUE 罚款已不存在，建单守卫在用例层拒绝；
        // 此处验证已终结订单不再占用：可再插入的唯一约束不再阻止任何新行
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM payment_order WHERE fine_id = ? AND status = 'OCCUPIED'",
                Integer.class, fineId)).isZero();
    }

    @Test
    void duplicateReservationAndDuplicateFineForSameReturnAreRejected() {
        long reader = insertReader("guard-reader");
        long title = insertTitle("重构");
        // 同读者同书目未终结事务唯一（重复预约守卫）
        insertBorrowTx(reader, title);
        assertThatThrownBy(() -> insertBorrowTx(reader, title))
                .isInstanceOf(DataIntegrityViolationException.class);
        // 同一归还事务不能重复计罚；不同归还事务允许同时欠费。
        long txId = insertBorrowTx(insertReader("fine-reader"));
        insertFine(txId);
        assertThatThrownBy(() -> insertFine(txId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void separateOverdueReturnsCanCreateMultipleUnpaidFinesForOneReader() {
        long reader = insertReader("multiple-fines-reader");
        long firstTx = insertBorrowTx(reader);
        long secondTx = insertBorrowTx(reader);
        insertFine(reader, firstTx);
        insertFine(reader, secondTx);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM fine WHERE reader_id = ? AND status = 'DUE'",
                Integer.class, reader)).isEqualTo(2);
        assertThatThrownBy(() -> insertFine(reader, firstTx))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v2UpgradePreservesExistingFineAndAllowsAnotherReturn() {
        jdbc.execute("CREATE DATABASE v1_upgrade");
        var source = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/v1_upgrade"),
                POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("1").load().migrate();
        var old = new JdbcTemplate(source);
        old.update("INSERT INTO account (username,password_hash,display_name,role) VALUES ('upgrade','x','upgrade','READER')");
        old.update("INSERT INTO book_title (title,price_cents) VALUES ('first',100),('second',100)");
        old.update("INSERT INTO borrow_transaction (reader_id,title_id,status) VALUES (1,1,'RETURNED'),(1,2,'RETURNED')");
        old.update("INSERT INTO fine (reader_id,source_tx_id,amount_cents) VALUES (1,1,50)");
        var upgraded = Flyway.configure().dataSource(source).locations("classpath:db/migration").target("2").load();
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);
        upgraded.validate();
        assertThat(old.queryForObject("SELECT amount_cents FROM fine WHERE source_tx_id=1", Long.class)).isEqualTo(50L);
        old.update("INSERT INTO fine (reader_id,source_tx_id,amount_cents) VALUES (1,2,75)");
        assertThat(old.queryForObject("SELECT count(*) FROM fine WHERE status='DUE'", Integer.class)).isEqualTo(2);
        assertThat(upgraded.migrate().migrationsExecuted).isZero();
    }

    @Test
    void v10UpgradeAddsConservativeLoanStartLowerBoundAndPreservesLiveLoans() {
        jdbc.execute("CREATE DATABASE v10_upgrade");
        var source = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/v10_upgrade"),
                POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("9").load().migrate();
        var old = new JdbcTemplate(source);
        old.update("INSERT INTO account (username,password_hash,display_name,role) VALUES ('v10-reader','x','v10-reader','READER')");
        old.update("INSERT INTO book_title (title,price_cents) VALUES ('v10-renewed',1000),('v10-no-due-date',1000)");
        old.update("INSERT INTO book_copy (title_id,copy_no,status) VALUES (1,'v10-copy-1','LENT'),(2,'v10-copy-2','LENT')");
        var actualOldStart = LocalDate.of(2026, 8, 1);
        var shortenedRenewalDue = actualOldStart.plusDays(36); // 30-day initial term plus a 6-day old renewal
        var dueWithoutDate = LocalDate.of(2026, 9, 20);
        old.update("""
                INSERT INTO borrow_transaction(reader_id,title_id,copy_id,status,reservation_date,due_date,renewal_count)
                VALUES (1,1,1,'LENT',?,?,1),(1,2,2,'LENT',?,NULL,0)
                """, java.sql.Date.valueOf(actualOldStart), java.sql.Date.valueOf(shortenedRenewalDue),
                java.sql.Date.valueOf(dueWithoutDate));

        var upgraded = Flyway.configure().dataSource(source).locations("classpath:db/migration").load();
        // V10 adds the conservative lower bound, V11 tracks contiguous closing progress,
        // V12 adds registration-session rate-limit storage, and V13 adds copy loan eligibility.
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(4);
        upgraded.validate();

        var inferredLowerBound = shortenedRenewalDue.minusDays(30 + 14);
        assertThat(inferredLowerBound).isEqualTo(actualOldStart.minusDays(8));
        assertThat(old.queryForObject("SELECT loan_started_date FROM borrow_transaction WHERE title_id=1",
                java.sql.Date.class).toLocalDate()).isEqualTo(inferredLowerBound);
        assertThat(old.queryForMap("SELECT status,due_date,renewal_count FROM borrow_transaction WHERE title_id=1"))
                .containsEntry("status", "LENT")
                .containsEntry("due_date", java.sql.Date.valueOf(shortenedRenewalDue))
                .containsEntry("renewal_count", 1);
        assertThat(old.queryForObject("SELECT loan_started_date FROM borrow_transaction WHERE title_id=2",
                java.sql.Date.class).toLocalDate()).isEqualTo(dueWithoutDate);
    }

    @Test
    void consumerReceiptsDistinguishEventsAndConsumers() {
        long first = jdbc.queryForObject("INSERT INTO outbox_event (agg_type,agg_id,event_type,payload) VALUES ('BOOK',42,'UPDATED','{}') RETURNING id", Long.class);
        long second = jdbc.queryForObject("INSERT INTO outbox_event (agg_type,agg_id,event_type,payload) VALUES ('BOOK',42,'UPDATED','{}') RETURNING id", Long.class);
        jdbc.update("INSERT INTO event_consumer_receipt (consumer_id,event_id) VALUES ('notification',?),('notification',?),('audit',?)", first, second, first);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO event_consumer_receipt (consumer_id,event_id) VALUES ('notification',?)", first))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM event_consumer_receipt WHERE event_id IN (?,?)", Integer.class, first, second)).isEqualTo(3);
    }

    // ---- fixtures ----

    private long insertReader(String username) {
        jdbc.update("""
                INSERT INTO account (username, password_hash, display_name, role)
                VALUES (?, 'x', ?, 'READER')
                """, username, username);
        return jdbc.queryForObject("SELECT id FROM account WHERE username = ?", Long.class, username);
    }

    private long insertTitle(String name) {
        jdbc.update("""
                INSERT INTO book_title (title, price_cents) VALUES (?, 10000)
                """, name);
        return jdbc.queryForObject("SELECT id FROM book_title WHERE title = ?", Long.class, name);
    }

    private long insertBorrowTx(long readerId) {
        return insertBorrowTx(readerId, insertTitle("tx-book-" + System.nanoTime()));
    }

    private long insertBorrowTx(long readerId, long titleId) {
        jdbc.update("""
                INSERT INTO borrow_transaction (reader_id, title_id, status, reservation_date)
                VALUES (?, ?, 'AWAIT', current_date)
                """, readerId, titleId);
        return jdbc.queryForObject("""
                SELECT id FROM borrow_transaction WHERE reader_id = ? AND title_id = ?
                """, Long.class, readerId, titleId);
    }

    private long insertFine(long readerId, long txId) {
        jdbc.update("""
                INSERT INTO fine (reader_id, source_tx_id, amount_cents) VALUES (?, ?, 500)
                """, readerId, txId);
        return jdbc.queryForObject("SELECT id FROM fine WHERE source_tx_id = ?", Long.class, txId);
    }

    private long insertFine(long txId) {
        return insertFine(jdbc.queryForObject(
                "SELECT reader_id FROM borrow_transaction WHERE id = ?", Long.class, txId), txId);
    }

    private long insertOrder(long fineId) {
        jdbc.update("""
                INSERT INTO payment_order (fine_id, expires_at)
                VALUES (?, now() + interval '1 day')
                """, fineId);
        return jdbc.queryForObject("""
                SELECT id FROM payment_order WHERE fine_id = ? ORDER BY id DESC LIMIT 1
                """, Long.class, fineId);
    }
}
