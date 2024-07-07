package com.eclectics.chamapayments.model;

import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Table;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "groups_tbl")
public class Group extends Auditable {
    private String name;
    private boolean active;
    private Boolean canWithdraw;
    private String email;
    private String cbsAccount;
    private String cbsAccountName;
    private String accType;
    private String groupType;
    private Double availableBalance;
    private Double actualBalance;
}
