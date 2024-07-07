package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.LoanApplications;
import com.eclectics.chamapayments.model.LoansDisbursed;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;
import java.util.Optional;


public interface LoansdisbursedRepo extends JpaRepository<LoansDisbursed, Long> {
    List<LoansDisbursed> findByGroupId(long groupId, Pageable pageable);

    List<LoansDisbursed> findAllByGroupIdAndSoftDeleteFalseOrderByIdAsc(long groupId);

    int countAllByCreatedOnBetween(Date startDate, Date endDate);

    int countAllByGroupIdAndCreatedOnBetween(long groupId, Date startDate, Date endDate);

    int countByGroupId(long groupId);

    @Query(nativeQuery = true,
            value = "select * from loans_disbursed ld join loan_products lp on ld.group_id=lp.group_id where ld.group_id=:groupId order by ld.id desc")
    List<LoansDisbursed> findByLoanProduct(long groupId, Pageable pageable);

    @Query(nativeQuery = true,
            value = "select * from " +
                    "loans_disbursed ld join loan_applications la " +
                    "on ld.loanapplicationid=la.id " +
                    "where la.loanproductid=:loanproductId and la.group_id=:groupId and ld.created_on between :startDate and :endDate order by ld.created_on desc")
    List<LoansDisbursed> findByLoanProductAndGroup(long loanproductId, long groupId, Date startDate, Date endDate, Pageable pageable);

    @Query(nativeQuery = true,
            value = "SELECT COUNT(ld.ID) FROM " +
                    "loans_disbursed ld JOIN loan_applications la " +
                    "ON ld.loanapplicationid=la.id " +
                    "WHERE la.loanproductid=:loanproductid")
    int countLoansDisbursedbyLoanproduct(long loanproductid);

    @Query(nativeQuery = true,
            value = "select COUNT(*) from " +
                    "loans_disbursed ld join loan_applications la " +
                    "on ld.loanapplicationid=la.id " +
                    "where la.loanproductid=:loanProductId and la.group_id=:groupId and la.created_on between :startDate and :endDate order by ld.created_on desc")
    int countLoansDisbursedbyLoanproductAndGroup(long loanProductId, long groupId, Date startDate, Date endDate);

    @Query(nativeQuery = true,
            value = "select COUNT(*) from " +
                    "loans_disbursed ld join loan_applications la " +
                    "on ld.loanapplicationid=la.id " +
                    "where la.loanproductid=:loanProductId AND la.created_on between :startDate AND :endDate order by ld.created_on DESC")
    int countAllLoansDisbursedbyLoanproductAndGroup(long loanProductId, Date startDate, Date endDate);

    List<LoansDisbursed> findByMemberIdOrderByCreatedOnDesc(long memberId, Pageable pageable);

    List<LoansDisbursed> findByGroupIdAndMemberIdOrderByCreatedOnDesc(long groupId, long memberId, Pageable pageable);

    List<LoansDisbursed> findByMemberIdOrderByCreatedOnDesc(long memberId);

    int countByGroupIdAndMemberIdAndDueamountGreaterThan(long groupId, long memberId, double dueamount);

    List<LoansDisbursed> findByGroupIdAndDueamountGreaterThanAndDuedateLessThanOrderByCreatedOnDesc(long groupId, double dueamount, Date today, Pageable pageable);

    int countByGroupIdAndDueamountGreaterThanAndDuedateLessThan(long groupId, double dueamount, Date today);

    List<LoansDisbursed> findAllByGroupIdAndStatusAndSoftDeleteFalseOrderByIdAsc(long groupId, String status);

    List<LoansDisbursed> findAllByGroupIdOrderByIdAsc(long groupId);


    @Query("select ld from LoansDisbursed ld where ld.dueamount > 0.0 and soft_delete=0 order by id desc")
    List<LoansDisbursed> findExpiredLoans();

    @Query(value = "select * from loans_disbursed where group_id=:groupId and member_id =:memberId and dueamount > 0.0 and soft_delete=0 order by id desc", nativeQuery = true)
    List<LoansDisbursed> findUserPendingLoans(long groupId, long memberId);

    @Query(value = "select * from loans_disbursed where group_id=:groupId and member_id=:memberId and soft_delete=0 order by id desc", nativeQuery = true)
    List<LoansDisbursed> sumMemberPendingLoans(long groupId, long memberId);

    @Query(value = "select ld.* from loans_disbursed ld inner join loan_applications la on la.id = ld.loanapplicationid inner join loan_products lp on la.loanproductid = lp.id where ld.member_id =1 and ld.loanapplicationid =2 and ld.dueamount > 0.0 order by ld.id desc", nativeQuery = true)
    List<LoansDisbursed> findUserPendingLoansInLoanProduct(Long memberId, Long productId);

    @Query(nativeQuery = true,
            value = "SELECT * FROM " +
                    "loans_disbursed ld JOIN loan_applications la " +
                    "ON ld.loanapplicationid=la.id " +
                    "WHERE la.loanproductid=:loanProductId AND ld.created_on between :startDate and :endDate ORDER BY ld.created_on DESC")
    List<LoansDisbursed> findAllByLoanProductId(long loanProductId, Date startDate, Date endDate, Pageable pageable);

