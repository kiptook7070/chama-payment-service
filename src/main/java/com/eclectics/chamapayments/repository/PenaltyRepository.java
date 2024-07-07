package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.Penalty;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PenaltyRepository extends JpaRepository<Penalty, Long> {
    List<Penalty> findAllByUserId(Long userId);


    Penalty findByUserIdAndSchedulePaymentId(Long userId, String scheduleId);

    Penalty findByTransactionId(String transactionId);

    List<Penalty> findByUserIdAndContributionId(Long userId, Long contributionId);
}
