package com.eclectics.chamapayments.model;

import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDate;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "contributions_tbl",
        indexes = {@Index(name = "index_contributions_tbl", columnList = "id, groupId, name, memberGroupId, autoShareOut, manualShareOut, active", unique = true)})
public class Contributions extends Auditable {
    private String name;
    private Long groupId;
    private Date startDate;
    private String contributiondetails;
    private long memberGroupId;
    private Double welfareAmt = 0.0;
    private String frequency;
    private String loanInterest;
    private String paymentPeriod;
    private String daysBeforeDue;
    @JsonFormat(pattern = "dd-MM-yyyy HH:mm:ss")
    private Date contributionDate;
    private boolean active;
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
    @JoinColumn(name = "amount_type", nullable = true)
    @JsonProperty("amountType")
    private AmountType amountType;
    @ManyToOne(fetch = FetchType.EAGER)
    private ContributionType contributionType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "schedule_type", nullable = true)
    @JsonProperty("scheduleType")
    private ScheduleTypes scheduleType;
    private Boolean manualShareOut;
    private Boolean autoShareOut;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy HH:mm:ss")
    private Date shareoutDate;

    @PrePersist
    public void addData() {
        this.setActive(true);
        this.setManualShareOut(true);
        this.setAutoShareOut(false);
        this.setIspercentage(true);
        this.setReminder(0);
        this.setDaysBeforeDue(String.valueOf(0));
        this.setLoanInterest(String.valueOf(0));
    }
}
