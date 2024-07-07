package com.eclectics.chamapayments.model;

import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import lombok.*;

import javax.persistence.Entity;
import javax.persistence.PrePersist;
import javax.persistence.Table;

@Getter
@Setter
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "group_notifications_tbl")
public class Notifications extends Auditable {
    private String message;
    private Long groupId;
    private String groupName;
}
