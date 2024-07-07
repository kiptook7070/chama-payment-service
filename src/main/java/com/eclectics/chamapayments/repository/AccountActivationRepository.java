package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.AccountActivation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountActivationRepository extends JpaRepository<AccountActivation, Long> {
    AccountActivation findFirstByGroupIdAndPhoneNumberAndSoftDeleteFalse(Long groupId, String phoneNumber);
}
