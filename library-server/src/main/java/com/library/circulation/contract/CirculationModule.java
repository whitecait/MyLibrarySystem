package com.library.circulation.contract;

/**
 * CIRCULATION 聚合接口：仅供 com.library.bootstrap 装配与一致性检查
 * （MLS-DOC-MD-0002 §3），不得注入应用用例。ClosingCommands 随 B4 加入。
 */
public interface CirculationModule extends AvailabilityQuery, ReservationCommands, LoanCommands,
        ReturnCommands, CopyCommands {
}
