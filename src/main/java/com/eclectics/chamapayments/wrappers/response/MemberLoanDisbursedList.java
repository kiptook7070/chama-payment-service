package com.eclectics.chamapayments.wrappers.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MemberLoanDisbursedList {
    private String groupName;
    private String memberName;
    private String memberPhone;
    private String loanStatus;
    private String dueamount;
    private String principal;
    private String interest;
    private String createdOn;
}
