package com.eclectics.chamapayments.wrappers.request;

import lombok.*;



@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareOutsPaymentReport {
    private long id;
    private long groupId;
    private double percentage;
    private String phoneNumber;
    private String coreAccount;
    private double interestEarn;
    private double totalContribution;
    private Character monthSatisfied;
    private String currentMonth;
    private String paymentStatus;
}
