package com.library.integration;

import com.library.contract.ModuleResult;
import com.library.contract.outbox.OutboxAppender;
import com.library.contract.security.ActorContext;
import com.library.contract.model.AccountId;
import com.library.contract.model.Role;
import com.library.circulation.contract.CopyCommands;
import com.library.circulation.contract.LoanCommands;
import com.library.circulation.contract.ReservationCommands;
import com.library.circulation.contract.ReturnCommands;
import com.library.circulation.contract.model.BusinessDate;
import com.library.circulation.contract.model.CopyId;
import com.library.catalog.contract.model.TitleId;
import com.library.circulation.contract.model.TxId;
import com.library.application.loan.LoanUseCases;
import com.library.application.returning.ReturnUseCases;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT-CIRC-001/003（MLS-DOC-MD-0005 §4）：同副本借出竞争（BR-0039/0041）；
 * 归还＝流通迁移＋罚款形成＋Outbox 同事务故障整体回滚（DD-0004 §1）。
 * 故障注入由测试装配提供（FlakyOutboxAppender），不进生产路由。
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(ITCirculationCoreTest.FlakyOutboxConfig.class)
class ITCirculationCoreTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            "postgres@sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** 故障注入：failNext 置位时下一次事件登记抛基础设施失败（触发 UnitOfWork 回滚）。 */
    static class FlakyOutboxAppender implements OutboxAppender {
        final OutboxAppender delegate;
        final AtomicBoolean failNext = new AtomicBoolean(false);

        FlakyOutboxAppender(OutboxAppender delegate) {
            this.delegate = delegate;
        }

        @Override
        public long append(String aggType, long aggId, String eventType, Object payload) {
            if (failNext.compareAndSet(true, false)) {
                throw new com.library.contract.InfrastructureFailure("注入的 Outbox 故障");
            }
            return delegate.append(aggType, aggId, eventType, payload);
        }
    }

    @TestConfiguration
    static class FlakyOutboxConfig {
        @Bean
        @org.springframework.context.annotation.Primary
        OutboxAppender flakyOutbox(OutboxAppender real) {
            return new FlakyOutboxAppender(real);
        }
    }

    @Autowired LoanUseCases loans;
    @Autowired ReturnUseCases returns;
    @Autowired JdbcTemplate jdbc;
    @Autowired FlakyOutboxAppender flakyOutbox;
    @Autowired com.library.contract.transaction.UnitOfWork unitOfWork;

    private final ActorContext reader = new ActorContext(AccountId.of(1L), Role.READER,
            java.time.Instant.parse("2026-09-16T08:00:00Z"));
    private final ActorContext librarian = new ActorContext(AccountId.of(2L), Role.LIBRARIAN,
            java.time.Instant.parse("2026-09-16T08:00:00Z"));

    private long seedReader(String username) {
        return jdbc.queryForObject("""
                INSERT INTO account (username, password_hash, display_name, role)
                VALUES (?, 'x', ?, 'READER') RETURNING id
                """, Long.class, username, username);
    }

    private long seedAvailableCopy(String isbnPrefix, String copyNo) {
        var titleId = jdbc.queryForObject("""
                INSERT INTO book_title (isbn, title, price_cents) VALUES (?, '竞争书目', 9900)
                RETURNING id
                """, Long.class, isbnPrefix + (System.nanoTime() % 1_000_000_000L));
        return jdbc.queryForObject("""
                INSERT INTO book_copy (title_id, copy_no, status) VALUES (?, ?, 'SHELF_READY')
                RETURNING id
                """, Long.class, titleId, copyNo);
    }

    private long seedLentTx(long readerId, long copyId, int overdueDays) {
        var titleId = jdbc.queryForObject("SELECT title_id FROM book_copy WHERE id = ?", Long.class, copyId);
        var txId = jdbc.queryForObject("""
                INSERT INTO borrow_transaction (reader_id, title_id, copy_id, status,
                                                reservation_date, due_date)
                VALUES (?, ?, ?, 'LENT', current_date, current_date - ?)
                RETURNING id
                """, Long.class, readerId, titleId, copyId, overdueDays);
        jdbc.update("UPDATE book_copy SET status = 'LENT' WHERE id = ?", copyId);
        return txId;
    }

    // IT-CIRC-001 / X1：同副本借出竞争——恰一成功
    @Test
    void itCirc001_concurrentLendOnSameCopyProducesSingleWinner() throws Exception {
        seedReader("itcirc001-reader");
        var copyId = seedAvailableCopy("isbn-a-", "ITCIRC001-C1");
        var titleId = jdbc.queryForObject(
                "SELECT title_id FROM book_copy WHERE id = ?", Long.class, copyId);
        var command = new LoanCommands.WalkInLoanCommand(1L,
                List.of(new LoanCommands.WalkInItem(TitleId.of(titleId), CopyId.of(copyId))),
                "SELF_SERVICE", reader);
        var latch = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var futures = List.of(0, 1).stream().<Future<ModuleResult<LoanCommands.LoanBatch>>>map(
                    ignored -> pool.submit(() -> {
                        latch.await();
                        return loans.lend(reader, new LoanCommands.WalkInLoanCommand(
                                command.readerId(), command.items(), command.channel(), reader));
                    })).toList();
            latch.countDown();
            int lent = 0;
            int rejected = 0;
            for (var future : futures) {
                var batch = future.get();
                if (batch instanceof ModuleResult.Accepted<LoanCommands.LoanBatch> accepted) {
                    for (var item : accepted.value().items()) {
                        if ("LENT".equals(item.outcome())) {
                            lent++;
                        } else {
                            rejected++;
                        }
                    }
                }
            }
            assertThat(lent).isEqualTo(1);
            assertThat(rejected).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM book_copy WHERE id = ?", String.class, copyId)).isEqualTo("LENT");
        } finally {
            pool.shutdownNow();
        }
    }

    // IT-CIRC-003：归还（流通+罚款+Outbox）同事务——无故障整体提交、注入故障整体回滚
    @Test
    void itCirc003_returnIsAtomicAcrossCirculationFineAndOutbox() {
        seedReader("itcirc003-reader");
        var copyId = seedAvailableCopy("isbn-b-", "ITCIRC003-C1");
        var txId = seedLentTx(1L, copyId, 5); // 逾期 5 天 → 罚款 500（封顶 9900）

        // 故障注入：归还事件登记失败 → InfrastructureFailure 抛出（HTTP 层映射 500）→ 全体回滚
        flakyOutbox.failNext.set(true);
        try {
            unitOfWork.inTransaction(() -> returns.returnCopy(librarian, new ReturnCommands.ReturnCommand(
                    CopyId.of(copyId), "INTACT", librarian)));
            throw new AssertionError("注入故障应导致异常");
        } catch (com.library.contract.InfrastructureFailure expected) {
            // 预期：UnitOfWork 回滚
        }
        assertThat(jdbc.queryForObject(
                "SELECT status FROM book_copy WHERE id = ?", String.class, copyId)).isEqualTo("LENT");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM borrow_transaction WHERE id = ?", String.class, txId)).isEqualTo("LENT");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM fine WHERE source_tx_id = ?", Integer.class, txId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE agg_id = ? AND event_type = 'COPY_RETURNED'",
                Integer.class, txId)).isZero();

        // 无故障：整体提交——副本回架、罚款形成、Outbox 登记
        var ok = unitOfWork.inTransaction(() -> returns.returnCopy(librarian,
                new ReturnCommands.ReturnCommand(CopyId.of(copyId), "INTACT", librarian)));
        if (ok instanceof ModuleResult.Accepted<ReturnUseCases.ReturnResult> accepted) {
            assertThat(accepted.value().returned().outcome()).isEqualTo("RETURNED");
            assertThat(accepted.value().fineId()).isNotNull(); // 逾期 5 天 × 100 分
        } else {
            throw new AssertionError("归还应成功: " + ok);
        }
        assertThat(jdbc.queryForObject(
                "SELECT status FROM book_copy WHERE id = ?", String.class, copyId)).isEqualTo("SHELF_READY");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM borrow_transaction WHERE id = ?", String.class, txId)).isEqualTo("RETURNED");
        assertThat(jdbc.queryForObject(
                "SELECT amount_cents FROM fine WHERE source_tx_id = ?", Long.class, txId)).isEqualTo(500L);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE agg_id = ? AND event_type = 'COPY_RETURNED'",
                Integer.class, txId)).isEqualTo(1);
        // 重复归还（同一请求重放语义之外的新请求）→ MLS-CIRC-006
        var duplicate = returns.returnCopy(librarian, new ReturnCommands.ReturnCommand(
                CopyId.of(copyId), "INTACT", librarian));
        assertThat(duplicate).isInstanceOf(ModuleResult.Rejected.class);
        assertThat(((ModuleResult.Rejected<?>) duplicate).code()).isEqualTo("MLS-CIRC-006");
    }
}
