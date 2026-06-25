package com.library.dto;

import jakarta.validation.constraints.NotNull;

public class RenewalRequest {

    @NotNull(message = "借阅记录ID不能为空")
    private Long borrowId;

    private Integer durationDays;  // optional, default from config

    public Long getBorrowId() { return borrowId; }
    public void setBorrowId(Long borrowId) { this.borrowId = borrowId; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
}
