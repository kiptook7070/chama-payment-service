package com.eclectics.chamapayments.wrappers.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Date;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EditContributionWrapper {
    private double contributionAmt;
    private double welfareAmt;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy HH:mm:ss")
    private Date contributionDate;
    private  String frequency;
    private Integer reminders;
    private String loanInterest;
    private String paymentPeriod;
    private String daysBeforeDue;
    private LocalDate dueDate;
    private long groupid;
    private boolean isManualShareOut;
    private boolean isAutoShareOut;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy HH:mm:ss")
    private Date shareoutDate;
    private Boolean ispercentage;
    private double penalty;
}
