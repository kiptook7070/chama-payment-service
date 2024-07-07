package com.eclectics.chamapayments.wrappers.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class GroupShareOutsResponseWrapper {
    private long groupId;
    private String status;
    private double amount;
    private String message;
    private String groupName;
    private String phoneNumber;
    private String coreAccount;
    private boolean disbursed;
}
