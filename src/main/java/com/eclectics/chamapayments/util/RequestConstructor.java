package com.eclectics.chamapayments.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@UtilityClass
public class RequestConstructor {
    public static Map<String, String> constructBody(String cbsAccount,
                                                    String account,
                                                    String coreAccount, Integer amount,
                                                    String transactionId,
                                                    String scope,
                                                    String chargeAmount) {
        Map<String, String> request = new HashMap<>();
        request.put("field0", "0200");
        request.put("field4", String.valueOf(amount));
        request.put("field24", "BB");
        request.put("field32", "MCHAMA");
        request.put("field37", transactionId);
        request.put("field43", "Kenya Post Office Savings Bank");
        request.put("field46", "Mchama Joined Account Withdrawal");
        request.put("field49", "KSH");
        request.put("field60", "KE");
        request.put("channel", "MCHAMA");
        request.put("accountType", "27");
        request.put("chargeAmount", chargeAmount);

        switch (scope) {

            case "MC": // Member Contribution
                request.put("field2", account);
                request.put("field3", "146000");
                request.put("field6", "C2B_DEPOSIT");
                request.put("field65", account);
                request.put("field68", "M-PESA DEPOSIT FROM " + account + " to " + cbsAccount);
                request.put("field100", "MCHAMA_DEPOSIT");
                request.put("field102", cbsAccount);
                request.put("field103", cbsAccount);
                request.put("USSDMNO", "SAFARICOM");
                break;

            case "MCC": // Member Contribution using Core Account
                request.put("field2", account);
                request.put("field3", "400000");
                request.put("field6", "IFT_FUND_TRANSFER");
                request.put("field65", coreAccount);
                request.put("field68", "PBK MCHAMA DEPOSIT FROM " + coreAccount + " to " + cbsAccount);
                request.put("field100", "IFT_FUND_TRANSFER");
                request.put("field102", coreAccount);
                request.put("field103", cbsAccount);

                break;

            case "MW": // Member Withdrawal to phone number
                request.put("field2", account);
                request.put("field3", "430000");
                request.put("field65", account);
                request.put("field68", "M-PESA WITHDRAWAL FROM " + cbsAccount + " to " + account);
                request.put("field100", "MCHAMA_WITHDRAWAL");
                request.put("field102", cbsAccount);
                request.put("field123", "MCHAMA");
                request.put("USSDMNO", "SAFARICOM");
                break;

            case "MWC": // Member Withdrawal to Core Account
                request.put("field2", account);
                request.put("field3", "400000");
                request.put("field65", coreAccount);
                request.put("field68", "PBK MCHAMA WITHDRAWAL FROM " + cbsAccount + " to " + coreAccount);
                request.put("field100", "IFT_FUND_TRANSFER");
                request.put("field102", cbsAccount);
                request.put("field103", coreAccount);
                request.put("field123", "MCHAMA");
                break;

            case "LD": // Loan Disbursement to WA
                request.put("field2", account);
                request.put("field3", "430000");
                request.put("field65", account);
                request.put("field68", "M-PESA LOAN DISBURSEMENT FROM " + cbsAccount + " to " + account);
                request.put("field100", "MCHAMA_WITHDRAWAL");
                request.put("field102", cbsAccount);
                request.put("field123", "MCHAMA");
                request.put("USSDMNO", "SAFARICOM");

                break;

            case "LDC": // Loan Disbursement to CA
                request.put("field2", account);
                request.put("field3", "400000");
                request.put("field65", coreAccount);
                request.put("field68", "PBK LOAN DISBURSEMENT FROM " + cbsAccount + " to " + coreAccount);
                request.put("field100", "IFT_FUND_TRANSFER");
                request.put("field102", cbsAccount);
                request.put("field103", coreAccount);
                request.put("field123", "MCHAMA");

                break;

            case "SMW": // Share Out To Wallet Account
                request.put("field2", account);
                request.put("field3", "430000");
                request.put("field65", account);
                request.put("field68", "SHARE OUT B2C M-PESA PAYMENT FROM " + cbsAccount + " to " + account);
                request.put("field100", "MCHAMA_WITHDRAWAL");
                request.put("field102", cbsAccount);
                request.put("field123", "MCHAMA");
                request.put("USSDMNO", "SAFARICOM");
                break;

            case "SCW": // Share Out To Core Account
                request.put("field2", account);
                request.put("field3", "400000");
                request.put("field65", coreAccount);
                request.put("field68", "SHARE OUT FUND TRANSFER FROM " + cbsAccount + " to " + coreAccount);
                request.put("field100", "IFT_FUND_TRANSFER");
                request.put("field102", cbsAccount);
                request.put("field103", coreAccount);
                request.put("field123", "MCHAMA");
                break;

            default:
                break;
        }
        return request;
    }

    public static Map<String, String> getBalanceInquiryReq(String account, String charges, String wallet, String transactionId) {
        Map<String, String> balanceInquiryReq = new HashMap<>();
        balanceInquiryReq.put("field0", "0100");
        balanceInquiryReq.put("field2", wallet);
        balanceInquiryReq.put("field3", "310000");
        balanceInquiryReq.put("field24", "BB");
        balanceInquiryReq.put("field32", "APP");
        balanceInquiryReq.put("field37", transactionId);
        balanceInquiryReq.put("field39", "00");
        balanceInquiryReq.put("field68", "Balance inquiry for account " + account);
        balanceInquiryReq.put("field100", "BALANCE");
        balanceInquiryReq.put("field102", account);
        balanceInquiryReq.put("chargeAmount", charges);
        balanceInquiryReq.put("accountType", "09");
        balanceInquiryReq.put("isMchama", "1");
        balanceInquiryReq.put("field48", "success");
        return balanceInquiryReq;
    }

    public static Map<String, String> constructChargesBody(String account, Integer amount, String ref, String chargeType) {
        Map<String, String> chargeRequest = new HashMap<>();
        chargeRequest.put("field0", "0100");
        chargeRequest.put("field2", account);
        chargeRequest.put("field3", "150000");
        chargeRequest.put("field4", String.valueOf(amount));
        chargeRequest.put("field24", "BB");
        chargeRequest.put("field32", "APP");
        chargeRequest.put("field37", ref);
        chargeRequest.put("field100", chargeType);
        chargeRequest.put("field126", "430000");
        return chargeRequest;
    }

    public static Map<String, String> constructStatementBody(String account, String startDateFormat, String endDateFormat) {
        Map<String, String> statementRequest = new HashMap<>();
        statementRequest.put("accountNo", account);
        statementRequest.put("startDate", startDateFormat);
        statementRequest.put("endDate", endDateFormat);
        return statementRequest;
    }


    public static Map<String, String> constructCustomerByAccountBody(String account) {
        Map<String, String> accountRequest = new HashMap<>();
        accountRequest.put("account", account);
        return accountRequest;
    }

    public static Map<String, String> constructUserAccountsBody(String username) {
        Map<String, String> accountRequest = new HashMap<>();
        accountRequest.put("phoneNumber", username);
        return accountRequest;
    }
}
