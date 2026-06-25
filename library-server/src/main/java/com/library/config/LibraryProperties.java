package com.library.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "library")
public class LibraryProperties {

    private Borrow borrow = new Borrow();
    private Fine fine = new Fine();
    private Renewal renewal = new Renewal();

    public Borrow getBorrow() { return borrow; }
    public void setBorrow(Borrow borrow) { this.borrow = borrow; }
    public Fine getFine() { return fine; }
    public void setFine(Fine fine) { this.fine = fine; }
    public Renewal getRenewal() { return renewal; }
    public void setRenewal(Renewal renewal) { this.renewal = renewal; }

    public static class Borrow {
        private int maxLimit = 5;
        private int defaultDurationDays = 30;
        private int autoApproveDuration = 30;
        private int maxDurationCap = 60;
        public int getMaxLimit() { return maxLimit; }
        public void setMaxLimit(int maxLimit) { this.maxLimit = maxLimit; }
        public int getDefaultDurationDays() { return defaultDurationDays; }
        public void setDefaultDurationDays(int defaultDurationDays) { this.defaultDurationDays = defaultDurationDays; }
        public int getAutoApproveDuration() { return autoApproveDuration; }
        public void setAutoApproveDuration(int autoApproveDuration) { this.autoApproveDuration = autoApproveDuration; }
        public int getMaxDurationCap() { return maxDurationCap; }
        public void setMaxDurationCap(int maxDurationCap) { this.maxDurationCap = maxDurationCap; }
    }

    public static class Fine {
        private BigDecimal ratePerDay = new BigDecimal("1.0");
        public BigDecimal getRatePerDay() { return ratePerDay; }
        public void setRatePerDay(BigDecimal ratePerDay) { this.ratePerDay = ratePerDay; }
    }

    public static class Renewal {
        private int autoApproveCount = 1;
        public int getAutoApproveCount() { return autoApproveCount; }
        public void setAutoApproveCount(int autoApproveCount) { this.autoApproveCount = autoApproveCount; }
    }
}
