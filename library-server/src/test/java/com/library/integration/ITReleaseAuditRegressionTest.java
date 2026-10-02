package com.library.integration;

import com.library.catalog.contract.MaintainTitle;
import com.library.catalog.contract.TitleChange;
import com.library.catalog.contract.TitleCommands;
import com.library.catalog.contract.TitleCriteria;
import com.library.catalog.contract.TitlePage;
import com.library.catalog.contract.TitleQuery;
import com.library.catalog.contract.TitleView;
import com.library.catalog.contract.model.TitleId;
import com.library.contract.ModuleResult;
import com.library.contract.model.AccountId;
import com.library.contract.model.PageCursor;
import com.library.contract.model.Role;
import com.library.contract.security.ActorContext;
import com.library.contract.security.Visibility;
import com.library.delivery.contract.NotificationCommands;
import com.library.delivery.contract.NotificationQuery;
import com.library.query.contract.MyBusinessQuery;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Real PostgreSQL regression for the independently audited release defects. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ITReleaseAuditRegressionTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            "postgres@sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3");
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("library.closing.daily-enabled", () -> false);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired TitleCommands titles;
    @Autowired TitleQuery catalog;
    @Autowired NotificationQuery notifications;
    @Autowired NotificationCommands reads;
    @Autowired MyBusinessQuery business;

    @Test
    void categoryOnlyUpdateDoesNotChangePublisher() { updateAndReadBack("category-new", null); }
    @Test
    void publisherOnlyUpdateDoesNotChangeCategory() { updateAndReadBack(null, "publisher-new"); }
    @Test
    void simultaneousMetadataUpdateKeepsColumnsAndCategorySearchConsistent() {
        updateAndReadBack("category-new", "publisher-new");
    }

    private void updateAndReadBack(String category, String publisher) {
        var made = value(titles.maintain(new MaintainTitle("CREATE", null, UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                "audited title", List.of("author"), "category-original", "publisher-original",
                2020, 100L, null, null)));
        value(titles.maintain(new MaintainTitle("UPDATE", made.titleId(), null, null, null,
                category, publisher, null, null, made.version(), null)));
        var current = value(catalog.title(TitleId.of(made.titleId()), Visibility.PUBLIC));
        assertThat(current.category()).isEqualTo(category == null ? "category-original" : category);
        assertThat(current.publisher()).isEqualTo(publisher == null ? "publisher-original" : publisher);
        var found = value(catalog.search(new TitleCriteria(null, current.category(), PageCursor.first()),
                Visibility.PUBLIC));
        assertThat(found.items()).anyMatch(row -> row.titleId() == made.titleId());
    }

    @Test
    void notificationPagingUsesDatesAndTimeWithIdTieBreakRatherThanInsertionId() {
        long owner = account();
        long far = note(owner, LocalDate.of(2026, 10, 8), "2026-10-01T00:00:00Z");
        long near = note(owner, LocalDate.of(2026, 10, 2), "2026-10-01T00:00:00Z");
        long tied = note(owner, LocalDate.of(2026, 10, 2), "2026-10-01T00:00:00Z");
        var actor = actor(owner);
        var first = page(actor, false, null);
        assertThat(first.items()).extracting(NotificationQuery.Notification::notificationId).containsExactly(near);
        var second = page(actor, false, first.nextCursor());
        assertThat(second.items()).extracting(NotificationQuery.Notification::notificationId).containsExactly(tied);
        var third = page(actor, false, second.nextCursor());
        assertThat(third.items()).extracting(NotificationQuery.Notification::notificationId).containsExactly(far);
        assertThat(third.nextCursor()).isNull();
    }

    @Test
    void unreadPagingKeepsCursorPositionAfterMarkReadAndNeverResolvesAnotherReadersCursor() {
        long owner = account();long foreign = account();
        long far = note(owner, LocalDate.of(2026, 10, 8), "2026-10-01T00:00:00Z");
        long near = note(owner, LocalDate.of(2026, 10, 2), "2026-10-01T00:00:00Z");
        long other = note(foreign, LocalDate.of(2026, 10, 1), "2026-10-01T00:00:00Z");
        var first = page(actor(owner), true, null);
        value(reads.markRead(near, actor(owner)));
        assertThat(page(actor(owner), true, first.nextCursor()).items())
                .extracting(NotificationQuery.Notification::notificationId).containsExactly(far);
        assertThat(page(actor(owner), false, Long.toString(other)).items()).isEmpty();
    }

    @Test
    void combinedQueryComposesOnlyOwnedSnapshotsAndPreservesFilteringAndPagination() {
        long owner = account();long other = account();
        long title = jdbc.queryForObject("INSERT INTO book_title(isbn,title,price_cents) VALUES (?, 'snapshot title', 100) RETURNING id",
                Long.class, UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        long old = transaction(owner, title, "RETURNED");
        long current = transaction(owner, title, "LENT");
        transaction(other, title, "LENT");
        var first = value(business.myBusiness(new MyBusinessQuery.MyBusinessCriteria("LOAN", null,
                PageCursor.of(null, 1)), actor(owner)));
        assertThat(first.items()).extracting(MyBusinessQuery.BusinessItem::id).containsExactly(current);
        assertThat(first.items().get(0).title()).isEqualTo("snapshot title");
        var second = value(business.myBusiness(new MyBusinessQuery.MyBusinessCriteria("LOAN", null,
                PageCursor.of(first.nextCursor(), 1)), actor(owner)));
        assertThat(second.items()).extracting(MyBusinessQuery.BusinessItem::id).containsExactly(old);
        var filtered = value(business.myBusiness(new MyBusinessQuery.MyBusinessCriteria("LOAN", "RETURNED",
                PageCursor.first()), actor(owner)));
        assertThat(filtered.items()).extracting(MyBusinessQuery.BusinessItem::id).containsExactly(old);
        jdbc.update("INSERT INTO fine(reader_id,source_tx_id,amount_cents) VALUES (?,?,125)",owner,old);
        var fines = value(business.myBusiness(new MyBusinessQuery.MyBusinessCriteria("FINE", "UNPAID",
                PageCursor.first()), actor(owner)));
        assertThat(fines.items()).singleElement().satisfies(row -> {
            assertThat(row.status()).isEqualTo("UNPAID");assertThat(row.amountCents()).isEqualTo(125L);
        });
    }

    private long account() {
        return jdbc.queryForObject("INSERT INTO account(username,password_hash,display_name,role) VALUES (?, 'test-hash', 'reader', 'READER') RETURNING id",
                Long.class, UUID.randomUUID().toString().replace("-", "").substring(0, 16));
    }
    private long note(long reader, LocalDate date, String created) {
        return jdbc.queryForObject("INSERT INTO notification(reader_id,kind,biz_ref,reservation_date,created_at) VALUES (?,'VISIT',?,?,?::timestamptz) RETURNING id",
                Long.class, reader, Math.abs(UUID.randomUUID().getMostSignificantBits()), java.sql.Date.valueOf(date), created);
    }
    private long transaction(long reader, long title, String status) {
        return jdbc.queryForObject("INSERT INTO borrow_transaction(reader_id,title_id,status,reservation_date,pickup_slot) VALUES (?,?,?,CURRENT_DATE,'AM') RETURNING id",
                Long.class, reader,title,status);
    }
    private ActorContext actor(long id) { return new ActorContext(AccountId.of(id), Role.READER, Instant.now()); }
    private <T> T value(ModuleResult<T> result) {
        assertThat(result).isInstanceOf(ModuleResult.Accepted.class);
        return ((ModuleResult.Accepted<T>) result).value();
    }
    private NotificationQuery.NotificationPage page(ActorContext actor, boolean unread, String cursor) {
        return value(notifications.notifications(new NotificationQuery.NotificationCriteria(unread,
                PageCursor.of(cursor, 1)), actor));
    }
}
