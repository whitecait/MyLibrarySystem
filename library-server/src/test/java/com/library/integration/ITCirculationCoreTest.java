package com.library.integration;

import com.library.contract.ModuleResult;
import com.library.contract.outbox.OutboxAppender;
import com.library.contract.security.ActorContext;
import com.library.contract.model.AccountId;
import com.library.contract.model.Role;
import com.library.circulation.contract.ClosingCommands;
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
import com.library.contract.time.BusinessClock;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
        MutableBusinessClock mutableBusinessClock() {
            return new MutableBusinessClock();
        }

        @Bean
        @org.springframework.context.annotation.Primary
        OutboxAppender flakyOutbox(OutboxAppender real) {
            return new FlakyOutboxAppender(real);
        }
    }

    static final class MutableBusinessClock implements BusinessClock {
        private final AtomicReference<Instant> now = new AtomicReference<>(
                Instant.parse("2026-09-27T04:00:00Z"));

        @Override
        public Instant now() {
            return now.get();
        }

        void setDate(LocalDate date) {
            now.set(date.atTime(12, 0).atZone(BusinessClock.ZONE).toInstant());
        }
    }

    @Autowired LoanUseCases loans;
    @Autowired ReturnUseCases returns;
    @Autowired CopyCommands copies;
    @Autowired ClosingCommands closing;
    @Autowired JdbcTemplate jdbc;
    @Autowired FlakyOutboxAppender flakyOutbox;
    @Autowired com.library.contract.transaction.UnitOfWork unitOfWork;
    @Autowired MutableBusinessClock businessClock;

    @BeforeEach
    void resetBusinessClock() {
        var databaseBusinessDate = jdbc.queryForObject(
                "SELECT (now() AT TIME ZONE 'Asia/Shanghai')::date", LocalDate.class);
        businessClock.setDate(databaseBusinessDate);
    }

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

    private long seedCopy(long titleId, String copyNo, String status) {
        return jdbc.queryForObject("""
                INSERT INTO book_copy (title_id, copy_no, status) VALUES (?, ?, ?)
                RETURNING id
                """, Long.class, titleId, copyNo, status);
    }

    private long seedLentTx(long readerId, long copyId, int overdueDays) {
        var titleId = jdbc.queryForObject("SELECT title_id FROM book_copy WHERE id = ?", Long.class, copyId);
        var txId = jdbc.queryForObject("""
                INSERT INTO borrow_transaction (reader_id, title_id, copy_id, status,
                                                reservation_date, due_date)
        VALUES (?, ?, ?, 'LENT', ?, ?)
                RETURNING id
                """, Long.class, readerId, titleId, copyId,
                java.sql.Date.valueOf(businessClock.today()),
                java.sql.Date.valueOf(businessClock.today().minusDays(overdueDays)));
        jdbc.update("UPDATE book_copy SET status = 'LENT' WHERE id = ?", copyId);
        return txId;
    }

    // IT-CIRC-001 / X1：同副本借出竞争——恰一成功
    @Test
    void itCirc001_concurrentLendOnSameCopyProducesSingleWinner() throws Exception {
        var readerIdA = seedReader("itcirc001-reader-a");
        var readerIdB = seedReader("itcirc001-reader-b");
        var librarianIdA = jdbc.queryForObject("""
                INSERT INTO account(username, password_hash, display_name, role, status)
                VALUES ('itcirc001-librarian-a', 'x', 'itcirc001-librarian-a', 'LIBRARIAN', 'ACTIVE')
                RETURNING id
                """, Long.class);
        var librarianIdB = jdbc.queryForObject("""
                INSERT INTO account(username, password_hash, display_name, role, status)
                VALUES ('itcirc001-librarian-b', 'x', 'itcirc001-librarian-b', 'LIBRARIAN', 'ACTIVE')
                RETURNING id
                """, Long.class);
        var librarianActorA = new ActorContext(AccountId.of(librarianIdA), Role.LIBRARIAN,
                java.time.Instant.parse("2026-09-27T12:00:00Z"));
        var librarianActorB = new ActorContext(AccountId.of(librarianIdB), Role.LIBRARIAN,
                java.time.Instant.parse("2026-09-27T12:00:00Z"));
        var copyId = seedAvailableCopy("isbn-a-", "ITCIRC001-C1");
        var titleId = jdbc.queryForObject(
                "SELECT title_id FROM book_copy WHERE id = ?", Long.class, copyId);
        var commands = List.of(readerIdA, readerIdB).stream().map(readerId ->
                new LoanCommands.WalkInLoanCommand(readerId,
                        List.of(new LoanCommands.WalkInItem(TitleId.of(titleId), CopyId.of(copyId))),
                        "DESK", readerId == readerIdA ? librarianActorA : librarianActorB)).toList();
        var latch = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var futures = commands.stream().<Future<ModuleResult<LoanCommands.LoanBatch>>>map(
                    command -> pool.submit(() -> {
                        latch.await();
                        return loans.lend(command.actor(), command);
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
        var readerId = seedReader("itcirc003-reader");
        var copyId = seedAvailableCopy("isbn-b-", "ITCIRC003-C1");
        var txId = seedLentTx(readerId, copyId, 5); // 逾期 5 天 → 书价的 10%×5 = 4950 分

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

        // 无故障：整体提交——副本待闭馆清点、罚款形成、Outbox 登记
        var ok = unitOfWork.inTransaction(() -> returns.returnCopy(librarian,
                new ReturnCommands.ReturnCommand(CopyId.of(copyId), "INTACT", librarian)));
        if (ok instanceof ModuleResult.Accepted<ReturnUseCases.ReturnResult> accepted) {
            assertThat(accepted.value().returned().outcome()).isEqualTo("RETURNED");
            assertThat(accepted.value().fineId()).isNotNull(); // 逾期 5 天 × 100 分
        } else {
            throw new AssertionError("归还应成功: " + ok);
        }
        assertThat(jdbc.queryForObject(
                "SELECT status FROM book_copy WHERE id = ?", String.class, copyId)).isEqualTo("PENDING_SHELVING");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM borrow_transaction WHERE id = ?", String.class, txId)).isEqualTo("RETURNED");
        assertThat(jdbc.queryForObject(
                "SELECT amount_cents FROM fine WHERE source_tx_id = ?", Long.class, txId)).isEqualTo(4950L);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE agg_id = ? AND event_type = 'COPY_RETURNED'",
                Integer.class, txId)).isEqualTo(1);
        // 重复归还（同一请求重放语义之外的新请求）→ MLS-CIRC-006
        var duplicate = returns.returnCopy(librarian, new ReturnCommands.ReturnCommand(
                CopyId.of(copyId), "INTACT", librarian));
        assertThat(duplicate).isInstanceOf(ModuleResult.Rejected.class);
        assertThat(((ModuleResult.Rejected<?>) duplicate).code()).isEqualTo("MLS-CIRC-006");
    }

    // P3-13 / MLS-BR-0023：正常归还当天隔离，闭馆清点后下一营业日可借。
    @Test
    void itCirc013_sameDayReturnIsNotReservedAndBecomesBookableAfterClosing() {
        var today = businessClock.today();
        var readerId = seedReader("itcirc013-reader");
        var reader = new ActorContext(AccountId.of(readerId), Role.READER, businessClock.now());
        var copyId = seedAvailableCopy("isbn-p3-13-", "ITCIRC013-C1");
        seedLentTx(readerId, copyId, 0);

        var returned = unitOfWork.inTransaction(() -> returns.returnCopy(librarian,
                new ReturnCommands.ReturnCommand(CopyId.of(copyId), "INTACT", librarian)));
        assertThat(returned).isInstanceOf(ModuleResult.Accepted.class);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM book_copy WHERE id = ?", String.class, copyId))
                .as("an intact return stays pending inspection for the rest of its return date")
                .isEqualTo("PENDING_SHELVING");
        assertThat(jdbc.queryForObject(
                "SELECT loan_eligible_on FROM book_copy WHERE id = ?", LocalDate.class, copyId))
                .isEqualTo(today.plusDays(1));

        var priorDay = today.minusDays(1);
        assertThat(closing.closeBusinessDate(
                new ClosingCommands.ClosingCommand(BusinessDate.of(priorDay))))
                .isInstanceOf(ModuleResult.Accepted.class);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM book_copy WHERE id = ?", String.class, copyId))
                .as("catching up a prior business date must not clear a copy returned today")
                .isEqualTo("PENDING_SHELVING");
        assertThat(closing.reopen(new ClosingCommands.ReopenCommand(BusinessDate.of(priorDay))))
                .isInstanceOf(ModuleResult.Accepted.class);

        var titleId = jdbc.queryForObject(
                "SELECT title_id FROM book_copy WHERE id = ?", Long.class, copyId);
        var reservationResult = loans.reserve(reader, new ReservationCommands.ReservationCommand(
                readerId, TitleId.of(titleId), BusinessDate.of(today), "AM", "DESK", false, reader));
        if (!(reservationResult instanceof ModuleResult.Accepted<ReservationCommands.Reservation> accepted)) {
            throw new AssertionError("same-day desk request should wait while the copy is pending inspection: "
                    + reservationResult);
        }
        var reservation = accepted.value();
        assertThat(reservation.outcome()).isEqualTo("WAITLIST_ENQUEUED");
        assertThat(reservation.txId()).isNotNull();

        var sameDayClaim = loans.claim(librarian, new LoanCommands.ClaimPickup(
                reservation.txId(), CopyId.of(copyId), librarian));
        assertThat(sameDayClaim).isInstanceOf(ModuleResult.Rejected.class);
        assertThat(((ModuleResult.Rejected<?>) sameDayClaim).code()).isEqualTo("MLS-CIRC-004");
        var cancelled = loans.cancel(reader, new ReservationCommands.CancelReservation(
                reservation.txId(), "P3-13 test isolation", reader));
        assertThat(cancelled).isInstanceOf(ModuleResult.Accepted.class);

        var sameDayLoan = loans.lend(librarian, new LoanCommands.WalkInLoanCommand(readerId,
                List.of(new LoanCommands.WalkInItem(TitleId.of(titleId), CopyId.of(copyId))), "DESK", librarian));
        assertThat(((ModuleResult.Accepted<LoanCommands.LoanBatch>) sameDayLoan).value().items().getFirst().outcome())
                .isEqualTo("REJECTED");
        assertThat(((ModuleResult.Accepted<LoanCommands.LoanBatch>) sameDayLoan).value().items().getFirst().reasons())
                .contains("COPY_NOT_AVAILABLE");

        var closed = closing.closeBusinessDate(new ClosingCommands.ClosingCommand(BusinessDate.of(today)));
        assertThat(closed).isInstanceOf(ModuleResult.Accepted.class);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM book_copy WHERE id = ?", String.class, copyId)).isEqualTo("SHELF_READY");
        assertThat(closing.reopen(new ClosingCommands.ReopenCommand(BusinessDate.of(today))))
                .isInstanceOf(ModuleResult.Accepted.class);

        businessClock.setDate(today.plusDays(1));
        var nextDayLoan = loans.lend(librarian, new LoanCommands.WalkInLoanCommand(readerId,
                List.of(new LoanCommands.WalkInItem(TitleId.of(titleId), CopyId.of(copyId))), "DESK", librarian));
        assertThat(((ModuleResult.Accepted<LoanCommands.LoanBatch>) nextDayLoan).value().items().getFirst().outcome())
                .isEqualTo("LENT");
    }

    @Test
    void itCirc013_backfillClearsItsNoShowButKeepsTodaysReturnPending() {
        var today = businessClock.today();
        var priorDay = today.minusDays(1);
        var returnReaderId = seedReader("itcirc013-backfill-return-reader");
        var returnedCopyId = seedAvailableCopy("isbn-r-", "ITCIRC013-B1");
        seedLentTx(returnReaderId, returnedCopyId, 0);
        var returned = unitOfWork.inTransaction(() -> returns.returnCopy(librarian,
                new ReturnCommands.ReturnCommand(CopyId.of(returnedCopyId), "INTACT", librarian)));
        assertThat(returned).isInstanceOf(ModuleResult.Accepted.class);

        var noShowReaderId = seedReader("itcirc013-backfill-noshow-reader");
        var noShowCopyId = seedAvailableCopy("isbn-n-", "ITCIRC013-B2");
        var noShowTitleId = jdbc.queryForObject(
                "SELECT title_id FROM book_copy WHERE id = ?", Long.class, noShowCopyId);
        jdbc.update("UPDATE book_copy SET status = 'RESERVED' WHERE id = ?", noShowCopyId);
        var noShowTxId = jdbc.queryForObject("""
                INSERT INTO borrow_transaction (reader_id, title_id, copy_id, status, reservation_date)
                VALUES (?, ?, ?, 'CONFIRMED', ?)
                RETURNING id
                """, Long.class, noShowReaderId, noShowTitleId, noShowCopyId,
                java.sql.Date.valueOf(priorDay));

        var closed = closing.closeBusinessDate(new ClosingCommands.ClosingCommand(BusinessDate.of(priorDay)));
        assertThat(closed).isInstanceOf(ModuleResult.Accepted.class);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM book_copy WHERE id = ?", String.class, returnedCopyId))
                .as("closing yesterday leaves today's return isolated")
                .isEqualTo("PENDING_SHELVING");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM book_copy WHERE id = ?", String.class, noShowCopyId))
                .as("a no-show released by the backfilled close is cleared in that same close")
                .isEqualTo("SHELF_READY");
        assertThat(jdbc.queryForObject(
                "SELECT loan_eligible_on FROM book_copy WHERE id = ?", LocalDate.class, noShowCopyId))
                .isEqualTo(priorDay.plusDays(1));
        assertThat(jdbc.queryForObject(
                "SELECT status FROM borrow_transaction WHERE id = ?", String.class, noShowTxId))
                .isEqualTo("NOT_BORROWED");
        assertThat(closing.reopen(new ClosingCommands.ReopenCommand(BusinessDate.of(priorDay))))
                .isInstanceOf(ModuleResult.Accepted.class);
    }

    @Test
    void itCirc013_sameDayShelvingCanAllocateButEveryLendPathWaitsUntilTomorrow() {
        var today = businessClock.today();
        var titleId = jdbc.queryForObject("""
                INSERT INTO book_title (title, price_cents) VALUES ('ITCIRC013 same-day shelving', 9900)
                RETURNING id
                """, Long.class);
        var reservedCopy = createPendingCopy(titleId, "ITCIRC013-S1");
        var manualCopy = createPendingCopy(titleId, "ITCIRC013-S2");
        var walkInCopy = createPendingCopy(titleId, "ITCIRC013-S3");
        var restoredCopyId = seedCopy(titleId, "ITCIRC013-S4", "MAINTENANCE");
        var restoredVersion = jdbc.queryForObject(
                "SELECT version FROM book_copy WHERE id = ?", Long.class, restoredCopyId);
        assertThat(copies.maintainCopy(new CopyCommands.MaintainCopy(
                CopyId.of(restoredCopyId), "RESTORE", restoredVersion, librarian)))
                .isInstanceOf(ModuleResult.Accepted.class);
        var reservedCopyId = reservedCopy.copyId().value();
        var manualCopyId = manualCopy.copyId().value();
        var walkInCopyId = walkInCopy.copyId().value();
        for (var copy : List.of(reservedCopy, manualCopy, walkInCopy)) {
            assertThat(jdbc.queryForObject("SELECT loan_eligible_on FROM book_copy WHERE id = ?",
                    LocalDate.class, copy.copyId().value())).isEqualTo(today.plusDays(1));
            var shelved = copies.maintainCopy(new CopyCommands.MaintainCopy(
                    copy.copyId(), "SHELVE", copy.version(), librarian));
            assertThat(shelved).isInstanceOf(ModuleResult.Accepted.class);
        }

        var walkInReaderId = seedReader("itcirc013-shelve-walkin");
        var walkIn = loans.lend(librarian, new LoanCommands.WalkInLoanCommand(walkInReaderId,
                List.of(new LoanCommands.WalkInItem(TitleId.of(titleId), CopyId.of(walkInCopyId))),
                "DESK", librarian));
        var walkInItem = ((ModuleResult.Accepted<LoanCommands.LoanBatch>) walkIn).value().items().getFirst();
        assertThat(walkInItem.outcome()).isEqualTo("REJECTED");
        assertThat(walkInItem.reasons()).contains("COPY_NOT_AVAILABLE");

        var reservationReaderId = seedReader("itcirc013-shelve-reserve");
        var reservationActor = new ActorContext(AccountId.of(reservationReaderId), Role.READER, businessClock.now());
        var reservationResult = loans.reserve(reservationActor, new ReservationCommands.ReservationCommand(
                reservationReaderId, TitleId.of(titleId), BusinessDate.of(today), "AM", "DESK", false,
                reservationActor));
        var reservation = ((ModuleResult.Accepted<ReservationCommands.Reservation>) reservationResult).value();
        assertThat(reservation.outcome()).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject(
                "SELECT copy_id FROM borrow_transaction WHERE id = ?", Long.class, reservation.txId().value()))
                .isEqualTo(reservedCopyId);

        var sameDayClaim = loans.claim(librarian, new LoanCommands.ClaimPickup(
                reservation.txId(), CopyId.of(reservedCopyId), librarian));
        assertThat(sameDayClaim).isInstanceOf(ModuleResult.Rejected.class);
        assertThat(((ModuleResult.Rejected<?>) sameDayClaim).reasons())
                .anySatisfy(reason -> assertThat(reason).contains("尚未到可借业务日"));
        assertThat(jdbc.queryForObject(
                "SELECT status FROM borrow_transaction WHERE id = ?", String.class, reservation.txId().value()))
                .isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM book_copy WHERE id = ?", String.class, reservedCopyId))
                .isEqualTo("RESERVED");

        var titleOnlyWalkIn = loans.lend(librarian, new LoanCommands.WalkInLoanCommand(walkInReaderId,
                List.of(new LoanCommands.WalkInItem(TitleId.of(titleId), null)), "DESK", librarian));
        var titleOnlyItem = ((ModuleResult.Accepted<LoanCommands.LoanBatch>) titleOnlyWalkIn)
                .value().items().getFirst();
        assertThat(titleOnlyItem.outcome())
                .as("automatic selection must skip same-day copies and keep looking for a loanable copy")
                .isEqualTo("LENT");
        assertThat(jdbc.queryForObject("SELECT copy_id FROM borrow_transaction WHERE id = ?",
                Long.class, titleOnlyItem.txId().value())).isEqualTo(restoredCopyId);

        var manualReaderId = seedReader("itcirc013-shelve-manual");
        var manualTxId = jdbc.queryForObject("""
                INSERT INTO borrow_transaction (reader_id, title_id, status, reservation_date)
                VALUES (?, ?, 'MANUAL_HANDLING', ?)
                RETURNING id
                """, Long.class, manualReaderId, titleId, java.sql.Date.valueOf(today));
        var sameDayManualRelease = loans.applyManualAction(librarian, new LoanCommands.ManualAction(
                TxId.of(manualTxId), "RELEASE", "P3-13 same-day guard", librarian));
        assertThat(sameDayManualRelease).isInstanceOf(ModuleResult.Rejected.class);
        assertThat(((ModuleResult.Rejected<?>) sameDayManualRelease).code()).isEqualTo("MLS-CIRC-005");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM borrow_transaction WHERE id = ?", String.class, manualTxId))
                .isEqualTo("MANUAL_HANDLING");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM book_copy WHERE id = ?", String.class, manualCopyId))
                .as("a failed manual release must put its newly bound copy back in the shelf")
                .isEqualTo("SHELF_READY");

        businessClock.setDate(today.plusDays(1));
        var nextDayWalkInReaderId = seedReader("itcirc013-shelve-next-walkin");
        var nextDayWalkIn = loans.lend(librarian, new LoanCommands.WalkInLoanCommand(nextDayWalkInReaderId,
                List.of(new LoanCommands.WalkInItem(TitleId.of(titleId), CopyId.of(walkInCopyId))),
                "DESK", librarian));
        assertThat(((ModuleResult.Accepted<LoanCommands.LoanBatch>) nextDayWalkIn).value()
                .items().getFirst().outcome()).isEqualTo("LENT");

        var nextDayClaim = loans.claim(librarian, new LoanCommands.ClaimPickup(
                reservation.txId(), CopyId.of(reservedCopyId), librarian));
        assertThat(nextDayClaim).isInstanceOf(ModuleResult.Accepted.class);
        assertThat(((ModuleResult.Accepted<com.library.circulation.contract.Transaction>) nextDayClaim)
                .value().status()).isEqualTo("MANUAL_HANDLING");
        var nextDayClaimRelease = loans.applyManualAction(librarian, new LoanCommands.ManualAction(
                reservation.txId(), "RELEASE", "P3-13 next-day claim", librarian));
        assertThat(nextDayClaimRelease).isInstanceOf(ModuleResult.Accepted.class);

        var nextDayManualRelease = loans.applyManualAction(librarian, new LoanCommands.ManualAction(
                TxId.of(manualTxId), "RELEASE", "P3-13 next-day manual release", librarian));
        assertThat(nextDayManualRelease).isInstanceOf(ModuleResult.Accepted.class);
    }

    @Test
    void itCirc013_restoreRemainsAvailableForSameDayLend() {
        var readerId = seedReader("itcirc013-restore-reader");
        var titleId = jdbc.queryForObject("""
                INSERT INTO book_title (title, price_cents) VALUES ('ITCIRC013 restore exception', 9900)
                RETURNING id
                """, Long.class);
        var copyId = seedCopy(titleId, "ITCIRC013-R1", "MAINTENANCE");
        jdbc.update("UPDATE book_copy SET loan_eligible_on = ? WHERE id = ?",
                java.sql.Date.valueOf(businessClock.today().plusDays(5)), copyId);
        var version = jdbc.queryForObject("SELECT version FROM book_copy WHERE id = ?", Long.class, copyId);
        var restored = copies.maintainCopy(new CopyCommands.MaintainCopy(
                CopyId.of(copyId), "RESTORE", version, librarian));
        assertThat(restored).isInstanceOf(ModuleResult.Accepted.class);
        assertThat(jdbc.queryForObject("SELECT loan_eligible_on FROM book_copy WHERE id = ?",
                LocalDate.class, copyId)).isEqualTo(businessClock.today());

        var sameDayLoan = loans.lend(librarian, new LoanCommands.WalkInLoanCommand(readerId,
                List.of(new LoanCommands.WalkInItem(TitleId.of(titleId), CopyId.of(copyId))), "DESK", librarian));
        assertThat(((ModuleResult.Accepted<LoanCommands.LoanBatch>) sameDayLoan).value()
                .items().getFirst().outcome()).isEqualTo("LENT");
    }

    @Test
    void itCirc013_manualReleaseCannotBorrowAlreadyBoundCopyOnShelvingDate() {
        var today = businessClock.today();
        var titleId = jdbc.queryForObject("""
                INSERT INTO book_title (title, price_cents) VALUES ('ITCIRC013 bound manual release', 9900)
                RETURNING id
                """, Long.class);
        var copy = createPendingCopy(titleId, "ITCIRC013-M1");
        assertThat(copies.maintainCopy(new CopyCommands.MaintainCopy(
                copy.copyId(), "SHELVE", copy.version(), librarian)))
                .isInstanceOf(ModuleResult.Accepted.class);
        var readerId = seedReader("itcirc013-bound-manual-reader");
        var actor = new ActorContext(AccountId.of(readerId), Role.READER, businessClock.now());
        var reservationResult = loans.reserve(actor, new ReservationCommands.ReservationCommand(
                readerId, TitleId.of(titleId), BusinessDate.of(today.plusDays(1)), "AM", "ONLINE", false, actor));
        var reservation = ((ModuleResult.Accepted<ReservationCommands.Reservation>) reservationResult).value();
        assertThat(reservation.outcome()).isEqualTo("CONFIRMED");

        var routedToManual = loans.claim(librarian, new LoanCommands.ClaimPickup(
                reservation.txId(), copy.copyId(), librarian));
        assertThat(routedToManual).isInstanceOf(ModuleResult.Accepted.class);
        assertThat(((ModuleResult.Accepted<com.library.circulation.contract.Transaction>) routedToManual)
                .value().status()).isEqualTo("MANUAL_HANDLING");
        var sameDayRelease = loans.applyManualAction(librarian, new LoanCommands.ManualAction(
                reservation.txId(), "RELEASE", "P3-13 bound same-day guard", librarian));
        assertThat(sameDayRelease).isInstanceOf(ModuleResult.Rejected.class);
        assertThat(((ModuleResult.Rejected<?>) sameDayRelease).reasons())
                .anySatisfy(reason -> assertThat(reason).contains("尚未到可借业务日"));
        assertThat(jdbc.queryForObject("SELECT status FROM book_copy WHERE id = ?",
                String.class, copy.copyId().value())).isEqualTo("RESERVED");
        assertThat(jdbc.queryForObject("SELECT status FROM borrow_transaction WHERE id = ?",
                String.class, reservation.txId().value())).isEqualTo("MANUAL_HANDLING");

        businessClock.setDate(today.plusDays(1));
        var nextDayRelease = loans.applyManualAction(librarian, new LoanCommands.ManualAction(
                reservation.txId(), "RELEASE", "P3-13 bound next-day release", librarian));
        assertThat(nextDayRelease).isInstanceOf(ModuleResult.Accepted.class);
    }

    @Test
    void itCirc013_cancelledReservationKeepsNextDayEligibilityThroughShelving() {
        var today = businessClock.today();
        var titleId = jdbc.queryForObject("""
                INSERT INTO book_title (title, price_cents) VALUES ('ITCIRC013 cancelled release', 9900)
                RETURNING id
                """, Long.class);
        var copy = createPendingCopy(titleId, "ITCIRC013-CANCEL1");
        assertThat(copies.maintainCopy(new CopyCommands.MaintainCopy(
                copy.copyId(), "SHELVE", copy.version(), librarian)))
                .isInstanceOf(ModuleResult.Accepted.class);
        var readerId = seedReader("itcirc013-cancel-reader");
        var actor = new ActorContext(AccountId.of(readerId), Role.READER, businessClock.now());
        var reservationResult = loans.reserve(actor, new ReservationCommands.ReservationCommand(
                readerId, TitleId.of(titleId), BusinessDate.of(today), "AM", "DESK", false, actor));
        var reservation = ((ModuleResult.Accepted<ReservationCommands.Reservation>) reservationResult).value();
        assertThat(reservation.outcome()).isEqualTo("CONFIRMED");
        assertThat(loans.cancel(actor, new ReservationCommands.CancelReservation(
                reservation.txId(), "same-day release", actor))).isInstanceOf(ModuleResult.Accepted.class);
        assertThat(jdbc.queryForObject("SELECT loan_eligible_on FROM book_copy WHERE id = ?",
                LocalDate.class, copy.copyId().value())).isEqualTo(today.plusDays(1));
        assertThat(jdbc.queryForObject("SELECT status FROM book_copy WHERE id = ?",
                String.class, copy.copyId().value())).isEqualTo("PENDING_SHELVING");

        var pendingVersion = jdbc.queryForObject("SELECT version FROM book_copy WHERE id = ?",
                Long.class, copy.copyId().value());
        assertThat(copies.maintainCopy(new CopyCommands.MaintainCopy(
                copy.copyId(), "SHELVE", pendingVersion, librarian)))
                .isInstanceOf(ModuleResult.Accepted.class);
        var sameDayLoan = loans.lend(librarian, new LoanCommands.WalkInLoanCommand(readerId,
                List.of(new LoanCommands.WalkInItem(TitleId.of(titleId), copy.copyId())), "DESK", librarian));
        assertThat(((ModuleResult.Accepted<LoanCommands.LoanBatch>) sameDayLoan).value()
                .items().getFirst().outcome()).isEqualTo("REJECTED");
    }

    @Test
    void itCirc013_unboundManualReleaseSkipsFutureCopyAndDoesNotStrandIt() {
        var today = businessClock.today();
        var titleId = jdbc.queryForObject("""
                INSERT INTO book_title (title, price_cents) VALUES ('ITCIRC013 manual selection', 9900)
                RETURNING id
                """, Long.class);
        var futureCopy = createPendingCopy(titleId, "ITCIRC013-UR1");
        assertThat(copies.maintainCopy(new CopyCommands.MaintainCopy(
                futureCopy.copyId(), "SHELVE", futureCopy.version(), librarian)))
                .isInstanceOf(ModuleResult.Accepted.class);
        var eligibleCopyId = seedCopy(titleId, "ITCIRC013-UR2", "MAINTENANCE");
        var eligibleVersion = jdbc.queryForObject(
                "SELECT version FROM book_copy WHERE id = ?", Long.class, eligibleCopyId);
        assertThat(copies.maintainCopy(new CopyCommands.MaintainCopy(
                CopyId.of(eligibleCopyId), "RESTORE", eligibleVersion, librarian)))
                .isInstanceOf(ModuleResult.Accepted.class);

        var readerId = seedReader("itcirc013-manual-select-reader");
        var txId = jdbc.queryForObject("""
                INSERT INTO borrow_transaction (reader_id, title_id, status, reservation_date)
                VALUES (?, ?, 'MANUAL_HANDLING', ?)
                RETURNING id
                """, Long.class, readerId, titleId, java.sql.Date.valueOf(today));
        var release = loans.applyManualAction(librarian, new LoanCommands.ManualAction(
                TxId.of(txId), "RELEASE", "P3-13 select eligible copy", librarian));
        assertThat(release).isInstanceOf(ModuleResult.Accepted.class);
        assertThat(jdbc.queryForObject("SELECT copy_id FROM borrow_transaction WHERE id = ?",
                Long.class, txId)).isEqualTo(eligibleCopyId);
        assertThat(jdbc.queryForObject("SELECT status FROM book_copy WHERE id = ?",
                String.class, futureCopy.copyId().value())).isEqualTo("SHELF_READY");

        var onlyFutureTitleId = jdbc.queryForObject("""
                INSERT INTO book_title (title, price_cents) VALUES ('ITCIRC013 manual future only', 9900)
                RETURNING id
                """, Long.class);
        var onlyFutureCopy = createPendingCopy(onlyFutureTitleId, "ITCIRC013-UR3");
        assertThat(copies.maintainCopy(new CopyCommands.MaintainCopy(
                onlyFutureCopy.copyId(), "SHELVE", onlyFutureCopy.version(), librarian)))
                .isInstanceOf(ModuleResult.Accepted.class);
        var onlyFutureReaderId = seedReader("itcirc013-manual-future-reader");
        var onlyFutureTxId = jdbc.queryForObject("""
                INSERT INTO borrow_transaction (reader_id, title_id, status, reservation_date)
                VALUES (?, ?, 'MANUAL_HANDLING', ?)
                RETURNING id
                """, Long.class, onlyFutureReaderId, onlyFutureTitleId, java.sql.Date.valueOf(today));
        var denied = loans.applyManualAction(librarian, new LoanCommands.ManualAction(
                TxId.of(onlyFutureTxId), "RELEASE", "P3-13 future-only copy", librarian));
        assertThat(denied).isInstanceOf(ModuleResult.Rejected.class);
        assertThat(((ModuleResult.Rejected<?>) denied).code()).isEqualTo("MLS-CIRC-005");
        assertThat(jdbc.queryForObject("SELECT status FROM borrow_transaction WHERE id = ?",
                String.class, onlyFutureTxId)).isEqualTo("MANUAL_HANDLING");
        assertThat(jdbc.queryForObject("SELECT copy_id FROM borrow_transaction WHERE id = ?",
                Long.class, onlyFutureTxId)).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM book_copy WHERE id = ?",
                String.class, onlyFutureCopy.copyId().value())).isEqualTo("SHELF_READY");
    }

    private CopyCommands.CopySnapshot createPendingCopy(long titleId, String copyNo) {
        return ((ModuleResult.Accepted<CopyCommands.CopySnapshot>) copies.createCopy(
                new CopyCommands.CreateCopy(TitleId.of(titleId), copyNo, librarian))).value();
    }
}
