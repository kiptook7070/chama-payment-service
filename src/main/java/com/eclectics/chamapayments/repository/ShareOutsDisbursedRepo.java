package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.ShareOutsDisbursed;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShareOutsDisbursedRepo extends JpaRepository<ShareOutsDisbursed, Long> {

    ShareOutsDisbursed findFirstByGroupIdAndPhoneNumberAndStatusAndDisbursedTrueAndSoftDeleteFalseOrderByIdDesc(Long groupId, String phoneNumber, String status);

    List<ShareOutsDisbursed> findAllByGroupIdAndPhoneNumberAndStatusAndSoftDeleteFalseOrderByIdDesc(Long groupId, String phoneNumber, String status);

    List<ShareOutsDisbursed> findAllByGroupIdAndStatusOrderByIdDesc(long groupId, String status, Pageable pageable);

    List<ShareOutsDisbursed> findAllBySoftDeleteTrueOrderByIdDesc(Pageable pageable);

}
