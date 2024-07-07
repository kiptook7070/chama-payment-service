package com.eclectics.chamapayments.model;

import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;
import java.util.Date;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tb_charges",
        indexes = {@Index(name = "index_charges", columnList = "id,chargeType", unique = true)})
public class Charges extends Auditable {
    private String chargeType;
    private Integer chargeAmount;
    private String chargeStatus;
    private String chargeResponse;
    private String chargeReference;
    private Date chargeRequestDate;

}
