package com.eclectics.chamapayments.wrappers.response;

import lombok.*;

import java.util.Date;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class MemberFineResponse {
    private String paymentStatus;
    private Long fineId;
    private Double amount;
    private String description;
    private String fineName;
    private String groupName;
    private Date createdOn;
}
