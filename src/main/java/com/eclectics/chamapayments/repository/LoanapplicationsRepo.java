package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.LoanApplications;
import com.eclectics.chamapayments.model.LoanProducts;
import com.eclectics.chamapayments.wrappers.response.LoanApplicationsProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;

public interface LoanapplicationsRepo extends JpaRepository<LoanApplications, Long> {

    @Query(nativeQuery = true,
            countQuery = "select COUNT(*) from " +
                    "loan_applications la join loan_products lp " +
                    "ON la.loanproductid=lp.id " +
                    "where lp.group_id=:groupid and la.pending = true",
            value = "select * from " +
                    "loan_applications la join loan_products lp " +
                    "on la.loanproductid=lp.id " +
                    "where lp.group_id=:groupid and la.pending=1 order by la.created_on desc")
    List<LoanApplications> getApplicationsbygroup(@Param("groupid") long groupid, Pageable pageable);

    @Query(nativeQuery = true,
            value = "select COUNT(*) from " +
                    "loan_applications la join loan_products lp " +
                    "on la.loanproductid=lp.id " +
                    "where lp.group_id=:groupid and pending=1 order by la.created_on desc")
    int countApplicationsbyGroup(@Param("groupid") long groupid);

    List<LoanApplications> findByMemberIdAndPendingTrueOrderByCreatedOnDesc(long memberId, Pageable pageable);

    int countByMemberIdAndPendingTrue(long memberId);

    List<LoanApplications> findByLoanProductsAndPendingTrueOrderByCreatedOnDesc(LoanProducts loanProducts, Pageable pageable);

    @Query(nativeQuery = true, value = "SELECT count(*) from loan_applications la join loan_products lp" +
            " on  la.loanproductid= lp.id where la.loanproductid= :loanProductId AND lp.group_id= :groupId AND created_on between :startDate AND :endDate")
    int countByLoanProductsAndApproved(@Param(value = "loanProductId") long productId, @Param("groupId") long groupId, @Param("startDate") Date startDate, @Param("endDate") Date endDate);

    List<LoanApplications> findAllByMemberId(long memberId);

    List<LoanApplications> findAllByPendingAndSoftDeleteAndCreatedOnBetweenOrderByCreatedOnDesc(boolean pending, boolean softDelete, Date startDate, Date endDate);

    Page<LoanApplications> findAllByApprovedAndSoftDeleteAndCreatedOnBetweenOrderByCreatedOnDesc(boolean pending, boolean softDelete, Date startDate, Date endDate, Pageable pageable);

    List<LoanApplications> findAllByLoanProductsAndCreatedOnBetweenAndApprovedAndSoftDeleteOrderByCreatedOnDesc(LoanProducts loanProducts, Date startDate, Date endDate, boolean approved, boolean softDelete, Pageable pageable);

    int countAllByLoanProductsAndPendingTrue(LoanProducts loanProducts);

    @Query(nativeQuery = true, value = "select concat(u.first_name, concat(' ', u.last_name)) as recipientName,\n" +
            "       u.phone_number                                 as phoneNumber,\n" +
            "       ld.principal                                   as principal,\n" +
            "       ceil(ld.principal + ld.interest)                   as amount,\n" +
            "       ceil(ld.dueamount)                                   as dueamount,\n" +
            "       ceil(ld.interest)                                    as interest,\n" +
            "       ld.status                                      as status\n" +
            "from loan_applications la\n" +
            "         inner join loan_products lp on lp.id = la.loanproductid\n" +
            "         inner join loans_disbursed ld on ld.id = la.id\n" +
            "         inner join members_tbl m on m.id = ld.member_id\n" +
            "         inner join users u on u.id = m.users_id\n" +
            "where lp.id = :loaProductId")
    Page<LoanApplicationsProjection> findLoanApplications(Long loaProductId, Pageable pageable);
}
