package com.eclectics.chamapayments.model;


import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import com.fasterxml.jackson.annotation.JsonMerge;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import javax.persistence.*;
import java.util.Date;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "accounts")
public class Accounts extends Auditable {
    private String name;
    private String phoneNumber;
    private String accountdetails;
    private Boolean isCore;
    @ManyToOne()
    @JoinColumn(name = "accountType", nullable = false)
    @JsonMerge
    @JsonProperty("accountType")
    private AccountType accountType;
    private Double accountbalance;
    private Double availableBal;
    private Long groupId;
    private Boolean hasAccount;
    @Column(name = "is_active")
    private Boolean active;
    private Date balanceRequestDate;
    private Date statementRequestDate;

    public Accounts(long groupId, String accountName, double actualBalance, double availableBalance, String phoneNumber, String accountNumber, AccountType accountType) {
        setGroupId(groupId);
        setName(accountName);
        setAccountType(accountType);
        setAccountbalance(actualBalance);
        setAvailableBal(availableBalance);
        setPhoneNumber(phoneNumber);
        setAccountdetails(accountNumber);
        setBalanceRequestDate(new Date());
        setLastModifiedBy(phoneNumber);
        setLastModifiedDate(new Date());
    }


    @PrePersist
    private void addAccount() {
        setHasAccount(true);
        setActive(true);
        setIsCore(true);
        setLastModifiedBy("SYSTEM");
    }

}
