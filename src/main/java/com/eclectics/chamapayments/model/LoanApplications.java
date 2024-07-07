package com.eclectics.chamapayments.model;

import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import lombok.*;

import javax.persistence.*;
import java.util.Date;

@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@Getter
@Setter
@ToString
@Table(name = "loan_applications")
public class LoanApplications extends Auditable {
    private String status;
    private Long groupId;
    private long memberId;
    private double amount;
    private String reminder;
    private int unpaidloans;
    private boolean pending;
    private boolean approved;
    private String approvedby;
    private int approvalCount;
    private boolean isUsingWallet;
    private String accountToDeposit;
    private Date transactionDate;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name="loanproductid",referencedColumnName = "id")
    @JsonManagedReference
    private LoanProducts loanProducts;
    private String creator;
    private String creatorPhoneNumber;
}
