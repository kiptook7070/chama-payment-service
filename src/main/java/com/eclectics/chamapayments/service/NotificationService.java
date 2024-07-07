package com.eclectics.chamapayments.service;

import com.eclectics.chamapayments.wrappers.response.MemberWrapper;

import java.time.LocalDate;
import java.util.List;

/**
 * @author Alex Maina
 * @created 06/12/2021
 */
public interface NotificationService {
    void sendLoanApprovalText(MemberWrapper member, Double amount, String language);

    void sendGuarantorsInviteMessage(List<String> phoneNumbers, Double amount, String loanRecipientName, String language);

    void sendGuarantorshipApprovalMessage(String guarantorPhone, String guarantorName, String applicantPhone, String applicantName, double amount, String language);

    void sendGuarantorshipDeclinedMessage(String guarantorPhone, String guarantorName, String applicantPhone, String applicantName, double amount, String language);

    public void sendGroupGuarantorshipDeclinedMessage(String memberPhone, String guarantorName, String applicantPhone, String applicantName, double amount, String language);

    void sendLoanApprovedMessage(String phonenumber, String name, double amount, String language);

    void sendLoanDeclinedMessage(String phoneNumber, String name, double amount, String language);

    void sendLoanDisbursedMessage(String phoneNumber, String name, double amount, String language);


    void sendWithdrawalRequestApprovedText(String phoneNumber, String name, double amount, String language);

    void sendB2cError(String phoneNumber, String name, double amount, String language);

    void sendPenaltyFailureMessage(String phoneNumber, String name, Integer amount, String language);

    void sendPenaltySuccessMessage(String phoneNumber, String name, Integer amount, String language);

    void sendContributionFailureMessage(String phoneNumber, String name, Integer amount, String language);

    void sendContributionSuccessMessage(String phoneNumber, String name, Integer amount, String language);

    void sendContributionSuccessMessageToGroup(String phoneNumber, String memberName, String groupName, Integer amount, String language);

//    void sendReminderMessage(MemberWrapper groupMembership, String name, String contributionName, Integer reminder, String language);

    void sendLoanDisbursementTextToGroup(String phoneNumber, String memberName, String groupName, double amount, String language);

    void sendLoanRepaymentSuccessText(String phoneNumber, String memberName, String name, int amountPaid, String language);

    void sendLoanRepaymentFailureText(String phoneNumber, String memberName, String groupName, int amount, String language);

    void sendContributionWithdrawalFailure(String phoneNumber, String firstname, String groupName, int amount, String language);

    void sendContributionWithdrawalSuccess(String phoneNumber, String firstname, String groupName, int amount, String language);

    void sendContributionWithdrawalToGroup(String phoneNumber, String memberName, String groupName, double amount, String language);

    void sendPenaltyCreatedMessage(String phoneNumber, String memberName, String scheduledId, String groupName, double amount, String language);

    void sendPenaltyCreatedMessageToGroup(String phoneNumber, String memberName, String scheduledId, double amount, String language, String groupName);

    void sendOutstandingPaymentConfirmation(String phoneNumber, String firstname, int dueAmount, String groupName, int remainder, String scheduleId, String language);

    void sendGroupGuarantorshipApprovedMessage(String first, String guarantorName, String phoneNumber, String memberName, double amount, String language);

    void sendPenaltySuccessMessageToGroup(String first, String memberName, String groupName, Integer amount, String language);

    void sendMemberWithdrawRequestText(String memberName, double amount, String groupName, String phoneNumber, String language);

    void sendLoanProductCreated(String memberName, String productName, String name, String phoneNumber, String language);

    void sendLoanProductEdited(String memberName, String productName, String groupName, double maxPrincipal, double minPrincipal, int userSavingValue, Integer penaltyValue, String phoneNumber, String language);

    void sendLoanProductActivatedMessage(String memberName, String productName, String groupName, String phoneNumber, String language);

    void sendLoanProductDeactivatedMessage(String memberName, String productName, String groupName, String phoneNumber, String language);


    void sendKitTransferSms(String firstname, String memberName, String groupName, double amount, String phoneNumber, String from, String to, String language);
    
    void sendKittyTransferAcceptedText(String firstname, String memberName, String groupName, double amount, String phonenumber, String language);

    void sendGroupEnableWithdrawalSms(String firstname, String lastname, String name, String phonenumber, String language);

    void sendGroupDisableWithdrawalSms(String firstname, String name, String phonenumber, String language);


    void sendPostBankEmail(String message, String toEmail, String groupName);

    void sendFirstDepositSmsToOfficials(String firstname, String name, Integer amount, String phonenumber, String language);

    void sendDepositSmsToGroupMembers(String firstname, String depositor, String groupName, Integer amount, String transactionId, String phonenumber, String maskedCardNumber, String language);

    void sendKittyTransferDeclineText(String firstname, String memberName, String groupName, String phonenumber, String language);


    void sendWithdrawalRequestFailureText(String phonenumber, String firstname, String waWithdrawalFailure, String language);

    void sendOfficialWithdrawRequestText(String firstname, String memberName, String name, double amount, String phonenumber, String language);

