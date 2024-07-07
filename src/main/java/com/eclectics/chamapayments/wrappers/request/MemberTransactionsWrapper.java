package com.eclectics.chamapayments.wrappers.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MemberTransactionsWrapper {
    @NotNull(message = "Phone number is required")
    private String phoneNumber;
    @NotNull(message = "Payment type is required")
    private String paymentType;
    @NotNull(message = "Amount is required")
    private double amount;
    private long otherTransactionId;
}
