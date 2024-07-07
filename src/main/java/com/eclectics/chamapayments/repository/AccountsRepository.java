package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.Accounts;
import com.eclectics.chamapayments.model.jpaInterfaces.AccountsTotals;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AccountsRepository extends JpaRepository<Accounts, Long> {

    List<Accounts> findByGroupIdAndActive(long groupId, boolean isActive);

    Accounts findByGroupIdAndActiveTrueAndSoftDeleteFalse(long groupId);
    Accounts findTopByGroupIdAndActiveTrueAndSoftDeleteFalseOrderByIdDesc(long groupId);

    Accounts findByGroupId(long groupId);

    Accounts findFirstByGroupIdAndSoftDeleteFalse(long groupId);

    Accounts findFirstByAccountdetails(String account);

    List<Accounts> findByGroupIdOrderByCreatedOnAsc(long groupId);

    @Query(nativeQuery = true, value = "select * from accounts where group_id=:groupId order by id desc fetch next 1 rows only")
    Optional<Accounts> findGroupSavingsAccount(long groupId);

    Accounts findAccountsByIdAndSoftDeleteFalse(long id);

    @Query(value = "select * from accounts where accountdetails='DEFAULT_ACCOUNT'", nativeQuery = true)
    Optional<Accounts> findDefaultAccount();

    @Query(nativeQuery = true, value = "select ceil(sum(a.available_bal)) as groupbalances, sum(a.accountbalance) as coreaccountbalances from accounts a")
    AccountsTotals getGroupAccountBalances();

    Accounts findAccountsByGroupIdAndIsCoreTrue(long groupId);

    List<Accounts> findAllBySoftDeleteFalseOrderByGroupIdAsc();
}
