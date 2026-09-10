package com.library;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PostgresInfrastructureTest {

    @Container
    // Official postgres:17.6-bookworm; use digest-only syntax for Testcontainers 1.x parsing.
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            "postgres@sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired TestRestTemplate http;

    @Test
    void connectsToRealPostgres() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
        }
        assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
    }

    @Test
    void appliesInfrastructureProbeExactlyOnceAndValidates() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM flyway_schema_history
                WHERE script = 'R__infrastructure_probe.sql' AND success = true AND version IS NULL
                """, Integer.class)).isEqualTo(1);
        var before = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank"))
                .isEqualTo(before);
    }

    @Test
    void rejectsChangedChecksumInIsolatedDatabase() {
        // A separate database keeps the negative fixture out of the application's history.
        jdbc.execute("CREATE DATABASE checksum_probe");
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(5432) + "/checksum_probe";
        Flyway original = Flyway.configure()
                .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/checksum/original").load();
        assertThat(original.migrate().migrationsExecuted).isEqualTo(1);
        original.validate();
        Flyway modified = Flyway.configure()
                .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/checksum/modified").load();
        var validation = modified.validateWithResult();
        assertThat(validation.validationSuccessful).isFalse();
        assertThat(validation.invalidMigrations).singleElement().satisfies(migration -> {
            assertThat(migration.version).isEqualTo("1");
            assertThat(migration.errorDetails.errorCode.toString()).isEqualTo("CHECKSUM_MISMATCH");
        });
        assertThatThrownBy(modified::migrate).isInstanceOf(FlywayValidateException.class)
                .hasMessageContaining("checksum mismatch");
    }

    @Test
    void servesHealthFromRunningApplication() {
        var response = http.getForEntity("/api/health", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("{\"status\":\"UP\"}");
    }
}
