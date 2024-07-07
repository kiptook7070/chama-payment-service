package com.eclectics.chamapayments.wrappers.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * Class name: RequestwithdrawalWrapper
 * Creater: wgicheru
 * Date:4/3/2020
 */
@Getter
@Setter
@NoArgsConstructor
public class RequestwithdrawalWrapper {
    private String action;
    @NotNull(message = "account cannot be empty")
    @ApiModelProperty(value = "the account in which the funds are held")
    private long debitaccountid;

    @NotNull(message = "debit account cannot be null")
    @NotEmpty(message = "debit account cannot be empty")
    private String coreAccount;
    @ApiModelProperty(value = "the phone-number of the member making the withdrawal, funds will be given to this member")
    private String creditaccount;
    /**
     * The Amount.
     */
    @NotNull(message = "amount cannot be empty")
    private double amount;
    /**
     * The Contribution Id.
     */
    @NotNull(message = "contribution ID cannot be empty")
    private long contributionid;

    @NotNull(message = "withdrawal reason cannot be empty")
    private String withdrawalreason;

    private Long groupId;
    private String userName;
    @Size(max = 10, message = "Length cannot be more than 10")
    @NotNull(message = "Payment type cannot be null")
    private String paymentType;
}
