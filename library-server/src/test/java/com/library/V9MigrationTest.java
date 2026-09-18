package com.library;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** V9 owns the fresh-database closing baseline without overwriting an active gate. */
@Testcontainers
class V9MigrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>(
            "postgres@sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3");

    @Test void v9RestoresMissingClosingBaselineAndIsRepeatable() throws Exception {
        var database=createDatabase();var source=source(database);
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("8").load().migrate();
        var jdbc=new JdbcTemplate(source);
        jdbc.update("DELETE FROM closing_admission");jdbc.update("DELETE FROM closing_control");
        var latest=Flyway.configure().dataSource(source).locations("classpath:db/migration").load();
        assertThat(latest.migrate().migrationsExecuted).isOne();
        assertThat(jdbc.queryForMap("SELECT status,version FROM closing_control WHERE scope='GLOBAL'"))
                .containsEntry("status","OPEN").containsEntry("version",0L);
        assertThat(jdbc.queryForObject("SELECT draining FROM closing_admission WHERE scope='GLOBAL'",Boolean.class)).isFalse();
        assertThat(latest.migrate().migrationsExecuted).isZero();
        assertThat(latest.validateWithResult().validationSuccessful).isTrue();
    }

    @Test void v9PreservesExistingIsolatedGateAndAdmissionIntent() throws Exception {
        var database=createDatabase();var source=source(database);
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("8").load().migrate();
        var jdbc=new JdbcTemplate(source);
        jdbc.update("UPDATE closing_control SET status='ISOLATED',version=7 WHERE scope='GLOBAL'");
        jdbc.update("UPDATE closing_admission SET draining=true WHERE scope='GLOBAL'");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        assertThat(jdbc.queryForMap("SELECT status,version FROM closing_control WHERE scope='GLOBAL'"))
                .containsEntry("status","ISOLATED").containsEntry("version",7L);
        assertThat(jdbc.queryForObject("SELECT draining FROM closing_admission WHERE scope='GLOBAL'",Boolean.class)).isTrue();
    }

    private String createDatabase() throws Exception {
        var name="v9_"+UUID.randomUUID().toString().replace("-","");
        try(var connection=DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
            var statement=connection.createStatement()){statement.execute("CREATE DATABASE "+name);}
        return name;
    }
    private DriverManagerDataSource source(String database){
        return new DriverManagerDataSource(POSTGRES.getJdbcUrl().replaceFirst("/test\\?", "/"+database+"?"),POSTGRES.getUsername(),POSTGRES.getPassword());
    }
}
