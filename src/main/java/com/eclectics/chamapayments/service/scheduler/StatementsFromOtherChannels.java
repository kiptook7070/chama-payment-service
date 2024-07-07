package com.eclectics.chamapayments.service.scheduler;

import com.eclectics.chamapayments.model.Accounts;
import com.eclectics.chamapayments.model.Group;
import com.eclectics.chamapayments.model.OtherChannelsBalances;
import com.eclectics.chamapayments.repository.AccountsRepository;
import com.eclectics.chamapayments.repository.GroupRepository;
import com.eclectics.chamapayments.repository.OtherChannelsBalancesRepository;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.scheduler.Schedulers;

import javax.annotation.PostConstruct;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.eclectics.chamapayments.util.RequestConstructor.constructStatementBody;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatementsFromOtherChannels {
    private final GroupRepository groupRepository;
    private final AccountsRepository accountsRepository;
    private final OtherChannelsBalancesRepository otherChannelsBalancesRepository;
    Gson gson = new Gson();
    private WebClient postBankWebClient;
    @Value("${statement.url}")
    private String statementUrl;
    int numThreads = Runtime.getRuntime().availableProcessors();
    ExecutorService executorService = Executors.newFixedThreadPool(numThreads);


    @PostConstruct
    private void init() {
        postBankWebClient = WebClient.builder().baseUrl(statementUrl).build();
    }

    @Scheduled(fixedDelay = 500000)
    void executeReminders() {
        executorService.execute(this::otherChannelDeposits);
    }

    @Transactional
    public void otherChannelDeposits() {
        List<Group> groupList = groupRepository.findAllCBSSatisfied();
        if (!groupList.isEmpty()) {
            groupList.parallelStream().forEach(group -> {
                long currentDays = 2;
                Date today = new Date();
                Date dateBefore = new Date(today.getTime() - currentDays * 24 * 3600 * 1000); //Subtract n days
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
                String startDateFormat = simpleDateFormat.format(dateBefore);
                String endDateFormat = simpleDateFormat.format(today);
                Map<String, String> esbStatementRequest = constructStatementBody(group.getCbsAccount(), startDateFormat, endDateFormat);
                String esbStatementBody = gson.toJson(esbStatementRequest);
                String statementResponse = postBankWebClient.post()
                        .uri(statementUrl)
                        .headers(httpHeaders -> httpHeaders.setBasicAuth("eclectics", "eclectics123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(esbStatementBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .subscribeOn(Schedulers.boundedElastic())
                        .block();

                JsonObject otherChannelJsonObject = gson.fromJson(statementResponse, JsonObject.class);
                String apiMessage = otherChannelJsonObject.get("message").getAsString();
                String apiStatus = otherChannelJsonObject.get("status").getAsString();

                if (apiMessage.equals("Success") && apiStatus.equals("000")) {
                    Group cbsGroup = groupRepository.findFirstByCbsAccountAndSoftDeleteFalse(group.getCbsAccount());
                    if (!(cbsGroup == null)) {
                        long groupId = cbsGroup.getId();
                        String regNumber = otherChannelJsonObject.get("idNumber").getAsString();
                        String acctName = otherChannelJsonObject.get("acctName").getAsString();
                        String accountNo = otherChannelJsonObject.get("accountNo").getAsString();
                        double ledgerBalance = otherChannelJsonObject.get("ledgerBalance").getAsDouble();
                        double actualBalance = otherChannelJsonObject.get("actualBalance").getAsDouble();

                        JsonArray tranData = otherChannelJsonObject.get("tranData").getAsJsonArray().isJsonNull() ? null : otherChannelJsonObject.get("tranData").getAsJsonArray();

                        assert tranData != null;
                        Objects.requireNonNull(tranData).forEach(transaction -> {
                            String tranType = transaction.getAsJsonObject().get("tranType").isJsonNull() ? null : transaction.getAsJsonObject().get("tranType").getAsString();
                            String crDRMAINTIND = (transaction.getAsJsonObject().get("cr_DR_MAINT_IND").isJsonNull() ? null : transaction.getAsJsonObject().get("cr_DR_MAINT_IND").getAsString());
                            String tranDESC = (transaction.getAsJsonObject().get("tran_DESC").isJsonNull() ? null : transaction.getAsJsonObject().get("tran_DESC").getAsString());
                            String referenceNumber = (transaction.getAsJsonObject().get("refNo").isJsonNull() ? null : transaction.getAsJsonObject().get("refNo").getAsString());
                            double creditAmount = transaction.getAsJsonObject().get("creditAmount").getAsDouble();
                            double debitAmount = transaction.getAsJsonObject().get("debitAmount").getAsDouble();
                            String tranDate = transaction.getAsJsonObject().get("tranDate").isJsonNull() ? null : transaction.getAsJsonObject().get("tranDate").getAsString();
                            String branch = transaction.getAsJsonObject().get("branch").isJsonNull() ? null : transaction.getAsJsonObject().get("branch").getAsString();
                            double runningBalance = transaction.getAsJsonObject().get("runningBalance").getAsDouble();
                            if (!(tranDESC == null && !(tranType == null))) {
                                //TODO:: CHECK IF ITS CONTAINING CASH DEPOSIT
                                assert tranType != null;
                                if (Objects.requireNonNull(tranType).contains("CASH DEPOSIT")) {
                                    //TODO:: CHECK IF ITS FROM POS
                                    if (Objects.equals(tranType, "CASH DEPOSIT") && Objects.equals(crDRMAINTIND, "C")) {
                                        String posChannel = "POS";
                                        //TODO:  check the transaction for duplicate records
                                        channelDeposits(group, groupId, regNumber, acctName, ledgerBalance, actualBalance, tranType, tranDESC, referenceNumber, creditAmount, debitAmount, tranDate, branch, runningBalance, posChannel);
                                    }
                                }

                                //TODO:: CHECK IF ITS CONTAINING LOCAL CHEQUE DEPOSIT
                                if (Objects.requireNonNull(tranType).contains("LOCAL CHEQUE DEPOSIT")) {
                                    //TODO:: CHECK IF ITS FROM CHEQUE
                                    if (Objects.equals(tranType, "LOCAL CHEQUE DEPOSIT") && Objects.equals(crDRMAINTIND, "C")) {
                                        String chequeChannel = "CHEQUE";
                                        //TODO:  check the transaction for duplicate records
                                        channelDeposits(group, groupId, regNumber, acctName, ledgerBalance, actualBalance, tranType, tranDESC, referenceNumber, creditAmount, debitAmount, tranDate, branch, runningBalance, chequeChannel);
                                    }
                                }


                                if (Objects.equals(tranType, "TRANSFER DEPOSIT") && Objects.equals(crDRMAINTIND, "C") && Objects.requireNonNull(tranDESC).contains("FUND TRANSFER")) {
                                    if (tranDESC.length() == 28) {
                                        //TODO:: check if it has phone numbers && its a fund transfer from USSD
                                        String customer = tranDESC.substring(15, 27);
                                        if (customer.startsWith("254")) {
                                            String ussdChannel = "USSD";
                                            //TODO:: check the transaction for duplicate records
                                            channelDeposits(group, groupId, regNumber, acctName, ledgerBalance, actualBalance, tranType, tranDESC, referenceNumber, creditAmount, debitAmount, tranDate, branch, runningBalance, ussdChannel);
                                        }
                                    }
                                }

                                if (Objects.equals(tranType, "TRANSFER DEPOSIT") && Objects.equals(crDRMAINTIND, "C") && Objects.requireNonNull(tranDESC).contains("FUNDS TRANSFER FROM")) {
                                    if (tranDESC.length() == 65) {
                                        String MIBCustomer = tranDESC.substring(52, 64);
                                        if (MIBCustomer.startsWith("254")) {
                                            String mibChannel = "MIB";
                                            //TODO:: check the transaction for duplicate records
                                            channelDeposits(group, groupId, regNumber, acctName, ledgerBalance, actualBalance, tranType, tranDESC, referenceNumber, creditAmount, debitAmount, tranDate, branch, runningBalance, mibChannel);
                                        }
                                    }
                                }

                                //TODO:: CHECK MIB APP CHECKOUT
                                if (Objects.equals(tranType, "MOBILE DEPOSIT ") && Objects.equals(crDRMAINTIND, "C") && Objects.requireNonNull(tranDESC).contains("APP - MPESA CR FROM : ")) {
                                    String mibChannel = "APP";
                                    //TODO:: check the transaction for duplicate records
                                    channelDeposits(group, groupId, regNumber, acctName, ledgerBalance, actualBalance, tranType.trim(), tranDESC, referenceNumber, creditAmount, debitAmount, tranDate, branch, runningBalance, mibChannel);
                                }

                                //TODO:: CHECK USSD CHECKOUT
                                if (Objects.equals(tranType, "MOBILE DEPOSIT ") && Objects.equals(crDRMAINTIND, "C") && Objects.requireNonNull(tranDESC).contains("USSD - MPESA CR FROM : ")) {
                                    String channel = "USSD";
                                    //TODO:: check the transaction for duplicate records
                                    channelDeposits(group, groupId, regNumber, acctName, ledgerBalance, actualBalance, tranType.trim(), tranDESC, referenceNumber, creditAmount, debitAmount, tranDate, branch, runningBalance, channel);
                                }
                                //TODO:: CHECK POS CHECKOUT
                                if (Objects.equals(tranType, "CASH DEPOSIT") && Objects.equals(crDRMAINTIND, "C") && Objects.requireNonNull(tranDESC).contains("POS - MPESA CR FROM : ")) {
                                    String channel = "POS";
                                    //TODO:: check the transaction for duplicate records
                                    channelDeposits(group, groupId, regNumber, acctName, ledgerBalance, actualBalance, tranType.trim(), tranDESC, referenceNumber, creditAmount, debitAmount, tranDate, branch, runningBalance, channel);
                                }

                                //TODO:: CHECK POST CARD TRANSFER
                                if (Objects.equals(tranType, "TRANSFER DEPOSIT") && Objects.equals(crDRMAINTIND, "C") && Objects.requireNonNull(tranDESC).contains("FT BY " + acctName)) {
                                    //TODO:: CHECK LAST FOUR CHARACTER GROUP ACCOUNT
                                    String accountFound = accountNo.substring(accountNo.length() - 4);
                                    String tranDESCFound = tranDESC.substring(tranDESC.length() - 4);
                                    if (accountFound.equals(tranDESCFound)) {
                                        //TODO:: SAVE THE TRANSACTION
                                        String posChannel = "POS";
                                        channelDeposits(group, groupId, regNumber, acctName, ledgerBalance, actualBalance, tranType, tranDESC, referenceNumber, creditAmount, debitAmount, tranDate, branch, runningBalance, posChannel);
                                    }
                                }
                            }

                            //TODO:: UPDATE GROUP BALANCES
                            Accounts accounts = accountsRepository.findByGroupId(groupId);
                            if (!(accounts == null)) {
                                //TOD:: CHECK BALANCES
                                if (actualBalance > accounts.getAccountbalance()) {
                                    //TOD:: UPDATE BALANCES
                                    accounts.setAccountbalance(actualBalance);
                                    accounts.setAvailableBal(ledgerBalance);
                                    accountsRepository.save(accounts);
                                }
                            }
                        });
                    }
                }
            });
        }
    }

    private void channelDeposits(Group groupAccount, long groupId, String regNumber, String acctName, double ledgerBalance, double actualBalance, String tranType, String tranDESC, String referenceNumber, double creditAmount, double debitAmount, String tranDate, String branch, double runningBalance, String ussdChannel) {
        OtherChannelsBalances otherTransaction = otherChannelsBalancesRepository.findFirstByGroupIdAndTransactionIdAndSoftDeleteIsFalseOrderByIdAsc(groupId, referenceNumber);
        if (otherTransaction == null) {
            //TODO:: save transaction
            log.info("new:::: ref no {} group id {}", referenceNumber, groupId);
            OtherChannelsBalances otherChannelsBalance = new OtherChannelsBalances(groupId, groupAccount.getCbsAccount(), branch, regNumber, acctName, ledgerBalance, actualBalance, referenceNumber, creditAmount, debitAmount, runningBalance, tranDate, tranType, ussdChannel, tranDESC);
            otherChannelsBalancesRepository.save(otherChannelsBalance);
        } else {
            log.info("already staged ref no {} group id {}", referenceNumber, groupId);
        }
    }
}
