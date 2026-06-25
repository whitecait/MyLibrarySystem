package com.library.dto;

public class BorrowResponse {

    private Long id;
    private Long readerId;
    private String readerName;
    private String studentId;
    private Long bookId;
    private String bookTitle;
    private String bookIsbn;
    private String borrowDate;
    private String dueDate;
    private String approved;
    private String reply;
    private String addtime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getReaderId() { return readerId; }
    public void setReaderId(Long readerId) { this.readerId = readerId; }
    public String getReaderName() { return readerName; }
    public void setReaderName(String readerName) { this.readerName = readerName; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public String getBookTitle() { return bookTitle; }
    public void setBookTitle(String bookTitle) { this.bookTitle = bookTitle; }
    public String getBookIsbn() { return bookIsbn; }
    public void setBookIsbn(String bookIsbn) { this.bookIsbn = bookIsbn; }
    public String getBorrowDate() { return borrowDate; }
    public void setBorrowDate(String borrowDate) { this.borrowDate = borrowDate; }
    public String getDueDate() { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }
    public String getApproved() { return approved; }
    public void setApproved(String approved) { this.approved = approved; }
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
    public String getAddtime() { return addtime; }
    public void setAddtime(String addtime) { this.addtime = addtime; }
}
