package com.eclectics.chamapayments.model;

import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDate;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class ContributionsPendingApprovals extends Auditable {
    private String name;
    private Long groupId;
    private Date startDate;
    private double welfareAmt;
    private String contributiondetails;
    private String frequency;
    private String loanInterest;
    private String paymentPeriod;
    private String daysBeforeDue;
    @JsonFormat(pattern = "dd-MM-yyyy HH:mm:ss")
    private Date contributionDate;
    @Column(name = "reminder")
    private Integer reminder;
    @Column(name = "penalty")
    private Double penalty;
    @Column(name = "contribution_amount")
    private double contributionAmount = 0.0;
    @Column(name = "ispenaltypercentage")
    private Boolean ispercentage;
    @Column(name = "duedate")
    private LocalDate duedate;
    @ManyToOne
    @JoinColumn(name = "amount_type")
    @JsonProperty("amountType")
    private AmountType amountType;
    @ManyToOne(fetch = FetchType.EAGER)
    private ContributionType contributionType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "schedule_type")
    @JsonProperty("scheduleType")
    private ScheduleTypes scheduleType;
    private Boolean manualShareOut;
    private Boolean autoShareOut;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy HH:mm:ss")
    private Date shareoutDate;
    private boolean approvalStaged;
    private boolean approvalProcessed;
    private String approvedBy;
    private int approvalCount;
    private boolean approved;
    private Boolean pending;
    private String creator;
    private String creatorPhoneNumber;
    private Date createdOn;

    @PostPersist
    public void addData() {
        this.setApprovalStaged(true);
        this.setApprovalProcessed(false);
        this.setPending(true);
        this.setApprovalCount(0);
        this.setApproved(false);
        this.setCreatedOn(new Date());
    }
}
