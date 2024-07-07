package com.eclectics.chamapayments.service.scheduler;

import com.eclectics.chamapayments.model.*;
import com.eclectics.chamapayments.repository.*;
import com.eclectics.chamapayments.service.ChamaKycService;
import com.eclectics.chamapayments.service.NotificationService;
import com.eclectics.chamapayments.service.enums.PaymentEnum;
import com.eclectics.chamapayments.service.enums.TypeOfContribution;
import com.eclectics.chamapayments.util.TransactionIdGenerator;
import com.eclectics.chamapayments.wrappers.response.GroupWrapper;
import com.eclectics.chamapayments.wrappers.response.MemberWrapper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

import javax.annotation.PostConstruct;
import javax.net.ssl.SSLException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.eclectics.chamapayments.util.RequestConstructor.constructBody;
import static com.eclectics.chamapayments.util.RequestConstructor.constructUserAccountsBody;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareOutProcessingExecutor {
    @Value("${esb.channel.uri}")
    private String postBankUrl;
    @Value("${esb.user.accounts}")
    private String userAccounts;
    private WebClient webClient;
    private WebClient userAccountWebClient;
    private WebClient postBankWebClient;
    Gson gson = new Gson();
    private final ChamaKycService chamaKycService;
    private final FinesRepository finesRepository;
    private final LoansdisbursedRepo loansdisbursedRepo;
    private final NotificationService notificationService;
    private final ShareOutsRepository shareOutsRepository;
    private final ParametersRepository parametersRepository;
    private final ShareOutsDisbursedRepo shareOutsDisbursedRepo;
    private final ShareOutsPaymentRepository shareOutsPaymentRepository;
    private final AccountActivationRepository accountActivationRepository;
    private final ContributionsPaymentRepository contributionsPaymentRepository;
    private final ShareOutsPendingDisbursementRepo shareOutsPendingDisbursementRepo;
    int numThreads = Runtime.getRuntime().availableProcessors();
    ExecutorService executorService = Executors.newFixedThreadPool(numThreads);

    @PostConstruct
    private void init() {
        postBankWebClient = WebClient.builder().baseUrl(postBankUrl).build();
        userAccountWebClient = WebClient.builder().baseUrl(userAccounts).build();
    }

    @PostConstruct
    public void initUserAccounts() throws SSLException {
        SslContext sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        HttpClient httpClient = HttpClient.create().secure(sslContextSpec -> sslContextSpec.sslContext(sslContext));
        userAccountWebClient = WebClient.builder().baseUrl(userAccounts).build();
        webClient = WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).baseUrl(userAccounts).build();

    }


    @Scheduled(fixedDelay = 120000)
    void executeReminders() {
        executorService.execute(this::processTransaction);
    }

    @Transactional
    public void processTransaction() {
        List<ShareOutsPendingDisbursement> shareOutsPendingDisbursementList = shareOutsPendingDisbursementRepo.findAllByStatusAndPendingTrueAndSoftDeleteFalseOrderByCreatedOnAsc(PaymentEnum.SHARE_OUT_PENDING_DISBURSEMENT.name());
        if (!shareOutsPendingDisbursementList.isEmpty()) {
            log.info("SHARES FOUND LIST {}", shareOutsPendingDisbursementList.size());

            shareOutsPendingDisbursementList.parallelStream().forEach(disbursement -> {

                log.info("GROUP NAME {}, MEMBER {}, AMOUNT {}", disbursement.getGroupName(), disbursement.getPhoneNumber(), disbursement.getAmount());

                long groupId = disbursement.getGroupId();
                GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(groupId);

                if (groupWrapper == null) {
                    log.info("GROUP {} NOT FOUND", disbursement.getGroupName());
                    return;
                }

                String groupName = disbursement.getGroupName();
                if (!groupWrapper.isActive()) {
                    log.info("GROUP {} WITHDRAWAL NOT ACTIVE", groupName);
                    return;
                }

                if (!groupWrapper.isCanWithdraw()) {
                    log.info("GROUP {} WITHDRAWAL NOT ALLOWED", groupName);
                    return;
                }

                String phoneNumber = disbursement.getPhoneNumber();
                String coreAccount = disbursement.getCoreAccount();

                MemberWrapper mchamaMember = chamaKycService.searchMonoMemberByPhoneNumber(phoneNumber);
                if (mchamaMember == null) {
                    log.info("MEMBER WITH PHONE NUMBER {}, IN GROUP {} NOT FOUND", phoneNumber, disbursement.getGroupName());
                    sendAnonymousShareOuts(groupId, phoneNumber);
                    return;
                }

                String memberName = mchamaMember.getFirstname().concat(" " + mchamaMember.getLastname());
                String language = mchamaMember.getLanguage();
                double totalMemberEarnings = disbursement.getAmount();

                Parameters parameters = parametersRepository.findTopByNameIgnoreCaseAndSoftDeleteIsFalse("LIMITS");

                if (parameters == null) {
                    log.info("SHARE OUTS LIMITS PARAMETERS NOT SET");
                    return;
                }

                JsonObject jsonObject = gson.fromJson(parameters.getValue(), JsonObject.class);

                double limitAmount = jsonObject.get("withdrawal").getAsDouble();


                Integer chargeAmount = 0;
                String transactionId = TransactionIdGenerator.generateTransactionId("SHO");

                ShareOutsPendingDisbursement shareOutsPendingDisbursement = shareOutsPendingDisbursementRepo.findFirstByGroupIdAndPhoneNumberAndPendingTrueAndStatusAndSoftDeleteFalseOrderByIdDesc(groupId, phoneNumber, PaymentEnum.SHARE_OUT_PENDING_DISBURSEMENT.name());
                if (!(shareOutsPendingDisbursement == null)) {
                    log.info("STARTING DISBURSAL OF KSHS. {}, FOR  MEMBER {}, IN GROUP {} +++++++++++++++++++++++!!", totalMemberEarnings, phoneNumber, groupName);

                    List<LoansDisbursed> memberLoansList = loansdisbursedRepo.findAllByGroupIdAndMemberIdAndSoftDeleteFalseOrderByIdAsc(shareOutsPendingDisbursement.getGroupId(), mchamaMember.getId());

                    List<Fines> memberFinesList = finesRepository.findAllByGroupIdAndMemberIdAndSoftDeleteFalseOrderByIdAsc(shareOutsPendingDisbursement.getGroupId(), mchamaMember.getId());
                    double memberLoans = 0;
                    double memberFines = 0;

                    if (!memberLoansList.isEmpty()) {
                        memberLoans = memberLoansList.stream().mapToDouble(LoansDisbursed::getDueamount).sum();
                    }

                    if (!memberFinesList.isEmpty()) {
                        memberFines = memberFinesList.stream().mapToDouble(Fines::getFineBalance).sum();
                    }

                    log.info("MEMBER LOANS {}, FINES {}, TOTALS(FINE&LOANS) {}", memberLoans, memberFines, memberFines + memberFines);

                    log.info("GROUP {}, MEMBER {}, MEMBER LOANS {} ", groupName, mchamaMember.getFirstname(), memberLoans);

                    Map<String, String> userAccountRequest = constructUserAccountsBody(phoneNumber);
                    String userAccountBody = gson.toJson(userAccountRequest);
                    log.info("USER ACCOUNT REQUEST {}", userAccountBody);

                    String response = webClient.post()
                            .uri(userAccounts)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(userAccountBody)
                            .retrieve()
                            .bodyToMono(String.class)
                            .subscribeOn(Schedulers.boundedElastic())
                            .block();
                    JsonObject userAccountsjsonObject = gson.fromJson(response, JsonObject.class);
                    log.info("USER ACCOUNTS RESPONSE {}", response);


                    JsonArray accountsData = userAccountsjsonObject.get("accounts").getAsJsonArray().isJsonNull() ? null : userAccountsjsonObject.get("accounts").getAsJsonArray();
                    log.info("ACCOUNTS DATA {}", accountsData);


                    if (totalMemberEarnings < limitAmount && accountsData.isEmpty()) {
                        log.info("---------------CHECKOUT  CONDITION----------------");
                        //TODO: CHECK CUSTOMER ACCOUNT) {
                        log.info("WITHDRAW TO MPESA --- SAFARICOM............");
                        String scope = "SMW";
                        Map<String, String> esbRequest = constructBody(
                                groupWrapper.getCsbAccount(), phoneNumber,
                                phoneNumber, (int) totalMemberEarnings, transactionId,
                                scope, String.valueOf(chargeAmount));
                        String esbRequestBody = gson.toJson(esbRequest);

                        log.info("SHARE OUT DISBURSAL REQUEST TO MPESA {}", esbRequestBody);
                        String shareOutResponse = postBankWebClient.post()
                                .uri(postBankUrl)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(esbRequestBody)
                                .retrieve()
                                .bodyToMono(String.class)
                                .subscribeOn(Schedulers.boundedElastic())
                                .block();

                        JsonObject shareOutJsonObject = gson.fromJson(shareOutResponse, JsonObject.class);
                        log.info("SHARE OUT RESPONSE TO MPESA  {}", shareOutJsonObject);

                        if (shareOutJsonObject.get("field39").getAsString().equals("00")) {
                            log.info("SHARE OUT RESPONSE TO MPESA ===== ON SUCCESS {}", shareOutJsonObject);
                            //sendNotification
                            sendNotification(groupId, groupName, phoneNumber, totalMemberEarnings, memberName, language);
                            disableMemberPendingShareOuts(groupId, phoneNumber);
                            disablePendingDisbursement(groupId, phoneNumber);
                            saveDisbursedShareOut(coreAccount, phoneNumber, groupId, groupName, shareOutJsonObject.toString(), totalMemberEarnings, memberName, language);
                            //write off member loans
                            writeOffMemberLoans(memberLoansList, memberFinesList);
                            disableShareOutsDisbursed(groupId, phoneNumber);
                            //disable all payments on completion
                            disableShareOuts(groupId, phoneNumber);
                            disableShareOutsPayments(groupId, phoneNumber);
                            disableOtherGroupPayments(groupId, phoneNumber);
                            disableGroupFines(groupId, phoneNumber);
                            disableGroupLoanInterest(groupId, mchamaMember.getId());
                        }


                    } else if (!accountsData.isEmpty()) {
                        log.info("---------------ACCOUNT  CONDITION--------------------");
                        JsonObject accountsDataJsonObject = gson.fromJson(accountsData.get(0), JsonObject.class);
                        log.info("USER ACCOUNTS JSON OBJECT {}", accountsDataJsonObject);

                        String linkedAccount = accountsDataJsonObject.get("linkedaccount").getAsString();
                        log.info("LINKED ACCOUNT {}", linkedAccount);


                        String scope = "SCW";
                        Map<String, String> esbRequest = constructBody(
                                groupWrapper.getCsbAccount(), phoneNumber,
                                linkedAccount, (int) totalMemberEarnings, transactionId,
                                scope, String.valueOf(chargeAmount));
                        String esbRequestBody = gson.toJson(esbRequest);

                        log.info("SHARE OUT DISBURSAL REQUEST TO CORE ACCOUNT {}", esbRequestBody);
                        String shareOutResponse = postBankWebClient.post()
                                .uri(postBankUrl)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(esbRequestBody)
                                .retrieve()
                                .bodyToMono(String.class)
                                .subscribeOn(Schedulers.boundedElastic())
                                .block();

                        JsonObject shareOutJsonObject = gson.fromJson(shareOutResponse, JsonObject.class);
                        log.info("SHARE OUT RESPONSE TO CORE ACCOUNT - IFT {}", shareOutJsonObject);
                        if (shareOutJsonObject.get("field39").getAsString().equals("00")) {
                            log.info("SHARE OUT RESPONSE TO CORE ACCOUNT ===== ON SUCCESS {}", shareOutJsonObject);
                            sendNotification(groupId, groupName, phoneNumber, totalMemberEarnings, memberName, language);
                            disableMemberPendingShareOuts(groupId, phoneNumber);
                            disablePendingDisbursement(groupId, phoneNumber);
                            saveDisbursedShareOut(coreAccount, phoneNumber, groupId, groupName, shareOutJsonObject.toString(), totalMemberEarnings, memberName, language);
                            //write off member loans
                            writeOffMemberLoans(memberLoansList, memberFinesList);
                            disableShareOutsDisbursed(groupId, phoneNumber);
                            //disable all payments on completion
                            disableShareOuts(groupId, phoneNumber);
                            disableShareOutsPayments(groupId, phoneNumber);
                            disableOtherGroupPayments(groupId, phoneNumber);
                            disableGroupFines(groupId, phoneNumber);
                            disableGroupLoanInterest(groupId, mchamaMember.getId());
                        }
                    } else if (totalMemberEarnings >= limitAmount && accountsData.isEmpty()) {
                        log.info("MEMBER HAS NO LINKED ACCOUNT {}", phoneNumber);
                        //TODO:: SEND ACTIVATION SMS
                        sendAccountActivation(groupId, groupName, phoneNumber, memberName, totalMemberEarnings, language, memberLoansList, memberFinesList);
                    }

                } else {
                    log.info("+++++++++++++++++++++++GROUP {}, MEMBER {}, AMOUNT {} DISBURSAL NOT FOUND +++++++++++++++++++++++", groupName, phoneNumber, totalMemberEarnings);

                }

            });

        }
    }

    private void sendAccountActivation(long groupId, String groupName, String phoneNumber, String memberName, double totalMemberEarnings, String language, List<LoansDisbursed> memberLoansList, List<Fines> memberFinesList) {
        AccountActivation accountActivation = accountActivationRepository.findFirstByGroupIdAndPhoneNumberAndSoftDeleteFalse(groupId, phoneNumber);
        if (accountActivation == null) {
            log.info("*******SENDING ACCOUNT ACTIVATION SMS REMINDER******");
            AccountActivation activation = new AccountActivation();
            activation.setGroupId(groupId);
            activation.setType("WITHDRAWAL");
            activation.setPhoneNumber(phoneNumber);
            activation.setProcess(PaymentEnum.SHARE_OUT_PENDING_DISBURSEMENT.name());
            accountActivationRepository.save(activation);

            //TODO:: SEND NOTIFICATION TO INDIVIDUAL MEMBER!!!
            notificationService.sendAccountActivation(phoneNumber, memberName, totalMemberEarnings, language, groupName);
            notificationService.sendShareOutWithdrawalToMembers(memberName, groupName, phoneNumber, language);
        } else {
            log.info("ACCOUNT ACTIVATION SMS ALREADY SEND TO MEMBER ");
            //activate counts
            accountActivation.setAccountAttempts(accountActivation.getAccountAttempts() + 1);
            accountActivation.setLastModifiedDate(new Date());
            accountActivation.setLastModifiedBy(phoneNumber);
            accountActivationRepository.save(accountActivation);
            if (accountActivation.getAccountAttempts() > 4) {
                //send sms to officials for withdrawal
                disableMemberPendingShareOuts(groupId, phoneNumber);
                disablePendingDisbursement(groupId, phoneNumber);
//                write off member loans
                writeOffMemberLoans(memberLoansList, memberFinesList);
                disableShareOutsDisbursed(groupId, phoneNumber);
//                disable all payments on completion
                disableShareOuts(groupId, phoneNumber);
                disableShareOutsPayments(groupId, phoneNumber);
                disableOtherGroupPayments(groupId, phoneNumber);
                disableGroupFines(groupId, phoneNumber);
                sendOfficialReminder(groupId, groupName, memberName, totalMemberEarnings);
            }
        }

    }

    private void sendOfficialReminder(long groupId, String groupName, String memberName, double totalMemberEarnings) {
        GroupWrapper group = chamaKycService.getMonoGroupById(groupId);
        if (group != null && group.isActive()) {
            chamaKycService.getFluxGroupMembers(group.getId())
                    .subscribe(member -> notificationService.sendOfficialReminder(groupName, memberName, totalMemberEarnings, member.getFirstname(), member.getPhonenumber(), member.getLanguage()));
        } else {
            log.error("COULD NOT SEND WITHDRAWAL REMINDER SMS. GROUP NOT FOUND.");
        }
    }

    private void sendNotification(long groupId, String groupName, String phoneNumber, double totalMemberEarnings, String memberName, String language) {
        //sendNotification
        notificationService.sendShareOutWithdrawalToMember(phoneNumber, memberName, totalMemberEarnings, language, groupName);
        //TODO:: SEND NOTIFICATION TO INDIVIDUAL MEMBER!!!
        notificationService.sendShareOutWithdrawalToMembers(memberName, groupName, phoneNumber, language);
    }


    private void disableMemberPendingShareOuts(long groupId, String phoneNumber) {
        ShareOutsPendingDisbursement shareOutsPendingDisbursement = shareOutsPendingDisbursementRepo.findFirstByGroupIdAndPhoneNumberAndPendingTrueAndStatusAndSoftDeleteFalseOrderByIdDesc(groupId, phoneNumber, PaymentEnum.SHARE_OUT_PENDING_DISBURSEMENT.name());
        if (!(shareOutsPendingDisbursement == null)) {
            shareOutsPendingDisbursement.setSoftDelete(true);
            shareOutsPendingDisbursement.setPending(false);
            shareOutsPendingDisbursement.setLastModifiedDate(new Date());
            shareOutsPendingDisbursement.setNarration(PaymentEnum.SHARE_OUT_DISBURSED_SUCCESSFULLY.name());
            shareOutsPendingDisbursement.setStatus(PaymentEnum.SHARE_OUT_DISBURSED_SUCCESSFULLY.name());
            shareOutsPendingDisbursementRepo.save(shareOutsPendingDisbursement);
        }
    }

    private void disablePendingDisbursement(long groupId, String phoneNumber) {
        List<ShareOutsPendingDisbursement> shareOutsPendingDisbursements = shareOutsPendingDisbursementRepo.findAllByGroupIdAndPhoneNumberAndStatusAndPendingTrueAndSoftDeleteFalseOrderByCreatedOnAsc(groupId, phoneNumber, PaymentEnum.SHARE_OUT_PENDING_DISBURSEMENT.name());
        if (!shareOutsPendingDisbursements.isEmpty()) {
            shareOutsPendingDisbursements.parallelStream().forEach(pending -> {
                pending.setSoftDelete(true);
                pending.setPending(false);
                pending.setNarration(PaymentEnum.SHARE_OUT_DISBURSED_SUCCESSFULLY.name());
                pending.setStatus(PaymentEnum.SHARE_OUT_DISBURSED_SUCCESSFULLY.name());
                shareOutsPendingDisbursementRepo.save(pending);
            });
        }
    }


    private void disableShareOutsDisbursed(long groupId, String phoneNumber) {
        List<ShareOutsDisbursed> shareOutsDisbursedList = shareOutsDisbursedRepo.findAllByGroupIdAndPhoneNumberAndStatusAndSoftDeleteFalseOrderByIdDesc(groupId, phoneNumber, PaymentEnum.SHARE_OUT_DISBURSED_SUCCESSFULLY.name());
        shareOutsDisbursedList.parallelStream().forEach(disbursed -> {
            disbursed.setLastModifiedDate(new Date());
            disbursed.setSoftDelete(true);
            disbursed.setDisbursed(true);
            shareOutsDisbursedRepo.save(disbursed);
        });
    }


    private void disableShareOutsPayments(long groupId, String phoneNumber) {
        List<ShareOuts> shareOuts = shareOutsRepository.findAllByGroupIdAndPhoneNumberAndPaymentStatusAndSoftDeleteFalse(groupId, phoneNumber, PaymentEnum.PAYMENT_SUCCESS.name());
        if (!shareOuts.isEmpty()) {
            shareOuts.parallelStream().forEach(payment -> {
                payment.setSoftDelete(false);
                payment.setSharOutStatus(PaymentEnum.SHARE_OUT_COMPLETED_SUCCESSFULLY.name());
                payment.setPaymentStatus(PaymentEnum.SHARE_OUT_COMPLETED_SUCCESSFULLY.name());
                payment.setLastModifiedDate(new Date());
                shareOutsRepository.save(payment);
            });
        }

        List<ShareOutsPayment> shareOutsPaymentList = shareOutsPaymentRepository.findAllByGroupIdAndPhoneNumberAndPaymentStatusAndSoftDeleteFalse(groupId, phoneNumber, PaymentEnum.PAYMENT_SUCCESS.name());
        if (!shareOutsPaymentList.isEmpty()) {
            shareOutsPaymentList.parallelStream().forEach(shareOutsPayment -> {
                shareOutsPayment.setPaymentStatus(PaymentEnum.SHARE_OUT_DISBURSED_SUCCESSFULLY.name());
                shareOutsPayment.setLastModifiedDate(new Date());
                shareOutsPayment.setSoftDelete(true);
                shareOutsPaymentRepository.save(shareOutsPayment);
            });
        }
    }


    private void disableOtherGroupPayments(long groupId, String phoneNumber) {
        //disable all savings
        List<ContributionPayment> savingsList = contributionsPaymentRepository.findAllByGroupIdAndPhoneNumberAndPaymentStatusAndPaymentTypeAndSoftDeleteFalseOrderByIdDesc(groupId, phoneNumber, PaymentEnum.PAYMENT_SUCCESS.name(), TypeOfContribution.saving.name());
        if (!savingsList.isEmpty()) {
            savingsList.parallelStream().forEach(saving -> {
                saving.setPaymentStatus(PaymentEnum.SHARE_OUT_DISBURSED_SUCCESSFULLY.name());
                saving.setSoftDelete(true);
                saving.setLastModifiedDate(new Date());
                contributionsPaymentRepository.save(saving);
            });
        }
        //todo:: Disable Loans
        List<ContributionPayment> loansList = contributionsPaymentRepository.findAllByGroupIdAndPaymentTypeOrderByIdAsc(groupId, TypeOfContribution.loan.name());
        if (!loansList.isEmpty()) {
            savingsList.parallelStream().forEach(loan -> {
                loan.setPaymentStatus(PaymentEnum.SHARE_OUT_DISBURSED_SUCCESSFULLY.name());
                loan.setSoftDelete(true);
                loan.setLastModifiedDate(new Date());
                contributionsPaymentRepository.save(loan);
            });
        }
    }


    private void disableGroupLoanInterest(long groupId, long member) {
        List<LoansDisbursed> loansDisbursedList = loansdisbursedRepo.findAllByGroupIdAndMemberIdAndSoftDeleteFalseOrderByIdAsc(groupId, member);
        if (!loansDisbursedList.isEmpty()) {
            loansDisbursedList.parallelStream().forEach(loansDisbursed -> {
                loansDisbursed.setLastModifiedDate(new Date());
                loansDisbursed.setStatus(PaymentEnum.SHARE_OUT_DISBURSED_SUCCESSFULLY.name());
                loansDisbursed.setSoftDelete(true);
                loansdisbursedRepo.saveAndFlush(loansDisbursed);
            });
        }
    }

    private void disableGroupFines(long groupId, String phoneNumber) {
        List<ContributionPayment> contributionPaymentList = contributionsPaymentRepository.findAllByGroupIdAndPhoneNumberAndPaymentStatusAndPaymentTypeAndSoftDeleteFalse(groupId, phoneNumber, PaymentEnum.PAYMENT_SUCCESS.name(), TypeOfContribution.fine.name());
        if (!contributionPaymentList.isEmpty()) {
            contributionPaymentList.parallelStream().forEach(payment -> {
                payment.setPaymentStatus(PaymentEnum.SHARE_OUT_DISBURSED_SUCCESSFULLY.name());
                payment.setSoftDelete(true);
                payment.setLastModifiedDate(new Date());
                contributionsPaymentRepository.save(payment);
            });
        }
    }


    private void writeOffMemberLoans(List<LoansDisbursed> memberLoansList, List<Fines> memberFinesList) {

        memberLoansList.parallelStream().forEach(loan -> {
            loan.setSoftDelete(true);
            if (loan.getStatus().equals(PaymentEnum.YET_TO_PAY.name())) {
                loan.setStatus(PaymentEnum.PAYMENT_WRITTEN_OFF.name());
                loan.setDueamount(0.0);
            } else {
                loan.setStatus(PaymentEnum.FULLY_PAID.name());
            }
            loan.setDuedate(new Date());
            loan.setPaymentStartDate(new Date());
            loan.setLastModifiedDate(new Date());
            loansdisbursedRepo.saveAndFlush(loan);
        });
        memberFinesList.parallelStream().forEach(fine -> {
            fine.setSoftDelete(true);
            if (fine.getPaymentStatus().equals(PaymentEnum.PAYMENT_PENDING.name())) {
                fine.setPaymentStatus(PaymentEnum.PAYMENT_WRITTEN_OFF.name());
                fine.setFineBalance(0.0);
            } else {
                fine.setPaymentStatus(PaymentEnum.FULLY_PAID.name());
            }
            fine.setLastModifiedDate(new Date());
            fine.setTransactionDate(new Date());
            finesRepository.saveAndFlush(fine);

        });
    }


    private void saveDisbursedShareOut(String coreAccount, String phoneNumber, long groupId, String groupName, String disbursalMessage, double disbursalAmount, String memberName, String language) {
        ShareOutsDisbursed sharePaid = shareOutsDisbursedRepo.findFirstByGroupIdAndPhoneNumberAndStatusAndDisbursedTrueAndSoftDeleteFalseOrderByIdDesc(groupId, phoneNumber, PaymentEnum.SHARE_OUT_DISBURSED_SUCCESSFULLY.name());
        if (sharePaid == null) {
            ShareOutsDisbursed shareOutsDisbursed = new ShareOutsDisbursed(coreAccount, phoneNumber, groupId, groupName, disbursalMessage, disbursalAmount, "");
            shareOutsDisbursedRepo.save(shareOutsDisbursed);

        } else {
            log.info("MEMBER {}, PHONE {}, IN GROUP {} ALREADY PAID==AMOUNT {}", memberName, phoneNumber, groupId, disbursalAmount);
        }
    }


    private void sendAnonymousShareOuts(long id, String phoneNumber) {
        GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(id);
        if (groupWrapper != null && groupWrapper.isActive()) {
            String groupName = groupWrapper.getName();
            chamaKycService.getGroupOfficials(id)
                    .subscribe(member -> notificationService.sendAnonymousShareOutsText(member.getFirstname(), groupName, member.getPhonenumber(), phoneNumber, member.getLanguage()));
        } else {
            log.error("Group not found. for share outs");
        }

    }


    @Async
    protected void disableShareOuts(long groupId, String phoneNumber) {
        List<ShareOuts> shareOutsList = shareOutsRepository.findAllByGroupIdAndPhoneNumberAndSoftDeleteFalseOrderByIdAsc(groupId, phoneNumber);
        if (!shareOutsList.isEmpty()) {
            shareOutsList.parallelStream().forEach(share -> {
                share.setSoftDelete(true);
                share.setSharOutStatus(PaymentEnum.SHARE_OUT_DISBURSED_SUCCESSFULLY.name());
                share.setPaymentStatus(PaymentEnum.SHARE_OUT_DISBURSED_SUCCESSFULLY.name());
                shareOutsRepository.save(share);
            });
        }
    }
}
