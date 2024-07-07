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

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "loan_penalty_payment")
public class LoanPenaltyPayment extends Auditable {
    private String transactionId;
    private String receiptNumber;
    private String paymentMethod;
    private Double paidAmount;
    private String paymentStatus;
    private String receiptImageUrl;
    private String mpesaCheckoutId;
    private Date transactionDate;
    @ManyToOne
    @JoinColumn(name="penalty_loan_id",referencedColumnName = "id")
    private LoanPenalty loanPenalty;
}
