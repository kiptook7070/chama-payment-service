package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.ShareOuts;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShareOutsRepository extends JpaRepository<ShareOuts, Long> {


    ShareOuts findFirstByGroupIdAndPhoneNumberAndMonthAndPaymentStatusAndSoftDeleteFalse(long groupId, String phoneNumber, String month, String paymentStatus);

    @Query(value = "select * from share_outs_tbl where group_id=:groupId and phone_number=:phoneNumber and month=:month and executed='N' and member='Y' and soft_delete=0 order by id desc fetch next 1 rows only", nativeQuery = true)
    Optional<ShareOuts> getUnExecutedSharesPerMonthForMember(long groupId, String phoneNumber, String month);


    @Query(value = "select * from share_outs_tbl where month=:month and shar_out_status='ACTIVE' and payment_status='PAYMENT_SUCCESS' and executed='N' and member='Y' and soft_delete=0 order by id desc", nativeQuery = true)
    List<ShareOuts> unExecutedSharesOut(String month);


    List<ShareOuts> findAllByGroupIdAndPhoneNumberAndSoftDeleteFalseOrderByIdAsc(long groupId, String phoneNumber);

    List<ShareOuts> findAllByGroupIdAndPhoneNumberAndPaymentStatusAndSoftDeleteFalse(long groupId, String phoneNumber, String paymentStatus);
}
