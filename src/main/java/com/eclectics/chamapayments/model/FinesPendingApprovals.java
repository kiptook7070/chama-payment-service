package com.eclectics.chamapayments.model;

import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import com.eclectics.chamapayments.service.enums.PaymentEnum;
import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import java.util.Date;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fines_pending_approvals_tbl")
public class FinesPendingApprovals extends Auditable {
    private Long groupId;
    private Long memberId;
    private String status;
    private String creator;
    private String fineName;
    private Boolean pending;
    private Boolean approved;
    private Double fineAmount;
    private String approvedBy;
    private Double fineBalance;
    private String finedMember;
    private String paymentStatus;
    private Date transactionDate;
    private Integer approvalCount;
    private String fineDescription;
    private String creatorPhone;
    private String finedMemberPhoneNumber;

    public FinesPendingApprovals(long groupId, Long memberId, Double amount, String finedMember, String finedMemberPhone, String creatorName, String creatorMobileNumber, String description) {
        setGroupId(groupId);
        setFineAmount(amount);
        setMemberId(memberId);
        setFineBalance(amount);
        setFineDescription(description);
        setFineName(description);
        setFinedMemberPhoneNumber(finedMemberPhone);
        setFinedMember(finedMember);
        setCreatorPhone(creatorMobileNumber);
        setCreator(creatorName);
        setApproved(false);
        setPending(true);
        setApprovalCount(0);
        setApprovedBy(new JsonObject().toString());
        setStatus(PaymentEnum.PAYMENT_PENDING.name());
        setPaymentStatus(PaymentEnum.PAYMENT_PENDING.name());
        setTransactionDate(new Date());
        setCreatedOn(new Date());
    }
}