    @Query(nativeQuery = true, value = "SELECT * FROM " +
            "loans_disbursed ld JOIN loan_applications la " +
            "ON ld.loanapplicationid=la.id " +
            "WHERE la.loanproductid=:loanProductId AND ld.dueamount > 0 AND ld.duedate < current_date AND ld.created_on between :startDate and :endDate ORDER BY ld.created_on DESC")
    List<LoansDisbursed> findAllOverdueByLoanProductId(long loanProductId, Date startDate, Date endDate, Pageable pageable);

    @Query(nativeQuery = true, value = "SELECT ld.* FROM " +
            "loans_disbursed ld JOIN loan_applications la " +
            "ON ld.loanapplicationid=la.id " +
            "WHERE  ld.dueamount > 0 AND ld.duedate < current_date AND ld.created_on between :startDate and :endDate ORDER BY ld.created_on DESC")
    List<LoansDisbursed> findAllOverdue(Date startDate, Date endDate, Pageable pageable);

    @Query(nativeQuery = true,
            value = "SELECT COUNT(*) FROM " +
                    "loans_disbursed ld JOIN loan_applications la " +
                    "ON ld.loanapplicationid=la.id " +
                    "WHERE  ld.dueamount > 0 AND ld.duedate < current_date AND ld.created_on between :startDate and :endDate")
    int countAllOverdue(Date startDate, Date endDate);

    @Query(nativeQuery = true,
            value = "SELECT COUNT(*) FROM " +
                    "loans_disbursed ld JOIN loan_applications la " +
                    "ON ld.loanapplicationid=la.id " +
                    "WHERE la.loanproductid=:loanProductId  AND ld.dueamount > 0 AND ld.duedate < current_date AND ld.created_on between :startDate and :endDate")
    int countAllOverdueByLoanProductId(long loanProductId, Date startDate, Date endDate);

    @Query(nativeQuery = true, value = "SELECT * FROM " +
            "loans_disbursed ld JOIN loan_applications la " +
            "ON ld.loanapplicationid=la.id " +
            "WHERE la.loanproductid=:loanProductId AND group_id=:groupid AND ld.dueamount > 0 AND ld.duedate < CURRENT_DATE AND ld.created_on between :startDate and :endDate ")
    List<LoansDisbursed> findAllOverdueByLoanProductIdAndGroup(long loanProductId, long groupid, Date startDate, Date endDate, Pageable pageable);

    @Query(nativeQuery = true,
            value = "SELECT COUNT(*) FROM " +
                    "loans_disbursed ld JOIN loan_applications la " +
                    "ON ld.loanapplicationid=la.id " +
                    "WHERE la.loanproductid=:loanProductId AND group_id=:groupid AND ld.dueamount > 0 AND ld.duedate < CURRENT_DATE() AND ld.created_on between :startDate and :endDate")
    int countAllOverdueByLoanProductIdAndGroup(long loanProductId, long groupid, Date startDate, Date endDate);

    @Query(nativeQuery = true, value = "SELECT * FROM " +
            "loans_disbursed ld JOIN loan_applications la " +
            "ON ld.loanapplicationid=la.id " +
            "WHERE  group_id=:groupid AND ld.dueamount > 0 AND ld.duedate < CURRENT_DATE AND ld.created_on between :startDate and :endDate ORDER BY ld.created_on DESC")
    List<LoansDisbursed> findAllOverdueByGroup(long groupid, Date startDate, Date endDate, Pageable pageable);

    @Query(nativeQuery = true,
            value = "SELECT COUNT(*) FROM " +
                    "loans_disbursed ld JOIN loan_applications la " +
                    "ON ld.loanapplicationid=la.id " +
                    "WHERE group_id=:groupid AND ld.dueamount > 0 AND ld.duedate < CURRENT_DATE() AND ld.created_on between :startDate and :endDate ORDER BY ld.created_on DESC")
    int countAllOverdueByGroup(long groupid, Date startDate, Date endDate);

    List<LoansDisbursed> findAllByCreatedOnBetweenOrderByCreatedOnDesc(Date StartDate, Date endDate, Pageable pageable);

    List<LoansDisbursed> findAllByGroupIdAndCreatedOnBetweenOrderByCreatedOnDesc(long groupId, Date startDate, Date endDate, Pageable pageable);


    @Query(nativeQuery = true, value = "SELECT COALESCE(sum(ld.principal + ld.interest), 0) FROM loans_disbursed ld")
    long getSumOfTotalLoansDisbursed();

    LoansDisbursed findFirstByGroupIdAndMemberIdAndStatusOrderByIdAsc(long groupId, long memberId, String status);

    List<LoansDisbursed> findAllByGroupIdAndMemberIdAndSoftDeleteFalseOrderByIdAsc(long groupId, long memberId);
}
