package com.eclectics.chamapayments.wrappers.response;


import lombok.*;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Date;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class LoanproductWrapper {
    private long productid;
    @NotNull(message = "name cannot be null")
    @NotEmpty(message = "name cannot be empty")
    private String productname;
    private String description;
    @NotNull(message = "max_principal cannot be null")
    private double max_principal = 999999;
    @NotNull(message = "min_principal cannot be null")
    private double min_principal = 1;
    private String interesttype;
    private double interestvalue;
    private int paymentperiod;
    private String paymentperiodtype;
    private long contributionid;
    private String contributionname;
    private double contributionbalance;
    private String groupname;
    @NotNull(message = "groupid cannot be null")
    private long groupid;
    private Boolean isguarantor;
    private Boolean hasPenalty = false;
    private Integer penaltyvalue;
    private Boolean ispenaltypercentage;
    private Integer usersavingvalue = 100;
    private Double userLoanLimit = 0.0;
    private Long debitAccountId;
    private Boolean isActive = true;
    private String penaltyPeriod;
    private Integer gracePeriod = 1;
    private Date transactionDate;
}
