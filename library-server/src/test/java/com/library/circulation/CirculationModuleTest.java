package com.library.circulation;

import com.library.contract.ModuleResult;
import com.library.contract.security.ActorContext;
import com.library.contract.model.AccountId;
import com.library.contract.model.Role;
import com.library.circulation.contract.AvailabilityQuery;
import com.library.circulation.contract.CopyCommands;
import com.library.circulation.contract.LoanCommands;
import com.library.circulation.contract.ReservationCommands;
import com.library.circulation.contract.ReturnCommands;
import com.library.circulation.contract.Transaction;
import com.library.circulation.contract.model.BusinessDate;
import com.library.circulation.contract.model.CopyId;
import com.library.catalog.contract.model.TitleId;
import com.library.circulation.contract.model.TxId;
import com.library.circulation.internal.CirculationModuleImpl;
import com.library.circulation.internal.adapter.persistence.InMemoryCirculationStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UT-CIRC-001~015（MLS-DOC-MD-0005 §3）：副本六态迁移、预约/候补、逐册借出、
 * 取书重验、人工处置、续借守卫、归还与重复归还、日历与可用性投影。
 * PostgreSQL 锁与事务交错不在本层证明（IT-CIRC）。
 */
class CirculationModuleTest {

    private final InMemoryCirculationStore store = new InMemoryCirculationStore();
    private final CirculationModuleImpl module = new CirculationModuleImpl(store);

    private final ActorContext reader = new ActorContext(AccountId.of(1L), Role.READER,
            Instant.parse("2026-09-16T08:00:00Z"));
    private final ActorContext librarian = new ActorContext(AccountId.of(2L), Role.LIBRARIAN,
            Instant.parse("2026-09-16T08:00:00Z"));
    private final TitleId title = TitleId.of(10L);

    private long shelvedCopy(String copyNo) {
        var copyId = ((ModuleResult.Accepted<CopyCommands.CopySnapshot>) module.createCopy(
                new CopyCommands.CreateCopy(title, copyNo, librarian))).value().copyId().value();
        module.maintainCopy(new CopyCommands.MaintainCopy(CopyId.of(copyId), "SHELVE", 0, librarian));
        return copyId;
    }

    // UT-CIRC-001/002 副本六态迁移：SHELVE/MAINTAIN/RESTORE/REMOVE 与非法迁移、重复副本
    @Test
    void utCirc001_002_copyLifecycle() {
        var created = ((ModuleResult.Accepted<CopyCommands.CopySnapshot>) module.createCopy(
                new CopyCommands.CreateCopy(title, "C-1", librarian))).value();
        assertThat(created.status()).isEqualTo("PENDING_SHELVING");
        assertThat(codeOf(module.createCopy(new CopyCommands.CreateCopy(title, "C-1", librarian))))
                .isEqualTo("MLS-CIRC-010");

        var shelved = maintain(created.copyId(), "SHELVE", 0);
        assertThat(shelved.status()).isEqualTo("AVAILABLE");
        var maintaining = maintain(created.copyId(), "MAINTAIN", shelved.version());
        assertThat(maintaining.status()).isEqualTo("MAINTENANCE");
        var restored = maintain(created.copyId(), "RESTORE", maintaining.version());
        assertThat(restored.status()).isEqualTo("AVAILABLE");
        var removed = maintain(created.copyId(), "REMOVE", restored.version());
        assertThat(removed.status()).isEqualTo("REMOVED");
        // 已下架不可逆（BR-0044）：REMOVE 不可再迁移
        assertThat(codeOf(module.maintainCopy(new CopyCommands.MaintainCopy(
                created.copyId(), "SHELVE", removed.version(), librarian)))).isEqualTo("MLS-CIRC-003");
        // 版本竞争（CAS）：旧版本被拒并携带 currentVersion
        var stale = (ModuleResult.Rejected<CopyCommands.CopySnapshot>) module.maintainCopy(
                new CopyCommands.MaintainCopy(created.copyId(), "SHELVE", 0, librarian));
        assertThat(stale.code()).isEqualTo("MLS-CIRC-003");
        assertThat(stale.currentVersion()).isEqualTo(removed.version());
    }

    // UT-CIRC-003/004 预约：可用绑定 CONFIRMED、重复预约 003、候补两段式（场景 3）
    @Test
    void utCirc003_004_reservationAndWaitlistTwoPhase() {
        var copyId = shelvedCopy("C-10");
        var confirmed = ((ModuleResult.Accepted<ReservationCommands.Reservation>) module.reserve(
                command(reader, title, false))).value();
        assertThat(confirmed.outcome()).isEqualTo("CONFIRMED");
        assertThat(confirmed.txId()).isNotNull();
        // 绑定副本转 RESERVED
        assertThat(store.copyById(copyId).orElseThrow().status()).isEqualTo("RESERVED");
        // 同读者同书目重复预约 → 003（BR-0020）
        assertThat(codeOf(module.reserve(command(reader, title, false)))).isEqualTo("MLS-CIRC-003");

        // 另一读者预约无可用副本 → 待确认且无业务事实（txId=null）
        var other = readerOf(3L);
        var pending = ((ModuleResult.Accepted<ReservationCommands.Reservation>) module.reserve(
                command(other, title, false))).value();
        assertThat(pending.outcome()).isEqualTo("WAITLIST_PENDING_CONFIRMATION");
        assertThat(pending.txId()).isNull();
        // 确认复送（confirmWaitlist=true）→ 入队 WAITLIST
        var enqueued = ((ModuleResult.Accepted<ReservationCommands.Reservation>) module.reserve(
                command(other, title, true))).value();
        assertThat(enqueued.outcome()).isEqualTo("WAITLIST_ENQUEUED");
        assertThat(enqueued.txId()).isNotNull();
    }

