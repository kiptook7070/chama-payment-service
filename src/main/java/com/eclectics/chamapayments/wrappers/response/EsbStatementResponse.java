package com.eclectics.chamapayments.wrappers.response;

import com.eclectics.chamapayments.wrappers.request.TransactionData;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EsbStatementResponse {
    private String accountNo;
    private String acctName;
    private String address;
    private String email1;
    private String email2;
    private String ledgerBalance;
    private String actualBalance;
    private String message;
    private String status;
    private String idNumber;
    private List<TransactionData> transactionData;
}
