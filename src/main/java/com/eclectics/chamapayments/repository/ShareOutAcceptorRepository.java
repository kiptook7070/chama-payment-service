package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.ShareOutAcceptor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareOutAcceptorRepository extends JpaRepository<ShareOutAcceptor, Long> {

    ShareOutAcceptor findTopByGroupIdAndEnabledTrueAndSoftDeleteFalse(long groupId);
}
