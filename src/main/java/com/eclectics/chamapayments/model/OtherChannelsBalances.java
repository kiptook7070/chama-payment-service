package com.eclectics.chamapayments.model;

import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.PrePersist;
import javax.persistence.Table;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder

@Table(name = "other_channels_balances")
public class OtherChannelsBalances extends Auditable {
    private Long groupId;
    private String branch;
    private String groupName;
    private String cbsAccount;
    private String cbsRegNumber;
    private String tranType;
    private String channel;
    private String tranDate;
    private Double debitAmount;
    private Double creditAmount;
    private Double ledgerBalance;
    private Double actualBalance;
    private String transactionId;
    private Double runningBalance;
    private Boolean amountDepleted;
    private Boolean transactionActedOn;
    private String transactionDescription;

    public OtherChannelsBalances(long groupId, String groupAccount, String branch, String regNumber, String acctName, double ledgerBalance, double actualBalance, String referenceNumber, double creditAmount, double debitAmount, double runningBalance, String tranDate, String tranType, String channel, String transDesc) {
        setGroupId(groupId);
        setBranch(branch);
        setGroupName(acctName);
        setCbsAccount(groupAccount);
        setCbsRegNumber(regNumber);
        setTranType(tranType);
        setChannel(channel);
        setTranDate(tranDate);
        setCreditAmount(creditAmount);
        setDebitAmount(debitAmount);
        setLedgerBalance(ledgerBalance);
        setActualBalance(actualBalance);
        setRunningBalance(runningBalance);
        setTransactionId(referenceNumber);
        setTransactionDescription(transDesc);
    }

    @PrePersist
    private void addTransaction() {
        setAmountDepleted(false);
        setTransactionActedOn(false);
    }
}
