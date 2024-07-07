package com.eclectics.chamapayments.model;

import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import lombok.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Builder
@Entity
@Table(name = "group_membership_tbl")
public class GroupMembership extends Auditable {
    private boolean activemembership;
    private boolean isrequesttoleaveactedon;
    private boolean requesttoleavegroup;
    private String deactivationreason;
    private String title;
    @Column(columnDefinition = "text")
    private String permissions;
    private Long groupId;
}
