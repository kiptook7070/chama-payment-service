package com.eclectics.chamapayments.wrappers.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class PenaltyWrapper {
    private long id;
    private long userId;
    private String memberNames;
    private boolean isPaid;
    private String schedulePaymentId;
    private String paymentPhoneNumber;
    private String contributionName;
    private String paymentStatus;
    private String expectedPaymentDate;
    private long contributionId;
    private long groupId;
    private double amount;
    private String transactionId;
    private LocalDate defaultDate;
}
