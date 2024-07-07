package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.ContributionPayment;
import com.eclectics.chamapayments.wrappers.response.UserGroupContributions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface ContributionsPaymentRepository extends JpaRepository<ContributionPayment, Long> {

    List<ContributionPayment> findAllByGroupIdAndPaymentTypeOrderByIdAsc(Long groupId, String paymentType);

    List<ContributionPayment> findAllByGroupIdAndPaymentTypeAndSoftDeleteFalseOrderByIdAsc(long groupId, String paymentType);

    @Query(value = "select * from contribution_payment where payment_status='PAYMENT_SUCCESS' and payment_type='saving' and share_out='Y' and contribution='Y' and shares_completed='N' order by id desc", nativeQuery = true)
    List<ContributionPayment> findContributionsPayments();

    @Query("SELECT contributionPayment from ContributionPayment contributionPayment WHERE contributionPayment.transactionId = :transactionId")
    Optional<ContributionPayment> findContributionByTransactionId(@Param("transactionId") String transactionId);

    ContributionPayment findFirstByTransactionId(String transactionId);

    ContributionPayment findFirstByIdAndGroupIdAndTransactionIdAndSoftDeleteFalse(long id, long groupId, String transactionId);

    @Query(value = "SELECT * FROM contribution_payment as cp where cp.contribution_id = :contributionId AND cp.payment_status = 'PAYMENT_SUCCESS' AND cp.phone_number = :phoneNumber", nativeQuery = true)
    List<ContributionPayment> findUsersContribution(Long contributionId, String phoneNumber);

    List<ContributionPayment> findAllByGroupIdAndContributionIdAndPhoneNumberAndPaymentStatusAndPaymentTypeAndSoftDeleteFalse(Long groupId, Long contributionId, String phoneNumber, String paymentStatus, String paymentType);

    List<ContributionPayment> findAllByGroupIdAndPhoneNumberAndPaymentStatusAndPaymentTypeAndSoftDeleteFalseOrderByIdDesc(Long groupId, String phoneNumber, String paymentStatus, String paymentType);

    List<ContributionPayment> findAllByGroupIdAndPhoneNumberAndPaymentStatusAndPaymentTypeAndSoftDeleteFalse(Long groupId, String phoneNumber, String paymentStatus, String paymentType);

    @Query("SELECT  contributionPayment FROM ContributionPayment contributionPayment where contributionPayment.contributionId = :contributionId AND contributionPayment.penaltyId = :penaltyId")
    List<ContributionPayment> findPenaltyContributions(@Param("contributionId") Long contributionId, @Param("penaltyId") Long penaltyId);

    @Query("SELECT  contributionPayment FROM ContributionPayment contributionPayment where contributionPayment.phoneNumber = :phoneNumber AND contributionPayment.schedulePaymentId = :scheduleId AND contributionPayment.paymentStatus = 'PAYMENT_SUCCESS' AND contributionPayment.isPenalty= false ")
    List<ContributionPayment> findPaidScheduledContributions(@Param("phoneNumber") String phoneNumber, @Param("scheduleId") String scheduleId);

    @Query("select new com.eclectics.chamapayments.wrappers.response.UserGroupContributions(c.name, cp.amount) from ContributionPayment cp inner join Contributions c on c.id = cp.contributionId where cp.paymentStatus = 'PAYMENT_SUCCESS' and cp.phoneNumber = :phoneNumber")
    List<UserGroupContributions> findUserContributions(@Param("phoneNumber") String phoneNumber);

    List<ContributionPayment> findAllByCreatedOnBetweenAndSoftDeleteFalseAndPaymentStatus(Date startDate, Date endDate, String paymentStatus, Pageable pageable);

    @Query(nativeQuery = true, value = "select * from contribution_payment where group_id=:groupId and phone_number=:phoneNumber and payment_status='PAYMENT_SUCCESS' and soft_delete = 0 and created_on between  to_date(:startDateFormat,'DD-MM-YYYY') and to_date(:endDateFormat,'DD-MM-YYYY') order by id asc")
    List<ContributionPayment> generateMemberStatement(long groupId, String phoneNumber, String startDateFormat, String endDateFormat);

    @Query(nativeQuery = true, value = "select * from contribution_payment where created_on between  to_date(:startDateFormat,'DD-MM-YYYY') and to_date(:endDateFormat,'DD-MM-YYYY') order by id desc")
    List<ContributionPayment> findAllPaymentsByGroup(String startDateFormat, String endDateFormat, Pageable pageable);

    @Query(nativeQuery = true, value = "select * from contribution_payment cp inner join groups_tbl gt on cp.GROUP_ID=gt.ID where gt.NAME LIKE concat('%', concat(:group, '%')) and cp.created_on between  to_date(:startDateFormat,'DD-MM-YYYY') AND to_date(:endDateFormat,'DD-MM-YYYY') order by cp.created_on desc")
    List<ContributionPayment> findAllPaymentsByGroupName(String group, String startDateFormat, String endDateFormat, Pageable pageable);

    List<ContributionPayment> findContributionPaymentByPhoneNumber(String phoneNumber);

    Page<ContributionPayment> findContributionPaymentByPhoneNumber(String phoneNumber, Pageable pageable);

    @Query(value = "select * from contribution_payment where group_id=:groupId and phone_number=:phone and amount>0.0 and payment_status = 'PAYMENT_SUCCESS' and soft_delete=0 order by id desc fetch next 20 rows only", nativeQuery = true)
    Page<ContributionPayment> findAllUserTransactions(long groupId, String phone, Pageable pageable);

    long countByPaymentStatus(String status);

    @Query(nativeQuery = true, value = "SELECT COALESCE(sum(cp.amount), 0) FROM contribution_payment cp WHERE cp.payment_status = 'PAYMENT_SUCCESS'")
    long getTotalSuccessfulContributions();

    @Query("SELECT COALESCE(SUM(cp.amount), 0) FROM ContributionPayment cp WHERE cp.schedulePaymentId = :contributionScheduledId AND cp.phoneNumber = :phoneNumber AND cp.paymentStatus = 'PAYMENT_SUCCESS' ")
    Integer getTotalMemberContributionsForScheduledPayment(String contributionScheduledId, String phoneNumber);

    Page<ContributionPayment> findByContributionIdOrderByIdDesc(Long contributionId, Pageable pageable);

    List<ContributionPayment> findAllByCreatedOnBetweenAndPaymentStatus(Date createdOn, Date createdOn2, String paymentStatus);

    List<ContributionPayment> findAllByGroupIdAndPaymentTypeAndPaymentStatusAndSoftDeleteFalseOrderByIdDesc(Long groupId, String paymentType, String paymentStatus);

    List<ContributionPayment> findAllByGroupIdAndPaymentTypeAndPaymentStatusAndIsDebitAndSoftDeleteFalseOrderByIdDesc(long groupId, String paymentType, String paymentStatus, Character isDebit);

    @Query(value = "select * from contribution_payment where group_id=:groupId and payment_status='PAYMENT_SUCCESS' and payment_type=:paymentType and phone_number=:phoneNumber and soft_delete=0 order by id desc", nativeQuery = true)
    List<ContributionPayment> findMemberPaymentsByPaymentType(long groupId, String paymentType, String phoneNumber);

    List<ContributionPayment> findAllByCreatedOnBetweenAndGroupIdAndPaymentStatus(Date createdOn, Date createdOn2, Long groupId, String paymentStatus);

    @Query(value = "select * from contribution_payment where group_id=:groupId and amount>0.0 and payment_status ='PAYMENT_SUCCESS' and soft_delete=0 order by id desc", nativeQuery = true)
    Page<ContributionPayment> getTransactionsByGroup(@Param("groupId") Long groupId, Pageable pageable);

    int countAllByGroupIdAndContributionIdAndPaymentStatusAndSoftDeleteFalse(long groupId, long contributionId, String paymentStatus);

}
