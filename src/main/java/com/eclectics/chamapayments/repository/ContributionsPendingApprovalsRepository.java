package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.ContributionsPendingApprovals;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ContributionsPendingApprovalsRepository extends JpaRepository<ContributionsPendingApprovals, Long> {

    Optional<ContributionsPendingApprovals> findByIdAndSoftDeleteFalseAndApprovalProcessedFalse(Long id);

    Page<ContributionsPendingApprovals> findAllByGroupIdAndSoftDeleteFalseAndPendingTrueOrderByIdDesc(long groupId, Pageable pageable);
}
