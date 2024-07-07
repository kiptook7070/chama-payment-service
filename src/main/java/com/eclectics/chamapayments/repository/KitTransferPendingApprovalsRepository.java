package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.KitTransferPendingApprovals;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KitTransferPendingApprovalsRepository extends JpaRepository<KitTransferPendingApprovals,Long> {
    KitTransferPendingApprovals findKitTransferPendingApprovalsByIdAndSoftDeleteFalseAndPendingTrue(long id);
    Page<KitTransferPendingApprovals> findAllByGroupIdAndPendingTrueAndSoftDeleteFalseOrderByIdDesc(long groupId, Pageable pageable);

}
