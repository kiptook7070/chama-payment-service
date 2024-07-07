package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.OtherChannelsBalances;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OtherChannelsBalancesRepository extends JpaRepository<OtherChannelsBalances, Long> {

    Page<OtherChannelsBalances> findAllByGroupIdAndCreditAmountGreaterThanAndAmountDepletedFalseAndSoftDeleteFalseOrderByIdAsc(Long groupId, Double creditAmount, Pageable pageable);

    Page<OtherChannelsBalances> findAllByGroupIdAndSoftDeleteFalseOrderByIdAsc(Long groupId, Pageable pageable);

    List<OtherChannelsBalances> findAllByGroupIdAndCreditAmountGreaterThanAndAmountDepletedFalseAndSoftDeleteFalseOrderByIdAsc(long groupId, double creditAmount);

    OtherChannelsBalances findFirstByGroupIdAndTransactionIdAndSoftDeleteIsFalseOrderByIdAsc(long groupId, String transactionId);

    OtherChannelsBalances findFirstByIdAndGroupIdAndSoftDeleteFalseOrderByIdAsc(long id, long groupId);

    OtherChannelsBalances findFirstByGroupIdAndTransactionIdAndSoftDeleteFalse(long groupId, String transactionId);

}
