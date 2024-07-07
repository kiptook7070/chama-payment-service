package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.ShareOutsPendingDisbursement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShareOutsPendingDisbursementRepo extends JpaRepository<ShareOutsPendingDisbursement, Long> {

    List<ShareOutsPendingDisbursement> findAllByGroupIdAndPhoneNumberAndStatusAndPendingTrueAndSoftDeleteFalseOrderByCreatedOnAsc(Long groupId, String phoneNumber, String status);

    ShareOutsPendingDisbursement findFirstByGroupIdAndPhoneNumberAndPendingTrueAndStatusAndSoftDeleteFalseOrderByIdDesc(Long groupId, String phoneNumber, String status);

    List<ShareOutsPendingDisbursement> findAllByGroupIdAndPendingTrueAndStatusAndSoftDeleteFalseOrderByIdDesc(Long groupId, String status);

    List<ShareOutsPendingDisbursement> findAllByStatusAndPendingTrueAndSoftDeleteFalseOrderByCreatedOnAsc(String status);

}
