package com.library.integration;

import com.library.application.loan.LoanUseCases;
import com.library.bootstrap.OutboxDispatcher;
import com.library.catalog.contract.model.TitleId;
import com.library.circulation.contract.ClosingCommands;
import com.library.circulation.contract.CopyCommands;
import com.library.circulation.contract.ReservationCommands;
import com.library.circulation.contract.model.BusinessDate;
import com.library.circulation.contract.model.CopyId;
import com.library.contract.ModuleResult;
import com.library.contract.model.AccountId;
import com.library.contract.model.PageCursor;
import com.library.contract.model.Role;
import com.library.contract.security.ActorContext;
import com.library.contract.time.BusinessClock;
import com.library.delivery.contract.NotificationQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DEF-M6-012 端到端通知链：业务操作 → Outbox 事件 → 分发器投递 → 通知可查询。
 * 覆盖维护完成刷新结算的分配（WAITLIST_ASSIGNED／到馆通知）、闭馆结算的分配与到期清退
 * （WAITLIST_ASSIGNED／WAITLIST_EXPIRED）、以及崩溃后重投的单次业务效果。
 * 后台轮询在测试中以 `library.outbox.dispatch-enabled=false` 关闭，测试显式调用分发器。
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ITDefM6012NotificationChainTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            "postgres@sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired LoanUseCases loans;
    @Autowired CopyCommands copies;
    @Autowired ClosingCommands closing;
    @Autowired NotificationQuery notifications;
    @Autowired OutboxDispatcher dispatcher;
    @Autowired JdbcTemplate jdbc;
    @Autowired BusinessClock businessClock;

    // 维护完成刷新结算：SHELVE 使副本回可借 → 分配候补队头 → 通知可见
    @Test
    void defM6012_maintenanceCompletionAssignsWaitlistAndNotificationIsVisible() {
        var reader = seedAccount("def012-nc-a-" + System.nanoTime(), "READER");
        var title = seedTitle("m6012a-");
        var copyId = seedCopy(title, "DEF012-NC-A1", "PENDING_SHELVING");
        var readerActor = actor(reader, Role.READER);

        var reserved = reserve(readerActor, reader, title, businessClock.today().plusDays(1), true);
        assertThat(reserved.outcome()).isEqualTo("WAITLIST_ENQUEUED");
        assertThat(notesOf(reader)).isEmpty();

        var shelved = copies.maintainCopy(new CopyCommands.MaintainCopy(
                CopyId.of(copyId), "SHELVE", 0, actor(seedAccount(
                "def012-nc-lib-" + System.nanoTime(), "LIBRARIAN"), Role.LIBRARIAN)));
        assertThat(((ModuleResult.Accepted<CopyCommands.CopySnapshot>) shelved).value().status())
                .isEqualTo("AVAILABLE");

        assertThat(dispatcher.dispatchPending()).isPositive();

        var assigned = jdbc.queryForMap(
                "SELECT status, copy_id FROM borrow_transaction WHERE id=?", reserved.txId().value());
        assertThat(assigned.get("status")).isEqualTo("CONFIRMED");
        assertThat(((Number) assigned.get("copy_id")).longValue()).isEqualTo(copyId);
        assertThat(jdbc.queryForObject("SELECT status FROM book_copy WHERE id=?", String.class, copyId))
                .isEqualTo("RESERVED");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM outbox_event
                WHERE agg_id=? AND event_type='WAITLIST_ASSIGNED' AND published_at IS NOT NULL
                """, Integer.class, reserved.txId().value())).isEqualTo(1);

        var notes = notesOf(reader);
        assertThat(notes).singleElement().satisfies(note -> {
            assertThat(note.type()).isEqualTo("VISIT");
            assertThat(note.relatedTxId()).isEqualTo(reserved.txId().value());
        });
    }

    // 闭馆结算：未取书 CONFIRMED 释放的副本分配给候补队头（到馆通知），无可分配副本者清退（无需到馆通知）
    @Test
    void defM6012_closingAllocatesReleasedCopyAndExpiresUnassignedWaitlist() {
        var date = LocalDate.now().plusDays(1);
        var readerA = seedAccount("def012-nc-conf-" + System.nanoTime(), "READER");
        var readerB = seedAccount("def012-nc-head-" + System.nanoTime(), "READER");
        var readerC = seedAccount("def012-nc-past-" + System.nanoTime(), "READER");
        var titleA = seedTitle("m6012b-");
        var titleB = seedTitle("m6012c-");
        var copyA = seedCopy(titleA, "DEF012-NC-B1", "RESERVED");
        seedCopy(titleB, "DEF012-NC-C1", "MAINTENANCE");
        var confirmedTx = seedTransaction(readerA, titleA, copyA, "CONFIRMED", date);
        var headTx = seedTransaction(readerB, titleA, null, "WAITLIST", date);
        var expiredTx = seedTransaction(readerC, titleB, null, "WAITLIST", date);

        var run = closing.closeBusinessDate(new ClosingCommands.ClosingCommand(BusinessDate.of(date)));
        assertThat(run).isInstanceOf(ModuleResult.Accepted.class);

        assertThat(jdbc.queryForObject("SELECT status FROM borrow_transaction WHERE id=?",
                String.class, confirmedTx)).isEqualTo("NOT_BORROWED");
        // 队头获分配：事务转已确认并绑定被释放的副本
        assertThat(jdbc.queryForMap("SELECT status, copy_id FROM borrow_transaction WHERE id=?",
                headTx).get("status")).isEqualTo("CONFIRMED");
        assertThat(((Number) jdbc.queryForMap("SELECT copy_id FROM borrow_transaction WHERE id=?",
                headTx).get("copy_id")).longValue()).isEqualTo(copyA);
        assertThat(jdbc.queryForObject("SELECT status FROM book_copy WHERE id=?", String.class, copyA))
                .isEqualTo("RESERVED");
        // 无可分配副本者清退，闭馆后无到期滞留
        assertThat(jdbc.queryForObject("SELECT status FROM borrow_transaction WHERE id=?",
                String.class, expiredTx)).isEqualTo("NOT_BORROWED");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM borrow_transaction
                WHERE status='WAITLIST' AND reservation_date<=?
                """, Integer.class, java.sql.Date.valueOf(date))).isZero();

        assertThat(dispatcher.dispatchPending()).isPositive();

        assertThat(notesOf(readerB)).singleElement().satisfies(note -> {
            assertThat(note.type()).isEqualTo("VISIT");
            assertThat(note.relatedTxId()).isEqualTo(headTx);
        });
        assertThat(notesOf(readerC)).singleElement().satisfies(note -> {
            assertThat(note.type()).isEqualTo("NO_VISIT");
            assertThat(note.relatedTxId()).isEqualTo(expiredTx);
        });
    }

    // 崩溃后重投：清除 published_at 再次投递，消费者回执保证单次业务效果
    @Test
    void defM6012_redeliveryAfterCrashKeepsSingleNotificationEffect() {
        var reader = seedAccount("def012-nc-crash-" + System.nanoTime(), "READER");
        var title = seedTitle("m6012d-");
        var copyId = seedCopy(title, "DEF012-NC-D1", "PENDING_SHELVING");
        var readerActor = actor(reader, Role.READER);
        var reserved = reserve(readerActor, reader, title, businessClock.today().plusDays(1), true);
        copies.maintainCopy(new CopyCommands.MaintainCopy(CopyId.of(copyId), "SHELVE", 0,
                actor(seedAccount("def012-nc-lib2-" + System.nanoTime(), "LIBRARIAN"), Role.LIBRARIAN)));
        assertThat(dispatcher.dispatchPending()).isPositive();
        assertThat(notesOf(reader)).hasSize(1);

        var eventId = jdbc.queryForObject("""
                SELECT id FROM outbox_event WHERE agg_id=? AND event_type='WAITLIST_ASSIGNED'
                """, Long.class, reserved.txId().value());
        // 模拟「消费已生效但 publish 标记未落地即崩溃」
        jdbc.update("UPDATE outbox_event SET published_at=NULL WHERE id=?", eventId);
        assertThat(dispatcher.dispatchPending()).isPositive();

        assertThat(notesOf(reader)).hasSize(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM event_consumer_receipt WHERE consumer_id='notification' AND event_id=?
                """, Integer.class, eventId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM outbox_event WHERE id=? AND published_at IS NOT NULL
                """, Integer.class, eventId)).isEqualTo(1);
    }

    private ReservationCommands.Reservation reserve(ActorContext readerActor, long reader, long title,
                                                    LocalDate date, boolean confirmWaitlist) {
        var result = loans.reserve(readerActor, new ReservationCommands.ReservationCommand(
                reader, TitleId.of(title), BusinessDate.of(date), "09:00-10:30", "ONLINE", confirmWaitlist,
                readerActor));
        return ((ModuleResult.Accepted<ReservationCommands.Reservation>) result).value();
    }

    private List<NotificationQuery.Notification> notesOf(long reader) {
        var result = notifications.notifications(
                new NotificationQuery.NotificationCriteria(false, PageCursor.first()),
                actor(reader, Role.READER));
        return ((ModuleResult.Accepted<NotificationQuery.NotificationPage>) result).value().items();
    }

    private long seedAccount(String username, String role) {
        return jdbc.queryForObject("""
                INSERT INTO account (username, password_hash, display_name, role)
                VALUES (?, 'x', ?, ?) RETURNING id
                """, Long.class, username, username, role);
    }

    private long seedTitle(String isbnPrefix) {
        return jdbc.queryForObject("""
                INSERT INTO book_title (isbn, title, price_cents) VALUES (?, 'DEF-M6-012 书目', 9900)
                RETURNING id
                """, Long.class, isbnPrefix + (System.nanoTime() % 100_000_000L));
    }

    private long seedCopy(long titleId, String copyNo, String status) {
        return jdbc.queryForObject("""
                INSERT INTO book_copy (title_id, copy_no, status) VALUES (?, ?, ?) RETURNING id
                """, Long.class, titleId, copyNo, status);
    }

    private long seedTransaction(long readerId, long titleId, Long copyId, String status,
                                 LocalDate reservationDate) {
        return jdbc.queryForObject("""
                INSERT INTO borrow_transaction (reader_id, title_id, copy_id, status,
                                                reservation_date, pickup_slot, queued_at)
                VALUES (?, ?, ?, ?, ?, 'AM', now())
                RETURNING id
                """, Long.class, readerId, titleId, copyId, status,
                java.sql.Date.valueOf(reservationDate));
    }

    private static ActorContext actor(long id, Role role) {
        return new ActorContext(AccountId.of(id), role, Instant.parse("2026-09-22T08:00:00Z"));
    }
}
