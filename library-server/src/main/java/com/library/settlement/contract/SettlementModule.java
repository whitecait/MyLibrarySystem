package com.library.settlement.contract;

/**
 * SETTLEMENT 聚合接口：仅供 com.library.bootstrap 装配与一致性检查
 * （MLS-DOC-MD-0002 §3）。订单/回调/退款/对账能力随 B3 加入。
 */
public interface SettlementModule extends DebtQuery, FineCommands {
}
