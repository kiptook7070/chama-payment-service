package com.eclectics.chamapayments.wrappers.response;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class FineResponse {
    private String paymentStatus;
    private Double amount;
    private String description;
    private String fineName;
    private String groupName;
}
