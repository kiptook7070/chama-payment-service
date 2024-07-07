package com.eclectics.chamapayments.wrappers.response;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GroupLoanProductWrapper {
    private long id;
    private String productname;
    private String description;
    private double max_principal;
    private double min_principal;
    private String interesttype;
    private String paymentperiodtype;
    private long groupId;
    private boolean isPercentagePercentage;
    private boolean isActive;
    private long debitAccountId;
    private long contributionsId;
}
