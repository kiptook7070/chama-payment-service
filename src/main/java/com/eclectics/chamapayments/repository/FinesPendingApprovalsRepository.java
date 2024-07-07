package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.FinesPendingApprovals;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FinesPendingApprovalsRepository extends JpaRepository<FinesPendingApprovals, Long> {
    FinesPendingApprovals findByIdAndGroupIdAndApprovedFalseAndPendingTrueAndSoftDeleteFalseOrderByIdDesc(long id, long groupId);

    Page<FinesPendingApprovals> findAllByGroupIdAndApprovedFalseAndPendingTrueAndSoftDeleteFalseOrderByIdDesc(long groupId, Pageable pageable);

}
