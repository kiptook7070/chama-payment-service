package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.ShareOutsPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShareOutsPaymentRepository extends JpaRepository<ShareOutsPayment, Long> {

    List<ShareOutsPayment> findAllByGroupIdAndPaymentStatusAndSoftDeleteFalse(long groupId, String paymentStatus);

    List<ShareOutsPayment> findAllByGroupIdAndPhoneNumberAndPaymentStatusAndSoftDeleteFalse(Long groupId, String phoneNumber, String paymentStatus);

    List<ShareOutsPayment> findAllByGroupIdAndSoftDeleteFalseOrderByIdAsc(long groupId);

    List<ShareOutsPayment> findAllByGroupIdAndPhoneNumberAndSoftDeleteFalseOrderByIdAsc(Long groupId, String phoneNumber);

    Page<ShareOutsPayment> findAllByGroupIdAndPaymentStatusAndSoftDeleteFalseOrderByIdAsc(Long groupId, String paymentStatus, Pageable pageable);

    ShareOutsPayment findFirstByGroupIdAndPhoneNumberAndPaymentStatusAndSoftDeleteFalseOrderByIdAsc(long groupId, String phoneNumber, String paymentStatus);

}
