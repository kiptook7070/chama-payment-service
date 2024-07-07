package com.eclectics.chamapayments.service;

import com.eclectics.chamapayments.wrappers.request.ApplyLoanWrapper;
import com.eclectics.chamapayments.wrappers.request.LoanInterestWrapper;
import com.eclectics.chamapayments.wrappers.request.LoanLimitWrapper;
import com.eclectics.chamapayments.wrappers.request.LoanRepaymentsWrapper;
import com.eclectics.chamapayments.wrappers.response.LoanproductWrapper;
import com.eclectics.chamapayments.wrappers.response.UniversalResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

import java.util.Date;

/**
 * @author Alex Maina
 * @createdOn  06/12/2021
 */
public interface LoanService {
    int checkLoanLimit(String phoneNumber, Long contributionId);

    Mono<UniversalResponse> createLoanProduct(LoanproductWrapper loanproductWrapper, String createdBy);

    Mono<UniversalResponse> editLoanProduct(LoanproductWrapper loanproductWrapper, String approvedBy);

    Mono<UniversalResponse> activateDeactivateLoanProduct(LoanproductWrapper loanproductWrapper, String currentUser, boolean activate);

    Mono<UniversalResponse> getLoanProductsbyGroup(long groupId);

    Mono<UniversalResponse> applyLoan(ApplyLoanWrapper applyLoanWrapper, String username);

    Mono<UniversalResponse> getLoansPendingApprovalbyGroup(long groupid, int page, int size);

    Mono<UniversalResponse> getLoansPendingApprovalbyUser(String phonenumber, int page, int size);

    Mono<UniversalResponse> getLoansPendingApprovalbyLoanProduct(long loanproductid, String currentUser, int page, int size);

    Mono<UniversalResponse> approveLoanApplication(boolean approve, long loanApplicationId, String approvedBy);

    Mono<UniversalResponse> getDisbursedLoansperGroup(long groupid, int page, int size);

    Mono<UniversalResponse> getDisbursedLoansperLoanproduct(long loanproductid, int page, int size);

    Mono<UniversalResponse> getDisbursedLoansperUser(long filterId, String phonenumber, int page, int size);

    Mono<UniversalResponse> getLoanPaymentPendingApprovalByUser(String phonenumber, int page, int size);

    Mono<UniversalResponse> getLoanPaymentPendingApprovalByGroup(long groupid, String currentUser, int page, int size);

    Mono<UniversalResponse> getLoanPaymentsbyUser(String phonenumber, int page, int size);

    Mono<UniversalResponse> getLoanPaymentsbyGroupid(long groupid, int page, int size);

    Mono<UniversalResponse> getLoanPaymentsbyDisbursedloan(long disbursedloanid, int page, int size);

    Mono<UniversalResponse> getOverdueLoans(long groupid, int page, int size);


    Mono<UniversalResponse> getGroupLoansPenalties(Long groupId);

    Mono<UniversalResponse> getMemberLoansPenalties(String phoneNumber);

    Mono<UniversalResponse> getMemberLoansPenalties(String phoneNumber, Integer page, Integer size);

    Mono<UniversalResponse> getInactiveGroupLoanProducts(Long groupId);

    Mono<UniversalResponse> getGroupsLoanSummaryPayment(String groupName, Date startDate, Date endDate, Pageable pageable);

    Mono<UniversalResponse> getLoanApplications(Long loanProductId, Integer page, Integer size);

    Mono<UniversalResponse> getUserLoanApplications(String phoneNumber, Integer page, Integer size);

    Mono<UniversalResponse> getLoanPaymentsByLoanProductProduct(long loanProductId, int page, int size);

    Mono<UniversalResponse> getUserLoanProducts(String username);

    Mono<UniversalResponse> getActiveLoanProductsbyGroup(Long groupId, boolean isActive);

    Mono<UniversalResponse> loanLimit(LoanLimitWrapper wrapper, String username);

    Mono<UniversalResponse> loanInterest(LoanInterestWrapper wrapper, String username);
}
