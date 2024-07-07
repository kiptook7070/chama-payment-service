package com.eclectics.chamapayments.model;

import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import com.eclectics.chamapayments.service.enums.PaymentEnum;
import com.fasterxml.jackson.annotation.JsonMerge;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "share_outs_payment_tbl",
        indexes = {@Index(name = "index_share_outs_payment_tbl", columnList = "id, groupId, phoneNumber, coreAccount", unique = true)})
public class ShareOutsPayment extends Auditable {
    private Double percentage;
    private String phoneNumber;
    private String coreAccount;
    private Double interestEarn;
    private Double totalContribution;
    private Long groupId;
    private Character monthSatisfied;
    private String currentMonth;
    private String paymentStatus;
    @ManyToOne()
    @JoinColumn(name = "shareO_outs_id", nullable = false)
    @JsonMerge
    @JsonProperty("shareO_outs_id")
    private ShareOuts shareOuts;


    public ShareOutsPayment(Long groupId, String phoneNumber, String coreAccount, String currentMonthString, Double amount, ShareOuts shareOuts) {
        setTotalContribution(amount);
        setPhoneNumber(phoneNumber);
        setCoreAccount(coreAccount);
        setGroupId(groupId);
        setCurrentMonth(currentMonthString);
        setShareOuts(shareOuts);
    }

    @PrePersist
    private void addPayment() {
        setPercentage(1.00);
        setInterestEarn(0.00);
        setMonthSatisfied('Y');
        setPaymentStatus(PaymentEnum.PAYMENT_SUCCESS.name());
    }
}
