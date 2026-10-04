package com.library;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V14 adds creation-time reservation guarantee metadata without guessing unknown history. */
@Testcontainers
class V14GuaranteeTypeMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            "postgres@sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3");

    @Test
    void v14MigratesAnEmptyDatabaseAndEnforcesTheNullableEnum() throws Exception {
        var source = source(createDatabase());
        var flyway = Flyway.configure().dataSource(source).locations("classpath:db/migration").load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(14);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();

        var jdbc = new JdbcTemplate(source);
        var readerId = seedReader(jdbc, "v14-empty-reader");
        var titleId = seedTitle(jdbc, "v14-empty-title");
        var txId = insertTransaction(jdbc, readerId, titleId, "MANUAL_HANDLING");
        assertThat(guaranteeType(jdbc, txId)).isNull();

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE borrow_transaction SET guarantee_type = 'UNKNOWN' WHERE id = ?", txId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v14BackfillsOnlyUnambiguousPositiveReservationCreationFacts() throws Exception {
        var source = source(createDatabase());
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("13").load().migrate();
        var jdbc = new JdbcTemplate(source);
        var readerId = seedReader(jdbc, "v14-backfill-reader");

        var direct = insertTransaction(jdbc, readerId, seedTitle(jdbc, "direct"), "NOT_BORROWED");
        var assignedWaitlist = insertTransaction(jdbc, readerId, seedTitle(jdbc, "assigned-waitlist"), "CONFIRMED");
        var missing = insertTransaction(jdbc, readerId, seedTitle(jdbc, "missing"), "CONFIRMED");
        var wrongEvent = insertTransaction(jdbc, readerId, seedTitle(jdbc, "wrong-event"), "CONFIRMED");
        var sameNumberOtherEvent = insertTransaction(jdbc, readerId, seedTitle(jdbc, "same-number-other-event"), "CONFIRMED");
        var conflicting = insertTransaction(jdbc, readerId, seedTitle(jdbc, "conflicting"), "CONFIRMED");
        var unknownOutcome = insertTransaction(jdbc, readerId, seedTitle(jdbc, "unknown-outcome"), "CONFIRMED");
        var stringId = insertTransaction(jdbc, readerId, seedTitle(jdbc, "string-id"), "CONFIRMED");

        audit(jdbc, "RESERVATION_DECIDED", direct, "CONFIRMED");
        audit(jdbc, "RESERVATION_DECIDED", direct, "CONFIRMED"); // identical positive facts are safe
        audit(jdbc, "RESERVATION_DECIDED", assignedWaitlist, "WAITLIST_ENQUEUED");
        audit(jdbc, "RESERVATION_CANCELLED", wrongEvent, "CONFIRMED");
        audit(jdbc, "WALKIN_LOAN_BATCH", sameNumberOtherEvent, "LENT");
        audit(jdbc, "RESERVATION_DECIDED", conflicting, "CONFIRMED");
        audit(jdbc, "RESERVATION_DECIDED", conflicting, "WAITLIST_ENQUEUED");
        audit(jdbc, "RESERVATION_DECIDED", unknownOutcome, "CONFIRMED");
        audit(jdbc, "RESERVATION_DECIDED", unknownOutcome, "MANUAL_HANDLING");
        auditJson(jdbc, "RESERVATION_DECIDED", "{\"relatedId\":\"" + stringId
                + "\",\"outcome\":\"CONFIRMED\"}");
        auditJson(jdbc, "RESERVATION_DECIDED",
                "{\"relatedId\":9223372036854775808,\"outcome\":\"CONFIRMED\"}");

        var upgraded = Flyway.configure().dataSource(source).locations("classpath:db/migration").target("14").load();
        assertThat(upgraded.migrate().migrationsExecuted).isOne();
        assertThat(upgraded.validateWithResult().validationSuccessful).isTrue();
        assertThat(upgraded.migrate().migrationsExecuted).isZero();

        assertThat(guaranteeType(jdbc, direct)).isEqualTo("DIRECT_CONFIRMATION");
        assertThat(guaranteeType(jdbc, assignedWaitlist)).isEqualTo("WAITLIST");
        for (var unknown : new long[]{missing, wrongEvent, sameNumberOtherEvent,
                conflicting, unknownOutcome, stringId}) {
            assertThat(guaranteeType(jdbc, unknown)).as("transaction %s remains unknown", unknown).isNull();
        }
    }

    private long seedReader(JdbcTemplate jdbc, String username) {
        return jdbc.queryForObject("""
                INSERT INTO account (username, password_hash, display_name, role)
                VALUES (?, 'x', ?, 'READER') RETURNING id
                """, Long.class, username + "-" + UUID.randomUUID(), username);
    }

    private long seedTitle(JdbcTemplate jdbc, String title) {
        return jdbc.queryForObject("""
                INSERT INTO book_title (title, price_cents) VALUES (?, 1000) RETURNING id
                """, Long.class, title + "-" + UUID.randomUUID());
    }

    private long insertTransaction(JdbcTemplate jdbc, long readerId, long titleId, String status) {
        return jdbc.queryForObject("""
                INSERT INTO borrow_transaction (reader_id, title_id, status)
                VALUES (?, ?, ?) RETURNING id
                """, Long.class, readerId, titleId, status);
    }

    private void audit(JdbcTemplate jdbc, String eventType, long relatedId, String outcome) {
        auditJson(jdbc, eventType,
                "{\"relatedId\":" + relatedId + ",\"outcome\":\"" + outcome + "\"}");
    }

    private void auditJson(JdbcTemplate jdbc, String eventType, String detail) {
        jdbc.update("INSERT INTO audit_event (event_type, detail) VALUES (?, ?::jsonb)", eventType, detail);
    }

    private String guaranteeType(JdbcTemplate jdbc, long txId) {
        return jdbc.queryForObject("SELECT guarantee_type FROM borrow_transaction WHERE id = ?",
                String.class, txId);
    }

    private String createDatabase() throws Exception {
        var name = "v14_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        }
        return name;
    }

    private DriverManagerDataSource source(String database) {
        var url = POSTGRES.getJdbcUrl().replaceFirst("/test\\?", "/" + database + "?");
        return new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
