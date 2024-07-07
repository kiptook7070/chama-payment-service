package com.eclectics.chamapayments.wrappers.request;

import lombok.Getter;
import lombok.Setter;

import java.util.Optional;

@Getter
@Setter
public class LoanDisbursedRequest {
   private int page;
    private int size;
    private String filter;
    private long filterId;
}
