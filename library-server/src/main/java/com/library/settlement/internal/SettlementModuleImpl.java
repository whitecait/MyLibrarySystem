package com.library.settlement.internal;

import com.library.contract.ModuleResult;
import com.library.settlement.contract.DebtQuery;
import com.library.settlement.contract.FineCommands;
import com.library.settlement.contract.model.FineId;
import com.library.settlement.contract.model.MoneyCents;
import com.library.settlement.internal.adapter.persistence.SettlementStore;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * SETTLEMENT 模块实现（B2：欠费快照与归还罚款形成；订单/回调/退款/对账随 B3）。
 */
@Component
public class SettlementModuleImpl implements DebtQuery, FineCommands {

    private final SettlementStore store;

    public SettlementModuleImpl(SettlementStore store) {
        this.store = store;
    }

    @Override
    public ModuleResult<DebtSnapshot> debtFor(com.library.identity.contract.model.ReaderId readerId) {
        var summary = store.debtFor(readerId.value())
                .orElse(new SettlementStore.DebtSummary(0, 0));
        return ModuleResult.Accepted.of(new DebtSnapshot(readerId.value(),
                summary.dueFineCount(), summary.dueCents()));
    }

    @Override
    public ModuleResult<Fine> createFine(FineFromReturn command) {
        if (command.amount().value() < 0) {
            return ModuleResult.Rejected.of(com.library.contract.error.StableErrorCode.MLS_PAY_007.code(),
                    "罚款金额不可为负");
        }
        var existing = store.fineBySourceTx(command.sourceTxId());
        if (existing.isPresent()) {
            // 同归还重复计罚幂等吸收（BR-0019 数据库唯一约束 + 用例层返回首笔）
            return ModuleResult.Accepted.of(toFine(existing.get()));
        }
        store.insertFine(new SettlementStore.NewFine(command.readerId(),
                command.sourceTxId(), command.amount().value(), command.reason()));
        return ModuleResult.Accepted.of(toFine(store.fineBySourceTx(command.sourceTxId()).orElseThrow()));
    }

    private Fine toFine(SettlementStore.FineRow row) {
        // DB DUE → API UNPAID（枚举三方一致，OD-0002 §17）
        return new Fine(FineId.of(row.id()), row.readerId(), row.amountCents(),
                "DUE".equals(row.status()) ? "UNPAID" : row.status(), Instant.now());
    }
}
