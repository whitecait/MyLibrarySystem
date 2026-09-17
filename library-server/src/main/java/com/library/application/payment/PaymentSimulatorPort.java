package com.library.application.payment;

import com.library.contract.ModuleResult;

public interface PaymentSimulatorPort {
    ModuleResult<Simulation> schedule(Command command);
    record Command(long orderId,String result,long delayMs,int duplicateCount) {}
    record Simulation(String simulationId,String status) {}
}
