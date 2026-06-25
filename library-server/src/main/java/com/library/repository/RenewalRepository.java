package com.library.repository;

import com.library.entity.Renewal;
import com.library.entity.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface RenewalRepository extends JpaRepository<Renewal, Long>,
        JpaSpecificationExecutor<Renewal> {
    long countByBorrowId(Long borrowId);
    boolean existsByBorrowIdAndApproved(Long borrowId, ApprovalStatus approved);
}
