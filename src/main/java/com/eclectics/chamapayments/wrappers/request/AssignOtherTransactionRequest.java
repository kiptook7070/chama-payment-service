package com.eclectics.chamapayments.wrappers.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssignOtherTransactionRequest {
    private String action;
    private long groupId;
    private List<MemberTransactionsWrapper> memberTransactions;
}
