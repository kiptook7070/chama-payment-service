package com.eclectics.chamapayments.model;

import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;
import java.util.Date;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "contribution_payment",
        indexes = {@Index(name = "index_contribution_payment_tbl", columnList = "id, contributionId, fineId, groupId, paymentType, paymentStatus, phoneNumber, coreAccount, isDebit, isCredit, firstDeposit", unique = true)})
public class ContributionPayment extends Auditable {
    private long contributionId;
    private Long fineId;
    private Long groupId;
    private String transactionId;
    private String paymentStatus;
    private String paymentType;
    private String paymentForType;
    private Integer amount;
    private String phoneNumber;
    private String coreAccount;
    private String paymentFailureReason;
    private Long groupAccountId;
    private Boolean isPenalty;
    private Boolean isFine;
    private String narration;
    private Long penaltyId;
    private String schedulePaymentId;
    private Boolean isCombinedPayment;
    private Character isDebit;
    private Character isCredit;
    private String month;
    private Character contribution;
    private Character shareOut;
    private Date shareOutDate;
    private Character sharesCompleted;
    private Date transactionDate;
    private Boolean firstDeposit;
    private Double paidIn;
    private Double paidOut;
    private Double paidBalance;
    private Double actualBalance;
}
