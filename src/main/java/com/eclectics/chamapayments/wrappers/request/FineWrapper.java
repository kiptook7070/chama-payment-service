package com.eclectics.chamapayments.wrappers.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FineWrapper {
    private String phoneNumber;
    private Long groupId;
    private Double amount;
    private String description;
    private String fineName;
}
