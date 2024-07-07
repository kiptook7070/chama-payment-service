package com.eclectics.chamapayments.wrappers.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.util.Date;

/**
 * @author kiptoo joshua
 * @createdOn 15/05/2024
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OtherGroupTransactionReportWrapper {
    private long id;
    private String branch;
    private String channel;
    private String tranType;
    private String tranDate;
    private String groupName;
    private String cbsAccount;
    private String cbsRegNumber;
    private Double debitAmount;
    private Double creditAmount;
    private Double ledgerBalance;
    private Double actualBalance;
    private String transactionId;
    private Double runningBalance;
    private Boolean amountDepleted;
    private Boolean transactionActedOn;
    private String transactionDescription;
}
