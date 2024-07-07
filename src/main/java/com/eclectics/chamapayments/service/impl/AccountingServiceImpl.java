package com.eclectics.chamapayments.service.impl;

import com.eclectics.chamapayments.config.CustomAuthenticationUtil;
import com.eclectics.chamapayments.model.*;
import com.eclectics.chamapayments.model.jpaInterfaces.UpcomingContributionsProjection;
import com.eclectics.chamapayments.repository.*;
import com.eclectics.chamapayments.service.*;
import com.eclectics.chamapayments.service.enums.*;
import com.eclectics.chamapayments.util.TransactionIdGenerator;
import com.eclectics.chamapayments.wrappers.esbWrappers.EsbAccountWrapper;
import com.eclectics.chamapayments.wrappers.request.*;
import com.eclectics.chamapayments.wrappers.response.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.util.Pair;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import javax.annotation.PostConstruct;
import javax.net.ssl.SSLException;
import java.io.ByteArrayInputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.eclectics.chamapayments.service.impl.LoanServiceImpl.GROUP_NOT_FOUND;
import static com.eclectics.chamapayments.util.RequestConstructor.*;
import static java.time.Instant.ofEpochMilli;
import static java.util.stream.Collectors.groupingBy;

/**
 * @author david charo
 * created 07/12/2021
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountingServiceImpl implements AccountingService {
    private final AccountTypeRepository accountTypeRepository;
    private final LoanPenaltyRepository loanPenaltyRepository;
    private final LoanproductsRepository loanproductsRepository;
    private final LoansdisbursedRepo loansdisbursedRepo;
    private final PaymentMemberRepository memberRepository;
    private final GroupRepository groupRepository;
    private final LoansrepaymentRepo loansrepaymentRepo;
    private final AccountsRepository accountsRepository;
    private final FinesRepository finesRepository;
    private final FinesPendingApprovalsRepository finesPendingApprovalsRepository;
    private final LoanService loanService;
    private final OutstandingContributionPaymentRepository outstandingContributionPaymentRepository;
    private final ContributionTypesRepository contributionTypesRepository;
    private final ScheduleTypeRepository scheduleTypesRepository;
    private final ContributionRepository contributionsRepository;
    private final TransactionlogsRepo transactionlogsRepo;
    private final AmounttypeRepo amounttypeRepo;
    private final WithdrawallogsRepo withdrawallogsRepo;
    private final TransactionspendingaApprovalRepo transactionspendingaApprovalRepo;
    private final WithdrawalspendingapprovalRepo withdrawalspendingapprovalRepo;
    private final ContributionsPaymentRepository contributionsPaymentRepository;
    private final KitTransferPendingApprovalsRepository kitTransferPendingApprovalsRepository;
    private final ContributionSchedulePaymentRepository contributionSchedulePaymentRepository;
    private final LoanapplicationsRepo loanapplicationsRepo;
    private final ChamaKycService chamaKycService;
    private final NotificationService notificationService;
    private final PenaltyRepository penaltyRepository;
    private final ESBLoggingService esbLoggingService;
    private final OverpaidContributionRepository overpaidContributionRepository;
    private final OtherChannelsBalancesRepository otherChannelsBalancesRepository;
    BiPredicate<Long, String> groupFilterByGroupNameParamId;
    private final NumberFormat numberFormat;
    private final ESBService esbService;
    private final ResourceBundleMessageSource source;
    private final GroupMembersRepository groupMembersRepository;
    private final ShareOutsRepository shareOutsRepository;
    private final ShareOutsPaymentRepository shareOutsPaymentRepository;
    private final ContributionsPendingApprovalsRepository contributionsPendingApprovalsRepository;
    private final NotificationsRepository notificationsRepository;
    private final AuditTrailRepository auditTrailRepository;
    private final AssignTransactionPendingApprovalsRepository assignTransactionPendingApprovalsRepository;
    private final ShareOutsDisbursedRepo shareOutsDisbursedRepo;
    private final ShareOutsPendingDisbursementRepo shareOutsPendingDisbursementRepo;
    private final GroupShareOutsRepository groupShareOutsRepository;
    private final MailService mailService;
    private final ShareOutAcceptorRepository shareOutAcceptorRepository;
    public static final String MEMBER_NOT_FOUND = "Member not found";
    int numThreads = Runtime.getRuntime().availableProcessors();
    ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
    @Value("${esb.channel.uri}")
    private String postBankUrl;
    @Value("${esb.statement}")
    private String statementUrl;
    private WebClient postBankWebClient;

    @Value("${auth.baseUrl}")
    private String authServerUrl;
    @Value("${auth.mobile.username}")
    private String authUserName;
    @Value("${auth.mobile.password}")
    private String authPassword;
    private WebClient webClient;

    @Value("${statement.url}")
    private String statementDataUrl;

    @Value("${esb.accounts.account}")
    private String userAccounts;
    JsonParser jsonParser = new JsonParser();
    Gson gson = new Gson();

    @PostConstruct
    public void initUserAccounts() throws SSLException {
        SslContext sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        HttpClient httpClient = HttpClient.create().secure(sslContextSpec -> sslContextSpec.sslContext(sslContext));
        webClient = WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).baseUrl(userAccounts).build();

    }

    @PostConstruct
    private void init() {
        groupFilterByGroupNameParamId = (groupId, filterName) -> {
            String groupName = chamaKycService.getMonoGroupNameByGroupId(groupId);
            if (groupName == null || !filterName.equalsIgnoreCase("all")) {
                return filterName.equalsIgnoreCase(groupName);
            }
            return true;
        };
        postBankWebClient = WebClient.builder().baseUrl(postBankUrl).build();
    }

    @PostConstruct
    private void initStatement() {
        postBankWebClient = WebClient.builder().baseUrl(statementDataUrl).build();
    }

    private String getResponseMessage(String tag) {
        Locale locale = LocaleContextHolder.getLocale();
        return source.getMessage(tag, null, locale);
    }

    @Override
    public List<AccountType> findAccountTypes() {
        return accountTypeRepository.findAll();
    }


    @Override
    public List<AccountDto> getAccountbyGroup(long groupId) {
        return accountsRepository.findByGroupIdAndActive(groupId, true)
                .stream().map(a ->
                        AccountDto.builder()
                                .accountId(a.getId())
                                .groupId(a.getGroupId())
                                .accountbalance(a.getAccountbalance())
                                .active(a.getActive())
                                .availableBal(a.getAvailableBal())
                                .name(a.getName())
                                .accountdetails(a.getAccountdetails())
                                .accountType(a.getAccountType().getAccountName())
                                .build())
                .collect(Collectors.toList());
    }


    @Override
    public List<ContributionType> getContributiontypes() {
        return contributionTypesRepository.findAll();
    }


    @Override
    public List<AmountType> getAmounttypes() {
        return amounttypeRepo.findAll();
    }

    public double getTotalWithdrawalbyContribution(Contributions contributions) {
        return withdrawallogsRepo.getTotalbyContribution(contributions);
    }

    @Override
    public void createContribution(Contributions contributions) {
        contributionsRepository.save(contributions);
    }

    @Override
    public Mono<UniversalResponse> approveContributionPayment(long paymentId, boolean approved, String approvedBy) {
        return Mono.fromCallable(() -> {
            TransactionsPendingApproval transactionsPendingApproval = transactionspendingaApprovalRepo.findByIdAndPendingTrue(paymentId).orElse(null);
            if (transactionsPendingApproval == null)
                return new UniversalResponse("fail", getResponseMessage("transactionPendingApprovalNotFound"));

            MemberWrapper memberWrapper = chamaKycService.searchMonoMemberByPhoneNumber(approvedBy);
            if (memberWrapper == null) return new UniversalResponse("fail", getResponseMessage("approverNotFound"));

            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(transactionsPendingApproval.getContribution().getMemberGroupId());

            if (groupWrapper == null) return new UniversalResponse("fail", getResponseMessage("groupNotFound"));

            if (!groupWrapper.isActive()) return new UniversalResponse("fail", getResponseMessage("groupIsInactive"));

            if (!approved) {
                transactionsPendingApproval.setApproved(false);
                transactionsPendingApproval.setApprovedby(approvedBy);
                transactionsPendingApproval.setPending(false);
                transactionsPendingApproval.setLastModifiedDate(new Date());
            } else {
                Accounts accounts = transactionsPendingApproval.getAccount();
                Contributions contributions = transactionsPendingApproval.getContribution();

                TransactionsLog transactionsLog = new TransactionsLog();
                transactionsLog.setContributionNarration(transactionsPendingApproval.getContribution_narration());
                transactionsLog.setCreditaccounts(accounts);
                transactionsLog.setDebitphonenumber(transactionsPendingApproval.getPhonenumber());
                String transid = accounts.getAccountType().getAccountPrefix().concat(String.valueOf(new Date().getTime()));
                transactionsLog.setUniqueTransactionId(transid);
                transactionsLog.setOldbalance(accounts.getAvailableBal());
                transactionsLog.setNewbalance(accounts.getAvailableBal() + transactionsPendingApproval.getAmount());
                transactionsLog.setTransamount(transactionsPendingApproval.getAmount());
                transactionsLog.setCapturedby(transactionsPendingApproval.getCapturedby());
                transactionsLog.setApprovedby(approvedBy);
                transactionsLog.setContributions(contributions);

                transactionsPendingApproval.setApproved(true);
                transactionsPendingApproval.setApprovedby(approvedBy);
                transactionsPendingApproval.setPending(false);

                //set new account balance
                accounts.setAvailableBal(transactionsLog.getNewbalance());

                transactionlogsRepo.save(transactionsLog);
                accountsRepository.save(accounts);
                transactionspendingaApprovalRepo.save(transactionsPendingApproval);

                updateContributionPayment(transactionsPendingApproval.getContributionPaymentId());
                sendContributionTextToMembers(memberWrapper, groupWrapper.getId(), (int) transactionsPendingApproval.getAmount(), false);
                return new UniversalResponse("success", getResponseMessage("transactionApprovalAccepted"));
            }
            try {
                notificationService.sendContributionFailureMessage(memberWrapper.getPhonenumber(), memberWrapper.getFirstname(), (int) transactionsPendingApproval.getAmount(), memberWrapper.getLanguage());
            } catch (Exception e) {
                log.error("sending contribution message failure {}", e.getMessage());
            }

            transactionspendingaApprovalRepo.save(transactionsPendingApproval);
            return new UniversalResponse("success", getResponseMessage("transactionApprovalDeclined"));
        }).publishOn(Schedulers.boundedElastic());
    }

    private void updateContributionPayment(Long id) {
        Optional<ContributionPayment> contributionPaymentOptional = contributionsPaymentRepository.findById(id);
        if (contributionPaymentOptional.isEmpty()) {
            return;
        }

        ContributionPayment contributionPayment = contributionPaymentOptional.get();
        contributionPayment.setPaymentStatus(PaymentEnum.PAYMENT_SUCCESS.name());
        contributionsPaymentRepository.save(contributionPayment);
    }

    Function<TransactionsPendingApproval, PaymentApproval> transactionsPendingApprovalPaymentApprovalFunction() {
        return p -> PaymentApproval.builder().creditaccountid(p.getAccount().getId()).creditaccountname(p.getAccount().getName()).creditaccounttype(p.getAccount().getAccountType().getAccountName()).amount(p.getAmount()).capturedby(p.getCapturedby()).contributionid(p.getContribution().getId()).debitaccount(p.getPhonenumber()).narration(p.getContribution_narration()).paymentid(p.getId()).appliedon(p.getCreatedOn()).build();
    }

    @Override
    public PageDto getPendingWithdrawalRequestByGroupId(long groupId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.abs(page), Math.min(size, 50));

        Page<WithdrawalsPendingApproval> pagedData = withdrawalspendingapprovalRepo.findByGroupandPendingTrue(groupId, pageable);

        List<WithdrawalApproval> withdrawalApprovalList = pagedData.getContent()
                .stream().map(mapWithdrawalsToWithdrawalResponse())
                .collect(Collectors.toList());

        return new PageDto(pagedData.getNumber(), pagedData.getTotalPages(), withdrawalApprovalList);
    }

    Function<WithdrawalsPendingApproval, WithdrawalApproval> mapWithdrawalsToWithdrawalResponse() {
        return p -> WithdrawalApproval.builder()
                .amount(p.getAmount())
                .capturedby(p.getCapturedby())
                .capturedByPhoneNumber(p.getCapturedByPhoneNumber())
                .fullName(p.getCapturedby())
                .contributionid(p.getContribution().getId())
                .creditaccount(p.getPhonenumber())
                .debitaccountid(p.getAccount().getId())
                .debitaccountname(p.getAccount().getName())
                .debitaccounttype(p.getAccount()
                        .getAccountType()
                        .getAccountName())
                .requestid(p.getId())
                .withdrawal_narration(p.getWithdrawal_narration()).withdrawalreason(p.getWithdrawalreason()).status(p.getStatus()).appliedon(p.getCreatedOn()).build();
    }


    Function<ShareOutsPayment, ShareOutsMapper> mapShareOutsToWrapperResponse(String userName, Long groupId) {
        return p -> {
            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(groupId);
            if (groupWrapper == null)
                return null;
            MemberWrapper memberWrapper = chamaKycService.searchMonoMemberByPhoneNumber(p.getPhoneNumber());
            if (memberWrapper == null) {
                return null;
            }

            List<ShareOutsPayment> paymentList = shareOutsPaymentRepository.findAllByGroupIdAndSoftDeleteFalseOrderByIdAsc(groupWrapper.getId());
            if (paymentList.isEmpty()) {
                return null;
            }
            double groupContribution = paymentList.stream().mapToDouble(ShareOutsPayment::getTotalContribution).sum();
            log.info("TOTAL SHARE-OUTS PAYMENT {}m, MEMBER CONTRIBUTION {}, MEMBER {}", groupContribution, p.getTotalContribution(), p.getPhoneNumber());

            List<LoansDisbursed> loansDisbursedList = loansdisbursedRepo.findAllByGroupIdAndStatusAndSoftDeleteFalseOrderByIdAsc(groupId, PaymentEnum.FULLY_PAID.name());

            double loanInterest = loansDisbursedList.stream().mapToDouble(LoansDisbursed::getInterest).sum();

            double fineBalance = calculateBalance(groupId, TypeOfContribution.fine.name());
            double fineDeductions = calculateTransferBalance(groupId, TypeOfContribution.fine.name());

            log.info("FINE BALANCE {}, FINE DEDUCTIONS {}, FINES TOTAL {}", fineBalance, fineDeductions, fineBalance - fineDeductions);
            //Fines contributions from menu
            fineBalance = formatInterestAmount(fineBalance);
            fineDeductions = formatInterestAmount(fineDeductions);
            fineBalance = fineBalance - fineDeductions;

            log.info("GROUP FINES {}, GROUP LOANS {}, TOTAL GROUP INTEREST {}", fineBalance, loanInterest, loanInterest + fineBalance);

            loanInterest = formatInterestAmount(loanInterest);

            double totalInterest = loanInterest + fineBalance;

            totalInterest = formatInterestAmount(totalInterest);

            double amount = p.getTotalContribution();

            amount = formatInterestAmount(amount);
            //member percentage rate
            double memberPercentage = (amount / groupContribution) * 100;

            memberPercentage = formatInterestAmount(memberPercentage);

            double memberEarnings = (memberPercentage / 100) * totalInterest;
            memberEarnings = formatInterestAmount(memberEarnings);
            memberEarnings = formatAmount(memberEarnings);
            double finalAmount = amount + memberEarnings;

            finalAmount = formatAmount(finalAmount);

            if (finalAmount < 1) {
                finalAmount = 0.0;
            }

            List<LoansDisbursed> memberLoansList = loansdisbursedRepo.sumMemberPendingLoans(groupWrapper.getId(), memberWrapper.getId());


            double memberLoans = memberLoansList.stream().mapToDouble(LoansDisbursed::getDueamount).sum();

            log.info("GROUP {}, MEMBER {}, MEMBER LOANS {} ", groupWrapper.getName(), memberWrapper.getFirstname(), memberLoans);

            if (memberLoans < 1) {
                memberLoans = 0.0;
            } else {
                memberLoans = formatAmount(memberLoans);

            }
            double memberDeductions = finalAmount - memberLoans;
            String memberName = String.format("%s %s", memberWrapper.getFirstname(), memberWrapper.getLastname());
            String memberPhone = memberWrapper.getPhonenumber();

            log.info("MEMBER NAME {}, ====== PHONE {}, AMOUNT {}", memberName, memberPhone, memberDeductions);
            auditTrail("share out preview", "share out preview for members", memberWrapper.getImsi());
            creatNotification(groupId, groupWrapper.getName(), "share out preview for members by " + memberWrapper.getImsi());
            return ShareOutsMapper.builder()
                    .name(memberName)
                    .phone(p.getPhoneNumber())
                    .interestEarn(memberEarnings)
                    .totalEarnings(amount)
                    .totalContribution(memberDeductions)
                    .group(groupWrapper.getName())
                    .build();
        };
    }

    Function<OtherChannelsBalances, OtherGroupTransactionWrapper> mapOtherTransactionsToWrapperResponse(String username) {
        MemberWrapper member = chamaKycService.searchMonoMemberByPhoneNumber(username);
        if (member == null)
            return null;
        return t -> {
            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(t.getGroupId());
            if (groupWrapper == null) {
                return null;
            }

            return OtherGroupTransactionWrapper.builder()
                    .otherTransactionId(t.getId())
                    .groupId(t.getGroupId())
                    .name(t.getGroupName())
                    .accountNumber(t.getCbsAccount())
                    .amount(formatAmount(t.getCreditAmount()))
                    .transactionId(t.getTransactionId())
                    .transactiondate(t.getCreatedOn())
                    .build();
        };
    }

    Function<OtherChannelsBalances, OtherGroupTransactionReportWrapper> mapToOtherGroupTransactionReportWrapper(String username) {

        return t -> {
            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(t.getGroupId());
            if (groupWrapper == null) {
                return null;
            }
            return OtherGroupTransactionReportWrapper.builder()
                    .id(t.getId())
                    .branch(t.getBranch())
                    .channel(t.getChannel())
                    .tranType(t.getTranType())
                    .tranDate(t.getTranDate())
                    .groupName(t.getGroupName())
                    .cbsAccount(t.getCbsAccount())
                    .cbsRegNumber(t.getCbsRegNumber())
                    .transactionId(t.getTransactionId())
                    .amountDepleted(t.getAmountDepleted())
                    .transactionActedOn(t.getTransactionActedOn())
                    .debitAmount(formatAmount(t.getDebitAmount()))
                    .creditAmount(formatAmount(t.getCreditAmount()))
                    .actualBalance(formatAmount(t.getActualBalance()))
                    .ledgerBalance(formatAmount(t.getLedgerBalance()))
                    .runningBalance(formatAmount(t.getRunningBalance()))
                    .transactionDescription(t.getTransactionDescription())
                    .build();
        };
    }

    Function<ContributionPayment, TransactionLogWrapper> mapTransactionLogToWrapperResponse1(String username) {
        return p -> {
            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(p.getGroupId());
            MemberWrapper memberWrapper = chamaKycService.searchMonoMemberByPhoneNumber(p.getPhoneNumber());

            return TransactionLogWrapper.builder()
                    .amount(formatAmount(Double.valueOf(p.getAmount())))
                    .capturedby(memberWrapper.getFirstname())
                    .contributionid(p.getContributionId())
                    .creditaccount(groupWrapper.getCsbAccount())
                    .creditaccountname(groupWrapper.getName())
                    .contributionname(groupWrapper.getName())
                    .debitaccount(p.getPhoneNumber())
                    .narration(p.getNarration())
                    .transactionid(p.getTransactionId())
                    .transactiondate(p.getCreatedOn())
                    .groupname(groupWrapper.getName())
                    .paymentType(p.getPaymentType())
                    .membername(memberWrapper.getFirstname().concat(" ").concat(memberWrapper.getLastname()))
                    .accounttype("mchama account")
                    .build();
        };
    }


    Function<TransactionsLog, TransactionLogWrapper> mapTransactionLogToWrapperResponse(String username) {
        return p -> {
            Optional<String> groupName = chamaKycService.getGroupNameByGroupId(p.getContributions().getMemberGroupId());
            Optional<Member> member = memberRepository.findByImsi(p.getDebitphonenumber());
            Optional<MemberWrapper> memberWrapper1 = chamaKycService.getMemberDetailsById(member.get().getId());
            Optional<Member> memberWrapperOptional = memberRepository.findByImsi(p.getDebitphonenumber());
            if (groupName.isEmpty() || memberWrapperOptional.isEmpty())
                return null;

            return TransactionLogWrapper.builder()
                    .amount(p.getTransamount())
                    .capturedby(p.getCapturedby())
                    .contributionid(p.getContributions().getId())
                    .contributionname(p.getContributions().getName())
                    .creditaccount(parseAccount(p.getCreditaccounts()))
                    .creditaccountname(p.getCreditaccounts().getName())
                    .debitaccount(p.getDebitphonenumber())
                    .narration(p.getContributionNarration())
                    .transactionid(p.getUniqueTransactionId())
                    .transactiondate(p.getCreatedOn())
                    .groupname(groupName.get())
                    .paymentType(p.getTransactionType())
                    .membername(memberWrapper1.get().getFirstname().concat(" ").concat(memberWrapper1.get().getLastname()))
                    .accounttype(p.getCreditaccounts().getAccountType().getAccountName())
                    .build();
        };
    }


    Function<WithdrawalLogs, TransactionLogWrapper> mapWithdrawalLogsToWrapperResponse() {
        return p -> {
            Optional<MemberWrapper> memberWrapperOptional = chamaKycService.searchMemberByPhoneNumber(p.getCreditphonenumber());
            Optional<String> groupName = chamaKycService.getGroupNameByGroupId(p.getContributions().getMemberGroupId());
            if (memberWrapperOptional.isEmpty() || groupName.isEmpty()) return null;
            MemberWrapper memberWrapper = memberWrapperOptional.get();
            String memberName = memberWrapper.getFirstname().concat(" ").concat(memberWrapper.getLastname());
            return TransactionLogWrapper.builder().amount(p.getTransamount()).capturedby(p.getCapturedby()).contributionid(p.getContributions().getId()).contributionname(p.getContributions().getName()).creditaccount(p.getCreditphonenumber()).creditaccountname(memberName).debitaccount(memberWrapper.getImsi()).narration(p.getContribution_narration()).transactionid(p.getUniqueTransactionId()).transactiondate(p.getCreatedOn()).groupname(groupName.get()).membername(memberName).accounttype(p.getDebitAccounts().getAccountType().getAccountName()).build();
        };
    }

    String parseAccount(Accounts accounts) {
        String raw_requiredfields = accounts.getAccountType().getAccountFields();
        JsonArray fields = jsonParser.parse(raw_requiredfields).getAsJsonArray();
        String accountdetail_field = "na";
        if (fields.contains(jsonParser.parse("account_number"))) {
            JsonObject accountdetails = jsonParser.parse(accounts.getAccountdetails()).getAsJsonObject();
            accountdetail_field = accountdetails.get("account_number").getAsString();
        }
        return accountdetail_field;

    }

    @Override
    public List<TransactionLogWrapper> getTransactionsbyGroup(long groupid, Pageable pageable, String username) {
        return contributionsPaymentRepository.getTransactionsByGroup(groupid, pageable)
                .stream()
                .filter(p -> p.getPhoneNumber() != null)
                .map(mapTransactionLogToWrapperResponse1(username))
                .collect(Collectors.toList());
    }


    @Override
    public List<TransactionLogWrapper> getTransactionsbyGroupUser(long groupId, String username, Pageable pageable) {
        return contributionsPaymentRepository.findAllUserTransactions(groupId, username, pageable)
                .stream()
                .map(mapTransactionLogToWrapperResponse1(username))
                .collect(Collectors.toList());
    }

    @Override
    public List<TransactionLogWrapper> getWithdrawalsbyGroup(long groupid, Pageable pageable) {
        return withdrawallogsRepo.getWithdrawalsbygroup(groupid, pageable).stream().map(mapWithdrawalLogsToWrapperResponse()).sorted().collect(Collectors.toList());
    }


    @Override
    public UniversalResponse getTransactionsByUser(String username, Pageable pageable) {
        Page<TransactionsLog> pagedData = transactionlogsRepo.findByDebitphonenumberOrderByCreatedOnDesc(username, pageable);

        List<TransactionLogWrapper> data = pagedData.getContent()
                .parallelStream()
                .map(mapTransactionLogToWrapperResponse(username))
                .collect(Collectors.toList());
        return UniversalResponse.builder()
                .status("Success")
                .message("Transactions by user")
                .data(data)
                .metadata(Map.of("currentPage", pagedData.getNumber(), "numOfRecords", pagedData.getNumberOfElements(), "totalPages", pagedData.getTotalPages()))
                .timestamp(new Date())
                .build();
    }


    @Override
    public List<TransactionLogWrapper> getWithdrawalsbyUser(String phonenumber, Pageable pageable) {
        return withdrawallogsRepo.findByCreditphonenumber(phonenumber, pageable).stream().map(mapWithdrawalLogsToWrapperResponse()).sorted().collect(Collectors.toList());
    }


    @Override
    public UniversalResponse getTransactionsByContributions(Long contributionId, Pageable pageable, String username) {
        Optional<Contributions> contribution = contributionsRepository.findById(contributionId);

        if (contribution.isEmpty()) return new UniversalResponse("fail", getResponseMessage("contributionNotFound"));

        Page<TransactionsLog> pagedData = transactionlogsRepo.findByContributionsOrderByCreatedOnDesc(contribution.get(), pageable);

        List<TransactionLogWrapper> data = pagedData.getContent().parallelStream().map(mapTransactionLogToWrapperResponse(username)).sorted().collect(Collectors.toList());
        return UniversalResponse.builder().status("Success").message("Transactions by contributions").data(data).metadata(Map.of("currentPage", pagedData.getNumber(), "numOfRecords", pagedData.getNumberOfElements(), "totalPages", pagedData.getTotalPages())).timestamp(new Date()).build();
    }


    @Override
    public List<TransactionLogWrapper> getWithdrawalsbyContribution(Long contributionId, Pageable pageable) {
        Optional<Contributions> contribution = contributionsRepository.findById(contributionId);

        if (contribution.isEmpty()) return Collections.emptyList();

        return withdrawallogsRepo.findByContributions(contribution.get(), pageable).stream().map(mapWithdrawalLogsToWrapperResponse()).sorted().collect(Collectors.toList());
    }

    @Override
    public List<TransactionLogWrapper> getTransactionsbyUserandContributions(String phonenumber, Contributions contributions, Pageable pageable) {
        return transactionlogsRepo.findByDebitphonenumberAndContributionsAndSoftDeleteFalseOrderByCreatedOnDesc(phonenumber, contributions, pageable).stream().map(mapTransactionLogToWrapperResponse(phonenumber)).collect(Collectors.toList());
    }


    @Override
    public List<TransactionLogWrapper> getTransactionsbyUserandGroupId(String username, long groupId, Pageable pageable) {
        return transactionlogsRepo.getTransactionsbygroupandmember(groupId, username, pageable).stream().map(mapTransactionLogToWrapperResponse(username)).collect(Collectors.toList());
    }


    @Override
    public UniversalResponse getTransactionsByAccount(Long accountId, Pageable pageable, String username) {
        Accounts account = accountsRepository.findAccountsByIdAndSoftDeleteFalse(accountId);

        if (account == null)
            return new UniversalResponse("fail", getResponseMessage("accountNotFound"));

        Page<TransactionsLog> pagedData = transactionlogsRepo.findByCreditaccountsOrderByCreatedByDesc(account, pageable);

        List<TransactionLogWrapper> data = pagedData.getContent().parallelStream().map(mapTransactionLogToWrapperResponse(username)).sorted().collect(Collectors.toList());

        return UniversalResponse.builder().status("Success").message("Transactions by account").data(data).metadata(Map.of("currentPage", pagedData.getNumber(), "numOfRecords", pagedData.getNumberOfElements(), "totalPages", pagedData.getTotalPages())).timestamp(new Date()).build();
    }


    @Override
    public List<TransactionLogWrapper> getWithdrawalsbyAccount(Long accountId, Pageable pageable) {
        Optional<Accounts> account = accountsRepository.findById(accountId);

        if (account.isEmpty()) return Collections.emptyList();

        return withdrawallogsRepo.findByDebitAccounts(account.get(), pageable).stream().map(mapWithdrawalLogsToWrapperResponse()).sorted().collect(Collectors.toList());
    }


    static Map<String, TemporalAdjuster> timeAdjusters() {
        Map<String, TemporalAdjuster> adjusterHashMap = new HashMap<>();
        adjusterHashMap.put("days", TemporalAdjusters.ofDateAdjuster(d -> d)); // identity
        adjusterHashMap.put("weeks", TemporalAdjusters.previousOrSame(DayOfWeek.of(1)));
        adjusterHashMap.put("months", TemporalAdjusters.firstDayOfMonth());
        adjusterHashMap.put("years", TemporalAdjusters.firstDayOfYear());
        return adjusterHashMap;
    }

    static Comparator<Map<String, Object>> mapComparator() {
        return new Comparator<Map<String, Object>>() {
            @SneakyThrows
            public int compare(Map<String, Object> m1, Map<String, Object> m2) {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
                Date date1 = sdf.parse(m1.get("dateofday").toString());
                Date date2 = sdf.parse(m2.get("dateofday").toString());
                return date1.compareTo(date2);
            }
        };
    }


    BiPredicate<String, String> groupFilterByGroupNameParamName = (groupName, filterName) -> {
        if (!filterName.equalsIgnoreCase("all")) {
            return groupName.equalsIgnoreCase(filterName);
        }
        return true;
    };

    @Override
    public List<Map<String, Object>> groupTransactionsDetailed(Date startDate, Date endDate, String period, String group) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy");
        String startDateFormat = simpleDateFormat.format(endDate);
        String endDateFormat = simpleDateFormat.format(endDate);
        List<TransactionsLog> transactionsLogList = transactionlogsRepo.findAllByCreatedOnBetweenOrderByCreatedOnDesc(startDate, endDate);

        Period dp = Period.between(endDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate(), startDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        int diffDays = Math.abs(dp.getDays());

        double walletTotals = transactionsLogList.stream().filter(p -> groupFilterByGroupNameParamId.test(p.getContributions().getMemberGroupId(), group)).filter(transactionsLog -> transactionsLog.getCreditaccounts().getAccountType().getId() == 1).map(TransactionsLog::getTransamount).reduce(0.0, Double::sum);

        long countWalletTotals = transactionsLogList.stream().filter(p -> groupFilterByGroupNameParamId.test(p.getContributions().getMemberGroupId(), group)).filter(transactionsLog -> transactionsLog.getCreditaccounts().getAccountType().getId() == 1).count();


        double bankTotals = transactionsLogList.stream().filter(p -> groupFilterByGroupNameParamId.test(p.getContributions().getMemberGroupId(), group)).filter(transactionsLog -> transactionsLog.getCreditaccounts().getAccountType().getId() == 2).map(TransactionsLog::getTransamount).reduce(0.0, Double::sum);

        double mobileMoneyTotal = transactionsLogList.stream().filter(p -> groupFilterByGroupNameParamId.test(p.getContributions().getMemberGroupId(), group)).filter(transactionsLog -> transactionsLog.getCreditaccounts().getAccountType().getId() == 3).map(TransactionsLog::getTransamount).reduce(0.0, Double::sum);

        double saccoTotals = transactionsLogList.stream().filter(p -> groupFilterByGroupNameParamId.test(p.getContributions().getMemberGroupId(), group)).filter(transactionsLog -> transactionsLog.getCreditaccounts().getAccountType().getId() == 4).map(TransactionsLog::getTransamount).reduce(0.0, Double::sum);

        double pettyCashTotals = transactionsLogList.stream().filter(p -> groupFilterByGroupNameParamId.test(p.getContributions().getMemberGroupId(), group)).filter(transactionsLog -> transactionsLog.getCreditaccounts().getAccountType().getId() == 5).map(TransactionsLog::getTransamount).reduce(0.0, Double::sum);

        double investmentTotals = transactionsLogList.stream().filter(p -> groupFilterByGroupNameParamId.test(p.getContributions().getMemberGroupId(), group)).filter(transactionsLog -> transactionsLog.getCreditaccounts().getAccountType().getId() == 7).map(TransactionsLog::getTransamount).reduce(0.0, Double::sum);

        double walletAvg = walletTotals / diffDays;
        List<ContributionPayment> contributionPayments;
        if (!group.equals("all")) {
            Optional<Group> checkGroup = groupRepository.findByName(group);
            if (checkGroup.isEmpty()) {
                return (List<Map<String, Object>>) new UniversalResponse("fail", GROUP_NOT_FOUND);
            } else {
                Group group1 = checkGroup.get();
                contributionPayments = contributionsPaymentRepository.findAllByCreatedOnBetweenAndGroupIdAndPaymentStatus(startDate, endDate, group1.getId(), "PAID");
            }

        } else {
            contributionPayments = contributionsPaymentRepository.findAllByCreatedOnBetweenAndPaymentStatus(startDate, endDate, "PAID");
        }
        double totalGroupPayments = contributionPayments.stream().mapToInt(ContributionPayment::getAmount).sum();
        double totalGroupPaymentsCount = (long) contributionPayments.size();
        double groupPaymentAvg = totalGroupPayments / diffDays;
        List<WithdrawalLogs> withdrawalLogsList = withdrawallogsRepo.findAllByCreatedOnBetweenOrderByCreatedOnDesc(startDate, endDate);


        double totalGroupWithdrawals = withdrawalLogsList.stream().mapToDouble(WithdrawalLogs::getTransamount).sum();
        long totalGroupWithdrawalsCount = withdrawalLogsList.size();
        double withdrawalsAvg = totalGroupWithdrawals / diffDays;

        long groupsTransCount = (long) (totalGroupPaymentsCount + totalGroupWithdrawalsCount);
        double totalTransactedAmount = totalGroupPayments + totalGroupWithdrawals;
        double transactedAmtAvg = totalTransactedAmount / diffDays;

        Map<String, Object> groupPaymentMap = new LinkedHashMap<>();
        groupPaymentMap.put("payments count", totalGroupPaymentsCount);
        groupPaymentMap.put("total payments", totalGroupPayments);
        groupPaymentMap.put("average payments", groupPaymentAvg);
        Map<String, Object> groupWithdrawalsMap = new LinkedHashMap<>();
        groupWithdrawalsMap.put("withdrawals count", totalGroupWithdrawalsCount);
        groupWithdrawalsMap.put("total withdrawals", totalGroupWithdrawals);
        groupWithdrawalsMap.put("average withdrawals", withdrawalsAvg);


        Map<String, Object> transactionsMap = new LinkedHashMap<>();
        transactionsMap.put("transactions Count", groupsTransCount);
        transactionsMap.put("total amount", totalTransactedAmount);
        transactionsMap.put("average transactions", transactedAmtAvg);

        Map<String, Object> walletTransactions = new LinkedHashMap<>();
        walletTransactions.put("transactions count", countWalletTotals);
        walletTransactions.put("total amount", walletTotals);
        walletTransactions.put("average", walletAvg);

        Map<String, Object> transactionsByAccountType = new LinkedHashMap<>();
        transactionsByAccountType.put("wallet", walletTotals);
        transactionsByAccountType.put("bank", bankTotals);
        transactionsByAccountType.put("mobileMoney", mobileMoneyTotal);
        transactionsByAccountType.put("sacco", saccoTotals);
        transactionsByAccountType.put("pettyCash", pettyCashTotals);
        transactionsByAccountType.put("investment", investmentTotals);
//MODIFIED BY KIPTOO
        Map<Object, List<ContributionPayment>> transData = contributionPayments.stream()
                .collect(groupingBy(
                        t -> ofEpochMilli(t.getCreatedOn().getTime())
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                ));
        List<Map<String, Object>> transactionList = new ArrayList<>();
        transData.forEach((key, value) -> transactionList.add(new HashMap<>() {{
            put("dateofday", key);
            put("value", value);
        }}));

        transactionList.sort(mapComparator());
        Map<String, Object> transactionSummary = new LinkedHashMap<>() {{
            put("transaction", transactionsMap);
            put("transactionByAccounts", transactionsByAccountType);
            put("payment", groupPaymentMap);
            put("wallet", walletTransactions);
            put("withdrawal", groupWithdrawalsMap);
        }};
        Map<String, Object> transactionData = new LinkedHashMap<>() {{
            put("transactionData", transactionList);
        }};
        Map<String, Object> transactionDataCount = new LinkedHashMap<>() {{
            put("numberofrecords", transactionData.size());
        }};

        List<Map<String, Object>> response = new ArrayList<>();
        response.add(transactionSummary);
        response.add(transactionData);
        response.add(transactionDataCount);

        return response;
    }


    private UniversalResponse getWithdrawalLogs(Date startDate, Date endDate, String period, String group, Pageable pageable) {
        List<WithdrawalLogs> withdrawalLogs;
        int numOfRecords = 0;
        if (group.equals("all")) {
            withdrawalLogs = withdrawallogsRepo.findAllByCreatedOnBetweenAndSoftDelete(startDate, endDate, false, pageable);
            numOfRecords = withdrawallogsRepo.countAllByCreatedOnBetweenAndSoftDeleteFalse(startDate, endDate);
        } else {
            GroupWrapper groups = chamaKycService.findMonoGroupByActiveAndSoftDeleteAndNameLike(true, false, group);
            if (groups != null) {
                withdrawalLogs = withdrawallogsRepo.findAllByCreatedOnBetweenAndSoftDelete(startDate, endDate, false, pageable).stream().filter(log -> groupFilterByGroupNameParamId.test(groups.getId(), group)).collect(Collectors.toList());


            } else {
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"), new ArrayList<>());
            }
        }

        List<WithdrawLogsWrapper> withdrawLogsWrappers =
                withdrawalLogs.stream().map(
                                log -> WithdrawLogsWrapper.builder()
                                        .transactionId(log.getUniqueTransactionId())
                                        .contributionNarration(log.getContribution_narration())
                                        .debitAccountId(log.getDebitAccounts().getId())
                                        .contributionAccount(log.getContributions().getId())
                                        .isContribution(null == log.getContributions())
                                        .contributionName(null == log.getContributions() ? "" : log.getContributions().getName())
                                        .loan(log.getLoan())
                                        .creditUserNumber(log.getCreditphonenumber())
                                        .newBalance(log.getNewbalance())
                                        .oldBalance(log.getOldbalance())
                                        .capturedBy(log.getCapturedby())
                                        .reason(log.getWithdrawalreason())
                                        .transferToUserStatus(log.getTransferToUserStatus())
                                        .createdOn(log.getCreatedOn())
                                        .build())
                        .collect(Collectors.toList());

        Map<String, List<WithdrawLogsWrapper>> disbursedLoans = withdrawLogsWrappers.stream().collect(groupingBy((t -> t.getCreatedOn().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().with(timeAdjusters().get(period)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))));
        List<Map<String, Object>> responseData = new ArrayList<>();
        disbursedLoans.forEach((key, value) -> responseData.add(Map.of("dateofday", key, "objects", value)));
        responseData.sort(mapComparator());
        Map<String, Integer> noRecords = new HashMap<>();
        noRecords.put("numofrecords", numOfRecords);
        UniversalResponse response = new UniversalResponse("success", getResponseMessage("groupWithdrawalLogs"), responseData);
        response.setMetadata(noRecords);
        return response;
    }

    Function<TransactionsLog, TransactionLogsWrapper> mapTransactionLogToTransactionWrappper() {
        return (transaction) -> {
            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(transaction.getContributions().getMemberGroupId());
            if (groupWrapper == null) return null;
            return TransactionLogsWrapper.builder().id(transaction.getId()).updatedBalance(transaction.getNewbalance()).initialBalance(transaction.getOldbalance()).transactionAmount(transaction.getTransamount()).creditAccountId(transaction.getCreditaccounts().getId()).contributionNarration(transaction.getContributionNarration()).debitPhonenUmber(transaction.getDebitphonenumber()).capturedBy(transaction.getCapturedby()).contributionId(transaction.getContributions().getId()).contributionsName(transaction.getContributions().getName()).groupId(groupWrapper.getId()).groupName(groupWrapper.getName()).createdOn(transaction.getCreatedOn()).build();
        };
    }

    private UniversalResponse getTransactionsLogsByGroup(Date startDate, Date endDate, String period, String group, Pageable pageable) {
        List<TransactionsLog> transactionsLogList;
        if (group.equalsIgnoreCase("all")) {
            transactionsLogList = transactionlogsRepo.findAllByCreatedOnBetweenOrderByCreatedOnDesc(startDate, endDate, pageable);
        } else {
            GroupWrapper groups = chamaKycService.findMonoGroupByActiveAndSoftDeleteAndNameLike(true, false, group);
            if (groups != null) {
                transactionsLogList = transactionlogsRepo.findAllByCreatedOnBetweenOrderByCreatedOnDesc(startDate, endDate, pageable).stream().filter(log -> groupFilterByGroupNameParamId.test(groups.getId(), group)).collect(Collectors.toList());
            } else {
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"), new ArrayList<>());
            }
        }
        List<TransactionLogsWrapper> transactionLogsWrapperList = transactionsLogList.stream().map(mapTransactionLogToTransactionWrappper()).collect(Collectors.toList());

        Map<String, List<TransactionLogsWrapper>> transactionLogs = transactionLogsWrapperList.stream().collect(groupingBy((t -> t.getCreatedOn().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().with(timeAdjusters().get(period)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))));
        List<Map<String, Object>> responseData = new ArrayList<>();
        transactionLogs.forEach((key, value) -> responseData.add(new HashMap<>() {{
            put("dateofday", key);
            put("objects", value);
        }}));

        responseData.sort(mapComparator());
        Map<String, Object> responseMssg = new LinkedHashMap<>() {{
            put("data", responseData);
        }};
        return new UniversalResponse("success", getResponseMessage("transactionLogsByGroup"), responseMssg);
    }


    private UniversalResponse getApprovedLoanApplications(Date startDate, Date endDate, String period, String group, Pageable pageable) {
        List<LoanApplications> loanApplicationsList;
        if (group.equalsIgnoreCase("all")) {
            loanApplicationsList = loanapplicationsRepo.findAllByApprovedAndSoftDeleteAndCreatedOnBetweenOrderByCreatedOnDesc(true, false, startDate, endDate, pageable).getContent();
        } else {
            GroupWrapper groups = chamaKycService.findMonoGroupByActiveAndSoftDeleteAndNameLike(false, false, group);
            if (groups != null) {
                loanApplicationsList = loanapplicationsRepo.findAllByApprovedAndSoftDeleteAndCreatedOnBetweenOrderByCreatedOnDesc(true, false, startDate, endDate, pageable).getContent().stream().filter(loan -> loan.getLoanProducts().getGroupId() == groups.getId()).collect(Collectors.toList());
            } else {
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"), new ArrayList<>());
            }
        }
        List<GroupLoansApprovedWrapper> approvedWrappers = loanApplicationsList.stream().map(loan -> {
            GroupWrapper groupWrapper = chamaKycService.findMonoGroupByActiveAndSoftDeleteAndNameLike(false, false, group);
            MemberWrapper memberWrapper = chamaKycService.getMonoMemberDetailsById(loan.getMemberId());
            if (groupWrapper == null || memberWrapper == null) return null;
            return GroupLoansApprovedWrapper.builder().loanproductid(loan.getId()).loanapplicationid(loan.getLoanProducts().getId()).amount(loan.getAmount()).loanproductname(loan.getLoanProducts().getProductname()).appliedon(loan.getCreatedOn()).membername(String.format("%s %s", memberWrapper.getFirstname(), memberWrapper.getLastname())).memberphonenumber(memberWrapper.getPhonenumber()).unpaidloans(loan.getUnpaidloans()).isGuarantor(loan.getLoanProducts().isGuarantor()).approvedBy(loan.getApprovedby()).build();
        }).collect(Collectors.toList());

        Map<String, List<GroupLoansApprovedWrapper>> approvedLoansMap = approvedWrappers.stream().collect(groupingBy((t -> t.getAppliedon().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().with(timeAdjusters().get(period)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))));
        List<Map<String, Object>> responseData = new ArrayList<>();
        approvedLoansMap.forEach((key, value) -> responseData.add(new HashMap<>() {{
            put("dateofday", key);
            put("objects", value);
        }}));

        responseData.sort(mapComparator());
        Map<String, Object> responseMssg = new LinkedHashMap<>() {{
            put("data", responseData);
        }};
        return new UniversalResponse("success", getResponseMessage("approvedGroupLoans"), responseMssg);

    }


    private UniversalResponse getPendingLoanApplications(Date startDate, Date endDate, String period, String group, Pageable pageable) {
        List<LoanApplications> loanApplicationsList;
        if (group.equalsIgnoreCase("all")) {
            loanApplicationsList = loanapplicationsRepo.findAllByPendingAndSoftDeleteAndCreatedOnBetweenOrderByCreatedOnDesc(true, false, startDate, endDate);
        } else {
            GroupWrapper groupWrapper = chamaKycService.findMonoGroupByActiveAndSoftDeleteAndNameLike(false, false, group);
            if (groupWrapper != null) {
                loanApplicationsList = loanapplicationsRepo.findAllByPendingAndSoftDeleteAndCreatedOnBetweenOrderByCreatedOnDesc(true, false, startDate, endDate).stream().filter(loan -> groupFilterByGroupNameParamId.test(loan.getLoanProducts().getGroupId(), group)).collect(Collectors.toList());
            } else {
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"), new ArrayList<>());
            }
        }

        List<GroupLoansPendingApproval> pendingWrapper = loanApplicationsList.stream().map(loan -> {
            MemberWrapper memberWrapper = chamaKycService.getMonoMemberDetailsById(loan.getMemberId());
            if (memberWrapper == null) return null;
            return GroupLoansPendingApproval.builder().loanproductid(loan.getId()).loanapplicationid(loan.getLoanProducts().getId()).amount(loan.getAmount()).loanproductname(loan.getLoanProducts().getProductname()).appliedon(loan.getCreatedOn()).membername(String.format("%s %s", memberWrapper.getFirstname(), memberWrapper.getLastname())).memberphonenumber(memberWrapper.getPhonenumber()).unpaidloans(loan.getUnpaidloans()).isGuarantor(loan.getLoanProducts().isGuarantor()).build();
        }).collect(Collectors.toList());
        Map<String, List<GroupLoansPendingApproval>> loansPendingApproval = pendingWrapper.stream().collect(groupingBy((t -> t.getAppliedon().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().with(timeAdjusters().get(period)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))));
        List<Map<String, Object>> responseData = new ArrayList<>();
        loansPendingApproval.forEach((key, value) -> responseData.add(new HashMap<>() {{
            put("dateofday", key);
            put("objects", value);
        }}));

        responseData.sort(mapComparator());
        Map<String, Object> responseMssg = new LinkedHashMap<>() {{
            put("data", responseData);
        }};
        return new UniversalResponse("success", getResponseMessage("groupsLoanPendingApproval"), responseMssg);

    }


    private UniversalResponse getLoanPenalties(Date startDate, Date endDate, String period, String group, Pageable pageable) {
        List<LoanPenalty> loansPenaltyList = loanPenaltyRepository.findAllByCreatedOnBetweenAndSoftDeleteFalse(startDate, endDate, pageable);
        if (!group.equals("all")) {
            GroupWrapper groups = chamaKycService.findMonoGroupByActiveAndSoftDeleteAndNameLike(true, false, group);
            if (groups == null) {
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"), new ArrayList<>());
            } else {
                loansPenaltyList = loansPenaltyList.stream().filter(loan -> groupFilterByGroupNameParamId.test(loan.getLoansDisbursed().getGroupId(), group)).collect(Collectors.toList());
            }
        }
        List<LoanPenaltyReportWrapper> loanPenaltyWrapper = loansPenaltyList.stream().map(penalty -> {
            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(penalty.getLoansDisbursed().getGroupId());
            MemberWrapper memberWrapper = chamaKycService.getMonoMemberDetailsById(penalty.getMemberId());
            if (groupWrapper == null || memberWrapper == null) return null;
            return LoanPenaltyReportWrapper.builder().loanPenaltyId(penalty.getId()).penaltyAmount(penalty.getPenaltyAmount()).paymentStatus(penalty.getPaymentStatus()).paidAmount(penalty.getPaidAmount()).dueAmount(penalty.getDueAmount()).transactionId(penalty.getTransactionId()).loanDueDate(penalty.getLoanDueDate().toString()).lastPaymentDate(penalty.getLastPaymentDate()).groupName(groupWrapper.getName()).memberName(String.format(" %s %s", memberWrapper.getFirstname(), memberWrapper.getLastname())).memberPhoneNumber(memberWrapper.getPhonenumber()).createdOn(penalty.getCreatedOn()).build();
        }).collect(Collectors.toList());

        Map<String, List<LoanPenaltyReportWrapper>> penaltyData = loanPenaltyWrapper.stream().collect(groupingBy((t -> t.getCreatedOn().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().with(timeAdjusters().get(period)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))));

        List<Map<String, Object>> penaltyResponse = new ArrayList<>();

        penaltyData.forEach((key, value) -> penaltyResponse.add(new HashMap<>() {{
            put("dateofday", key);
            put("objects", value);
        }}));

        penaltyResponse.sort(mapComparator());
        return new UniversalResponse("success", getResponseMessage("penaltyReportsByGroup"), penaltyResponse);
    }

    private UniversalResponse getGroupOverdueLoans(Date startDate, Date endDate, String period, String group, String additional, Pageable pageable) {
        long loanProductId;
        try {
            loanProductId = Long.parseLong(additional);
        } catch (NumberFormatException ex) {
            return new UniversalResponse("fail", getResponseMessage("loanProductAdditionalParamRequirement"));
        }
        List<LoansDisbursed> loansDisbursedList;
        int recordsCount;
        if (group.equalsIgnoreCase("all")) {
            loansDisbursedList = loanProductId == 0 ? loansdisbursedRepo.findAllOverdue(startDate, endDate, pageable) : loansdisbursedRepo.findAllOverdueByLoanProductId(loanProductId, startDate, endDate, pageable);
            recordsCount = loanProductId == 0 ? loansdisbursedRepo.countAllOverdue(startDate, endDate) : loansdisbursedRepo.countAllOverdueByLoanProductId(loanProductId, startDate, endDate);
        } else {
            GroupWrapper groups = chamaKycService.findMonoGroupByActiveAndSoftDeleteAndNameLike(true, false, group);
            if (groups != null) {
                loansDisbursedList = loanProductId == 0 ? loansdisbursedRepo.findAllOverdueByGroup(groups.getId(), startDate, endDate, pageable) : loansdisbursedRepo.findAllOverdueByLoanProductIdAndGroup(loanProductId, groups.getId(), startDate, endDate, pageable);
                recordsCount = loanProductId == 0 ? loansdisbursedRepo.countAllOverdueByGroup(groups.getId(), startDate, endDate) : loansdisbursedRepo.countAllOverdueByLoanProductIdAndGroup(loanProductId, groups.getId(), startDate, endDate);
            } else {
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"), new ArrayList<>());
            }
        }
        List<LoansDisbursedWrapper> loansDisbursedWrapperList = loansDisbursedList.stream().map(disbursed -> {
            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(disbursed.getGroupId());
            MemberWrapper memberWrapper = chamaKycService.getMonoMemberDetailsById(disbursed.getMemberId());
            if (groupWrapper == null || memberWrapper == null) return null;
            return LoansDisbursedWrapper.builder().accountTypeId(disbursed.getId()).appliedOn(disbursed.getCreatedOn()).contributionId(disbursed.getLoanApplications().getLoanProducts().getContributions().getId()).contributionName(disbursed.getLoanApplications().getLoanProducts().getContributions().getName()).dueAmount(disbursed.getDueamount()).principal(disbursed.getPrincipal()).dueDate(disbursed.getDuedate()).interest(disbursed.getInterest()).groupId(groupWrapper.getId()).groupName(groupWrapper.getName()).interest(disbursed.getInterest()).isGuarantor(disbursed.getLoanApplications().getLoanProducts().isGuarantor() ? "true" : "false").recipient(String.format("%s  %s", memberWrapper.getFirstname(), memberWrapper.getLastname())).recipientNumber(memberWrapper.getPhonenumber()).approvedBy(disbursed.getLoanApplications().getApprovedby()).build();
        }).collect(Collectors.toList());

        Map<String, List<LoansDisbursedWrapper>> disbursedLoans = loansDisbursedWrapperList.stream().collect(groupingBy((t -> t.getAppliedOn().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().with(timeAdjusters().get(period)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))));
        List<Map<String, Object>> responseData = new ArrayList<>();
        disbursedLoans.forEach((key, value) -> responseData.add(new HashMap<>() {{
            put("dateofday", key);
            put("objects", value);
        }}));

        responseData.sort(mapComparator());
        Map<String, Integer> noRecords = new HashMap<>();
        noRecords.put("numofrecords", recordsCount);
        UniversalResponse response = new UniversalResponse("success", getResponseMessage("groupOverdueLoans"), responseData);
        response.setMetadata(noRecords);
        return response;
    }

    Function<LoansRepayment, LoanRepaymentWrapper> mapLoansRepaymentToLoanRepaymentWrapper() {
        return (loansRepayment) -> {
            MemberWrapper member = chamaKycService.getMonoMemberDetailsById(loansRepayment.getMemberId());
            GroupWrapper group = chamaKycService.getMonoGroupById(loansRepayment.getLoansDisbursed().getGroupId());
            if (member == null || group == null) return null;
            return LoanRepaymentWrapper.builder().id(loansRepayment.getId()).memberId(member.getId()).memberName(String.format("%s  %s", member.getFirstname(), member.getLastname())).initialLoan(loansRepayment.getOldamount()).balance(loansRepayment.getNewamount()).paidAmount(loansRepayment.getAmount()).receiptNumber(loansRepayment.getReceiptnumber()).groupId(group.getId()).groupName(group.getName()).paymentType(loansRepayment.getPaymentType()).createdDate(loansRepayment.getCreatedOn()).build();
        };
    }

    private UniversalResponse getLoanRepaymentsByGroupAndProductId(Date startDate, Date endDate, String period, String group, String additional, Pageable pageable) {
        List<LoansRepayment> loansRepaymentList;
        long productId;
        long recordCount;
        try {
            productId = Long.parseLong(additional);
        } catch (Exception ex) {
            return new UniversalResponse("fail", getResponseMessage("loanIdMustBeANumber"));
        }
        if (group.equalsIgnoreCase("all")) {
            loansRepaymentList = productId == 0 ? loansrepaymentRepo.findAllByCreatedOnBetweenAndSoftDeleteFalseOrderByCreatedOnDesc(startDate, endDate, pageable) : loansrepaymentRepo.findAllByCreatedOnBetweenAndSoftDeleteFalseOrderByCreatedOnDesc(startDate, endDate, pageable).stream().filter(loansRepayment -> loansRepayment.getLoansDisbursed().getLoanApplications().getLoanProducts().getId() == productId).collect(Collectors.toList());
            recordCount = loansrepaymentRepo.countAllByCreatedOnBetweenAndSoftDeleteFalse(startDate, endDate);
        } else {
            GroupWrapper groupWrapper = chamaKycService.findMonoGroupByActiveAndSoftDeleteAndNameLike(true, false, group);
            if (groupWrapper != null) {
                loansRepaymentList = loansrepaymentRepo.findAllByCreatedOnBetweenAndSoftDeleteFalseOrderByCreatedOnDesc(startDate, endDate, pageable).stream().filter(loan -> groupFilterByGroupNameParamId.test(loan.getLoansDisbursed().getGroupId(), group)).filter(loan -> productId == 0 || loan.getLoansDisbursed().getLoanApplications().getLoanProducts().getId() == productId).collect(Collectors.toList());
                recordCount = loansrepaymentRepo.countloanpaymentsbyGroupid(groupWrapper.getId());

            } else {
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"), new ArrayList<>());
            }
        }
        List<LoanRepaymentWrapper> loanRepaymentWrappersList = loansRepaymentList.stream().map(mapLoansRepaymentToLoanRepaymentWrapper()).collect(Collectors.toList());

        Map<String, List<LoanRepaymentWrapper>> disbursedLoans = loanRepaymentWrappersList.stream().collect(groupingBy((t -> t.getCreatedDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().with(timeAdjusters().get(period)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))));
        List<Map<String, Object>> responseData = new ArrayList<>();
        disbursedLoans.forEach((key, value) -> responseData.add(new HashMap<>() {{
            put("dateofday", key);
            put("objects", value);
        }}));
        responseData.sort(mapComparator());
        Map<String, Object> numofrecords = new HashMap<>();
        numofrecords.put("numofrecords", recordCount);
        UniversalResponse response = new UniversalResponse("success", getResponseMessage("loanRepaymentByGroupAndProductId"), responseData);
        response.setMetadata(numofrecords);
        return response;
    }

    private double loanLimit(LoanProducts loanProducts) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        try {
            int limit = loanService.checkLoanLimit(auth.getName(), loanProducts.getContributions().getId());
            if (!loanProducts.isGuarantor()) {
                limit = (loanProducts.getUserSavingValue() * limit) / 100;
            }
            return limit;
        } catch (Exception exception) {
            return 0.0;
        }
    }

    private UniversalResponse getLoanProductsByGroup(Date startDate, Date endDate, String period, String group, String additional, Pageable pageable) {
        List<LoanProducts> loanProductsList = new ArrayList<>();
        int recordCount = 0;
        if (group.equalsIgnoreCase("all")) {
            switch (additional) {
                case "active":
                    loanProductsList = loanproductsRepository.findAllByIsActiveAndSoftDeleteAndCreatedOnBetween(true, false, startDate, endDate, pageable);
                    recordCount = loanproductsRepository.countLoanProductsByIsActiveAndSoftDeleteAndCreatedOnBetween(true, false, startDate, endDate);
                    break;
                case "inactive":
                    loanProductsList = loanproductsRepository.findAllByIsActiveAndSoftDeleteAndCreatedOnBetween(false, false, startDate, endDate, pageable);
                    recordCount = loanproductsRepository.countLoanProductsByIsActiveAndSoftDeleteAndCreatedOnBetween(false, false, startDate, endDate);
                    break;
                case "all":
                    loanProductsList = loanproductsRepository.findAllBySoftDeleteAndCreatedOnBetween(false, startDate, endDate, pageable);
                    recordCount = loanproductsRepository.countAllBySoftDeleteAndCreatedOnBetween(false, startDate, endDate);
                    break;
            }
        } else {
            GroupWrapper groupWrapper = chamaKycService.findMonoGroupByActiveAndSoftDeleteAndNameLike(true, false, group);
            if (groupWrapper != null) {
                switch (additional) {
                    case "active":
                        loanProductsList = loanproductsRepository.findAllByGroupIdAndIsActiveAndCreatedOnBetween(groupWrapper.getId(), true, startDate, endDate, pageable);
                        recordCount = loanproductsRepository.countByGroupIdAndIsActiveAndCreatedOnBetween(groupWrapper.getId(), true, startDate, endDate);
                        break;
                    case "inactive":
                        loanProductsList = loanproductsRepository.findAllByGroupIdAndIsActiveAndCreatedOnBetween(groupWrapper.getId(), false, startDate, endDate, pageable);
                        recordCount = loanproductsRepository.countByGroupIdAndIsActiveAndCreatedOnBetween(groupWrapper.getId(), true, startDate, endDate);
                        break;
                    case "all":
                        loanProductsList = loanproductsRepository.findAllByGroupIdAndSoftDelete(groupWrapper.getId(), false, pageable);
                        recordCount = loanproductsRepository.countAllByGroupIdAndSoftDelete(groupWrapper.getId(), false);
                        break;
                }
            } else {
                return new UniversalResponse("fail", String.format("Group search with name %s failed", group), new ArrayList<>());
            }
        }
        List<LoanProductsWrapperReport> loanproductWrapperList = loanProductsList.stream().map(product -> {
            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(product.getGroupId());
            if (groupWrapper == null) return null;
            return LoanProductsWrapperReport.builder().productid(product.getId()).productname(product.getProductname()).description(product.getDescription()).max_principal(product.getMax_principal()).min_principal(product.getMin_principal()).interesttype(product.getInteresttype()).interestvalue(product.getInterestvalue()).paymentperiod(product.getPaymentperiod()).paymentperiodtype(product.getPaymentperiodtype()).contributionid(product.getContributions().getId()).contributionname(product.getContributions().getName()).contributionbalance(product.getContributions().getContributionAmount()).groupid(groupWrapper.getId()).groupname(groupWrapper.getName()).isguarantor(product.isGuarantor()).ispenalty(product.isPenalty()).ispenaltypercentage(product.getIsPercentagePercentage()).usersavingvalue(product.getUserSavingValue()).userLoanLimit(loanLimit(product)).debitAccountId(product.getDebitAccountId().getId()).isActive(product.getIsActive()).penaltyPeriod(product.getPenaltyPeriod()).createdOn(product.getCreatedOn()).build();
        }).collect(Collectors.toList());

        Map<String, List<LoanProductsWrapperReport>> approvedLoansMap = loanproductWrapperList.stream().collect(groupingBy((t -> t.getCreatedOn().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().with(timeAdjusters().get(period)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))));
        List<Map<String, Object>> responseData = new ArrayList<>();
        approvedLoansMap.forEach((key, value) -> responseData.add(new HashMap<>() {{
            put("dateofday", key);
            put("objects", value);
        }}));
        responseData.sort(mapComparator());
        Map<String, Object> numOfRecords = new HashMap<>();
        numOfRecords.put("numofrecords", recordCount);
        UniversalResponse response = new UniversalResponse("success", String.format(getResponseMessage("loanProductsWithAdditional"), additional), responseData);
        response.setMetadata(numOfRecords);
        return response;
    }

    Function<LoanApplications, GroupLoansPendingApproval> mapLoanApplicationsToWrapper() {
        return loan -> {
            MemberWrapper member = chamaKycService.getMonoMemberDetailsById(loan.getMemberId());
            if (member == null) return null;
            return GroupLoansPendingApproval.builder().loanproductid(loan.getId()).loanapplicationid(loan.getLoanProducts().getId()).amount(loan.getAmount()).loanproductname(loan.getLoanProducts().getProductname()).appliedon(loan.getCreatedOn()).membername(String.format("%s %s", member.getFirstname(), member.getLastname())).memberphonenumber(member.getPhonenumber()).unpaidloans(loan.getUnpaidloans()).isGuarantor(loan.getLoanProducts().isGuarantor()).build();
        };
    }

    private UniversalResponse getLoansPendingApprovalbyLoanProduct(Date startDate, Date endDate, String period, String group, String additional, Pageable pageable) {
        long loanProductId;
        int recordCount = 0;
        try {
            loanProductId = Long.parseLong(additional);
        } catch (NumberFormatException ex) {
            return new UniversalResponse("fail", "Additional  param value must be a loan product id");
        }
        LoanProducts loanProducts = loanproductsRepository.findById(loanProductId).orElse(null);
        if (loanProducts == null) return new UniversalResponse("fail", getResponseMessage("loanProductNotFound"));
        List<LoanApplications> loansApplicationList;
        if (group.equalsIgnoreCase("all")) {
            loansApplicationList = loanapplicationsRepo.findAllByLoanProductsAndCreatedOnBetweenAndApprovedAndSoftDeleteOrderByCreatedOnDesc(loanProducts, startDate, endDate, false, false, pageable);
        } else {
            GroupWrapper groups = chamaKycService.findMonoGroupByActiveAndSoftDeleteAndNameLike(true, false, group);
            if (groups == null) {
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"), new ArrayList<>());
            } else {
                loansApplicationList = loanapplicationsRepo.findAllByLoanProductsAndCreatedOnBetweenAndApprovedAndSoftDeleteOrderByCreatedOnDesc(loanProducts, startDate, endDate, false, false, pageable).stream().filter(application -> groupFilterByGroupNameParamId.test(application.getLoanProducts().getGroupId(), group)).collect(Collectors.toList());
                recordCount = loanapplicationsRepo.countByLoanProductsAndApproved(loanProductId, groups.getId(), startDate, endDate);
            }
        }
        List<GroupLoansPendingApproval> pendingWrapper = loansApplicationList.stream().map(mapLoanApplicationsToWrapper()).collect(Collectors.toList());

        Map<String, List<GroupLoansPendingApproval>> loansPendingApproval = pendingWrapper.stream().collect(groupingBy((t -> t.getAppliedon().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().with(timeAdjusters().get(period)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))));
        List<Map<String, Object>> responseData = new ArrayList<>();
        loansPendingApproval.forEach((key, value) -> responseData.add(new HashMap<>() {{
            put("dateofday", key);
            put("objects", value);
        }}));
        responseData.sort(mapComparator());

        Map<String, Integer> numrecords = new HashMap<>();
        numrecords.put("numofrecords", recordCount);

        UniversalResponse response = new UniversalResponse("success", getResponseMessage("groupsLoanPendingApproval"), responseData);
        response.setMetadata(numrecords);
        return response;
    }


    Function<LoansDisbursed, LoansDisbursedWrapper> mapLoansDisbursedToWrapper() {
        return disbursed -> {
            String groupName = chamaKycService.getMonoGroupNameByGroupId(disbursed.getGroupId());
            MemberWrapper member = chamaKycService.getMonoMemberDetailsById(disbursed.getMemberId());
            if (groupName.isEmpty() || member == null) return null;
            return LoansDisbursedWrapper.builder().accountTypeId(disbursed.getId()).appliedOn(disbursed.getCreatedOn()).contributionId(disbursed.getLoanApplications().getLoanProducts().getContributions().getId()).contributionName(disbursed.getLoanApplications().getLoanProducts().getContributions().getName()).dueAmount(disbursed.getDueamount()).principal(disbursed.getPrincipal()).dueDate(disbursed.getDuedate()).interest(disbursed.getInterest()).groupId(disbursed.getGroupId()).groupName(groupName).interest(disbursed.getInterest()).isGuarantor(disbursed.getLoanApplications().getLoanProducts().isGuarantor() ? "true" : "false").recipient(String.format("%s  %s", member.getFirstname(), member.getLastname())).recipientNumber(member.getPhonenumber()).approvedBy(disbursed.getLoanApplications().getApprovedby()).build();
        };
    }

    private UniversalResponse getDisbursedLoansPerProduct(Date startDate, Date endDate, String period, String group, String additional, Pageable pageable) {
        long loanProductId;
        try {
            loanProductId = Long.parseLong(additional);
        } catch (NumberFormatException ex) {
            return new UniversalResponse("fail", getResponseMessage("loanProductAdditionalParamRequirement"));
        }
        List<LoansDisbursed> loansDisbursedList;
        int recordsCount;
        if (group.equalsIgnoreCase("all")) {
            loansDisbursedList = loansdisbursedRepo.findAllByLoanProductId(loanProductId, startDate, endDate, pageable);
            recordsCount = loansdisbursedRepo.countAllLoansDisbursedbyLoanproductAndGroup(loanProductId, startDate, endDate);
        } else {
            GroupWrapper groups = chamaKycService.findMonoGroupByActiveAndSoftDeleteAndNameLike(true, false, group);
            if (groups != null) {
                loansDisbursedList = loansdisbursedRepo.findByLoanProductAndGroup(loanProductId, groups.getId(), startDate, endDate, pageable);
                recordsCount = loansdisbursedRepo.countLoansDisbursedbyLoanproductAndGroup(loanProductId, groups.getId(), startDate, endDate);
            } else {
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"), new ArrayList<>());
            }
        }
        List<LoansDisbursedWrapper> loansDisbursedWrapperList = loansDisbursedList.stream().map(mapLoansDisbursedToWrapper()).collect(Collectors.toList());

        Map<String, List<LoansDisbursedWrapper>> disbursedLoans = loansDisbursedWrapperList.stream().collect(groupingBy((t -> t.getAppliedOn().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().with(timeAdjusters().get(period)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))));
        List<Map<String, Object>> responseData = new ArrayList<>();
        disbursedLoans.forEach((key, value) -> responseData.add(new HashMap<>() {{
            put("dateofday", key);
            put("objects", value);
        }}));

        responseData.sort(mapComparator());
        Map<String, Integer> noRecords = new HashMap<>();
        noRecords.put("numofrecords", recordsCount);
        UniversalResponse response = new UniversalResponse("success", getResponseMessage("disbursedLoanProductsById"), responseData);
        response.setMetadata(noRecords);
        return response;
    }

    private UniversalResponse getLoanDisbursed(Date startDate, Date endDate, String period, String group, Pageable pageable) {
        List<LoansDisbursed> loansDisbursedList;
        int recordsCount;
        if (group.equalsIgnoreCase("all")) {
            loansDisbursedList = loansdisbursedRepo.findAllByCreatedOnBetweenOrderByCreatedOnDesc(startDate, endDate, pageable);
            recordsCount = loansdisbursedRepo.countAllByCreatedOnBetween(startDate, endDate);
        } else {
            GroupWrapper groups = chamaKycService.findMonoGroupByActiveAndSoftDeleteAndNameLike(true, false, group);
            if (groups != null) {
                loansDisbursedList = loansdisbursedRepo.findAllByGroupIdAndCreatedOnBetweenOrderByCreatedOnDesc(groups.getId(), startDate, endDate, pageable);
                recordsCount = loansdisbursedRepo.countAllByGroupIdAndCreatedOnBetween(groups.getId(), startDate, endDate);
            } else {
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"), new ArrayList<>());
            }
        }
        List<LoansDisbursedWrapper> loansDisbursedWrapperList = loansDisbursedList.stream().map(mapLoansDisbursedToWrapper()).sorted().collect(Collectors.toList());

        Map<String, List<LoansDisbursedWrapper>> disbursedLoans = loansDisbursedWrapperList.stream().collect(groupingBy((t -> t.getAppliedOn().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().with(timeAdjusters().get(period)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))));
        List<Map<String, Object>> responseData = new ArrayList<>();
        disbursedLoans.forEach((key, value) -> responseData.add(new HashMap<>() {{
            put("dateofday", key);
            put("objects", value);
        }}));
        responseData.sort(mapComparator());
        Map<String, Integer> noRecords = new HashMap<>();
        noRecords.put("numofrecords", recordsCount);
        UniversalResponse response = new UniversalResponse("success", getResponseMessage("activeLoanProducts"), responseData);
        response.setMetadata(noRecords);
        return response;
    }

    private UniversalResponse getContributionSchedulePayment(Date startDate, Date endDate, String period, String group, Pageable pageable) {
        List<ContributionSchedulePayment> contributionSchedulePaymentList;
        if (group.equalsIgnoreCase("all")) {
            contributionSchedulePaymentList = contributionSchedulePaymentRepository.findAllByCreatedOnBetweenAndSoftDelete(startDate, endDate, false, pageable);
        } else {
            GroupWrapper groups = chamaKycService.findMonoGroupByActiveAndSoftDeleteAndNameLike(false, false, group);
            if (groups != null) {
                contributionSchedulePaymentList = contributionSchedulePaymentRepository.findAllByCreatedOnBetweenAndSoftDelete(startDate, endDate, false, pageable).stream().filter(cont -> {
                    Contributions contributions = contributionsRepository.findById(cont.getContributionId()).orElse(null);
                    return (contributions != null && contributions.getMemberGroupId() == groups.getId());
                }).collect(Collectors.toList());
            } else {
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"), new ArrayList<>());
            }
        }

        List<ContributionSchedulePaymentWrapper> contributionSchedulePaymentWrapperList = contributionSchedulePaymentList.stream().map(cont -> {
            Contributions contributions = contributionsRepository.findById(cont.getContributionId()).orElse(new Contributions());
            String groupName = chamaKycService.getMonoGroupNameByGroupId(contributions.getMemberGroupId());
            if (groupName.isEmpty()) return null;
            return ContributionSchedulePaymentWrapper.builder().schedulePaymentId(cont.getId()).groupName(groupName).contributionPaymentId(cont.getContributionId()).contributionName(contributions.getName()).contributionStartDate(contributions.getStartDate()).contributionType(contributions.getContributionType().getName()).scheduleType(contributions.getScheduleType().getName()).expectedContributionDate(cont.getExpectedContributionDate()).createdOn(cont.getCreatedOn()).scheduledId(cont.getContributionScheduledId()).build();
        }).collect(Collectors.toList());

        Map<String, List<ContributionSchedulePaymentWrapper>> paymentWrapperMap = contributionSchedulePaymentWrapperList.stream().collect(groupingBy((t -> t.getCreatedOn().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().with(timeAdjusters().get(period)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))));
        List<Map<String, Object>> responseData = new ArrayList<>();
        paymentWrapperMap.forEach((key, value) -> responseData.add(new HashMap<>() {{
            put("dateofday", key);
            put("objects", value);
        }}));

        responseData.sort(mapComparator());
        Map<String, Object> responseMssg = new LinkedHashMap<String, Object>() {{
            put("data", responseData);
        }};
        return new UniversalResponse("success", getResponseMessage("contributionsSchedulePayment"), responseMssg);
    }

    private UniversalResponse getContributionPaymentReport(Date startDate, Date endDate, String period, String group, Pageable pageable) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy");
        String startDateFormat = simpleDateFormat.format(startDate);
        String endDateFormat = simpleDateFormat.format(endDate);
        List<ContributionPayment> contributionPaymentList;
        if (group.equalsIgnoreCase("all")) {
            contributionPaymentList = contributionsPaymentRepository.findAllPaymentsByGroup(startDateFormat, endDateFormat, pageable);
        } else {
            GroupWrapper groups = chamaKycService.findMonoGroupByActiveAndSoftDeleteAndNameLike(false, false, group);
            if (groups != null) {
                contributionPaymentList = contributionsPaymentRepository.findAllPaymentsByGroupName(group, startDateFormat, endDateFormat, pageable).stream()
                        .filter(cont -> {
                            Contributions contributions = contributionsRepository.findById(cont.getContributionId()).orElse(null);
                            return (contributions != null && contributions.getMemberGroupId() == groups.getId());
                        }).collect(Collectors.toList());
            } else {
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"), new ArrayList<>());
            }
        }
        List<ContributionPaymentWrapper> paymentWrapperList = contributionPaymentList.stream().map(cont -> {
            Contributions contributions = contributionsRepository.findById(cont.getContributionId()).orElse(new Contributions());
            String groupName = chamaKycService.getMonoMemberGroupNameById(contributions.getMemberGroupId());

            if (groupName == null) return null;
            return ContributionPaymentWrapper.builder().contributionPaymentId(cont.getContributionId()).contributionName(contributions.getName()).groupName(groupName).contributionType(contributions.getContributionType().getName()).scheduleType(contributions.getScheduleType().getName()).transactionId(cont.getTransactionId()).paymentStatus(cont.getPaymentStatus()).amount(cont.getAmount()).phoneNumber(String.valueOf(cont.getPhoneNumber())).createdOn(cont.getCreatedOn()).paymentFailureReason(cont.getPaymentFailureReason()).paymentType(cont.getPaymentType()).isPenalty(cont.getIsPenalty()).isCombinedPayment(cont.getIsCombinedPayment() != null ? cont.getIsCombinedPayment() : false).build();
        }).collect(Collectors.toList());

        Map<String, List<ContributionPaymentWrapper>> paymentWrapperMap = paymentWrapperList.stream().collect(groupingBy((t -> t.getCreatedOn().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().with(timeAdjusters().get(period)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))));
        List<Map<String, Object>> responseData = new ArrayList<>();
        paymentWrapperMap.forEach((key, value) -> responseData.add(new HashMap<>() {{
            put("dateofday", key);
            put("objects", value);
        }}));
        responseData.sort(mapComparator());
        Map<String, Object> responseMssg = new LinkedHashMap<>() {{
            put("data", responseData);
        }};
        return new UniversalResponse("success", getResponseMessage("contributionsPayment"), responseMssg);
    }


    @Override
    public UniversalResponse getContributionTypes() {
        return new UniversalResponse("success", getResponseMessage("contributionTypes"), getContributiontypes());
    }


    @Override
    public Mono<UniversalResponse> checkLoanLimit(String phoneNumber, long groupId, Long contributionId, Long productId) {
        log.info("group id {} and member phone {} and status {} and payment type {}", groupId, phoneNumber, PaymentEnum.PAYMENT_SUCCESS.name(), TypeOfContribution.saving.name());
        return Mono.fromCallable(() -> {

            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(groupId);

            if (groupWrapper == null) {
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"));
            }
            Optional<Contributions> contributions = contributionsRepository.findByGroupIdAndActiveTrueAndSoftDeleteFalse(groupId);

            if (contributions.isEmpty()) {
                return new UniversalResponse("fail", getResponseMessage("contributionNotFound"));
            }

            Optional<LoanProducts> loanProductsOptional = loanproductsRepository.findById(productId);

            if (loanProductsOptional.isEmpty())
                return new UniversalResponse("fail", getResponseMessage("loanProductNotFound"));

            MemberWrapper member = chamaKycService.searchMonoMemberByPhoneNumber(phoneNumber);

            if (member == null)
                return new UniversalResponse("fail", getResponseMessage("memberNotFound"));

            GroupMemberWrapper groupMemberWrapper = chamaKycService.memberIsPartOfGroup(groupId, phoneNumber);

            if (groupMemberWrapper == null)
                return new UniversalResponse("fail", getResponseMessage("memberIsNotPartOfGroup"));

            List<ContributionPayment> userContributions = contributionsPaymentRepository.findAllByGroupIdAndPhoneNumberAndPaymentStatusAndPaymentTypeAndSoftDeleteFalse(groupId, phoneNumber, PaymentEnum.PAYMENT_SUCCESS.name(), TypeOfContribution.saving.name());

            int totalContributions = userContributions
                    .parallelStream()
                    .mapToInt(ContributionPayment::getAmount)
                    .sum();

            List<LoansDisbursed> loansDisbursedList = loansdisbursedRepo.findUserPendingLoans(groupId, member.getId());
            double pendingLoans = loansDisbursedList
                    .parallelStream()
                    .mapToDouble(LoansDisbursed::getDueamount)
                    .sum();

            double loanLimit = (double) (totalContributions) * 3;
            String formattedLoanLimit = String.format("%.2f", loanLimit);
            String formattedPendingLoans = String.format("%.2f", pendingLoans);
            Double availableLimit = loanLimit - pendingLoans;
            String formattedAvailable = String.format("%.2f", availableLimit);

            return new UniversalResponse("success", "user loan limit", Map.of("loan_limit", formattedLoanLimit, "pending_loan", formattedPendingLoans, "available_limit", formattedAvailable));
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> userWalletBalance() {
        return CustomAuthenticationUtil.getUsername().flatMap(esbService::balanceInquiry);
    }

    @Override
    public Mono<UniversalResponse> groupAccountBalance(Long groupId) {
        return Mono.fromCallable(() -> {

            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(groupId);

            if (groupWrapper == null) {
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"));
            }

            Accounts account = accountsRepository.findByGroupIdAndActiveTrueAndSoftDeleteFalse(groupId);

            if (account == null) {
                return new UniversalResponse("fail", getResponseMessage("groupAccountNotFound"));
            }


            if (account.getBalanceRequestDate() == null) {
                account.setBalanceRequestDate(new Date());
                accountsRepository.save(account);
            }
            double actualBalance = formatAmount(account.getAccountbalance());
            double availableBalance = formatAmount(account.getAvailableBal());
            double balance = actualBalance - availableBalance;

            Instant instant = account.getBalanceRequestDate().toInstant();

            LocalDateTime localDateTimeBefore = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();

            ZonedDateTime lastCheck = localDateTimeBefore.atZone(ZoneId.of("Africa/Nairobi"));

            LocalDateTime currentTime = LocalDateTime.now();
            ZonedDateTime currentCheck = currentTime.atZone(ZoneId.of("Africa/Nairobi"));

            long time_difference = ChronoUnit.SECONDS.between(lastCheck, currentCheck);

            if (time_difference > 5) {
                groupAccountBalanceInquiry(groupWrapper, account);
            }

            Map<String, Object> metadata = Map.of(
                    "groupId", groupWrapper.getId(),
                    "groupName", groupWrapper.getName(),
                    "availableBal", availableBalance,
                    "actualBal", actualBalance,
                    "balance", balance);
            auditTrail("Balance Inquiry", "Group Balance Inquiry for group " + groupWrapper.getName(), account.getAccountdetails());

            return new UniversalResponse("success", getResponseMessage("groupAccountTotals"), metadata);
        }).publishOn(Schedulers.boundedElastic());
    }

    private void auditTrail(String action, String description, String username) {
        AuditTrail trail = AuditTrail.builder()
                .action(action)
                .description(description)
                .capturedBy(username).build();
        auditTrailRepository.save(trail);

    }

    private String groupBalanceInquiry(GroupWrapper groupWrapper) {
        String transactionId = RandomStringUtils.randomNumeric(12);
        Map<String, String> balanceInquiryReq = getBalanceInquiryReq(
                groupWrapper.getCsbAccount(), "0", transactionId, transactionId);
        String balanceInquiryBody = gson.toJson(balanceInquiryReq);
        log.info("CHANNEL API GROUP ACCOUNT BALANCE INQUIRY REQUEST {}", balanceInquiryBody);

        return postBankWebClient.post()
                .uri("http://192.168.20.18:8092/ChannelAPI")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(balanceInquiryBody)
                .retrieve()
                .bodyToMono(String.class)
                .subscribeOn(Schedulers.boundedElastic())
                .block();
    }


    @Override
    public Mono<UniversalResponse> shareOutsAccountBalance(Long groupId) {
        return Mono.fromCallable(() -> {
            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(groupId);

            if (groupWrapper == null)
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"));

            Accounts account = accountsRepository.findByGroupIdAndActiveTrueAndSoftDeleteFalse(groupId);

            if (account == null)

                return new UniversalResponse("fail", getResponseMessage("groupAccountNotFound"));

            groupAccountBalanceInquiry(groupWrapper, account);

            List<ShareOutsPayment> paymentList = shareOutsPaymentRepository.findAllByGroupIdAndPaymentStatusAndSoftDeleteFalse(groupId, PaymentEnum.PAYMENT_SUCCESS.name());

            double contributions = paymentList.stream().mapToDouble(ShareOutsPayment::getTotalContribution).sum();
            contributions = formatAmount(contributions);

            List<Fines> finesList = finesRepository.findFinesByGroupIdAndPaymentStatusAndSoftDeleteFalseOrderByIdDesc(groupId, PaymentEnum.PAID.name());
            List<LoansDisbursed> loansDisbursedList = loansdisbursedRepo.findAllByGroupIdAndStatusAndSoftDeleteFalseOrderByIdAsc(groupId, PaymentEnum.FULLY_PAID.name());

            double fineInterest = finesList.stream().mapToDouble(Fines::getFineAmount).sum();
            double loanInterest = loansDisbursedList.stream().mapToDouble(LoansDisbursed::getInterest).sum();

            fineInterest = formatAmount(fineInterest);
            loanInterest = formatAmount(loanInterest);

            double totalInterest = loanInterest + fineInterest;

            double welfareBalance = calculateBalance(groupId, "welfare");
            double projectBalance = calculateBalance(groupId, "project");

            //TODO:: HANDLE DEDUCTIONS
            double welfareDeducted = calculateTransferBalance(groupId, "welfare");
            double projectDeducted = calculateTransferBalance(groupId, "project");


            welfareBalance = welfareBalance - welfareDeducted;
            projectBalance = projectBalance - projectDeducted;
            //TODO:: HANDLE THE TRAILING ZERO FORMATTING
            welfareBalance = formatAmount(welfareBalance);
            projectBalance = formatAmount(projectBalance);

            double retainingAmount = welfareBalance + projectBalance;
            Accounts account1 = accountsRepository.findByGroupIdAndActiveTrueAndSoftDeleteFalse(groupId);

            double actualAmount = formatAmount(account1.getAvailableBal());
            double availableAmount = formatAmount(account1.getAccountbalance());

            Map<String, Object> metadata = Map.of(
                    "groupId", groupWrapper.getId(),
                    "groupName", groupWrapper.getName(),
                    "availableBal", availableAmount,
                    "actualBal", actualAmount,
                    "totalInterest", totalInterest,
                    "shareOutsBalance", contributions,
                    "retainingBalance", retainingAmount);
            String response = String.format(getResponseMessage("groupShareOutTotals"), groupWrapper.getName());
            return new UniversalResponse("success", response, metadata);

        }).publishOn(Schedulers.boundedElastic());
    }


    @Override
    public Mono<UniversalResponse> addContribution(ContributionDetailsWrapper wrapper) {
        return Mono.fromCallable(() -> {
            Optional<ContributionType> contributionType = contributionTypesRepository.findById(wrapper.getContributiontypeid());
            Optional<ScheduleTypes> scheduleType = scheduleTypesRepository.findById(wrapper.getScheduletypeid());
            Optional<AmountType> amountType = amounttypeRepo.findById(wrapper.getAmounttypeid());
            if (contributionsRepository.countByNameAndMemberGroupIdAndSoftDeleteFalse(wrapper.getName(), wrapper.getGroupid()) > 0)
                return new UniversalResponse("fail", getResponseMessage("contributionExists"));

            if (chamaKycService.getMonoGroupById(wrapper.getGroupid()) == null)
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"));

            if (scheduleType.isEmpty())
                return new UniversalResponse("fail", getResponseMessage("scheduleTypeNotFound"));

            if (contributionType.isEmpty())
                return new UniversalResponse("fail", getResponseMessage("contributionTypeNotFound"));

            if (amountType.isEmpty()) return new UniversalResponse("fail", getResponseMessage("amountTypeNotFound"));

            Contributions contribution = new Contributions();
            contribution.setContributiondetails(gson.toJson(wrapper.getContributiondetails()));
            contribution.setContributionType(contributionType.get());
            contribution.setCreatedBy(wrapper.getCreatedby());
            contribution.setMemberGroupId(wrapper.getGroupid());
            contribution.setName(wrapper.getName());
            contribution.setScheduleType(scheduleType.get());
            contribution.setStartDate(wrapper.getStartdate());
            contribution.setAmountType(amountType.get());
            contribution.setActive(true);

            createContribution(contribution);
            return new UniversalResponse("success", getResponseMessage("contributionAddedSuccessfully"));
        }).publishOn(Schedulers.boundedElastic());
    }

    /**
     * Initiates a contribution payment.
     *
     * @param dto         that contains group id, contribution id and amount to contribute
     * @param phoneNumber
     * @return a success or fail message
     */
    @Override
    public Mono<UniversalResponse> makeContribution(ContributionPaymentDto dto, String phoneNumber) {
        return Mono.fromCallable(() -> {

            MemberWrapper userName = chamaKycService.searchMonoMemberByPhoneNumber(phoneNumber);

            if (userName == null) {
                return new UniversalResponse("fail", getResponseMessage("appUserNotFound"));
            }

            boolean mpesa;

            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(dto.getGroupId());

            if (groupWrapper == null) {
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"));
            }

            if (!groupWrapper.isActive()) {
                return new UniversalResponse("fail", getResponseMessage("groupIsDeactivated"));
            }

            long groupId = groupWrapper.getId();

            MemberWrapper member = chamaKycService.searchMonoMemberByPhoneNumber(dto.getPhoneNumber());

            if (member == null) {
                return new UniversalResponse("fail", getResponseMessage("memberNotFound"));
            }

            long memberId = member.getId();
            GroupMemberWrapper groupMembership = chamaKycService.getMonoGroupMembershipByGroupIdAndMemberId(groupId, memberId);
            if (groupMembership == null) {
                return new UniversalResponse("fail", getResponseMessage("memberIsNotPartOfGroup"));
            }

            if (dto.getAmount() < 1) {
                return new UniversalResponse("fail", getResponseMessage("amountNotValid"));
            }

            if (!dto.getPaymentType().equalsIgnoreCase("saving") && !dto.getPaymentType().equalsIgnoreCase("project") && !dto.getPaymentType().equalsIgnoreCase("loan") && !dto.getPaymentType().equalsIgnoreCase("welfare") && !dto.getPaymentType().equalsIgnoreCase("fine")) {
                return new UniversalResponse("fail", "Invalid payment type. valid payment types are saving,project,loan,welfare,fine");
            }

            if (dto.getCoreAccount().length() < 13) {
                log.info("CONTRIBUTION*************ITS MPESA {}", dto.getPhoneNumber());
                mpesa = true;
                dto.setCoreAccount("");
                if (!member.getPhonenumber().equals(userName.getPhonenumber()))

                    return new UniversalResponse("fail", "Phone number does not belong to the user!!");
            } else {
                log.info("CONTRIBUTION*************ITS CORE ACCOUNT {}", dto.getCoreAccount());
                if (!member.getPhonenumber().equals(userName.getPhonenumber()))
                    return new UniversalResponse("fail", "PBK account does not belong to Mchama Member!!");
                mpesa = false;
            }

            Optional<Contributions> contributions = contributionsRepository.findByGroupIdAndActiveTrueAndSoftDeleteFalse(dto.getGroupId());

            if (contributions.isEmpty()) {
                return new UniversalResponse("fail", getResponseMessage("contributionNotFound"));
            }

            Optional<Accounts> accounts = accountsRepository.findGroupSavingsAccount(groupWrapper.getId());

            if (accounts.isEmpty() || accounts.get().getName().equals("DEFAULT_ACCOUNT")) {
                return new UniversalResponse("fail", getResponseMessage("groupHasNoAccount"));
            }

            String transactionId = TransactionIdGenerator.generateTransactionId("CNT");
            Accounts originalAccount = accounts.get();
            String cardNumber = originalAccount.getAccountdetails().replaceAll("\\b(\\d{4})(\\d{8})(\\d{4})", "$1XXXXXXXX$3");
            String firstFourChars = cardNumber.substring(0, 4);
            String lastFourChars = cardNumber.substring(9, 13);
            String maskedCardNumber = firstFourChars + "*****" + lastFourChars;

            Integer chargeAmount;

            String chargeRef = TransactionIdGenerator.generateTransactionId("MWC");
            String accountRef = member.getPhonenumber();
            String depositor = member.getFirstname();
            Map<String, String> esbChargeRequest = constructChargesBody(
                    accountRef,
                    dto.getAmount(),
                    chargeRef,
                    ChargeType.MCHAMA_DEPOSIT.name()
            );

            String esbChargeBody = gson.toJson(esbChargeRequest);

            log.info("API CHANNEL CHARGE REQUEST {}", esbChargeBody);

            String chargeRes = postBankWebClient.post()
                    .uri(postBankUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(esbChargeBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribeOn(Schedulers.boundedElastic())
                    .block();

            JsonObject chargeJsonObject = gson.fromJson(chargeRes, JsonObject.class);
            log.info("ESB CHARGES RESPONSE {}", chargeRes);

            if (!chargeJsonObject.get("field39").getAsString().equals("00")) {
                log.info("CHARGES FAILURE REASON... {}", chargeJsonObject.get("field48").getAsString());
                return UniversalResponse.builder()
                        .status("fail")
                        .message("Fail to get charges")
                        .timestamp(new Date(System.currentTimeMillis()))
                        .build();
            }

            chargeAmount = chargeJsonObject.get("chargeamount").getAsInt();

            Contributions contrib = contributions.get();

            groupAccountBalanceInquiry(groupWrapper, originalAccount);

            ContributionPayment contributionPayment = ContributionPayment.builder()
                    .contributionId(contrib.getId())
                    .paymentType(dto.getPaymentType())
                    .groupAccountId(contrib.getMemberGroupId())
                    .amount(dto.getAmount())
                    .transactionId(transactionId)
                    .groupId(dto.getGroupId())
                    .isCombinedPayment(false)
                    .paymentStatus(PaymentEnum.PAYMENT_PENDING.name())
                    .isPenalty(false)
                    .fineId(0L)
                    .penaltyId(0L)
                    .schedulePaymentId(String.valueOf(contrib.getScheduleType().getId()))
                    .isFine(false)
                    .narration(dto.getNarration())
                    .phoneNumber(dto.getPhoneNumber())
                    .firstDeposit(false)
                    .paidIn(Double.valueOf(dto.getAmount()))
                    .paidOut((double) 0)
                    .paidBalance(originalAccount.getAccountbalance())
                    .actualBalance(originalAccount.getAccountbalance())
                    .build();

            ContributionPayment savedContributionPayment = contributionsPaymentRepository.save(contributionPayment);

            Mono<UniversalResponse> responseMono = null;

            if (mpesa) {
                Map<String, String> esbRequest = constructBody(
                        groupWrapper.getCsbAccount(),
                        dto.getPhoneNumber(), dto.getCoreAccount(),
                        dto.getAmount(), transactionId,
                        "MC", String.valueOf(chargeAmount));

                String body = gson.toJson(esbRequest);
                String scope = dto.getCoreAccount().isBlank() ? "MC" : "MCC";
                log.info("MPESA CHANNEL API REQUEST {}", body);
                responseMono = postBankWebClient.post()
                        .uri(postBankUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body).retrieve()
                        .bodyToMono(String.class)
                        .publishOn(Schedulers.boundedElastic())
                        .map(res -> {
                            JsonObject jsonObject = gson.fromJson(res, JsonObject.class);
                            log.info("MPESA CHANNEL API RESPONSE {}", jsonObject);
                            if (!jsonObject.get("field39").getAsString().equals("00")) {
                                log.info("CHECKOUT CONTRIBUTION PAYMENT FAILURE REASON {}", jsonObject.get("field48").getAsString());
                                return new UniversalResponse("fail", jsonObject.get("field48").getAsString(), null);
                            }
                            log.info("MPESA CHANNEL API RESPONSE ON SUCCES {}", jsonObject);
                            LocalDate currentDate = LocalDate.now();
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM");
                            String currentMonthString = currentDate.format(formatter);
                            if (savedContributionPayment.getPaymentType().equals("fine")) {
                                savedContributionPayment.setIsFine(true);
                            }
                            savedContributionPayment.setPaymentStatus(PaymentEnum.PAID.name());
                            savedContributionPayment.setLastModifiedDate(new Date());
                            savedContributionPayment.setTransactionDate(new Date());
                            savedContributionPayment.setMonth(currentMonthString.toLowerCase());
                            savedContributionPayment.setContributionId(savedContributionPayment.getContributionId());
                            savedContributionPayment.setPaymentForType("CO");
                            contributionsPaymentRepository.save(savedContributionPayment);
                            savedTransactionLog(savedContributionPayment, contrib, originalAccount);

                            return new UniversalResponse("success", getResponseMessage("requestReceived"));
                        })
                        .onErrorReturn(new UniversalResponse("fail", getResponseMessage("serviceNotAvailable")))
                        .doOnNext(res -> esbLoggingService.logESBRequest(body, scope));
            }
            if (!mpesa) {
                Map<String, String> esbRequest = constructBody(
                        groupWrapper.getCsbAccount(),
                        dto.getPhoneNumber(),
                        dto.getCoreAccount(), dto.getAmount(), transactionId,
                        "MCC", String.valueOf(chargeAmount));

                String body = gson.toJson(esbRequest);
                String scope = dto.getCoreAccount().isBlank() ? "MC" : "MCC";
                log.info("IFT CHANNEL API DEPOSIT REQUEST {}", body);
                responseMono = postBankWebClient.post()
                        .uri(postBankUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body).retrieve()
                        .bodyToMono(String.class)
                        .publishOn(Schedulers.boundedElastic())
                        .map(res -> {
                            log.info("IFT CHANNEL API RESPONSE e {}", res);
                            JsonObject jsonObject = gson.fromJson(res, JsonObject.class);
                            String esbResponse = jsonObject.get("field48").getAsString();

                            if (!jsonObject.get("field39").getAsString().equals("00")) {
                                log.info("IFT CONTRIBUTION PAYMENT FAILURE REASON {}", jsonObject.get("field48").getAsString());
                                return new UniversalResponse("fail", jsonObject.get("field48").getAsString());
                            }
                            log.info("IFT CHANNEL API RESPONSE ON SUCCESS {}", jsonObject);
                            LocalDate currentDate = LocalDate.now();
                            //TODO:: Get the current month
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM");
                            String currentMonthString = currentDate.format(formatter);
                            if (savedContributionPayment.getPaymentType().equals("fine")) {
                                savedContributionPayment.setIsFine(true);
                            }
                            savedContributionPayment.setPaymentStatus(PaymentEnum.PAYMENT_SUCCESS.name());
                            savedContributionPayment.setLastModifiedDate(new Date());
                            savedContributionPayment.setTransactionDate(new Date());
                            savedContributionPayment.setMonth(currentMonthString.toLowerCase());
                            savedContributionPayment.setFirstDeposit(true);
                            savedContributionPayment.setContributionId(savedContributionPayment.getContributionId());
                            savedContributionPayment.setPaymentForType("FT");
                            savedContributionPayment.setCoreAccount(dto.getCoreAccount());
                            contributionPayment.setPaidIn(Double.valueOf(savedContributionPayment.getAmount()));
                            contributionPayment.setPaidOut((double) 0);
                            contributionPayment.setPaidBalance(savedContributionPayment.getAmount() + originalAccount.getAccountbalance());
                            contributionPayment.setActualBalance(savedContributionPayment.getAmount() + originalAccount.getAccountbalance());
                            ContributionPayment updateContribution = contributionsPaymentRepository.save(savedContributionPayment);


                            if (savedContributionPayment.getPaymentType().equals("saving")) {
                                addContributionsToShareOut(updateContribution);
                            }
                            if (savedContributionPayment.getPaymentType().equals("loan")) {
                                updateLoanDisbursed(updateContribution, member, originalAccount);
                            }

                            //todo::handle first deposit to officials
                            int checkFirstDeposit = contributionsPaymentRepository.countAllByGroupIdAndContributionIdAndPaymentStatusAndSoftDeleteFalse(groupWrapper.getId(), contributionPayment.getContributionId(), PaymentEnum.PAID.name());

                            if (checkFirstDeposit == 1) {
                                sendFirstDepositSmsToOfficials(updateContribution);
                            }

                            //todo::send sms notification to official
                            sendDepositSmsToGroupMembers(groupWrapper.getId(), updateContribution.getAmount(), maskedCardNumber, updateContribution.getTransactionId(), depositor);
                            savedTransactionLog(contributionPayment, contrib, originalAccount);
                            return new UniversalResponse("success", esbResponse);
                        })
                        .onErrorReturn(new UniversalResponse("fail", getResponseMessage("serviceNotAvailable")))
                        .doOnNext(res -> esbLoggingService.logESBRequest(body, scope));
            }
            return new UniversalResponse("success", Objects.requireNonNull(responseMono.block()).getMessage(), savedContributionPayment.getId());

        }).subscribeOn(Schedulers.boundedElastic());
    }

    private void updateLoanDisbursed(ContributionPayment contr, MemberWrapper member, Accounts accounts) {
        GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(contr.getGroupId());
        LoansDisbursed loansDisbursed = loansdisbursedRepo.findFirstByGroupIdAndMemberIdAndStatusOrderByIdAsc(contr.getGroupId(), member.getId(), PaymentEnum.YET_TO_PAY.name());

        if (!(loansDisbursed == null)) {
            double disbursedAmount = loansDisbursed.getDueamount();
            double paidAmount = contr.getAmount();
            double totalAmount = loansDisbursed.getPrincipal() + loansDisbursed.getInterest();
            double newAmount = loansDisbursed.getDueamount() - paidAmount;

            if (disbursedAmount > paidAmount) {
                double remainingAmount = disbursedAmount - paidAmount;
                loansDisbursed.setDueamount(disbursedAmount - paidAmount);
                loansDisbursed.setLastModifiedDate(new Date());
                loansDisbursed.setLastModifiedBy(contr.getPhoneNumber());
                loansdisbursedRepo.save(loansDisbursed);
                notificationService.sendPendingLoansMessage(member.getFirstname(), member.getPhonenumber(), remainingAmount, member.getLanguage(), groupWrapper.getName(), totalAmount);
                String status = PaymentEnum.PAYMENT_PENDING.name();
                saveLoanRepayment(contr, loansDisbursed, paidAmount, newAmount, disbursedAmount, status);

            } else if (paidAmount == disbursedAmount) {
                loansDisbursed.setDueamount(0);
                loansDisbursed.setLastModifiedDate(new Date());
                loansDisbursed.setLastModifiedBy(contr.getPhoneNumber());
                loansDisbursed.setStatus(PaymentEnum.FULLY_PAID.name());
                loansdisbursedRepo.save(loansDisbursed);
                notificationService.sendSettledLoansMessage(member.getFirstname(), groupWrapper.getName(), totalAmount, member.getPhonenumber(), member.getLanguage());
                saveLoanRepayment(contr, loansDisbursed, paidAmount, newAmount, disbursedAmount, PaymentEnum.FULLY_PAID.name());
            } else if (paidAmount > disbursedAmount) {
                //todo:: handle over payments
                double overPaid = paidAmount - disbursedAmount;
                loansDisbursed.setDueamount(0);
                loansDisbursed.setLastModifiedDate(new Date());
                loansDisbursed.setLastModifiedBy(contr.getPhoneNumber());
                loansDisbursed.setStatus(PaymentEnum.FULLY_PAID.name());
                loansdisbursedRepo.save(loansDisbursed);
                //TODO:: update overpaid loan to savings
                updateMemberLoanOverPayment(contr, member, overPaid, accounts);
                saveLoanRepayment(contr, loansDisbursed, paidAmount, overPaid, disbursedAmount, PaymentEnum.FULLY_PAID.name());
                notificationService.sendOverPaidLoansMessage(member.getFirstname(), groupWrapper.getName(), totalAmount, overPaid, member.getPhonenumber(), member.getLanguage());

            }
        } else {
            //todo:: Deduct Overpaid Balance
            ContributionPayment updatePayment = contributionsPaymentRepository.findFirstByIdAndGroupIdAndTransactionIdAndSoftDeleteFalse(contr.getId(), contr.getGroupId(), contr.getTransactionId());
            if (!(updatePayment == null)) {
                updatePayment.setPaymentType(TypeOfContribution.saving.name());
                updatePayment.setAmount(contr.getAmount());
                updatePayment.setLastModifiedDate(new Date());
                updatePayment.setLastModifiedBy(contr.getPhoneNumber());
                updatePayment.setPaidIn(Double.valueOf(contr.getAmount()));
                updatePayment.setPaidOut((double) 0);
                updatePayment.setPaidBalance(accounts.getAccountbalance() + contr.getAmount());
                updatePayment.setActualBalance(accounts.getAccountbalance() + contr.getAmount());
                contributionsPaymentRepository.save(updatePayment);
            }
        }
    }


    private void saveLoanRepayment(ContributionPayment contr, LoansDisbursed loansDisbursed, double paidAmount,
                                   double newAmount, double disbursedAmount, String status) {
        LoansRepayment loansRepayment = new LoansRepayment();
        loansRepayment.setLoansDisbursed(loansDisbursed);
        loansRepayment.setAmount(paidAmount);
        loansRepayment.setMemberId(loansDisbursed.getMemberId());
        loansRepayment.setPaymentType(contr.getPaymentType());
        loansRepayment.setReceiptnumber(contr.getTransactionId());
        loansRepayment.setNewamount(newAmount);
        loansRepayment.setOldamount(disbursedAmount);
        loansRepayment.setStatus(status);
        loansRepayment.setTransactionDate(new Date());
        loansrepaymentRepo.save(loansRepayment);
        //todo:: save to contribution payment for wallet deductions
        updateLoanRepaymentContribution(contr);
    }

    @Async
    protected void updateLoanRepaymentContribution(ContributionPayment contr) {
        String transactionId = TransactionIdGenerator.generateTransactionId("LRM");
        ContributionPayment payment = new ContributionPayment();
        payment.setPaymentType("loan");
        payment.setAmount(contr.getAmount());
        payment.setContribution('N');
        payment.setShareOut('N');
        payment.setIsFine(false);
        payment.setIsPenalty(false);
        payment.setPaidIn(contr.getPaidIn());
        payment.setPaidOut(contr.getPaidOut());
        payment.setActualBalance(contr.getActualBalance());
        payment.setPaidBalance(contr.getPaidBalance());
        payment.setFirstDeposit(false);
        payment.setSharesCompleted('N');
        payment.setMonth(contr.getMonth());
        payment.setTransactionId(transactionId);
        payment.setContributionId(contr.getContributionId());
        payment.setGroupId(contr.getGroupId());
        payment.setGroupAccountId(contr.getGroupAccountId());
        payment.setPhoneNumber(contr.getPhoneNumber());
        payment.setCoreAccount(contr.getCoreAccount());
        payment.setNarration(contr.getNarration());
        payment.setSchedulePaymentId(contr.getSchedulePaymentId());
        payment.setPaymentForType(contr.getPaymentForType());
        payment.setPaymentStatus(PaymentEnum.FULLY_PAID.name());
        contributionsPaymentRepository.save(payment);
    }


    private void updateMemberLoanOverPayment(ContributionPayment contrib, MemberWrapper member,
                                             double overPaid, Accounts accounts) {
        String transactionId = TransactionIdGenerator.generateTransactionId("CNT");
        ContributionPayment payment = ContributionPayment.builder()
                .contributionId(contrib.getId())
                .paymentType("saving")
                .groupAccountId(contrib.getGroupAccountId())
                .amount((int) overPaid)
                .transactionId(transactionId)
                .groupId(contrib.getGroupId())
                .isCombinedPayment(false)
                .paymentStatus(PaymentEnum.PAYMENT_SUCCESS.name())
                .isPenalty(false)
                .fineId(0L)
                .penaltyId(0L)
                .schedulePaymentId(contrib.getSchedulePaymentId())
                .isFine(false)
                .narration("Overflow from Loan Repayment")
                .phoneNumber(member.getPhonenumber())
                .coreAccount(contrib.getCoreAccount())
                .firstDeposit(false)
                .paidIn(overPaid)
                .paidOut((double) 0)
                .paidBalance(accounts.getAccountbalance() + overPaid)
                .actualBalance(accounts.getAccountbalance() + overPaid)
                .month(contrib.getMonth())
                .contribution('Y')
                .shareOut('Y')
                .sharesCompleted('N')
                .build();
        contributionsPaymentRepository.save(payment);
        OverpaidContribution contribution = new OverpaidContribution();
        contribution.setContributionId(payment.getContributionId());
        contribution.setAmount(overPaid);
        contribution.setGroupId(payment.getGroupId());
        contribution.setLastEsbTransactionCode(payment.getTransactionId());
        contribution.setPhoneNumber(payment.getPhoneNumber());
        contribution.setMemberId(member.getId());
        overpaidContributionRepository.save(contribution);
    }

    private void savedTransactionLog(ContributionPayment payment, Contributions contrib, Accounts account) {

        TransactionsLog transactionsLog = TransactionsLog.builder()
                .transactionDate(new Date())
                .transactionType(payment.getPaymentType())
                .uniqueTransactionId(payment.getTransactionId())
                .capturedby(payment.getCreatedBy())
                .transamount(payment.getAmount())
                .contributionNarration(payment.getNarration())
                .contributions(contrib)
                .status(payment.getPaymentStatus())
                .paymentType(payment.getPaymentType())
                .creditaccounts(account)
                .debitaccounts(payment.getPhoneNumber())
                .debitphonenumber(payment.getPhoneNumber())
                .build();
        transactionlogsRepo.save(transactionsLog);
    }

    private void sendDepositSmsToGroupMembers(Long groupId, Integer amount, String maskedCardNumber, String
            transactionId, String depositor) {
        GroupWrapper group = chamaKycService.getMonoGroupById(groupId);
        if (group != null && group.isActive()) {
            chamaKycService.getFluxGroupMembers(group.getId())
                    .subscribe(member -> notificationService.sendDepositSmsToGroupMembers(member.getFirstname(), depositor, group.getName(), amount, transactionId, member.getPhonenumber(), maskedCardNumber, member.getLanguage()));
        } else {
            log.error("COULD NOT SEND MEMBER DEPOSIT SMS. GROUP NOT FOUND.");
        }
    }

    @Async
    protected void sendFirstDepositSmsToOfficials(ContributionPayment savedPayment) {
        GroupWrapper group = chamaKycService.getMonoGroupById(savedPayment.getGroupId());
        if (group != null && group.isActive()) {
            chamaKycService.getGroupOfficials(group.getId())
                    .subscribe(member -> notificationService.sendFirstDepositSmsToOfficials(member.getFirstname(), group.getName(), savedPayment.getAmount(), member.getPhonenumber(), member.getLanguage()));
            savedPayment.setFirstDeposit(false);
            savedPayment.setLastModifiedDate(new Date());
            savedPayment.setLastModifiedBy("SYSTEM");
            contributionsPaymentRepository.save(savedPayment);
        } else {
            log.error("Could not send first deposit SMS. Group not found.");
        }
    }

    private void addContributionsToShareOut(ContributionPayment payment) {
        LocalDate localDate = payment.getLastModifiedDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate currentDate = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM");
        String currentMonthString = currentDate.format(formatter);
        Locale locale = Locale.getDefault();
        WeekFields weekFields = WeekFields.of(locale);
        int weekNumber = currentDate.get(weekFields.weekOfWeekBasedYear());
        payment.setLastModifiedDate(new Date());
        payment.setTransactionDate(new Date());
        payment.setContribution('Y');
        payment.setShareOut('Y');
        payment.setSharesCompleted('N');
        payment.setMonth(currentMonthString.toLowerCase());
        payment.setContributionId(payment.getContributionId());
        contributionsPaymentRepository.save(payment);
        log.info("Group Id ===> {}, ===> Phone {}, ===> Month {}, === Status {}", payment.getGroupId(), payment.getPhoneNumber(), currentMonthString.toLowerCase(), payment.getPaymentStatus());
    }

    private void updatePaymentContribution(ContributionPayment contributionPayment, String currentMonthString) {
        contributionPayment.setLastModifiedDate(new Date());
        contributionPayment.setTransactionDate(new Date());
        contributionPayment.setContribution('Y');
        contributionPayment.setShareOut('Y');
        contributionPayment.setSharesCompleted('Y');
        contributionPayment.setMonth(currentMonthString.toLowerCase());
        contributionsPaymentRepository.save(contributionPayment);
    }

    private UniversalResponse validateCoreAccount(ContributionPaymentDto dto, MemberWrapper member) {
        if (!dto.getCoreAccount().isBlank() && member.getLinkedAccounts() == null)
            return new UniversalResponse("fail", getResponseMessage("memeberHasNoLinkedAccounts"));

        if (!dto.getCoreAccount().isBlank() && Arrays.stream(member.getLinkedAccounts().split(",")).noneMatch(s -> s.equals(dto.getCoreAccount())))
            return new UniversalResponse("fail", getResponseMessage("coreAccountDoesNotBelongToMember"));
        return null;
    }

    private void checkBalance(ContributionPaymentDto dto, MemberWrapper member) {
        String accountToCheckBalance = dto.getCoreAccount().isBlank() ? member.getEsbwalletaccount() : dto.getCoreAccount();
        // Simulate a successful balance inquiry response with a fixed available balance of 1000000
        String fixedAvailableBalance = "100000000";

        // Check if the available balance is less than the requested amount
        if (Double.parseDouble(fixedAvailableBalance) <= dto.getAmount()) {
            throw new IllegalArgumentException(getResponseMessage("insufficientBalance"));
        }
    }


    @Override
    public Mono<UniversalResponse> payForContributionPenalty(ContributionPaymentDto dto) {
        return Mono.fromCallable(() -> {

            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(dto.getGroupId());
            if (groupWrapper == null)
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"));
            if (!groupWrapper.isActive())
                return new UniversalResponse("fail", getResponseMessage("groupIsDeactivated"));

            long groupId = groupWrapper.getId();

            MemberWrapper member = chamaKycService.searchMonoMemberByPhoneNumber(dto.getPhoneNumber());

            if (member == null)
                return new UniversalResponse("fail", getResponseMessage("memberNotFound"));
            long memberId = member.getId();
            long fineId = dto.getFineId();

            Fines fines = finesRepository.findFirstByIdAndGroupIdAndPaymentStatusAndSoftDeleteFalse(fineId, groupId, PaymentEnum.PAYMENT_PENDING.name());

            if (fines == null)
                return new UniversalResponse("fail", getResponseMessage("penaltyNotFound"));

            if (dto.getAmount() < 1)
                return new UniversalResponse("fail", getResponseMessage("amountNotValid"));

            if (dto.getCoreAccount().length() < 13)
                dto.setCoreAccount("");


            Optional<Contributions> contributions = contributionsRepository.findByGroupIdAndActiveTrueAndSoftDeleteFalse(dto.getGroupId());

            if (contributions.isEmpty()) {
                return new UniversalResponse("fail", getResponseMessage("contributionNotFound"));
            }
            Contributions contrib = contributions.get();
            Accounts accounts = accountsRepository.findByGroupIdAndActiveTrueAndSoftDeleteFalse(groupWrapper.getId());
            if (accounts == null) {
                return new UniversalResponse("fail", getResponseMessage("groupAccountNotFound"));
            }
            boolean mpesa;
            if (dto.getCoreAccount().length() < 13) {
                mpesa = true;
                dto.setCoreAccount("");
            } else {
                mpesa = false;
            }
            String chargeType = "MCHAMA_DEPOSIT";
            Integer chargeAmount;
            String chargeRef = TransactionIdGenerator.generateTransactionId("MWC");
            String accountRef = member.getPhonenumber();

            Map<String, String> esbChargeRequest = constructChargesBody(
                    accountRef,
                    dto.getAmount(),
                    chargeRef,
                    chargeType
            );
            String esbChargeBody = gson.toJson(esbChargeRequest);

            log.info("API Channel Charge request {}", esbChargeRequest);

            String chargeRes = postBankWebClient.post()
                    .uri(postBankUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(esbChargeBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribeOn(Schedulers.boundedElastic())
                    .block();

            JsonObject chargeJsonObject = gson.fromJson(chargeRes, JsonObject.class);
            log.info("Esb Charges response {}", chargeRes);

            if (!chargeJsonObject.get("field39").getAsString().equals("00")) {
                log.info("Charges failure reason... {}", chargeJsonObject.get("field48").getAsString());
                return UniversalResponse.builder()
                        .status("fail")
                        .message("Fail to get charges")
                        .timestamp(new Date(System.currentTimeMillis()))
                        .build();
            }

            chargeAmount = chargeJsonObject.get("chargeamount").getAsInt();


            String transactionId = TransactionIdGenerator.generateTransactionId("FIN");

            groupAccountBalanceInquiry(groupWrapper, accounts);

            LocalDate currentDate = LocalDate.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM");
            String currentMonthString = currentDate.format(formatter);
            Locale locale = Locale.getDefault();
            WeekFields weekFields = WeekFields.of(locale);
            int weekNumber = currentDate.get(weekFields.weekOfWeekBasedYear());
            ContributionPayment contributionPayment = ContributionPayment.builder()
                    .contributionId(contrib.getId())
                    .paymentType("fine")
                    .groupAccountId(contrib.getMemberGroupId())
                    .amount(dto.getAmount())
                    .transactionId(transactionId)
                    .groupId(dto.getGroupId())
                    .isCombinedPayment(true)
                    .paymentStatus(PaymentEnum.PAYMENT_PENDING.name())
                    .isPenalty(true)
                    .fineId(fines.getId())
                    .penaltyId(fines.getId())
                    .schedulePaymentId(String.valueOf(contrib.getScheduleType().getId()))
                    .isFine(true)
                    .narration(dto.getNarration())
                    .phoneNumber(dto.getPhoneNumber())
                    .coreAccount(dto.getCoreAccount())
                    .firstDeposit(false)
                    .paidIn(Double.valueOf(dto.getAmount()))
                    .paidOut((double) 0)
                    .paidBalance(accounts.getAccountbalance())
                    .actualBalance(accounts.getAccountbalance())
                    .month(currentMonthString.toLowerCase())
                    .build();


            ContributionPayment savePayment = contributionsPaymentRepository.save(contributionPayment);

            Mono<UniversalResponse> responseMono = null;
            if (mpesa) {
                Map<String, String> esbRequest = constructBody(
                        groupWrapper.getCsbAccount(),
                        member.getPhonenumber(), dto.getCoreAccount(),
                        dto.getAmount(), transactionId,
                        "MC", String.valueOf(chargeAmount));

                String body = gson.toJson(esbRequest);
                String scope = dto.getCoreAccount().isBlank() ? "MC" : "MCC";
                log.info("API Channel Deposit request {}", body);
                responseMono = postBankWebClient.post()
                        .uri(postBankUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body).retrieve()
                        .bodyToMono(String.class)
                        .publishOn(Schedulers.boundedElastic())
                        .map(res -> {
                            JsonObject jsonObject = gson.fromJson(res, JsonObject.class);
                            if (!jsonObject.get("field39").getAsString().equals("00")) {
                                log.info("Fine payment failure reason... {}", jsonObject.get("field48").getAsString());
                                savePayment.setPaymentFailureReason(jsonObject.get("field48").getAsString());
                                contributionsPaymentRepository.save(savePayment);
                                return new UniversalResponse("fail", jsonObject.get("field48").getAsString());
                            }
                            log.info("Check out Esb response on success {}", jsonObject);

                            savePayment.setPaymentStatus(PaymentEnum.PAID.name());
                            savePayment.setLastModifiedDate(new Date());
                            savePayment.setTransactionDate(new Date());
                            savePayment.setMonth(currentMonthString.toLowerCase());
                            savePayment.setContributionId(savePayment.getContributionId());
                            savePayment.setPaymentForType("CO");
                            contributionsPaymentRepository.save(savePayment);
                            savedTransactionLog(savePayment, contrib, accounts);

                            return new UniversalResponse("success", getResponseMessage("requestReceived"));

                        }).onErrorReturn(new UniversalResponse("fail", getResponseMessage("serviceNotAvailable"))).doOnNext(res -> esbLoggingService.logESBRequest(body, scope));
            }
            if (!mpesa) {
                Map<String, String> esbRequest = constructBody(
                        groupWrapper.getCsbAccount(),
                        member.getPhonenumber(),
                        dto.getCoreAccount(), dto.getAmount(), transactionId,
                        "MCC", String.valueOf(chargeAmount));

                String body = gson.toJson(esbRequest);
                String scope = dto.getCoreAccount().isBlank() ? "MC" : "MCC";
                log.info("API Channel Deposit request {}", body);

                responseMono = postBankWebClient.post()
                        .uri(postBankUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body).retrieve()
                        .bodyToMono(String.class)
                        .publishOn(Schedulers.boundedElastic())
                        .map(res -> {
                            log.info("IFT Esb response {}", res);
                            JsonObject jsonObject = gson.fromJson(res, JsonObject.class);
                            String esbResponse = jsonObject.get("field48").getAsString();

                            if (!jsonObject.get("field39").getAsString().equals("00")) {
                                log.info("Fine payment failure reason... {}", jsonObject.get("field48").getAsString());
                                savePayment.setPaymentFailureReason(jsonObject.get("field48").getAsString());
                                contributionsPaymentRepository.save(savePayment);
                                return new UniversalResponse("fail", jsonObject.get("field48").getAsString());
                            }
                            log.info("IFT Esb response on success {}", jsonObject);

                            //TODO:: Get the current month
                            savePayment.setPaymentStatus(PaymentEnum.PAYMENT_SUCCESS.name());
                            savePayment.setLastModifiedDate(new Date());
                            savePayment.setTransactionDate(new Date());
                            savePayment.setMonth(currentMonthString.toLowerCase());
                            savePayment.setFirstDeposit(true);
                            savePayment.setContributionId(savePayment.getContributionId());
                            savePayment.setPaymentForType("FT");
                            savePayment.setCoreAccount(dto.getCoreAccount());
                            savePayment.setPaidIn(Double.valueOf(savePayment.getAmount()));
                            savePayment.setPaidOut((double) 0);
                            savePayment.setPaidBalance(savePayment.getAmount() + accounts.getAccountbalance());
                            savePayment.setActualBalance(savePayment.getAmount() + accounts.getAccountbalance());

                            ContributionPayment payment = contributionsPaymentRepository.save(savePayment);

                            Optional<Fines> optionalFines = finesRepository.getFineFined(dto.getGroupId(), dto.getFineId());

                            Fines fines1 = optionalFines.get();
                            double fineBalance = fines1.getFineBalance();
                            double paidAmount = payment.getAmount();

                            if (paidAmount > fineBalance) {
                                double overFlow = paidAmount - fineBalance;
                                fines1.setFineBalance(0.0);
                                fines1.setPaymentStatus(PaymentEnum.PAID.name());
                                fines1.setLastModifiedDate(new Date());
                                fines1.setLastModifiedBy(member.getPhonenumber());
                                finesRepository.save(fines1);
                                recordOverflowToSavings(payment, overFlow, fines1, accounts);
                            } else if (payment.getAmount() < fineBalance) {
                                Double remainingBalance = fineBalance - payment.getAmount();
                                fines1.setFineBalance(remainingBalance);
                                fines1.setPaymentStatus(PaymentEnum.PAYMENT_PENDING.name());
                                fines1.setLastModifiedDate(new Date());
                                fines1.setLastModifiedBy(member.getPhonenumber());
                                finesRepository.save(fines1);
                            } else if (payment.getAmount() == fineBalance) {
                                fines1.setFineBalance(0.0);
                                fines1.setPaymentStatus(PaymentEnum.PAID.name());
                                fines1.setLastModifiedDate(new Date());
                                fines1.setLastModifiedBy(member.getPhonenumber());
                                finesRepository.save(fines1);
                            }
                            savedTransactionLog(savePayment, contrib, accounts);
                            return new UniversalResponse("success", esbResponse);

                        })
                        .onErrorReturn(new UniversalResponse("fail", getResponseMessage("serviceNotAvailable")))
                        .doOnNext(res -> esbLoggingService.logESBRequest(body, scope));
            }
            return new UniversalResponse("success", responseMono.block().getMessage(), savePayment.getId());
        }).subscribeOn(Schedulers.boundedElastic());

    }

    private void recordOverflowToSavings(ContributionPayment payment, double overFlow, Fines fine, Accounts accounts) {
        MemberWrapper memberWrapper = chamaKycService.searchMonoMemberByPhoneNumber(payment.getPhoneNumber());
        String transactionCode = TransactionIdGenerator.generateTransactionId("CNT");
        //todo:: Deduct Overpaid Balance
        ContributionPayment updatePayment = contributionsPaymentRepository.findFirstByIdAndGroupIdAndTransactionIdAndSoftDeleteFalse(payment.getId(), payment.getGroupId(), payment.getTransactionId());
        if (!(payment == null)) {
            updatePayment.setAmount((int) (payment.getAmount() - overFlow));
            updatePayment.setLastModifiedDate(new Date());
            updatePayment.setLastModifiedBy(payment.getPhoneNumber());
            updatePayment.setPaidIn((double) 0);
            updatePayment.setPaidOut(overFlow);
            updatePayment.setPaidBalance(accounts.getAccountbalance() - overFlow);
            updatePayment.setActualBalance(accounts.getAccountbalance() - overFlow);
            contributionsPaymentRepository.save(updatePayment);

            ContributionPayment contributionPayment = ContributionPayment.builder()
                    .contributionId(payment.getContributionId())
                    .paymentType("saving")
                    .groupAccountId(payment.getGroupAccountId())
                    .amount((int) overFlow)
                    .transactionId(transactionCode)
                    .groupId(payment.getGroupId())
                    .isCombinedPayment(true)
                    .paymentStatus(PaymentEnum.PAYMENT_SUCCESS.name())
                    .isPenalty(true)
                    .fineId(fine.getId())
                    .penaltyId(fine.getId())
                    .schedulePaymentId(String.valueOf(payment.getSchedulePaymentId()))
                    .isFine(false)
                    .narration("overflow from fine payment.")
                    .phoneNumber(payment.getPhoneNumber())
                    .coreAccount(payment.getCoreAccount())
                    .firstDeposit(false)
                    .paidIn(overFlow)
                    .paidOut((double) 0)
                    .paidBalance(accounts.getAccountbalance() + overFlow)
                    .actualBalance(accounts.getAccountbalance() + overFlow)
                    .month(payment.getMonth())
                    .contribution('Y')
                    .shareOut('Y')
                    .sharesCompleted('N')
                    .build();

            contributionsPaymentRepository.save(contributionPayment);

            OverpaidContribution contribution = new OverpaidContribution();
            contribution.setContributionId(payment.getContributionId());
            contribution.setAmount(overFlow);
            contribution.setGroupId(payment.getGroupId());
            contribution.setLastEsbTransactionCode(payment.getTransactionId());
            contribution.setPhoneNumber(payment.getPhoneNumber());
            contribution.setMemberId(memberWrapper.getId());
            overpaidContributionRepository.save(contribution);
        }
    }


    @Override
    public Mono<UniversalResponse> makeContributionForOtherMember(ContributionPaymentDto dto, String walletAccount) {
        return Mono.fromCallable(() -> {
            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(dto.getGroupId());
            MemberWrapper member = chamaKycService.searchMonoMemberByPhoneNumber(walletAccount);

            if (dto.getAmount() < 1)
                return new UniversalResponse("fail", getResponseMessage("amountNotValid"));

            if (groupWrapper == null)
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"));

            if (dto.getCoreAccount().length() < 14)
                dto.setCoreAccount("");

            UniversalResponse coreAccountValidationRes = validateCoreAccount(dto, member);
            if (coreAccountValidationRes != null)
                return coreAccountValidationRes;

            checkBalance(dto, member);

            ContributionSchedulePayment contributionSchedulePayment = contributionSchedulePaymentRepository.findByContributionScheduledId(dto.getSchedulePaymentId());

            if (contributionSchedulePayment == null)
                return new UniversalResponse("fail", getResponseMessage("contributionSchedulePaymentFound"));

            MemberWrapper beneficiary = chamaKycService.searchMonoMemberByPhoneNumber(dto.getBeneficiary());

            if (beneficiary == null)
                return new UniversalResponse("faill", getResponseMessage("beneficiaryNotFound"));

            GroupMemberWrapper membership = chamaKycService.getMonoGroupMembershipByGroupIdAndMemberId(groupWrapper.getId(), beneficiary.getId());

            if (membership == null)
                return new UniversalResponse("fail", getResponseMessage("memberIsNotPartOfGroup"));

            Optional<Contributions> contributions = contributionsRepository.findByIdAndMemberGroupIdAndSoftDeleteFalse(contributionSchedulePayment.getContributionId(), dto.getGroupId());

            if (contributions.isEmpty()) {
                return new UniversalResponse("fail", "Contribution not found");
            }

            if (!groupWrapper.isActive())
                return new UniversalResponse("fail", getResponseMessage("groupIsDeactivated"));

            Contributions contrib = contributions.get();
            if (contrib.getAmountType().getName().equalsIgnoreCase("fixed amount") && dto.getAmount() > contrib.getContributionAmount()) {
                return new UniversalResponse("fail", "You cannot contribute more than Kes. " + contrib.getContributionAmount());
            }

            String transactionId = TransactionIdGenerator.generateTransactionId("CNT");
            Map<String, String> esbRequest = constructBody(
                    groupWrapper.getCsbAccount(),
                    dto.getCoreAccount().isBlank() ? walletAccount : dto.getCoreAccount(),
                    dto.getCoreAccount(), dto.getAmount(),
                    transactionId,
                    dto.getCoreAccount().isBlank() ? "CO" : "COC", "0");
            String body = gson.toJson(esbRequest);
            String scope = dto.getCoreAccount().isBlank() ? "MC" : "MCC";
            ContributionPayment contributionPayment = ContributionPayment.builder().contributionId(contrib.getId()).groupAccountId(contrib.getMemberGroupId()).schedulePaymentId(contributionSchedulePayment.getContributionScheduledId()).amount(dto.getAmount()).transactionId(transactionId).isCombinedPayment(false).paymentStatus(PaymentEnum.PAYMENT_PENDING.name()).isPenalty(false).phoneNumber(beneficiary.getPhonenumber()).build();
            // Do a funds transfer from Member wallet to Group core account.
            return postBankWebClient.post().contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body).retrieve().bodyToMono(String.class)
                    .publishOn(Schedulers.boundedElastic())
                    .map(res -> {
                        JsonObject jsonObject = gson.fromJson(res, JsonObject.class);

                        if (jsonObject.get("48").getAsString().equals("fail")) {
                            log.info("Contribution payment failure reason... {}", jsonObject.get("54").getAsString());
                            return new UniversalResponse("fail", getResponseMessage("contributionCannotBeMade"));
                        }

                        ContributionPayment savedTransactionLog = contributionsPaymentRepository.save(contributionPayment);
                        List<Accounts> groupAccounts = accountsRepository.findByGroupIdAndActive(groupWrapper.getId(), true);

                        saveTransactionLog(savedTransactionLog, beneficiary, contrib, groupAccounts.get(0), transactionId);

                        return new UniversalResponse("success", getResponseMessage("requestReceived"));
                    }).onErrorReturn(new UniversalResponse("fail", getResponseMessage("serviceNotAvailable")))
                    .doOnNext(res -> esbLoggingService.logESBRequest(body, scope)).block();
        }).publishOn(Schedulers.boundedElastic());
    }

    /**
     * Record a user's withdrawal request and await for approvals from the
     * responsible parties.
     *
     * @param requestWithdrawalWrapper that contains the withdrawal info
     * @return as success or failure message
     */
    @Override
    public Mono<UniversalResponse> recordWithdrawal(RequestwithdrawalWrapper requestWithdrawalWrapper) {
        return Mono.fromCallable(() -> {
            MemberWrapper userName = chamaKycService.searchMonoMemberByPhoneNumber(requestWithdrawalWrapper.getUserName());
            if (userName == null) {
                return new UniversalResponse("fail", getResponseMessage("appUserNotFound"));
            }
            String paymentType = requestWithdrawalWrapper.getPaymentType();

            if (!paymentType.equalsIgnoreCase("saving") && !paymentType.equalsIgnoreCase("project") && !paymentType.equalsIgnoreCase("welfare")) {
                return new UniversalResponse("fail", "Invalid payment type. valid payment types are savings, projects, welfare");
            }

            Optional<Contributions> contributionOptional = contributionsRepository.findById(requestWithdrawalWrapper.getContributionid());
            if (contributionOptional.isEmpty()) {
                return new UniversalResponse("fail", getResponseMessage("contributionNotFound"));
            }

            Contributions contribution = contributionOptional.get();
            Group group = groupRepository.findById(contribution.getGroupId()).orElse(null);
            if (group == null) {
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"));
            }
            Accounts accounts = accountsRepository.findTopByGroupIdAndActiveTrueAndSoftDeleteFalseOrderByIdDesc(contribution.getGroupId());

            if (accounts == null) {
                return new UniversalResponse("fail", getResponseMessage("groupAccountInfo"));
            }

            requestWithdrawalWrapper.setDebitaccountid(accounts.getId());
            log.info("GROUP ID {}, CONTRIBUTION ID {}, CONTRIBUTION GROUP ID {}, ACCOUNTS GROUP ID {} ", group.getId(), contribution.getId(), contribution.getGroupId(), accounts.getGroupId());
            log.info("GROUP NAME {}, CONTRIBUTION DETAILS {}, CONTRIBUTION GROUP NAME {}, ACCOUNTS GROUP NAME {} ", group.getName(), contribution.getContributiondetails(), contribution.getName(), accounts.getName());
            if (requestWithdrawalWrapper.getAmount() < 1) {
                return new UniversalResponse("fail", getResponseMessage("amountNotValid"));
            }

            //TODO:: CHECK TOTAL PAYMENTS
            double paymentTypeBalance = calculateBalance(group.getId(), paymentType);

            //TODO:: DEDUCTIONS
            if (requestWithdrawalWrapper.getAmount() > paymentTypeBalance) {
                return new UniversalResponse("fail", "You can not withdraw more than the available kitty balance.");
            }

            if (!group.getCanWithdraw()) {
                return new UniversalResponse("fail", "Unable to make a withdrawal. Account has not been verified by the Bank");
            }

            if (requestWithdrawalWrapper.getCreditaccount().length() < 13) {
                log.info("WITHDRAWAL**********ITS MPESA {}", requestWithdrawalWrapper.getCreditaccount());
                requestWithdrawalWrapper.setCoreAccount("");

            } else {
                log.info("WITHDRAWAL**********ITS CORE ACCOUNT {}", requestWithdrawalWrapper.getCreditaccount());

            }

            String capturedBy = userName.getFirstname().concat(" " + userName.getLastname());
            String capturedByPhoneNumber = userName.getPhonenumber();
            saveWithdrawal(requestWithdrawalWrapper, accounts, contribution, capturedBy, capturedByPhoneNumber);
            sendWithdrawalRequestMessageToOfficials(userName, contribution.getGroupId(), requestWithdrawalWrapper.getAmount());
            auditTrail("group withdrawal", "group withdrawal requested by member " + capturedByPhoneNumber + " in group " + group.getName(), capturedByPhoneNumber);
            creatNotification(group.getId(), group.getName(), "group withdrawal request sent to officials by" + capturedByPhoneNumber);
            return new UniversalResponse("success", getResponseMessage("withdrawalAwaitingApproval"));
        }).publishOn(Schedulers.boundedElastic());
    }


    private void saveWithdrawal(RequestwithdrawalWrapper requestWithdrawalWrapper, Accounts accounts, Contributions
            contribution, String capturedBy, String capturedByPhoneNumber) {

        WithdrawalsPendingApproval withdrawalsPendingApproval = new WithdrawalsPendingApproval();
        withdrawalsPendingApproval.setAmount(requestWithdrawalWrapper.getAmount());
        withdrawalsPendingApproval.setPending(true);
        if (requestWithdrawalWrapper.getCoreAccount().length() < 13) {
            withdrawalsPendingApproval.setCoreAccount("");
        } else {
            withdrawalsPendingApproval.setCoreAccount(requestWithdrawalWrapper.getCoreAccount());
        }
        withdrawalsPendingApproval.setPhonenumber(requestWithdrawalWrapper.getCreditaccount());
        withdrawalsPendingApproval.setCreditaccount(requestWithdrawalWrapper.getCreditaccount());
        withdrawalsPendingApproval.setGroupId(accounts.getGroupId());
        withdrawalsPendingApproval.setApprovedby(new JsonObject().toString());
        withdrawalsPendingApproval.setApprovalCount(0);
        withdrawalsPendingApproval.setAccount(accounts);
        withdrawalsPendingApproval.setContribution(contribution);
        withdrawalsPendingApproval.setCapturedby(capturedBy);
        withdrawalsPendingApproval.setPaymentStatus(PaymentEnum.PAYMENT_PENDING.name());
        withdrawalsPendingApproval.setPaymentType(requestWithdrawalWrapper.getPaymentType());
        withdrawalsPendingApproval.setCapturedByPhoneNumber(capturedByPhoneNumber);
        withdrawalsPendingApproval.setStatus(getResponseMessage("withdrawalAwaitingApprovalFrom"));
        withdrawalsPendingApproval.setWithdrawal_narration(String.format(getResponseMessage("withdrawalMessage"), capturedBy, requestWithdrawalWrapper.getAmount(), contribution.getName()));
        withdrawalsPendingApproval.setWithdrawalreason(requestWithdrawalWrapper.getWithdrawalreason());
        withdrawalspendingapprovalRepo.save(withdrawalsPendingApproval);
    }

    /**
     * Sends notification message to officials for a member withdrawal request.
     *
     * @param member  the member requesting a withdrawal
     * @param groupId the group id
     * @param amount  the amount to withdraw
     */
    @Async
    protected void sendWithdrawalRequestMessageToOfficials(MemberWrapper member, long groupId, double amount) {
        GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(groupId);
        if (groupWrapper != null && groupWrapper.isActive()) {
            String memberName = String.format("%s %s", member.getFirstname(), member.getLastname());
            notificationService.sendMemberWithdrawRequestText(memberName, amount, groupWrapper.getName(), member.getPhonenumber(), member.getLanguage());
            chamaKycService.getFluxGroupMembers(groupWrapper.getId())
                    .subscribe(mbr -> notificationService.sendOfficialWithdrawRequestText(mbr.getFirstname(), memberName, groupWrapper.getName(), amount, mbr.getPhonenumber(), mbr.getLanguage()));
        } else {
            log.info("COULD NOT SEND MEMBER DEPOSIT SMS. GROUP NOT FOUND OR DEACTIVATED");
        }

    }


    /**
     * Handles callbacks from the postbank ESB.
     * Will be used to update the contribution payment and loan disbursement.
     *
     * @return nothing
     */

//TODO : uncomment this
    @Bean
    @Override
    public Consumer<String> fundsTransferCallback() {
        return body -> {
            Mono.fromRunnable(() -> {
                log.info("***********FUNDS TRANSFER RESPONSE *********** CALL BACK {}", body);
                JsonObject jsonObject = new Gson().fromJson(body, JsonObject.class);
                String account = jsonObject.get("field2").getAsString();
                String transactionId = jsonObject.get("field37").getAsString();

                Optional<EsbRequestLog> esbRequestLog = esbLoggingService.findByTransactionId(transactionId);

                esbRequestLog.ifPresentOrElse(esbLog -> {
                    if (esbLog.isCallbackReceived()) {
                        log.info("***********CALLBACK WITH TRX ID {} IS ALREADY HANDLED*******", transactionId);
                        return;
                    }
                    log.info("***********SAVED SCOPE*********** {}", esbRequestLog.get().getScope());
                    String scope = esbRequestLog.get().getScope();

                    MemberWrapper member;
                    if (account.length() > 12) {
                        //TODO::MEANS IT IS A CORE ACCOUNT
                        member = chamaKycService.searchMonoMemberByCoreAccount(account);
                    } else {
                        //TODO::MEANS IT IS A PHONE NUMBER
                        member = chamaKycService.searchMonoMemberByPhoneNumber(account);
                    }

                    if (member == null) {
                        log.info("MEMBER NOT FOUND {}", jsonObject.get("102").getAsString());
                        return;
                    }
                    if (scope.equals("MC")) {
                        //TODO:: MEMBER CONTRIBUTION WITH WALLET
                        memberContributionResponse(jsonObject, member);
                    }
                    //TODO:: UPDATE CALLBACK RECEIVED STATUS
                    esbLoggingService.updateCallBackReceived(esbLog);
                    log.info("***********CALLBACK PROCESSED SUCCESSFULLY***********");
                }, () -> log.info("***********TRANSACTION LOG NOT FOUND****** {}", transactionId));
            }).subscribeOn(Schedulers.boundedElastic()).publishOn(Schedulers.boundedElastic()).subscribe(res -> log.info("Done executing FT callback..."));
        };
    }

    private void contributeForOtherResponse(JsonObject jsonObject) {
        MemberWrapper memberWrapper = chamaKycService.searchMonoMemberByPhoneNumber(jsonObject.get("102").getAsString());
        MemberWrapper beneficiary = chamaKycService.searchMonoMemberByPhoneNumber(jsonObject.get("66").getAsString());

        if (memberWrapper == null || beneficiary == null) {
            log.info("One of the members is not present!");
            return;
        }

        Optional<ContributionPayment> contributionPayment = contributionsPaymentRepository.findContributionByTransactionId(jsonObject.get("37").getAsString());

        if (contributionPayment.isEmpty()) {
            log.info("Contribution payment with id not found {}", jsonObject);
            return;
        }

        if (jsonObject.get("48").getAsString().equalsIgnoreCase("fail")) {
            // Send SMS to user to let them know the contribution was not successful
            // This is for the member performing payment.
            notificationService.sendContributionFailureMessage(memberWrapper.getPhonenumber(), memberWrapper.getFirstname(), jsonObject.get("4").getAsInt(), memberWrapper.getLanguage());

            ContributionPayment cp = contributionPayment.get();
            cp.setPaymentStatus(PaymentEnum.PAYMENT_FAILED.name());
            ContributionPayment savedContributionPayment = contributionsPaymentRepository.save(cp);
            Optional<Contributions> optionalContribution = contributionsRepository.findById(savedContributionPayment.getContributionId());
            List<Accounts> groupAccount = accountsRepository.findByGroupIdAndActive(savedContributionPayment.getGroupAccountId(), true);
            // save transaction log
            optionalContribution.ifPresent(contrib -> saveTransactionLog(savedContributionPayment, memberWrapper, contrib, groupAccount.get(0), jsonObject.get("37").getAsString()));
            return;
        }

        int amount = jsonObject.get("4").getAsInt();

        contributionSchedulePaymentRepository.findById(jsonObject.get("61").getAsLong()).ifPresentOrElse(csp -> {
            Optional<Contributions> contribution = contributionsRepository.findById(csp.getContributionId());

            contribution.ifPresentOrElse(contrib -> {
                // check if there is an outstanding contribution payment
                Optional<OutstandingContributionPayment> outstandingContributionPayment = outstandingContributionPaymentRepository.findByContributionIdAndMemberId(contrib.getId(), beneficiary.getId());

                if (outstandingContributionPayment.isEmpty() || outstandingContributionPayment.get().getDueAmount() == 0) {
                    updateContributionPayment(jsonObject, beneficiary, amount, csp, contrib, contributionPayment.get());
                    return;
                }

                // subtract remainder amount and complete the contribution
                OutstandingContributionPayment ocp = outstandingContributionPayment.get();

                GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(contrib.getMemberGroupId());

                if (groupWrapper == null) {
                    log.info("Contribution not found... {}", jsonObject);
                    return;
                }

                handleOutstandingContributionPayment(jsonObject, beneficiary, csp, contrib, ocp, groupWrapper, contributionPayment.get());
            }, () -> log.info("Group not found... on contribution payment"));
        }, () -> log.info("Could not find the contribution schedule payment... {}", jsonObject));
    }

    private void memberContributionResponse(JsonObject jsonObject, MemberWrapper memberWrapper) {
        log.info("JSON OBJECT FOR UPDATE {}", jsonObject);
        String transactionId = jsonObject.get("field37").getAsString();
        log.info("TRANSACTION ID {}", transactionId);
        ContributionPayment contributionPayment = contributionsPaymentRepository.findFirstByTransactionId(transactionId);

        if (contributionPayment == null) {
            log.info("COULD NOT FIND CONTRIBUTION PAYMENT WITH TRANSACTION ID {}", transactionId);
            return;
        }

        TransactionsLog transactionLog = transactionlogsRepo.findFirstByUniqueTransactionIdOrderByIdDesc(transactionId);

        if (transactionLog == null) {
            log.info("COULD NOT FIND TRANSACTION LOG WITH TRANSACTION ID {}", transactionId);
            return;
        }

        if (!jsonObject.get("field39").getAsString().equals("00")) {
            //TODO:: Send SMS to user to let them know the contribution was not successful
            log.info("CONTRIBUTION FAILED ------- REASON {} ", jsonObject.get("field48").getAsString());

            notificationService.sendContributionFailureMessage(memberWrapper.getPhonenumber(), memberWrapper.getFirstname(), contributionPayment.getAmount(), memberWrapper.getLanguage());

            contributionPayment.setPaymentStatus(PaymentEnum.PAYMENT_FAILED.name());
            contributionPayment.setLastModifiedDate(new Date());
            contributionPayment.setLastModifiedBy(GeneralEnums.ESB_CALLBACK.name());
            transactionLog.setStatus(PaymentEnum.PAYMENT_FAILED.name());
            transactionLog.setLastModifiedDate(new Date());
            transactionLog.setLastModifiedBy(GeneralEnums.ESB_CALLBACK.name());
            contributionsPaymentRepository.save(contributionPayment);
            transactionlogsRepo.save(transactionLog);

        } else if (Objects.equals(jsonObject.get("field39").getAsString(), "00")) {
            //TODO:: Send SMS to user to let them know the contribution was successful
            int amount = contributionPayment.getAmount();
            updateContributionPayment(jsonObject, memberWrapper, amount, null, null, contributionPayment);
        }

    }

    private void handleOutstandingContributionPayment(JsonObject jsonObject, MemberWrapper
            memberWrapper, ContributionSchedulePayment csp, Contributions contrib, OutstandingContributionPayment
                                                              ocp, GroupWrapper group, ContributionPayment contributionPayment) {
        int contributedAmount = jsonObject.get("field4").getAsInt();
        int remainder = 0;
        int dueAmount = ocp.getDueAmount();

        if (contributedAmount <= ocp.getDueAmount()) {
            // debit all the money to the outstanding payment
            remainder = ocp.getDueAmount() - contributedAmount;

            int paidAmount = ocp.getPaidAmount() + contributedAmount;

            ocp.setDueAmount(remainder);
            ocp.setPaidAmount(paidAmount);
            ocp.setLastModifiedDate(new Date());
            contributionPayment.setPaymentStatus(PaymentEnum.SENT_TO_OUTSTANDING_CONTRIBUTION.name());

            outstandingContributionPaymentRepository.save(ocp);
            contributionsPaymentRepository.save(contributionPayment);

            notificationService.sendOutstandingPaymentConfirmation(memberWrapper.getPhonenumber(), memberWrapper.getFirstname(), dueAmount, group.getName(), remainder, csp.getContributionScheduledId(), memberWrapper.getLanguage());

            return;
        }

        remainder = contributedAmount - ocp.getDueAmount();

        ocp.setDueAmount(0);
        ocp.setPaidAmount(ocp.getDueAmount() + ocp.getPaidAmount());
        ocp.setLastModifiedDate(new Date());

        outstandingContributionPaymentRepository.save(ocp);

        notificationService.sendOutstandingPaymentConfirmation(memberWrapper.getPhonenumber(), memberWrapper.getFirstname(), dueAmount, group.getName(), 0, csp.getContributionScheduledId(), memberWrapper.getLanguage());

        updateContributionPayment(jsonObject, memberWrapper, remainder, csp, contrib, contributionPayment);
    }

    private void updateContributionPayment(JsonObject jsonObject, MemberWrapper memberWrapper,
                                           int amount, ContributionSchedulePayment csp, Contributions contrib, ContributionPayment contributionPayment) {

        GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(contributionPayment.getGroupId());

        Optional<Accounts> groupAccount = accountsRepository.findGroupSavingsAccount(contributionPayment.getGroupId());

        Accounts originalAccount = groupAccount.get();
        groupAccountBalanceInquiry(groupWrapper, originalAccount);
        String cardNumber = originalAccount.getAccountdetails().replaceAll("\\b(\\d{4})(\\d{8})(\\d{4})", "$1XXXXXXXX$3");

        String firstFourChars = cardNumber.substring(0, 4);
        String lastFourChars = cardNumber.substring(9, 13);
        String maskedCardNumber = firstFourChars + "*****" + lastFourChars;

        contributionPayment.setPaymentStatus(PaymentEnum.PAYMENT_SUCCESS.name());
        contributionPayment.setPaidIn(Double.valueOf(contributionPayment.getAmount()));
        contributionPayment.setPaidOut((double) 0);
        contributionPayment.setPaidBalance(contributionPayment.getAmount() + originalAccount.getAccountbalance());
        contributionPayment.setActualBalance(contributionPayment.getAmount() + originalAccount.getAccountbalance());

        ContributionPayment savedPayment = contributionsPaymentRepository.save(contributionPayment);

        if (savedPayment.getPaymentType().equals("loan")) {
            updateLoanDisbursed(savedPayment, memberWrapper, originalAccount);
        }
        String depositor = memberWrapper.getFirstname();
        //TODO::SEND TEXT TO MEMBER THAT HAS CONTRIBUTED I.E., BENEFICIARY
        if (savedPayment.getIsFine()) {
            notificationService.sendPenaltySuccessMessage(memberWrapper.getPhonenumber(), memberWrapper.getFirstname(), amount, memberWrapper.getLanguage());
            Fines fines = finesRepository.findByIdAndGroupId(savedPayment.getFineId(), savedPayment.getGroupAccountId());
            Optional<Fines> optionalFines = finesRepository.getFineFined(savedPayment.getGroupId(), savedPayment.getFineId());

            Fines fines1 = optionalFines.get();
            double fineBalance = fines1.getFineBalance();
            double paidAmount = savedPayment.getAmount();
            if (paidAmount > fineBalance) {
                double overFlow = paidAmount - fineBalance;
                fines1.setFineBalance(0.0);
                fines1.setPaymentStatus(PaymentEnum.PAID.name());
                fines1.setLastModifiedDate(new Date());
                finesRepository.save(fines1);
                recordOverflowToSavings(savedPayment, overFlow, fines1, originalAccount);
            } else if (savedPayment.getAmount() < fineBalance) {
                Double remainingBalance = fineBalance - savedPayment.getAmount();
                fines1.setFineBalance(remainingBalance);
                fines1.setPaymentStatus(PaymentEnum.PAYMENT_PENDING.name());
                fines1.setLastModifiedDate(new Date());
                finesRepository.save(fines1);
            } else if (savedPayment.getAmount() == fineBalance) {
                fines1.setFineBalance(0.0);
                fines1.setPaymentStatus(PaymentEnum.PAID.name());
                fines1.setLastModifiedDate(new Date());
                finesRepository.save(fines1);
            }
        }
        //todo::handle first deposit to officials
        int checkFirstDeposit = contributionsPaymentRepository.countAllByGroupIdAndContributionIdAndPaymentStatusAndSoftDeleteFalse(savedPayment.getGroupId(), savedPayment.getContributionId(), PaymentEnum.PAYMENT_SUCCESS.name());
        if (checkFirstDeposit == 1) {
            sendFirstDepositSmsToOfficials(savedPayment);
        }

        //TODO::send sms notification to official
        sendDepositSmsToGroupMembers(savedPayment.getGroupId(), savedPayment.getAmount(), maskedCardNumber, savedPayment.getTransactionId(), depositor);
    }

    private void checkContributionOverPayment(MemberWrapper memberWrapper, String esbTrxCode, Contributions contrib,
                                              int amountContributed, ContributionSchedulePayment csp) {


        Integer totalContributed = contributionsPaymentRepository.getTotalMemberContributionsForScheduledPayment(csp.getContributionScheduledId(), memberWrapper.getPhonenumber());
        log.info("Upcoming contribution {}", totalContributed);

        totalContributed += amountContributed;
        log.info("Upcoming contribution{}", contrib.getContributionAmount());
        log.info("amountContributed{}", amountContributed);
        log.info("totalContributed += amountContributed{}", totalContributed);


        if (totalContributed > contrib.getContributionAmount()) {
            log.info("Over contribution detected...");

            createOrUpdateContributionOverPayment(memberWrapper, esbTrxCode, contrib, amountContributed, totalContributed);
        } else {
            log.info("Over contribution not detected...");
        }
    }

    private void createOrUpdateContributionOverPayment(MemberWrapper memberWrapper, String
            esbTrxCode, Contributions contrib, int amountContributed, Integer totalContributed) {

        Optional<OverpaidContribution> overpaidContributionOptional = overpaidContributionRepository.findByMemberIdAndContributionId(memberWrapper.getId(), contrib.getId());

        if (overpaidContributionOptional.isEmpty()) {
            // create a new overpaid contribution
            OverpaidContribution overpaidContribution = new OverpaidContribution();
            overpaidContribution.setContributionId(contrib.getId());
            overpaidContribution.setMemberId(memberWrapper.getId());
            overpaidContribution.setGroupId(contrib.getMemberGroupId());
            overpaidContribution.setLastEsbTransactionCode(esbTrxCode);
            overpaidContribution.setPhoneNumber(memberWrapper.getPhonenumber());
            overpaidContribution.setAmount((double) (totalContributed - contrib.getContributionAmount()));

            overpaidContributionRepository.save(overpaidContribution);
        } else {
            log.info("Updating over contribution... Amount => KES {}", (totalContributed - contrib.getContributionAmount()));
            OverpaidContribution overpaidContribution = overpaidContributionOptional.get();
            double newAmount = totalContributed - (double) contrib.getContributionAmount();
            log.info("Updating over contribution... Amount => KES {}", newAmount);
            overpaidContribution.setAmount(newAmount);
            overpaidContribution.setLastModifiedDate(new Date());
            overpaidContribution.setLastEsbTransactionCode(esbTrxCode);

            overpaidContributionRepository.save(overpaidContribution);
        }
    }

    @Async
    public void saveTransactionLog(ContributionPayment savedPayment, MemberWrapper memberWrapper, Contributions
            contribution, Accounts accounts, String transactionId) {
        TransactionsLog transactionsLog = TransactionsLog.builder().contributionNarration(String.format("Member contribution by %s %s of amount Kes. %d", memberWrapper.getFirstname(), memberWrapper.getLastname(), savedPayment.getAmount())).contributions(contribution).status(savedPayment.getPaymentStatus()).transamount(savedPayment.getAmount()).creditaccounts(accounts).debitphonenumber(memberWrapper.getPhonenumber()).uniqueTransactionId(transactionId).transactionType(TransactionType.CREDIT.name()).build();
        transactionlogsRepo.save(transactionsLog);
    }

    private void saveLoanRepaymentTransactionLog(LoansRepayment loansRepayment, MemberWrapper memberWrapper,
                                                 long groupId, String transactionId) {
        List<Accounts> groupAccount = accountsRepository.findByGroupIdAndActive(groupId, true);

        if (groupAccount.isEmpty()) return;

        Optional<Contributions> contributions = contributionsRepository.findByMemberGroupIdAndSoftDeleteFalse(groupId);

        if (contributions.isEmpty()) return;
        TransactionsLog transactionsLog = TransactionsLog.builder().contributionNarration(String.format("Loan repayment by %s %s of amount Kes. %.2f", memberWrapper.getFirstname(), memberWrapper.getLastname(), loansRepayment.getAmount())).contributions(contributions.get()).transamount(loansRepayment.getAmount()).creditaccounts(groupAccount.get(0)).uniqueTransactionId(transactionId).debitphonenumber(memberWrapper.getPhonenumber()).build();
        transactionlogsRepo.save(transactionsLog);
    }

    @Async
    public void sendLoanRepaymentSmsToMembers(Long groupId, String memberPhoneNumber, String groupName, String
            memberName, int amountPaid) {
        Flux<Pair<String, String>> fluxMembersLanguageAndPhonesInGroup = chamaKycService.getFluxMembersLanguageAndPhonesInGroup(groupId);

        fluxMembersLanguageAndPhonesInGroup.toStream().filter(pair -> !Objects.equals(pair.getFirst(), memberPhoneNumber)).forEach(pair -> notificationService.sendLoanRepaymentSuccessText(pair.getFirst(), memberName, groupName, amountPaid, pair.getSecond()));
    }

    private void saveWithdrawalLog(WithdrawalsPendingApproval withdrawal, GroupWrapper group, MemberWrapper
            memberWrapper, String transactionId, String paymentStatus) {
        List<Accounts> groupAccount = accountsRepository.findByGroupIdAndActive(group.getId(), true);

        if (groupAccount.isEmpty())
            return;
        Accounts accounts = groupAccount.get(0);
        Optional<Contributions> checkContribution = contributionsRepository.findByGroupIdAndActiveTrueAndSoftDeleteFalse(group.getId());

        if (checkContribution.isEmpty())
            return;
        Contributions contributions = checkContribution.get();

        WithdrawalLogs withdrawalLogs = WithdrawalLogs.builder()
                .contribution_narration(String.format("Member contribution withdrawal by %s %s of amount Kes. %s", memberWrapper.getFirstname(), memberWrapper.getLastname(), withdrawal.getAmount())).transamount(withdrawal.getAmount())
                .memberGroupId(group.getId())
                .debitAccounts(accounts)
                .uniqueTransactionId(transactionId)
                .creditphonenumber(withdrawal.getPhonenumber())
                .creditCoreAccount(withdrawal.getCoreAccount())
                .contributions(contributions)
                .capturedby(withdrawal.getCapturedByPhoneNumber())
                .transferToUserStatus(paymentStatus)
                .build();
        log.info("Saving withdrawal log...");
        withdrawallogsRepo.save(withdrawalLogs);
    }

    @Async
    public void sendWithdrawalMessageToGroup(String phonenumber, String memberName, GroupWrapper group,
                                             double amount) {

        chamaKycService.getFluxMembersLanguageAndPhonesInGroup(group.getId()).filter(pair -> !Objects.equals(pair.getFirst(), phonenumber)).subscribe(pair -> {
            notificationService.sendContributionWithdrawalToGroup(pair.getFirst(), memberName, group.getName(), amount, pair.getSecond());
        });
    }

    @Async
    public void sendContributionTextToMembers(MemberWrapper memberWrapper, Long groupId, Integer amount, Boolean
            isPenalty) {
        String memberName = String.format("%s %s", memberWrapper.getFirstname(), memberWrapper.getLastname());
        String groupName = chamaKycService.getMonoGroupNameByGroupId(groupId);

        if (groupName == null) {
            log.info("Group name not found....");
            return;
        }

        chamaKycService.getFluxMembersLanguageAndPhonesInGroup(groupId).filter(pair -> !Objects.equals(pair.getFirst(), memberWrapper.getPhonenumber())).subscribe(pair -> {
            if (isPenalty)
                notificationService.sendPenaltySuccessMessageToGroup(pair.getFirst(), memberName, groupName, amount, pair.getSecond());
            else
                notificationService.sendContributionSuccessMessageToGroup(pair.getFirst(), memberName, groupName, amount, pair.getSecond());
        });
    }

    /**
     * Send texts to group members to notify them of
     * a loan disbursement.
     *
     * @param memberWrapper that has member data
     * @param groupId       the group that gave the loan
     * @param amount        the amount of the loan
     */
    @Async("threadPoolTaskExecutor")
    public void sendLoanDisbursementTextToMembers(MemberWrapper memberWrapper, Long groupId, Double amount) {
        String memberName = String.format("%s %s", memberWrapper.getFirstname(), memberWrapper.getLastname());
        String groupName = chamaKycService.getMonoGroupNameByGroupId(groupId);

        if (groupName == null) {
            log.info("Group name not found...");
            return;
        }
        chamaKycService.getFluxMembersLanguageAndPhonesInGroup(groupId).toStream().filter(pair -> !Objects.equals(pair.getFirst(), memberWrapper.getPhonenumber())).forEach(pair -> notificationService.sendLoanDisbursementTextToGroup(pair.getFirst(), memberName, groupName, amount, pair.getSecond()));
    }

    /**
     * Creates a group's account from Kafka Event
     *
     * @param accountInfo that contains the id, name and available balance
     */
    @Override
    public void createGroupAccount(String accountInfo) {
        log.info("Group account info <CONSUMER>... {} ", accountInfo);
        JsonObject jsonObject = gson.fromJson(accountInfo, JsonObject.class);
        AccountType accountType = accountTypeRepository.findFirstByAccountPrefixAndSoftDeleteFalse("MGA");
        if (accountType == null) {
            log.info("Group Account type not found!");
            return;
        }

        Accounts account = accountsRepository.findTopByGroupIdAndActiveTrueAndSoftDeleteFalseOrderByIdDesc(jsonObject.get("id").getAsLong());

        if (account == null) {
            log.info("GROUP ACCOUNT NOT FOUND FOR {} ", jsonObject.get("name").getAsString());
            long groupId = jsonObject.get("id").getAsLong();
            String accountName = jsonObject.get("name").getAsString();
            double availableBalance = jsonObject.get("availableBalance").getAsDouble();
            double actualBalance = jsonObject.get("availableBalance").getAsDouble();
            String phoneNumber = jsonObject.get("phoneNumber").getAsString();
            String accountNumber = jsonObject.get("accountNumber").getAsString();
            Accounts accounts = new Accounts(groupId, accountName, actualBalance, availableBalance, phoneNumber, accountNumber, accountType);
            Accounts createAccount = accountsRepository.save(accounts);
            updateLoanProduct(createAccount);
        } else {
            log.info("GROUP ACCOUNT FOUND FOR {} ", jsonObject.get("name").getAsString());
            account.setGroupId(jsonObject.get("id").getAsLong());
            account.setName(jsonObject.get("name").getAsString());
            account.setPhoneNumber(jsonObject.get("phoneNumber").getAsString());
            account.setAccountdetails(jsonObject.get("accountNumber").getAsString());
            account.setAvailableBal(jsonObject.get("availableBalance").getAsDouble());
            account.setAccountbalance(jsonObject.get("availableBalance").getAsDouble());
            Accounts updateAccounts = accountsRepository.save(account);
            updateLoanProduct(updateAccounts);
        }

    }

    private void updateLoanProduct(Accounts accounts) {
        Accounts account = accountsRepository.findTopByGroupIdAndActiveTrueAndSoftDeleteFalseOrderByIdDesc(accounts.getGroupId());
        if (!(account == null)) {
            LoanProducts loanProducts = loanproductsRepository.findTopByGroupIdAndIsActiveTrueAndSoftDeleteFalseOrderByIdDesc(account.getGroupId());
            if (!(loanProducts == null)) {
                loanProducts.setDebitAccountId(account);
                loanProducts.setLastModifiedDate(new Date());
                loanProducts.setLastModifiedBy("SYSTEM");
                loanproductsRepository.save(loanProducts);
            }
        }
    }

    @Override
    public void createGroupContribution(String contributionInfo) {
        log.info("GROUP CONTRIBUTION INFO... {}", contributionInfo);
        JsonObject jsonObject = gson.fromJson(contributionInfo, JsonObject.class);
        AmountType amountType = amounttypeRepo.findAmountName();
        if (amountType == null) {
            log.info("******************AMOUNT TYPE CONFIGURATION NOT SET******************");
            return;
        }
        ScheduleTypes scheduleTypes = scheduleTypesRepository.findByNameIgnoreCaseAndSoftDeleteFalse("Monthly");
        if (scheduleTypes == null) {
            log.info("******************SCHEDULE TYPE CONFIGURATION NOT SET*********************");
            return;
        }
        ContributionType contributionType = contributionTypesRepository.findContributionType();
        if (contributionType == null) {
            log.info("******************CONTRIBUTION TYPE CONFIGURATION NOT SET******************");
            return;
        }
        Contributions contributions = new Contributions();
        contributions.setName(jsonObject.get("contributionName").getAsString());
        contributions.setCreatedBy(jsonObject.get("createdBy").getAsString());
        contributions.setGroupId(jsonObject.get("groupId").getAsLong());
        contributions.setAmountType(amountType);
        contributions.setContributiondetails("Contribution".concat("-") + jsonObject.get("contributionName").getAsString());
        contributions.setScheduleType(scheduleTypes);
        contributions.setPenalty(jsonObject.get("penalty").getAsDouble());
        contributions.setContributionType(contributionType);
        contributions.setContributionAmount(jsonObject.get("contributionAmount").getAsDouble());
        contributions.setWelfareAmt(jsonObject.get("welfareAmount").getAsDouble());
        contributions.setFrequency(jsonObject.get("frequency").getAsString());
        contributions.setLoanInterest(jsonObject.get("loanInterestRate").getAsString());
        contributions.setDaysBeforeDue(jsonObject.get("daysBeforeDue").getAsString());
        contributions.setActive(true);
        contributions.setCreatedOn(new Date());
        contributions.setLastModifiedDate(new Date());
        contributions.setLastModifiedBy("SYSTEM");
        Contributions savedContribution = contributionsRepository.save(contributions);
        createLoanProduct(jsonObject, savedContribution);
    }

    public void createLoanProduct(JsonObject jsonObject, Contributions savedContribution) {
        Optional<Accounts> accountsOptional = accountsRepository.findDefaultAccount();
        if (accountsOptional.isEmpty()) {
            log.info("******************DEFAULT ACCOUNT NOT SET******************");
            return;
        }
        ScheduleTypes scheduleTypes = scheduleTypesRepository.findByNameIgnoreCaseAndSoftDeleteFalse("Monthly");
        if (scheduleTypes == null) {
            log.info("******************SCHEDULE TYPE NOT SET******************");
            return;
        }
        LoanProducts loanProduct = loanproductsRepository.findTopByGroupIdAndIsActiveTrueAndSoftDeleteFalseOrderByIdDesc(savedContribution.getGroupId());
        if (loanProduct == null) {
            LoanProducts loanProducts = new LoanProducts();
            loanProducts.setContributions(savedContribution);
            loanProducts.setInteresttype("Simple");
            loanProducts.setGroupId(jsonObject.get("groupId").getAsLong());
            loanProducts.setProductname((jsonObject.get("contributionName").getAsString()) + "-Loan");
            loanProducts.setDescription((jsonObject.get("contributionName").getAsString()) + "-Loan");
            loanProducts.setMax_principal(999999);
            loanProducts.setMin_principal(1);
            loanProducts.setInterestvalue(1);
            loanProducts.setPaymentperiodtype(scheduleTypes.getName());
            loanProducts.setPaymentperiod(1);
            loanProducts.setGracePeriod(0);
            loanProducts.setPenalty(false);
            loanProducts.setPenaltyValue(0);
            loanProducts.setIsPercentagePercentage(false);
            loanProducts.setUserSavingValue(0);
            loanProducts.setIsActive(true);
            loanProducts.setGuarantor(false);
            loanProducts.setDebitAccountId(accountsOptional.get());
            loanProducts.setCreatedOn(new Date());
            loanProducts.setLastModifiedDate(new Date());
            loanProducts.setLastModifiedBy("SYSTEM");
            LoanProducts saveLoanProduct = loanproductsRepository.save(loanProducts);
            log.info("++++LOAN PRODUCT ADDED+++::: GROUP ID FROM CONTRIBUTION {}, LOAN PRODUCT GROUP ID {}, PRODUCT NAME {}", savedContribution.getGroupId(), saveLoanProduct.getGroupId(), saveLoanProduct.getProductname());
        }
    }

    private void createInitialContribution(Contributions savedContribution, String initialAmount) {
        ContributionPayment contributionPayment = ContributionPayment.builder().contributionId(savedContribution.getId()).amount(Integer.parseInt(initialAmount)).groupAccountId(0L).isCombinedPayment(false).paymentStatus(PaymentEnum.PAYMENT_SUCCESS.name()).phoneNumber("071111111111").build();
        contributionPayment.setCreatedBy("SYSTEM");
        contributionsPaymentRepository.save(contributionPayment);
    }

    @Override
    public Mono<UniversalResponse> kitTransferPendingApprovals(PendingApprovalsWrapper request, String user) {
        return Mono.fromCallable(() -> {
            Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
            Page<KitTransferPendingApprovals> groupsPage = kitTransferPendingApprovalsRepository.findAllByGroupIdAndPendingTrueAndSoftDeleteFalseOrderByIdDesc(request.getGroupId(), pageable);
            List<PaymentsRepostWrapper> repostWrapperList = groupsPage.getContent()
                    .parallelStream()
                    .map(this::getPaymentsRepostWrapper)
                    .collect(Collectors.toList());
            UniversalResponse response = new UniversalResponse("success", getResponseMessage("kitTransferPendingApprovals"), repostWrapperList);
            response.setMetadata(Map.of("numofrecords", repostWrapperList.size()));
            return response;
        }).publishOn(Schedulers.boundedElastic());
    }

    private PaymentsRepostWrapper getPaymentsRepostWrapper(KitTransferPendingApprovals approvals) {
        return PaymentsRepostWrapper.builder()
                .id(approvals.getId())
                .amount(approvals.getAmount())
                .paymentType(approvals.getPaymentType())
                .sourceAccount(approvals.getSourceAccount())
                .destinationAccount(approvals.getDestianationAccount())
                .paymentStatus(approvals.getPaymentStatus())
                .transactionId(approvals.getTransactionId())
                .creator(approvals.getCreator())
                .creatorPhoneNumber(approvals.getCreatorPhoneNumber())
                .narration(approvals.getNarration())
                .groupId(approvals.getGroupId())
                .transactionDate(approvals.getTransactionDate()).build();
    }

    @Override
    public Mono<UniversalResponse> approveKitTransfer(KitTransferApprovalDto request, String approvedBy) {
        return Mono.fromCallable(() -> {
            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(request.getGroupId());
            if (groupWrapper == null)
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"));
            MemberWrapper approver = chamaKycService.searchMonoMemberByPhoneNumber(approvedBy);
            if (approver == null)

                return new UniversalResponse("fail", getResponseMessage("approverNotFound"));

            Accounts accounts = accountsRepository.findByGroupIdAndActiveTrueAndSoftDeleteFalse(groupWrapper.getId());
            if (accounts == null) {
                return new UniversalResponse("fail", getResponseMessage("groupHasNoAccount"));
            }
            KitTransferPendingApprovals kitTransferPendingApprovals = kitTransferPendingApprovalsRepository.findKitTransferPendingApprovalsByIdAndSoftDeleteFalseAndPendingTrue(request.getRequestId());

            if (kitTransferPendingApprovals == null)
                return new UniversalResponse("fail", getResponseMessage("kitTransferNotPendingOrNotFound"));

            GroupMemberWrapper groupMembership = chamaKycService.getMonoGroupMembershipByGroupIdAndMemberId(request.getGroupId(), approver.getId());
            if (groupMembership == null)
                return new UniversalResponse("fail", getResponseMessage("memberIsNotPartOfGroup"));

            if (approvedBy.equals(kitTransferPendingApprovals.getCreator()))
                return new UniversalResponse("fail", getResponseMessage("kitTransferApproverCanNotBeCreator"));

            ///new version
            JsonObject approvals = gson.fromJson(kitTransferPendingApprovals.getApprovedBy(), JsonObject.class);

            if (approvals.has(groupMembership.getTitle()))
                return new UniversalResponse("fail", getResponseMessage("youCannotAuthorizeMoreThanOnce"));

            if (groupMembership.getTitle().equals("member"))

                return new UniversalResponse("fail", getResponseMessage("accountsOfficialsApproval"));

            approvals.addProperty(groupMembership.getTitle(), approver.getId());
            String creator = kitTransferPendingApprovals.getCreator();
            String creatorPhoneNumber = kitTransferPendingApprovals.getCreatorPhoneNumber();

            if (!request.getApprove()) {
                kitTransferPendingApprovals.setApproved(true);
                kitTransferPendingApprovals.setPending(false);
                kitTransferPendingApprovals.setApprovalCount(kitTransferPendingApprovals.getApprovalCount() + 1);
                kitTransferPendingApprovals.setApprovedBy(approvals.toString());
                kitTransferPendingApprovals.setDeclinedBy(approvedBy);
                kitTransferPendingApprovalsRepository.save(kitTransferPendingApprovals);
                sendKitTransferDeclineSmsToOfficials(groupWrapper, creator, creatorPhoneNumber);
                auditTrail("kitty transfer", "kit transfer approval decline successfully", approvedBy);
                creatNotification(request.getGroupId(), groupWrapper.getName(), "kit transfer approval decline by " + approvedBy);

                return new UniversalResponse("success", getResponseMessage("kitTransferDeclined"));
            }

            kitTransferPendingApprovals.setApprovalCount(kitTransferPendingApprovals.getApprovalCount() + 1);
            kitTransferPendingApprovals.setApprovedBy(approvals.toString());
            kitTransferPendingApprovals.setApproved(false);
            kitTransferPendingApprovals.setPending(true);
            kitTransferPendingApprovals.setPaymentStatus(PaymentEnum.PAYMENT_PENDING.name());

            kitTransferPendingApprovalsRepository.save(kitTransferPendingApprovals);
            if (kitTransferPendingApprovals.getApprovalCount() > 1) {
                kitTransferPendingApprovals.setApproved(true);
                kitTransferPendingApprovals.setPending(false);
                kitTransferPendingApprovals.setPaymentStatus(PaymentEnum.PAYMENT_SUCCESS.name());
                KitTransferPendingApprovals contributionPayment = kitTransferPendingApprovalsRepository.save(kitTransferPendingApprovals);
                //todo:: Perform Actual Payments
                kitTransferFrom(contributionPayment, accounts);
                kitTransferTo(contributionPayment, accounts);
                sendKitTransferAcceptedSmsToOfficials(groupWrapper, kitTransferPendingApprovals, approver);
                auditTrail("kitty transfer", "kit transfer approved successfully", approvedBy);
                creatNotification(request.getGroupId(), groupWrapper.getName(), "kit transfer approved by " + approvedBy);
                return new UniversalResponse("success", getResponseMessage("approvalSuccessful"));
            }
            return new UniversalResponse("success", String.format(getResponseMessage("initialApprovalSuccessful")));
        }).publishOn(Schedulers.boundedElastic());
    }

    @Async("threadPoolTaskExecutor")

    protected void sendKitTransferDeclineSmsToOfficials(GroupWrapper groupWrapper, String creator, String
            creatorPhoneNumber) {
        GroupWrapper group = chamaKycService.getMonoGroupById(groupWrapper.getId());
        if (group != null && group.isActive()) {

            chamaKycService.getFluxGroupMembers(group.getId())
                    .subscribe(member -> notificationService.sendKittyTransferDeclineText(member.getFirstname(), creator, group.getName(), member.getPhonenumber(), member.getLanguage()));
        } else {
            log.error("Could not send Kit Transfer SMS. Group not found.");
        }

    }


    @Async
    protected void sendKitTransferAcceptedSmsToOfficials(GroupWrapper groupWrapper, KitTransferPendingApprovals
            payment, MemberWrapper approver) {
        GroupWrapper group = chamaKycService.getMonoGroupById(groupWrapper.getId());
        if (group != null && group.isActive()) {
            String memberName = String.format("%s %s", approver.getFirstname(), approver.getLastname());
            String language = approver.getLanguage();
            String groupName = group.getName();
            double amount = payment.getAmount();
            chamaKycService.getGroupOfficials(group.getId())
                    .subscribe(member -> notificationService.sendKittyTransferAcceptedText(member.getFirstname(), memberName, groupName, amount, member.getPhonenumber(), language));
        } else {
            log.error("Could not send Kit Transfer SMS. Group not found.");
        }

    }

    @Override
    public Mono<UniversalResponse> approveWithdrawalRequest(WithdrawalApprovalRequest request, String approvedBy) {
        return Mono.fromCallable(() -> {
            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(request.getGroupId());
            if (groupWrapper == null)
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"));

            MemberWrapper approver = chamaKycService.searchMonoMemberByPhoneNumber(approvedBy);

            if (approver == null)
                return new UniversalResponse("fail", getResponseMessage("approverNotFound"));

            Optional<WithdrawalsPendingApproval> withdrawalPendingApproval = withdrawalspendingapprovalRepo.findByIdAndPendingTrue(request.getRequestId());

            if (withdrawalPendingApproval.isEmpty()) {
                return new UniversalResponse("fail", getResponseMessage("withdrawalNotPendingOrNotFound"));
            }

            WithdrawalsPendingApproval withdrawal = withdrawalPendingApproval.get();

            MemberWrapper member = chamaKycService.searchMonoMemberByPhoneNumber(withdrawal.getCapturedByPhoneNumber());

            if (member == null)
                return new UniversalResponse("fail", getResponseMessage("memberNotFound"));

            String memberName = String.format("%s %s", member.getFirstname(), member.getLastname());

            String approverName = approver.getFirstname();
            String creator = withdrawal.getCapturedByPhoneNumber();

            if (Objects.equals(approver.getPhonenumber(), creator))
                return new UniversalResponse("fail", getResponseMessage("cannotApproveOwnWithdrawal"));

            GroupMemberWrapper groupMembership = chamaKycService.getMonoGroupMembershipByGroupIdAndMemberId(request.getGroupId(), approver.getId());
            if (groupMembership == null) {
                return new UniversalResponse("fail", getResponseMessage("memberIsNotPartOfGroup"));
            }

            //todo :: approver
            JsonObject approvals = gson.fromJson(withdrawal.getApprovedby(), JsonObject.class);

            if (approvals.has(groupMembership.getTitle()))
                return new UniversalResponse("fail", getResponseMessage("youCannotAuthorizeMoreThanOnce"));

            if (groupMembership.getTitle().equals("member"))

                return new UniversalResponse("fail", getResponseMessage("accountsOfficialsApproval"));

            approvals.addProperty(groupMembership.getTitle(), approver.getId());

            if (!request.getApprove()) {
                withdrawal.setApproved(true);
                withdrawal.setPending(false);
                withdrawal.setStatus("Approved");
                approvals.addProperty(groupMembership.getTitle(), approver.getId());
                withdrawal.setApprovalCount(withdrawal.getApprovalCount() + 1);
                withdrawal.setApprovedby(approvals.toString());
                withdrawal.setPaymentStatus(PaymentEnum.PAYMENT_FAILED.name());
                withdrawalspendingapprovalRepo.save(withdrawal);
                sendDeclinedWithdrawRequestTextToMembers(groupWrapper.getId(), groupWrapper.getName(), approverName, memberName, withdrawal.getAmount(), member.getPhonenumber(), member.getLanguage());
                auditTrail("group withdrawal", "group withdrawal requested by member " + member.getPhonenumber() + " in group " + groupWrapper.getName() + " declined successfully", approver.getImsi());
                creatNotification(groupWrapper.getId(), groupWrapper.getName(), "group withdrawal request declined by" + approvedBy);
                return new UniversalResponse("success", getResponseMessage("successfullyDeclinedWithdrawal"));
            }
            withdrawal.setApprovalCount(withdrawal.getApprovalCount() + 1);
            withdrawal.setApprovedby(approvals.toString());
            withdrawal.setStatus("Pending");
            withdrawal.setPending(true);
            withdrawal.setApproved(false);

            String message = getResponseMessage("initialApprovalSuccessful");
            withdrawal.setStatus(message);

            withdrawalspendingapprovalRepo.save(withdrawal);
            if (withdrawal.getApprovalCount() > 1) {
                double amount = withdrawal.getAmount();
                String accountToDeposit = withdrawal.getCoreAccount() == null || withdrawal.getCoreAccount().isBlank() ? withdrawal.getPhonenumber() : withdrawal.getCoreAccount();
                withdrawal.setApproved(true);
                withdrawal.setPending(false);
                withdrawal.setStatus("Approved");
                withdrawal.setPaymentStatus(PaymentEnum.KIT_TRANSFER_SUCCESS.name());
                withdrawalspendingapprovalRepo.save(withdrawal);

                String groupCbsAccount = groupWrapper.getCsbAccount();
                log.info("WITHDRAW CONTRIBUTIONS... FROM ACCOUNT => {} TO => {}", groupCbsAccount, accountToDeposit);

                String chargeRef = TransactionIdGenerator.generateTransactionId("CHG");
                String accountRef = member.getPhonenumber();

                Map<String, String> esbChargeRequest = constructChargesBody(
                        accountRef,
                        (int) amount,
                        chargeRef,
                        ChargeType.MCHAMA_WITHDRAWAL.name()
                );

                String esbChargeBody = gson.toJson(esbChargeRequest);

                log.info("CHANNEL API CHARGE REQUEST {}", esbChargeRequest);

                String chargeRes = postBankWebClient.post()
                        .uri(postBankUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(esbChargeBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .subscribeOn(Schedulers.boundedElastic())
                        .block();

                JsonObject chargeJsonObject = gson.fromJson(chargeRes, JsonObject.class);
                log.info("CHANNEL API CHARGES RESPONSE {}", chargeRes);

                if (!chargeJsonObject.get("field39").getAsString().equals("00")) {
                    log.info("CHANNEL API CHARGES FAILURE REASON... {}", chargeJsonObject.get("field48").getAsString());
                    return new UniversalResponse("success", getResponseMessage("failedToGetCharges"));
                }

                Integer chargeAmount = chargeJsonObject.get("chargeamount").getAsInt();

                Accounts account = accountsRepository.findByGroupIdAndActiveTrueAndSoftDeleteFalse(groupWrapper.getId());
                if (account == null) {
                    return new UniversalResponse("success", getResponseMessage("groupAccountInfo"));
                }

                groupAccountBalanceInquiry(groupWrapper, account);

                withdrawContributions(withdrawal, approverName, groupWrapper, accountToDeposit, (int) amount, chargeAmount, member, account);
            }
            if (withdrawal.getApprovalCount() >= 2) {
                return new UniversalResponse("success", getResponseMessage("withdrawalApprovalSuccess"));
            }

            return new UniversalResponse("success", getResponseMessage("initialApprovalSuccessful"));
        }).publishOn(Schedulers.boundedElastic());
    }

    private void withdrawContributions(WithdrawalsPendingApproval withdrawal, String approver, GroupWrapper
            groupWrapper, String accountToDeposit, int amount, Integer chargeAmount, MemberWrapper member, Accounts account) {
        String transactionId = TransactionIdGenerator.generateTransactionId("CWT");
        String phoneNumber = member.getPhonenumber();
        String memberName = String.format("%s %s", member.getFirstname(), member.getLastname());
        String language = member.getLanguage();
        String groupName = groupWrapper.getName();
        boolean mpesa;

        if (accountToDeposit.length() < 13) {
            mpesa = true;
            log.info("ITS WITHDRAWAL TO MPESA ACCOUNT {}", accountToDeposit);
        } else {
            log.info("ITS WITHDRAWAL CORE ACCOUNT {}", accountToDeposit);
            mpesa = false;
        }
        if (mpesa) {
            String phoneScope = "MW";
            Map<String, String> esbRequest = constructBody(
                    groupWrapper.getCsbAccount(), accountToDeposit,
                    accountToDeposit, amount, transactionId,
                    phoneScope, String.valueOf(chargeAmount));
            String body = gson.toJson(esbRequest);
            log.info("CHECK OUT[MPESA] WITHDRAWAL REQUEST {} , MEMBER PHONE NUMBER {}", body, accountToDeposit);
            saveWithdrawalLog(withdrawal, groupWrapper, member, transactionId, PaymentEnum.PAYMENT_PENDING.name());

            try {
                String withdrawalResponse = postBankWebClient.post()
                        .uri(postBankUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(String.class)
                        .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                        .subscribeOn(Schedulers.boundedElastic())
                        .block();

                JsonObject withdrawalJsonObject = gson.fromJson(withdrawalResponse, JsonObject.class);
                log.info("CHECK OUT[MPESA] WITHDRAWAL RESPONSE {}", withdrawalJsonObject);

                if (!withdrawalJsonObject.get("field39").getAsString().equals("00")) {
                    log.info("CHECK OUT[MPESA] WITHDRAWAL FAILURE REASON... {}", withdrawalJsonObject.get("field48").getAsString());
                    String WAWithdrawalFailure = withdrawalJsonObject.get("field48").getAsString();
                    notificationService.sendWithdrawalRequestFailureText(phoneNumber, memberName, WAWithdrawalFailure, member.getLanguage());
                    esbLoggingService.logESBRequest(body, phoneScope);
                    return;
                }

                if (withdrawalJsonObject.get("field39").getAsString().equals("00")) {
                    log.info("CHECK OUT[MPESA] WITHDRAWAL RESPONSE ON SUCCESS {}", withdrawalJsonObject);
                    updateContributionWithdrawal(withdrawal, account);
                    updateWithdrawalLog(withdrawal, transactionId, amount, account, accountToDeposit);
                    sendApprovedMemberWithdrawalText(groupWrapper.getId(), groupName, phoneNumber, memberName, amount, language);
                    esbLoggingService.logESBRequest(body, phoneScope);
                }
            } catch (Exception e) {
                throw new RuntimeException("CATCHED[EXCEPTION] ERROR MESSAGE {}", e);
            }
        }
        if (!mpesa) {
            String accountScope = "MWC";
            Map<String, String> esbRequest = constructBody(
                    groupWrapper.getCsbAccount(), member.getPhonenumber(),
                    accountToDeposit, amount, transactionId,
                    accountScope, String.valueOf(chargeAmount));
            String body = gson.toJson(esbRequest);
            log.info("IFT WITHDRAWAL REQUEST {}, MEMBER CORE ACCOUNT {}", body, accountToDeposit);
            saveWithdrawalLog(withdrawal, groupWrapper, member, transactionId, PaymentEnum.PAYMENT_PENDING.name());
            try {
                String withdrawalResponse = postBankWebClient.post()
                        .uri(postBankUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(String.class)
                        .subscribeOn(Schedulers.boundedElastic())
                        .block();

                JsonObject withdrawalJsonObject = gson.fromJson(withdrawalResponse, JsonObject.class);
                log.info("IFT WITHDRAWAL RESPONSE ... {}", withdrawalJsonObject);
                if (!withdrawalJsonObject.get("field39").getAsString().equals("00")) {
                    log.info("IFT WITHDRAWAL FAILURE REASON... {}", withdrawalJsonObject.get("field48").getAsString());
                    String WAWithdrawalFailure = withdrawalJsonObject.get("field48").getAsString();
                    //TODO::SEND CBS FAILURE MESSAGE TO MEMBER
                    notificationService.sendWithdrawalRequestFailureText(phoneNumber, memberName, WAWithdrawalFailure, member.getLanguage());
                    esbLoggingService.logESBRequest(body, accountScope);
                }
                if (withdrawalJsonObject.get("field39").getAsString().equals("00")) {
                    log.info("IFT WITHDRAWAL RESPONSE ON SUCCESS {}", withdrawalJsonObject);
                    updateContributionWithdrawal(withdrawal, account);
                    updateWithdrawalLog(withdrawal, transactionId, amount, account, accountToDeposit);
                    sendApprovedMemberWithdrawalText(groupWrapper.getId(), groupName, phoneNumber, memberName, amount, language);
                    esbLoggingService.logESBRequest(body, accountScope);
                }
            } catch (Exception e) {
                throw new RuntimeException("CATCHED[EXCEPTION] ERROR MESSAGE {}", e);
            }
        }
    }

    @Async
    protected void sendApprovedMemberWithdrawalText(long id, String groupName, String phoneNumber, String
            memberName, int amount, String language) {
        notificationService.sendWithdrawalRequestApprovedText(phoneNumber, memberName, amount, language);

        chamaKycService.getFluxGroupMembers(id)
                .subscribe(mbr -> notificationService.sendApprovedWithdrawRequestTextToMembers(mbr.getFirstname(), memberName, groupName, amount, mbr.getPhonenumber(), mbr.getLanguage()));

    }

    @Async
    protected void sendDeclinedWithdrawRequestTextToMembers(long id, String name, String approverName, String
            memberName, double amount, String phonenumber, String language) {
        //todo:: send notification to member
        notificationService.sendWithdrawalRequestDeclineText(memberName, approverName, amount, name, phonenumber, language);
        //todo:: send notification to members
        chamaKycService.getFluxGroupMembers(id)
                .filter(gm -> !gm.getPhonenumber().equals(phonenumber))
                .subscribe(mbr -> notificationService.sendMembersDeclinedWithdrawRequestText(mbr.getFirstname(), approverName, memberName, name, amount, mbr.getPhonenumber(), mbr.getLanguage()));
    }


    private void updateContributionWithdrawal(WithdrawalsPendingApproval withdrawals, Accounts account) {
        String transactionId = TransactionIdGenerator.generateTransactionId("CWT");
        ContributionPayment payment = new ContributionPayment();
        payment.setGroupAccountId(withdrawals.getAccount().getId());
        payment.setGroupId(withdrawals.getGroupId());
        payment.setContributionId(withdrawals.getContribution().getId());
        payment.setAmount((int) withdrawals.getAmount());
        payment.setTransactionId(transactionId);
        payment.setPaymentStatus(PaymentEnum.WITHDRAWN_AND_DISBURSED.name());
        payment.setTransactionDate(new Date());
        payment.setPaymentType("saving");
        payment.setActualBalance(account.getAccountbalance());
        payment.setPaidOut(withdrawals.getAmount());
        payment.setPaidIn((double) 0);
        payment.setPaidBalance(account.getAccountbalance() - withdrawals.getAmount());
        payment.setIsDebit('Y');
        payment.setIsCredit('N');
        payment.setCreatedOn(new Date());
        payment.setNarration(withdrawals.getWithdrawal_narration());
        payment.setCreatedBy(withdrawals.getCreatedBy());
        payment.setTransactionDate(new Date());
        contributionsPaymentRepository.save(payment);
        //TODO:: SAVE TRANSACTION TO AFFECT KITTY WALLET
    }

    private void updateWithdrawalLog(WithdrawalsPendingApproval withdrawal, String transactionId, int amount, Accounts account, String accountToDeposit) {
        Optional<Contributions> contributions = contributionsRepository.findByGroupIdAndActiveTrueAndSoftDeleteFalse(account.getGroupId());
        Contributions contr = contributions.get();
        WithdrawalLogs withdrawalLogs = new WithdrawalLogs();
        withdrawalLogs.setContribution_narration("mchama group withdrawal");
        withdrawalLogs.setCapturedby(withdrawal.getCapturedby());
        withdrawalLogs.setContributions(contr);
        withdrawalLogs.setUniqueTransactionId(transactionId);
        if (accountToDeposit.length() >= 13) {
            withdrawalLogs.setCreditCoreAccount(accountToDeposit);
        } else {
            withdrawalLogs.setCreditphonenumber(accountToDeposit);
        }
        withdrawalLogs.setMemberGroupId(contr.getMemberGroupId());
        withdrawalLogs.setDebitAccounts(account);
        withdrawalLogs.setTransferToUserStatus(PaymentEnum.PAYMENT_SUCCESS.name());
        withdrawalLogs.setWithdrawalreason(withdrawal.getWithdrawalreason());
        withdrawalLogs.setTransactionDate(new Date());
        withdrawalLogs.setOldbalance(account.getAccountbalance());
        withdrawalLogs.setNewbalance(account.getAccountbalance() - amount);
        withdrawallogsRepo.save(withdrawalLogs);
        String coreAccount;
        if (accountToDeposit.length() >= 13) {
            coreAccount = accountToDeposit;
        } else {
            coreAccount = " ";
        }

        ContributionPayment contributionPayment = ContributionPayment.builder()
                .amount(amount)
                .isCredit('N')
                .isDebit('Y')
                .paidOut((double) amount)
                .paidIn((double) 0)
                .coreAccount(coreAccount)
                .transactionId(transactionId)
                .groupId(account.getGroupId())
                .contributionId(contr.getId())
                .paymentType(withdrawal.getPaymentType())
                .groupAccountId(withdrawal.getGroupId())
                .actualBalance(account.getAccountbalance())
                .narration(withdrawal.getWithdrawal_narration())
                .phoneNumber(withdrawal.getCapturedByPhoneNumber())
                .paidBalance(account.getAccountbalance() - amount)
                .paymentStatus(PaymentEnum.KIT_TRANSFER_SUCCESS.name())
                .schedulePaymentId(String.valueOf(contr.getScheduleType().getId()))
                .build();
        contributionsPaymentRepository.save(contributionPayment);

    }

    @Override
    public Mono<UniversalResponse> getUserContributionPayments(String phoneNumber) {
        return Mono.fromCallable(() -> contributionsPaymentRepository.findContributionPaymentByPhoneNumber(phoneNumber)).publishOn(Schedulers.boundedElastic()).map(res -> new UniversalResponse("success", getResponseMessage("userContributionPayments"), res));
    }

    @Override
    public Mono<UniversalResponse> getUserContributionPayments(String phoneNumber, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return Mono.fromCallable(() -> contributionsPaymentRepository.findContributionPaymentByPhoneNumber(phoneNumber, pageable)).publishOn(Schedulers.boundedElastic()).map(res -> UniversalResponse.builder().status("Success").message(getResponseMessage("userContributionPayments")).data(res.getContent()).metadata(Map.of("currentPage", res.getNumber(), "numOfRecords", res.getNumberOfElements(), "totalPages", res.getTotalPages())).timestamp(new Date()).build());
    }

    @Override
    public Mono<UniversalResponse> getGroupContributionPayments(Long contributionId, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(Math.abs(page), Math.min(size, 10));
        return Mono.fromCallable(() -> contributionsPaymentRepository.findByContributionIdOrderByIdDesc(contributionId, pageable)).publishOn(Schedulers.boundedElastic()).map(res -> new UniversalResponse("success", "Member contributions", res.getContent()));
    }

    @Override
    public Mono<UniversalResponse> getUssdGroupContributionPayments(Long contributionId, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(Math.abs(page), Math.min(size, 10));
        return Mono.fromCallable(() -> contributionsPaymentRepository.findByContributionIdOrderByIdDesc(contributionId, pageable)).publishOn(Schedulers.boundedElastic()).map(pagedData -> {
            List<ContributionPayment> contributionPayments = pagedData.getContent();
            return contributionPayments.parallelStream()
                    .filter(cp -> cp.getPaymentStatus().equals(PaymentEnum.PAYMENT_SUCCESS.name()) && !Objects.isNull(cp.getPhoneNumber())).
                    map(cp -> {
                        SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
                        MemberWrapper member = chamaKycService.searchMonoMemberByPhoneNumber(cp.getPhoneNumber());
                        return formatter.format(cp.getCreatedOn()) + " " + String.format("%s %s", member.getFirstname(), member.getLastname()) + "gave " + "KES " + numberFormat.format(cp.getAmount());
                    }).collect(Collectors.joining("|"));
        }).map(res -> new UniversalResponse("success", getResponseMessage("groupContributionPayments"), res));
    }

    @Override
    public Mono<UniversalResponse> getUserUpcomingPayments(String phoneNumber, long groupId) {
        return Mono.fromCallable(() -> {
            MemberWrapper memberWrapper = chamaKycService.searchMonoMemberByPhoneNumber(phoneNumber);

            if (memberWrapper == null) return new UniversalResponse("fail", getResponseMessage("memberNotFound"));

            List<UserUpcomingContributionsWrapper> scheduledPayments = getScheduledPayments(memberWrapper, true, groupId);
            return new UniversalResponse("success", getResponseMessage("userUpcomingContributions"), scheduledPayments);
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> getUserUpcomingPayments(String phoneNumber) {
        Optional<MemberWrapper> memberWrapperOptional = chamaKycService.searchMemberByPhoneNumber(phoneNumber);

        Flux<GroupWrapper> fluxGroupsMemberBelongs = chamaKycService.getFluxGroupsMemberBelongs(phoneNumber);

        return memberWrapperOptional.map(memberWrapper -> fluxGroupsMemberBelongs.map(GroupWrapper::getId).doOnNext(gId -> log.info("Group Id: " + gId)).publishOn(Schedulers.boundedElastic()).map(gId -> getScheduledPayments(memberWrapper, true, gId)).flatMap(Flux::fromIterable).collectList().map(sp -> UniversalResponse.builder().status("success").message("User upcoming payments").data(sp).build())).orElseGet(() -> Mono.just(new UniversalResponse("fail", getResponseMessage("memberNotFound"))));
    }

    @Override
    public Mono<UniversalResponse> getAllUserUpcomingPayments(String phoneNumber) {
        return Mono.fromCallable(() -> {
            MemberWrapper memberWrapper = chamaKycService.searchMonoMemberByPhoneNumber(phoneNumber);

            if (memberWrapper == null) return new UniversalResponse("fail", getResponseMessage("memberNotFound"));

            List<UpcomingContributionsProjection> userUpcomingContributions = contributionSchedulePaymentRepository.findAllUserUpcomingContributions(memberWrapper.getId()).stream().filter(upcoming -> upcoming.getRemainder() > 0).collect(Collectors.toList());

            return new UniversalResponse("success", getResponseMessage("userUpcomingContributions"), userUpcomingContributions);
        }).publishOn(Schedulers.boundedElastic());
    }

    private List<UserUpcomingContributionsWrapper> getScheduledPayments(MemberWrapper memberWrapper,
                                                                        boolean isUpcoming, long groupId) {
        Pageable pageable = PageRequest.of(0, 15);
        List<UserUpcomingContributionsWrapper> userUpcomingContributionsWrappers = new ArrayList<>();

        GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(groupId);

        if (groupWrapper == null) return Collections.emptyList();

        if (!groupWrapper.isActive()) return Collections.emptyList();

        List<Contributions> contributions = contributionsRepository.findByMemberGroupIdAndSoftDeleteFalse(groupId, pageable);

        contributions.forEach(contribution -> {
            List<ContributionSchedulePayment> contributionSchedulePayments = contributionSchedulePaymentRepository.findUpcomingContributionById(contribution.getId());

            contributionSchedulePayments.forEach(contributionSchedulePayment -> {
                Optional<OutstandingContributionPayment> anyOutstandingContributionPayment = outstandingContributionPaymentRepository.findByContributionIdAndMemberId(contribution.getId(), memberWrapper.getId());

                UserUpcomingContributionsWrapper userUpcomingContributionsWrapper = new UserUpcomingContributionsWrapper();

                List<ContributionPayment> contributionPayment = contributionsPaymentRepository.findPaidScheduledContributions(memberWrapper.getPhonenumber(), contributionSchedulePayment.getContributionScheduledId());

                int totalPayment = contributionPayment.stream().mapToInt(ContributionPayment::getAmount).sum();

                if (totalPayment >= contribution.getContributionAmount()) {

                    return;
                }

                if (!isUpcoming) {
                    Penalty penalty = penaltyRepository.findByUserIdAndSchedulePaymentId(memberWrapper.getId(), contributionSchedulePayment.getContributionScheduledId());
                    if (penalty != null) {
                        userUpcomingContributionsWrapper.setHasPenalty(true);
                        userUpcomingContributionsWrapper.setPenaltyAmount((int) penalty.getAmount());
                        userUpcomingContributionsWrapper.setPenaltyId(penalty.getId());
                    }
                }

                userUpcomingContributionsWrapper.setGroupId(groupWrapper.getId());
                userUpcomingContributionsWrapper.setAmount((int) contribution.getContributionAmount());
                userUpcomingContributionsWrapper.setRemaining((int) (contribution.getContributionAmount() - totalPayment));
                userUpcomingContributionsWrapper.setContributionName(contribution.getName());
                userUpcomingContributionsWrapper.setSchedulePaymentId(contributionSchedulePayment.getContributionScheduledId());
                userUpcomingContributionsWrapper.setExpectedPaymentDate(contributionSchedulePayment.getExpectedContributionDate());
                anyOutstandingContributionPayment.ifPresent(outstandingPayment -> userUpcomingContributionsWrapper.setOutstandingAmount(outstandingPayment.getDueAmount()));

                userUpcomingContributionsWrappers.add(userUpcomingContributionsWrapper);
            });
        });

        return userUpcomingContributionsWrappers;
    }

    @Async
    @Override
    public void enableGroupContributions(String groupInfo) {
        JsonObject jsonObject = gson.fromJson(groupInfo, JsonObject.class);

        long groupId = jsonObject.get("groupId").getAsLong();
        String modifier = jsonObject.get("modifier").getAsString();

        List<Contributions> groupContributions = contributionsRepository.findAllByMemberGroupIdAndSoftDeleteFalse(groupId);

        List<Contributions> updatedContributions = groupContributions.stream().map(c -> {
            c.setActive(false);
            c.setLastModifiedDate(new Date());
            c.setLastModifiedBy(modifier);
            return c;
        }).collect(Collectors.toList());

        contributionsRepository.saveAll(updatedContributions);
    }

    @Async
    @Override
    public void disableGroupContributions(String groupInfo) {
        JsonObject jsonObject = gson.fromJson(groupInfo, JsonObject.class);

        long groupId = jsonObject.get("groupId").getAsLong();
        String modifier = jsonObject.get("modifier").getAsString();

        List<Contributions> groupContributions = contributionsRepository.findAllByMemberGroupIdAndSoftDeleteFalse(groupId);

        List<Contributions> updatedContributions = groupContributions.stream().map(c -> {
            c.setActive(false);
            c.setLastModifiedDate(new Date());
            c.setLastModifiedBy(modifier);
            return c;
        }).collect(Collectors.toList());

        contributionsRepository.saveAll(updatedContributions);
    }

    @Override
    public Mono<UniversalResponse> getUserContributionsPerGroup(String phoneNumber) {
        return Mono.fromCallable(() -> {
            List<UserGroupContributions> userContributions = contributionsPaymentRepository.findUserContributions(phoneNumber);

            Map<String, List<UserGroupContributions>> collect = userContributions.stream().collect(groupingBy(UserGroupContributions::getGroupName));

            Set<ContributionAnalyticsWrapper> contributionsByUserForGroups = new HashSet<>();
            collect.forEach((s, userGroupContributions) -> {
                int sum = userGroupContributions.stream().mapToInt(UserGroupContributions::getAmount).sum();
                contributionsByUserForGroups.add(new ContributionAnalyticsWrapper(s, sum));
            });

            return contributionsByUserForGroups;
        }).publishOn(Schedulers.boundedElastic()).map(res -> new UniversalResponse("success", getResponseMessage("totalUserContributionsPerGroup"), res));
    }

    @Override
    public Mono<UniversalResponse> getAllMemberPenalties(String username) {
        return Mono.fromCallable(() -> {
            MemberWrapper member = chamaKycService.searchMonoMemberByPhoneNumber(username);

            if (member == null) return new UniversalResponse("fail", "Could not find member");

            List<Penalty> userPenalties = penaltyRepository.findAllByUserId(member.getId());
            List<PenaltyWrapper> penaltyWrappers = new ArrayList<>();
            userPenalties.parallelStream().filter(p -> !p.isPaid()).forEach(penalty -> mapToPenaltyWrapper(penaltyWrappers, penalty));
            return new UniversalResponse("success", getResponseMessage("allMemberPenalties"), penaltyWrappers);
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> getGroupContributionPenalties(Long groupId, int page, int size) {
        Flux<MemberWrapper> fluxGroupMembers = chamaKycService.getFluxGroupMembers(groupId);
        return findGroupContribution(groupId).zipWith(fluxGroupMembers.collectList()).map(tuple -> {
            Contributions contribution = tuple.getT1();
            List<MemberWrapper> groupMembers = tuple.getT2();
            List<PenaltyWrapper> penaltyWrappers = new ArrayList<>();
            groupMembers.parallelStream().map(gm -> penaltyRepository.findByUserIdAndContributionId(gm.getId(), contribution.getId())).flatMap(List::stream).filter(penalty -> !penalty.isPaid()).forEach(penalty -> mapToPenaltyWrapper(penaltyWrappers, penalty));
            return new UniversalResponse("success", getResponseMessage("allMemberPenalties"), penaltyWrappers);
        }).publishOn(Schedulers.boundedElastic());
    }

    private void mapToPenaltyWrapper(List<PenaltyWrapper> penaltyWrappers, Penalty penalty) {
        ContributionSchedulePayment contributionSchedulePayment = contributionSchedulePaymentRepository.findByContributionScheduledId(penalty.getSchedulePaymentId());
        if (contributionSchedulePayment == null) return;

        Contributions contributions = contributionsRepository.findById(contributionSchedulePayment.getContributionId()).get();
        List<ContributionPayment> contributionPaymentList = contributionsPaymentRepository.findPenaltyContributions(contributions.getId(), penalty.getId());
        penalty.setContributionName(contributions.getName());
        penalty.setPaymentStatus(contributionPaymentList.isEmpty() ? "NOT_PAID" : contributionPaymentList.get(0).getPaymentStatus());
        penalty.setExpectedPaymentDate(contributionSchedulePayment.getExpectedContributionDate());
        penalty.setContributionId(contributions.getId());
        penalty.setGroupId(contributions.getMemberGroupId());
        PenaltyWrapper penaltyWrapper = mapPenaltyToWrapper().apply(penalty);
        penaltyWrappers.add(penaltyWrapper);
    }

    private Mono<Contributions> findGroupContribution(Long groupId) {
        return Mono.fromCallable(() -> contributionsRepository.findByMemberGroupIdAndSoftDeleteFalse(groupId).orElse(null)).publishOn(Schedulers.boundedElastic()).switchIfEmpty(Mono.error(new IllegalArgumentException("Contribution for group not found")));
    }

    private Function<Penalty, PenaltyWrapper> mapPenaltyToWrapper() {
        return penalty -> {
            Optional<MemberWrapper> memberWrapperOptional = chamaKycService.searchMemberByUserId(penalty.getUserId());
            if (memberWrapperOptional.isEmpty()) return null;
            MemberWrapper memberWrapper = memberWrapperOptional.get();
            return PenaltyWrapper.builder().id(penalty.getId()).userId(penalty.getUserId()).schedulePaymentId(penalty.getSchedulePaymentId()).contributionName(penalty.getContributionName()).paymentStatus(penalty.getPaymentStatus()).expectedPaymentDate(penalty.getExpectedPaymentDate()).contributionId(penalty.getContributionId()).groupId(penalty.getGroupId()).amount(penalty.getAmount()).memberNames(String.format("%s %s", memberWrapper.getFirstname(), memberWrapper.getLastname())).transactionId(penalty.getTransactionId()).isPaid(penalty.isPaid()).build();
        };
    }

    @Override
    public Mono<UniversalResponse> editContribution(ContributionDetailsWrapper contributionDetailsWrapper, String
            username) {
        return Mono.fromCallable(() -> {
            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(contributionDetailsWrapper.getGroupid());
            if (groupWrapper == null) {
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"), new ArrayList<>());
            }

            Pageable pageable = PageRequest.of(0, 10);
            List<Contributions> contributionsList = contributionsRepository.findByMemberGroupIdAndSoftDeleteFalse(groupWrapper.getId(), pageable);

            if (contributionsList.isEmpty()) {
                return new UniversalResponse("fail", getResponseMessage("noContributionsAssociatedWithGroup"), new ArrayList<>());
            }

            Contributions contributions = contributionsList.get(0); //weekly
            contributions.setContributionAmount(contributionDetailsWrapper.getAmountcontributed());
            ScheduleTypes scheduleTypes = scheduleTypesRepository.findByNameIgnoreCaseAndSoftDeleteFalse(contributionDetailsWrapper.getScheduletypename());

            if (scheduleTypes == null) {
                return new UniversalResponse("fail", getResponseMessage("frequencyNotCateredFor"), new ArrayList<>());
            }

            contributions.setScheduleType(scheduleTypes);
            contributions.setPenalty(contributionDetailsWrapper.getPenalty() == null ? 0 : contributionDetailsWrapper.getPenalty());
            contributions.setIspercentage(contributionDetailsWrapper.getIsPercentage() != null && contributionDetailsWrapper.getIsPercentage());
            contributions.setDuedate(LocalDate.parse(contributionDetailsWrapper.getDueDate()));
            contributions.setLastModifiedDate(new Date());
            contributions.setLastModifiedBy(username);
            contributions.setActive(true);
            contributions.setContributionAmount(contributionDetailsWrapper.getAmountcontributed() == 0 ? contributions.getContributionAmount() : contributionDetailsWrapper.getAmountcontributed());

            contributions = contributionsRepository.saveAndFlush(contributions);


//            sendContributionEditText(contributions, username);
            return new UniversalResponse("success", getResponseMessage("successfulContributionEdit"), contributions);
        }).publishOn(Schedulers.boundedElastic());
    }

    @Async
    public void sendContributionEditText(long groupId, String groupName, String creator, String creatorPhone, String language) {
        GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(groupId);

        if (groupWrapper != null && groupWrapper.isActive()) {

            //send sms to fined member
            notificationService.sendEditContributionToMember(groupId, groupName, creator, creatorPhone, language);
            //send to officials
            chamaKycService.getGroupOfficials(groupId)
                    .filter(gm -> !gm.getPhonenumber().equals(creatorPhone))
                    .subscribe(official ->
                            notificationService.sendEditContributionToOfficials(groupId, groupName, creator, official.getFirstname(), official.getPhonenumber(), official.getLanguage())
                    );
        } else {
            log.error("Could not send Edit Contribution SMS. Group not found.");
        }
    }

    @Override
    public Mono<UniversalResponse> getGroupContributions(Long groupId) {
        return Mono.fromCallable(() -> {
            SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
            List<ContributionWrapper> groupContributions = contributionsRepository.findAllByMemberGroupIdAndSoftDeleteFalse(groupId).stream().map(c -> ContributionWrapper.builder().id(c.getId()).groupId(c.getMemberGroupId()).name(c.getName()).amountType(c.getAmountType().getName()).contributionAmount((long) c.getContributionAmount()).contributionTypeName(c.getContributionType().getName()).scheduleTypeName(c.getScheduleType().getName()).active(c.isActive()).ispercentage(c.getIspercentage()).memberGroupId(c.getMemberGroupId()).contributionDate(c.getContributionDate()).reminder(c.getReminder()).dueDate(c.getDuedate()).penalty(c.getPenalty()).startDate(formatter.format(c.getStartDate())).build())
                    .collect(Collectors.toList());
            return new UniversalResponse("success", getResponseMessage("groupContributions"), groupContributions);
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> getGroupContribution(Long contributionId) {
        return Mono.fromCallable(() -> {

            Optional<Contributions> contributions = contributionsRepository.findByGroupIdAndActiveTrueAndSoftDeleteFalse(contributionId);

            if (contributions.isEmpty())
                return UniversalResponse.builder()
                        .status("fail")
                        .message("Could not find contribution with id")
                        .timestamp(new Date()).build();
            Contributions contribution = contributions.get();
            Optional<ContributionType> optionalContributionType = contributionTypesRepository.findById(contribution.getContributionType().getId());

            ContributionWrapper contributionWrapper = ContributionWrapper.builder()
                    .id(contribution.getId())
                    .groupId(contribution.getGroupId())
                    .name(contribution.getName())
                    .shareoutDate(contribution.getShareoutDate())
                    .isAutoShareOut(contribution.getAutoShareOut())
                    .isManualShareOut(contribution.getManualShareOut())
                    .amountType(contribution.getAmountType().getName())
                    .contributionAmount((long) contribution.getContributionAmount())
                    .contributionTypeName(contribution.getContributionType().getName())
                    .scheduleTypeName(contribution.getScheduleType().getName())
                    .active(contribution.isActive())
                    .penalty(contribution.getPenalty())
                    .dueDate(contribution.getDuedate())
                    .ispercentage(contribution.getIspercentage())
                    .welfareAmt(contribution.getWelfareAmt())
                    .contributionDate(contribution.getContributionDate())
                    .memberGroupId(contribution.getMemberGroupId())
                    .reminder(contribution.getReminder())
                    .contributionAmountValue(contribution.getContributionAmount()).frequency(contribution.getFrequency()).loanInterest(contribution.getLoanInterest()).paymentPeriod(contribution.getPaymentPeriod()).daysBeforeDue(contribution.getDaysBeforeDue()).build();
            return UniversalResponse.builder().status("success").message("Contribution details").data(contributionWrapper).timestamp(new Date()).build();
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> getGroupAccountsMemberBelongsTo(String username) {
        return Mono.fromCallable(() -> {
            Flux<GroupWrapper> fluxGroupsMemberBelongs = chamaKycService.getFluxGroupsMemberBelongs(username);

            List<Map<String, Object>> groupAccountData = new ArrayList<>();

            fluxGroupsMemberBelongs.toIterable().forEach(group -> {
                List<Accounts> groupAccounts = accountsRepository.findByGroupIdAndActive(group.getId(), true);

                Map<String, Object> metadata;
                if (groupAccounts.isEmpty()) {
                    metadata = Map.of("groupId", group.getId(), "groupName", group.getName(), "accountNumber", group.getCsbAccount(), "accountBal", 0, "availableBal", 0);
                } else {
                    metadata = Map.of("groupId", group.getId(), "groupName", group.getName(), "accountNumber", group.getCsbAccount(), "accountBal", groupAccounts.get(0).getAccountbalance(), "availableBal", groupAccounts.get(0).getAvailableBal());
                }

                groupAccountData.add(metadata);

            });
            return new UniversalResponse("success", getResponseMessage("userGroupAccountDetails"), groupAccountData);
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> getGroupTransactions(Long groupId, Integer page, Integer size, String username) {
        Pageable pageable = PageRequest.of(page, size);
        return Mono.fromCallable(() -> getTransactionsbyGroup(groupId, pageable, username))
                .publishOn(Schedulers.boundedElastic()).map(res -> new UniversalResponse("success",
                        getResponseMessage("groupTransactions"), res));
    }


    @Override
    public Mono<UniversalResponse> getGroupTransactionsPerUser(long groupId, String username, Integer
            page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return Mono.fromCallable(() -> getTransactionsbyGroupUser(groupId, username, pageable))
                .publishOn(Schedulers.boundedElastic()).map(res -> new UniversalResponse("success",
                        "user transactions", res));
    }


    @Override
    public Mono<UniversalResponse> getUserTransactionsByContribution(String username, Long contributionId, Integer
            page, Integer size) {
        return Mono.fromCallable(() -> {
            Optional<Contributions> contributionsOptional = contributionsRepository.findByIdAndSoftDeleteFalse(contributionId);

            if (contributionsOptional.isEmpty())
                return new UniversalResponse("fail", getResponseMessage("contributionNotFound"));
            Pageable pageable = PageRequest.of(page, size);
            List<TransactionLogWrapper> transactions = getTransactionsbyUserandContributions(username, contributionsOptional.get(), pageable);

            return new UniversalResponse("success", getResponseMessage("userTransactionsByContribution"), transactions);
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> getUserTransactionsByGroup(String username, Long groupId, Integer page, Integer
            size) {
        return Mono.fromCallable(() -> {
            GroupWrapper group = chamaKycService.getMonoGroupById(groupId);

            if (group == null) return new UniversalResponse("fail", getResponseMessage("userTransactionsByGroup"));
            Pageable pageable = PageRequest.of(page, size);
            List<TransactionLogWrapper> transactions = getTransactionsbyUserandGroupId(username, groupId, pageable);

            return new UniversalResponse("success", getResponseMessage("userTransactionsByGroup"), transactions);
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> getUserSummary(String phone, Long contributionId) {
        return Mono.fromCallable(() -> {
            MemberWrapper member = chamaKycService.searchMonoMemberByPhoneNumber(phone);

            if (member == null) return new UniversalResponse("fail", getResponseMessage("memberNotFound"));

            // total contributions
            int totalContributions = contributionsPaymentRepository.findUsersContribution(contributionId, phone).parallelStream().filter(cp -> Objects.equals(cp.getPaymentStatus(), PaymentEnum.PAYMENT_SUCCESS.name())).mapToInt(ContributionPayment::getAmount).sum();

            // total loans
            Double totalLoansDisbursed = loansdisbursedRepo.findByMemberIdOrderByCreatedOnDesc(member.getId()).parallelStream().filter(ld -> ld.getDueamount() != 0).mapToDouble(ld -> ld.getDueamount() + ld.getInterest()).sum();
            // total contributions penalties
            Double totalPenalties = penaltyRepository.findAllByUserId(member.getId()).parallelStream().filter(p -> !p.isPaid()).mapToDouble(Penalty::getAmount).sum();
            // total loan penalties
            Double totalLoanPenalties = loanPenaltyRepository.findAllByMemberId(member.getId()).parallelStream().filter(lp -> Objects.equals(lp.getPaymentStatus(), PaymentEnum.PAYMENT_PENDING.name())).mapToDouble(LoanPenalty::getPenaltyAmount).sum();
            Map<String, ? extends Number> memberSummary = Map.of("totalContributions", totalContributions, "totalLoansDisbursed", totalLoansDisbursed, "totalContributionsPenalties", totalPenalties, "totalLoansPenalties", totalLoanPenalties);
            return new UniversalResponse("success", getResponseMessage("memberAccountingSummary"), memberSummary);
        }).publishOn(Schedulers.boundedElastic());
    }

    private Mono<UniversalResponse> getGroupAccounts(Long groupId) {
        return Mono.fromCallable(() -> {
            List<Accounts> accountsList = accountsRepository.findByGroupIdAndActive(groupId, true);

            return accountsList.parallelStream().map(a -> AccountDto.builder().accountId(a.getId()).name(a.getName()).active(a.getActive()).accountbalance(a.getAccountbalance()).availableBal(a.getAvailableBal()).build()).collect(Collectors.toList());
        }).publishOn(Schedulers.boundedElastic()).map(res -> new UniversalResponse("success", getResponseMessage("groupAccounts"), res));
    }


    @Override
    public void writeOffLoansAndPenalties(String memberInfo) {
        Mono.fromRunnable(() -> {
            JsonObject jsonObject = gson.fromJson(memberInfo, JsonObject.class);

            // get member id and group id
            long memberId = jsonObject.get("memberId").getAsLong();
            long groupId = jsonObject.get("groupId").getAsLong();

            // find penalties (Loan and Contribution)
            Optional<GroupWrapper> groupWrapperOptional = chamaKycService.getGroupById(groupId);

            groupWrapperOptional.ifPresentOrElse(group -> {

                List<Contributions> groupContributions = contributionsRepository.findAllByMemberGroupIdAndSoftDeleteFalse(group.getId());

                // clear contribution penalties
                groupContributions.parallelStream().map(contribution -> penaltyRepository.findByUserIdAndContributionId(memberId, contribution.getId())).flatMap(List::stream).forEach(penalty -> {
                    penalty.setPaid(true);
                    penalty.setSoftDelete(true);
                    penalty.setPaymentStatus(PaymentEnum.PAYMENT_WRITTEN_OFF.name());
                    penaltyRepository.save(penalty);
                });

                // clear loans and loan penalties
                loansdisbursedRepo.findAllByGroupIdAndMemberIdAndSoftDeleteFalseOrderByIdAsc(group.getId(), memberId).parallelStream().forEach(loanDisbursed -> {
                    loanDisbursed.setDueamount(0.0);
                    loanDisbursed.setSoftDelete(true);
                    loansdisbursedRepo.save(loanDisbursed);

                    loanPenaltyRepository.findAllByMemberIdAndLoansDisbursed(memberId, loanDisbursed).parallelStream().forEach(loanPenalty -> {
                        loanPenalty.setDueAmount(0.0);
                        loanPenalty.setSoftDelete(true);
                        loanPenalty.setPaymentStatus(PaymentEnum.PAYMENT_WRITTEN_OFF.name());
                    });
                });
            }, () -> log.info("Group not found... On Writing off loans and penalties"));
        }).publishOn(Schedulers.boundedElastic()).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    @Override
    public void editContributionName(String contributionNameUpdate) {
        Mono.fromRunnable(() -> {
            JsonObject jsonObject = gson.fromJson(contributionNameUpdate, JsonObject.class);
            long groupId = jsonObject.get("groupId").getAsLong();
            String contributionName = jsonObject.get("contributionName").getAsString();
            String modifiedBy = jsonObject.get("modifiedBy").getAsString();

            Optional<GroupWrapper> optionalGroupWrapper = chamaKycService.getGroupById(groupId);

            optionalGroupWrapper.ifPresentOrElse(group -> {
                Optional<Contributions> optionalContribution = contributionsRepository.findByGroupIdAndActiveTrueAndSoftDeleteFalse(group.getId());
                optionalContribution.ifPresentOrElse(contribution -> {
                    contribution.setName(contributionName);
                    contribution.setLastModifiedDate(new Date());
                    contribution.setLastModifiedBy(modifiedBy);
                    contributionsRepository.save(contribution);
                }, () -> log.info("Contribution not found with group id {} ... on contribution name edit.", groupId));
            }, () -> log.info("Group not found with id {} ... on updating group contribution name!", groupId));
        }).publishOn(Schedulers.boundedElastic()).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    @Override
    public void updateGroupCoreAccount(String groupCoreAccountInfo) {
        Mono.fromRunnable(() -> {
            JsonObject jsonObject = gson.fromJson(groupCoreAccountInfo, JsonObject.class);
            long groupId = jsonObject.get("groupId").getAsLong();
            String cbsAccount = jsonObject.get("account").getAsString();
            String initialBalance = jsonObject.get("initialBalance").getAsString();
            String modifiedBy = jsonObject.get("modifiedBy").getAsString();
            List<Accounts> groupAccounts = accountsRepository.findByGroupIdOrderByCreatedOnAsc(groupId);
            Accounts account = groupAccounts.get(0);
            account.setActive(true);
            JsonObject accountDetails = new JsonObject();
            accountDetails.addProperty("account_number", cbsAccount);
            account.setAccountdetails(accountDetails.toString());
            account.setAvailableBal(Double.parseDouble(initialBalance));
            account.setAccountbalance(Double.parseDouble(initialBalance));
            account.setLastModifiedBy(modifiedBy);
            accountsRepository.save(account);
            Optional<Contributions> optionalContribution = contributionsRepository.findByMemberGroupIdAndSoftDeleteFalse(account.getGroupId());
            optionalContribution.ifPresentOrElse(contribution -> createInitialContribution(contribution, initialBalance), () -> log.info("Group contribution not found... On enabling group"));
            log.info("Updated Group Core Account with ID {}", groupId);
        }).publishOn(Schedulers.boundedElastic()).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    @Override
    public Mono<UniversalResponse> getOverpaidContributions(String username) {
        return Mono.fromCallable(() -> {
            List<OverpaidContribution> overpaidContributions = overpaidContributionRepository.findByPhoneNumber(username);
            if (overpaidContributions.isEmpty()) {
                return new ArrayList<>();
            }
            return overpaidContributions.parallelStream().map(oc -> {
                String groupName = groupRepository.findById(oc.getGroupId()).orElseThrow(() -> new RuntimeException("Group not found")).getName();
                return Map.of("username", oc.getPhoneNumber(), "groupName", groupName, "amount", oc.getAmount());
            }).collect(Collectors.toList());
        }).publishOn(Schedulers.boundedElastic()).map(res -> new UniversalResponse("success", getResponseMessage("overpaidContributions"), res));
    }


    @Override
    public Mono<UniversalResponse> viewFines(String phoneNumber, Long groupId) {
        return Mono.fromCallable(() -> {
            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(groupId);
            if (groupWrapper == null)
                return new UniversalResponse("fail", getResponseMessage(GROUP_NOT_FOUND));
            MemberWrapper memberWrapper = chamaKycService.searchMonoMemberByPhoneNumber(phoneNumber);
            if (memberWrapper == null) {
                return new UniversalResponse("fail", MEMBER_NOT_FOUND);
            }
            List<Fines> fines = finesRepository.findByMemberIdAndGroupIdAndPaymentStatusAndSoftDeleteFalseOrderByIdDesc(memberWrapper.getId(), groupId, PaymentEnum.PAYMENT_PENDING.name());
            if (fines.isEmpty()) {
                return new UniversalResponse("fail", "No fines found");
            }

            List<MemberFineResponse> fineList = fines
                    .stream()
                    .map(fine -> MemberFineResponse.builder()
                            .groupName(groupWrapper.getName())
                            .amount(fine.getFineBalance())
                            .description(fine.getFineDescription())
                            .fineName(fine.getFineName())
                            .paymentStatus(fine.getPaymentStatus())
                            .fineId(fine.getId())
                            .createdOn(fine.getCreatedOn())
                            .build()).collect(Collectors.toList());
            return new UniversalResponse("success", "fines", fineList);
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> viewFineById(Long id) {
        return Mono.fromCallable(() -> {
            Optional<Fines> optionalFines = finesRepository.findById(id);
            if (optionalFines.isEmpty()) {
                return new UniversalResponse("fail", "No fines found");
            }
            Fines fines = optionalFines.get();
            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(fines.getGroupId());
            if (groupWrapper == null)
                return new UniversalResponse("fail", getResponseMessage(GROUP_NOT_FOUND));

            FineResponse response = FineResponse.builder().groupName(groupWrapper.getName())
                    .amount(fines.getFineBalance())
                    .description(fines.getFineDescription())
                    .fineName(fines.getFineName())
                    .paymentStatus(fines.getPaymentStatus())
                    .build();
            return new UniversalResponse("success", "fines", response);

        }).publishOn(Schedulers.boundedElastic());
    }


    @Override
    public Mono<UniversalResponse> updateFine(Long id, FineWrapper fineWrapper) {
        return Mono.fromCallable(() -> {
            Optional<Fines> optionalFines = finesRepository.findById(id);
            if (optionalFines.isEmpty()) {
                return new UniversalResponse("fail", "No fines found");
            }
            Fines fines = optionalFines.get();
            fines.setFineAmount(fineWrapper.getAmount());
            fines.setFineDescription(fineWrapper.getDescription());
            fines.setFineName(fineWrapper.getFineName());
            finesRepository.save(fines);
            return new UniversalResponse("success", "Fine updated successfully");
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> createFines(List<FineWrapper> fineWrapperList, String username) {
        return Mono.fromCallable(() -> {
            MemberWrapper creator = chamaKycService.searchMonoMemberByPhoneNumber(username);
            if (creator == null)
                return new UniversalResponse("fail", getResponseMessage("appUserNotFound"));

            List<FinesPendingApprovals> finesPendingApprovalsList = new ArrayList<>();
            for (FineWrapper fineWrapper : fineWrapperList) {
                GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(fineWrapper.getGroupId());
                if (groupWrapper == null)
                    return new UniversalResponse("fail", getResponseMessage("groupNotFound"));

                GroupMemberWrapper groupMemberWrapper = chamaKycService.memberIsPartOfGroup(fineWrapper.getGroupId(), username);

                if (groupMemberWrapper == null) {
                    return new UniversalResponse("fail", getResponseMessage("memberNotPartOfTheGroup"));
                }
                if (groupMemberWrapper.getTitle().equals("member"))

                    return new UniversalResponse("fail", getResponseMessage("officialsCreation"));

                Member member = memberRepository.findFirstByImsiIsAndSoftDeleteFalse(fineWrapper.getPhoneNumber());

                if (member == null) {

                    return new UniversalResponse("fail", getResponseMessage("memberNotFound"));
                }

                MemberWrapper memberWrapper = chamaKycService.searchMonoMemberByPhoneNumber(fineWrapper.getPhoneNumber());

                if (memberWrapper == null) {
                    return new UniversalResponse("fail", getResponseMessage("memberNotFound"));
                }
                long groupId = groupWrapper.getId();
                String groupName = groupWrapper.getName();
                String finedMember = String.format("%s %s", memberWrapper.getFirstname(), memberWrapper.getLastname());
                String finedMemberPhone = memberWrapper.getPhonenumber();
                String creatorName = String.format("%s %s", creator.getFirstname(), creator.getLastname());
                String creatorMobileNumber = creator.getPhonenumber();
                FinesPendingApprovals pendingApprovals = new FinesPendingApprovals(groupId, member.getId(), fineWrapper.getAmount(), finedMember, finedMemberPhone, creatorName, creatorMobileNumber, fineWrapper.getDescription());

                finesPendingApprovalsList.add(pendingApprovals);

                sendFineRequestText(groupId, groupName, finedMember, creatorName, fineWrapper.getAmount(), fineWrapper.getDescription(), memberWrapper.getPhonenumber(), memberWrapper.getLanguage(), creatorMobileNumber);
                auditTrail("group member fines", "group member fines added successfully.", username);
                creatNotification(groupId, groupWrapper.getName(), "group member fines added successfully by " + username);
            }

            finesPendingApprovalsRepository.saveAll(finesPendingApprovalsList);

            if (finesPendingApprovalsList.size() > 1) {
                return new UniversalResponse("success", getResponseMessage("finesCreatedSuccessfully"));
            }

            return new UniversalResponse("success", getResponseMessage("fineCreatedSuccessfully"));
        }).publishOn(Schedulers.boundedElastic());
    }

    @Async
    protected void sendFineRequestText(long groupId, String groupName, String finedMember, String
            initiator, Double amount, String description, String phonenumber, String language, String creatorPhone) {
        //todo:: send sms to fined member
        notificationService.sendFineSmsToMember(finedMember, initiator, groupName, amount, description, phonenumber, language, groupId);
        //todo:: send to officials
        chamaKycService.getGroupOfficials(groupId)
                .filter(gm -> !gm.getPhonenumber().equals(creatorPhone) && !gm.getPhonenumber().equals(phonenumber))
                .subscribe(official ->
                        notificationService.sendFineSmsToGroupMembers(finedMember, initiator, groupName, amount, description, official.getFirstname(), official.getPhonenumber(), official.getLanguage())
                );
    }

    @Override
    public Mono<UniversalResponse> viewGroupFines(Long groupId) {
        return Mono.fromCallable(() -> {
            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(groupId);
            if (groupWrapper == null)
                return new UniversalResponse("fail", getResponseMessage(GROUP_NOT_FOUND));
            List<Fines> fines = finesRepository.findFinesByGroupIdAndPaymentStatusAndSoftDeleteFalseOrderByIdDesc(groupId, PaymentEnum.PAYMENT_PENDING.name());
            if (fines.isEmpty()) {
                return new UniversalResponse("fail", "No fines found");
            }


            List<FinesWrapperResponse> fineList = fines
                    .stream()
                    .map(fine -> {
                        Optional<MemberWrapper> optionalMember = chamaKycService.getMemberDetailsById(fine.getMemberId());
                        MemberWrapper member = optionalMember.get();
                        return FinesWrapperResponse.builder()
                                .groupName(groupWrapper.getName())
                                .amount(fine.getFineBalance())
                                .description(fine.getFineDescription())
                                .fineName(fine.getFineName())
                                .paymentStatus(fine.getPaymentStatus())
                                .memberName(member.getFirstname().concat(" " + member.getLastname()))
                                .fineId(fine.getId())
                                .createdOn(fine.getCreatedOn())
                                .build();
                    }).collect(Collectors.toList());
            return new UniversalResponse("success", "fines", fineList);
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public void createLoanProduct(String s) {
        Mono.fromCallable(() -> {
            JsonObject jsonObject = gson.fromJson(s, JsonObject.class);
            log.info("Loan product request {}", jsonObject);
            LoanProducts loanProduct = new LoanProducts();
            loanProduct.setGroupId(jsonObject.get("groupId").getAsLong());
            loanProduct.setProductname(jsonObject.get("productName").getAsString());
            loanProduct.setDescription(jsonObject.get("productName").getAsString());
            loanProduct.setMax_principal(999999.00);
            loanProduct.setMin_principal(1);
            loanProduct.setInteresttype("Simple");
            loanproductsRepository.save(loanProduct);
            return new UniversalResponse("success", "Loan product created successfully");
        });
    }

    @Override
    public Mono<UniversalResponse> editContributionPostBank(EditContributionWrapper req, String username) {
        return Mono.fromCallable(() -> {
            MemberWrapper memberWrapper = chamaKycService.searchMonoMemberByPhoneNumber(username);
            if (memberWrapper == null)
                return new UniversalResponse("fail", getResponseMessage(MEMBER_NOT_FOUND));

            String creator = String.format("%s %s", memberWrapper.getFirstname(), memberWrapper.getLastname());
            String creatorPhone = memberWrapper.getPhonenumber();
            String language = memberWrapper.getLanguage();
            long memberId = memberWrapper.getId();

            GroupMemberWrapper groupMembership = chamaKycService.getMonoGroupMembershipByGroupIdAndMemberId(req.getGroupid(), memberId);
            if (groupMembership == null)
                return new UniversalResponse("fail", getResponseMessage("memberIsNotPartOfGroup"));

            if (groupMembership.getTitle().equals("member"))

                return new UniversalResponse("fail", getResponseMessage("accountsOfficialsApproval"));

            Optional<Contributions> optionalContribution = contributionsRepository.findByGroupIdAndActiveTrueAndSoftDeleteFalse(req.getGroupid());
            if (optionalContribution.isEmpty()) {
                return new UniversalResponse("fail", getResponseMessage("contributionNotFound"));
            }
            Contributions contribution = optionalContribution.get();
            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(req.getGroupid());
            if (groupWrapper == null) {
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"));
            }
            long groupId = groupWrapper.getId();
            String groupName = groupWrapper.getName();

            AmountType amountType = amounttypeRepo.findAmountName();
            if (amountType == null) {
                return new UniversalResponse("fail", getResponseMessage("amountTypeNotFound"));

            }
            ScheduleTypes scheduleTypes = scheduleTypesRepository.findByNameIgnoreCaseAndSoftDeleteFalse("Monthly");
            if (scheduleTypes == null) {
                return new UniversalResponse("fail", getResponseMessage("scheduleTypeNotFound"));
            }
            ContributionType contributionType = contributionTypesRepository.findContributionType();
            if (contributionType == null) {
                return new UniversalResponse("fail", getResponseMessage("contributionTypeNotFound"));

            }

            //todo:: stage contribution approval
            ContributionsPendingApprovals pendingApprovals = new ContributionsPendingApprovals();

            pendingApprovals.setIspercentage(req.getIspercentage());
            pendingApprovals.setAutoShareOut(req.isAutoShareOut());
            pendingApprovals.setManualShareOut(req.isManualShareOut());
            pendingApprovals.setPenalty(req.getPenalty());
            pendingApprovals.setAmountType(amountType);
            pendingApprovals.setScheduleType(scheduleTypes);
            pendingApprovals.setContributionType(contributionType);
            pendingApprovals.setName(contribution.getName());
            pendingApprovals.setContributionAmount(req.getContributionAmt());
            pendingApprovals.setWelfareAmt(req.getWelfareAmt());
            pendingApprovals.setContributionDate(req.getContributionDate());
            pendingApprovals.setFrequency(req.getFrequency());
            pendingApprovals.setReminder(req.getReminders());
            pendingApprovals.setLoanInterest(req.getLoanInterest());
            pendingApprovals.setPaymentPeriod(req.getPaymentPeriod());
            pendingApprovals.setDaysBeforeDue(req.getDaysBeforeDue());
            pendingApprovals.setDuedate(req.getDueDate());
            pendingApprovals.setShareoutDate(req.getShareoutDate());
            pendingApprovals.setLastModifiedDate(new Date());
            pendingApprovals.setLastModifiedBy(username);
            pendingApprovals.setGroupId(contribution.getGroupId());
            pendingApprovals.setApprovedBy(new JsonObject().toString());
            pendingApprovals.setApprovalCount(0);
            pendingApprovals.setPending(true);
            pendingApprovals.setApprovalStaged(true);
            pendingApprovals.setApprovalProcessed(false);
            pendingApprovals.setApproved(false);
            pendingApprovals.setCreator(creator);
            pendingApprovals.setCreatorPhoneNumber(creatorPhone);

            contributionsPendingApprovalsRepository.save(pendingApprovals);
            sendContributionEditText(groupId, groupName, creator, creatorPhone, language);
            AuditTrail auditTrail = AuditTrail.builder()
                    .action("Edit Contribution")
                    .description("Contribution request send to officials for approval.")
                    .capturedBy(username).build();
            auditTrailRepository.save(auditTrail);
            createNotification(groupId, "Contribution Request Send to Officials for Approval", groupName);
            return new UniversalResponse("success", "Contribution Staged successfully. waiting Approvals");
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> approveDeclineContribution(ContributionsApprovalRequest req, String approvedBy) {
        return Mono.fromCallable(() -> {
            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(req.getGroupId());
            if (groupWrapper == null)
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"));
            MemberWrapper approver = chamaKycService.searchMonoMemberByPhoneNumber(approvedBy);

            if (approver == null)
                return new UniversalResponse("fail", getResponseMessage("approverNotFound"));

            GroupMemberWrapper groupMembership = chamaKycService.getMonoGroupMembershipByGroupIdAndMemberId(req.getGroupId(), approver.getId());
            if (groupMembership == null)
                return new UniversalResponse("fail", getResponseMessage("memberIsNotPartOfGroup"));

            if (groupMembership.getTitle().equals("member"))

                return new UniversalResponse("fail", getResponseMessage("accountsOfficialsApproval"));

            Optional<ContributionsPendingApprovals> optionalContributionsPendingApprovals = contributionsPendingApprovalsRepository.findByIdAndSoftDeleteFalseAndApprovalProcessedFalse(req.getRequestId());

            if (optionalContributionsPendingApprovals.isEmpty())
                return new UniversalResponse("fail", getResponseMessage("contributionApprovalNotPending"));

            ContributionsPendingApprovals pendingApprovals = optionalContributionsPendingApprovals.get();

            MemberWrapper creator = chamaKycService.searchMonoMemberByPhoneNumber(pendingApprovals.getCreatorPhoneNumber());

            if (creator == null)
                return new UniversalResponse("fail", getResponseMessage("memberNotFound"));

            if (Objects.equals(approver.getPhonenumber(), creator.getPhonenumber()))
                return new UniversalResponse("fail", getResponseMessage("cannotApproveOwnContribution"));

            ///new version
            JsonObject approvals = gson.fromJson(pendingApprovals.getApprovedBy(), JsonObject.class);

            if (approvals.has(groupMembership.getTitle()))
                return new UniversalResponse("fail", getResponseMessage("youCannotAuthorizeMoreThanOnce"));
            String memberName = String.format("%s %s", creator.getFirstname(), creator.getLastname());
            String memberPhoneNumber = creator.getPhonenumber();
            String language = creator.getLanguage();
            if (!req.getApprove()) {
                pendingApprovals.setApprovalStaged(false);
                pendingApprovals.setApprovalProcessed(true);
                pendingApprovals.setPending(false);
                pendingApprovals.setApproved(true);
                contributionsPendingApprovalsRepository.save(pendingApprovals);
                sendDeclinedEditContributionText(groupWrapper, memberName, memberPhoneNumber, language);
                createNotification(groupWrapper.getId(), "Contribution declined successfully.", groupWrapper.getName());
                return new UniversalResponse("success", getResponseMessage("contributionApprovalDeclined"));
            }

            approvals.addProperty(groupMembership.getTitle(), approver.getId());
            pendingApprovals.setApprovalCount(pendingApprovals.getApprovalCount() + 1);
            pendingApprovals.setApprovedBy(approvals.toString());
            pendingApprovals.setApprovalStaged(true);
            pendingApprovals.setApprovalProcessed(false);
            pendingApprovals.setApproved(false);
            pendingApprovals.setPending(true);
            contributionsPendingApprovalsRepository.save(pendingApprovals);
            AuditTrail auditTrail = AuditTrail.builder()
                    .action("Contribution Approval")
                    .description("Contribution Approval Saved.")
                    .capturedBy(approvedBy)
                    .build();
            auditTrailRepository.save(auditTrail);
            if (pendingApprovals.getApprovalCount() > 1) {
                pendingApprovals.setApprovalStaged(true);
                pendingApprovals.setApprovalProcessed(true);
                pendingApprovals.setApproved(true);
                pendingApprovals.setPending(false);

                ContributionsPendingApprovals contributionsPendingApprovals = contributionsPendingApprovalsRepository.save(pendingApprovals);
                updateGroupEditContribution(contributionsPendingApprovals);

                sendEditContributionAcceptedText(groupWrapper, memberPhoneNumber, memberName, language);

                AuditTrail trail = AuditTrail.builder()
                        .action("Contribution Approval")
                        .description("Contribution Approval Saved.")
                        .capturedBy(approvedBy)
                        .build();
                auditTrailRepository.save(trail);
                createNotification(groupWrapper.getId(), "Final contribution approval done", groupWrapper.getName());
                return new UniversalResponse("success", getResponseMessage("approvalSuccessful"));
            }
            createNotification(groupWrapper.getId(), "Initial contribution approval done", groupWrapper.getName());
            return new UniversalResponse("success", String.format(getResponseMessage("initialApprovalSuccessful")));
        }).publishOn(Schedulers.boundedElastic());

    }


    private void sendEditContributionAcceptedText(GroupWrapper groupWrapper, String memberPhoneNumber, String
            memberName, String language) {
        GroupWrapper group = chamaKycService.getMonoGroupById(groupWrapper.getId());
        if (group != null && group.isActive()) {
            String groupName = group.getName();
            notificationService.sendInitiatorEditContributionAcceptedText(groupName, memberPhoneNumber, memberName, language);
            chamaKycService.getFluxGroupMembers(group.getId())
                    .subscribe(member -> notificationService.sendMembersEditContributionAcceptedText(member.getFirstname(), memberName, groupName, member.getPhonenumber(), language));
            AuditTrail trail = AuditTrail.builder()
                    .action("Edit Contributions SMS")
                    .description("Edit Contribution SMS Send.")
                    .capturedBy(memberPhoneNumber)
                    .build();
            auditTrailRepository.save(trail);
            createNotification(groupWrapper.getId(), "Edit Contribution SMS Send.", group.getName());
        } else {
            log.error("Could not send Kit Transfer SMS. Group not found.");
        }
    }

    private void updateGroupEditContribution(ContributionsPendingApprovals approvals) {

        GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(approvals.getGroupId());
        if (groupWrapper == null) {
            return;
        }
        Optional<Contributions> optionalContribution = contributionsRepository.findByGroupIdAndActiveTrueAndSoftDeleteFalse(groupWrapper.getId());
        if (optionalContribution.isPresent()) {
            LocalDate now = LocalDate.now();
            LocalDate dueDate = null;
            int reminder;
            if (approvals.getFrequency().equalsIgnoreCase("Daily")) {
                reminder = 1;
                dueDate = now.plusDays(reminder);
            } else if (approvals.getFrequency().equalsIgnoreCase("Weekly")) {
                reminder = 7;
                dueDate = now.plusDays(reminder);
            } else if (approvals.getFrequency().equalsIgnoreCase("Monthly")) {
                reminder = 30;
                dueDate = now.plusDays(reminder);
            }

            Contributions contributions = optionalContribution.get();
            contributions.setContributionAmount(approvals.getContributionAmount());
            contributions.setLastModifiedDate(new Date());
            contributions.setLastModifiedBy(approvals.getApprovedBy());
            contributions.setContributionType(approvals.getContributionType());
            contributions.setAmountType(approvals.getAmountType());
            contributions.setActive(true);
            contributions.setAutoShareOut(approvals.getAutoShareOut());
            contributions.setManualShareOut(approvals.getManualShareOut());
            contributions.setDaysBeforeDue(approvals.getDaysBeforeDue());
            contributions.setContributionDate(approvals.getContributionDate());
            contributions.setShareoutDate(approvals.getShareoutDate());
            contributions.setStartDate(approvals.getContributionDate());
            contributions.setFrequency(approvals.getFrequency());
            contributions.setIspercentage(true);
            contributions.setPenalty(approvals.getPenalty());
            contributions.setReminder(approvals.getReminder());
            contributions.setScheduleType(approvals.getScheduleType());
            contributions.setLoanInterest(approvals.getLoanInterest());
            contributions.setPaymentPeriod(approvals.getPaymentPeriod());
            contributions.setWelfareAmt(approvals.getWelfareAmt());
            contributions.setDuedate(dueDate);
            contributionsRepository.save(contributions);
            AuditTrail trail = AuditTrail.builder()
                    .action("Update Edit Contributions")
                    .description("Contribution Details Updated.")
                    .capturedBy(approvals.getApprovedBy())
                    .build();
            auditTrailRepository.save(trail);
            createNotification(groupWrapper.getId(), "Contribution details updated by officials", groupWrapper.getName());
        }

    }

    private void sendDeclinedEditContributionText(GroupWrapper groupWrapper, String memberName, String
            phoneNumber, String language) {
        GroupWrapper group = chamaKycService.getMonoGroupById(groupWrapper.getId());
        if (group != null && group.isActive()) {
            String groupName = group.getName();
            notificationService.sendEditContributionDeclineText(groupName, memberName, phoneNumber, language);
            chamaKycService.getFluxGroupMembers(group.getId())
                    .subscribe(member -> notificationService.sendEditContributionDeclineTextToGroup(memberName, groupName, member.getFirstname(), member.getPhonenumber(), member.getLanguage()));
            AuditTrail trail = AuditTrail.builder()
                    .action("Edit Contributions SMS")
                    .description("Edit Contribution Decline SMS Send")
                    .capturedBy(memberName)
                    .build();
            auditTrailRepository.save(trail);
            createNotification(groupWrapper.getId(), "Send Edit Decline Sms To Officials", groupName);
        } else {
            log.error("Could not send Kit Transfer SMS. Group not found.");
        }
    }


    @Override
    public Mono<UniversalResponse> getKitBalance(KitBalanceWrapper req) {
        return Mono.fromCallable(() -> {
            long groupId = req.getGroupId();
            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(groupId);
            if (groupWrapper == null) {
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"));
            }
            double savingBalance = calculateBalance(groupId, "saving");
            double welfareBalance = calculateBalance(groupId, "welfare");
            double fineBalance = calculateBalance(groupId, "fine");
            double projectBalance = calculateBalance(groupId, "project");

            //DEDUCTIONS
            double savingDeducted = calculateTransferBalance(groupId, "saving");
            double welfareDeducted = calculateTransferBalance(groupId, "welfare");
            double fineDeducted = calculateTransferBalance(groupId, "fine");
            double projectDeducted = calculateTransferBalance(groupId, "project");
            List<LoansDisbursed> loansDisbursed = loansdisbursedRepo.findAllByGroupIdAndStatusAndSoftDeleteFalseOrderByIdAsc(groupId, PaymentEnum.YET_TO_PAY.name());

            double loansDisbursedSum = loansDisbursed
                    .stream()
                    .mapToDouble(LoansDisbursed::getDueamount)
                    .sum();

            List<LoansDisbursed> loansDisbursedList = loansdisbursedRepo.findAllByGroupIdAndStatusAndSoftDeleteFalseOrderByIdAsc(groupId, PaymentEnum.FULLY_PAID.name());

            double paidLoan = loansDisbursedList.stream().mapToDouble(LoansDisbursed::getDueamount).sum();

            savingBalance = savingBalance - savingDeducted;
            welfareBalance = welfareBalance - welfareDeducted;
            fineBalance = fineBalance - fineDeducted;
            projectBalance = projectBalance - projectDeducted;
            //handle the trailing Zero formatting
            savingBalance = formatAmount(savingBalance);
            welfareBalance = formatAmount(welfareBalance);
            fineBalance = formatAmount(fineBalance);
            projectBalance = formatAmount(projectBalance);
            paidLoan = formatAmount(paidLoan);
            loansDisbursedSum = formatAmount(loansDisbursedSum);
            double loanPendingRepayment = loansDisbursedSum - paidLoan;

            double totalBalance = savingBalance + welfareBalance + fineBalance + projectBalance;
            //todo:: Create and return the response
            return new UniversalResponse("success", "Kit balance", Map.of("savingBalance", savingBalance, "loanBalance", loanPendingRepayment, "welfareBalance", welfareBalance, "fineBalance", fineBalance, "projectBalance", projectBalance, "totalBalance", totalBalance));

        }).publishOn(Schedulers.boundedElastic());
    }


    @Override
    public Mono<UniversalResponse> kitTransfer(KitTransferWrapper req) {
        return Mono.fromCallable(() -> {
            Long groupId = req.getGroupId();
            Integer amount = req.getAmount();
            String from = req.getSourceAccount();
            String to = req.getDestinationAccount();
            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(groupId);
            if (groupWrapper == null) {
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"));
            }

            MemberWrapper memberWrapper = chamaKycService.searchMonoMemberByPhoneNumber(req.getUsername());
            if (memberWrapper == null) {
                return new UniversalResponse("fail", getResponseMessage("memberNotFound"));
            }

            //todo::from and to must be this "loan" or "saving" or "welfare" or "fine" or "loan"
            if (amount < 1) {
                return new UniversalResponse("fail", "Please enter a valid amount");
            }
            Optional<Contributions> checkContribution = contributionsRepository.findByGroupIdAndActiveTrueAndSoftDeleteFalse(groupId);

            if (checkContribution.isEmpty()) {
                return new UniversalResponse("fail", getResponseMessage("contributionNotFound"));
            }
            Contributions contributions = checkContribution.get();
            //todo::check if source acc has balance > amount
            Double sourceBalance = calculateBalance(groupId, from);
            if (sourceBalance < amount) {
                return new UniversalResponse("fail", "Insufficient Balance, You can not transfer Amount that is Higher than " + from + " account.");
            }
            //todo:: do the actual transfer
            saveKitTransferAwaitingApprovals(req, groupId, contributions, amount, from, memberWrapper);

            return new UniversalResponse("success", "Kit transfer successful. " + " Ksh. " + amount + " Has been Transferred from " + from + " to " + to + " waiting approvals.");
        }).publishOn(Schedulers.boundedElastic());
    }

    private void saveKitTransferAwaitingApprovals(KitTransferWrapper req, Long groupId, Contributions
            contributions, Integer amount, String from, MemberWrapper memberWrapper) {
        KitTransferPendingApprovals pendingApprovals = new KitTransferPendingApprovals();
        pendingApprovals.setCreator(memberWrapper.getFirstname().concat(" ") + memberWrapper.getLastname());
        pendingApprovals.setCreatorPhoneNumber(memberWrapper.getPhonenumber());
        pendingApprovals.setGroupAccountId(groupId);
        pendingApprovals.setGroupId(groupId);
        pendingApprovals.setContributionId(contributions.getId());
        pendingApprovals.setAmount(amount);
        pendingApprovals.setTransactionId(generateTransactionId());
        pendingApprovals.setPaymentStatus(PaymentEnum.PAYMENT_PENDING.name());//ON APPRVAL --SUCCESS
        pendingApprovals.setTransactionDate(new Date());
        pendingApprovals.setPaymentType(from);
        pendingApprovals.setCreatedOn(new Date());
        pendingApprovals.setIsDebit('Y');
        pendingApprovals.setIsCredit('N');
        pendingApprovals.setNarration("kit transfer to " + req.getDestinationAccount());
        pendingApprovals.setCreatedBy(req.getUsername());
        pendingApprovals.setTransactionDate(new Date());
        pendingApprovals.setApprovedBy(new JsonObject().toString());
        pendingApprovals.setApprovalCount(0);
        pendingApprovals.setApproved(false);
        pendingApprovals.setPending(true);
        pendingApprovals.setSourceAccount(req.getSourceAccount());
        pendingApprovals.setDestianationAccount(req.getDestinationAccount());
        KitTransferPendingApprovals contributionPayment = kitTransferPendingApprovalsRepository.save(pendingApprovals);
        AuditTrail trail = AuditTrail.builder()
                .action("Kit Transfer")
                .description("Kit Transfer Waiting for officials")
                .capturedBy(memberWrapper.getImsi())
                .build();
        auditTrailRepository.save(trail);
        sendKitTransferSmsToOfficials(contributionPayment, req, memberWrapper);
        creatNotification(groupId, contributions.getName(), "kit transfer initiated, waiting for officials");
        log.info("AUDIT LOG STAGED::: {}", trail);

    }

    private void creatNotification(Long groupId, String groupName, String message) {
        Notifications notifications = new Notifications();
        notifications.setGroupId(groupId);
        notifications.setGroupName(groupName);
        notifications.setMessage(message);
        notificationsRepository.save(notifications);
        log.info("NOTIFICATION SAVED::: {}", notifications);
    }

    @Async
    protected void sendKitTransferSmsToOfficials(KitTransferPendingApprovals
                                                         contributionPayment, KitTransferWrapper req, MemberWrapper memberWrapper) {
        GroupWrapper group = chamaKycService.getMonoGroupById(contributionPayment.getGroupId());
        if (group != null && group.isActive()) {
            String memberName = String.format("%s %s", memberWrapper.getFirstname(), memberWrapper.getLastname());
            String language = memberWrapper.getLanguage();
            String groupName = group.getName();
            double amount = req.getAmount();
            String from = req.getSourceAccount();
            String to = req.getDestinationAccount();
            chamaKycService.getGroupOfficials(group.getId())
                    .subscribe(member -> notificationService.sendKitTransferSms(member.getFirstname(), memberName, groupName, amount, member.getPhonenumber(), from, to, language));
        } else {
            log.error("Could not send Kit Transfer SMS. Group not found.");
        }

    }


    private void kitTransferFrom(KitTransferPendingApprovals transfer, Accounts accounts) {
        ContributionPayment kitPaymentFrom = new ContributionPayment();
        kitPaymentFrom.setGroupAccountId(transfer.getGroupAccountId());
        kitPaymentFrom.setGroupId(transfer.getGroupId());
        kitPaymentFrom.setContributionId(transfer.getContributionId());
        kitPaymentFrom.setAmount(transfer.getAmount());
        kitPaymentFrom.setTransactionId(transfer.getTransactionId());
        kitPaymentFrom.setPaymentStatus(PaymentEnum.KIT_TRANSFER_SUCCESS.name());
        kitPaymentFrom.setTransactionDate(new Date());
        kitPaymentFrom.setPaymentType(transfer.getSourceAccount());
        kitPaymentFrom.setIsDebit(transfer.getIsDebit());
        kitPaymentFrom.setIsCredit(transfer.getIsCredit());
        kitPaymentFrom.setShareOut('N');
        kitPaymentFrom.setIsPenalty(false);
        kitPaymentFrom.setIsCombinedPayment(false);
        kitPaymentFrom.setFirstDeposit(false);
        kitPaymentFrom.setPaymentForType("TF");
        kitPaymentFrom.setCreatedOn(new Date());
        kitPaymentFrom.setNarration(transfer.getNarration());
        kitPaymentFrom.setCreatedBy(transfer.getCreatedBy());
        kitPaymentFrom.setTransactionDate(new Date());
        kitPaymentFrom.setPaidIn((double) 0);
        kitPaymentFrom.setPaidOut(Double.valueOf(transfer.getAmount()));
        kitPaymentFrom.setPaidBalance(accounts.getAccountbalance() - Double.valueOf(transfer.getAmount()));
        kitPaymentFrom.setActualBalance(accounts.getAccountbalance() - Double.valueOf(transfer.getAmount()));
        contributionsPaymentRepository.save(kitPaymentFrom);
    }

    private void kitTransferTo(KitTransferPendingApprovals transfer, Accounts accounts) {
        ContributionPayment kitPaymentTo = new ContributionPayment();
        kitPaymentTo.setGroupAccountId(transfer.getGroupAccountId());
        kitPaymentTo.setGroupId(transfer.getGroupId());
        kitPaymentTo.setContributionId(transfer.getContributionId());
        kitPaymentTo.setAmount(transfer.getAmount());
        kitPaymentTo.setTransactionId(generateTransactionId());
        kitPaymentTo.setPaymentStatus(PaymentEnum.PAYMENT_SUCCESS.name());
        kitPaymentTo.setTransactionDate(new Date());
        kitPaymentTo.setPaymentType(transfer.getDestianationAccount());
        kitPaymentTo.setIsDebit('N');
        kitPaymentTo.setIsCredit('Y');
        kitPaymentTo.setShareOut('N');
        kitPaymentTo.setFirstDeposit(false);
        kitPaymentTo.setIsCombinedPayment(false);
        kitPaymentTo.setIsPenalty(false);
        kitPaymentTo.setPaymentForType("TF");
        kitPaymentTo.setCreatedOn(new Date());
        kitPaymentTo.setNarration("Received kit transfer from  " + transfer.getSourceAccount());
        kitPaymentTo.setCreatedBy(transfer.getCreatedBy());
        kitPaymentTo.setTransactionDate(new Date());
        kitPaymentTo.setPaidIn(Double.valueOf(transfer.getAmount()));
        kitPaymentTo.setPaidOut((double) 0);
        kitPaymentTo.setPaidBalance(accounts.getAccountbalance() + Double.valueOf(transfer.getAmount()));
        kitPaymentTo.setActualBalance(accounts.getAccountbalance() + Double.valueOf(transfer.getAmount()));
        contributionsPaymentRepository.save(kitPaymentTo);
    }

    private Double calculateBalance(Long groupId, String paymentType) {
        List<ContributionPayment> contributionPaymentList = contributionsPaymentRepository.findAllByGroupIdAndPaymentTypeAndPaymentStatusAndSoftDeleteFalseOrderByIdDesc(groupId, paymentType, PaymentEnum.PAYMENT_SUCCESS.name());
        double balance = 0.0;

        for (ContributionPayment contributionPayment : contributionPaymentList) {
            balance += contributionPayment.getAmount();
        }
        balance = formatAmount(balance);

        return balance;
    }


    private Double calculateTransferBalance(Long groupId, String paymentType) {
        List<ContributionPayment> paymentList = contributionsPaymentRepository.findAllByGroupIdAndPaymentTypeAndPaymentStatusAndIsDebitAndSoftDeleteFalseOrderByIdDesc(groupId, paymentType, PaymentEnum.KIT_TRANSFER_SUCCESS.name(), 'Y');

        double deduction = 0.0;

        for (ContributionPayment payment : paymentList) {
            deduction += payment.getAmount();
        }
        deduction = formatAmount(deduction);

        return deduction;
    }


    @Override
    public Mono<UniversalResponse> accountLookup(String phoneNumber) {
        return Mono.fromCallable(() -> {
            //hardcoded for now this accountnumber 0001090021322
            return new UniversalResponse("success", "Account lookup successful", Map.of("accountNumber", "0001090021322", "phoneNumber", phoneNumber));
        });
    }

    @Override
    public Mono<UniversalResponse> shareOutsPreview(String userName, Long groupId, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return Mono.fromCallable(() -> getGroupMemberShareOuts(userName, groupId, pageable))
                .publishOn(Schedulers.boundedElastic()).map(res -> new UniversalResponse("success",
                        "Group share out preview", res));
    }

    private List<ShareOutsMapper> getGroupMemberShareOuts(String userName, Long groupId, Pageable pageable) {
        return shareOutsPaymentRepository.findAllByGroupIdAndPaymentStatusAndSoftDeleteFalseOrderByIdAsc(groupId, PaymentEnum.PAYMENT_SUCCESS.name(), pageable)
                .stream()
                .map(mapShareOutsToWrapperResponse(userName, groupId))
                .collect(Collectors.toList());
    }


    //function to generate TransactionId for kitTransfer start with "KIT" and 10 random numbers
    private String generateTransactionId() {
        String transactionId = "KIT";
        Random random = new Random();
        for (int i = 0; i < 9; i++) {
            transactionId += random.nextInt(9);
        }
        return transactionId;
    }


    @Transactional
    @Override
    public Mono<UniversalResponse> shareOuts(ShareOutsWrapper wrapper) {
        return Mono.fromCallable(() -> {
            if (wrapper.getGroupId() == null) {
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"));
            }
            Group checkGroup = groupRepository.findById(wrapper.getGroupId()).orElse(null);
            if (checkGroup == null) {
                return new UniversalResponse("fail", GROUP_NOT_FOUND);
            }
            long groupId = checkGroup.getId();
            String groupName = checkGroup.getName();
            if (!checkGroup.isActive()) {
                return new UniversalResponse("fail", getResponseMessage("groupIsInActive"));
            }
            if (!checkGroup.getCanWithdraw()) {
                return new UniversalResponse("fail", getResponseMessage("allowGroupWithdrawal"));
            }

            MemberWrapper member = chamaKycService.searchMonoMemberByPhoneNumber(wrapper.getTreasurerPhone());
            if (member == null) {
                return new UniversalResponse("fail", MEMBER_NOT_FOUND);
            }

            long treasurerId = member.getId();
            String treasurerPhone = member.getPhonenumber();
            String notTreasurerMessage = "Dear, " + member.getFirstname().concat(" " + member.getLastname()) + ", you must be a Treasurer in group " + groupName;

            GroupMemberWrapper groupMembership = chamaKycService.getMonoGroupMembershipByGroupIdAndMemberId(groupId, treasurerId);
            if (groupMembership == null)
                return new UniversalResponse("fail", getResponseMessage("memberIsNotPartOfGroup"));

            if (groupMembership.getTitle().isEmpty()) {
                return new UniversalResponse("fail", getResponseMessage("noOfficialTitle"));
            }

            if (!groupMembership.getTitle().equals("Treasurer")) {
                return new UniversalResponse("fail", notTreasurerMessage);
            }

            Contributions contributions = contributionsRepository.findFirstByGroupIdAndActiveTrueAndSoftDeleteFalse(groupId);

            if (contributions == null) {
                return new UniversalResponse("fail", getResponseMessage("contributionScheduleNotFound"));
            }

            LoanProducts loanProducts = loanproductsRepository.findFirstByGroupIdAndIsActiveTrueAndSoftDeleteFalse(checkGroup.getId());
            if (loanProducts == null) {
                return new UniversalResponse("fail", getResponseMessage("loanProductNotFound"));
            }

            if (contributions.getManualShareOut() == null || contributions.getAutoShareOut() == null) {
                return new UniversalResponse("fail", getResponseMessage("shareOutNotSet"));
            }

            LocalDate startDate = LocalDate.now();
            if (contributions.getShareoutDate() == null) {
                return new UniversalResponse("fail", getResponseMessage("shareOutDateNotSet"));
            }

            LocalDate endDate = contributions.getShareoutDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

            long days_difference = ChronoUnit.DAYS.between(startDate, endDate);

            if (days_difference > 1) {
                return new UniversalResponse("fail", "Share outs date is " + contributions.getShareoutDate());
            }

            if (contributions.getManualShareOut().equals(true)) {
                return new UniversalResponse("fail", getResponseMessage("manualShareOutsEnabled"));
            }

            if (contributions.getAutoShareOut().equals(false)) {
                return new UniversalResponse("fail", getResponseMessage("automaticShareOutsDisabled"));
            }

            Accounts accounts = accountsRepository.findFirstByGroupIdAndSoftDeleteFalse(checkGroup.getId());
            if (accounts == null) {
                return new UniversalResponse("fail", getResponseMessage("groupAccountNotFound"));
            }

            if (accounts.getAccountdetails().equals("DEFAULT_ACCOUNT")) {
                return new UniversalResponse("fail", getResponseMessage("groupHasNoAccount"));

            }

            List<ShareOutsPayment> paymentList = shareOutsPaymentRepository.findAllByGroupIdAndSoftDeleteFalseOrderByIdAsc(groupId);
            if (paymentList.isEmpty()) {
                return new UniversalResponse("fail", String.format(getResponseMessage("shareOutsPaymentNotFound"), checkGroup.getName()));
            }

            double groupContribution = paymentList.stream().mapToDouble(ShareOutsPayment::getTotalContribution).sum();

            groupContribution = formatAmount(groupContribution);

            List<LoansDisbursed> loansDisbursedList = loansdisbursedRepo.findAllByGroupIdAndStatusAndSoftDeleteFalseOrderByIdAsc(groupId, PaymentEnum.FULLY_PAID.name());


            double loanInterest = loansDisbursedList.stream().mapToDouble(LoansDisbursed::getInterest).sum();

            double fineBalance = calculateBalance(groupId, TypeOfContribution.fine.name());
            double fineDeductions = calculateTransferBalance(groupId, TypeOfContribution.fine.name());

            log.info("FINES  {}, DEDUCTIONS {}, REMAINING AMOUNT {} ", fineBalance, fineDeductions, fineBalance - fineDeductions);

            //TODO:: FINES CONTRIBUTIONS FROM MENU
            fineBalance = formatInterestAmount(fineBalance);
            fineDeductions = formatInterestAmount(fineDeductions);
            fineBalance = fineBalance - fineDeductions;

            loanInterest = formatInterestAmount(loanInterest);

            double totalInterest = loanInterest + fineBalance;

            totalInterest = formatInterestAmount(totalInterest);

            log.info("GROUP FINES {}, GROUP LOANS {}, TOTAL GROUP INTEREST(FINE&LOANS) {}", fineBalance, loanInterest, loanInterest + fineBalance);

            double savingsBalance = calculateBalance(groupId, TypeOfContribution.saving.name());

            double savingsDeductions = calculateTransferBalance(groupId, TypeOfContribution.saving.name());

            //TODO:: SAVINGS CONTRIBUTIONS FROM MENU
            savingsBalance = savingsBalance - savingsDeductions;

            log.info("SAVING BALANCE {} ---- SAVINGS TRANSFERRED {},  ACTUAL BALANCE( SAVINGS BALANCE - SAVINGS DEDUCTIONS) {}", savingsBalance, savingsDeductions, savingsBalance);

            log.info("TOTAL SHARE OUTS PAYMENTS {}, TOTAL SAVING BALANCE {}, DIFFERENCE(SAVINGSBALANCE-GROUP  CONTRIBUTION) {}", groupContribution, savingsBalance, savingsBalance - groupContribution);
            log.info("TOTAL SAVING BALANCE {}, TOTAL GROUP BALANCE {}", savingsBalance, groupContribution);


            for (ShareOutsPayment payment :
                    paymentList) {
                String phoneNumber = payment.getPhoneNumber();
                String coreAccount = payment.getCoreAccount();

                MemberWrapper mchamaMember = chamaKycService.searchMonoMemberByPhoneNumber(phoneNumber);
                if (mchamaMember == null) {
                    sendAnonymousShareOuts(checkGroup.getId(), phoneNumber);
                    continue;
                }
                long memberId = mchamaMember.getId();

                double amount = payment.getTotalContribution();
                amount = formatAmount(amount);
                log.info("MEMBER CONTRIBUTION FOR {}, ====== AMOUNT {} ", phoneNumber, amount);

                //TODO:: MEMBER PERCENTAGE RATE
                double memberPercentage;

                if (savingsBalance < groupContribution) {

                    memberPercentage = (amount / savingsBalance) * 100;

                    log.info("MEMBER PERCENTAGE NOT OK {}, ::::: AMOUNT {}, MEMBER {} ", memberPercentage, amount, phoneNumber);
                    //TODO:: DISABLE WELFARE AND PROJECT BALANCES

                } else {
                    memberPercentage = (amount / groupContribution) * 100;
                    log.info("MEMBER PERCENTAGE OK {}, ::::: AMOUNT {}, MEMBER {} ", memberPercentage, amount, phoneNumber);
                }
                memberPercentage = formatAmount(memberPercentage);
                log.info("FINAL MEMBER PERCENTAGE {}, ::: FOR MEMBER {} ", memberPercentage, phoneNumber);

                double memberEarnings = (memberPercentage / 100) * totalInterest;
                log.info("MEMBER EARNING {} ", memberEarnings);
                memberEarnings = formatAmount(memberEarnings);
                log.info("MEMBER EARNING {}, FINAL {} ", memberEarnings, amount + memberEarnings);
                double finalAmount = amount + memberEarnings;

                finalAmount = formatAmount(finalAmount);

                List<LoansDisbursed> memberLoansList = loansdisbursedRepo.findAllByGroupIdAndMemberIdAndSoftDeleteFalseOrderByIdAsc(groupId, memberId);
                List<Fines> memberFinesList = finesRepository.findAllByGroupIdAndMemberIdAndSoftDeleteFalseOrderByIdAsc(groupId, memberId);
                double memberLoans = memberLoansList.stream().mapToDouble(LoansDisbursed::getDueamount).sum();
                double memberFines = memberFinesList.stream().mapToDouble(Fines::getFineBalance).sum();
                log.info("MEMBER LOANS {}, FINES {}, TOTALS(FINE&LOANS) {}", memberLoans, memberFines, memberFines + memberFines);

                if (memberLoans < 1) {
                    memberLoans = 0.0;
                } else {
                    memberLoans = formatAmount(memberLoans);
                }
                if (memberFines < 1) {
                    memberFines = 0.0;
                } else {
                    memberFines = formatAmount(memberFines);
                }
                log.info("NEW MEMBER LOANS {}, FINES {}, TOTAL DEDUCTIONS {} ", memberLoans, memberFines, memberLoans + memberFines);
                log.info("MEMBER EARNINGS {}, FINES {}, TOTAL DEDUCTIONS {} ", memberEarnings, memberFines, memberLoans + memberFines);

                double memberDeductions = memberLoans + memberFines;

                double totalMemberEarnings = finalAmount - memberDeductions;

                if (totalMemberEarnings < 0) {
                    notificationService.sendNoShareOutWithdrawalRequestFailureText(phoneNumber, checkGroup.getName(), mchamaMember.getFirstname(), memberLoans, mchamaMember.getLanguage());
                    continue;
                }

                log.info("PERCENTAGE FOR MEMBER {} ==== IS {} AND FINAL AMOUNT {} =====> TO BE EARNED {} ", phoneNumber, memberPercentage, memberEarnings, totalMemberEarnings);

                ShareOutsPendingDisbursement shareOutsPendingDisbursement = shareOutsPendingDisbursementRepo.findFirstByGroupIdAndPhoneNumberAndPendingTrueAndStatusAndSoftDeleteFalseOrderByIdDesc(groupId, phoneNumber, PaymentEnum.SHARE_OUT_PENDING_DISBURSEMENT.name());
                if (shareOutsPendingDisbursement == null) {
                    log.info("SHARE OUTS IN GROUP {}, FOR MEMBER {}, AND AMOUNT {} NEW!!", groupName, phoneNumber, totalMemberEarnings);
                    ShareOutsPendingDisbursement pendingDisbursement = new ShareOutsPendingDisbursement(coreAccount, phoneNumber, groupName, groupId, PaymentEnum.SHARE_OUT_PENDING_DISBURSEMENT.name(), totalMemberEarnings, treasurerPhone);
                    shareOutsPendingDisbursementRepo.save(pendingDisbursement);
                    //deactivate share outs payment
                    List<ShareOutsPayment> memberPaymentList = shareOutsPaymentRepository.findAllByGroupIdAndPhoneNumberAndSoftDeleteFalseOrderByIdAsc(groupId, phoneNumber);
                    if (!memberPaymentList.isEmpty()) {
                        memberPaymentList.parallelStream().forEach(contrib -> {
                            contrib.setSoftDelete(true);
                            shareOutsPaymentRepository.save(contrib);
                        });
                    }

                    //TODO:: DEACTIVATE SHARE OUTS
                    List<ShareOuts> shareOutsList = shareOutsRepository.findAllByGroupIdAndPhoneNumberAndSoftDeleteFalseOrderByIdAsc(groupId, phoneNumber);
                    if (!shareOutsList.isEmpty()) {
                        shareOutsList.parallelStream().forEach(share -> {
                            share.setSoftDelete(true);
                            shareOutsRepository.save(share);
                        });
                    }

                } else {
                    log.info("GROUP {}, MEMBER {}, AMOUNT {} ALREADY STAGED", groupName, phoneNumber, totalMemberEarnings);

                }
            }

            List<ShareOutsPendingDisbursement> shareOutsPendingDisbursements = shareOutsPendingDisbursementRepo.findAllByGroupIdAndPendingTrueAndStatusAndSoftDeleteFalseOrderByIdDesc(groupId, PaymentEnum.SHARE_OUT_PENDING_DISBURSEMENT.name());

            if (paymentList.size() == shareOutsPendingDisbursements.size()) {
                return new UniversalResponse("success", String.format(getResponseMessage("shareOutsGeneratedPending"), checkGroup.getName()));
            }
            auditTrail("share out contributions", "share out contributions initiated, waiting disbursement", treasurerPhone);
            creatNotification(groupId, checkGroup.getName(), "share out contributions waiting disbursement initiated by " + treasurerPhone);

            return new UniversalResponse("success", String.format(getResponseMessage("shareOutsGenerated"), checkGroup.getName()));
        }).publishOn(Schedulers.boundedElastic());
    }


    @Async
    protected void disableWelfareAndProjectsContributions(long groupId, String treasurerPhone) {
        List<ContributionPayment> projectLists = contributionsPaymentRepository.findAllByGroupIdAndPaymentTypeAndSoftDeleteFalseOrderByIdAsc(groupId, TypeOfContribution.project.name());
        List<ContributionPayment> welfareLists = contributionsPaymentRepository.findAllByGroupIdAndPaymentTypeAndSoftDeleteFalseOrderByIdAsc(groupId, TypeOfContribution.welfare.name());
        log.info("PROJECTS LIST {}, :::: WELFARE LIST {}", projectLists.size(), welfareLists.size());
        if (!projectLists.isEmpty()) {
            projectLists.parallelStream().forEach(project -> {
                project.setLastModifiedDate(new Date());
                project.setLastModifiedBy(treasurerPhone);
                project.setPaymentStatus(PaymentEnum.SHARE_OUT_COMPLETED_SUCCESSFULLY.name());
                project.setSoftDelete(true);
                contributionsPaymentRepository.save(project);
            });
        }

        if (!welfareLists.isEmpty()) {
            welfareLists.parallelStream().forEach(welfare -> {
                welfare.setLastModifiedDate(new Date());
                welfare.setLastModifiedBy(treasurerPhone);
                welfare.setPaymentStatus(PaymentEnum.SHARE_OUT_COMPLETED_SUCCESSFULLY.name());
                welfare.setSoftDelete(true);
                contributionsPaymentRepository.save(welfare);
            });
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


    @Override
    public Mono<UniversalResponse> approveAccount(CanWithdrawWrapper req, String username) {
        return Mono.fromCallable(() -> {
            Group group = groupRepository.findGroupsById(req.getGroupId());
            if (group == null) {
                return new UniversalResponse("fail", "Group not found");
            }
            Accounts gAccounts = accountsRepository.findAccountsByGroupIdAndIsCoreTrue(req.getGroupId());
            if (gAccounts == null && group.getCbsAccount().length() < 9) {
                return new UniversalResponse("fail", group.getName() + " account has not been generated");
            }
            if (!group.isActive())
                return new UniversalResponse("fail", group.getName() + " Must be Active!");

            group.setCanWithdraw(req.isApprove());
            groupRepository.save(group);
            String msg = req.isApprove() ? "Group account withdrawal has been approved successfully" : "Group account withdrawal has been disabled successfully";
            sendGroupWithdrawalSms(group);

            notificationService.sendPostBankEmail(msg, group.getEmail(), "ACCOUNT WITHDRAWAL " + group.getName());
            //todo:: publish group email notification
            createNotification(group.getId(), "Group Account Approval : " + gAccounts.getAccountdetails(), group.getName());
            return new UniversalResponse("success", msg);
        }).publishOn(Schedulers.boundedElastic());
    }

    private void sendGroupWithdrawalSms(Group group) {
        GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(group.getId());

        if (groupWrapper == null)
            return;
        if (group.isActive() && group.getCanWithdraw().equals(true)) {
            chamaKycService.getFluxGroupMembers(groupWrapper.getId()).subscribe(member -> notificationService.sendGroupEnableWithdrawalSms(member.getFirstname(), member.getLastname(), groupWrapper.getName(), member.getPhonenumber(), member.getLanguage()));
        } else {
            chamaKycService.getFluxGroupMembers(groupWrapper.getId())
                    .subscribe(member -> notificationService.sendGroupDisableWithdrawalSms(member.getFirstname(), groupWrapper.getName(), member.getPhonenumber(), member.getLanguage()));
        }

    }


    @Override
    public Mono<UniversalResponse> editContributionPendingApprovals(PendingApprovalsWrapper request, String user) {
        return Mono.fromCallable(() -> {
            Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
            Page<ContributionsPendingApprovals> groupsPage = contributionsPendingApprovalsRepository.findAllByGroupIdAndSoftDeleteFalseAndPendingTrueOrderByIdDesc(request.getGroupId(), pageable);
            List<ContributionsReportWrapper> repostWrapperList = groupsPage.getContent()
                    .parallelStream()
                    .map(this::getContributionsReportWrapper)
                    .collect(Collectors.toList());
            UniversalResponse response = new UniversalResponse("success", getResponseMessage("editContributionsPendingApprovals"), repostWrapperList);
            response.setMetadata(Map.of("numofrecords", repostWrapperList.size()));
            return response;
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> esbAccountValidation(EsbAccountWrapper wrapper) {
        return Mono.fromCallable(() -> {

            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(wrapper.getGroupId());
            if (groupWrapper == null)

                return new UniversalResponse("fail", getResponseMessage("groupNotFound"));
            MemberWrapper member = chamaKycService.searchMonoMemberByPhoneNumber(wrapper.getPhoneNumber());

            if (member == null) {
                return new UniversalResponse("fail", getResponseMessage("memberNotFound"));
            }

            GroupMemberWrapper groupMembership = chamaKycService.getMonoGroupMembershipByGroupIdAndMemberId(groupWrapper.getId(), member.getId());
            if (groupMembership == null)
                return new UniversalResponse("fail", getResponseMessage("memberIsNotPartOfGroup"));

            if (wrapper.getUserAccount().length() != 13) {
                return new UniversalResponse("fail", getResponseMessage("userWalletAccount"));
            }
            String esbTransactionReference = TransactionIdGenerator.generateTransactionId("USR");
            String requestType = "User Accounts";
            Map<String, String> userAccountRequest = constructCustomerByAccountBody(wrapper.getUserAccount());
            String userAccountBody = gson.toJson(userAccountRequest);
            log.info("user account request {}", userAccountBody);
            String response = webClient.post()
                    .uri(userAccounts)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(userAccountBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribeOn(Schedulers.boundedElastic())
                    .block();
            JsonObject jsonObject = gson.fromJson(response, JsonObject.class);
            log.info("user accounts response {}", response);
            String status = jsonObject.get("status").toString();
            status = status.substring(1, status.length() - 1);
            if (!status.equals("00"))

                return new UniversalResponse("success", getResponseMessage("accountValidationFailure"));

            Map<String, Object> metadata = Map.of(
                    "details", jsonObject.get("data").toString());
            log.info("met data response  {}", metadata);

            return new UniversalResponse("success", getResponseMessage("accountValidationSuccess"), metadata);

        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> listFinesPendingApprovals(FinesPendingApprovalsWrapper wrapper, String username) {
        return Mono.fromCallable(() -> {
            MemberWrapper member = chamaKycService.searchMonoMemberByPhoneNumber(username);
            if (member == null) {
                return UniversalResponse.builder()
                        .status("fail")
                        .message("Member not found")
                        .timestamp(new Date(System.currentTimeMillis()))
                        .build();
            }
            Pageable pageable = PageRequest.of(wrapper.getPage(), wrapper.getSize());
            Page<FinesPendingApprovals> finesPendingApprovalsPage = finesPendingApprovalsRepository.findAllByGroupIdAndApprovedFalseAndPendingTrueAndSoftDeleteFalseOrderByIdDesc(wrapper.getGroupId(), pageable);
            List<FinesPendingApprovalsResponse> finesPendingApprovalsResponseList = finesPendingApprovalsPage.getContent()
                    .parallelStream()
                    .map(this::getFinesPendingApprovalsResponse)
                    .collect(Collectors.toList());
            UniversalResponse response = new UniversalResponse("success", getResponseMessage("finesPendingApprovals"), finesPendingApprovalsResponseList);
            response.setMetadata(Map.of("numberOfRecords", finesPendingApprovalsResponseList.size()));
            return response;
        }).publishOn(Schedulers.boundedElastic());

    }

    @Override
    public Mono<UniversalResponse> approveDeclineFineRequest(FineApprovalRequest request, String username) {
        return Mono.fromCallable(() -> {
            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(request.getGroupId());
            if (groupWrapper == null)
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"));
            MemberWrapper approver = chamaKycService.searchMonoMemberByPhoneNumber(username);

            if (approver == null)
                return new UniversalResponse("fail", getResponseMessage("approverNotFound"));
            FinesPendingApprovals pendingApprovals = finesPendingApprovalsRepository.findByIdAndGroupIdAndApprovedFalseAndPendingTrueAndSoftDeleteFalseOrderByIdDesc(request.getId(), groupWrapper.getId());

            if (pendingApprovals == null)
                return new UniversalResponse("fail", getResponseMessage("finePendingApprovalNotFound"));

            MemberWrapper finedMember = chamaKycService.searchMonoMemberByPhoneNumber(pendingApprovals.getFinedMemberPhoneNumber());

            if (finedMember == null)
                return new UniversalResponse("fail", getResponseMessage("memberNotFound"));

            if (Objects.equals(approver.getPhonenumber(), pendingApprovals.getCreatorPhone()))
                return new UniversalResponse("fail", getResponseMessage("fineApproverCanNotBeCreator"));

            if (Objects.equals(approver.getPhonenumber(), pendingApprovals.getFinedMemberPhoneNumber()))
                return new UniversalResponse("fail", getResponseMessage("cannotApproveOwnFine"));

            GroupMemberWrapper groupMembership = chamaKycService.getMonoGroupMembershipByGroupIdAndMemberId(groupWrapper.getId(), approver.getId());
            if (groupMembership == null)
                return new UniversalResponse("fail", getResponseMessage("memberIsNotPartOfGroup"));


            String memberName = String.format("%s %s", finedMember.getFirstname(), finedMember.getLastname());

            JsonObject approvals = gson.fromJson(pendingApprovals.getApprovedBy(), JsonObject.class);
            String approverName = String.format("%s", approver.getFirstname());


            if (approvals.has(groupMembership.getTitle()))
                return new UniversalResponse("fail", getResponseMessage("youCannotAuthorizeMoreThanOnce"));

            if (groupMembership.getTitle().equals("member"))

                return new UniversalResponse("fail", getResponseMessage("accountsOfficialsApproval"));

            if (!request.isApprove()) {
                pendingApprovals.setApproved(true);
                pendingApprovals.setPending(false);
                pendingApprovals.setStatus("Declined");
                approvals.addProperty(groupMembership.getTitle(), approver.getId());
                pendingApprovals.setApprovalCount(pendingApprovals.getApprovalCount() + 1);
                pendingApprovals.setApprovedBy(approvals.toString());
                finesPendingApprovalsRepository.save(pendingApprovals);
                sendDeclinedFineTextToMembers(groupWrapper.getId(), groupWrapper.getName(), approverName, memberName, pendingApprovals.getFineAmount(), finedMember.getPhonenumber(), finedMember.getLanguage(), pendingApprovals.getFineName());
                auditTrail("group member fines", "group member fines declined successfully.", username);
                creatNotification(groupWrapper.getId(), groupWrapper.getName(), "group member fines declined successfully by " + username);
                return new UniversalResponse("success", getResponseMessage("successfullyDeclinedFine"));
            }

            approvals.addProperty(groupMembership.getTitle(), approver.getId());
            pendingApprovals.setApprovalCount(pendingApprovals.getApprovalCount() + 1);
            pendingApprovals.setApprovedBy(approvals.toString());
            pendingApprovals.setStatus("Pending");
            pendingApprovals.setPending(true);
            pendingApprovals.setApproved(false);

            finesPendingApprovalsRepository.save(pendingApprovals);

            if (pendingApprovals.getApprovalCount() > 1) {
                pendingApprovals.setApproved(true);
                pendingApprovals.setPending(false);
                pendingApprovals.setStatus("Approved");
                finesPendingApprovalsRepository.save(pendingApprovals);
                //todo:: add fine
                Fines fines = new Fines();
                fines.setFineAmount(pendingApprovals.getFineAmount());
                fines.setFineBalance(pendingApprovals.getFineBalance());
                fines.setGroupId(pendingApprovals.getGroupId());
                fines.setMemberId(pendingApprovals.getMemberId());
                fines.setFineName(pendingApprovals.getFineName());
                fines.setFineDescription(pendingApprovals.getFineDescription());
                fines.setPaymentStatus(PaymentEnum.PAYMENT_PENDING.name());
                fines.setTransactionDate(pendingApprovals.getTransactionDate());
                fines.setLastModifiedDate(new Date());
                finesRepository.save(fines);
                sendApprovedFineTextToGroupMembers(groupWrapper.getId(), groupWrapper.getName(), approverName, memberName, pendingApprovals.getFineAmount(), finedMember.getPhonenumber(), finedMember.getLanguage(), pendingApprovals.getFineName());
                auditTrail("group member fines", "group member fines approved successfully.", username);
                creatNotification(groupWrapper.getId(), groupWrapper.getName(), "group member fines approved successfully by " + username);
                return new UniversalResponse("success", getResponseMessage("fineApprovalSuccessful"));
            }
            return new UniversalResponse("success", "First approval success.");
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> getOtherGroupAccountTransactions(Long groupId, Integer page, Integer
            size, String username) {
        Pageable pageable = PageRequest.of(page, size);
        return Mono.fromCallable(() -> getGroupAccountTransactions(groupId, pageable, username))
                .publishOn(Schedulers.boundedElastic()).map(res -> new UniversalResponse("success",
                        getResponseMessage("groupTransactions"), res));
    }

    @Override
    public Mono<UniversalResponse> assignTransaction(List<MemberTransactionsWrapper> memberTransactions,
                                                     long groupId, String username) {
        return Mono.fromCallable(() -> {

            MemberWrapper assigner = chamaKycService.searchMonoMemberByPhoneNumber(username);
            if (assigner == null)
                return new UniversalResponse("fail", getResponseMessage(MEMBER_NOT_FOUND));

            GroupMemberWrapper groupMemberWrapper = chamaKycService.getMonoGroupMembershipByGroupIdAndMemberId(groupId, assigner.getId());
            if (groupMemberWrapper == null)
                return new UniversalResponse("fail", getResponseMessage("memberIsNotPartOfGroup"));

            if (groupMemberWrapper.getTitle().equals("member"))

                return new UniversalResponse("fail", getResponseMessage("accountsOfficialsApproval"));

            String assignerName = String.format("%s %s", assigner.getFirstname(), assigner.getLastname());
            String assignerPhoneNumber = assigner.getPhonenumber();

            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(groupId);
            if (groupWrapper == null)
                return new UniversalResponse("fail", getResponseMessage(GROUP_NOT_FOUND));

            String groupName = groupWrapper.getName();
            List<GroupMembership> groupMemberships = groupMembersRepository.findAllByGroupIdAndActivemembershipTrue(groupWrapper.getId());
            //todo:: check if there exists a chairperson, secretary and treasurer.
            long officials = groupMemberships
                    .parallelStream()
                    .filter(gm -> gm.getTitle()
                            .equalsIgnoreCase("Treasurer")
                            || gm.getTitle()
                            .equalsIgnoreCase("Secretary")
                            || gm.getTitle().equalsIgnoreCase("Chairperson"))
                    .count();
            if (officials < 3) {
                return new UniversalResponse("fail", getResponseMessage("groupNeedsOfficials"));
            }

            List<AssignTransactionPendingApprovals> transactionPendingApprovalsList = new ArrayList<>();
            for (MemberTransactionsWrapper transaction :
                    memberTransactions) {

                if (transaction.getAmount() < 1) {
                    return new UniversalResponse("fail", "Please enter a valid amount!!");
                }

                MemberWrapper memberWrapper = chamaKycService.searchMonoMemberByPhoneNumber(transaction.getPhoneNumber());

                if (memberWrapper == null)
                    return new UniversalResponse("fail", "Member with phone number" + transaction.getPhoneNumber() + "not registered.");

                long memberId = memberWrapper.getId();
                String memberName = String.format("%s %s", memberWrapper.getFirstname(), memberWrapper.getLastname());
                String memberPhoneNumber = memberWrapper.getPhonenumber();
                String language = memberWrapper.getLanguage();

                AssignTransactionPendingApprovals pendingApprovals = assignTransactionPendingApprovalsRepository.findFirstByGroupIdAndOtherTransactionIdAndApprovedFalseAndPendingTrueAndRejectedFalseAndTransactionActedOnFalseOrderByIdDesc(groupWrapper.getId(), transaction.getOtherTransactionId());

                if (!(pendingApprovals == null)) {
                    return new UniversalResponse("fail", getResponseMessage("otherTransactionAlreadyAssigned"));
                }

                AssignTransactionPendingApprovals pendingApprovals1 = assignTransactionPendingApprovalsRepository.findFirstByGroupIdAndOtherTransactionIdAndApprovedTrueAndPendingFalseAndTransactionActedOnTrueOrderByIdDesc(groupWrapper.getId(), transaction.getOtherTransactionId());

                if (!(pendingApprovals1 == null)) {
                    return new UniversalResponse("fail", getResponseMessage("otherTransactionAlreadyApproved"));
                }

                GroupMemberWrapper groupMembership = chamaKycService.getMonoGroupMembershipByGroupIdAndMemberId(groupId, memberId);
                if (groupMembership == null)
                    return new UniversalResponse("fail", getResponseMessage("memberIsNotPartOfGroup"));


                Optional<Contributions> checkContributions = contributionsRepository.findByGroupIdAndActiveTrueAndSoftDeleteFalse(groupId);

                if (checkContributions.isEmpty()) {
                    return new UniversalResponse("fail", getResponseMessage("contributionNotFound"));
                }
                Contributions contributions = checkContributions.get();
                long contributionId = contributions.getId();

                Accounts gAccounts = accountsRepository.findFirstByGroupIdAndSoftDeleteFalse(groupWrapper.getId());
                if (gAccounts == null) {
                    return new UniversalResponse("fail", groupWrapper.getName() + " account has not been generated");
                }
                if (!transaction.getPaymentType().equalsIgnoreCase("saving") && !transaction.getPaymentType().equalsIgnoreCase("project") && !transaction.getPaymentType().equalsIgnoreCase("loan") && !transaction.getPaymentType().equalsIgnoreCase("welfare") && !transaction.getPaymentType().equalsIgnoreCase("fine"))
                    return new UniversalResponse("fail", "Invalid payment type. Valid payment types are saving,project,loan,welfare,fine");

                OtherChannelsBalances otherTransaction = otherChannelsBalancesRepository.findFirstByIdAndGroupIdAndSoftDeleteFalseOrderByIdAsc(transaction.getOtherTransactionId(), groupId);
                if (otherTransaction == null) {
                    return new UniversalResponse("fail", getResponseMessage("otherChannelTransactionNotFound"));
                }

                if (transaction.getAmount() != otherTransaction.getCreditAmount()) {
                    return new UniversalResponse("fail", getResponseMessage("otherTransactionCanNotBeAssigned"));
                }

                Optional<Member> optionalMember = memberRepository.findByImsi(memberPhoneNumber);
                Member member = optionalMember.get();

                String transactionId = otherTransaction.getTransactionId();
                long otherTransactionId = transaction.getOtherTransactionId();
                String paymentType = transaction.getPaymentType();
                double amount = transaction.getAmount();
                //todo:: check existing pending transaction
                OtherChannelsBalances otherChannel = otherChannelsBalancesRepository.findFirstByGroupIdAndTransactionIdAndSoftDeleteFalse(otherTransaction.getGroupId(), otherTransaction.getTransactionId());
                if (!(otherChannel == null)) {
                    if (!(transaction.getAmount() == otherChannel.getCreditAmount())) {
                        return new UniversalResponse("fail", "allocated amount should be " + otherTransaction.getCreditAmount());
                    } else {
                        AssignTransactionPendingApprovals transactionPendingApprovals = new AssignTransactionPendingApprovals(groupId, memberPhoneNumber, memberName, paymentType, member, amount, transactionId, otherTransactionId, contributionId, assignerName, assignerPhoneNumber);
                        transactionPendingApprovalsList.add(transactionPendingApprovals);
                        //todo::send sms for approvals
                        sendAssignedOtherTransactionSms(groupWrapper.getId(), groupName, amount, assignerName, memberName, memberPhoneNumber, otherChannel.getChannel(), language);
                    }
                }

            }
            assignTransactionPendingApprovalsRepository.saveAll(transactionPendingApprovalsList);

            if (transactionPendingApprovalsList.size() > 1) {
                return new UniversalResponse("success", getResponseMessage("otherTransactionsCreatedSuccessfully"));
            }
            auditTrail("other channel transaction", "Other group channel transaction added successfully", assigner.getImsi());
            creatNotification(groupId, groupWrapper.getName(), "Other group channel transaction added successfully by " + assigner.getImsi());
            return new UniversalResponse("success", getResponseMessage("otherTransactionCreatedSuccessfully"));
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> listOtherTransactionsPendingApprovals(OtherTransactionsPendingApprovalsWrapper
                                                                                 wrapper, String username) {
        return Mono.fromCallable(() -> {
            MemberWrapper member = chamaKycService.searchMonoMemberByPhoneNumber(username);
            if (member == null) {
                return new UniversalResponse("fail", getResponseMessage("appUserNotFound"));
            }
            Pageable pageable = PageRequest.of(wrapper.getPage(), wrapper.getSize());
            Page<AssignTransactionPendingApprovals> transactionPendingApprovals = assignTransactionPendingApprovalsRepository.findAllByGroupIdAndApprovedFalseAndPendingTrueAndTransactionActedOnFalseAndSoftDeleteFalseOrderByIdAsc(wrapper.getGroupId(), pageable);
            List<OtherTransactionsPendingApprovalsResponse> pendingApprovalsResponses = transactionPendingApprovals.getContent()
                    .parallelStream()
                    .map(this::getChannelsPendingApprovalsResponse)
                    .collect(Collectors.toList());
            UniversalResponse response = new UniversalResponse("success", getResponseMessage("otherTransactionsPendingApproval"), pendingApprovalsResponses);
            response.setMetadata(Map.of("numberOfRecords", pendingApprovalsResponses.size()));
            return response;
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> approveDeclineOtherTransaction(OtherTransactionApprovalRequest request, String
            username) {
        return Mono.fromCallable(() -> {
            MemberWrapper approver = chamaKycService.searchMonoMemberByPhoneNumber(username);

            if (approver == null)
                return new UniversalResponse("fail", getResponseMessage("appUserNotFound"));
            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(request.getGroupId());

            if (groupWrapper == null)
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"));

            long groupId = groupWrapper.getId();

            AssignTransactionPendingApprovals pendingApprovals = assignTransactionPendingApprovalsRepository.findFirstByIdAndGroupIdAndApprovedFalseAndPendingTrueAndTransactionActedOnFalseOrderByIdDesc(request.getId(), groupWrapper.getId());

            if (pendingApprovals == null)
                return new UniversalResponse("fail", getResponseMessage("otherTransactionPendingApprovalNotFound"));

            long pendingApprovalId = pendingApprovals.getId();
            MemberWrapper creditMember = chamaKycService.searchMonoMemberByPhoneNumber(pendingApprovals.getPhoneNumber());

            if (creditMember == null)
                return new UniversalResponse("fail", getResponseMessage("memberNotFound"));

            if (Objects.equals(approver.getPhonenumber(), creditMember.getPhonenumber()))
                return new UniversalResponse("fail", getResponseMessage("cannotApproveOwnDeposit"));

            GroupMemberWrapper groupMembership = chamaKycService.getMonoGroupMembershipByGroupIdAndMemberId(groupWrapper.getId(), approver.getId());
            if (groupMembership == null)
                return new UniversalResponse("fail", getResponseMessage("makerCheckerIsNotPartOfGroup"));

            GroupMemberWrapper creditCroupMembership = chamaKycService.getMonoGroupMembershipByGroupIdAndMemberId(groupWrapper.getId(), creditMember.getId());
            if (creditCroupMembership == null)
                return new UniversalResponse("fail", getResponseMessage("memberIsNotPartOfGroup"));

            String memberName = String.format("%s %s", creditMember.getFirstname(), creditMember.getLastname());

            JsonObject approvals = gson.fromJson(pendingApprovals.getApprovedBy(), JsonObject.class);
            String approverName = String.format("%s", approver.getFirstname());

            if (Objects.equals(approver, pendingApprovals.getCreatorPhoneNumber()))
                return new UniversalResponse("fail", getResponseMessage("transactionApproverCanNotBeCreator"));


            if (groupMembership.getTitle().equals("member"))

                return new UniversalResponse("fail", getResponseMessage("accountsOfficialsApproval"));

            if (approvals.has(groupMembership.getTitle()))
                return new UniversalResponse("fail", getResponseMessage("youCannotAuthorizeMoreThanOnce"));


            if (!request.isApprove()) {
                pendingApprovals.setApproved(false);
                pendingApprovals.setPending(false);
                pendingApprovals.setRejected(true);
                pendingApprovals.setTransactionActedOn(true);
                pendingApprovals.setApprovedBy(approvals.toString());
                approvals.addProperty(groupMembership.getTitle(), approver.getId());
                pendingApprovals.setApprovalCount(pendingApprovals.getApprovalCount() + 1);
                assignTransactionPendingApprovalsRepository.save(pendingApprovals);
                sendDeclinedOtherTransactionText(groupWrapper.getId(), groupWrapper.getName(), approverName, memberName, pendingApprovals.getAmount(), creditMember.getPhonenumber(), creditMember.getLanguage());
                auditTrail("other channel transaction", "Other group channel transaction decline successfully", username);
                creatNotification(groupId, groupWrapper.getName(), "Other group channel transaction decline successfully by " + username);
                return new UniversalResponse("success", getResponseMessage("successfullyDeclinedTransaction"));
            }
            approvals.addProperty(groupMembership.getTitle(), approver.getId());
            pendingApprovals.setApprovalCount(pendingApprovals.getApprovalCount() + 1);
            pendingApprovals.setApprovedBy(approvals.toString());
            pendingApprovals.setPending(true);
            pendingApprovals.setApproved(false);
            pendingApprovals.setRejected(false);

            assignTransactionPendingApprovalsRepository.save(pendingApprovals);

            if (pendingApprovals.getApprovalCount() > 1) {
                OtherChannelsBalances otherChannelsBalances = otherChannelsBalancesRepository.findFirstByIdAndGroupIdAndSoftDeleteFalseOrderByIdAsc(pendingApprovals.getOtherTransactionId(), pendingApprovals.getGroupId());
                if (otherChannelsBalances == null) {
                    return new UniversalResponse("fail", "Other Transaction not found on the list.");
                }

                if (pendingApprovals.getAmount() == 0) {
                    return new UniversalResponse("fail", "Transaction amount in invalid.");
                }

                if (!pendingApprovals.getAmount().equals(otherChannelsBalances.getCreditAmount())) {
                    return new UniversalResponse("fail", "Transaction amount not allowed!!");
                }

                AssignTransactionPendingApprovals pendingApprovals1 = assignTransactionPendingApprovalsRepository.findFirstByIdAndGroupIdAndOtherTransactionIdAndApprovedTrueAndPendingFalseAndTransactionActedOnTrueOrderByIdDesc(pendingApprovalId, groupId, pendingApprovals.getOtherTransactionId());

                if (!(pendingApprovals1 == null)) {
                    return new UniversalResponse("fail", getResponseMessage("otherTransactionAlreadyApproved"));
                }

                otherChannelsBalances.setCreditAmount(otherChannelsBalances.getCreditAmount() - pendingApprovals.getAmount());

                OtherChannelsBalances otherChannelsBalances1 = otherChannelsBalancesRepository.save(otherChannelsBalances);
                if (otherChannelsBalances1.getCreditAmount() == 0) {
                    otherChannelsBalances1.setTransactionActedOn(true);
                    otherChannelsBalances1.setAmountDepleted(true);
                }

                otherChannelsBalancesRepository.save(otherChannelsBalances1);
                pendingApprovals.setApproved(true);
                pendingApprovals.setPending(false);
                pendingApprovals.setRejected(false);
                pendingApprovals.setTransactionActedOn(true);
                pendingApprovals.setPaymentStatus(PaymentEnum.PAYMENT_SUCCESS.name());
                assignTransactionPendingApprovalsRepository.save(pendingApprovals);
                //toto:: updated member contribution payment
                updatePaymentFromOtherTransactions(pendingApprovals, creditMember, approverName);

                sendApprovedOtherTransactionTextToGroupMembers(groupWrapper.getId(), groupWrapper.getName(), approverName, memberName, pendingApprovals.getAmount(), creditMember.getPhonenumber(), otherChannelsBalances.getChannel(), creditMember.getLanguage());
                auditTrail("other channel transaction", "Other group channel transaction approved successfully", username);
                creatNotification(groupId, groupWrapper.getName(), "Other group channel transaction approved successfully by " + username);
                return new UniversalResponse("success", getResponseMessage("otherTransactionApprovalSuccessful"));
            }


            return new UniversalResponse("success", getResponseMessage("firstOtherTransactionSuccess"));
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> groupStatement(AccountStatementDto statementDto) {
        return Mono.fromCallable(() -> {
            MemberWrapper memberWrapper = chamaKycService.searchMonoMemberByPhoneNumber(statementDto.getPhoneNumber());
            if (memberWrapper == null) {
                return new UniversalResponse("fail", MEMBER_NOT_FOUND);
            }
            String memberName = memberWrapper.getFirstname().concat(" " + memberWrapper.getLastname());

            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(statementDto.getGroupId());
            if (groupWrapper == null) {
                return new UniversalResponse("fail", GROUP_NOT_FOUND);
            }
            Accounts accounts = accountsRepository.findFirstByGroupIdAndSoftDeleteFalse(groupWrapper.getId());
            if (accounts == null) {
                return new UniversalResponse("fail", getResponseMessage("groupAccountNotFound"));
            }
            if (accounts.getAccountdetails().equals("DEFAULT_ACCOUNT")) {
                return new UniversalResponse("fail", getResponseMessage("groupHasNoAccount"));

            }
            String groupAccount = accounts.getAccountdetails();

            Date today = new Date();
            Date dateBefore = new Date(today.getTime() - statementDto.getDays() * 24 * 3600 * 1000); //Subtract n days
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");

            String startDateFormat = simpleDateFormat.format(dateBefore);
            String endDateFormat = simpleDateFormat.format(today);
            String toEmail = statementDto.getEmail();


            Map<String, String> esbStatementRequest = constructStatementBody(groupAccount, startDateFormat, endDateFormat);

            String esbStatementBody = gson.toJson(esbStatementRequest);

            log.info("API CHANNEL STATEMENT REQUEST {}", esbStatementBody);

            String statementResponse = postBankWebClient.post()
                    .uri(statementDataUrl)
                    .headers(httpHeaders -> httpHeaders.setBasicAuth("eclectics", "eclectics123"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(esbStatementBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribeOn(Schedulers.boundedElastic())
                    .block();

            JsonObject statementJsonObject = gson.fromJson(statementResponse, JsonObject.class);
            log.info("API CHANNEL STATEMENT RESPONSE {}", statementJsonObject);

            if (statementJsonObject == null) {
                return new UniversalResponse("fail", "Service unavailable at the moment, please try again later.");
            }

            if (!statementJsonObject.get("status").getAsString().equals("000")) {
                return new UniversalResponse("fail", "Service unavailable at the moment, please try again later.");
            } else {
                String regNumber = statementJsonObject.get("idNumber").getAsString();
                String acctName = statementJsonObject.get("acctName").getAsString();
                String ledgerBalance = statementJsonObject.get("ledgerBalance").getAsString();
                String actualBalance = statementJsonObject.get("actualBalance").getAsString();
                String address = statementJsonObject.get("address").getAsString();

                getGroupInputStreamResourceResponseEntity(groupWrapper.getId(), acctName, regNumber, ledgerBalance, actualBalance, address, groupAccount, toEmail, memberName, statementJsonObject.get("tranData").getAsJsonArray());

                getOtherTransactionsInGroup(groupWrapper.getId(), groupWrapper.getName(), toEmail, groupAccount);
                getLoanTransactionsInGroup(groupWrapper.getId(), groupWrapper.getName(), toEmail);
                auditTrail("group statement", "group statement requested for group " + groupWrapper.getName(), memberWrapper.getImsi());
                creatNotification(groupWrapper.getId(), groupWrapper.getName(), "group statement requested sent to " + toEmail);

                return new UniversalResponse("success", "Account statement has been successfully sent to".concat(" ") + toEmail);

            }

        }).publishOn(Schedulers.boundedElastic());
    }

    @Async
    protected void getLoanTransactionsInGroup(long groupId, String groupName, String toEmail) {
        List<LoansDisbursed> loansDisbursedList = loansdisbursedRepo.findAllByGroupIdAndSoftDeleteFalseOrderByIdAsc(groupId);
        if (!loansDisbursedList.isEmpty()) {
            List<MemberLoanDisbursedList> memberLoanDisbursedListList = new ArrayList<>();
            loansDisbursedList.parallelStream().forEach(loansDisbursed -> {
                Optional<MemberWrapper> memberWrapper = chamaKycService.getMemberDetailsById(loansDisbursed.getMemberId());
                if (memberWrapper.isEmpty()) {
                    return;
                }
                GroupWrapper group = chamaKycService.getMonoGroupById(groupId);
                if (group == null) {
                    return;
                }
                String status;
                if (loansDisbursed.getStatus().equals("FULLY_PAID")) {
                    status = "PAID";
                } else {
                    status = "ACTIVE";
                }

                String pattern = "dd-MM-yyyy";
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
                String dt_of_application = simpleDateFormat.format(loansDisbursed.getCreatedOn());
                MemberWrapper member = memberWrapper.get();
                String memberName = String.format("%s %s", member.getFirstname(), member.getLastname());
                MemberLoanDisbursedList memberLoan = new MemberLoanDisbursedList();
                memberLoan.setGroupName(groupName);
                memberLoan.setMemberName(memberName);
                memberLoan.setMemberPhone(member.getPhonenumber());
                memberLoan.setLoanStatus(status);
                memberLoan.setCreatedOn(dt_of_application);
                memberLoan.setPrincipal(String.valueOf(loansDisbursed.getPrincipal()));
                memberLoan.setDueamount(String.valueOf(loansDisbursed.getDueamount()));
                memberLoan.setInterest(String.valueOf(loansDisbursed.getInterest()));
                memberLoanDisbursedListList.add(memberLoan);

            });

            double borrowed = loansDisbursedList.stream().mapToDouble(LoansDisbursed::getPrincipal).sum();
            double repaymentAmount = loansDisbursedList.stream().mapToDouble(LoansDisbursed::getDueamount).sum();
            double interest = loansDisbursedList.stream().mapToDouble(LoansDisbursed::getInterest).sum();

            ByteArrayInputStream bis = GroupPDFGeneratorUtility
                    .loanDisbursedReport(groupName, borrowed, repaymentAmount, interest, memberLoanDisbursedListList);
            String sendFrom = "mbanking@postbank.co.ke";
            String transaction = "LOAN-DISBURSED";
            mailService.sendChannelTransactionsReport(bis, transaction, groupName, sendFrom, toEmail);
        }
    }

    @Async
    protected void getOtherTransactionsInGroup(long groupId, String groupName, String toEmail, String groupAccount) {
        List<OtherChannelsBalances> channelsBalancesList = otherChannelsBalancesRepository.findAllByGroupIdAndCreditAmountGreaterThanAndAmountDepletedFalseAndSoftDeleteFalseOrderByIdAsc(groupId, 0);
        if (!channelsBalancesList.isEmpty()) {
            //TODO: TOTAL COUNT
            double groupBalances = channelsBalancesList.stream().mapToDouble(OtherChannelsBalances::getCreditAmount).sum();
            //TODO:: DEPOSITS FROM OTHER CHANNELS PENDING APPROVALS EXISTS

            ByteArrayInputStream bis = GroupPDFGeneratorUtility
                    .channelTransactionsReport(groupName, groupAccount, channelsBalancesList, groupBalances);
            String sendFrom = "mbanking@postbank.co.ke";
            String transaction = "CHANNEL-TRANSACTIONS";
            mailService.sendChannelTransactionsReport(bis, transaction, groupName, sendFrom, toEmail);

        } else {
            log.info("***************DEPOSITS FROM OTHER CHANNELS CLEARED***************");
        }
    }

    @Override
    public Mono<UniversalResponse> memberAccountStatement(AccountStatementDto statementDto) {
        return Mono.fromCallable(() -> {
            MemberWrapper memberWrapper = chamaKycService.searchMonoMemberByPhoneNumber(statementDto.getPhoneNumber());
            if (memberWrapper == null) {
                return new UniversalResponse("fail", MEMBER_NOT_FOUND);
            }
            long memberId = memberWrapper.getId();
            String phoneNumber = memberWrapper.getPhonenumber();
            String language = memberWrapper.getLanguage();
            String memberName = memberWrapper.getFirstname().concat(" " + memberWrapper.getLastname());

            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(statementDto.getGroupId());
            if (groupWrapper == null) {
                return new UniversalResponse("fail", GROUP_NOT_FOUND);
            }

            String groupName = groupWrapper.getName();
            long groupId = groupWrapper.getId();
            GroupMemberWrapper groupMembership = chamaKycService.getMonoGroupMembershipByGroupIdAndMemberId(groupId, memberId);
            if (groupMembership == null) {
                return new UniversalResponse("fail", getResponseMessage("memberIsNotPartOfGroup"));
            }

            Accounts accounts = accountsRepository.findFirstByGroupIdAndSoftDeleteFalse(groupWrapper.getId());
            if (accounts == null) {
                return new UniversalResponse("fail", getResponseMessage("groupAccountNotFound"));
            }
            if (accounts.getAccountdetails().equals("DEFAULT_ACCOUNT")) {
                return new UniversalResponse("fail", getResponseMessage("groupHasNoAccount"));

            }
            Date today = new Date();
            Date dateBefore = new Date(today.getTime() - statementDto.getDays() * 24 * 3600 * 1000); //Subtract n days
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy");

            String startDateFormat = simpleDateFormat.format(dateBefore);
            String endDateFormat = simpleDateFormat.format(today);
            String toEmail = statementDto.getEmail();

            List<ContributionPayment> contributionPayments = contributionsPaymentRepository.
                    generateMemberStatement(groupId, phoneNumber, startDateFormat, endDateFormat);

            getMemberInputStreamResourceResponseEntity(groupId, groupName, memberName, toEmail, phoneNumber, language, contributionPayments);

            auditTrail("group member statement", "group member statement requested for member " + memberName + " in group " + groupWrapper.getName(), memberWrapper.getImsi());
            creatNotification(groupWrapper.getId(), groupWrapper.getName(), "group member statement requested sent to " + toEmail);
            return new UniversalResponse("success", "Member statement has been successfully sent to".concat(" ") + toEmail);

        }).publishOn(Schedulers.boundedElastic());
    }


    @Override
    public Mono<UniversalResponse> getGroupMemberShareOutsDisbursement(String username, ShareOutsDisbursementWrapper wrapper) {
        return Mono.fromCallable(() -> {
            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(wrapper.getGroupId());
            if (groupWrapper == null) {
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"));
            }
            String groupName = groupWrapper.getName();
            long groupId = groupWrapper.getId();

            MemberWrapper memberWrapper = chamaKycService.searchMonoMemberByPhoneNumber(wrapper.getMemberPhoneNumber());
            if (memberWrapper == null) {
                return new UniversalResponse("fail", getResponseMessage("memberNotFound"));
            }
            long memberId = memberWrapper.getId();
            String phoneNumber = memberWrapper.getPhonenumber();
            String memberName = String.format("%s %s", memberWrapper.getFirstname(), memberWrapper.getLastname());

            GroupMemberWrapper groupMembership = chamaKycService.getMonoGroupMembershipByGroupIdAndMemberId(groupId, memberId);
            if (groupMembership == null) {
                return new UniversalResponse("fail", getResponseMessage("memberIsNotPartOfGroup"));
            }

            String response = String.format("Member %s share outs disbursement in group %s", memberName, groupName);
            return new UniversalResponse("success", response);
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> getGroupsMembersShareOutsDisbursement(String username, ShareOutsDisbursementWrapper wrapper) {
        return Mono.fromCallable(() -> {

            Pageable pageable = PageRequest.of(wrapper.getPage(), wrapper.getSize());

            List<ShareOutsDisbursed> shareOutsDisbursedList = shareOutsDisbursedRepo.findAllBySoftDeleteTrueOrderByIdDesc(pageable);

            List<ShareOutsDisbursementReport> reportWrapperList = shareOutsDisbursedList
                    .parallelStream()
                    .map(this::getGroupMembersShareOutsDisbursementReport)
                    .collect(Collectors.toList());
            UniversalResponse response = new UniversalResponse("success", "Groups share outs disbursement", reportWrapperList);
            response.setMetadata(Map.of("numofrecords", reportWrapperList.size()));
            return response;
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> getIndividualGroupMembersShareOutsDisbursement(String username, ShareOutsDisbursementWrapper wrapper) {
        return Mono.fromCallable(() -> {

            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(wrapper.getGroupId());
            if (groupWrapper == null) {
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"));
            }

            String groupName = groupWrapper.getName();
            long groupId = groupWrapper.getId();

            Pageable pageable = PageRequest.of(wrapper.getPage(), wrapper.getSize());

            List<ShareOutsDisbursed> shareOutsDisbursedList = shareOutsDisbursedRepo.findAllByGroupIdAndStatusOrderByIdDesc(groupId, PaymentEnum.SHARE_OUT_DISBURSED_SUCCESSFULLY.name(), pageable);

            if (shareOutsDisbursedList.isEmpty()) {
                return new UniversalResponse("fail", getResponseMessage("shareOutsDisbursementNotFound"));
            }

            log.info("DISBURSEMENT LIST {} ", shareOutsDisbursedList.size());

            List<ShareOutsDisbursementReport> reportWrapperList = shareOutsDisbursedList
                    .parallelStream()
                    .map(this::getGroupMembersShareOutsDisbursementReport)
                    .collect(Collectors.toList());
            String message = String.format("Group share outs disbursement in group %s", groupName);
            UniversalResponse response = new UniversalResponse("success", message, reportWrapperList);
            response.setMetadata(Map.of("numofrecords", reportWrapperList.size()));
            return response;
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> getGroupTransactionsFromOtherChannels(Long groupId, Pageable pageable, String username) {

        return Mono.fromCallable(() -> getOtherGroupTransactionReportWrapper(groupId, pageable, username))
                .publishOn(Schedulers.boundedElastic()).map(res -> new UniversalResponse("success",
                        getResponseMessage("groupTransactions"), res));
    }

    @Override
    public Mono<UniversalResponse> mchamaAccountValidation(String account) {
        return Mono.fromCallable(() -> {
            Accounts accounts = accountsRepository.findFirstByAccountdetails(account);
            if (accounts == null) {
                String response = String.format("%s %s", account, "Not registered as an Mchama Group Account!");
                return new UniversalResponse("fail", response);
            } else {
                Map<String, Object> metadata = Map.of(
                        "accountName", accounts.getName(),
                        "availableBalance", accounts.getAvailableBal(),
                        "actualBalance", accounts.getAccountbalance(),
                        "accountNumber", accounts.getAccountdetails());
                return new UniversalResponse("success", "Account Details Retrieved Successfully", metadata);
            }
        }).publishOn(Schedulers.boundedElastic());
    }

    private ShareOutsDisbursementReport getGroupMembersShareOutsDisbursementReport(ShareOutsDisbursed disbursed) {
        return ShareOutsDisbursementReport.builder()
                .id(disbursed.getId())
                .groupId(disbursed.getGroupId())
                .status(disbursed.getStatus())
                .amount(disbursed.getAmount())
                .phoneNumber(disbursed.getPhoneNumber())
                .coreAccount(disbursed.getCoreAccount())
                .groupName(disbursed.getGroupName())
                .disbursed(disbursed.getDisbursed())
                .active(disbursed.isSoftDelete())
                .message(disbursed.getMessage())
                .createdOn(disbursed.getCreatedOn())
                .modifiedOn(disbursed.getLastModifiedDate())
                .build();
    }


    @Async
    protected void getMemberInputStreamResourceResponseEntity(long groupId, String groupName, String memberName, String toEmail, String phoneNumber, String language, List<ContributionPayment> contributionPayments) {
        double savingBalance = calculateMemberBalance(groupId, "saving", phoneNumber);
        double loanBalance = calculateMemberBalance(groupId, "loan", phoneNumber);
        double welfareBalance = calculateMemberBalance(groupId, "welfare", phoneNumber);
        double fineBalance = calculateMemberBalance(groupId, "fine", phoneNumber);
        double projectBalance = calculateMemberBalance(groupId, "project", phoneNumber);
        ByteArrayInputStream bis = GroupPDFGeneratorUtility
                .mchamaMemberPDFReport(contributionPayments, memberName, groupName, phoneNumber, savingBalance, welfareBalance, projectBalance, loanBalance, fineBalance);

        String sendFrom = "mbanking@postbank.co.ke";

        mailService.sendMemberStatement(bis, sendFrom, toEmail, memberName, phoneNumber, groupName, language);
    }

    @Async
    protected double calculateMemberBalance(long groupId, String category, String phoneNumber) {
        List<ContributionPayment> memberPayments = contributionsPaymentRepository.findMemberPaymentsByPaymentType(groupId, category, phoneNumber);
        double balance = 0;

        for (ContributionPayment payment : memberPayments) {
            balance += payment.getAmount();
        }
        return balance;
    }

    private void getGroupInputStreamResourceResponseEntity(long groupId, String acctName, String regNumber, String ledgerBalance, String actualBalance, String address, String groupAccount, String toEmail, String memberName, JsonArray tranData) {

        double savingBalance = calculateBalance(groupId, TypeOfContribution.saving.name());
        double fineBalance = calculateBalance(groupId, TypeOfContribution.fine.name());
        double projectBalance = calculateBalance(groupId, TypeOfContribution.project.name());
        double welfareBalance = calculateBalance(groupId, TypeOfContribution.welfare.name());
        double fineDeductions = calculateTransferBalance(groupId, TypeOfContribution.fine.name());

        double savingDeductions = calculateTransferBalance(groupId, TypeOfContribution.saving.name());
        double projectDeductions = calculateTransferBalance(groupId, TypeOfContribution.project.name());
        double welfareDeductions = calculateTransferBalance(groupId, TypeOfContribution.welfare.name());

        //todo:: Savings contributions from menu
        savingBalance = formatAmount(savingBalance);
        savingDeductions = formatAmount(savingDeductions);
        savingBalance = savingBalance - savingDeductions;

        //todo:: Fines contributions from menu
        fineBalance = formatAmount(fineBalance);
        fineDeductions = formatAmount(fineDeductions);
        fineBalance = fineBalance - fineDeductions;
        //todo:: Projects contributions from menu
        projectBalance = formatAmount(projectBalance);
        projectDeductions = formatAmount(projectDeductions);
        projectBalance = projectBalance - projectDeductions;
        //todo:: Welfare contributions from menu
        welfareBalance = formatAmount(welfareBalance);
        welfareDeductions = formatAmount(welfareDeductions);
        welfareBalance = welfareBalance - welfareDeductions;

        ByteArrayInputStream bis = GroupPDFGeneratorUtility
                .cbsGroupPDFReport(acctName, regNumber, ledgerBalance, actualBalance, address, groupAccount, tranData, savingBalance, welfareBalance, fineBalance, projectBalance);
        String sendFrom = "mbanking@postbank.co.ke";

        mailService.sendGroupStatement(bis, sendFrom, toEmail, acctName, groupId, memberName, regNumber);
    }


    private void updatePaymentFromOtherTransactions(AssignTransactionPendingApprovals approvals, MemberWrapper
            creditMember, String approverName) {
        String transactionId = TransactionIdGenerator.generateTransactionId("CNT");
        Optional<Contributions> optionalContributions = contributionsRepository.findByGroupIdAndActiveTrueAndSoftDeleteFalse(approvals.getGroupId());
        if (optionalContributions.isEmpty()) {
            return;
        }
        Contributions contrib = optionalContributions.get();
        Accounts accounts = accountsRepository.findByGroupIdAndActiveTrueAndSoftDeleteFalse(approvals.getGroupId());
        if (accounts == null) {
            return;
        }
        double approvedAmount = approvals.getAmount();
        int amount = (int) approvedAmount;
        String cardNumber = accounts.getAccountdetails().replaceAll("\\b(\\d{4})(\\d{8})(\\d{4})", "$1XXXXXXXX$3");

        String firstFourChars = cardNumber.substring(0, 4);
        String lastFourChars = cardNumber.substring(9, 13);
        String maskedCardNumber = firstFourChars + "*****" + lastFourChars;
        ContributionPayment payment = new ContributionPayment();
        payment.setContributionId(approvals.getId());
        payment.setPaymentType(approvals.getPaymentType());
        payment.setPaymentForType(approvals.getPaymentForType());
        payment.setGroupAccountId(contrib.getMemberGroupId());
        payment.setAmount(amount);
        payment.setTransactionId(transactionId);
        payment.setGroupId(approvals.getGroupId());
        payment.setPhoneNumber(approvals.getPhoneNumber());
        payment.setNarration("Deposits transactions");
        payment.setPaymentStatus(PaymentEnum.PAYMENT_SUCCESS.name());
        payment.setSchedulePaymentId(String.valueOf(contrib.getScheduleType().getId()));
        payment.setFirstDeposit(false);
        if (approvals.getPaymentType().equals("saving") || approvals.getPaymentType().equals("fine") || approvals.getPaymentType().equals("project") || approvals.getPaymentType().equals("welfare")) {
            payment.setPaidIn(approvals.getAmount());
            payment.setPaidOut((double) 0);
            payment.setPaidBalance(accounts.getAccountbalance() + approvals.getAmount());
            payment.setActualBalance(accounts.getAccountbalance() + approvals.getAmount());
        }
        if (approvals.getPaymentType().equals("loan")) {
            payment.setPaidOut(approvals.getAmount());
            payment.setPaidIn((double) 0);
            payment.setPaidBalance(accounts.getAccountbalance() + approvals.getAmount());
            payment.setActualBalance(accounts.getAccountbalance() + approvals.getAmount());
        }
        ContributionPayment savedContributionPayment = contributionsPaymentRepository.save(payment);

        if (savedContributionPayment.getPaymentType().equals("loan")) {
            updateLoanDisbursed(savedContributionPayment, creditMember, accounts);
        }
        if (savedContributionPayment.getPaymentType().equals("fine")) {
            updateFinesPayment(savedContributionPayment, creditMember, accounts, contrib);
        }
        if (savedContributionPayment.getPaymentType().equals("saving")) {
            addContributionsToShareOut(savedContributionPayment);
        }
        //todo:: send sms notification to official
//        sendDepositSmsToGroupMembers(approvals.getGroupId(), savedContributionPayment.getAmount(), maskedCardNumber, savedContributionPayment.getTransactionId(), approverName);
    }

    private void updateFinesPayment(ContributionPayment payment, MemberWrapper creditMember, Accounts
            accounts, Contributions contrib) {

        Fines fines = finesRepository.getMemberFineInGroup(payment.getGroupId(), creditMember.getId());
        if (!(fines == null)) {
            double fineBalance = fines.getFineBalance();
            double paidAmount = payment.getAmount();
            if (paidAmount > fineBalance) {
                double overFlow = paidAmount - fineBalance;
                fines.setFineBalance(0.0);
                fines.setPaymentStatus(PaymentEnum.PAID.name());
                fines.setLastModifiedDate(new Date());
                fines.setLastModifiedBy(payment.getPhoneNumber());
                finesRepository.save(fines);
                recordOverflowToSavings(payment, overFlow, fines, accounts);
            } else if (payment.getAmount() < fineBalance) {
                Double remainingBalance = fineBalance - payment.getAmount();
                fines.setFineBalance(remainingBalance);
                fines.setPaymentStatus(PaymentEnum.PAYMENT_PENDING.name());
                fines.setLastModifiedDate(new Date());
                fines.setLastModifiedBy(payment.getPhoneNumber());
                finesRepository.save(fines);
            } else if (payment.getAmount() == fineBalance) {
                fines.setFineBalance(0.0);
                fines.setPaymentStatus(PaymentEnum.PAID.name());
                fines.setLastModifiedDate(new Date());
                fines.setLastModifiedBy(payment.getPhoneNumber());
                finesRepository.save(fines);
            }
            payment.setIsFine(true);
            payment.setIsPenalty(true);
            contributionsPaymentRepository.save(payment);
            savedTransactionLog(payment, contrib, accounts);
        }
    }

    @Async
    protected void sendApprovedOtherTransactionTextToGroupMembers(long id, String groupName, String
            approverName, String memberName, Double amount, String phonenumber, String channel, String language) {
        notificationService.sendApprovedOtherTransactionTextToMember(groupName, amount, approverName, memberName, phonenumber, channel, language);

        chamaKycService.getFluxGroupMembers(id)
                .subscribe(mbr -> notificationService.sendApprovedOtherTransactionTextToMembers(groupName, amount, approverName, memberName, mbr.getFirstname(), mbr.getPhonenumber(), channel, mbr.getLanguage()));
    }

    @Async
    protected void sendDeclinedOtherTransactionText(long id, String groupName, String approverName, String
            memberName, double amount, String phonenumber, String language) {
        notificationService.sendDeclinedOtherTransactionTextToMember(groupName, amount, approverName, memberName, phonenumber, language);

        chamaKycService.getFluxGroupMembers(id)
                .subscribe(mbr -> notificationService.sendDeclinedOtherTransactionTextToMembers(groupName, amount, approverName, memberName, mbr.getFirstname(), mbr.getPhonenumber(), mbr.getLanguage()));
    }

    private OtherTransactionsPendingApprovalsResponse getChannelsPendingApprovalsResponse
            (AssignTransactionPendingApprovals trasaction) {
        return OtherTransactionsPendingApprovalsResponse.builder()
                .id(trasaction.getId())
                .amount(trasaction.getAmount())
                .otherTransactionId(trasaction.getOtherTransactionId())
                .groupId(trasaction.getGroupId())
                .creator(trasaction.getCreator())
                .pending(trasaction.getPending())
                .approved(trasaction.getApproved())
                .creatorPhoneNumber(trasaction.getCreatorPhoneNumber())
                .paymentType(trasaction.getPaymentType())
                .memberName(trasaction.getMemberName())
                .phoneNumber(trasaction.getPhoneNumber())
                .createdOn(trasaction.getCreatedOn())
                .build();
    }

    @Async
    protected void sendAssignedOtherTransactionSms(long groupId, String groupName, double amount, String creator, String
            otherMember, String memberPhone, String channel, String language) {

        //todo:: send sms to initiator
        notificationService.sendMemberOtherTransactionText(groupName, amount, creator, otherMember, memberPhone, channel, language);
        //todo:: send to officials
        chamaKycService.getGroupOfficials(groupId)
                .filter(gm -> !gm.getPhonenumber().equals(memberPhone))
                .subscribe(official ->
                        notificationService.sendMembersOtherTransactionText(groupName, amount, creator, otherMember, official.getFirstname(), official.getPhonenumber(), channel, official.getLanguage()));
    }


    private List<OtherGroupTransactionWrapper> getGroupAccountTransactions(Long groupId, Pageable pageable, String
            username) {
        return otherChannelsBalancesRepository.findAllByGroupIdAndCreditAmountGreaterThanAndAmountDepletedFalseAndSoftDeleteFalseOrderByIdAsc(groupId, 0.0, pageable)
                .filter(t -> t.getCbsAccount() != null)
                .map(mapOtherTransactionsToWrapperResponse(username)).toList();

    }

    private List<OtherGroupTransactionReportWrapper> getOtherGroupTransactionReportWrapper(Long groupId, Pageable pageable, String
            username) {
        return otherChannelsBalancesRepository.findAllByGroupIdAndSoftDeleteFalseOrderByIdAsc(groupId, pageable)
                .filter(t -> t.getCbsAccount() != null)
                .map(mapToOtherGroupTransactionReportWrapper(username)).toList();

    }


    @Async
    protected void sendApprovedFineTextToGroupMembers(long id, String group, String approverName, String
            memberName, Double fineAmount, String phonenumber, String language, String fineName) {
        notificationService.sendFineApprovedText(memberName, approverName, fineAmount, group, phonenumber, fineName, language);

        chamaKycService.getFluxGroupMembers(id)
                .subscribe(mbr -> notificationService.sendMembersApprovedFineText(mbr.getFirstname(), approverName, memberName, group, fineAmount, mbr.getPhonenumber(), mbr.getLanguage(), fineName));
    }

    @Async
    protected void sendDeclinedFineTextToMembers(long id, String group, String approverName, String
            memberName, Double fineAmount, String phonenumber, String language, String fineName) {
        notificationService.sendFineDeclineText(memberName, approverName, fineAmount, group, phonenumber, fineName, language, id);

        chamaKycService.getFluxGroupMembers(id)
                .filter(gm -> !gm.getPhonenumber().equals(phonenumber))
                .subscribe(mbr -> notificationService.sendMembersDeclinedFineText(mbr.getFirstname(), approverName, memberName, group, fineAmount, mbr.getPhonenumber(), mbr.getLanguage(), fineName));
    }


    private FinesPendingApprovalsResponse getFinesPendingApprovalsResponse(FinesPendingApprovals fine) {
        return FinesPendingApprovalsResponse.builder()
                .id(fine.getId())
                .finedMember(fine.getFinedMember())
                .finedMemberPhoneNumber(fine.getFinedMemberPhoneNumber())
                .fineAmount(fine.getFineAmount())
                .memberId(fine.getMemberId())
                .fineName(fine.getFineName())
                .groupId(fine.getGroupId())
                .creator(fine.getCreator())
                .creatorPhoneNumber(fine.getCreatorPhone())
                .createdOn(fine.getCreatedOn()).build();
    }

    private ContributionsReportWrapper getContributionsReportWrapper(ContributionsPendingApprovals approvals) {
        return ContributionsReportWrapper.builder()
                .id(approvals.getId())
                .groupId(approvals.getGroupId())
                .contributionAmount(approvals.getContributionAmount())
                .frequency(approvals.getFrequency())
                .amountType(approvals.getAmountType().getName())
                .contributionType(approvals.getContributionType().getName())
                .scheduleType(approvals.getScheduleType().getName())
                .daysBeforeDue(approvals.getDaysBeforeDue())
                .name(approvals.getName())
                .contributionDate(approvals.getContributionDate())
                .paymentPeriod(approvals.getPaymentPeriod())
                .welfareAmt(approvals.getWelfareAmt())
                .loanInterest(approvals.getLoanInterest())
                .startDate(approvals.getStartDate())
                .reminder(approvals.getReminder())
                .createdOn(approvals.getCreatedOn())
                .creator(approvals.getCreator())
                .creatorPhoneNumber(approvals.getCreatorPhoneNumber())
                .build();
    }

    private void groupAccountBalanceInquiry(GroupWrapper groupWrapper, Accounts account) {
        String transactionId = RandomStringUtils.randomNumeric(12);
        Map<String, String> balanceInquiryReq = getBalanceInquiryReq(
                groupWrapper.getCsbAccount(), "0", transactionId, transactionId);

        String balanceBody = gson.toJson(balanceInquiryReq);
        postBankWebClient.post().uri(postBankUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(balanceBody)
                .retrieve()
                .bodyToMono(String.class)
                .subscribe(jsonString -> {
                    JsonObject jsonObject = new Gson().fromJson(jsonString, JsonObject.class);
                    if (!jsonObject.get("field39").getAsString().equals("00")) {
                        log.info("**********BALANCE INQUIRY SERVICE UNAVAILABLE**********");
                        return;
                    }
                    double availableBalance = Double.parseDouble(String.valueOf(jsonObject.get("Available_balance").getAsDouble()));
                    double actualBalance = Double.parseDouble(String.valueOf(jsonObject.get("Actual_balance").getAsDouble()));
                    account.setAccountbalance(Double.parseDouble(String.valueOf(actualBalance)));
                    account.setAvailableBal(Double.parseDouble(String.valueOf(availableBalance)));
                    account.setLastModifiedDate(new Date());
                    account.setBalanceRequestDate(new Date());
                    accountsRepository.save(account);
                });
    }

    public void audiTrail(String action, String description, String username) {
        AuditTrail auditTrail = new AuditTrail();
        auditTrail.setAction(action);
        auditTrail.setDescription(description);
        auditTrail.setCreatedBy(username);
        auditTrail.setCreatedOn(new Date());
        auditTrailRepository.save(auditTrail);
    }

    private void createNotification(long id, String message, String groupName) {
        Notifications notifications = Notifications.builder()
                .groupId(id)
                .message(message)
                .groupName(groupName)
                .build();
        notificationsRepository.save(notifications);
    }

    public double formatAmount(Double amount) {
        DecimalFormat decimalFormat = new DecimalFormat("#.##");
        //todo:: Formatting the number to two decimal places
        double formattedAmount = Double.parseDouble(decimalFormat.format(amount));
        return formattedAmount;
    }

    public double formatInterestAmount(Double amount) {
        DecimalFormat decimalFormat = new DecimalFormat("#.####");
        //todo:: Formatting the number to two decimal places
        double interest = Double.parseDouble(decimalFormat.format(amount));
        return interest;
    }

    public static String decimalFormatValue(double amount) {
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");
        return decimalFormat.format(amount);
    }


    public static double parseAmount(String amountStr) throws ParseException {
        NumberFormat format = NumberFormat.getNumberInstance();
        Number number = format.parse(amountStr);
        return number.doubleValue();
    }
}


