package com.eclectics.chamapayments.wrappers.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Optional;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoanPenaltiesRequest {
    private String filter;
    private Optional<Long> filterId;
}
