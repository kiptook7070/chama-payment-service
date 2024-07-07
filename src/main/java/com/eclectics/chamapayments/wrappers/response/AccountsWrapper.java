package com.eclectics.chamapayments.wrappers.response;

import lombok.*;

import java.util.Date;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccountsWrapper {
    private long id;
    private String name;
    private String accountdetails;
    private String phoneNumber;
    private long accountType;
    private double accountbalance;
    private double availableBal;
    private long groupId;
    private boolean active;
    private Date balanceRequestDate;
}
