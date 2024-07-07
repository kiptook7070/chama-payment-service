package com.eclectics.chamapayments.model;

import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "account_types",
        indexes = {@Index(name = "index_account_types_tbl", columnList = "id, accountName, accountPrefix, accountFields", unique = true)})
public class AccountType extends Auditable {
    private String accountName;
    private String accountPrefix;
    private String accountFields;
}
