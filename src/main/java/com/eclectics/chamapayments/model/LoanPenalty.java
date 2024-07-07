package com.eclectics.chamapayments.model;

import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.util.Date;

/**
 *
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_loan_penalty_tbl")
public class LoanPenalty extends Auditable {
    private Long groupId;
    private Long memberId;
    private Double penaltyAmount;
    private String paymentStatus;
    private Double paidAmount;
    private Double dueAmount;
    private String transactionId;
    private Date lastPaymentDate;
    private Date loanDueDate;
    private String paymentPeriod;
    private Date expectedPaymentDate;
    private Date transactionDate;
    @ManyToOne
    @JoinColumn(name = "loandisbursed_id", nullable = false,referencedColumnName = "id")
    private LoansDisbursed loansDisbursed;


}
