package com.eclectics.chamapayments.wrappers.response;

import lombok.*;

import java.util.Date;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FullStatementWrapper {
    public Long id;
    public String createdBy;
    public Date createdOn;
    public String lastModifiedBy;
    public Date lastModifiedDate;
    public boolean softDelete;
    public Long contributionId;
    public Long groupId;
    public String transactionId;
    public String paymentStatus;
    public Double amount;
    public String phoneNumber;
    public String paymentFailureReason;
    public Long groupAccountId;
    public String paymentType;
    public boolean isPenalty;
    public boolean isFine;
    public String narration;
    public boolean isCombinedPayment;
    public Character isDebit;
    public Character isCredit;
    public String month;
    public String shareOut;
    public Date shareOutDate;
    public Character sharesCompleted;

}
