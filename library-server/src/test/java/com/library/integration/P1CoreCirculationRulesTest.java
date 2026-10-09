package com.library.integration;

import com.library.application.returning.ReturnUseCases;
import com.library.application.loan.LoanUseCases;
import com.library.catalog.contract.model.TitleId;
import com.library.bootstrap.DailyClosingScheduler;
import com.library.contract.ModuleResult;
import com.library.contract.model.AccountId;
import com.library.contract.model.PageCursor;
import com.library.contract.model.Role;
import com.library.contract.security.ActorContext;
import com.library.contract.time.BusinessClock;
import com.library.contract.closing.WriteAdmissionGate;
import com.library.contract.transaction.UnitOfWork;
import com.library.circulation.contract.ReturnCommands;
import com.library.circulation.contract.LoanCommands;
import com.library.circulation.contract.ReservationCommands;
import com.library.circulation.contract.ClosingCommands;
import com.library.circulation.contract.ReaderCirculationQuery;
import com.library.circulation.contract.model.BusinessDate;
import com.library.circulation.contract.model.CopyId;
import com.library.circulation.contract.model.TxId;
import com.library.settlement.contract.FineQuery;
import com.library.settlement.contract.OrderCommands;
import com.library.settlement.contract.model.FineId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "library.closing.daily-enabled=true",
        "library.closing.daily-cron=0 0 0 1 1 *",
        "library.outbox.dispatch-enabled=false"
})
@Testcontainers
class P1CoreCirculationRulesTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            "postgres@sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3")
            .withLabel("com.mylibrary.p1-stage", "p1-core-20260927-circulation-rules");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ReturnUseCases returns;
    @Autowired FineQuery fines;
    @Autowired LoanUseCases loans;
    @Autowired ReaderCirculationQuery circulationQuery;
    @Autowired DailyClosingScheduler dailyClosingScheduler;
    @Autowired BusinessClock businessClock;
    @Autowired ClosingCommands closing;
    @Autowired WriteAdmissionGate admission;
    @Autowired UnitOfWork unitOfWork;
    @Autowired OrderCommands orders;

    @Test
    void overdueFineIsTenPercentPerDayRoundedUpAndCappedAtThreeTimesPrice() {
        var readerId = insertAccount("READER");
        var librarianId = insertAccount("LIBRARIAN");
        var titleId = jdbc.queryForObject(
                "INSERT INTO book_title(title, price_cents) VALUES ('Fine Rule 19.95', 1995) RETURNING id",
                Long.class);
        var secondTitleId = jdbc.queryForObject(
                "INSERT INTO book_title(title, price_cents) VALUES ('Fine Cap 19.95', 1995) RETURNING id",
                Long.class);
        var today = businessClock.today();
        var firstCopy = insertOverdueLoan(readerId, titleId, today.minusDays(15));
        var secondCopy = insertOverdueLoan(readerId, secondTitleId, today.minusDays(40));
        var librarian = new ActorContext(AccountId.of(librarianId), Role.LIBRARIAN,
                Instant.parse("2026-09-27T12:00:00Z"));

        assertThat(returns.returnCopy(librarian, new ReturnCommands.ReturnCommand(
                CopyId.of(firstCopy), "INTACT", librarian))).isInstanceOf(ModuleResult.Accepted.class);
        assertThat(returns.returnCopy(librarian, new ReturnCommands.ReturnCommand(
                CopyId.of(secondCopy), "INTACT", librarian))).isInstanceOf(ModuleResult.Accepted.class);

        var page = accepted(fines.fines(new FineQuery.FineCriteria(readerId, "UNPAID", PageCursor.first())));
        assertThat(page.items()).extracting(FineQuery.FineView::amountCents)
                .containsExactly(2993L, 5985L);
    }

    @Test
    void pickupOutsideReservationDateMovesToManualAndManualReleaseUsesTheBoundCopy() {
        var readerId = insertAccount("READER");
        var librarianId = insertAccount("LIBRARIAN");
        var reader = new ActorContext(AccountId.of(readerId), Role.READER,
                Instant.parse("2026-09-27T12:00:00Z"));
        var librarian = new ActorContext(AccountId.of(librarianId), Role.LIBRARIAN,
                Instant.parse("2026-09-27T12:00:00Z"));
        var today = businessClock.today();
        var titleId = insertTitle("Non-date pickup", 1000);
        var reservedCopy = insertCopy(titleId, "SHELF_READY");
        var spareCopy = insertCopy(titleId, "SHELF_READY");

        var reservation = accepted(loans.reserve(reader, new ReservationCommands.ReservationCommand(
                readerId, TitleId.of(titleId), BusinessDate.of(today.plusDays(1)),
                "09:00-10:30", "ONLINE", true, reader)));
        assertThat(reservation.outcome()).isEqualTo("CONFIRMED");
        assertThat(reservation.txId()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT copy_id FROM borrow_transaction WHERE id=?", Long.class,
                reservation.txId().value())).isEqualTo(reservedCopy);

        var routed = accepted(loans.claim(librarian, new LoanCommands.ClaimPickup(
                reservation.txId(), CopyId.of(reservedCopy), librarian)));
        assertThat(routed.status()).isEqualTo("MANUAL_HANDLING");
        assertThat(transactionStatus(reservation.txId().value())).isEqualTo("MANUAL_HANDLING");
        assertThat(copyStatus(reservedCopy)).isEqualTo("RESERVED");
        assertThat(copyStatus(spareCopy)).isEqualTo("SHELF_READY");

        var released = accepted(loans.applyManualAction(librarian, new LoanCommands.ManualAction(
                reservation.txId(), "RELEASE", "预约日外到台", librarian)));
        assertThat(released.status()).isEqualTo("LENT");
        assertThat(transactionStatus(reservation.txId().value())).isEqualTo("LENT");
        assertThat(jdbc.queryForObject("SELECT copy_id FROM borrow_transaction WHERE id=?", Long.class,
                reservation.txId().value())).isEqualTo(reservedCopy);
        assertThat(copyStatus(reservedCopy)).isEqualTo("LENT");
        assertThat(copyStatus(spareCopy)).isEqualTo("SHELF_READY");
    }

    @Test
    void isolatedGateAllowsSessionIssueAndClosingControlButRejectsBusinessWrites() {
        var today = businessClock.today();
        resetClosingControl(today, "ISOLATED", true);

        assertThat(unitOfWork.inTransaction(() -> admission.acquire("/api/v1/auth/sessions"))).isTrue();
        assertThat(unitOfWork.inTransaction(() -> admission.acquire("/api/v1/closing/reopen"))).isTrue();
        assertThat(unitOfWork.inTransaction(() -> admission.acquire("/api/v1/reservations"))).isFalse();
    }

    @Test
    void walkInBatchStopsAtFiveActiveLoansForOneReader() {
        var readerId = insertAccount("READER");
        var librarianId = insertAccount("LIBRARIAN");
        var librarian = new ActorContext(AccountId.of(librarianId), Role.LIBRARIAN,
                Instant.parse("2026-09-27T12:00:00Z"));
        for (int i = 0; i < 4; i++) {
            var titleId = insertTitle("Existing " + i, 1000);
            var copyId = insertCopy(titleId, "LENT");
            jdbc.update("""
                    INSERT INTO borrow_transaction(reader_id, title_id, copy_id, status,
                                                   reservation_date, due_date)
                    VALUES (?, ?, ?, 'LENT', ?, ?)
                    """, readerId, titleId, copyId,
                    java.sql.Date.valueOf(businessClock.today().minusDays(1)),
                    java.sql.Date.valueOf(businessClock.today().plusDays(6)));
        }
        insertActiveReservation(readerId, "WAITLIST", businessClock.today().plusDays(1));
        var fifthTitle = insertTitle("Fifth copy", 1000);
        var fifthCopy = insertCopy(fifthTitle, "SHELF_READY");
        var sixthTitle = insertTitle("Sixth copy", 1000);
        var sixthCopy = insertCopy(sixthTitle, "SHELF_READY");

        var batch = accepted(loans.lend(librarian, new LoanCommands.WalkInLoanCommand(
                readerId, java.util.List.of(
                        new LoanCommands.WalkInItem(TitleId.of(fifthTitle), CopyId.of(fifthCopy)),
                        new LoanCommands.WalkInItem(TitleId.of(sixthTitle), CopyId.of(sixthCopy))),
                "DESK", librarian)));

        assertThat(batch.items()).extracting(LoanCommands.LoanBatchItem::outcome)
                .containsExactly("LENT", "REJECTED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM borrow_transaction WHERE reader_id=? AND status='LENT'",
                Integer.class, readerId)).isEqualTo(5);
    }

    @Test
    void concurrentWalkInsSerializeAtTheFiveLoanLimit() throws Exception {
        var readerId = insertAccount("READER");
        var librarianId = insertAccount("LIBRARIAN");
        var librarian = new ActorContext(AccountId.of(librarianId), Role.LIBRARIAN,
                Instant.parse("2026-09-27T12:00:00Z"));
        for (int i = 0; i < 4; i++) {
            var titleId = insertTitle("Concurrent existing " + i, 1000);
            var copyId = insertCopy(titleId, "LENT");
            jdbc.update("""
                    INSERT INTO borrow_transaction(reader_id, title_id, copy_id, status,
                                                   reservation_date, due_date)
                    VALUES (?, ?, ?, 'LENT', ?, ?)
                    """, readerId, titleId, copyId,
                    java.sql.Date.valueOf(businessClock.today().minusDays(1)),
                    java.sql.Date.valueOf(businessClock.today().plusDays(6)));
        }
        var titleA = insertTitle("Concurrent A", 1000);
        var titleB = insertTitle("Concurrent B", 1000);
        var copyA = insertCopy(titleA, "SHELF_READY");
        var copyB = insertCopy(titleB, "SHELF_READY");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);

        try {
            var first = CompletableFuture.supplyAsync(() -> concurrentWalkIn(
                    ready, start, librarian, readerId, titleA, copyA), workers);
            var second = CompletableFuture.supplyAsync(() -> concurrentWalkIn(
                    ready, start, librarian, readerId, titleB, copyB), workers);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            var results = java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertThat(results).flatExtracting(batch -> batch.items())
                    .extracting(LoanCommands.LoanBatchItem::outcome)
                    .containsExactlyInAnyOrder("LENT", "REJECTED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM borrow_transaction WHERE reader_id=? AND status='LENT'",
                    Integer.class, readerId)).isEqualTo(5);
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void reservationLimitDoesNotCountActiveLoans() {
        var readerId = insertAccount("READER");
        var reader = new ActorContext(AccountId.of(readerId), Role.READER,
                Instant.parse("2026-09-27T12:00:00Z"));
        for (int i = 0; i < 4; i++) {
            insertActiveReservation(readerId, "WAITLIST", businessClock.today().plusDays(1));
        }
        var lentTitle = insertTitle("Separate loan", 1000);
        var lentCopy = insertCopy(lentTitle, "LENT");
        jdbc.update("""
                INSERT INTO borrow_transaction(reader_id, title_id, copy_id, status, reservation_date, due_date)
                VALUES (?, ?, ?, 'LENT', ?, ?)
                """, readerId, lentTitle, lentCopy,
                java.sql.Date.valueOf(businessClock.today().minusDays(1)),
                java.sql.Date.valueOf(businessClock.today().plusDays(6)));
        var queuedTitle = insertTitle("Separate reservation", 1000);
        insertCopy(queuedTitle, "PENDING_SHELVING");

        var reservation = accepted(loans.reserve(reader, new ReservationCommands.ReservationCommand(
                readerId, TitleId.of(queuedTitle), BusinessDate.of(businessClock.today().plusDays(1)),
                "09:00-10:30", "ONLINE", true, reader)));

        assertThat(reservation.outcome()).isEqualTo("WAITLIST_ENQUEUED");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM borrow_transaction
                WHERE reader_id = ? AND status IN ('AWAIT', 'CONFIRMED', 'MANUAL_HANDLING', 'WAITLIST')
                """, Integer.class, readerId)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM borrow_transaction WHERE reader_id=? AND status='LENT'",
                Integer.class, readerId)).isEqualTo(1);
    }

    @Test
    void overdueLoanAndUnpaidFineBlockNewBorrowWithoutChangingCopies() {
        var librarianId = insertAccount("LIBRARIAN");
        var librarian = new ActorContext(AccountId.of(librarianId), Role.LIBRARIAN,
                Instant.parse("2026-09-27T12:00:00Z"));

        var overdueReader = insertAccount("READER");
        var overdueTitle = insertTitle("Overdue outstanding", 1000);
        insertOverdueLoan(overdueReader, overdueTitle, businessClock.today().minusDays(1));
        var overdueTarget = insertTitle("Overdue target", 1000);
        var overdueTargetCopy = insertCopy(overdueTarget, "SHELF_READY");
        assertRejected(loans.lend(librarian, oneCopyLoan(overdueReader, overdueTarget, overdueTargetCopy, librarian)),
                "MLS-CIRC-011");
        assertThat(copyStatus(overdueTargetCopy)).isEqualTo("SHELF_READY");

        var indebtedReader = insertAccount("READER");
        var returnedTitle = insertTitle("Fine source", 1000);
        var returnedTx = jdbc.queryForObject("""
                INSERT INTO borrow_transaction(reader_id, title_id, status, reservation_date, due_date)
                VALUES (?, ?, 'RETURNED', ?, ?) RETURNING id
                """, Long.class, indebtedReader, returnedTitle,
                java.sql.Date.valueOf(businessClock.today().minusDays(10)),
                java.sql.Date.valueOf(businessClock.today().minusDays(3)));
        jdbc.update("INSERT INTO fine(reader_id, source_tx_id, amount_cents) VALUES (?, ?, 100)",
                indebtedReader, returnedTx);
        var indebtedTarget = insertTitle("Indebted target", 1000);
        var indebtedTargetCopy = insertCopy(indebtedTarget, "SHELF_READY");
        assertRejected(loans.lend(librarian,
                oneCopyLoan(indebtedReader, indebtedTarget, indebtedTargetCopy, librarian)), "MLS-CIRC-011");
        assertThat(copyStatus(indebtedTargetCopy)).isEqualTo("SHELF_READY");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM borrow_transaction WHERE reader_id=? AND title_id=? AND status='LENT'
                """, Integer.class, indebtedReader, indebtedTarget)).isZero();
    }

    @Test
    void reservationChannelDateAndBookabilityRulesAreEnforced() {
        var readerId = insertAccount("READER");
        var reader = new ActorContext(AccountId.of(readerId), Role.READER,
                Instant.parse("2026-09-27T12:00:00Z"));
        var librarianId = insertAccount("LIBRARIAN");
        var librarian = new ActorContext(AccountId.of(librarianId), Role.LIBRARIAN,
                Instant.parse("2026-09-27T12:00:00Z"));
        var availableTitle = insertTitle("Online same-day guard", 1000);
        insertCopy(availableTitle, "SHELF_READY");
        var onlineToday = loans.reserve(reader, new ReservationCommands.ReservationCommand(
                readerId, TitleId.of(availableTitle), BusinessDate.of(businessClock.today()),
                "09:00-10:30", "ONLINE", true, reader));
        assertRejected(onlineToday, "MLS-CIRC-001");

        var reservedTitle = insertTitle("No reservable copies", 1000);
        insertCopy(reservedTitle, "RESERVED");
        var unavailable = loans.reserve(reader, new ReservationCommands.ReservationCommand(
                readerId, TitleId.of(reservedTitle), BusinessDate.of(businessClock.today().plusDays(1)),
                "09:00-10:30", "ONLINE", true, reader));
        assertRejected(unavailable, "MLS-CIRC-011");

        var deskTitle = insertTitle("Same-day desk waitlist", 1000);
        insertCopy(deskTitle, "LENT");
        var deskSameDay = accepted(loans.reserve(reader, new ReservationCommands.ReservationCommand(
                readerId, TitleId.of(deskTitle), BusinessDate.of(businessClock.today()),
                "09:00-10:30", "DESK", false, reader)));
        assertThat(deskSameDay.outcome()).isEqualTo("WAITLIST_ENQUEUED");
        assertThat(jdbc.queryForObject("SELECT status FROM borrow_transaction WHERE id=?", String.class,
                deskSameDay.txId().value())).isEqualTo("WAITLIST");

        var deskAvailableTitle = insertTitle("Same-day desk available", 1000);
        var deskAvailableCopy = insertCopy(deskAvailableTitle, "SHELF_READY");
        var deskConfirmed = accepted(loans.reserve(librarian, new ReservationCommands.ReservationCommand(
                readerId, TitleId.of(deskAvailableTitle), BusinessDate.of(businessClock.today()),
                "13:00-14:30", "DESK", false, librarian)));
        assertThat(deskConfirmed.outcome()).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT status FROM borrow_transaction WHERE id=?", String.class,
                deskConfirmed.txId().value())).isEqualTo("CONFIRMED");
        assertThat(copyStatus(deskAvailableCopy)).isEqualTo("RESERVED");

        var claimed = accepted(loans.claim(librarian, new LoanCommands.ClaimPickup(
                deskConfirmed.txId(), CopyId.of(deskAvailableCopy), librarian)));
        assertThat(claimed.status()).isEqualTo("LENT");
        assertThat(copyStatus(deskAvailableCopy)).isEqualTo("LENT");
        assertThat(jdbc.queryForObject("SELECT due_date FROM borrow_transaction WHERE id=?", java.sql.Date.class,
                deskConfirmed.txId().value()).toLocalDate()).isEqualTo(businessClock.today().plusDays(7));
    }

    @Test
    void loanTermsStartAtSevenDaysAndRenewalsStopAtDayThirty() {
        var readerId = insertAccount("READER");
        var librarianId = insertAccount("LIBRARIAN");
        var reader = new ActorContext(AccountId.of(readerId), Role.READER,
                Instant.parse("2026-09-27T12:00:00Z"));
        var librarian = new ActorContext(AccountId.of(librarianId), Role.LIBRARIAN,
                Instant.parse("2026-09-27T12:00:00Z"));
        var titleId = insertTitle("Loan dates", 1000);
        var copyId = insertCopy(titleId, "SHELF_READY");
        var batch = accepted(loans.lend(librarian, new LoanCommands.WalkInLoanCommand(
                readerId, java.util.List.of(new LoanCommands.WalkInItem(
                TitleId.of(titleId), CopyId.of(copyId))), "DESK", librarian)));
        var txId = batch.items().getFirst().txId();
        assertThat(batch.items().getFirst().outcome()).isEqualTo("LENT");
        assertThat(jdbc.queryForMap("SELECT loan_started_date, due_date FROM borrow_transaction WHERE id=?",
                txId.value())).containsEntry("loan_started_date", java.sql.Date.valueOf(businessClock.today()))
                .containsEntry("due_date", java.sql.Date.valueOf(businessClock.today().plusDays(7)));

        for (int i = 1; i <= 4; i++) {
            var renewal = accepted(loans.renew(reader, new LoanCommands.RenewalCommand(txId, null, reader)));
            assertThat(renewal.dueDate().value()).isEqualTo(businessClock.today().plusDays(Math.min(7 + 7 * i, 30)));
        }
        assertRejected(loans.renew(reader, new LoanCommands.RenewalCommand(txId, null, reader)), "MLS-CIRC-012");
    }

    @Test
    void renewalStopsTheDayBeforeEarliestConfirmedOrWaitlistReservation() {
        var readerId = insertAccount("READER");
        var reservationReaderId = insertAccount("READER");
        var librarianId = insertAccount("LIBRARIAN");
        var reader = new ActorContext(AccountId.of(readerId), Role.READER,
                Instant.parse("2026-09-27T12:00:00Z"));
        var librarian = new ActorContext(AccountId.of(librarianId), Role.LIBRARIAN,
                Instant.parse("2026-09-27T12:00:00Z"));
        var today = businessClock.today();
        var titleId = insertTitle("Renew before reservation", 1000);
        var lentCopy = insertCopy(titleId, "SHELF_READY");
        var loan = accepted(loans.lend(librarian, oneCopyLoan(readerId, titleId, lentCopy, librarian)));
        var loanTxId = loan.items().getFirst().txId();

        var confirmedCopy = insertCopy(titleId, "RESERVED");
        jdbc.update("""
                INSERT INTO borrow_transaction(reader_id,title_id,copy_id,status,reservation_date,pickup_slot)
                VALUES (?, ?, ?, 'CONFIRMED', ?, 'AM')
                """, reservationReaderId, titleId, confirmedCopy,
                java.sql.Date.valueOf(today.plusDays(12)));
        jdbc.update("""
                INSERT INTO borrow_transaction(reader_id,title_id,status,reservation_date,pickup_slot)
                VALUES (?, ?, 'WAITLIST', ?, 'PM')
                """, insertAccount("READER"), titleId, java.sql.Date.valueOf(today.plusDays(15)));

        var renewal = accepted(loans.renew(reader, new LoanCommands.RenewalCommand(loanTxId, null, reader)));

        assertThat(renewal.dueDate().value()).isEqualTo(today.plusDays(11));
        assertThat(jdbc.queryForObject("SELECT due_date FROM borrow_transaction WHERE id=?", java.sql.Date.class,
                loanTxId.value()).toLocalDate()).isEqualTo(today.plusDays(11));
        assertRejected(loans.renew(reader, new LoanCommands.RenewalCommand(loanTxId, null, reader)),
                "MLS-CIRC-012");
        assertThat(jdbc.queryForObject("SELECT renewal_count FROM borrow_transaction WHERE id=?", Integer.class,
                loanTxId.value())).isOne();

        var blockedReaderId = insertAccount("READER");
        var blockedReader = new ActorContext(AccountId.of(blockedReaderId), Role.READER,
                Instant.parse("2026-09-27T12:00:00Z"));
        var blockedTitleId = insertTitle("Renew blocked by current due date", 1000);
        var blockedCopy = insertCopy(blockedTitleId, "SHELF_READY");
        var blockedLoan = accepted(loans.lend(librarian,
                oneCopyLoan(blockedReaderId, blockedTitleId, blockedCopy, librarian)));
        var blockedTxId = blockedLoan.items().getFirst().txId();
        var blockedReservationCopy = insertCopy(blockedTitleId, "RESERVED");
        jdbc.update("""
                INSERT INTO borrow_transaction(reader_id,title_id,copy_id,status,reservation_date,pickup_slot)
                VALUES (?, ?, ?, 'CONFIRMED', ?, 'AM')
                """, insertAccount("READER"), blockedTitleId, blockedReservationCopy,
                java.sql.Date.valueOf(today.plusDays(7)));

        assertRejected(loans.renew(blockedReader,
                new LoanCommands.RenewalCommand(blockedTxId, null, blockedReader)), "MLS-CIRC-012");
        assertThat(jdbc.queryForObject("SELECT due_date FROM borrow_transaction WHERE id=?", java.sql.Date.class,
                blockedTxId.value()).toLocalDate()).isEqualTo(today.plusDays(7));
    }

    @Test
    void reservationAndRenewalSerializeOnTitleAndRenewalSeesCommittedReservation() throws Exception {
        var borrowerId = insertAccount("READER");
        var reservationReaderId = insertAccount("READER");
        var librarianId = insertAccount("LIBRARIAN");
        var borrower = new ActorContext(AccountId.of(borrowerId), Role.READER,
                Instant.parse("2026-09-27T12:00:00Z"));
        var reservationReader = new ActorContext(AccountId.of(reservationReaderId), Role.READER,
                Instant.parse("2026-09-27T12:00:00Z"));
        var librarian = new ActorContext(AccountId.of(librarianId), Role.LIBRARIAN,
                Instant.parse("2026-09-27T12:00:00Z"));
        var titleId = insertTitle("Reservation renewal race", 1000);
        var copyId = insertCopy(titleId, "SHELF_READY");
        var loan = accepted(loans.lend(librarian, oneCopyLoan(borrowerId, titleId, copyId, librarian)));
        var loanTxId = loan.items().getFirst().txId();
        var reservationDate = businessClock.today().plusDays(14);
        var lockHeld = new CountDownLatch(1);
        var releaseReservation = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);

        try {
            var reservation = CompletableFuture.supplyAsync(() -> unitOfWork.inTransaction(() -> {
                circulationQuery.lockReader(reservationReaderId);
                circulationQuery.lockTitleReservations(titleId);
                lockHeld.countDown();
                awaitLatch(releaseReservation);
                return accepted(loans.reserve(reservationReader,
                        new ReservationCommands.ReservationCommand(reservationReaderId, TitleId.of(titleId),
                                BusinessDate.of(reservationDate), "09:00-10:30", "ONLINE", true, reservationReader)));
            }), workers);
            assertThat(lockHeld.await(5, TimeUnit.SECONDS)).isTrue();

            var renewal = CompletableFuture.supplyAsync(() -> accepted(loans.renew(borrower,
                    new LoanCommands.RenewalCommand(loanTxId, null, borrower))), workers);
            assertThat(awaitTitleReservationLockWait()).isTrue();
            assertThat(renewal.isDone()).isFalse();

            releaseReservation.countDown();
            assertThat(reservation.get(15, TimeUnit.SECONDS).outcome()).isEqualTo("WAITLIST_ENQUEUED");
            var result = renewal.get(15, TimeUnit.SECONDS);
            assertThat(result.dueDate().value()).isEqualTo(reservationDate.minusDays(1));
            assertThat(jdbc.queryForObject("SELECT due_date FROM borrow_transaction WHERE id=?", java.sql.Date.class,
                    loanTxId.value()).toLocalDate()).isBefore(reservationDate);
        } finally {
            releaseReservation.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void deployedDailyCloseTriggerReleasesUnclaimedReservationWithoutSystemActor() {
        var closingDate = businessClock.today().minusDays(1);
        resetClosingControl(closingDate, "REOPENED", false);
        jdbc.update("DELETE FROM closing_batch WHERE business_date=?", java.sql.Date.valueOf(closingDate));
        var readerId = insertAccount("READER");
        var titleId = insertTitle("Daily close", 1000);
        var copyId = insertCopy(titleId, "RESERVED");
        var txId = jdbc.queryForObject("""
                INSERT INTO borrow_transaction(reader_id, title_id, copy_id, status, reservation_date, pickup_slot)
                VALUES (?, ?, ?, 'CONFIRMED', ?, 'AM') RETURNING id
                """, Long.class, readerId, titleId, copyId, java.sql.Date.valueOf(closingDate));

        dailyClosingScheduler.closePreviousBusinessDate();

        assertThat(jdbc.queryForObject("SELECT status FROM borrow_transaction WHERE id=?", String.class, txId))
                .isEqualTo("NOT_BORROWED");
        assertThat(copyStatus(copyId)).isEqualTo("SHELF_READY");
        assertThat(jdbc.queryForObject("SELECT status FROM closing_batch WHERE business_date=?",
                String.class, java.sql.Date.valueOf(closingDate))).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT draining FROM closing_admission WHERE scope='GLOBAL'", Boolean.class))
                .isFalse();
        assertThat(unitOfWork.inTransaction(() -> admission.acquire("/api/v1/reservations"))).isTrue();
    }

    @Test
    void startupCatchesUpEachMissedDateAndAutomaticallyReopens() {
        var today = businessClock.today();
        var firstMissed = today.minusDays(2);
        var closingDate = today.minusDays(1);
        var baseline = today.minusDays(3);
        resetClosingControl(baseline, "ISOLATED", true);
        jdbc.update("DELETE FROM closing_batch WHERE business_date BETWEEN ? AND ?",
                java.sql.Date.valueOf(baseline), java.sql.Date.valueOf(closingDate));
        jdbc.update("INSERT INTO closing_batch(business_date,status,attempts) VALUES (?,'SUCCEEDED',1)",
                java.sql.Date.valueOf(baseline));
        var firstTx = confirmedForDate(firstMissed, "Catch-up first");
        var secondTx = confirmedForDate(closingDate, "Catch-up second");

        try (var context = new GenericApplicationContext()) {
            context.registerBean(DailyClosingScheduler.class, () -> dailyClosingScheduler);
            context.refresh();
        }

        assertThat(transactionStatus(firstTx)).isEqualTo("NOT_BORROWED");
        assertThat(transactionStatus(secondTx)).isEqualTo("NOT_BORROWED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM closing_batch WHERE business_date IN (?,?) AND status='SUCCEEDED'",
                Integer.class, java.sql.Date.valueOf(firstMissed), java.sql.Date.valueOf(closingDate))).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT \"current_date\" FROM closing_control WHERE scope='GLOBAL'",
                java.sql.Date.class).toLocalDate()).isEqualTo(closingDate);
        assertThat(unitOfWork.inTransaction(() -> admission.acquire("/api/v1/loans"))).isTrue();
        assertThat(unitOfWork.inTransaction(() -> admission.acquire("/api/v1/ai/sessions"))).isTrue();
        dailyClosingScheduler.closePreviousBusinessDate();
        assertThat(jdbc.queryForList("SELECT attempts FROM closing_batch WHERE business_date IN (?,?)",
                Integer.class, java.sql.Date.valueOf(firstMissed), java.sql.Date.valueOf(closingDate)))
                .containsOnly(1);
    }

    @Test
    void startupDoesNotRetryPersistedFailedBatchPastRetryBudget() {
        var today = businessClock.today();
        var failedDate = today.minusDays(2);
        var latestDueDate = today.minusDays(1);
        resetClosingControl(failedDate, "ISOLATED", true);
        jdbc.update("UPDATE closing_control SET retry_budget=1 WHERE scope='GLOBAL'");
        jdbc.update("DELETE FROM closing_batch WHERE business_date BETWEEN ? AND ?",
                java.sql.Date.valueOf(failedDate), java.sql.Date.valueOf(latestDueDate));
        jdbc.update("INSERT INTO closing_batch(business_date,status,attempts) VALUES (?,'FAILED',2)",
                java.sql.Date.valueOf(failedDate));

        dailyClosingScheduler.closePreviousBusinessDate();

        assertThat(jdbc.queryForMap("SELECT status,attempts FROM closing_batch WHERE business_date=?",
                java.sql.Date.valueOf(failedDate))).containsEntry("status", "FAILED")
                .containsEntry("attempts", 2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM closing_batch WHERE business_date>?",
                Integer.class, java.sql.Date.valueOf(failedDate))).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM closing_control WHERE scope='GLOBAL'", String.class))
                .isEqualTo("ISOLATED");
        assertThat(jdbc.queryForObject("SELECT draining FROM closing_admission WHERE scope='GLOBAL'", Boolean.class))
                .isTrue();
        assertThat(unitOfWork.inTransaction(() -> admission.acquire("/api/v1/ai/sessions"))).isFalse();
    }

    @Test
    void startupRetriesPersistedFailedBatchWithinRetryBudget() {
        var today = businessClock.today();
        var failedDate = today.minusDays(2);
        var latestDueDate = today.minusDays(1);
        resetClosingControl(failedDate, "ISOLATED", true);
        jdbc.update("UPDATE closing_control SET retry_budget=1 WHERE scope='GLOBAL'");
        jdbc.update("DELETE FROM closing_batch WHERE business_date BETWEEN ? AND ?",
                java.sql.Date.valueOf(failedDate), java.sql.Date.valueOf(latestDueDate));
        jdbc.update("INSERT INTO closing_batch(business_date,status,attempts) VALUES (?,'FAILED',1)",
                java.sql.Date.valueOf(failedDate));

        dailyClosingScheduler.closePreviousBusinessDate();

        assertThat(jdbc.queryForMap("SELECT status,attempts FROM closing_batch WHERE business_date=?",
                java.sql.Date.valueOf(failedDate))).containsEntry("status", "SUCCEEDED")
                .containsEntry("attempts", 2);
        assertThat(jdbc.queryForMap("SELECT status,attempts FROM closing_batch WHERE business_date=?",
                java.sql.Date.valueOf(latestDueDate))).containsEntry("status", "SUCCEEDED")
                .containsEntry("attempts", 1);
        assertThat(unitOfWork.inTransaction(() -> admission.acquire("/api/v1/ai/sessions"))).isTrue();
    }

    @Test
    void dailyCloseWaitsForAdmittedWriteToCommitAndPreservesIt() throws Exception {
        var today = businessClock.today();
        var closingDate = today.minusDays(1);
        resetClosingControl(closingDate, "OPEN", false);
        jdbc.update("DELETE FROM closing_batch WHERE business_date=?", java.sql.Date.valueOf(closingDate));
        var readerId = insertAccount("READER");
        var titleId = insertTitle("In-flight close write", 1000);
        var copyId = insertCopy(titleId, "SHELF_READY");
        var writerEntered = new CountDownLatch(1);
        var releaseWriter = new CountDownLatch(1);
        var closeStarted = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            var writer = CompletableFuture.supplyAsync(() -> unitOfWork.inTransaction(() -> {
                assertThat(admission.acquire("/api/v1/walk-in-loans")).isTrue();
                jdbc.update("UPDATE book_copy SET status='LENT' WHERE id=?", copyId);
                var txId = jdbc.queryForObject("""
                        INSERT INTO borrow_transaction(reader_id,title_id,copy_id,status,reservation_date,
                                                       loan_started_date,due_date)
                        VALUES (?,?,?,'LENT',?,?,?) RETURNING id
                        """, Long.class, readerId, titleId, copyId,
                        java.sql.Date.valueOf(today), java.sql.Date.valueOf(today),
                        java.sql.Date.valueOf(today.plusDays(7)));
                writerEntered.countDown();
                awaitLatch(releaseWriter);
                return txId;
            }), workers);
            assertThat(writerEntered.await(5, TimeUnit.SECONDS)).isTrue();

            var close = CompletableFuture.supplyAsync(() -> {
                closeStarted.countDown();
                return closing.closeBusinessDate(new ClosingCommands.ClosingCommand(BusinessDate.of(closingDate)));
            }, workers);
            assertThat(closeStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(awaitClosingAdmissionLock()).isTrue();
            assertThat(close.isDone()).isFalse();

            releaseWriter.countDown();
            var txId = writer.get(10, TimeUnit.SECONDS);
            var result = close.get(10, TimeUnit.SECONDS);
            assertThat(result).isInstanceOf(ModuleResult.Accepted.class);
            assertThat(transactionStatus(txId)).isEqualTo("LENT");
            assertThat(copyStatus(copyId)).isEqualTo("LENT");
            assertThat(jdbc.queryForObject("SELECT status FROM closing_batch WHERE business_date=?",
                    String.class, java.sql.Date.valueOf(closingDate))).isEqualTo("SUCCEEDED");
            assertThat(unitOfWork.inTransaction(() -> admission.acquire("/api/v1/loans"))).isFalse();
            assertThat(closing.reopen(new ClosingCommands.ReopenCommand(BusinessDate.of(closingDate))))
                    .isInstanceOf(ModuleResult.Accepted.class);
            assertThat(unitOfWork.inTransaction(() -> admission.acquire("/api/v1/loans"))).isTrue();
        } finally {
            releaseWriter.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void administratorCannotReopenUsingOldThroughDateWhenNewestDueBatchIsMissing() {
        var today = businessClock.today();
        var requestedThrough = today.minusDays(2);
        var latestDue = today.minusDays(1);
        resetClosingControl(requestedThrough, "ISOLATED", true);
        jdbc.update("DELETE FROM closing_batch WHERE business_date BETWEEN ? AND ?",
                java.sql.Date.valueOf(requestedThrough), java.sql.Date.valueOf(latestDue));
        jdbc.update("INSERT INTO closing_batch(business_date,status,attempts) VALUES (?,'SUCCEEDED',1)",
                java.sql.Date.valueOf(requestedThrough));

        var result = closing.reopen(new ClosingCommands.ReopenCommand(BusinessDate.of(requestedThrough)));

        assertThat(result).isInstanceOf(ModuleResult.Rejected.class);
        assertThat(((ModuleResult.Rejected<?>) result).code()).isEqualTo("MLS-CIRC-008");
        assertThat(jdbc.queryForObject("SELECT status FROM closing_control WHERE scope='GLOBAL'", String.class))
                .isEqualTo("ISOLATED");
        assertThat(jdbc.queryForObject("SELECT draining FROM closing_admission WHERE scope='GLOBAL'", Boolean.class))
                .isTrue();
        assertThat(unitOfWork.inTransaction(() -> admission.acquire("/api/v1/ai/sessions"))).isFalse();
    }

    @Test
    void reopenRechecksLatestBatchAfterWaitingForControlRowLock() throws Exception {
        var latestDue = businessClock.today().minusDays(1);
        resetClosingControl(latestDue, "ISOLATED", true);
        jdbc.update("DELETE FROM closing_batch WHERE business_date=?", java.sql.Date.valueOf(latestDue));
        jdbc.update("INSERT INTO closing_batch(business_date,status,attempts) VALUES (?,'SUCCEEDED',1)",
                java.sql.Date.valueOf(latestDue));

        var controlLocked = new CountDownLatch(1);
        var releaseControl = new CountDownLatch(1);
        var reopenStarted = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            var holder = CompletableFuture.runAsync(() -> unitOfWork.inTransaction(() -> {
                jdbc.queryForObject("SELECT version FROM closing_control WHERE scope='GLOBAL' FOR UPDATE", Long.class);
                controlLocked.countDown();
                awaitLatch(releaseControl);
                jdbc.update("UPDATE closing_batch SET status='MANUAL',attempts=2 WHERE business_date=?",
                        java.sql.Date.valueOf(latestDue));
            }), workers);
            assertThat(controlLocked.await(5, TimeUnit.SECONDS)).isTrue();

            var reopen = CompletableFuture.supplyAsync(() -> {
                reopenStarted.countDown();
                return closing.reopen(new ClosingCommands.ReopenCommand(BusinessDate.of(latestDue)));
            }, workers);
            assertThat(reopenStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(awaitClosingControlLockWait()).isTrue();

            releaseControl.countDown();
            holder.get(10, TimeUnit.SECONDS);
            var result = reopen.get(10, TimeUnit.SECONDS);

            assertThat(result).isInstanceOf(ModuleResult.Rejected.class);
            assertThat(((ModuleResult.Rejected<?>) result).code()).isEqualTo("MLS-CIRC-008");
            assertThat(jdbc.queryForObject("SELECT status FROM closing_batch WHERE business_date=?",
                    String.class, java.sql.Date.valueOf(latestDue))).isEqualTo("MANUAL");
            assertThat(jdbc.queryForObject("SELECT status FROM closing_control WHERE scope='GLOBAL'", String.class))
                    .isEqualTo("ISOLATED");
            assertThat(jdbc.queryForObject("SELECT draining FROM closing_admission WHERE scope='GLOBAL'", Boolean.class))
                    .isTrue();
            assertThat(unitOfWork.inTransaction(() -> admission.acquire("/api/v1/ai/sessions"))).isFalse();
        } finally {
            releaseControl.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void administratorCannotReopenUntilAllPriorClosingBatchesSucceeded() {
        var today = businessClock.today();
        var missedDate = today.minusDays(1);
        var baseline = today.minusDays(3);
        resetClosingControl(baseline, "ISOLATED", true);
        jdbc.update("DELETE FROM closing_batch WHERE business_date BETWEEN ? AND ?",
                java.sql.Date.valueOf(baseline), java.sql.Date.valueOf(missedDate));
        jdbc.update("INSERT INTO closing_batch(business_date,status,attempts) VALUES (?,'SUCCEEDED',1)",
                java.sql.Date.valueOf(baseline));
        jdbc.update("INSERT INTO closing_batch(business_date,status,attempts) VALUES (?,'SUCCEEDED',1)",
                java.sql.Date.valueOf(missedDate));

        var missingMiddle = closing.reopen(new ClosingCommands.ReopenCommand(BusinessDate.of(missedDate)));

        assertThat(missingMiddle).isInstanceOf(ModuleResult.Rejected.class);
        assertThat(((ModuleResult.Rejected<?>) missingMiddle).code()).isEqualTo("MLS-CIRC-008");
        assertThat(jdbc.queryForObject("SELECT draining FROM closing_admission WHERE scope='GLOBAL'", Boolean.class))
                .isTrue();
        assertThat(unitOfWork.inTransaction(() -> admission.acquire("/api/v1/loans"))).isFalse();

        resetClosingControl(missedDate, "ISOLATED", true);
        var beforeBaseline = closing.reopen(new ClosingCommands.ReopenCommand(
                BusinessDate.of(missedDate.minusDays(1))));
        assertThat(beforeBaseline).isInstanceOf(ModuleResult.Rejected.class);
        assertThat(((ModuleResult.Rejected<?>) beforeBaseline).code()).isEqualTo("MLS-CIRC-008");
        jdbc.update("DELETE FROM closing_batch WHERE business_date=?", java.sql.Date.valueOf(missedDate));
        var noBatch = closing.reopen(new ClosingCommands.ReopenCommand(BusinessDate.of(missedDate)));
        assertThat(noBatch).isInstanceOf(ModuleResult.Rejected.class);
        assertThat(((ModuleResult.Rejected<?>) noBatch).code()).isEqualTo("MLS-CIRC-008");
        jdbc.update("DELETE FROM closing_batch");
        jdbc.update("UPDATE closing_control SET \"current_date\"=NULL,first_business_date=NULL WHERE scope='GLOBAL'");
        var emptyHistory = closing.reopen(new ClosingCommands.ReopenCommand(BusinessDate.of(missedDate)));
        assertThat(emptyHistory).isInstanceOf(ModuleResult.Rejected.class);
        assertThat(((ModuleResult.Rejected<?>) emptyHistory).code()).isEqualTo("MLS-CIRC-008");
        var future = closing.reopen(new ClosingCommands.ReopenCommand(BusinessDate.of(today.plusDays(1))));
        assertThat(future).isInstanceOf(ModuleResult.Rejected.class);
        assertThat(((ModuleResult.Rejected<?>) future).code()).isEqualTo("MLS-CIRC-008");
    }

    @Test
    void duplicateSuccessCallbacksRacingCloseHaveOneLegalTerminalState() throws Exception {
        var payment = openFineOrder("Payment close race");
        var eventId = "p1-close-callback-" + System.nanoTime();
        var callback = new OrderCommands.PaymentCallback(eventId, payment.orderId(), "SUCCESS", businessClock.now());
        var ready = new CountDownLatch(3);
        var start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(3);
        try {
            var first = CompletableFuture.supplyAsync(() -> racePayment(ready, start,
                    () -> unitOfWork.inTransaction(() -> orders.applyCallback(callback))), workers);
            var duplicate = CompletableFuture.supplyAsync(() -> racePayment(ready, start,
                    () -> unitOfWork.inTransaction(() -> orders.applyCallback(callback))), workers);
            var close = CompletableFuture.supplyAsync(() -> racePayment(ready, start,
                    () -> unitOfWork.inTransaction(() -> orders.closeOrder(
                            new OrderCommands.CloseOrder(payment.orderId(),
                                    new ActorContext(AccountId.of(1), Role.SYSTEM, businessClock.now()))))), workers);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            var firstResult = first.get(15, TimeUnit.SECONDS);
            var duplicateResult = duplicate.get(15, TimeUnit.SECONDS);
            var closeResult = close.get(15, TimeUnit.SECONDS);
            assertThat(firstResult).isInstanceOf(ModuleResult.Accepted.class);
            assertThat(duplicateResult).isInstanceOf(ModuleResult.Accepted.class);
            assertThat(closeResult).isInstanceOf(ModuleResult.Accepted.class);

            var orderStatus = jdbc.queryForObject("SELECT status FROM payment_order WHERE id=?",
                    String.class, payment.orderId());
            var fineStatus = jdbc.queryForObject("SELECT status FROM fine WHERE id=?",
                    String.class, payment.fineId());
            var callbackCount = jdbc.queryForObject("SELECT count(*) FROM payment_callback_event WHERE event_id=?",
                    Integer.class, eventId);
            assertThat(callbackCount).isOne();
            var recordedOutcome = jdbc.queryForObject("SELECT outcome FROM payment_callback_event WHERE event_id=?",
                    String.class, eventId);

            if ("PAID".equals(orderStatus)) {
                assertThat(fineStatus).isEqualTo("SETTLED");
                assertThat(recordedOutcome).isEqualTo("PAID");
            } else {
                assertThat(orderStatus).isEqualTo("CLOSED");
                assertThat(fineStatus).isEqualTo("DUE");
                assertThat(recordedOutcome).isEqualTo("CONFLICT_RECORDED");
            }
        } finally {
            start.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void failedDailyCloseKeepsIsolationWhenOuterIdempotentRequestRollsBack() {
        var closingDate = businessClock.today().minusDays(4);
        resetClosingControl(closingDate, "OPEN", false);
        jdbc.update("DELETE FROM closing_batch WHERE business_date BETWEEN ? AND ?",
                java.sql.Date.valueOf(closingDate), java.sql.Date.valueOf(businessClock.today().minusDays(1)));
        jdbc.update("UPDATE closing_control SET retry_budget=1 WHERE scope='GLOBAL'");
        var confirmedReader = insertAccount("READER");
        var confirmedTitle = insertTitle("Failure release", 1000);
        var reservedCopy = insertCopy(confirmedTitle, "RESERVED");
        var confirmedTx = jdbc.queryForObject("""
                INSERT INTO borrow_transaction(reader_id,title_id,copy_id,status,reservation_date,pickup_slot)
                VALUES (?,?,?,'CONFIRMED',?,'AM') RETURNING id
                """, Long.class, confirmedReader, confirmedTitle, reservedCopy,
                java.sql.Date.valueOf(closingDate));

        var waitlistReader = insertAccount("READER");
        var waitlistTitle = insertTitle("Failure assignment", 1000);
        var readyCopy = insertCopy(waitlistTitle, "SHELF_READY");
        var waitlistTx = jdbc.queryForObject("""
                INSERT INTO borrow_transaction(reader_id,title_id,status,reservation_date,pickup_slot)
                VALUES (?,?, 'WAITLIST', ?, 'AM') RETURNING id
                """, Long.class, waitlistReader, waitlistTitle,
                java.sql.Date.valueOf(closingDate.minusDays(1)));

        jdbc.execute("""
                CREATE OR REPLACE FUNCTION p1_fail_closing_success() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.status = 'SUCCEEDED' THEN
                        RAISE EXCEPTION 'P1 injected close commit failure';
                    END IF;
                    RETURN NEW;
                END;
                $$
                """);
        jdbc.execute("""
                CREATE TRIGGER p1_fail_closing_success
                BEFORE UPDATE OF status ON closing_batch
                FOR EACH ROW EXECUTE FUNCTION p1_fail_closing_success()
                """);
        try {
            IllegalStateException outerFailure = null;
            try {
                unitOfWork.inTransaction(() -> {
                    assertThat(admission.acquire("/api/v1/closing/runs")).isTrue();
                    var closeResult = closing.closeBusinessDate(
                            new ClosingCommands.ClosingCommand(BusinessDate.of(closingDate)));
                    assertThat(closeResult).isInstanceOf(ModuleResult.Rejected.class);
                    assertThat(((ModuleResult.Rejected<?>) closeResult).code()).isEqualTo("MLS-CIRC-014");
                    throw new IllegalStateException("injected outer idempotent request rollback");
                });
            } catch (IllegalStateException failure) {
                outerFailure = failure;
            }
            assertThat(outerFailure).hasMessage("injected outer idempotent request rollback");

            assertThat(jdbc.queryForObject("SELECT draining FROM closing_admission WHERE scope='GLOBAL'", Boolean.class))
                    .isTrue();
            assertThat(unitOfWork.inTransaction(() -> admission.acquire("/api/v1/ai/sessions"))).isFalse();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM closing_batch WHERE business_date > ?",
                    Integer.class, java.sql.Date.valueOf(closingDate))).isZero();
            assertThat(transactionStatus(confirmedTx)).isEqualTo("CONFIRMED");
            assertThat(copyStatus(reservedCopy)).isEqualTo("RESERVED");
            assertThat(transactionStatus(waitlistTx)).isEqualTo("WAITLIST");
            assertThat(copyStatus(readyCopy)).isEqualTo("SHELF_READY");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM outbox_event WHERE agg_type='CIRCULATION_TX' AND agg_id=?
                      AND event_type='WAITLIST_ASSIGNED'
                    """, Integer.class, waitlistTx)).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM audit_event
                    WHERE event_type='WAITLIST_ASSIGNED' AND detail->>'relatedId'=?
                    """, Integer.class, Long.toString(waitlistTx))).isZero();
            // A restart/next automatic check must preserve the exhausted persistent budget.
            dailyClosingScheduler.closePreviousBusinessDate();
            assertThat(jdbc.queryForMap("SELECT status,attempts FROM closing_batch WHERE business_date=?",
                    java.sql.Date.valueOf(closingDate))).containsEntry("status", "MANUAL")
                    .containsEntry("attempts", 2);
            assertThat(jdbc.queryForObject("SELECT draining FROM closing_admission WHERE scope='GLOBAL'", Boolean.class))
                    .isTrue();
            assertThat(unitOfWork.inTransaction(() -> admission.acquire("/api/v1/ai/sessions"))).isFalse();
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS p1_fail_closing_success ON closing_batch");
            jdbc.execute("DROP FUNCTION IF EXISTS p1_fail_closing_success()");
        }
    }

    private long confirmedForDate(LocalDate date, String label) {
        var readerId = insertAccount("READER");
        var titleId = insertTitle(label, 1000);
        var copyId = insertCopy(titleId, "RESERVED");
        return jdbc.queryForObject("""
                INSERT INTO borrow_transaction(reader_id,title_id,copy_id,status,reservation_date,pickup_slot)
                VALUES (?,?,?,'CONFIRMED',?,'AM') RETURNING id
                """, Long.class, readerId, titleId, copyId, java.sql.Date.valueOf(date));
    }

    private PaymentFixture openFineOrder(String label) {
        var readerId = insertAccount("READER");
        var titleId = insertTitle(label, 1200);
        var sourceTx = jdbc.queryForObject("""
                INSERT INTO borrow_transaction(reader_id,title_id,status,reservation_date)
                VALUES (?,?,'RETURNED',?) RETURNING id
                """, Long.class, readerId, titleId, java.sql.Date.valueOf(businessClock.today().minusDays(2)));
        var fineId = jdbc.queryForObject("""
                INSERT INTO fine(reader_id,source_tx_id,amount_cents) VALUES (?,?,1200) RETURNING id
                """, Long.class, readerId, sourceTx);
        var orderId = jdbc.queryForObject("""
                INSERT INTO payment_order(fine_id,expires_at) VALUES (?,now()+interval '15 minutes') RETURNING id
                """, Long.class, fineId);
        return new PaymentFixture(fineId, orderId);
    }

    private static <T> T racePayment(CountDownLatch ready, CountDownLatch start,
                                     java.util.function.Supplier<T> action) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("payment test start barrier timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
        return action.get();
    }

    private record PaymentFixture(long fineId, long orderId) {}

    private void resetClosingControl(LocalDate currentDate, String status, boolean draining) {
        jdbc.update("UPDATE closing_control SET \"current_date\"=?,first_business_date=?,status=? WHERE scope='GLOBAL'",
                java.sql.Date.valueOf(currentDate), java.sql.Date.valueOf(currentDate), status);
        jdbc.update("UPDATE closing_admission SET draining=? WHERE scope='GLOBAL'", draining);
    }

    private String transactionStatus(long txId) {
        return jdbc.queryForObject("SELECT status FROM borrow_transaction WHERE id=?", String.class, txId);
    }

    private boolean awaitClosingControlLockWait() throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE state='active' AND wait_event_type='Lock'
                      AND pid<>pg_backend_pid() AND query ILIKE '%closing_control%'
                    """, Integer.class) > 0) {
                return true;
            }
            Thread.onSpinWait();
        }
        return false;
    }

    private boolean awaitClosingAdmissionLock() throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE state='active' AND wait_event_type='Lock'
                      AND query LIKE 'UPDATE closing_admission SET draining=TRUE%'
                    """, Integer.class) > 0) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }

    private boolean awaitTitleReservationLockWait() throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE state='active' AND wait_event_type='Lock'
                      AND query LIKE '%pg_advisory_xact_lock%'
                    """, Integer.class) > 0) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("writer test barrier timed out");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private long insertAccount(String role) {
        return jdbc.queryForObject("""
                INSERT INTO account(username, password_hash, display_name, role)
                VALUES (?, 'x', ?, ?) RETURNING id
                """, Long.class, "p1-rule-" + role.toLowerCase() + "-" + System.nanoTime(), role, role);
    }

    private long insertOverdueLoan(long readerId, long titleId, LocalDate dueDate) {
        var copyId = insertCopy(titleId, "LENT");
        jdbc.update("""
                INSERT INTO borrow_transaction(reader_id, title_id, copy_id, status,
                                               reservation_date, due_date)
                VALUES (?, ?, ?, 'LENT', ?, ?)
                """, readerId, titleId, copyId, java.sql.Date.valueOf(dueDate), java.sql.Date.valueOf(dueDate));
        return copyId;
    }

    private long insertTitle(String title, long priceCents) {
        return jdbc.queryForObject("INSERT INTO book_title(title, price_cents) VALUES (?, ?) RETURNING id",
                Long.class, title + "-" + System.nanoTime(), priceCents);
    }

    private long insertCopy(long titleId, String status) {
        return jdbc.queryForObject("""
                INSERT INTO book_copy(title_id, copy_no, status) VALUES (?, ?, ?) RETURNING id
                """, Long.class, titleId, "p1-rule-copy-" + System.nanoTime(), status);
    }

    private LoanCommands.WalkInLoanCommand oneCopyLoan(long readerId, long titleId, long copyId,
                                                       ActorContext librarian) {
        return new LoanCommands.WalkInLoanCommand(readerId, java.util.List.of(
                new LoanCommands.WalkInItem(TitleId.of(titleId), CopyId.of(copyId))), "DESK", librarian);
    }

    private String copyStatus(long copyId) {
        return jdbc.queryForObject("SELECT status FROM book_copy WHERE id=?", String.class, copyId);
    }

    private void insertActiveReservation(long readerId, String status, LocalDate reservationDate) {
        var titleId = insertTitle("Existing reservation", 1000);
        jdbc.update("""
                INSERT INTO borrow_transaction(reader_id, title_id, status, reservation_date)
                VALUES (?, ?, ?, ?)
                """, readerId, titleId, status, java.sql.Date.valueOf(reservationDate));
    }

    private LoanCommands.LoanBatch concurrentWalkIn(CountDownLatch ready, CountDownLatch start,
                                                     ActorContext librarian, long readerId,
                                                     long titleId, long copyId) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrency test start barrier timed out");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
        return accepted(loans.lend(librarian, new LoanCommands.WalkInLoanCommand(readerId,
                java.util.List.of(new LoanCommands.WalkInItem(TitleId.of(titleId), CopyId.of(copyId))),
                "DESK", librarian)));
    }

    private static <T> T accepted(ModuleResult<T> result) {
        assertThat(result).isInstanceOf(ModuleResult.Accepted.class);
        return ((ModuleResult.Accepted<T>) result).value();
    }

    private static void assertRejected(ModuleResult<?> result, String code) {
        assertThat(result).isInstanceOf(ModuleResult.Rejected.class);
        assertThat(((ModuleResult.Rejected<?>) result).code()).isEqualTo(code);
    }
}
