package com.eclectics.chamapayments.model;

import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@Table(name = "group_share_outs_tbl",
        indexes = {@Index(name = "index_group_share_outs", columnList = "id, groupId, walletAccount", unique = true)})
public class GroupShareOuts extends Auditable {
    private Long groupId;
    private String name;
    private Double amount;
    private String walletAccount;
    private String coreAccount;
    private Date executed;
    private Integer groupSize;
    private Double expectedAmount;

    public GroupShareOuts  (long groupId, String groupName, String phoneNumber, String coreAccount, double totalMemberEarnings, int size,  String treasurerPhone, double groupContribution) {
        setGroupId(groupId);
        setName(groupName);
        setAmount(totalMemberEarnings);
        setExpectedAmount(groupContribution);
        setWalletAccount(phoneNumber);
        setCoreAccount(coreAccount);
        setExecuted(new Date());
        setCreatedBy(treasurerPhone);
        setCreatedOn(new Date());
        setGroupSize(size);
    }
}
