package com.library.repository;

import com.library.entity.Borrow;
import com.library.entity.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BorrowRepository extends JpaRepository<Borrow, Long> {

    List<Borrow> findByReaderId(Long readerId);

    long countByReaderIdAndApproved(Long readerId, ApprovalStatus approved);

    List<Borrow> findByBookId(Long bookId);

    long countByBookIdIn(List<Long> bookIds);

    boolean existsByReaderIdAndBookIdAndApproved(Long readerId, Long bookId, ApprovalStatus approved);
}
