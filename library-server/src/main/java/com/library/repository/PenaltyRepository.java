package com.library.repository;

import com.library.entity.Penalty;
import com.library.entity.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PenaltyRepository extends JpaRepository<Penalty, Long>,
        JpaSpecificationExecutor<Penalty> {

    @Query("SELECT COUNT(p) FROM Penalty p JOIN Borrow b ON p.borrowId = b.id " +
           "WHERE b.readerId = :readerId AND p.paid = :paid")
    long countByReaderIdAndPaid(@Param("readerId") Long readerId, @Param("paid") PaymentStatus paid);
}
