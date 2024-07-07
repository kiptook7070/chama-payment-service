package com.eclectics.chamapayments.wrappers.response;

import lombok.*;

import java.util.Date;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ContributionsReportWrapper {
    private long id;
    private String name;
    private long groupId;
    private double welfareAmt;
    private Date startDate;
    private String frequency;
    private String loanInterest;
    private String paymentPeriod;
    private String daysBeforeDue;
    private Date contributionDate;
    private int reminder;
    private double contributionAmount;
    private boolean ispercentage;
    private String amountType;
    private String contributionType;
    private String scheduleType;
    private String creator;
    private String creatorPhoneNumber;
    private Date createdOn;
}