    // UT-CIRC-005/006 逐册借出：混合批次保留结果；同书已借拒借
    @Test
    void utCirc005_006_walkInMixedBatch() {
        var availableCopy = shelvedCopy("C-20");
        var emptyTitle = TitleId.of(11L);
        var batch = ((ModuleResult.Accepted<LoanCommands.LoanBatch>) module.lend(
                new LoanCommands.WalkInLoanCommand(1L,
                        List.of(new LoanCommands.WalkInItem(title, CopyId.of(availableCopy)),
                                new LoanCommands.WalkInItem(emptyTitle, null)),
                        "SELF_SERVICE", reader))).value();
        assertThat(batch.items().get(0).outcome()).isEqualTo("LENT");
        assertThat(batch.items().get(0).txId()).isNotNull();
        assertThat(batch.items().get(1).outcome()).isEqualTo("REJECTED");
        assertThat(batch.items().get(1).reasons()).contains("COPY_NOT_AVAILABLE");
        // 混合批次逐册独立：成功册的副本已 LENT
        assertThat(store.copyById(availableCopy).orElseThrow().status()).isEqualTo("LENT");
    }

    // UT-CIRC-007/008 取书：CONFIRMED 绑定一致才借出；副本不一致 004（X4 提交重验）
    @Test
    void utCirc007_008_claimRevalidatesBinding() {
        var copyId = shelvedCopy("C-30");
        var reserved = ((ModuleResult.Accepted<ReservationCommands.Reservation>) module.reserve(
                command(reader, title, false))).value();
        // 绑定不一致的副本 → 004
        assertThat(codeOf(module.claim(new LoanCommands.ClaimPickup(reserved.txId(),
                CopyId.of(999999), librarian)))).isEqualTo("MLS-CIRC-004");
        var claimed = ((ModuleResult.Accepted<Transaction>) module.claim(
                new LoanCommands.ClaimPickup(reserved.txId(), CopyId.of(copyId), librarian))).value();
        assertThat(claimed.status()).isEqualTo("LENT");
        // 重复取书 → 004（状态已迁移）
        assertThat(codeOf(module.claim(new LoanCommands.ClaimPickup(reserved.txId(),
                CopyId.of(copyId), librarian)))).isEqualTo("MLS-CIRC-004");
    }

    // UT-CIRC-009/010 人工处置：REJECT 终态；RELEASE 无副本守卫 005
    @Test
    void utCirc009_010_manualHandling() {
        var txId = store.insertTransaction(new com.library.circulation.internal.adapter.persistence.CirculationStore.NewTransaction(
                1L, 10L, null, "MANUAL_HANDLING", LocalDate.now(), "AM", null));
        var rejected = ((ModuleResult.Accepted<Transaction>) module.applyManualAction(
                new LoanCommands.ManualAction(TxId.of(txId), "REJECT", "无法到馆", librarian))).value();
        assertThat(rejected.status()).isEqualTo("NOT_BORROWED");
        assertThat(codeOf(module.applyManualAction(new LoanCommands.ManualAction(
                TxId.of(txId), "RELEASE", "重复处置", librarian)))).isEqualTo("MLS-CIRC-005");

        var manual2 = store.insertTransaction(new com.library.circulation.internal.adapter.persistence.CirculationStore.NewTransaction(
                1L, 10L, null, "MANUAL_HANDLING", LocalDate.now(), "AM", null));
        // 无可用副本 → 005 保持人工处置中
        assertThat(codeOf(module.applyManualAction(new LoanCommands.ManualAction(
                TxId.of(manual2), "RELEASE", "无副本", librarian)))).isEqualTo("MLS-CIRC-005");
        var copyId = shelvedCopy("C-40");
        var released = ((ModuleResult.Accepted<Transaction>) module.applyManualAction(
                new LoanCommands.ManualAction(TxId.of(manual2), "RELEASE", "放行", librarian))).value();
        assertThat(released.status()).isEqualTo("LENT");
        assertThat(store.copyById(copyId).orElseThrow().status()).isEqualTo("LENT");
    }

