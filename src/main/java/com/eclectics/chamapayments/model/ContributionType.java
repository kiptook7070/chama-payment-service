package com.eclectics.chamapayments.model;

import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;

/**
 * @author Alex Maina
 * @created 06/12/2021
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@Table(name = "contribution_types_tbl",
        indexes = {@Index(name = "index_contribution_types_tbl", columnList = "id, name", unique = true)})
public class ContributionType extends Auditable {
    private String name;
}
