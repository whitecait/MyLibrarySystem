package com.library.integration;

import com.library.catalog.contract.model.TitleId;
import com.library.contract.ModuleResult;
import com.library.contract.model.AccountId;
import com.library.contract.model.Role;
import com.library.contract.security.ActorContext;
import com.library.contract.time.BusinessClock;
import com.library.contract.transaction.UnitOfWork;
import com.library.circulation.contract.CirculationReadSnapshots;
import com.library.circulation.contract.LoanCommands;
import com.library.circulation.contract.ReservationCommands;
import com.library.circulation.contract.ReturnCommands;
import com.library.circulation.contract.Transaction;
import com.library.circulation.contract.model.BusinessDate;
import com.library.circulation.contract.model.CopyId;
import com.library.circulation.contract.model.GuaranteeType;
import com.library.circulation.contract.model.TxId;
import com.library.circulation.internal.CirculationModuleImpl;
import com.library.circulation.internal.SettlementAllocation;
import com.library.identity.contract.model.ReaderId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Real PostgreSQL coverage for creation-time guarantee metadata and its read projection. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ITCirculationGuaranteeTypeTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            "postgres@sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired CirculationModuleImpl circulation;
    @Autowired CirculationReadSnapshots snapshots;
    @Autowired SettlementAllocation settlement;
    @Autowired UnitOfWork unitOfWork;
    @Autowired JdbcTemplate jdbc;
    @Autowired BusinessClock businessClock;

    @Test
    void creationOriginSurvivesAllocationLendingReturnAndCancellation() {
        var suffix = UUID.randomUUID().toString();
        var copySuffix = suffix.substring(0, 8);
        var today = businessClock.today();
        var readerId = seedAccount("reader", suffix, "READER");
        var librarianId = seedAccount("librarian", suffix, "LIBRARIAN");
        var reader = new ActorContext(AccountId.of(readerId), Role.READER, businessClock.now());
        var librarian = new ActorContext(AccountId.of(librarianId), Role.LIBRARIAN, businessClock.now());

        var directTitle = seedTitle("direct", suffix);
        var directCopy = seedCopy(directTitle, "direct-copy-" + copySuffix, "SHELF_READY", today);
        var direct = accepted(unitOfWork.inTransaction(() -> circulation.reserve(
                command(readerId, directTitle, today, reader))));
        assertThat(direct.outcome()).isEqualTo("CONFIRMED");
        assertGuarantee(direct.txId().value(), "DIRECT_CONFIRMATION");
        assertSnapshotGuarantee(readerId, direct.txId().value(), GuaranteeType.DIRECT_CONFIRMATION);

        var directClaim = accepted(unitOfWork.inTransaction(() -> circulation.claim(
                new LoanCommands.ClaimPickup(direct.txId(), CopyId.of(directCopy), librarian))));
        assertThat(directClaim.status()).isEqualTo("LENT");
        assertGuarantee(direct.txId().value(), "DIRECT_CONFIRMATION");
        var directReturn = accepted(unitOfWork.inTransaction(() -> circulation.returnCopy(
                new ReturnCommands.ReturnCommand(CopyId.of(directCopy), "INTACT", librarian))));
        assertThat(directReturn.outcome()).isEqualTo("RETURNED");
        assertGuarantee(direct.txId().value(), "DIRECT_CONFIRMATION");

        var waitingTitle = seedTitle("waiting", suffix);
        seedCopy(waitingTitle, "waiting-lent-" + copySuffix, "LENT", today);
        var waiting = accepted(unitOfWork.inTransaction(() -> circulation.reserve(
                command(readerId, waitingTitle, today, reader))));
        assertThat(waiting.outcome()).isEqualTo("WAITLIST_ENQUEUED");
        assertGuarantee(waiting.txId().value(), "WAITLIST");
        seedCopy(waitingTitle, "waiting-available-" + copySuffix, "SHELF_READY", today);

        assertThat(unitOfWork.inTransaction(() -> settlement.assignAndExpireDueWaitlists(today))).isOne();
        assertThat(jdbc.queryForObject("SELECT status FROM borrow_transaction WHERE id = ?",
                String.class, waiting.txId().value())).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT copy_id FROM borrow_transaction WHERE id = ?",
                Long.class, waiting.txId().value())).isNotNull();
        assertGuarantee(waiting.txId().value(), "WAITLIST");
        assertSnapshotGuarantee(readerId, waiting.txId().value(), GuaranteeType.WAITLIST);

        var assignedCopy = jdbc.queryForObject("SELECT copy_id FROM borrow_transaction WHERE id = ?",
                Long.class, waiting.txId().value());
        var waitingClaim = accepted(unitOfWork.inTransaction(() -> circulation.claim(
                new LoanCommands.ClaimPickup(waiting.txId(), CopyId.of(assignedCopy), librarian))));
        assertThat(waitingClaim.status()).isEqualTo("LENT");
        assertGuarantee(waiting.txId().value(), "WAITLIST");
        accepted(unitOfWork.inTransaction(() -> circulation.returnCopy(
                new ReturnCommands.ReturnCommand(CopyId.of(assignedCopy), "INTACT", librarian))));
        assertGuarantee(waiting.txId().value(), "WAITLIST");

        var cancelTitle = seedTitle("cancel", suffix);
        seedCopy(cancelTitle, "cancel-lent-" + copySuffix, "LENT", today);
        var cancelWaitlist = accepted(unitOfWork.inTransaction(() -> circulation.reserve(
                command(readerId, cancelTitle, today, reader))));
        assertThat(cancelWaitlist.outcome()).isEqualTo("WAITLIST_ENQUEUED");
        var cancelled = accepted(unitOfWork.inTransaction(() -> circulation.cancel(
                new ReservationCommands.CancelReservation(cancelWaitlist.txId(), "test cancellation", reader))));
        assertThat(cancelled.status()).isEqualTo("NOT_BORROWED");
        assertGuarantee(cancelWaitlist.txId().value(), "WAITLIST");
    }

    private ReservationCommands.ReservationCommand command(long readerId, long titleId,
                                                            java.time.LocalDate date, ActorContext actor) {
        return new ReservationCommands.ReservationCommand(readerId, TitleId.of(titleId),
                BusinessDate.of(date), "09:00-10:30", "DESK", false, actor);
    }

    private long seedAccount(String roleName, String suffix, String role) {
        var username = "guarantee-" + roleName + "-" + suffix;
        return jdbc.queryForObject("""
                INSERT INTO account (username, password_hash, display_name, role)
                VALUES (?, 'x', ?, ?) RETURNING id
                """, Long.class, username, username, role);
    }

    private long seedTitle(String label, String suffix) {
        return jdbc.queryForObject("""
                INSERT INTO book_title (title, price_cents) VALUES (?, 9900) RETURNING id
                """, Long.class, "guarantee-" + label + "-" + suffix);
    }

    private long seedCopy(long titleId, String copyNo, String status, java.time.LocalDate eligibleOn) {
        return jdbc.queryForObject("""
                INSERT INTO book_copy (title_id, copy_no, status, loan_eligible_on)
                VALUES (?, ?, ?, ?) RETURNING id
                """, Long.class, titleId, copyNo, status, Date.valueOf(eligibleOn));
    }

    private void assertGuarantee(long txId, String expected) {
        assertThat(jdbc.queryForObject("SELECT guarantee_type FROM borrow_transaction WHERE id = ?",
                String.class, txId)).isEqualTo(expected);
    }

    private void assertSnapshotGuarantee(long readerId, long txId, GuaranteeType expected) {
        var result = snapshots.readerTransactions(ReaderId.of(readerId), List.of("RESERVATION", "LOAN"),
                null, TxId.of(Long.MAX_VALUE), 100);
        assertThat(result).isInstanceOf(ModuleResult.Accepted.class);
        var rows = ((ModuleResult.Accepted<List<CirculationReadSnapshots.TransactionSnapshot>>) result).value();
        var snapshot = rows.stream().filter(row -> row.id().value() == txId).findFirst().orElseThrow();
        assertThat(snapshot.guaranteeType()).isEqualTo(expected);
    }

    private <T> T accepted(ModuleResult<T> result) {
        assertThat(result).isInstanceOf(ModuleResult.Accepted.class);
        return ((ModuleResult.Accepted<T>) result).value();
    }
}
