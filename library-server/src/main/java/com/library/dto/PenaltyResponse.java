package com.library.dto;

public class PenaltyResponse {

    private Long id;
    private String fineNo;
    private Long borrowId;
    private String readerName;
    private String studentId;
    private String bookTitle;
    private String bookIsbn;
    private String amount;
    private String fineDate;
    private String paid;
    private String addtime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFineNo() { return fineNo; }
    public void setFineNo(String fineNo) { this.fineNo = fineNo; }
    public Long getBorrowId() { return borrowId; }
    public void setBorrowId(Long borrowId) { this.borrowId = borrowId; }
    public String getReaderName() { return readerName; }
    public void setReaderName(String readerName) { this.readerName = readerName; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getBookTitle() { return bookTitle; }
    public void setBookTitle(String bookTitle) { this.bookTitle = bookTitle; }
    public String getBookIsbn() { return bookIsbn; }
    public void setBookIsbn(String bookIsbn) { this.bookIsbn = bookIsbn; }
    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }
    public String getFineDate() { return fineDate; }
    public void setFineDate(String fineDate) { this.fineDate = fineDate; }
    public String getPaid() { return paid; }
    public void setPaid(String paid) { this.paid = paid; }
    public String getAddtime() { return addtime; }
    public void setAddtime(String addtime) { this.addtime = addtime; }
}
