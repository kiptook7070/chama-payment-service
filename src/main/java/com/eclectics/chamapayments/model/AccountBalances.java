package com.eclectics.chamapayments.model;

import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import com.fasterxml.jackson.annotation.JsonMerge;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import javax.persistence.*;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@Table(name = "account_balances_tb",
        indexes = {@Index(name = "index_account_balances_tb", columnList = "id, groupId, phoneNumber, accountNumber", unique = true)})
public class AccountBalances extends Auditable {
    private Long groupId;
    private String name;
    private String phoneNumber;
    private String accountNumber;
    @ManyToOne()
    @JoinColumn(name = "accountType", nullable = false)
    @JsonMerge
    @JsonProperty("accountType")
    private AccountType accountType;

    @ManyToOne()
    @JoinColumn(name = "accounts", nullable = false)
    @JsonMerge
    @JsonProperty("accounts")
    private Accounts accounts;
    private Double actualBalance;
    private Double newActualBalance;
    private Double actualAmount;
    private Double availableBalance;
    private Double availableAmount;
    private Double newAvailableBalance;
    private Boolean transactionActedOn;
    private Boolean amountDepleted;
    private String transactionId;
}
