package com.eclectics.chamapayments.service;

import com.eclectics.chamapayments.model.*;
import com.eclectics.chamapayments.wrappers.esbWrappers.EsbAccountWrapper;
import com.eclectics.chamapayments.wrappers.request.*;
import com.eclectics.chamapayments.wrappers.response.*;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author David Charo
 * @created 06/12/2022
 */
public interface AccountingService {
    List<AccountType> findAccountTypes();

    List<AccountDto> getAccountbyGroup(long groupId);

    List<ContributionType> getContributiontypes();

    List<AmountType> getAmounttypes();

    void createContribution(Contributions contributions);

    Mono<UniversalResponse> approveContributionPayment(long paymentId, boolean approved, String approvedBy);

    PageDto getPendingWithdrawalRequestByGroupId(long groupId, int page, int size);

    List<TransactionLogWrapper> getTransactionsbyGroup(long groupid, Pageable pageable, String username);

    List<TransactionLogWrapper> getTransactionsbyGroupUser(long groupId, String username, Pageable pageable);

    List<TransactionLogWrapper> getWithdrawalsbyGroup(long groupid, Pageable pageable);

    UniversalResponse getTransactionsByUser(String username, Pageable pageable);

    List<TransactionLogWrapper> getWithdrawalsbyUser(String phonenumber, Pageable pageable);

    UniversalResponse getTransactionsByContributions(Long contributionId, Pageable pageable, String username);

    List<TransactionLogWrapper> getWithdrawalsbyContribution(Long contributionId, Pageable pageable);

    List<TransactionLogWrapper> getTransactionsbyUserandContributions(String phonenumber, Contributions contributions, Pageable pageable);

    List<TransactionLogWrapper> getTransactionsbyUserandGroupId(String username, long groupId, Pageable pageable);

    UniversalResponse getTransactionsByAccount(Long accountId, Pageable pageable, String username);

    List<TransactionLogWrapper> getWithdrawalsbyAccount(Long accountId, Pageable pageable);

    List<Map<String, Object>> groupTransactionsDetailed(Date startDate, Date endDate, String period, String group);

    UniversalResponse getContributionTypes();

    Mono<UniversalResponse> recordWithdrawal(RequestwithdrawalWrapper requestwithdrawalWrapper);

    Mono<UniversalResponse> checkLoanLimit(String phoneNumber, long groupId, Long contributionId, Long productId);

    Mono<UniversalResponse> userWalletBalance();

    Mono<UniversalResponse> groupAccountBalance(Long groupId);

    Mono<UniversalResponse> shareOutsAccountBalance(Long groupId);

    Mono<UniversalResponse> addContribution(ContributionDetailsWrapper wrapper);

    Mono<UniversalResponse> makeContribution(ContributionPaymentDto contributionPayment, String phoneNumber);

    Mono<UniversalResponse> makeContributionForOtherMember(ContributionPaymentDto contributionPayment, String walletAccount);

    @Bean
    Consumer<String> fundsTransferCallback();

    void createGroupAccount(String accountInfo);

    void createGroupContribution(String contributionInfo);

    Mono<UniversalResponse> approveWithdrawalRequest(WithdrawalApprovalRequest request, String approvedBy);

    Mono<UniversalResponse> approveKitTransfer(KitTransferApprovalDto request, String approvedBy);

    Mono<UniversalResponse> kitTransferPendingApprovals(PendingApprovalsWrapper request, String user);

    Mono<UniversalResponse> getUserContributionPayments(String phoneNumber);

    Mono<UniversalResponse> getUserContributionPayments(String phoneNumber, Integer page, Integer size);

    Mono<UniversalResponse> getGroupContributionPayments(Long contributionId, Integer page, Integer size);

    Mono<UniversalResponse> getUssdGroupContributionPayments(Long contributionId, Integer page, Integer size);

    Mono<UniversalResponse> getUserUpcomingPayments(String phoneNumber, long groupId);

    Mono<UniversalResponse> getUserUpcomingPayments(String phoneNumber);

    Mono<UniversalResponse> getAllUserUpcomingPayments(String phoneNumber);

    void enableGroupContributions(String groupInfo);

    void disableGroupContributions(String groupInfo);

    Mono<UniversalResponse> getUserContributionsPerGroup(String phoneNumber);

