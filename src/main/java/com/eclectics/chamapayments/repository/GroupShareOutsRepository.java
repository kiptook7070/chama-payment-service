package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.GroupShareOuts;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupShareOutsRepository extends JpaRepository<GroupShareOuts, Long> {
    GroupShareOuts findFirstByGroupIdAndSoftDeleteFalse(Long groupId);

    GroupShareOuts findFirstByGroupIdAndWalletAccountAndSoftDeleteFalse(long groupId, String walletAccount);
}
