package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.Accounts;
import com.eclectics.chamapayments.model.Contributions;
import com.eclectics.chamapayments.model.LoanApplications;
import com.eclectics.chamapayments.model.WithdrawalLogs;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;
import java.util.Optional;


public interface WithdrawallogsRepo extends JpaRepository<WithdrawalLogs, Long> {
    @Query(nativeQuery = true,
            countQuery = "SELECT COUNT(*) FROM " +
                    "account_withdrawal_log atl JOIN contributions_tbl ct " +
                    "ON atl.contribution_id=ct.id " +
                    "WHERE ct.member_groups=:groupid",
            value = "SELECT * FROM " +
                    "account_withdrawal_log atl JOIN contributions_tbl ct " +
                    "ON atl.contribution_id=ct.id " +
                    "WHERE ct.member_group_id=:groupid ORDER BY atl.id DESC")
    List<WithdrawalLogs> getWithdrawalsbygroup(@Param("groupid") long groupid, Pageable pageable);

    List<WithdrawalLogs> findByCreditphonenumber(String phonenumber, Pageable pageable);

    List<WithdrawalLogs> findByContributions(Contributions contributions, Pageable pageable);

    List<WithdrawalLogs> findByDebitAccounts(Accounts accounts, Pageable pageable);

    @Query("SELECT SUM(c.transamount) FROM WithdrawalLogs c WHERE c.contributions=:contribution")
    Double getTotalbyContribution(Contributions contribution);

    List<WithdrawalLogs> findAllByCreatedOnBetweenAndSoftDelete(Date startDate, Date endDate, boolean softDelete, Pageable pageable);

    int countAllByCreatedOnBetweenAndSoftDeleteFalse(Date startDate, Date endDate);

    List<WithdrawalLogs> findAllByCreatedOnBetweenOrderByCreatedOnDesc(Date startDate, Date endDate);

}
