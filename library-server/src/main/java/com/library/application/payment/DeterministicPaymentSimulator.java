package com.library.application.payment;

import com.library.contract.ModuleResult;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicLong;

@Component
public final class DeterministicPaymentSimulator implements PaymentSimulatorPort {
    private final AtomicLong sequence=new AtomicLong();
    @Override public ModuleResult<Simulation> schedule(Command c){if(c.orderId()<=0||!("SUCCESS".equals(c.result())||"FAILURE".equals(c.result()))||c.delayMs()<0||c.duplicateCount()<0||c.duplicateCount()>20)return ModuleResult.Rejected.of("MLS-PAY-006","模拟参数无效");return ModuleResult.Accepted.of(new Simulation(Long.toString(sequence.incrementAndGet()),"SCHEDULED"));}
}
