package com.eclectics.chamapayments.wrappers.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Getter
@Setter
@NoArgsConstructor
public class ContributionPaymentDto {
    private long groupId;
    private Integer amount;
    private String schedulePaymentId;
    private String beneficiary;
    private String narration;
    @Size(max = 10, message = "Length cannot be more than 10")
    @NotNull(message = "Payment type cannot be null")
    private String paymentType;
    @Size(max = 20, message = "Length cannot be more than 15")
    private String coreAccount = "";
    private Boolean isPenaltyPayment;
    private Boolean isFinePayment;

    private long penaltyId;
    private long fineId;

    private String action;
    private String phoneNumber;
}
