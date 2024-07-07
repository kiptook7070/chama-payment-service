package com.eclectics.chamapayments.wrappers.response;

import lombok.*;

import java.time.LocalDate;
import java.util.Date;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContributionsWrapper {
    private long id;
    private String name;
    private long groupId;
    private Date startDate;
    private String contributiondetails;
    private long memberGroupId;
    private double welfareAmt;
    private String frequency;
    private String loanInterest;
    private String paymentPeriod;
    private String daysBeforeDue;
    private boolean active;
    private int reminder;
    private double penalty;
    private double contributionAmount;
    private boolean ispercentage;
    private long amountTypeId;
    private long contributionTypeId;
    private long scheduleTypeId;
    private boolean manualShareOut;
    private boolean autoShareOut;
    private Date shareoutDate;
}
