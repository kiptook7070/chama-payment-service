package com.eclectics.chamapayments.model;

import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import lombok.*;

import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.util.Date;


@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "account_withdrawal_log")
public class WithdrawalLogs extends Auditable {
    private String uniqueTransactionId;
    private String contribution_narration;
    private String creditphonenumber;
    private String creditCoreAccount;
    private double oldbalance;
    private double transamount;
    private double newbalance;
    private String capturedby;
    private String withdrawalreason;
    private String transferToUserStatus;
    private long memberGroupId;
    private Date transactionDate;
    private String paymentType;
    private String paymentStatus;
    private Boolean loan;
    @ManyToOne
    @JoinColumn(name = "debitaccount_id", referencedColumnName = "id", nullable = false)
    private Accounts debitAccounts;
    @ManyToOne
    @JoinColumn(name = "contribution_id", referencedColumnName = "id", nullable = false)
    private Contributions contributions;
}
