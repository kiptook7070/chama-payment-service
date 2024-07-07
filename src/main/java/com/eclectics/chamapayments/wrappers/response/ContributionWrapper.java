package com.eclectics.chamapayments.wrappers.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDate;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContributionWrapper {
    private long id;
    private long groupId;
    private String name;
    private String startDate;
    private long memberGroupId;
    private boolean active;
    private Integer reminder;
    private Double penalty;
    private Long contributionAmount;
    private Boolean ispercentage;
    private LocalDate dueDate;
    private String amountType;
    private String contributionTypeName;
    private String scheduleTypeName;
    private double contributionAmountValue;
    @JsonFormat(pattern = "dd-MM-yyyy HH:mm:ss")
    private Date contributionDate;
    private Double welfareAmt;
    private String frequency;
    private String loanInterest;
    private String paymentPeriod;
    private String daysBeforeDue;
    private Boolean isManualShareOut;
    private Boolean isAutoShareOut;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy HH:mm:ss")
    private Date shareoutDate;
}
