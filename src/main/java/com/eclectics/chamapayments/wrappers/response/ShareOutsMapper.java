package com.eclectics.chamapayments.wrappers.response;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ShareOutsMapper {
    private String name;
    private String group;
    private String phone;
    private double interestEarn;
    private double totalEarnings;
    private double totalContribution;
}
