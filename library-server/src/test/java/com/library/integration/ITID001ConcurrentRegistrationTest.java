package com.library.integration;

import com.library.contract.ModuleResult;
import com.library.identity.contract.AccountRegistration;
import com.library.identity.contract.IdentityRegistration;
import com.library.identity.contract.RegisterReader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT-ID-001（MLS-DOC-MD-0005 §4）：并发自助注册一成一冲突；一账户一角色（BR-0031）。
 * 真实 PostgreSQL 唯一索引 + ON CONFLICT 冲突路径（不中止事务）。
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ITID001ConcurrentRegistrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            "postgres@sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired IdentityRegistration identity;
    @Autowired JdbcTemplate jdbc;

    @Test
    void concurrentSelfRegistrationsProduceExactlyOneAccount() throws Exception {
        var username = "race-" + System.nanoTime();
        int threads = 16;
        var latch = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(threads);
        try {
            var futures = List.copyOf(java.util.Collections.nCopies(threads, 0)).stream()
                    .<Future<ModuleResult<AccountRegistration>>>map(ignored -> pool.submit(() -> {
                        latch.await();
                        return identity.register(new RegisterReader(username, "password123", "并发注册"));
                    }))
                    .toList();
            latch.countDown();
            int accepted = 0;
            int duplicateRejected = 0;
            for (var future : futures) {
                var result = future.get();
                if (result instanceof ModuleResult.Accepted<AccountRegistration>) {
                    accepted++;
                } else if (result instanceof ModuleResult.Rejected<AccountRegistration> rejected
                        && "MLS-ID-002".equals(rejected.code())) {
                    duplicateRejected++;
                }
            }
            assertThat(accepted).isEqualTo(1);
            assertThat(duplicateRejected).isEqualTo(threads - 1);
            // 一账户一角色（BR-0031）：唯一行且为 READER/ACTIVE
            var rows = jdbc.queryForList(
                    "SELECT role, status FROM account WHERE username = ?", username);
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0)).containsEntry("role", "READER").containsEntry("status", "ACTIVE");
        } finally {
            pool.shutdownNow();
        }
    }
}
