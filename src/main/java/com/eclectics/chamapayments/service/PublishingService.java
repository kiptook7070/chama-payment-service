package com.eclectics.chamapayments.service;

public interface PublishingService {

    void sendPostBankText(String message, String phoneNumber);

    void sendPostBankEmail(String message, String toEmail, String groupName);
    void writeOffLoansAndPenalties(long memberId, long groupId);


}
