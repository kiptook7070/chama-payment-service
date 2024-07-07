package com.eclectics.chamapayments.wrappers.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class KitTransferWrapper {
    @NotNull(message = "source Acc cannot be null")
    private String sourceAccount;
    @NotNull(message = "destination Acc cannot be null")
    private Long groupId;
    @NotNull(message = "destination Acc cannot be null")
    private String destinationAccount;
    @NotNull(message = "amount cannot be null")
    private Integer amount;
    @NotNull(message = "username cannot be null")
    private String username;
    private String paymentType;
    private String action;

}
