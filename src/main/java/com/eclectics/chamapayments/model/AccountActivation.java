package com.eclectics.chamapayments.model;


import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.PrePersist;
import javax.persistence.Table;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tb_account_activation")
public class AccountActivation extends Auditable {
    private String type;
    private Long groupId;
    private String process;
    private String phoneNumber;
    private Integer accountAttempts;

    @PrePersist
    private void addCountData() {
        setAccountAttempts(0);
    }
}
