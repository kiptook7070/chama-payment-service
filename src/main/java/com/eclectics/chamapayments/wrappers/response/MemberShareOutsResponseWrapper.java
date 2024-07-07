package com.eclectics.chamapayments.wrappers.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MemberShareOutsResponseWrapper {
    private String status;
    private double amount;
    private String message;
    private String groupName;
    private String phoneNumber;
    private String coreAccount;
    private boolean disbursed;
}
