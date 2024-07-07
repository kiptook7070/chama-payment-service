package com.eclectics.chamapayments.util;

import com.eclectics.chamapayments.model.*;
import com.eclectics.chamapayments.wrappers.response.AccountsWrapper;
import com.eclectics.chamapayments.wrappers.response.ContributionsWrapper;
import com.eclectics.chamapayments.wrappers.response.GroupLoanProductWrapper;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import javax.persistence.Column;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.time.LocalDate;
import java.util.Date;
import java.util.function.Function;

@Component
@AllArgsConstructor
public class MapperFunction {
    public Function<Accounts, AccountsWrapper> mapAccountsToWrapper() {
        return accounts -> AccountsWrapper.builder()
                .id(accounts.getId())
                .groupId(accounts.getGroupId())
                .name(accounts.getName())
                .accountdetails(accounts.getAccountdetails())
                .accountbalance(accounts.getAccountbalance())
                .accountType(accounts.getAccountType().getId())
                .availableBal(accounts.getAvailableBal())
                .phoneNumber(accounts.getPhoneNumber())
                .active(accounts.getActive())
                .balanceRequestDate(accounts.getBalanceRequestDate())
                .build();
    }

    public Function<Contributions, ContributionsWrapper> mapGroupContributionsToWrapper() {
        return contrib -> ContributionsWrapper.builder()
                .id(contrib.getId())
                .groupId(contrib.getGroupId())
                .name(contrib.getName())
                .active(contrib.isActive())
                .amountTypeId(contrib.getAmountType().getId())
                .scheduleTypeId(contrib.getScheduleType().getId())
                .frequency(contrib.getFrequency())
                .autoShareOut(contrib.getAutoShareOut())
                .manualShareOut(contrib.getManualShareOut())
                .reminder(contrib.getReminder())
                .daysBeforeDue(contrib.getDaysBeforeDue())
                .paymentPeriod(contrib.getPaymentPeriod())
                .contributionTypeId(contrib.getContributionType().getId())
                .memberGroupId(contrib.getMemberGroupId())
                .startDate(contrib.getStartDate())
                .shareoutDate(contrib.getShareoutDate())
                .loanInterest(contrib.getLoanInterest())
                .welfareAmt(contrib.getWelfareAmt())
                .penalty(contrib.getPenalty())
                .contributionAmount(contrib.getContributionAmount())
                .contributiondetails(contrib.getContributiondetails())
                .build();
    }

    public Function<LoanProducts, GroupLoanProductWrapper> mapToGroupLoanProductWrapper() {
        return loanProducts -> GroupLoanProductWrapper.builder()
                .id(loanProducts.getId())
                .groupId(loanProducts.getGroupId())
                .debitAccountId(loanProducts.getDebitAccountId().getId())
                .productname(loanProducts.getProductname())
                .description(loanProducts.getDescription())
                .interesttype(loanProducts.getInteresttype())
                .isPercentagePercentage(loanProducts.getIsPercentagePercentage())
                .min_principal(loanProducts.getMin_principal())
                .max_principal(loanProducts.getMax_principal())
                .paymentperiodtype(loanProducts.getPaymentperiodtype())
                .build();
    }
}
