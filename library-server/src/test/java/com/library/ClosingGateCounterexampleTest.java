package com.library;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.assertThat;

/** Characterization evidence: an initial OPEN read cannot enforce a commit deadline. */
@Testcontainers
class ClosingGateCounterexampleTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            "postgres@sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3");

    @Test
    void initialGateReadAllowsCommitAfterIsolationEvenAtSerializable() throws Exception {
        for (int isolation : new int[]{java.sql.Connection.TRANSACTION_READ_COMMITTED,
                java.sql.Connection.TRANSACTION_SERIALIZABLE}) {
            try (var ordinary = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                 var closing = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
                try (var s = closing.createStatement()) {
                    s.execute("DROP TABLE IF EXISTS gate_probe, write_probe");
                    s.execute("CREATE TABLE gate_probe (id int PRIMARY KEY, status text)");
                    s.execute("INSERT INTO gate_probe VALUES (1, 'OPEN')");
                    s.execute("CREATE TABLE write_probe (id int PRIMARY KEY)");
                }
                ordinary.setTransactionIsolation(isolation);
                closing.setTransactionIsolation(isolation);
                ordinary.setAutoCommit(false);
                closing.setAutoCommit(false);
                try (var s = ordinary.createStatement(); var rows = s.executeQuery("SELECT status FROM gate_probe WHERE id=1")) {
                    rows.next(); assertThat(rows.getString(1)).isEqualTo("OPEN");
                }
                // Boundary T: isolation becomes a committed fact before the old transaction writes.
                try (var s = closing.createStatement()) { s.executeUpdate("UPDATE gate_probe SET status='ISOLATED' WHERE id=1"); }
                closing.commit();
                try (var s = ordinary.createStatement()) { s.executeUpdate("INSERT INTO write_probe VALUES (1)"); }
                ordinary.commit();
                // This PASS proves the old design's counterexample, not business correctness.
                try (var s = closing.createStatement(); var rows = s.executeQuery("SELECT count(*) FROM write_probe")) {
                    rows.next(); assertThat(rows.getInt(1)).isEqualTo(1);
                }
                closing.commit();
            }
        }
    }
}
