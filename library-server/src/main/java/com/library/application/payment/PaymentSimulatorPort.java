package com.library.application.payment;

import com.library.contract.ModuleResult;

public interface PaymentSimulatorPort {
    ModuleResult<Simulation> schedule(Command command);
    /** actorId 为发起调度的操作者账户 id（成功审计的责任链主体，DEF-M6-011）。 */
    record Command(long orderId,String result,long delayMs,int duplicateCount,long actorId) {}
    record Simulation(String simulationId,String status) {}
}
