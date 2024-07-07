package com.eclectics.chamapayments.repository;


import com.eclectics.chamapayments.model.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * The interface Account type repository.
 */
public interface AccountTypeRepository extends JpaRepository<AccountType, Long> {

    boolean existsAccountTypeByAccountName(String accountname);

    AccountType findByAccountNameContains(String accountname);

    @Query(value = "select * from account_types where account_prefix='WA' and soft_delete=0", nativeQuery = true)
    Optional<AccountType> getAccountType();

    AccountType findFirstByAccountPrefixAndSoftDeleteFalse(String accountPrefix);
}
