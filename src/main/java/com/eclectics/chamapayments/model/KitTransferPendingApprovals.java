package com.eclectics.chamapayments.model;

import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Date;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "kit_transfer_pending_approvals_tbl")
public class KitTransferPendingApprovals extends Auditable {
    private Long contributionId;
    private Long fineId;
    private Long groupId;
    private String transactionId;
    private String paymentStatus;
    private Integer amount;
    private String phoneNumber;
    private String paymentFailureReason;
    private Long groupAccountId;
    private String paymentType;
    private String sourceAccount;
    private String destianationAccount;
    private Boolean isPenalty;
    private Boolean isFine;
    private String narration;
    private Long penaltyId;
    private String schedulePaymentId;
    private Character isDebit;
    private Character isCredit;
    private Date transactionDate;
    private String approvedBy;
    private String declinedBy;
    private Integer approvalCount;
    private Boolean approved;
    private Boolean pending;
    private String creator;
    private String creatorPhoneNumber;
}
