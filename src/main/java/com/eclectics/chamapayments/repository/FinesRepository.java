package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.Fines;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FinesRepository extends JpaRepository<Fines, Long> {

    List<Fines> findByMemberIdAndGroupIdAndPaymentStatusAndSoftDeleteFalseOrderByIdDesc(Long memberId, Long groupId, String paymentStatus);

    @Query(value = "select * from fines_tbl where group_id=:groupId and member_id=:memberId and payment_status='PAYMENT_PENDING' order by id fetch next 1 rows only", nativeQuery = true)
    Fines getMemberFineInGroup(long groupId, long memberId);

    List<Fines> findFinesByGroupIdAndPaymentStatusAndSoftDeleteFalseOrderByIdDesc(Long groupId, String paymentStatus);

    Fines findByIdAndGroupId(long fineId, long groupId);

    @Query(value = "select * from fines_tbl where group_id=:groupId and id=:fineId and payment_status='PAYMENT_PENDING' order by id desc", nativeQuery = true)
    Optional<Fines> getFineFined(long groupId, long fineId);

    List<Fines> findAllByGroupIdAndMemberIdAndSoftDeleteFalseOrderByIdAsc(long groupId, long memberId);

    Fines findFirstByIdAndGroupIdAndPaymentStatusAndSoftDeleteFalse(long id, long groupId, String paymentStatus);

}
