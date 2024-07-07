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
@Table(name = "share_outs_disbursed_tbl")
public class ShareOutsDisbursed extends Auditable {
    private Long groupId;
    private String status;
    private Double amount;
    private String message;
    private String groupName;
    private String phoneNumber;
    private String coreAccount;
    private Boolean disbursed;
    private Boolean notificationSend;


    public ShareOutsDisbursed(String coreAccount, String phoneNumber, long groupId, String
            groupName, String disbursalMessage, double disbursalAmount, String treasurerPhone) {
        setDisbursed(true);
        setAmount(disbursalAmount);
        setCoreAccount(coreAccount);
        setPhoneNumber(phoneNumber);
        setStatus(PaymentEnum.SHARE_OUT_DISBURSED_SUCCESSFULLY.name());
        setGroupId(groupId);
        setGroupName(groupName);
        setMessage(disbursalMessage);
        setCreatedBy(treasurerPhone);
        setNotificationSend(false);
    }
}
