package com.eclectics.chamapayments.model;

import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import com.eclectics.chamapayments.service.enums.PaymentEnum;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.google.gson.JsonObject;
import lombok.*;

import javax.persistence.*;

/**
 * @author kiptoo joshua
 * @createdOn  06/12/2024
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "assign_transaction_pending_pprovals")
public class AssignTransactionPendingApprovals extends Auditable {
    private Long groupId;
    private String transactionId;
    private Long contributionId;
    private String paymentStatus;
    private String paymentType;
    private String paymentForType;
    private Double amount;
    private String phoneNumber;
    private String memberName;
    private Boolean pending;
    private Boolean approved;
    private Boolean rejected;
    private String approvedBy;
    private Integer approvalCount;
    private String creator;
    private String creatorPhoneNumber;
    private Boolean transactionActedOn;
    private String transactionADescription;
    @JsonBackReference
    @ManyToOne
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;
    private Long otherTransactionId;



    public AssignTransactionPendingApprovals(long groupId, String memberPhoneNumber, String memberName, String paymentType, Member member, double amount, String transactionId, long otherTransactionId, long contributionId, String assignerName, String assignerPhoneNumber) {
        setGroupId(groupId);
        setPhoneNumber(memberPhoneNumber);
        setMemberName(memberName);
        setPaymentType(paymentType);
        setMember(member);
        setAmount(amount);
        setTransactionId(transactionId);
        setOtherTransactionId(otherTransactionId);
        setContributionId(contributionId);
        setCreator(assignerName);
        setCreatorPhoneNumber(assignerPhoneNumber);
    }

    @PrePersist
    private void addTransaction(){
        setApproved(false);
        setPending(true);
        setRejected(false);
        setApprovalCount(0);
        setPaymentForType("FT");
        setTransactionActedOn(false);
        setApprovedBy(new JsonObject().toString());
        setPaymentStatus(PaymentEnum.PAYMENT_PENDING.name());
    }
}
