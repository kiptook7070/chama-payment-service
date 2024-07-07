package com.eclectics.chamapayments.wrappers.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AccountStatementDto {
    private int size;
    private int page;
    private Date startDate;
    private Date endDate;
    private long groupId;
    private String phoneNumber;
    private String email;
    private long days;
    private String action;
}
