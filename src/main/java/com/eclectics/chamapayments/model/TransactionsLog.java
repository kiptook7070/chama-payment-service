package com.eclectics.chamapayments.model;

import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import lombok.*;

import javax.persistence.*;
import java.util.Date;

@Getter
@Setter
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "account_transactions_log")
public class TransactionsLog extends Auditable {
    @Column(name = "unique_transaction_id")
    private String uniqueTransactionId;
    @Column(name = "contribution_narration")
    private String contributionNarration;
    private String status;
    private String debitphonenumber;
    private double oldbalance;
    private double transamount;
    private String paymentType;
    private double newbalance;
    private String capturedby;
    private String approvedby;
    private Date transactionDate;
    @Column(name = "trx_type")
    private String transactionType;
    @ManyToOne
    @JoinColumn(name = "contribution_id",referencedColumnName = "id",nullable = false)
    private Contributions contributions;

    @ManyToOne
    @JoinColumn(name = "creditaccount_id",referencedColumnName = "id",nullable = false)
    private Accounts creditaccounts;
    private String debitaccounts;

}
