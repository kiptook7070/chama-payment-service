package com.eclectics.chamapayments.model;

import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "share_outs_tbl")
public class ShareOuts extends Auditable {
    private LocalDate paymentDate;
    private Double amount;
    private Double newBalance;
    private String phoneNumber;
    private String coreAccount;
    private Long groupId;
    private Long contributionId;
    private Long paymentId;
    private String paymentStatus;
    private String sharOutStatus;
    private Integer year;
    private Month monthYear;
    private String month;
    private Integer week;
    private DayOfWeek dayOfWeek;
    private Character executed;
    private Character member;


    public ShareOuts(Long groupId, long contributionId, Long id, String phoneNumber, String coreAccount, String paymentStatus, int year, Integer amount, Month month, String currentMonth, int weekNumber, DayOfWeek dayOfWeek, LocalDate localDate) {
        setGroupId(groupId);
        setPaymentDate(localDate);
        setContributionId(contributionId);
        setPaymentId(id);
        setPhoneNumber(phoneNumber);
        setCoreAccount(coreAccount);
        setPaymentStatus(paymentStatus);
        setYear(year);
        setAmount(Double.valueOf(amount));
        setMonthYear(month);
        setMonth(currentMonth);
        setWeek(weekNumber);
        setDayOfWeek(dayOfWeek);
        setNewBalance(Double.valueOf(amount));
    }

    @PrePersist
    private void addShare() {
        setExecuted('N');
        setMember('Y');
        setSharOutStatus("ACTIVE");
    }
}
