package com.library;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/** SQL mechanism prototype against actual migrations; not the M5 business implementation. */
@Testcontainers
class LogicalClosingGateTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            "postgres@sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3");
    static final AtomicInteger DATABASE = new AtomicInteger();
    String url;

    @BeforeEach
    void prepare() throws Exception {
        String db = "logical_gate_" + DATABASE.incrementAndGet();
        try (var c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var s = c.createStatement()) { s.execute("CREATE DATABASE " + db); }
        url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + db);
        Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        try (var c = connect()) {
            execute(c, "INSERT INTO closing_control (scope) VALUES ('GLOBAL') ON CONFLICT(scope) DO NOTHING");
            execute(c, "INSERT INTO closing_admission(scope) VALUES ('GLOBAL') ON CONFLICT(scope) DO NOTHING");
        }
    }

    Connection connect() throws SQLException {
        var c = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
        c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        return c;
    }

    static void execute(Connection c, String sql) throws SQLException {
        try (var s = c.createStatement()) { s.execute(sql); }
    }

    static long value(Connection c, String sql) throws SQLException {
        try (var s = c.createStatement(); var r = s.executeQuery(sql)) {
            assertThat(r.next()).isTrue(); return r.getLong(1);
        }
    }

    static long admit(Connection c) throws SQLException {
        c.setAutoCommit(false);
        try (var s = c.createStatement(); var r = s.executeQuery(
                "SELECT status,version FROM closing_control WHERE scope='GLOBAL' FOR SHARE NOWAIT")) {
            if (!r.next()) throw new IllegalStateException("missing gate: fail closed");
            if (!r.getString(1).equals("OPEN") && !r.getString(1).equals("REOPENED"))
                throw new IllegalStateException("isolated");
            long epoch = r.getLong(2);
            try (var flag = c.createStatement(); var permit = flag.executeQuery(
                    "SELECT draining FROM closing_admission WHERE scope='GLOBAL'")) {
                if (!permit.next()) throw new IllegalStateException("missing admission: fail closed");
                if (permit.getBoolean(1)) throw new IllegalStateException("draining");
            }
            return epoch;
        }
    }

    static void isolate(Connection c) throws SQLException {
        if (!c.getAutoCommit()) throw new IllegalStateException("isolation requires fresh connection");
        // Independent commit: cannot wait on already granted control-row shares.
        execute(c, "UPDATE closing_admission SET draining=true WHERE scope='GLOBAL'");
        c.setAutoCommit(false);
        execute(c, "SET LOCAL lock_timeout='5s'");
        execute(c, "SELECT id FROM closing_control WHERE scope='GLOBAL' FOR UPDATE");
        execute(c, "UPDATE closing_control SET status='ISOLATED',version=version+1 WHERE scope='GLOBAL' AND status IN ('OPEN','REOPENED')");
        c.commit(); // This successful commit is logical T, not the scheduler's wall-clock tick.
    }

    @Test
    void isolationWaitsForExistingWriterThenRejectsNewWriter() throws Exception {
        try (var old = connect(); var closing = connect(); var observer = connect();
             var pool = Executors.newSingleThreadExecutor()) {
            long oldEpoch = admit(old);
            execute(old, "INSERT INTO book_title(title,price_cents) VALUES ('before-T',100)");
            long pid = value(closing, "SELECT pg_backend_pid()");
            var completion = pool.submit(() -> { isolate(closing); return true; });
            // Observe the database lock wait; no sleep-based race or timing assertion.
            long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
            while (value(observer, "SELECT cardinality(pg_blocking_pids(" + pid + "))") == 0) {
                assertThat(System.nanoTime()).isLessThan(deadline);
                assertThat(completion.isDone()).isFalse();
            }
            assertThat(completion.isDone()).isFalse();
            try (var late = connect()) {
                assertThatThrownBy(() -> admit(late)).isInstanceOf(IllegalStateException.class).hasMessage("draining");
                late.rollback();
            }
            old.commit();
            assertThat(completion.get(6, TimeUnit.SECONDS)).isTrue();
            assertThat(value(observer, "SELECT count(*) FROM book_title WHERE title='before-T'")).isEqualTo(1);
            assertThat(value(observer, "SELECT version FROM closing_control WHERE scope='GLOBAL'")).isEqualTo(oldEpoch+1);
            try (var fresh = connect()) {
                assertThatThrownBy(() -> admit(fresh)).isInstanceOf(IllegalStateException.class).hasMessage("isolated");
                fresh.rollback();
            }
        }
    }

    @Test
    void exclusiveHolderRejectsOrdinaryRequestWithoutQueueing() throws Exception {
        try (var closing = connect(); var fresh = connect()) {
            closing.setAutoCommit(false);
            execute(closing, "SELECT id FROM closing_control WHERE scope='GLOBAL' FOR UPDATE");
            assertThatThrownBy(() -> admit(fresh)).isInstanceOf(SQLException.class)
                    .satisfies(e -> assertThat(((SQLException)e).getSQLState()).isEqualTo("55P03"));
            fresh.rollback(); closing.rollback();
            assertThat(admit(fresh)).isZero(); // Failed isolation has no committed epoch.
            fresh.rollback();
        }
    }

    @Test
    void failedOrdinaryWriteRollsBackBeforeLogicalBoundary() throws Exception {
        try (var old = connect(); var closing = connect(); var observer = connect()) {
            admit(old);
            execute(old,"INSERT INTO book_title(title,price_cents) VALUES ('rolled-back',100)");
            old.rollback(); isolate(closing);
            assertThat(value(observer,"SELECT count(*) FROM book_title")).isZero();
            assertThat(value(observer,"SELECT version FROM closing_control")).isEqualTo(1);
        }
    }

    @Test
    void reopenRequiresAllDueBatchesAndAdvancesEpoch() throws Exception {
        try (var c = connect(); var fresh = connect()) {
            isolate(c);
            execute(c,"INSERT INTO closing_batch (business_date,status) VALUES (DATE '2026-09-11','SUCCEEDED'),(DATE '2026-09-12','FAILED')");
            c.commit();
            String reopen = "UPDATE closing_control SET status='REOPENED',version=version+1 WHERE scope='GLOBAL' AND status='ISOLATED' AND NOT EXISTS (SELECT 1 FROM closing_batch WHERE business_date<=DATE '2026-09-12' AND status<>'SUCCEEDED')";
            try(var s=c.createStatement()){assertThat(s.executeUpdate(reopen)).isZero();}
            c.commit();
            assertThatThrownBy(() -> admit(fresh)).hasMessage("isolated");fresh.rollback();
            execute(c,"UPDATE closing_batch SET status='SUCCEEDED' WHERE business_date=DATE '2026-09-12'");
            try(var s=c.createStatement()){assertThat(s.executeUpdate(reopen)).isEqualTo(1);}
            execute(c,"UPDATE closing_admission SET draining=false WHERE scope='GLOBAL'");
            c.commit();
            assertThat(admit(fresh)).isEqualTo(2);
            execute(fresh,"INSERT INTO book_title(title,price_cents) VALUES ('after-reopen',100)");fresh.commit();
            assertThat(value(c,"SELECT count(*) FROM book_title")).isEqualTo(1);
        }
    }

    @Test
    void failedIsolationKeepsDrainIntentUntilRetryCompletes() throws Exception {
        try(var c=connect();var fresh=connect()) {
            execute(c,"UPDATE closing_admission SET draining=true WHERE scope='GLOBAL'");
            c.setAutoCommit(false);
            execute(c,"UPDATE closing_control SET status='ISOLATED',version=version+1 WHERE scope='GLOBAL'");
            c.rollback();
            assertThat(value(c,"SELECT version FROM closing_control")).isZero();c.commit();
            assertThatThrownBy(()->admit(fresh)).hasMessage("draining");fresh.rollback();
            c.setAutoCommit(true);isolate(c);
            assertThat(value(c,"SELECT version FROM closing_control")).isEqualTo(1);
        }
    }

    @Test
    void absentGateFailsClosed() throws Exception {
        try(var c=connect()){execute(c,"DELETE FROM closing_admission");execute(c,"DELETE FROM closing_control");assertThatThrownBy(()->admit(c)).hasMessage("missing gate: fail closed");c.rollback();}
    }
}
