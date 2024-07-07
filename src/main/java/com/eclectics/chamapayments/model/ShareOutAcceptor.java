package com.eclectics.chamapayments.model;

import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Table;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tb_share_out_acceptor")
public class ShareOutAcceptor extends Auditable {
    private Long groupId;
    private String Status;
    private Boolean enabled;
    private String groupName;
}
