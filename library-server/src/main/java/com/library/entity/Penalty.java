package com.library.entity;

import com.library.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "penalties")
public class Penalty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fine_no")
    private String fineNo;

    @Column(name = "borrow_id", nullable = false)
    private Long borrowId;

    @Column(name = "amount", precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "fine_date")
    private LocalDate fineDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "paid", length = 8)
    private PaymentStatus paid;

    @Column(name = "addtime", updatable = false)
    private LocalDateTime addtime;

    @PrePersist
    protected void onCreate() { this.addtime = LocalDateTime.now(); }

    public Penalty() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFineNo() { return fineNo; }
    public void setFineNo(String fineNo) { this.fineNo = fineNo; }
    public Long getBorrowId() { return borrowId; }
    public void setBorrowId(Long borrowId) { this.borrowId = borrowId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getFineDate() { return fineDate; }
    public void setFineDate(LocalDate fineDate) { this.fineDate = fineDate; }
    public PaymentStatus getPaid() { return paid; }
    public void setPaid(PaymentStatus paid) { this.paid = paid; }
    public LocalDateTime getAddtime() { return addtime; }
}
