package com.library.entity;

import com.library.entity.enums.ApprovalStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "renewals")
public class Renewal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "borrow_id", nullable = false)
    private Long borrowId;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "renewal_date")
    private LocalDateTime renewalDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "approved", length = 8)
    private ApprovalStatus approved;

    @Column(name = "reply", length = 500)
    private String reply;

    @Column(name = "addtime", updatable = false)
    private LocalDateTime addtime;

    @PrePersist
    protected void onCreate() { this.addtime = LocalDateTime.now(); }

    public Renewal() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBorrowId() { return borrowId; }
    public void setBorrowId(Long borrowId) { this.borrowId = borrowId; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public LocalDateTime getRenewalDate() { return renewalDate; }
    public void setRenewalDate(LocalDateTime renewalDate) { this.renewalDate = renewalDate; }
    public ApprovalStatus getApproved() { return approved; }
    public void setApproved(ApprovalStatus approved) { this.approved = approved; }
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
    public LocalDateTime getAddtime() { return addtime; }
}
