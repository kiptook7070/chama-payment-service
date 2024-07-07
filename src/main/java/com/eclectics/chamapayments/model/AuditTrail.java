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
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "audit_trails_logs_tb")
public class AuditTrail extends Auditable {
    private String action;
    private String description;
    private String capturedBy;
}
