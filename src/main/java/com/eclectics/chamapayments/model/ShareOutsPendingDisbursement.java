package com.eclectics.chamapayments.model;

import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import com.eclectics.chamapayments.service.enums.PaymentEnum;
import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.PrePersist;
import javax.persistence.Table;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@Table(name = "tb_share_outs_pending_disbursement")
public class ShareOutsPendingDisbursement extends Auditable {
    private Long groupId;
    private Double amount;
    private String status;
    private Boolean pending;
    private String groupName;
    private String phoneNumber;
    private String coreAccount;
    private String narration;


    public ShareOutsPendingDisbursement(String coreAccount, String phoneNumber, String groupName,
                                        long groupId, String WAWithdrawalFailure, double memberDeductions, String treasurerPhone) {
        setPending(true);
        setAmount(memberDeductions);
        setCoreAccount(coreAccount);
        setPhoneNumber(phoneNumber);
        setGroupId(groupId);
        setGroupName(groupName);
        setStatus(PaymentEnum.SHARE_OUT_PENDING_DISBURSEMENT.name());
        setNarration(WAWithdrawalFailure);
        setCreatedBy(treasurerPhone);
    }
}