    void sendOfficialWithdrawRequestTextForCoreAccount(String firstname, String coreAccount, String name, double amount, String phonenumber, String language, String capturedBy);

    void sendApprovedWithdrawRequestTextToMembers(String firstname, String memberName, String name, int amount, String phonenumber, String language);

    void sendMembersDeclinedWithdrawRequestText(String firstname, String approverName, String memberName, String groupName, double amount, String phoneNumber, String language);

    void sendWithdrawalRequestDeclineText(String memberName, String approverName, double amount, String name, String phonenumber, String language);

    void sendMemberStatement(String toEmail, String memberName, String phoneNumber, String language, String group);

    void sendGroupStatement(String firstname, String groupName, String phonenumber, String language, String toEmail, String memberName);

    void sendMembersDeclinedFineText(String firstname, String approverName, String memberName, String group, Double fineAmount, String phonenumber, String language, String fineName);

    void sendFineDeclineText(String memberName, String approverName, Double fineAmount, String group, String phonenumber, String fineName, String language, long id);

    void sendFineSmsToMember(String finedMember, String initiator, String groupName, Double amount, String description, String phonenumber, String language, long groupId);

    void sendFineSmsToGroupMembers(String finedMember, String initiator, String groupName, Double amount, String description, String firstname, String phonenumber, String language);

    void sendFineApprovedText(String memberName, String approverName, Double fineAmount, String group, String phonenumber, String fineName, String language);

    void sendMembersApprovedFineText(String firstname, String approverName, String memberName, String group, Double fineAmount, String phonenumber, String language, String fineName);

    void sendPendingLoansMessage(String firstname, String phonenumber, double remainingAmount, String language, String name, double totalAmount);
    
    void sendOverPaidLoansMessage(String firstname, String name, double totalAmount, double overPaid, String phonenumber, String language);

    void sendSettledLoansMessage(String firstname, String name, double totalAmount, String phonenumber, String language);

    void sendMemberOtherTransactionText(String groupName, double amount, String creator, String otherMember, String memberPhone, String channel, String language);

    void sendMembersOtherTransactionText(String groupName, double amount, String creator, String otherMember, String firstname, String phonenumber, String channel, String language);

    void sendDeclinedOtherTransactionTextToMember(String groupName, double amount, String approverName, String memberName, String phonenumber, String language);

    void sendDeclinedOtherTransactionTextToMembers(String groupName, double amount, String approverName, String memberName, String firstname, String phonenumber, String language);

    void sendApprovedOtherTransactionTextToMember(String groupName, Double amount, String approverName, String memberName, String phonenumber, String channel, String language);

    void sendApprovedOtherTransactionTextToMembers(String groupName, Double amount, String approverName, String memberName, String firstname, String phonenumber, String channel, String language);

    void sendShareOutWithdrawalRequestFailureText(String phoneNumber, String memberName, String waWithdrawalFailure, String language, String name, double finalAmount);

    void sendShareOutWithdrawalToMembers(String firstname, String name, String phonenumber, String language);

    void sendShareOutWithdrawalToMember(String phoneNumber, String memberName, double finalAmount, String language, String name);

    void sendNoShareOutWithdrawalRequestFailureText(String phoneNumber, String name, String firstname, double memberLoans, String language);

    void sendEditContributionDeclineText(String groupName, String memberName, String phoneNumber, String language);

    void sendEditContributionDeclineTextToGroup(String memberName, String groupName, String firstname, String phonenumber, String language);

    void sendInitiatorEditContributionAcceptedText(String groupName, String memberPhoneNumber, String memberName, String language);

    void sendMembersEditContributionAcceptedText(String firstname, String memberName, String groupName, String phonenumber, String language);

    void sendAnonymousShareOutsText(String firstname, String groupName, String phonenumber, String phoneNumber, String language);

    void sendLoanToLoanedMember(String groupName, double amount, String memberName, String loanedMemberPhone, String language);

    void sendLoanApplicationToOfficials(long groupId, String groupName, String memberName, double amount, String firstname, String phoneNumber, String officialLanguage);

    void sendEditContributionToMember(long groupId, String groupName, String creator, String creatorPhone, String language);

    void sendEditContributionToOfficials(long groupId, String groupName, String creator, String firstname, String phonenumber, String officialLanguage);

    void loanDisbursementRequestFailureText(String groupName, String loanedMemberPhone, String loanedMemberName, Double amount, String loanDisbursementFailure, String memberLanguage);

    void sendMonthlyContributionReminder(String firstname, String name, double contributionAmount, String phonenumber, String currentMonthString, String frequency, String language);

    void sendReminderMessage(String firstname, String schedule, String group, double contributionAmount, LocalDate duedate, String phonenumber, String language);

    void sendAccountActivation(String phoneNumber, String memberName, double totalMemberEarnings, String language, String groupName);

    void sendOfficialReminder(String groupName, String memberName, double totalMemberEarnings, String firstname, String phonenumber, String language);

    void sendOfficialsLoanDeclinedMessage(long groupId, String groupName, String loanedMemberName, double amount, String firstname, String phonenumber, String language);
}
