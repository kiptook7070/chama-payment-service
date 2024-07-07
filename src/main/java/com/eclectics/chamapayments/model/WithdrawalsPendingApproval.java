package com.eclectics.chamapayments.model;

import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import lombok.*;

import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;


@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "withdrawals_pending_approval")
public class WithdrawalsPendingApproval extends Auditable {
    private String phonenumber;
    private String coreAccount;
    private String creditaccount;
    private double amount;
    private String capturedby;
    private String capturedByPhoneNumber;
    private String withdrawal_narration;
    private String withdrawalreason;
    private boolean pending;
    private boolean approved;
    private String approvedby;
    private long groupId;
    private String status;
    private int approvalCount;
    private String paymentType;
    private String paymentStatus;
    @ManyToOne
    @JoinColumn(name = "debitaccount_id", referencedColumnName = "id", nullable = false)
    private Accounts account;
    @ManyToOne
    @JoinColumn(name = "contribution_id", referencedColumnName = "id", nullable = false)
    private Contributions contribution;

}