    Mono<UniversalResponse> getAllMemberPenalties(String username);

    Mono<UniversalResponse> getGroupContributionPenalties(Long groupId, int page, int size);

    Mono<UniversalResponse> editContribution(ContributionDetailsWrapper contributionDetailsWrapper, String username);

    Mono<UniversalResponse> getGroupContributions(Long groupId);

    Mono<UniversalResponse> getGroupContribution(Long contributionId);

    Mono<UniversalResponse> getGroupAccountsMemberBelongsTo(String username);

    Mono<UniversalResponse> getGroupTransactions(Long groupId, Integer page, Integer size, String username);

    Mono<UniversalResponse> getGroupTransactionsPerUser(long groupId, String username, Integer page, Integer size);

    Mono<UniversalResponse> getUserTransactionsByContribution(String username, Long groupId, Integer page, Integer size);

    Mono<UniversalResponse> getUserTransactionsByGroup(String username, Long groupId, Integer page, Integer size);

    Mono<UniversalResponse> getUserSummary(String phone, Long contributionId);

    Mono<UniversalResponse> payForContributionPenalty(ContributionPaymentDto dto);

    void writeOffLoansAndPenalties(String memberInfo);

    void editContributionName(String contributionNameUpdate);

    void updateGroupCoreAccount(String groupCoreAccountInfo);

    Mono<UniversalResponse> getOverpaidContributions(String username);

    Mono<UniversalResponse> viewFines(String phoneNumber, Long groupId);

    Mono<UniversalResponse> viewFineById(Long id);

    Mono<UniversalResponse> updateFine(Long id, FineWrapper fineWrapper);

    Mono<UniversalResponse> createFines(List<FineWrapper> fineWrapperList, String username);

    Mono<UniversalResponse> viewGroupFines(Long groupId);

    void createLoanProduct(String s);

    Mono<UniversalResponse> editContributionPostBank(EditContributionWrapper req, String username);

    Mono<UniversalResponse> approveDeclineContribution(ContributionsApprovalRequest req, String approver);

    Mono<UniversalResponse> getKitBalance(KitBalanceWrapper req);

    Mono<UniversalResponse> kitTransfer(KitTransferWrapper req);

    Mono<UniversalResponse> accountLookup(String phoneNumber);

    Mono<UniversalResponse> shareOuts(ShareOutsWrapper wrapper);

    Mono<UniversalResponse> shareOutsPreview(String userName, Long groupId, Integer page, Integer size);

    Mono<UniversalResponse> approveAccount(CanWithdrawWrapper req, String username);

    Mono<UniversalResponse> editContributionPendingApprovals(PendingApprovalsWrapper request, String user);

    Mono<UniversalResponse> esbAccountValidation(EsbAccountWrapper wrapper);

    Mono<UniversalResponse> listFinesPendingApprovals(FinesPendingApprovalsWrapper wrapper, String username);

    Mono<UniversalResponse> approveDeclineFineRequest(FineApprovalRequest request, String username);

    Mono<UniversalResponse> getOtherGroupAccountTransactions(Long groupId, Integer page, Integer size, String username);

    Mono<UniversalResponse> assignTransaction(List<MemberTransactionsWrapper> memberTransactions, long groupId, String username);

    Mono<UniversalResponse> listOtherTransactionsPendingApprovals(OtherTransactionsPendingApprovalsWrapper wrapper, String username);

    Mono<UniversalResponse> approveDeclineOtherTransaction(OtherTransactionApprovalRequest request, String username);

    Mono<UniversalResponse> groupStatement(AccountStatementDto statementDto);

    Mono<UniversalResponse> memberAccountStatement(AccountStatementDto statementDto);

    Mono<UniversalResponse> getGroupMemberShareOutsDisbursement(String username, ShareOutsDisbursementWrapper wrapper);

    Mono<UniversalResponse> getGroupsMembersShareOutsDisbursement(String username, ShareOutsDisbursementWrapper wrapper);

    Mono<UniversalResponse> getIndividualGroupMembersShareOutsDisbursement(String username, ShareOutsDisbursementWrapper wrapper);

    Mono<UniversalResponse> getGroupTransactionsFromOtherChannels(Long aLong, Pageable pageable, String s);

    Mono<UniversalResponse> mchamaAccountValidation(String account);
}
