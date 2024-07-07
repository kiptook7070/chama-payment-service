package com.eclectics.chamapayments.wrappers.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContributionAnalyticsWrapper {
    private String name;
    private Integer amount;
}
