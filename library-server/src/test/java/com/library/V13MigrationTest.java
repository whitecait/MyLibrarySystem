package com.library;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Date;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** V13: loan eligibility backfill keeps old inventory usable and defers ambiguous same-day facts. */
@Testcontainers
class V13MigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            "postgres@sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3");

    @Test
    void v13BackfillsSameDayAndOlderCopyEligibilityByShanghaiBusinessDate() throws Exception {
        var source = source(createDatabase());
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("12").load().migrate();
        var jdbc = new JdbcTemplate(source);
        var businessDate = jdbc.queryForObject(
                "SELECT (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Shanghai')::date", LocalDate.class);
        var titleId = jdbc.queryForObject("""
                INSERT INTO book_title (title, price_cents) VALUES ('v13-backfill-' || ?, 1000) RETURNING id
                """, Long.class, UUID.randomUUID());

        var oldReady = insertCopy(jdbc, titleId, "old-ready", "SHELF_READY", businessDate.minusDays(5));
        var todayReady = insertCopy(jdbc, titleId, "today-ready", "SHELF_READY", businessDate);
        var todayReserved = insertCopy(jdbc, titleId, "today-reserved", "RESERVED", businessDate);
        var todayPending = insertCopy(jdbc, titleId, "today-pending", "PENDING_SHELVING", businessDate);

        var upgraded = Flyway.configure().dataSource(source).locations("classpath:db/migration").target("13").load();
        assertThat(upgraded.migrate().migrationsExecuted).isOne();
        assertThat(upgraded.validateWithResult().validationSuccessful).isTrue();
        assertThat(jdbc.queryForObject("SELECT loan_eligible_on FROM book_copy WHERE id = ?",
                LocalDate.class, oldReady)).isEqualTo(businessDate);
        assertThat(jdbc.queryForObject("SELECT loan_eligible_on FROM book_copy WHERE id = ?",
                LocalDate.class, todayReady)).isEqualTo(businessDate.plusDays(1));
        assertThat(jdbc.queryForObject("SELECT loan_eligible_on FROM book_copy WHERE id = ?",
                LocalDate.class, todayReserved)).isEqualTo(businessDate.plusDays(1));
        assertThat(jdbc.queryForObject("SELECT loan_eligible_on FROM book_copy WHERE id = ?",
                LocalDate.class, todayPending)).isEqualTo(businessDate.plusDays(1));
        assertThat(upgraded.migrate().migrationsExecuted).isZero();
    }

    private long insertCopy(JdbcTemplate jdbc, long titleId, String copyNo,
                            String status, LocalDate updatedBusinessDate) {
        var copyId = jdbc.queryForObject("""
                INSERT INTO book_copy (title_id, copy_no, status) VALUES (?, ?, ?) RETURNING id
                """, Long.class, titleId, copyNo, status);
        jdbc.update("""
                UPDATE book_copy
                SET updated_at = (?::date + time '12:00') AT TIME ZONE 'Asia/Shanghai'
                WHERE id = ?
                """, Date.valueOf(updatedBusinessDate), copyId);
        return copyId;
    }

    private String createDatabase() throws Exception {
        var name = "v13_" + UUID.randomUUID().toString().replace("-", "");
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
