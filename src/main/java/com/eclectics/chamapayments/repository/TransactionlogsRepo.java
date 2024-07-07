package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.Accounts;
import com.eclectics.chamapayments.model.Contributions;
import com.eclectics.chamapayments.model.TransactionsLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;

public interface TransactionlogsRepo extends JpaRepository<TransactionsLog, Long> {
    /**
     * Find by debit phonenumber list.
     *
     * @param phonenumber the phonenumber
     * @param pageable    the pageable
     * @return the list
     */
    Page<TransactionsLog> findByDebitphonenumberOrderByCreatedOnDesc(String phonenumber, Pageable pageable);


    Page<TransactionsLog> findByContributionsOrderByCreatedOnDesc(Contributions contributions, Pageable pageable);

    List<TransactionsLog> findByDebitphonenumberAndContributionsAndSoftDeleteFalseOrderByCreatedOnDesc(String phonenumber, Contributions contributions, Pageable pageable);

    @Query(nativeQuery = true,
            countQuery = "select count(*) from" +
                    "account_transactions_log atl join contributions_tbl ct " +
                    "on atl.contribution_id=ct.id" +
                    "where ct.member_group_id=:groupid  and debitphonenumber=:phonenumber",
            value = "select * from" +
                    "account_transactions_log atl join contributions_tbl ct " +
                    "on atl.contribution_id=ct.id " +
                    "where ct.member_group_id=:groupid and debitphonenumber=:phonenumber order by atl.created_on desc")
    List<TransactionsLog> getTransactionsbygroupandmember(@Param("groupid") long groupid, @Param("phonenumber") String phonenumber, Pageable pageable);

    Page<TransactionsLog> findByCreditaccountsOrderByCreatedByDesc(Accounts accounts, Pageable pageable);

    List<TransactionsLog> findAllByCreatedOnBetweenOrderByCreatedOnDesc(Date startdate, Date enddate);

    List<TransactionsLog> findAllByCreatedOnBetweenOrderByCreatedOnDesc(Date startdate, Date enddate, Pageable pageable);

    TransactionsLog findFirstByUniqueTransactionIdOrderByIdDesc(String uniqueTransactionId);
}
