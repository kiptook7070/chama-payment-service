package com.eclectics.chamapayments.wrappers.request;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;
import org.springframework.lang.NonNull;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TransactionData {

    private String refNo;
    private String tranType;
    private String tranDate;
    private String effectDate;
    private double debitAmount;
    private double creditAmount;
    private String branch;
    private double runningBalance;

    private JsonNode fullStatementData;

}