    // UT-CIRC-011/012 续借守卫：候补等待/次数上限/非本人；成功推进到期
    @Test
    void utCirc011_012_renewalGuards() {
        var copyId = shelvedCopy("C-50");
        var reserved = ((ModuleResult.Accepted<ReservationCommands.Reservation>) module.reserve(
                command(reader, title, false))).value();
        module.claim(new LoanCommands.ClaimPickup(reserved.txId(), CopyId.of(copyId), librarian));
        var renewed = ((ModuleResult.Accepted<LoanCommands.Renewal>) module.renew(
                new LoanCommands.RenewalCommand(reserved.txId(), null, reader))).value();
        assertThat(renewed.outcome()).isEqualTo("RENEWED");
        var tx = store.txById(reserved.txId().value()).orElseThrow();
        assertThat(tx.renewalCount()).isEqualTo(1);
        // 次数上限（LIMIT=1）
        assertThat(codeOf(module.renew(new LoanCommands.RenewalCommand(reserved.txId(), null, reader))))
                .isEqualTo("MLS-CIRC-012");
        // 非本人
        assertThat(codeOf(module.renew(new LoanCommands.RenewalCommand(reserved.txId(), null,
                readerOf(9L))))).isEqualTo("MLS-CIRC-012");
    }

    // UT-CIRC-013/014 归还：正常转 RETURNED/副本回架；重复归还 006；取消释放保留副本
    @Test
    void utCirc013_014_returnAndDuplicateGuard() {
        var copyId = shelvedCopy("C-60");
        var reserved = ((ModuleResult.Accepted<ReservationCommands.Reservation>) module.reserve(
                command(reader, title, false))).value();
        module.claim(new LoanCommands.ClaimPickup(reserved.txId(), CopyId.of(copyId), librarian));
        var returned = ((ModuleResult.Accepted<ReturnCommands.ReturnedCopy>) module.returnCopy(
                new ReturnCommands.ReturnCommand(CopyId.of(copyId), "INTACT", librarian))).value();
        assertThat(returned.outcome()).isEqualTo("RETURNED");
        assertThat(store.copyById(copyId).orElseThrow().status()).isEqualTo("SHELF_READY");
        // 重复归还 → 006
        assertThat(codeOf(module.returnCopy(new ReturnCommands.ReturnCommand(
                CopyId.of(copyId), "INTACT", librarian)))).isEqualTo("MLS-CIRC-006");

        // 取消 CONFIRMED 预约 → NOT_BORROWED 且保留副本释放
        var copy2 = shelvedCopy("C-61");
        var res2 = ((ModuleResult.Accepted<ReservationCommands.Reservation>) module.reserve(
                command(readerOf(5L), title, false))).value();
        var cancelled = ((ModuleResult.Accepted<Transaction>) module.cancel(
                new ReservationCommands.CancelReservation(res2.txId(), "改期", readerOf(5L)))).value();
        assertThat(cancelled.status()).isEqualTo("NOT_BORROWED");
        assertThat(store.copyById(copy2).orElseThrow().status()).isEqualTo("SHELF_READY");
    }

    // UT-CIRC-015 可用性与日历投影
    @Test
    void utCirc015_availabilityAndCalendar() {
        assertThat(((ModuleResult.Accepted<AvailabilityQuery.AvailabilitySnapshot>) module.availability(
                title, BusinessDate.of(LocalDate.now()))).value().availability()).isEqualTo("UNKNOWN");
        shelvedCopy("C-70");
        var snapshot = ((ModuleResult.Accepted<AvailabilityQuery.AvailabilitySnapshot>) module.availability(
                title, BusinessDate.of(LocalDate.now()))).value();
        assertThat(snapshot.availability()).isEqualTo("AVAILABLE");
        var calendar = ((ModuleResult.Accepted<AvailabilityQuery.ReservationCalendar>) module.calendar(
                title, BusinessDate.of(LocalDate.now()),
                BusinessDate.of(LocalDate.now().plusDays(6)))).value();
        assertThat(calendar.days()).hasSize(7);
        assertThat(calendar.days().get(0).availability()).isEqualTo("GUARANTEED");
        assertThat(codeOf(module.calendar(title, BusinessDate.of(LocalDate.now().plusDays(6)),
                BusinessDate.of(LocalDate.now())))).isEqualTo("MLS-CIRC-001");
    }

    private ReservationCommands.ReservationCommand command(ActorContext actor, TitleId titleId,
                                                           boolean confirmWaitlist) {
        return new ReservationCommands.ReservationCommand(actor.accountId().value(), titleId,
                BusinessDate.of(LocalDate.now().plusDays(1)), "AM", "ONLINE", confirmWaitlist, actor);
    }

    private ActorContext readerOf(long id) {
        return new ActorContext(AccountId.of(id), Role.READER, Instant.parse("2026-09-16T08:00:00Z"));
    }

    private CopyCommands.CopySnapshot maintain(CopyId copyId, String action, long version) {
        return ((ModuleResult.Accepted<CopyCommands.CopySnapshot>) module.maintainCopy(
                new CopyCommands.MaintainCopy(copyId, action, version, librarian))).value();
    }

    private String codeOf(ModuleResult<?> result) {
        if (result instanceof ModuleResult.Rejected<?> rejected) {
            return rejected.code();
        }
        throw new AssertionError("期望业务拒绝，实际为 Accepted: " + result);
    }
}
