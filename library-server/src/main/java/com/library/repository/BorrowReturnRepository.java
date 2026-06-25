package com.library.repository;

import com.library.entity.BorrowReturn;
import com.library.entity.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BorrowReturnRepository extends JpaRepository<BorrowReturn, Long> {
    boolean existsByBorrowIdAndApproved(Long borrowId, ApprovalStatus approved);
}
