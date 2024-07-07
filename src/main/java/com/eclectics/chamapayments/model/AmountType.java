package com.eclectics.chamapayments.model;

import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;

/**
 * @author Alex Maina
 * @createdOn  06/12/2021
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "amount_types",
        indexes = {@Index(name = "index_amount_types_tbl", columnList = "id, name", unique = true)})
public class AmountType  extends Auditable {
    private String name;
}
