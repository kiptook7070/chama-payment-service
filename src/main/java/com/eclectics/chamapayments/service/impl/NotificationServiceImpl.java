package com.eclectics.chamapayments.service.impl;

import com.eclectics.chamapayments.model.MessageTemplates;
import com.eclectics.chamapayments.model.Notifications;
import com.eclectics.chamapayments.repository.MessagetemplatesRepo;
import com.eclectics.chamapayments.repository.NotificationsRepository;
import com.eclectics.chamapayments.service.NotificationService;
import com.eclectics.chamapayments.service.PublishingService;
import com.eclectics.chamapayments.wrappers.response.MemberWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * @author Alex Maina
 * @created 07/12/2021
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final PublishingService publishingService;
    private final MessagetemplatesRepo messagetemplatesRepo;
    private final NotificationsRepository notificationsRepository;

    private static NumberFormat numberFormat() {
        NumberFormat numberFormat = NumberFormat.getInstance();
        numberFormat.setGroupingUsed(true);
        return numberFormat;
    }

    @Override
    public void sendLoanApprovalText(MemberWrapper member, Double amount, String language) {
        MessageTemplates messageTemplates = messagetemplatesRepo.findByTypeAndLanguage("loan_approved", language);
        String message = String.format(messageTemplates.getTemplate(), member.getFirstname(), numberFormat().format(amount));

        publishingService.sendPostBankText(message, member.getPhonenumber());

    }

    @Override
    public void sendGuarantorsInviteMessage(List<String> phoneNumbers, Double amount, String loanRecipientName, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("guarantor_request", language);

        phoneNumbers.parallelStream()
                .forEach(phoneNumber -> {
                    String message = String.format(messageTemplate.getTemplate(), loanRecipientName, amount);
                    publishingService.sendPostBankText(message, phoneNumber);
                });
    }

    @Override
    public void sendGuarantorshipApprovalMessage(String guarantorPhone, String guarantorName, String applicantPhone, String applicantName, double amount, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("guarantor_accept_request", language);
        String message = String.format(messageTemplate.getTemplate(), applicantName, guarantorName, numberFormat().format(amount));

        publishingService.sendPostBankText(message, guarantorPhone);
    }

    @Override
    public void sendGuarantorshipDeclinedMessage(String guarantorPhone, String guarantorName, String applicantPhone, String applicantName, double amount, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("guarantor_decline_request", language);
        String message = String.format(messageTemplate.getTemplate(), applicantName, guarantorName, numberFormat().format(amount));

        publishingService.sendPostBankText(message, guarantorPhone);
    }

    @Override
    public void sendGroupGuarantorshipDeclinedMessage(String memberPhone, String guarantorName, String applicantPhone, String applicantName, double amount, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("guarantor_decline_request_group", language);
        String message = String.format(messageTemplate.getTemplate(), guarantorName, applicantName, numberFormat().format(amount));

        publishingService.sendPostBankText(message, memberPhone);
    }

    @Override
    public void sendGroupGuarantorshipApprovedMessage(String memberPhone, String guarantorName, String applicantPhone, String applicantName, double amount, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("guarantor_accept_request_group", language);
        String message = String.format(messageTemplate.getTemplate(), guarantorName, applicantName, numberFormat().format(amount));

        publishingService.sendPostBankText(message, memberPhone);
    }

    @Override
    public void sendLoanApprovedMessage(String phoneNumber, String name, double amount, String language) {
        String formattedAmount = String.format("%.2f", amount);
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("loan_approved", language);
        String message = String.format(messageTemplate.getTemplate(), name, formattedAmount);

        publishingService.sendPostBankText(message, phoneNumber);
    }

    @Override
    public void sendLoanDeclinedMessage(String phoneNumber, String name, double amount, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("loan_declined", language);
        String message = String.format(messageTemplate.getTemplate(), name, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phoneNumber);
    }

    @Override
    public void sendLoanDisbursedMessage(String phoneNumber, String name, double amount, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("loan_disbursed", language);
        String message = String.format(messageTemplate.getTemplate(), name, numberFormat().format(amount));
        publishingService.sendPostBankText(message, phoneNumber);
    }


    @Override
    public void sendWithdrawalRequestApprovedText(String phoneNumber, String name, double amount, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("withdrawal_approved", language);
        String message = String.format(messageTemplate.getTemplate(), name, formatAmount(amount), getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phoneNumber);

    }

    @Override
    public void sendB2cError(String phoneNumber, String name, double amount, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("", language);
        String message = String.format(messageTemplate.getTemplate(), name, numberFormat().format(amount));

        publishingService.sendPostBankText(message, phoneNumber);
    }

    @Override
    public void sendPenaltyFailureMessage(String phoneNumber, String name, Integer amount, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("penalty_payment_failure", language);
        String message = String.format(messageTemplate.getTemplate(), name, numberFormat().format(amount));

        publishingService.sendPostBankText(message, phoneNumber);
    }

    @Override
    public void sendPenaltySuccessMessage(String phoneNumber, String name, Integer amount, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("penalty_payment_success", language);
        String message = String.format(messageTemplate.getTemplate(), name, numberFormat().format(amount));

        publishingService.sendPostBankText(message, phoneNumber);
    }

    @Override
    public void sendContributionFailureMessage(String phoneNumber, String name, Integer amount, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("contribution_payment_failure", language);
        String message = String.format(messageTemplate.getTemplate(), name, formatAmount(Double.valueOf(amount)));

        publishingService.sendPostBankText(message, phoneNumber);
    }

    @Override
    public void sendContributionSuccessMessage(String phoneNumber, String name, Integer amount, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("contribution_payment_success", language);
        String message = String.format(messageTemplate.getTemplate(), name, formatAmount(Double.valueOf(amount)));
        publishingService.sendPostBankText(message, phoneNumber);
    }

    @Override
    public void sendContributionSuccessMessageToGroup(String phoneNumber, String memberName, String groupName, Integer amount, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("contribution_payment_success_group", language);
        String message = String.format(messageTemplate.getTemplate(), memberName, formatAmount(Double.valueOf(amount)), groupName);

        publishingService.sendPostBankText(message, phoneNumber);
    }

    @Override
    public void sendPenaltySuccessMessageToGroup(String phoneNumber, String memberName, String groupName, Integer amount, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("penalty_payment_success_group", language);
        String message = String.format(messageTemplate.getTemplate(), memberName, formatAmount(Double.valueOf(amount)), groupName);

        publishingService.sendPostBankText(message, phoneNumber);
    }


    @Override
    public void sendLoanDisbursementTextToGroup(String phoneNumber, String memberName, String groupName, double amount, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("loan_disburse_success_group", language);
        String message = String.format(messageTemplate.getTemplate(), memberName, formatAmount(amount), groupName);

        publishingService.sendPostBankText(message, phoneNumber);

    }

    @Override
    public void sendLoanRepaymentSuccessText(String phoneNumber, String memberName, String groupName, int amountPaid, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("loan_repayment_success", language);
        String message = String.format(messageTemplate.getTemplate(), memberName, groupName, formatAmount((double) amountPaid));

        publishingService.sendPostBankText(message, phoneNumber);

    }

    @Override
    public void sendLoanRepaymentFailureText(String phoneNumber, String memberName, String groupName, int amount, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("loan_repayment_failure", language);
        String message = String.format(messageTemplate.getTemplate(), memberName, formatAmount((double) amount), groupName);

        publishingService.sendPostBankText(message, phoneNumber);

    }

    @Override
    public void sendContributionWithdrawalFailure(String phoneNumber, String firstname, String groupName, int amount, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("withdrawal_failure", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, formatAmount((double) amount), groupName);

        publishingService.sendPostBankText(message, phoneNumber);

    }

    @Override
    public void sendContributionWithdrawalSuccess(String phoneNumber, String firstname, String groupName, int amount, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("withdrawal_success", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, formatAmount((double) amount), groupName);

        publishingService.sendPostBankText(message, phoneNumber);

    }

    @Override
    public void sendContributionWithdrawalToGroup(String phoneNumber, String memberName, String groupName, double amount, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("withdrawal_success_group", language);
        String message = String.format(messageTemplate.getTemplate(), memberName, formatAmount(amount), groupName);

        publishingService.sendPostBankText(message, phoneNumber);

    }

    @Override
    public void sendPenaltyCreatedMessage(String phoneNumber, String memberName, String scheduledId, String groupName, double amount, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("penalty_created", language);
        String message = String.format(messageTemplate.getTemplate(), memberName, formatAmount(amount), groupName, scheduledId);

        publishingService.sendPostBankText(message, phoneNumber);

    }

    @Override
    public void sendPenaltyCreatedMessageToGroup(String phoneNumber, String memberName, String scheduledId, double amount, String language, String groupName) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("penalty_created_group", language);
        String message = String.format(messageTemplate.getTemplate(), memberName, formatAmount(amount), groupName, scheduledId);
        publishingService.sendPostBankText(message, phoneNumber);
    }

    @Override
    public void sendOutstandingPaymentConfirmation(String phoneNumber, String firstname, int dueAmount, String groupName, int remainder, String scheduledId, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("outsanding_contribution_payment", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, formatAmount((double) dueAmount), groupName, scheduledId, remainder);

        publishingService.sendPostBankText(message, phoneNumber);
    }

    @Override
    public void sendMemberWithdrawRequestText(String memberName, double amount, String groupName, String phoneNumber, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("member_withdraw_request", language);
        String message = String.format(messageTemplate.getTemplate(), memberName, formatAmount(amount), groupName, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phoneNumber);
    }

    @Override
    public void sendLoanProductCreated(String memberName, String productName, String groupName, String phoneNumber, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("loan_product_created", language);
        String message = String.format(messageTemplate.getTemplate(), memberName, productName, groupName);
        publishingService.sendPostBankText(message, phoneNumber);
    }

    @Override
    public void sendLoanProductEdited(String memberName, String productName, String groupName, double maxPrincipal,
                                      double minPrincipal, int userSavingValue, Integer penaltyValue, String phoneNumber, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("loan_product_updated", language);
        String message = String.format(messageTemplate.getTemplate(), memberName, productName, groupName, formatAmount(maxPrincipal), formatAmount(minPrincipal), userSavingValue, penaltyValue);
        publishingService.sendPostBankText(message, phoneNumber);
    }

    @Override
    public void sendLoanProductActivatedMessage(String memberName, String productName, String groupName, String phoneNumber, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("loan_product_activated", language);
        String message = String.format(messageTemplate.getTemplate(), memberName, productName, groupName);
        publishingService.sendPostBankText(message, phoneNumber);
    }

    @Override
    public void sendLoanProductDeactivatedMessage(String memberName, String productName, String groupName, String phoneNumber, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("loan_product_deactivated", language);
        String message = String.format(messageTemplate.getTemplate(), memberName, productName, groupName);

        publishingService.sendPostBankText(message, phoneNumber);
    }

    @Override
    public void sendGroupEnableWithdrawalSms(String firstname, String lastname, String name, String phonenumber, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("enable_group_withdrawal", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, name, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phonenumber);
    }

    @Override
    public void sendGroupDisableWithdrawalSms(String firstname, String name, String phonenumber, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("disable_group_withdrawal", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, name, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phonenumber);
    }

    @Override
    public void sendPostBankEmail(String message, String email, String name) {
        publishingService.sendPostBankEmail(message, email, name);
    }

    @Override
    public void sendFirstDepositSmsToOfficials(String firstname, String group, Integer amount, String phonenumber, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("first_group_deposit", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, group, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phonenumber);
    }

    @Override
    public void sendDepositSmsToGroupMembers(String firstname, String depositor, String groupName, Integer amount, String transactionId, String phonenumber, String maskedCardNumber, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("send_deposit_sms_to_members", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, formatAmount(Double.valueOf(amount)), maskedCardNumber, transactionId, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phonenumber);
    }


    @Override
    public void sendKittyTransferDeclineText(String firstname, String memberName, String groupName, String phonenumber, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("kit_transfer_declined", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, memberName, groupName, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phonenumber);
    }


    @Override
    public void sendWithdrawalRequestFailureText(String phonenumber, String firstname, String waWithdrawalFailure, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("withdrawal_to_account_failed", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, waWithdrawalFailure, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phonenumber);
    }

    @Override
    public void sendOfficialWithdrawRequestText(String firstname, String memberName, String name, double amount, String phonenumber, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("official_withdraw_request", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, memberName, formatAmount(amount), name, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phonenumber);
    }

    @Override
    public void sendOfficialWithdrawRequestTextForCoreAccount(String firstname, String coreAccount, String name, double amount, String phonenumber, String language, String capturedBy) {

        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("group_withdrawal_request_to_core", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, capturedBy, formatAmount(amount), name, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phonenumber);
    }

    @Override
    public void sendApprovedWithdrawRequestTextToMembers(String firstname, String memberName, String name, int amount, String phonenumber, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("withdrawal_approved_to_members", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, memberName, formatAmount((double) amount), name, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phonenumber);
    }

    @Override
    public void sendMembersDeclinedWithdrawRequestText(String firstname, String approverName, String memberName, String groupName, double amount, String phoneNumber, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("withdrawal_declined_to_members", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, approverName, memberName, formatAmount(amount), groupName, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phoneNumber);
    }

    @Override
    public void sendWithdrawalRequestDeclineText(String memberName, String approverName, double amount, String name, String phonenumber, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("withdrawal_declined", language);
        String message = String.format(messageTemplate.getTemplate(), memberName, approverName, formatAmount(amount), name, getCurrentDate(), getCurrentTime());

        publishingService.sendPostBankText(message, phonenumber);
    }

    @Override
    public void sendMemberStatement(String toEmail, String memberName, String phoneNumber, String language, String group) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("member_statement", language);
        String message = String.format(messageTemplate.getTemplate(), memberName, group, toEmail, getCurrentDate(), getCurrentTime());

        publishingService.sendPostBankText(message, phoneNumber);
    }


    @Override
    public void sendGroupStatement(String firstname, String groupName, String phonenumber, String language, String toEmail, String memberName) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("group_statement", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, memberName, groupName, toEmail, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phonenumber);
    }

    @Override
    public void sendMembersDeclinedFineText(String firstname, String approverName, String memberName, String group, Double fineAmount, String phonenumber, String language, String fineName) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("send_fine_declined_to_group_members", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, memberName, fineName, fineAmount, approverName, group, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phonenumber);
    }

    @Override
    public void sendFineDeclineText(String memberName, String approverName, Double fineAmount, String group, String phonenumber, String fineName, String language, long groupId) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("send_fine_declined_to_fined_member", language);
        String message = String.format(messageTemplate.getTemplate(), memberName, fineName, formatAmount(fineAmount), approverName, group, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phonenumber);
        String notificationMessage = memberName.concat(" fine of Kshs.") + formatAmount(fineAmount).concat(" in group ") + group.concat(" has been declined");
        saveGroupNotification(groupId, group, notificationMessage);
    }

    @Override
    public void sendFineSmsToMember(String finedMember, String initiator, String groupName, Double amount, String description, String phonenumber, String language, long groupId) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("send_fine_to_member", language);
        String message = String.format(messageTemplate.getTemplate(), finedMember, formatAmount(amount), description, initiator, groupName, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phonenumber);
        //add notification
        String notificationMessage = finedMember.concat(" has been fined Kshs.") + formatAmount(amount).concat(" in group ") + groupName;
        saveGroupNotification(groupId, groupName, notificationMessage);

    }

    private void saveGroupNotification(long groupId, String groupName, String notificationMessage) {
        Notifications notifications = new Notifications();
        notifications.setGroupName(groupName);
        notifications.setGroupId(groupId);
        notifications.setMessage(notificationMessage);
        notificationsRepository.save(notifications);
    }

    @Override
    public void sendFineSmsToGroupMembers(String finedMember, String initiator, String groupName, Double amount, String description, String firstname, String phonenumber, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("send_fine_to_group_member", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, description, formatAmount(amount), finedMember, initiator, groupName, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phonenumber);
    }

    @Override
    public void sendFineApprovedText(String memberName, String approverName, Double fineAmount, String group, String phonenumber, String fineName, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("send_fine_approved_to_fined_member", language);
        String message = String.format(messageTemplate.getTemplate(), memberName, fineName, formatAmount(fineAmount), group, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phonenumber);
    }

    @Override
    public void sendMembersApprovedFineText(String firstname, String approverName, String memberName, String group, Double fineAmount, String phonenumber, String language, String fineName) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("send_fine_approved_to_group_members", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, fineName, memberName, group, getCurrentDate(), getCurrentTime(), approverName);
        publishingService.sendPostBankText(message, phonenumber);
    }

    @Override
    public void sendPendingLoansMessage(String firstname, String phonenumber, double remainingAmount, String language, String group, double totalAmount) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("pending_loan_applied", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, formatAmount(remainingAmount), formatAmount(totalAmount), group);
        publishingService.sendPostBankText(message, phonenumber);
    }

    @Override
    public void sendOverPaidLoansMessage(String firstname, String group, double totalAmount, double overPaid, String phonenumber, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("send_over_paid_loan", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, formatAmount(totalAmount), group, formatAmount(overPaid));
        publishingService.sendPostBankText(message, phonenumber);
    }

    @Override
    public void sendSettledLoansMessage(String firstname, String group, double totalAmount, String phonenumber, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("send_settled_loan", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, formatAmount(totalAmount), group, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phonenumber);
    }

    @Override
    public void sendMemberOtherTransactionText(String groupName, double amount, String creator, String otherMember, String memberPhone, String channel, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("send_member_deposit_from_channel", language);
        String message = String.format(messageTemplate.getTemplate(), otherMember, formatAmount(amount), channel, groupName, creator, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, memberPhone);
    }

    @Override
    public void sendMembersOtherTransactionText(String groupName, double amount, String creator, String otherMember, String firstname, String phonenumber, String channel, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("send_members_deposit_from_channel", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, otherMember, formatAmount(amount), channel, groupName, creator, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phonenumber);
    }

    @Override
    public void sendDeclinedOtherTransactionTextToMember(String groupName, double amount, String approverName, String memberName, String phonenumber, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("decline_member_deposit_from_channel", language);
        String message = String.format(messageTemplate.getTemplate(), memberName, formatAmount(amount), groupName, approverName, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phonenumber);
    }

    @Override
    public void sendDeclinedOtherTransactionTextToMembers(String groupName, double amount, String approverName, String memberName, String firstname, String phonenumber, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("decline_members_deposit_from_channel", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, memberName, formatAmount(amount), groupName, approverName, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phonenumber);
    }

    @Override
    public void sendApprovedOtherTransactionTextToMember(String groupName, Double amount, String approverName, String memberName, String phonenumber, String channel, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("approve_member_deposit_from_channel", language);
        String message = String.format(messageTemplate.getTemplate(), memberName, formatAmount(amount), channel, groupName, approverName, getCurrentDate(), getCurrentTime());

        publishingService.sendPostBankText(message, phonenumber);
    }

    @Override
    public void sendApprovedOtherTransactionTextToMembers(String groupName, Double amount, String approverName, String memberName, String firstname, String phonenumber, String channel, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("approve_members_deposit_from_channel", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, memberName, formatAmount(amount), channel, groupName, approverName, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phonenumber);
    }

    @Override
    public void sendShareOutWithdrawalRequestFailureText(String phoneNumber, String memberName, String waWithdrawalFailure, String language, String groupName, double finalAmount) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("send_declined_share_out", language);
        String message = String.format(messageTemplate.getTemplate(), memberName, formatAmount(finalAmount), groupName);
        publishingService.sendPostBankText(message, phoneNumber);
    }

    @Override
    public void sendShareOutWithdrawalToMembers(String firstname, String groupName, String phonenumber, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("send_members_share_out", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, groupName, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phonenumber);
    }

    @Override
    public void sendShareOutWithdrawalToMember(String phoneNumber, String memberName, double finalAmount, String language, String groupName) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("send_member_share_out", language);
        String message = String.format(messageTemplate.getTemplate(), memberName, groupName, getCurrentDate(), getCurrentTime(), formatAmount(finalAmount));
        publishingService.sendPostBankText(message, phoneNumber);
    }

    @Override
    public void sendNoShareOutWithdrawalRequestFailureText(String phoneNumber, String groupName, String firstname, double memberLoans, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("send_no_share_out_sms", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, groupName, formatAmount(memberLoans));
        publishingService.sendPostBankText(message, phoneNumber);
    }

    @Override
    public void sendEditContributionDeclineText(String groupName, String memberName, String phoneNumber, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("send_member_edit_contribution_declined", language);
        String message = String.format(messageTemplate.getTemplate(), memberName, groupName, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phoneNumber);
    }

    @Override
    public void sendEditContributionDeclineTextToGroup(String memberName, String groupName, String firstname, String phonenumber, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("send_members_edit_contribution_declined", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, memberName, groupName, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phonenumber);
    }

    @Override
    public void sendInitiatorEditContributionAcceptedText(String groupName, String memberPhoneNumber, String memberName, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("send_member_edit_contribution_accepted", language);
        String message = String.format(messageTemplate.getTemplate(), memberName, groupName, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, memberPhoneNumber);
    }

    @Override
    public void sendMembersEditContributionAcceptedText(String firstname, String memberName, String groupName, String phonenumber, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("send_members_edit_contribution_accepted", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, memberName, groupName, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phonenumber);
    }

    @Override
    public void sendAnonymousShareOutsText(String firstname, String groupName, String phonenumber, String phoneNumber, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("send_officials_anonymous_shares", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, phoneNumber, groupName);
        publishingService.sendPostBankText(message, phonenumber);
    }

    @Override
    public void sendLoanToLoanedMember(String groupName, double amount, String memberName, String loanedMemberPhone, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("send_loan_applied_to_member", language);
        String message = String.format(messageTemplate.getTemplate(), memberName, formatAmount(amount), groupName, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, loanedMemberPhone);
    }

    @Override
    public void sendLoanApplicationToOfficials(long groupId, String groupName, String memberName, double amount, String firstname, String phoneNumber, String officialLanguage) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("loan_applied", officialLanguage);
        String message = String.format(messageTemplate.getTemplate(), firstname, memberName, formatAmount(amount), groupName, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phoneNumber);
    }

    @Override
    public void sendEditContributionToMember(long groupId, String groupName, String creator, String creatorPhone, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("contribution_edit_request_to_member", language);
        String message = String.format(messageTemplate.getTemplate(), creator, groupName, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, creatorPhone);
    }

    @Override
    public void sendEditContributionToOfficials(long groupId, String groupName, String creator, String firstname, String phonenumber, String officialLanguage) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("contribution_edit_request", officialLanguage);
        String message = String.format(messageTemplate.getTemplate(), firstname, creator, groupName, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phonenumber);
    }

    @Override
    public void loanDisbursementRequestFailureText(String groupName, String loanedMemberPhone, String loanedMemberName, Double amount, String loanDisbursementFailure, String memberLanguage) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("loan_disbursement_member_failed", memberLanguage);
        String message = String.format(messageTemplate.getTemplate(), loanedMemberName, formatAmount(amount), groupName, loanDisbursementFailure);
        publishingService.sendPostBankText(message, loanedMemberPhone);
    }

    @Override
    public void sendMonthlyContributionReminder(String firstname, String name, double contributionAmount, String phonenumber, String currentMonthString, String frequency, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("send_monthly_contribution_reminder", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, currentMonthString, frequency, formatAmount(contributionAmount), name);
        publishingService.sendPostBankText(message, phonenumber);
    }

    @Override
    public void sendReminderMessage(String firstname, String schedule, String group, double contributionAmount, LocalDate duedate, String phonenumber, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("contribution_payment_reminder", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, schedule, contributionAmount, group, duedate);
        publishingService.sendPostBankText(message, phonenumber);
    }

    @Override
    public void sendAccountActivation(String phoneNumber, String memberName, double totalMemberEarnings, String language, String groupName) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("send_member_account_activation", language);
        String message = String.format(messageTemplate.getTemplate(), memberName, groupName, getCurrentDate(), getCurrentTime(), formatAmount(totalMemberEarnings));
        publishingService.sendPostBankText(message, phoneNumber);
    }

    @Override
    public void sendOfficialReminder(String groupName, String memberName, double totalMemberEarnings, String firstname, String phonenumber, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("send_official_member_withdrawal_reminder", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, memberName, formatAmount(totalMemberEarnings), groupName);
        publishingService.sendPostBankText(message, phonenumber);
    }

    /**
     * @param groupId
     * @param groupName
     * @param loanedMemberName
     * @param amount
     * @param firstname
     * @param phonenumber
     * @param language
     */
    @Override
    public void sendOfficialsLoanDeclinedMessage(long groupId, String groupName, String loanedMemberName, double amount, String firstname, String phonenumber, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("send_officials_loan_declined", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, amount, loanedMemberName, groupName, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phonenumber);
    }


    @Override
    public void sendKitTransferSms(String firstname, String memberName, String groupName, double amount, String phoneNumber, String from, String to, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("kit_transfer_from", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, memberName, formatAmount(amount), from, to, groupName, getCurrentDate(), getCurrentTime());
        publishingService.sendPostBankText(message, phoneNumber);
    }


    @Override
    public void sendKittyTransferAcceptedText(String firstname, String memberName, String groupName, double amount, String phonenumber, String language) {
        MessageTemplates messageTemplate = messagetemplatesRepo.findByTypeAndLanguage("kit_transfer_accepted", language);
        String message = String.format(messageTemplate.getTemplate(), firstname, memberName, groupName, getCurrentDate(), getCurrentTime());

        publishingService.sendPostBankText(message, phonenumber);
    }

    public String getCurrentDate() {
        LocalDateTime currentDate = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        String formattedDate = currentDate.format(formatter);
        return formattedDate;
    }

    public String getCurrentTime() {
        LocalDateTime currentTime = LocalDateTime.now();
        ZonedDateTime formatTime = currentTime.atZone(ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        String currentTimeFormat = formatTime.format(formatter);
        return currentTimeFormat;
    }

    public String formatAmount(Double amount) {
        String formattedAmount = String.format("%.2f", amount);
        return formattedAmount;
    }
}
