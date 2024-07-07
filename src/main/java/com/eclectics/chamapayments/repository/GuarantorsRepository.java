package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.Guarantors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GuarantorsRepository extends JpaRepository<Guarantors, Long> {

    @Query
    List<Guarantors> findGuarantorsByPhoneNumberAndLoanStatusAndSoftDeleteFalse(String phoneNumber, String loanStatus);

    Optional<Guarantors> findGuarantorsByPhoneNumberAndLoanIdAndLoanStatus(String phoneNumber, long loanId, String loanStatus);

    @Query("SELECT guarantors FROM Guarantors guarantors WHERE guarantors.loanId = :loanId")
    List<Guarantors> findGuarantorsByLoanId(@Param("loanId") Long loanId);

    List<Guarantors> findAllByLoanIdAndLoanStatus(Long loanId, String loanStatus);

    @Query(nativeQuery = true, value = "select * from guarantors_tbl gt inner join loan_applications la on gt.loan_id= la.id where la.member_id=:memberId and gt.loan_status=:loanStatus")
    List<Guarantors> findAllByMemberIdAndLoanStatus(long memberId, String loanStatus);
}
