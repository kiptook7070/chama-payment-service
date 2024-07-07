package com.eclectics.chamapayments.wrappers.request;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;

@Getter
@Setter
public class LoanInterestWrapper {
    @NotNull(message = "group id cannot be null")
    private long groupId;
    @NotNull(message = "loan amount cannot be null")
    private double loanAmount;
}
