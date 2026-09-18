package com.library;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** IT-AI-008：V7→V8 升级后的所有权、保留数据与重复迁移不变量。 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class V8MigrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            "postgres@sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3");
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    @Test void itAi008_ownerXorGlobalQuotaAndRepeatableMigration() {
        var account = jdbc.queryForObject("INSERT INTO account(username,password_hash,display_name,role) VALUES ('v8','x','v8','READER') RETURNING id", Long.class);
        jdbc.update("INSERT INTO ai_session(account_id,status) VALUES (?, 'ACTIVE')", account);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_session WHERE account_id=? AND visitor_owner_digest IS NULL", Integer.class, account)).isOne();
        jdbc.update("INSERT INTO ai_session(visitor_owner_digest,status) VALUES (?, 'ACTIVE')", "b".repeat(64));
        assertThatThrownBy(() -> jdbc.update("INSERT INTO ai_session(status) VALUES ('ACTIVE')")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO ai_session(account_id,visitor_owner_digest,status) VALUES (?,?, 'ACTIVE')", account, "c".repeat(64))).isInstanceOf(Exception.class);
        jdbc.update("INSERT INTO ai_visitor_quota_usage(usage_date,model_calls) VALUES (current_date,1)");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO ai_visitor_quota_usage(usage_date,model_calls) VALUES (current_date,1)")).isInstanceOf(Exception.class);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test void itAi008_v7AccountSessionSurvivesUpgradeAndV1ChecksumStaysValid() throws Exception {
        try (var admin=java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
             var statement=admin.createStatement()) { statement.execute("CREATE DATABASE ai_v8_upgrade"); }
        var url=POSTGRES.getJdbcUrl().replaceFirst("/test\\?", "/ai_v8_upgrade?");
        var source=new org.springframework.jdbc.datasource.DriverManagerDataSource(url,POSTGRES.getUsername(),POSTGRES.getPassword());
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("7").load().migrate();
        var old=new JdbcTemplate(source);
        var account=old.queryForObject("INSERT INTO account(username,password_hash,display_name,role) VALUES ('old-ai','x','old','READER') RETURNING id",Long.class);
        var session=old.queryForObject("INSERT INTO ai_session(account_id,status) VALUES (?,'ACTIVE') RETURNING id",Long.class,account);
        var upgraded=Flyway.configure().dataSource(source).locations("classpath:db/migration").target("8").load();
        upgraded.migrate();
        assertThat(old.queryForObject("SELECT account_id FROM ai_session WHERE id=? AND visitor_owner_digest IS NULL",Long.class,session)).isEqualTo(account);
        assertThat(upgraded.validateWithResult().validationSuccessful).isTrue();
    }
}
