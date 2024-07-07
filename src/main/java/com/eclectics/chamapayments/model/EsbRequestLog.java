package com.eclectics.chamapayments.model;

import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import lombok.*;

import javax.persistence.Entity;
import javax.persistence.PrePersist;
import javax.persistence.Table;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "esb_request_logs")
public class EsbRequestLog extends Auditable {
    private boolean callbackReceived;
    private String field0;
    private String field2;
    private String field3;
    private String field4;
    private String field6;
    private String field24;
    private String field32;
    private String field37;
    private String field43;
    private String field46;
    private String field48;
    private String field49;
    private String field60;
    private String field65;
    private String field68;
    private String field100;
    private String field102;
    private String field103;
    private String field123;
    private String chargeAmount;
    private String channel;
    private String USSDMNO;
    private String accountType;
    private String scope;

    @PrePersist
    public void addData() {
        this.callbackReceived = false;
    }

}
