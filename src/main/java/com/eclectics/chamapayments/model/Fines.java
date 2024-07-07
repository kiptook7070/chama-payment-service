package com.eclectics.chamapayments.model;
import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@Table(name = "fines_tbl")
public class Fines extends Auditable {
    private Long groupId;
    private String fineName;
    private String fineDescription;
    private Double fineAmount;
    private String paymentStatus;
    private Double fineBalance;
    private Long memberId;
    private Date transactionDate;
}
