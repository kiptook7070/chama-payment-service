package com.eclectics.chamapayments.service.impl;

import com.eclectics.chamapayments.model.*;
import com.eclectics.chamapayments.repository.*;
import com.eclectics.chamapayments.service.*;
import com.eclectics.chamapayments.service.enums.ChargeType;
import com.eclectics.chamapayments.service.enums.LoanStatusEnum;
import com.eclectics.chamapayments.service.enums.PaymentEnum;
import com.eclectics.chamapayments.service.enums.TypeOfContribution;
import com.eclectics.chamapayments.util.TransactionIdGenerator;
import com.eclectics.chamapayments.wrappers.request.AccountWrapper;
import com.eclectics.chamapayments.wrappers.request.ApplyLoanWrapper;
import com.eclectics.chamapayments.wrappers.request.LoanInterestWrapper;
import com.eclectics.chamapayments.wrappers.request.LoanLimitWrapper;
import com.eclectics.chamapayments.wrappers.response.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

import javax.annotation.PostConstruct;
import javax.net.ssl.SSLException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.eclectics.chamapayments.util.RequestConstructor.*;

/**
 * @author Alex Maina
 * @created 07/12/2021
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoanServiceImpl implements LoanService {
    public static final String GROUP_NOT_FOUND = "groupNotFound";
    public static final String MEMBER_NOT_FOUND = "memberNotFound";
    private final ESBService esbService;
    private final WithdrawallogsRepo withdrawallogsRepo;
    private final LoanproductsRepository loanproductsRepository;
    private final LoanapplicationsRepo loanapplicationsRepo;
    private final LoansdisbursedRepo loansdisbursedRepo;
    private final LoanrepaymentpendingapprovalRepo loanrepaymentpendingapprovalRepo;
    private final LoansrepaymentRepo loansrepaymentRepo;
    private final ContributionRepository contributionRepository;
    private final GuarantorsRepository guarantorsRepository;
    private final AccountsRepository accountsRepository;
    private final TransactionlogsRepo transactionlogsRepo;
    private final LoanPenaltyRepository loanPenaltyRepository;
    private final ChamaKycService chamaKycService;
    private final NotificationService notificationService;
    private final ESBLoggingService esbLoggingService;
    private final ContributionRepository contributionsRepository;
    private final Gson gson;
    private final ContributionsPaymentRepository contributionsPaymentRepository;
    private final ResourceBundleMessageSource source;
    private final NotificationsRepository notificationsRepository;
    private final AuditTrailRepository auditTrailRepository;


    private WebClient webClient;
    private WebClient postBankWebClient;
    @Value("${esb.channel.uri}")
    private String postBankUrl;

    @PostConstruct
    public void init() throws SSLException {
        SslContext sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        HttpClient httpClient = HttpClient.create().secure(sslContextSpec -> sslContextSpec.sslContext(sslContext));
        postBankWebClient = WebClient.builder().baseUrl(postBankUrl).build();
        webClient = WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).baseUrl(postBankUrl).build();

    }

    private String getResponseMessage(String tag) {
        Locale locale = LocaleContextHolder.getLocale();
        return source.getMessage(tag, null, locale);
    }

    public boolean checkUserIsPartOfGroup(long groupId) {
        String phone = SecurityContextHolder.getContext().getAuthentication().getName();
        MemberWrapper memberWrapper = chamaKycService.searchMonoMemberByPhoneNumber(phone);
        if (memberWrapper == null) return false;
        GroupMemberWrapper optionalGroupMembership = chamaKycService.getMonoGroupMembershipByGroupIdAndMemberId(groupId, memberWrapper.getId());
        return optionalGroupMembership != null;
    }

    @Override
    public int checkLoanLimit(String phoneNumber, Long contributionId) {
        List<ContributionPayment> userContributionPayments = contributionsPaymentRepository.findUsersContribution(contributionId, phoneNumber);
        if (userContributionPayments.isEmpty()) {
            return 0;
        }

        return userContributionPayments.stream()
                .mapToInt(ContributionPayment::getAmount)
                .sum();
    }


    private void handleApprovalGuarantoship(boolean guarantee, Guarantors guarantor, LoanApplications loanApplication, MemberWrapper memberWrapper, String memberName) {
        if (guarantee) {
            guarantor.setLoanStatus(LoanStatusEnum.APPROVED.name());
            guarantorsRepository.save(guarantor);
            notificationService.sendGuarantorshipApprovalMessage(guarantor.getPhoneNumber(),
                    guarantor.getGuarantorName(), memberWrapper.getPhonenumber(),
                    memberWrapper.getFirstname(), guarantor.getAmount(), memberWrapper.getLanguage());
            chamaKycService.getFluxMembersLanguageAndPhonesInGroup(loanApplication.getLoanProducts().getGroupId())
                    .toStream()
                    .filter(pair -> !Objects.equals(pair.getFirst(), memberWrapper.getPhonenumber()))
                    .forEach(pair -> notificationService.sendGroupGuarantorshipApprovedMessage(
                            pair.getFirst(),
                            guarantor.getGuarantorName(),
                            memberWrapper.getPhonenumber(),
                            memberName, guarantor.getAmount(),
                            pair.getSecond()));
        } else {
            guarantor.setLoanStatus(LoanStatusEnum.DECLINED.name());
            guarantorsRepository.save(guarantor);
            notificationService.sendGuarantorshipDeclinedMessage(guarantor.getPhoneNumber(),
                    guarantor.getGuarantorName(), memberWrapper.getPhonenumber(),
                    memberWrapper.getFirstname(), guarantor.getAmount(), memberWrapper.getLanguage());

            chamaKycService.getFluxMembersLanguageAndPhonesInGroup(loanApplication.getLoanProducts().getGroupId())
                    .toStream()
                    .filter(pair -> !Objects.equals(pair.getFirst(), memberWrapper.getPhonenumber()))
                    .forEach(pair -> notificationService.sendGroupGuarantorshipDeclinedMessage(
                            pair.getFirst(),
                            guarantor.getGuarantorName(),
                            memberWrapper.getPhonenumber(),
                            memberName, guarantor.getAmount(),
                            pair.getSecond()));
        }
    }


    @Override
    public Mono<UniversalResponse> createLoanProduct(LoanproductWrapper loanproductWrapper, String createdBy) {
        return Mono.fromCallable(() -> {
            if (loanproductWrapper.getMax_principal() <= loanproductWrapper.getMin_principal())
                return new UniversalResponse("fail", getResponseMessage("principalNotValid"));

            if (loanproductWrapper.getPaymentperiodtype() == null || loanproductWrapper.getPaymentperiodtype().isBlank())
                return new UniversalResponse("fail", getResponseMessage("loanProductPaymentPeriodNotFound"), Collections.emptyList());

            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(loanproductWrapper.getGroupid());
            if (groupWrapper == null) {
                return new UniversalResponse("fail", getResponseMessage(GROUP_NOT_FOUND));
            }
            Optional<Contributions> optionalContribution = contributionRepository.findById(loanproductWrapper.getContributionid());
            if (optionalContribution.isEmpty())
                return new UniversalResponse("fail", getResponseMessage("contributionNotFound"));

            //make sure the group does not duplicate the product
            if (loanproductsRepository.countByGroupIdAndProductname(loanproductWrapper.getGroupid(), loanproductWrapper.getProductname()) > 0) {
                return new UniversalResponse("fail", getResponseMessage("loanProductExists"));
            }
            Accounts accounts = accountsRepository.findFirstByGroupIdAndSoftDeleteFalse(groupWrapper.getId());
            if (accounts == null)
                return new UniversalResponse("fail", getResponseMessage("groupAccountNotFound"));

            LoanProducts loanProducts = new LoanProducts();
            loanProducts.setDescription(loanproductWrapper.getDescription());
            loanProducts.setGroupId(loanproductWrapper.getGroupid());
            loanProducts.setInteresttype(loanproductWrapper.getInteresttype());
            loanProducts.setInterestvalue(loanproductWrapper.getInterestvalue());
            loanProducts.setMax_principal(loanproductWrapper.getMax_principal());
            loanProducts.setMin_principal(loanproductWrapper.getMin_principal());
            loanProducts.setProductname(loanproductWrapper.getProductname());
            loanProducts.setPaymentperiod(loanproductWrapper.getPaymentperiod());
            loanProducts.setPaymentperiodtype(loanproductWrapper.getPaymentperiodtype());
            loanProducts.setContributions(optionalContribution.get());
            loanProducts.setGuarantor(loanproductWrapper.getIsguarantor());
            loanProducts.setPenalty(loanproductWrapper.getHasPenalty());
            loanProducts.setPenaltyValue(loanproductWrapper.getPenaltyvalue());
            loanProducts.setIsPercentagePercentage(loanproductWrapper.getIspenaltypercentage());
            loanProducts.setUserSavingValue(loanproductWrapper.getUsersavingvalue());
            loanProducts.setDebitAccountId(accounts);
            loanProducts.setGracePeriod(loanproductWrapper.getGracePeriod());
            loanProducts.setIsActive(true);
            loanProducts.setPenaltyPeriod(loanproductWrapper.getPenaltyPeriod());
            LoanProducts savedLoanProduct = loanproductsRepository.save(loanProducts);
            sendLoanProductCreatedMessage(createdBy, savedLoanProduct, groupWrapper);
            return new UniversalResponse("success", getResponseMessage("loanProductAdded"));
        }).publishOn(Schedulers.boundedElastic());
    }

    @Async
    public void sendLoanProductCreatedMessage(String createdBy, LoanProducts savedLoanProduct, GroupWrapper groupWrapper) {
        MemberWrapper member = chamaKycService.searchMonoMemberByPhoneNumber(createdBy);
        String memberName = String.format("%s %s", member.getFirstname(), member.getLastname());

        chamaKycService.getFluxGroupMembers(savedLoanProduct.getGroupId())
                .subscribe(memberWrapper -> notificationService.sendLoanProductCreated(memberName, savedLoanProduct.getProductname(), groupWrapper.getName(), memberWrapper.getPhonenumber(), memberWrapper.getLanguage()));
    }

    @Override
    public Mono<UniversalResponse> editLoanProduct(LoanproductWrapper loanproductWrapper, String approvedBy) {
        return Mono.fromCallable(() -> {
            Optional<LoanProducts> optionalLoanProducts = loanproductsRepository.findById(loanproductWrapper.getProductid());
            if (optionalLoanProducts.isEmpty())
                return new UniversalResponse("fail", getResponseMessage("loanProductNotFound"), Collections.emptyList());

            if (loanproductWrapper.getPaymentperiodtype() == null || loanproductWrapper.getPaymentperiodtype().isBlank())
                return new UniversalResponse("fail", getResponseMessage("loanProductPaymentPeriodNotFound"), Collections.emptyList());

            LoanProducts loanProduct = optionalLoanProducts.get();
            loanProduct.setDescription(loanproductWrapper.getDescription());
            loanProduct.setInteresttype(loanproductWrapper.getInteresttype());
            loanProduct.setInterestvalue(loanproductWrapper.getInterestvalue());
            loanProduct.setMax_principal(loanproductWrapper.getMax_principal());
            loanProduct.setMin_principal(loanproductWrapper.getMin_principal());
            loanProduct.setProductname(loanproductWrapper.getProductname());
            loanProduct.setPaymentperiod(loanproductWrapper.getPaymentperiod());
            loanProduct.setGracePeriod(loanproductWrapper.getGracePeriod());
            loanProduct.setPaymentperiodtype(loanproductWrapper.getPaymentperiodtype());
            loanProduct.setGuarantor(loanproductWrapper.getIsguarantor());
            loanProduct.setPenalty(loanproductWrapper.getHasPenalty());
            loanProduct.setIsPercentagePercentage(loanproductWrapper.getIspenaltypercentage());
            loanProduct.setUserSavingValue(loanproductWrapper.getUsersavingvalue());
            loanProduct.setPenaltyPeriod(loanproductWrapper.getPenaltyPeriod());
            loanProduct.setIsActive(loanproductWrapper.getIsActive());
            loanProduct.setPenaltyValue(loanproductWrapper.getPenaltyvalue());
            LoanProducts savedLoanProduct = loanproductsRepository.save(loanProduct);

            sendLoanProductEditedMessage(approvedBy, savedLoanProduct);
            return new UniversalResponse("success", getResponseMessage("loanProductEditedSuccessfully"));
        }).publishOn(Schedulers.boundedElastic());
    }

    @Async
    public void sendLoanProductEditedMessage(String approvedBy, LoanProducts savedLoanProduct) {
        MemberWrapper member = chamaKycService.searchMonoMemberByPhoneNumber(approvedBy);
        GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(savedLoanProduct.getGroupId());
        String memberName = String.format("%s %s", member.getFirstname(), member.getLastname());

        double penaltyValue = savedLoanProduct.getIsPercentagePercentage() ?
                savedLoanProduct.getPenaltyValue() / (double) 100 : savedLoanProduct.getPenaltyValue();

        chamaKycService.getFluxGroupMembers()
                .filter(gm -> !gm.getPhoneNumber().equals(approvedBy))
                .map(gm -> chamaKycService.getMemberDetailsById(gm.getMemberId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .subscribe(memberWrapper -> notificationService.sendLoanProductEdited(memberName,
                        savedLoanProduct.getProductname(), groupWrapper.getName(), savedLoanProduct.getMax_principal(),
                        savedLoanProduct.getMin_principal(), savedLoanProduct.getUserSavingValue(),
                        (int) penaltyValue, memberWrapper.getPhonenumber(), memberWrapper.getLanguage()));
    }

    @Override
    public Mono<UniversalResponse> activateDeactivateLoanProduct(LoanproductWrapper loanproductWrapper, String currentUser, boolean activate) {
        return Mono.fromCallable(() -> {
            Optional<LoanProducts> optionalLoanProducts = loanproductsRepository.findById(loanproductWrapper.getProductid());
            if (optionalLoanProducts.isEmpty())
                return new UniversalResponse("fail", getResponseMessage("loanProductNotFound"));

            LoanProducts loanProduct = optionalLoanProducts.get();

            if (!activate && !loanProduct.getIsActive())
                return new UniversalResponse("success", getResponseMessage("loanProductIsDeactivated"));

            if (activate && loanProduct.getIsActive())
                return new UniversalResponse("success", getResponseMessage("loanProductIsDeactivated"));

            loanProduct.setIsActive(activate);
            LoanProducts savedLoanProduct = loanproductsRepository.save(loanProduct);
            sendLoanActivatedOrDeactivatedMessage(savedLoanProduct, activate, currentUser);
            return new UniversalResponse("success", activate ? getResponseMessage("loanProductActivatedSuccessfully") : getResponseMessage("loanProductDeactivatedSuccessfully"));
        }).publishOn(Schedulers.boundedElastic());
    }

    @Async
    public void sendLoanActivatedOrDeactivatedMessage(LoanProducts savedLoanProduct, boolean activate, String currentUser) {
        MemberWrapper memberWrapper = chamaKycService.searchMonoMemberByPhoneNumber(currentUser);
        GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(savedLoanProduct.getGroupId());

        if (memberWrapper == null) {
            log.info("Member not found on send loan product activation...");
            return;
        }

        String memberName = String.format("%s %s", memberWrapper.getFirstname(), memberWrapper.getLastname());
        chamaKycService.getFluxGroupMembers()
                .filter(gm -> !gm.getPhoneNumber().equals(currentUser))
                .map(gm -> chamaKycService.getMemberDetailsById(gm.getMemberId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .subscribe(member -> {
                    if (activate) {
                        notificationService.sendLoanProductActivatedMessage(memberName, savedLoanProduct.getProductname(), groupWrapper.getName(), member.getPhonenumber(), member.getLanguage());
                        return;
                    }
                    notificationService.sendLoanProductDeactivatedMessage(memberName, savedLoanProduct.getProductname(), groupWrapper.getName(), member.getPhonenumber(), member.getLanguage());
                });
    }

    @Override
    public Mono<UniversalResponse> getLoanProductsbyGroup(long groupId) {
        return Mono.fromCallable(() -> {
            GroupWrapper group = chamaKycService.getMonoGroupById(groupId);

            if (group == null) {
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"));
            }
            LoanProducts loanProducts = loanproductsRepository.findTopByGroupIdAndIsActiveTrueAndSoftDeleteFalseOrderByIdDesc(groupId);
            if (loanProducts == null) {
                return new UniversalResponse("fail", getResponseMessage("loanProductNotFound"));
            }

            List<LoanproductWrapper> loanProductsList = loanproductsRepository.findAllByGroupIdAndSoftDeleteFalse(groupId)
                    .stream()
                    .map(p -> mapToLoanProductWrapper(group, p))
                    .collect(Collectors.toList());

            UniversalResponse response = new UniversalResponse("success", getResponseMessage("loanProductsPerGroup"),
                    loanProductsList);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("numofrecords", loanProductsList.size());
            response.setMetadata(metadata);
            return response;
        }).publishOn(Schedulers.boundedElastic());
    }

    private double loanLimit(LoanProducts loanProducts) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        try {
            int userSavingValue = loanProducts.getUserSavingValue();

            int limit = checkLoanLimit(auth.getName(), loanProducts.getContributions().getId());

            limit = (userSavingValue * limit) / 100;

            return limit;
        } catch (Exception exception) {
            return 0.0;
        }
    }

    private double loanLimit(LoanProducts loanProducts, String appliedBy) {
        try {
            int userSavingValue = loanProducts.getUserSavingValue();

            int limit = checkLoanLimit(appliedBy, loanProducts.getContributions().getId());

            limit = (userSavingValue * limit) / 100;

            return limit;
        } catch (Exception exception) {
            return 0.0;
        }
    }

    @Override
    public Mono<UniversalResponse> applyLoan(ApplyLoanWrapper applyLoanWrapper, String appliedBy) {
        return Mono.fromCallable(() -> {
            Optional<LoanProducts> optionalLoanProduct = loanproductsRepository.findById(applyLoanWrapper.getLoanproduct());
            if (optionalLoanProduct.isEmpty()) {
                return new UniversalResponse("fail", getResponseMessage("loanProductNotFound"));
            }
            MemberWrapper memberWrapper = chamaKycService.searchMonoMemberByPhoneNumber(appliedBy);
            if (memberWrapper == null) {
                return new UniversalResponse("fail", getResponseMessage("appUserNotFound"));
            }

            LoanProducts loanProducts = optionalLoanProduct.get();
            if (!loanProducts.getIsActive()) {
                return new UniversalResponse("fail", getResponseMessage("loanProductIsNotActive"), Collections.emptyList());
            }

            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(loanProducts.getGroupId());
            if (groupWrapper == null) {
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"));
            }

            double amount = applyLoanWrapper.getAmount();
            if (amount > loanProducts.getMax_principal()) {
                return new UniversalResponse("fail", String.format(getResponseMessage("maximumPrincipal"), amount));
            }

            if (amount < loanProducts.getMin_principal()) {
                return new UniversalResponse("fail", String.format(getResponseMessage("minimumPrincipal"), amount));
            }


            Accounts accounts = accountsRepository.findByGroupId(loanProducts.getGroupId());
            if (accounts == null) {
                return new UniversalResponse("fail", getResponseMessage("groupAccountNotFound"));
            }


            Double useLoanLimit = getLoanLimit(loanProducts.getGroupId(), appliedBy);
            double loanLimit = useLoanLimit * 3;


            if (amount > accounts.getAvailableBal()) {
                return new UniversalResponse("fail", String.format(getResponseMessage("groupHasInsufficientFunds"), groupWrapper.getName()));
            }

            if (amount > loanLimit) {
                return new UniversalResponse("fail", getResponseMessage("loanIsAboveLoanLimit"));
            }
            double savingBalance = calculateBalance(groupWrapper.getId());
            double savingDeducted = calculateTransferBalance(groupWrapper.getId());
            savingBalance = savingBalance - savingDeducted;

            if (amount > savingBalance) {
                return new UniversalResponse("fail", getResponseMessage("loanAmountIsBeyondLimit"));
            }

            List<LoansDisbursed> loansDisbursedList = loansdisbursedRepo.findAllByGroupIdAndMemberIdAndSoftDeleteFalseOrderByIdAsc(groupWrapper.getId(), memberWrapper.getId());
            if (!loansDisbursedList.isEmpty())
                return new UniversalResponse("fail", getResponseMessage("youHaveAnExistingLoanApplication"), Collections.emptyList());

            //todo:: check interest
            Optional<Contributions> checkInterest = contributionRepository.findByGroupIdAndActiveTrueAndSoftDeleteFalse(loanProducts.getGroupId());
            if (checkInterest.isEmpty()) {
                return new UniversalResponse("fail", getResponseMessage("contributionFound"));
            }

            Contributions contributions = checkInterest.get();

            if (contributions.getLoanInterest() == null || contributions.getLoanInterest().isEmpty()) {
                return new UniversalResponse("fail", getResponseMessage("contributionNotSet"));
            }

            if (!groupWrapper.isCanWithdraw()) {
                return new UniversalResponse("fail", getResponseMessage("allowGroupWithdrawal"));
            }

            String accountToDeposit = applyLoanWrapper.getCoreAccount().isBlank() ? appliedBy : applyLoanWrapper.getCoreAccount();

            saveLoanApplication(amount, memberWrapper, loanProducts, accountToDeposit, applyLoanWrapper.getReminder());
            auditTrail("group member loans", "group member loans applied successfully.", appliedBy);
            creatNotification(groupWrapper.getId(), groupWrapper.getName(), "group member loans applied successfully by " + appliedBy);
            return UniversalResponse.builder()
                    .status("success")
                    .message(String.format(getResponseMessage("successfulLoanApplication"), applyLoanWrapper.getAmount(), groupWrapper.getName()))
                    .build();
        }).publishOn(Schedulers.boundedElastic());

    }

    @Async
    protected void creatNotification(long groupId, String groupName, String message) {
        Notifications notifications = new Notifications();
        notifications.setGroupId(groupId);
        notifications.setGroupName(groupName);
        notifications.setMessage(message);
        notificationsRepository.save(notifications);
        log.info("NOTIFICATION SAVED::: {}", notifications);
    }

    @Async
    protected void auditTrail(String action, String description, String username) {
        AuditTrail trail = AuditTrail.builder()
                .action(action)
                .description(description)
                .capturedBy(username).build();
        auditTrailRepository.save(trail);
        log.info("AUDIT LOG STAGED::: {}", trail);
    }

    private double calculateTransferBalance(long groupId) {
        List<ContributionPayment> paymentList = contributionsPaymentRepository.findAllByGroupIdAndPaymentTypeAndPaymentStatusAndIsDebitAndSoftDeleteFalseOrderByIdDesc(groupId, "saving", PaymentEnum.KIT_TRANSFER_SUCCESS.name(), 'Y');

        double deduction = 0.0;

        for (ContributionPayment payment : paymentList) {
            deduction += payment.getAmount();
        }
        return deduction;
    }

    private double calculateBalance(long groupId) {
        List<ContributionPayment> contributionPaymentList = contributionsPaymentRepository.findAllByGroupIdAndPaymentTypeAndPaymentStatusAndSoftDeleteFalseOrderByIdDesc(groupId, "saving", PaymentEnum.PAYMENT_SUCCESS.name());
        double balance = 0.0;

        for (ContributionPayment contributionPayment : contributionPaymentList) {
            balance += contributionPayment.getAmount();
        }
        return balance;
    }

    @Async
    protected void saveLoanApplication(double amount, MemberWrapper memberWrapper, LoanProducts loanProducts, String accountToDeposit, Map<String, Object> applyLoanWrapper) {
        String memberName = String.format("%s %s", memberWrapper.getFirstname(), memberWrapper.getLastname());
        String loanedMemberPhone = memberWrapper.getPhonenumber();
        String language = memberWrapper.getLanguage();
        LoanApplications loanApplications = new LoanApplications();
        loanApplications.setAmount(amount);
        loanApplications.setMemberId(memberWrapper.getId());
        loanApplications.setPending(true);
        loanApplications.setTransactionDate(new Date());
        loanApplications.setGroupId(loanProducts.getGroupId());
        loanApplications.setAccountToDeposit(accountToDeposit);
        loanApplications.setUsingWallet(accountToDeposit.length() <= 12);
        loanApplications.setApprovalCount(0);
        loanApplications.setLoanProducts(loanProducts);
        loanApplications.setApprovedby(new JsonObject().toString());
        Type type = new TypeToken<Map<String, Object>>() {
        }.getType();
        String member_reminder = gson.toJson(applyLoanWrapper, type);
        loanApplications.setReminder(member_reminder);
        loanApplications.setUnpaidloans(0);
        loanApplications.setCreator(memberName);
        loanApplications.setCreatorPhoneNumber(loanedMemberPhone);

        LoanApplications recordLoanApplication = loanapplicationsRepo.save(loanApplications);
        if (recordLoanApplication.isPending()) {
            sendLoanApplicationSmsToOfficials(recordLoanApplication, memberName, loanedMemberPhone, language);
        }
    }

    private Double getLoanLimit(long groupId, String appliedBy) {
        List<ContributionPayment> contributionPaymentList = contributionsPaymentRepository.findAllByGroupIdAndPhoneNumberAndPaymentStatusAndPaymentTypeAndSoftDeleteFalse(groupId, appliedBy, PaymentEnum.PAYMENT_SUCCESS.name(), TypeOfContribution.saving.name());
        double totalSaving = 0;
        for (ContributionPayment contributionPayment : contributionPaymentList) {
            totalSaving += contributionPayment.getAmount();
        }
        return totalSaving;
    }


    @Async
    public void sendLoanApplicationSmsToOfficials(LoanApplications loanApplication, String memberName, String loanedMemberPhone, String language) {
        GroupWrapper group = chamaKycService.getMonoGroupById(loanApplication.getLoanProducts().getGroupId());
        if (group != null && group.isActive()) {
            long groupId = group.getId();
            String groupName = group.getName();
            double amount = loanApplication.getAmount();
            //todo::send to initiator
            notificationService.sendLoanToLoanedMember(groupName, amount, memberName, loanedMemberPhone, language);
            //todo:: send to officials
            chamaKycService.getGroupOfficials(groupId)
                    .filter(gm -> !gm.getPhonenumber().equals(loanedMemberPhone))
                    .subscribe(official ->
                            notificationService.sendLoanApplicationToOfficials(groupId, groupName, memberName, amount, official.getFirstname(), official.getPhonenumber(), official.getLanguage())
                    );

        } else {
            log.info("COULD NOT SEND LOAN APPLICATION SMS. GROUP NOT FOUND/DEACTIVATED {}", group.getName());
        }
    }

    public boolean checkAllGuarantorsApproved(Long loanId) {
        Optional<LoanApplications> contributionLoanOptional = loanapplicationsRepo.findById(loanId);
        List<Guarantors> guarantorsList = guarantorsRepository.findGuarantorsByLoanId(loanId);
        if (contributionLoanOptional.isEmpty()) {
            return false;
        }
        if (guarantorsList.isEmpty()) {
            return false;
        }
        LoanApplications contributionLoan = contributionLoanOptional.get();
        double totalLoan = contributionLoan.getAmount();
        double amountGuaranteed = guarantorsList
                .stream()
                .filter(guarantor -> guarantor.getLoanStatus().equalsIgnoreCase(LoanStatusEnum.APPROVED.name()))
                .mapToDouble(Guarantors::getAmount)
                .sum();
        return amountGuaranteed >= totalLoan;
    }

    @Override
    public Mono<UniversalResponse> getLoansPendingApprovalbyGroup(long groupid, int page, int size) {
        return Mono.fromCallable(() -> {
            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(groupid);

            if (groupWrapper == null)
                return new UniversalResponse("fail", getResponseMessage(GROUP_NOT_FOUND));

            Pageable pageable = PageRequest.of(page, size);
            List<LoanApplications> loanApplicationsList = loanapplicationsRepo.getApplicationsbygroup(groupid, pageable);

            List<LoansPendingApprovalWrapper> loansPendingApproval = loanApplicationsList
                    .stream()
                    .map(loan -> {
                        MemberWrapper member = chamaKycService.getMonoMemberDetailsById(loan.getMemberId());
                        if (member == null) return null;
                        LoansPendingApprovalWrapper loansPendingApprovalWrapper = new LoansPendingApprovalWrapper();
                        loansPendingApprovalWrapper.setAmount(loan.getAmount());
                        loansPendingApprovalWrapper.setMembername(member.getFirstname().concat(" ").concat(member.getLastname()));
                        loansPendingApprovalWrapper.setAppliedon(loan.getCreatedOn());
                        loansPendingApprovalWrapper.setLoanproductid(loan.getLoanProducts().getId());
                        loansPendingApprovalWrapper.setLoanproductname(loan.getLoanProducts().getProductname());
                        loansPendingApprovalWrapper.setMemberphonenumber(member.getPhonenumber());
                        loansPendingApprovalWrapper.setLoanapplicationid(loan.getId());
                        Type type = new TypeToken<Map<String, Object>>() {
                        }.getType();
                        Map<String, Object> member_reminder = gson.fromJson(loan.getReminder(), type);
                        loansPendingApprovalWrapper.setReminder(member_reminder);
                        loansPendingApprovalWrapper.setCreator(loan.getCreator());
                        loansPendingApprovalWrapper.setCreatorPhoneNumber(loan.getCreatorPhoneNumber());
                        loansPendingApprovalWrapper.setUnpaidloans(loan.getUnpaidloans());
                        return loansPendingApprovalWrapper;
                    }).collect(Collectors.toList());

            UniversalResponse response = new UniversalResponse("success", getResponseMessage("loansPendingApprovalPerGroup"), loansPendingApproval);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("numofrecords", loanapplicationsRepo.countApplicationsbyGroup(groupid));
            response.setMetadata(metadata);
            return response;
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> getLoansPendingApprovalbyUser(String phonenumber, int page, int size) {
        return Mono.fromCallable(() -> {
            MemberWrapper member = chamaKycService.searchMonoMemberByPhoneNumber(phonenumber);

            if (member == null) {
                return Mono.just(new UniversalResponse("fail", getResponseMessage(MEMBER_NOT_FOUND)));
            }

            Pageable pageable = PageRequest.of(page, size);
            List<LoanApplications> loanApplicationsList = loanapplicationsRepo.findByMemberIdAndPendingTrueOrderByCreatedOnDesc(member.getId(), pageable);
            List<LoansPendingApprovalWrapper> loansPendingApprovalWrapperList = loanApplicationsList
                    .stream()
                    .map(loans -> {
                        LoansPendingApprovalWrapper loansPendingApprovalWrapper = new LoansPendingApprovalWrapper();
                        loansPendingApprovalWrapper.setAmount(loans.getAmount());
                        loansPendingApprovalWrapper.setMembername(member.getFirstname().concat(" ").concat(member.getLastname()));
                        loansPendingApprovalWrapper.setAppliedon(loans.getCreatedOn());
                        loansPendingApprovalWrapper.setLoanproductid(loans.getLoanProducts().getId());
                        loansPendingApprovalWrapper.setLoanproductname(loans.getLoanProducts().getProductname());
                        loansPendingApprovalWrapper.setMemberphonenumber(member.getPhonenumber());
                        loansPendingApprovalWrapper.setLoanapplicationid(member.getId());
                        loansPendingApprovalWrapper.setGuarantor(loans.getLoanProducts().isGuarantor());
                        Type type = new TypeToken<Map<String, Object>>() {
                        }.getType();
                        Map<String, Object> member_reminder = gson.fromJson(loans.getReminder(), type);
                        loansPendingApprovalWrapper.setReminder(member_reminder);
                        loansPendingApprovalWrapper.setUnpaidloans(loans.getUnpaidloans());
                        return loansPendingApprovalWrapper;
                    })
                    .collect(Collectors.toList());
            UniversalResponse response = new UniversalResponse("success", getResponseMessage("loansPendingApprovalPerUser"),
                    loansPendingApprovalWrapperList);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("numofrecords", loanapplicationsRepo.countByMemberIdAndPendingTrue(member.getId()));
            response.setMetadata(metadata);
            return Mono.just(response);
        }).publishOn(Schedulers.boundedElastic()).flatMap(res -> res);
    }

    @Override
    public Mono<UniversalResponse> getLoansPendingApprovalbyLoanProduct(long loanproductid, String currentUser, int page, int size) {
        return Mono.fromCallable(() -> {
            Optional<LoanProducts> optionalLoanProduct = loanproductsRepository.findById(loanproductid);
            if (optionalLoanProduct.isEmpty()) {
                return new UniversalResponse("fail", getResponseMessage("loanProductNotFound"));
            }
            Pageable pageable = PageRequest.of(page, size);
            List<LoanApplications> loanApplicationsList = loanapplicationsRepo.findByLoanProductsAndPendingTrueOrderByCreatedOnDesc(optionalLoanProduct.get(), pageable);
            List<LoansPendingApprovalWrapper> loansPendingApprovalWrapperList = loanApplicationsList
                    .stream()
                    .filter(loan -> checkAllGuarantorsApproved(loan.getId()))
                    .map(loan -> {
                        MemberWrapper member = chamaKycService.getMonoMemberDetailsById(loan.getMemberId());
                        if (member == null) return null;
                        LoansPendingApprovalWrapper loansPendingApprovalWrapper = new LoansPendingApprovalWrapper();
                        loansPendingApprovalWrapper.setAmount(loan.getAmount());
                        loansPendingApprovalWrapper.setMembername(member.getFirstname().concat(" ").concat(member.getLastname()));
                        loansPendingApprovalWrapper.setAppliedon(loan.getCreatedOn());
                        loansPendingApprovalWrapper.setLoanproductid(loan.getLoanProducts().getId());
                        loansPendingApprovalWrapper.setLoanproductname(loan.getLoanProducts().getProductname());
                        loansPendingApprovalWrapper.setMemberphonenumber(member.getPhonenumber());
                        loansPendingApprovalWrapper.setLoanapplicationid(loan.getId());
                        Type type = new TypeToken<Map<String, Object>>() {
                        }.getType();
                        Map<String, Object> member_reminder = gson.fromJson(loan.getReminder(), type);
                        loansPendingApprovalWrapper.setReminder(member_reminder);
                        loansPendingApprovalWrapper.setUnpaidloans(loan.getUnpaidloans());
                        return loansPendingApprovalWrapper;
                    }).collect(Collectors.toList());

            UniversalResponse response = new UniversalResponse("success", getResponseMessage("loansPendingApprovalPerProduct"),
                    loansPendingApprovalWrapperList);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("numofrecords", loanapplicationsRepo.countAllByLoanProductsAndPendingTrue(optionalLoanProduct.get()));
            response.setMetadata(metadata);
            return response;
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> approveLoanApplication(boolean approve, long loanApplicationId, String approvedBy) {
        return Mono.fromCallable(() -> {
            MemberWrapper member = chamaKycService.searchMonoMemberByPhoneNumber(approvedBy);

            if (member == null) {
                return new UniversalResponse("fail", getResponseMessage("appUserNotFound"));
            }

            Optional<LoanApplications> optionalLoanApplication = loanapplicationsRepo.findById(loanApplicationId);
            if (optionalLoanApplication.isEmpty()) {
                return new UniversalResponse("fail", getResponseMessage("loanApplicationNotFound"));
            }
            LoanApplications loanApplications = optionalLoanApplication.get();
            if (!loanApplications.isPending()) {
                return new UniversalResponse("fail", getResponseMessage("loanApplicationNotPending"));
            }
            long memberId = loanApplications.getMemberId();
            if (member.getId() == memberId) {
                return new UniversalResponse("fail", getResponseMessage("cannotApproveSelfLoanApplication"), Collections.emptyList());
            }
            MemberWrapper loanedMember = chamaKycService.getMonoMemberDetailsById(memberId);
            if (loanedMember == null) {
                return new UniversalResponse("fail", getResponseMessage("loanedMemberNotFound"));
            }

            String loanedMemberName = String.format("%s %s", loanedMember.getFirstname(), loanedMember.getLastname());
            String loanedMemberPhone = String.format("%s", loanedMember.getPhonenumber());
            String memberLanguage = String.format("%s", loanedMember.getLanguage());

            long groupId = loanApplications.getLoanProducts().getGroupId();
            GroupMemberWrapper groupMemberWrapper = chamaKycService.getMonoGroupMembershipByGroupIdAndMemberId(groupId, member.getId());

            if (groupMemberWrapper == null) {
                return new UniversalResponse("fail", getResponseMessage("loanApproverNotInGroup"));
            }

            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(groupId);
            if (groupWrapper == null) {
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"));
            }

            String groupName = groupWrapper.getName();
            Accounts account = accountsRepository.findByGroupIdAndActiveTrueAndSoftDeleteFalse(groupMemberWrapper.getGroupId());

            groupAccountBalanceInquiry(groupWrapper, account);

            if (account.getAccountbalance() < loanApplications.getAmount()) {
                return new UniversalResponse("fail", getResponseMessage("accountBalanceIsInsufficient"));
            }

            JsonObject approvals = gson.fromJson(loanApplications.getApprovedby(), JsonObject.class);

            if (approvals.has(groupMemberWrapper.getTitle())) {
                return new UniversalResponse("fail", getResponseMessage("youCannotAuthorizeMoreThanOnce"));
            }

            if (groupMemberWrapper.getTitle().equals("member")) {
                return new UniversalResponse("fail", getResponseMessage("accountsOfficialsApproval"));
            }

            approvals.addProperty(groupMemberWrapper.getTitle(), member.getId());

            if (!approve) {
                loanApplications.setPending(false);
                loanApplications.setApproved(true);
                loanApplications.setStatus("Approved");
                loanApplications.setApprovalCount(loanApplications.getApprovalCount() + 1);
                loanApplications.setApprovedby(approvals.toString());
                loanapplicationsRepo.save(loanApplications);
                //todo::send sms to the initiator
                notificationService.sendLoanDeclinedMessage(loanedMember.getPhonenumber(),
                        loanedMember.getFirstname(), loanApplications.getAmount(), loanedMember.getLanguage());
                //todo:: send to officials
                sendOfficialsLoanDeclinedMessage(groupId, loanApplications.getAmount(), loanedMemberPhone, loanedMemberName);
                auditTrail("group member loans", "group member loans declined successfully.", approvedBy);
                creatNotification(groupWrapper.getId(), groupWrapper.getName(), "group member loans declined successfully by " + approvedBy);
                return new UniversalResponse("success", getResponseMessage("loanRequestDeclinedSuccessfully"));
            }

            loanApplications.setApprovalCount(loanApplications.getApprovalCount() + 1);
            loanApplications.setApprovedby(approvals.toString());
            loanApplications.setStatus(PaymentEnum.PAYMENT_PENDING.name());
            loanApplications.setPending(true);
            loanApplications.setApproved(false);
            loanApplications.setTransactionDate(new Date());
            loanapplicationsRepo.save(loanApplications);

            if (loanApplications.getApprovalCount() > 1) {
                loanApplications.setStatus("Approved");
                loanApplications.setPending(false);
                loanApplications.setApproved(true);
                loanapplicationsRepo.save(loanApplications);
                String chargeRef = TransactionIdGenerator.generateTransactionId("CHG");
                String accountRef = member.getPhonenumber();

                Map<String, String> esbChargeRequest = constructChargesBody(
                        accountRef,
                        (int) loanApplications.getAmount(),
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

                //todo:: disburse the money
                disburseLoan(groupWrapper.getCsbAccount(), loanApplications.getAmount(), loanApplications.getAccountToDeposit(), loanedMemberPhone, loanedMemberName, memberLanguage, groupName, loanApplications, account, chargeAmount);

                deactivateLoanNotification(approvedBy);
                auditTrail("group member loans", "group member loans approved successfully.", approvedBy);
                creatNotification(groupWrapper.getId(), groupWrapper.getName(), "group member loans approved successfully by " + approvedBy);
                return new UniversalResponse("success", getResponseMessage("approvalDone"));
            }
            return new UniversalResponse("success", getResponseMessage("loanApprovalSuccessful"));
        }).publishOn(Schedulers.boundedElastic());
    }

    @Async
    protected void sendOfficialsLoanDeclinedMessage(long groupId, double amount, String loanedMemberPhone, String loanedMemberName) {
        GroupWrapper group = chamaKycService.getMonoGroupById(groupId);
        if (group != null && group.isActive()) {
            String groupName = group.getName();
            //todo:: send to memebers
            chamaKycService.getFluxGroupMembers(groupId)
                    .filter(gm -> !gm.getPhonenumber().equals(loanedMemberPhone))
                    .subscribe(official ->
                            notificationService.sendOfficialsLoanDeclinedMessage(groupId, groupName, loanedMemberName, amount, official.getFirstname(), official.getPhonenumber(), official.getLanguage())
                    );

        } else {
            log.error("Could not send Loan Application SMS. Group not found.");
        }
    }

    private void deactivateLoanNotification(String approvedBy) {
        Notifications notifications = new Notifications();
        notifications.setMessage("Loan Application Processed Successfully");
        notifications.setLastModifiedDate(new Date());
        notifications.setLastModifiedBy(approvedBy);
        notificationsRepository.save(notifications);
    }

    @Async
    public void disburseLoan(String debitAccountId, Double amount, String phoneNumber, String loanedMemberPhone, String loanedMemberName, String memberLanguage, String groupName, LoanApplications loanApplications, Accounts account, Integer chargeAmount) {


        String transactionId = TransactionIdGenerator.generateTransactionId("LDM");
        Map<String, String> esbRequest =
                constructBody(debitAccountId, phoneNumber, phoneNumber, amount.intValue(),
                        transactionId, "LD", String.valueOf(chargeAmount));
        String body = gson.toJson(esbRequest);
        log.info("CHANNEL API LOAN REQUEST {}", body);
        String scope = "MC";

        String loanResponse = postBankWebClient.post()
                .uri(postBankUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .subscribeOn(Schedulers.boundedElastic())
                .block();

        JsonObject loanResponseJsonObject = gson.fromJson(loanResponse, JsonObject.class);
        log.info("CHANNEL API LOAN RESP0NSE {}", loanResponseJsonObject);

        if (!loanResponseJsonObject.get("field39").getAsString().equals("00")) {
            log.info("LOAN DISBURSAL FAILURE REASON... {}", loanResponseJsonObject.get("field48").getAsString());
            String loanDisbursementFailure = loanResponseJsonObject.get("field48").getAsString();
            notificationService.loanDisbursementRequestFailureText(groupName, loanedMemberPhone, loanedMemberName, amount, loanDisbursementFailure, memberLanguage);
            esbLoggingService.logESBRequest(body, scope);
            return;
        }

        if (loanResponseJsonObject.get("field39").getAsString().equals("00")) {
            log.info("CHANNEL API LOAN RESP0NSE === SUCCESS {}", loanResponseJsonObject);
            esbLoggingService.logESBRequest(body, scope);
            saveWithdrawalLog(loanApplications, account, loanedMemberPhone, loanedMemberName, memberLanguage);
        }

    }

    public void saveWithdrawalLog(LoanApplications loanApplications, Accounts accounts, String loanedMemberPhone, String loanedMemberName, String memberLanguage) {
        WithdrawalLogs withdrawalLogs = new WithdrawalLogs();
        withdrawalLogs.setCapturedby(loanedMemberPhone);
        withdrawalLogs.setContribution_narration(String.format("loan disbursement for member %s", loanedMemberName));
        withdrawalLogs.setContributions(loanApplications.getLoanProducts().getContributions());
        withdrawalLogs.setCreditphonenumber(loanedMemberPhone);
        withdrawalLogs.setDebitAccounts(accounts);
        withdrawalLogs.setTransactionDate(new Date());
        withdrawalLogs.setTransamount(loanApplications.getAmount());
        withdrawalLogs.setUniqueTransactionId("LA_" + accounts.getAccountType().getAccountPrefix().concat(String.valueOf(new Date().getTime())));
        withdrawalLogs.setWithdrawalreason(String.format("loan disbursement for member %s", loanedMemberName));
        logLoanApplication(withdrawalLogs, accounts, loanApplications, loanedMemberPhone, loanedMemberName, memberLanguage);
    }


    public void logLoanApplication(WithdrawalLogs withdrawalLogs, Accounts accounts, LoanApplications loanApplications, String loanedMemberPhone, String loanedMemberName, String memberLanguage) {
        withdrawalLogs.setNewbalance(accounts.getAccountbalance() - loanApplications.getAmount());
        withdrawalLogs.setOldbalance(accounts.getAccountbalance());
        LoansDisbursed loansDisbursed = new LoansDisbursed();
        LoanProducts loanProducts = loanApplications.getLoanProducts();
        String interesttype = loanProducts.getInteresttype();
        double loanInterest = calculateInterest(loanApplications.getGroupId(), interesttype, loanApplications.getAmount());

        withdrawallogsRepo.save(withdrawalLogs);
        accountsRepository.save(accounts);
        //todo:: update disbursed loan
        loansDisbursed.setInterest(loanInterest);
        loansDisbursed.setPrincipal(loanApplications.getAmount());
        loansDisbursed.setDueamount(loanInterest + loanApplications.getAmount());
        loansDisbursed.setLoanApplications(loanApplications);
        loansDisbursed.setPaymentPeriodType(loanProducts.getPaymentperiodtype());
        loansDisbursed.setWithdrawalLogs(withdrawalLogs);
        loansDisbursed.setGroupId(accounts.getGroupId());
        loansDisbursed.setTransactionDate(new Date());
        loansDisbursed.setMemberId(loanApplications.getMemberId());
        loansDisbursed.setStatus(PaymentEnum.YET_TO_PAY.name());
        LoansDisbursed loans = loansdisbursedRepo.save(loansDisbursed);
        //todo:: send sms
        notificationService.sendLoanApprovedMessage(loanedMemberPhone, loanedMemberName,
                loans.getDueamount(), memberLanguage);
    }

    private double calculateInterest(Long groupId, String interesttype, double amount) {
        double interest = 0;
        Optional<Contributions> checkContributions = contributionsRepository.findByGroupIdAndActiveTrueAndSoftDeleteFalse(groupId);
        if (checkContributions.isEmpty())
            return interest;
        Contributions contributions = checkContributions.get();
        if (contributions.getLoanInterest().equals("0")) {
            return interest;
        }
        double period = Double.parseDouble(contributions.getPaymentPeriod());
        double interestvalue = Double.parseDouble(contributions.getLoanInterest());
        String frequency = contributions.getFrequency();
        if (interesttype.toLowerCase().contains("simple")) {
            period = period * 30;
            interest = Math.ceil((amount * interestvalue * period / 365) / 100);
            return interest;

        } else if (interesttype.toLowerCase().contains("compound")) {
            if (frequency.equalsIgnoreCase("monthly")) {
                interest = Math.ceil((1 + interestvalue / 100));

                interestvalue = Math.ceil(Math.pow(interest, period / 12));

                double totalamount = amount * interestvalue;
                interest = totalamount - amount;
            }
            return interest;

        } else if (interesttype.toLowerCase().contains("flat")) {
            interest = interestvalue;
            return interest;
        }
        return interest;
    }


    @Override
    public Mono<UniversalResponse> getDisbursedLoansperGroup(long groupid, int page, int size) {
        return Mono.fromCallable(() -> {
            GroupWrapper group = chamaKycService.getMonoGroupById(groupid);
            if (group == null)
                return new UniversalResponse("fail", getResponseMessage(GROUP_NOT_FOUND));
            Pageable pageable = PageRequest.of(page, size);
            List<DisbursedloansWrapper> disbursedloansWrapperList =
                    loansdisbursedRepo.findByGroupId(group.getId(), pageable).stream()
                            .map(p -> {
                                MemberWrapper member = chamaKycService.getMonoMemberDetailsById(p.getMemberId());
                                if (member == null) return null;
                                DisbursedloansWrapper disbursedloansWrapper = new DisbursedloansWrapper();
                                disbursedloansWrapper.setContributionid(p.getLoanApplications().getLoanProducts().getContributions().getId());
                                disbursedloansWrapper.setContributionname(p.getLoanApplications().getLoanProducts().getContributions().getName());
                                disbursedloansWrapper.setDueamount(Math.ceil(p.getDueamount()));
                                disbursedloansWrapper.setGroupid(group.getId());
                                disbursedloansWrapper.setGroupname(group.getName());
                                disbursedloansWrapper.setPaymentperiodtype(p.getPaymentPeriodType());
                                disbursedloansWrapper.setApprovedon(p.getCreatedOn());
                                disbursedloansWrapper.setDuedate(p.getDuedate());
                                disbursedloansWrapper.setInterest(Math.ceil(p.getInterest()));
                                disbursedloansWrapper.setLoanid(p.getId());
                                disbursedloansWrapper.setPrincipal(p.getPrincipal());
                                disbursedloansWrapper.setTransactionDate(new Date());
                                disbursedloansWrapper.setRecipient(String.format("%s %s", member.getFirstname(), member.getLastname()));
                                disbursedloansWrapper.setRecipientsnumber(member.getPhonenumber());
                                disbursedloansWrapper.setLoanproductname(p.getLoanApplications().getLoanProducts().getProductname());
                                Accounts accounts = p.getLoanApplications().getLoanProducts().getDebitAccountId();
                                AccountWrapper accountWrapper = AccountWrapper.builder()
                                        .groupid(group.getId())
                                        .accountid(accounts.getId())
                                        .accountbalance(accounts.getAvailableBal())
                                        .accountdetails(accounts.getAccountdetails())
                                        .accountname(accounts.getName())
                                        .accounttypeid(accounts.getAccountType().getId())
                                        .build();
                                disbursedloansWrapper.setDebitAccount(accountWrapper);
                                disbursedloansWrapper.setAccountTypeId(accountWrapper.getAccounttypeid());
                                disbursedloansWrapper.setAppliedon(p.getCreatedOn());
                                disbursedloansWrapper.setGuarantor(p.getLoanApplications().getLoanProducts().isGuarantor());
                                return disbursedloansWrapper;
                            })
                            .collect(Collectors.toList());
            UniversalResponse response = new UniversalResponse("success", getResponseMessage("loansDisbursedPerGroup"),
                    disbursedloansWrapperList);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("numofrecords", loansdisbursedRepo.countByGroupId(group.getId()));
            response.setMetadata(metadata);
            return response;
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> getDisbursedLoansperLoanproduct(long loanproductid, int page, int size) {
        return Mono.fromCallable(() -> {
            Optional<LoanProducts> optionalLoanProducts = loanproductsRepository.findById(loanproductid);
            if (optionalLoanProducts.isEmpty()) {
                return new UniversalResponse("fail", getResponseMessage("loanProductNotFound"), Collections.emptyList());
            }

            GroupWrapper group = chamaKycService.getMonoGroupById(optionalLoanProducts.get().getGroupId());
            if (group == null)
                return UniversalResponse.builder().status("fail").message(getResponseMessage(GROUP_NOT_FOUND)).build();

            Pageable pageable = PageRequest.of(page, size);
            List<DisbursedloansWrapper> disbursedloansWrapperList =
                    loansdisbursedRepo.findByLoanProduct(group.getId(), pageable).stream()
                            .map(p -> {
                                MemberWrapper member = chamaKycService.getMonoMemberDetailsById(p.getMemberId());
                                if (member == null)
                                    return null;
                                DisbursedloansWrapper disbursedloansWrapper = new DisbursedloansWrapper();
                                disbursedloansWrapper.setContributionid(p.getLoanApplications().getLoanProducts().getContributions().getId());
                                disbursedloansWrapper.setContributionname(p.getLoanApplications().getLoanProducts().getContributions().getName());
                                disbursedloansWrapper.setDueamount(Math.ceil(p.getDueamount()));
                                disbursedloansWrapper.setGroupid(group.getId());
                                disbursedloansWrapper.setGroupname(group.getName());
                                disbursedloansWrapper.setDuedate(p.getDuedate());
                                disbursedloansWrapper.setApprovedon(p.getCreatedOn());
                                disbursedloansWrapper.setInterest(Math.ceil(p.getInterest()));
                                disbursedloansWrapper.setLoanid(p.getId());
                                disbursedloansWrapper.setTransactionDate(new Date());
                                disbursedloansWrapper.setPrincipal(p.getPrincipal());
                                disbursedloansWrapper.setRecipient(String.format("%s %s", member.getFirstname(), member.getLastname()));
                                disbursedloansWrapper.setRecipientsnumber(member.getPhonenumber());
                                disbursedloansWrapper.setLoanproductname(p.getLoanApplications().getLoanProducts().getProductname());
                                Accounts accounts = p.getLoanApplications().getLoanProducts().getDebitAccountId();
                                AccountWrapper accountWrapper = AccountWrapper.builder()
                                        .accountbalance(accounts.getAccountbalance())
                                        .accountname(accounts.getName())
                                        .accounttypeid(accounts.getAccountType().getId())
                                        .build();
                                disbursedloansWrapper.setDebitAccount(accountWrapper);
                                disbursedloansWrapper.setAccountTypeId(accountWrapper.getAccounttypeid());
                                disbursedloansWrapper.setAppliedon(p.getCreatedOn());
                                disbursedloansWrapper.setGuarantor(p.getLoanApplications().getLoanProducts().isGuarantor());
                                return disbursedloansWrapper;
                            })
                            .collect(Collectors.toList());
            UniversalResponse response = new UniversalResponse("success", getResponseMessage("loansDisbursedPerProduct"),
                    disbursedloansWrapperList);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("numofrecords", loansdisbursedRepo.countLoansDisbursedbyLoanproduct(loanproductid));
            response.setMetadata(metadata);
            return response;
        }).publishOn(Schedulers.boundedElastic());
    }


    @Override
    public Mono<UniversalResponse> getDisbursedLoansperUser(long filterId, String phonenumber, int page, int size) {
        return Mono.fromCallable(() -> {
            MemberWrapper member = chamaKycService.searchMonoMemberByPhoneNumber(phonenumber);
            if (member == null) {
                return new UniversalResponse("fail", "Member not found");
            }
            GroupWrapper group = chamaKycService.getMonoGroupById(filterId);
            if (group == null) {
                return UniversalResponse.builder().status("fail").message(getResponseMessage(GROUP_NOT_FOUND)).build();
            }

            Pageable pageable = PageRequest.of(page, size);

            List<DisbursedloansWrapper> disbursedloansWrapperList =
                    loansdisbursedRepo.findByGroupIdAndMemberIdOrderByCreatedOnDesc(filterId, member.getId(), pageable).stream()
                            .map(p -> {
                                DisbursedloansWrapper disbursedloansWrapper = new DisbursedloansWrapper();
                                disbursedloansWrapper.setContributionid(p.getLoanApplications().getLoanProducts().getContributions().getId());
                                disbursedloansWrapper.setContributionname(p.getLoanApplications().getLoanProducts().getContributions().getName());
                                disbursedloansWrapper.setDueamount(Math.ceil(p.getDueamount()));
                                disbursedloansWrapper.setGroupid(group.getId());
                                disbursedloansWrapper.setGroupname(group.getName());
                                disbursedloansWrapper.setPaymentperiodtype(p.getPaymentPeriodType());
                                disbursedloansWrapper.setApprovedon(p.getCreatedOn());
                                disbursedloansWrapper.setDuedate(p.getDuedate());
                                disbursedloansWrapper.setInterest(Math.ceil(p.getInterest()));
                                disbursedloansWrapper.setLoanid(p.getId());
                                disbursedloansWrapper.setPrincipal(p.getPrincipal());
                                disbursedloansWrapper.setTransactionDate(new Date());
                                disbursedloansWrapper.setRecipient(String.format("%s %s", member.getFirstname(), member.getLastname()));
                                disbursedloansWrapper.setRecipientsnumber(member.getPhonenumber());
                                disbursedloansWrapper.setLoanproductname(p.getLoanApplications().getLoanProducts().getProductname());
                                Accounts accounts = p.getLoanApplications().getLoanProducts().getDebitAccountId();
                                AccountWrapper accountWrapper = AccountWrapper.builder()
                                        .groupid(group.getId())
                                        .accountid(accounts.getId())
                                        .accountbalance(accounts.getAvailableBal())
                                        .accountdetails(accounts.getAccountdetails())
                                        .accountname(accounts.getName())
                                        .accounttypeid(accounts.getAccountType().getId())
                                        .build();
                                disbursedloansWrapper.setDebitAccount(accountWrapper);
                                disbursedloansWrapper.setAccountTypeId(accountWrapper.getAccounttypeid());
                                disbursedloansWrapper.setAppliedon(p.getCreatedOn());
                                disbursedloansWrapper.setGuarantor(p.getLoanApplications().getLoanProducts().isGuarantor());
                                return disbursedloansWrapper;
                            })
                            .collect(Collectors.toList());

            UniversalResponse response = new UniversalResponse("success", getResponseMessage("loansDisbursedPerUser"),
                    disbursedloansWrapperList);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("numofrecords", loansdisbursedRepo.countByGroupIdAndMemberIdAndDueamountGreaterThan(filterId, member.getId(), 0));
            response.setMetadata(metadata);
            return response;
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> getLoanPaymentPendingApprovalByUser(String phonenumber, int page, int size) {
        return Mono.fromCallable(() -> {
            MemberWrapper member = chamaKycService.searchMonoMemberByPhoneNumber(phonenumber);
            if (member == null)
                return new UniversalResponse("fail", "Member not found");
            Pageable pageable = PageRequest.of(page, size);
            List<LoanpaymentPendingApprovalWrapper> loanpaymentPendingApprovalWrappersList =
                    loanrepaymentpendingapprovalRepo.findByMemberIdAndPendingTrueOrderByCreatedOnDesc(member.getId(), pageable)
                            .stream()
                            .filter(payment -> payment.getMemberId() != member.getId())
                            .map(p -> {
                                MemberWrapper loanedMember = chamaKycService.getMonoMemberDetailsById(p.getMemberId());
                                if (loanedMember == null) return null;
                                return LoanpaymentPendingApprovalWrapper.builder()
                                        .dueamount(p.getLoansDisbursed().getDueamount())
                                        .duedate(p.getLoansDisbursed().getDuedate())
                                        .loanid(p.getLoansDisbursed().getId())
                                        .loanproductname(p.getLoansDisbursed().getLoanApplications().getLoanProducts().getProductname())
                                        .membername(String.format("%s %s", loanedMember.getFirstname(), loanedMember.getLastname()))
                                        .memberphone(loanedMember.getPhonenumber())
                                        .receiptnumber(p.getReceiptnumber())
                                        .receiptImageUrl(p.getReceiptImageUrl())
                                        .loanpaymentid(p.getId())
                                        .transamount(p.getAmount())
                                        .paymentDate(p.getCreatedOn())
                                        .transactionDate(p.getTransactionDate())
                                        .appliedon(p.getLoansDisbursed().getCreatedOn())
                                        .build();
                            }).collect(Collectors.toList());

            UniversalResponse response = new UniversalResponse("success", getResponseMessage("loanPaymentsPendingApprovalByUser"),
                    loanpaymentPendingApprovalWrappersList);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("numofrecords", loanrepaymentpendingapprovalRepo.countByMemberIdAndPendingTrue(member.getId()));
            response.setMetadata(metadata);
            return response;
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> getLoanPaymentPendingApprovalByGroup(long groupid, String currentUser, int page, int size) {
        return Mono.fromCallable(() -> {
            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(groupid);
            if (groupWrapper == null)
                return new UniversalResponse("fail", getResponseMessage(GROUP_NOT_FOUND));
            Pageable pageable = PageRequest.of(page, size);

            List<LoanpaymentPendingApprovalWrapper> loanpaymentPendingApprovalWrapperList =
                    loanrepaymentpendingapprovalRepo.findpendingbyGroupid(groupid, pageable)
                            .stream()
                            .filter(p -> !p.getPaymentType().equalsIgnoreCase("mpesa"))
                            .map(p -> {
                                MemberWrapper member = chamaKycService.getMonoMemberDetailsById(p.getMemberId());
                                if (member == null) return null;
                                LoanpaymentPendingApprovalWrapper loanpaymentPendingApprovalWrapper = new LoanpaymentPendingApprovalWrapper();
                                loanpaymentPendingApprovalWrapper.setDueamount(p.getLoansDisbursed().getDueamount());
                                loanpaymentPendingApprovalWrapper.setDuedate(p.getLoansDisbursed().getDuedate());
                                loanpaymentPendingApprovalWrapper.setLoanid(p.getLoansDisbursed().getId());
                                loanpaymentPendingApprovalWrapper.setLoanproductname(p.getLoansDisbursed().getLoanApplications().getLoanProducts().getProductname());
                                loanpaymentPendingApprovalWrapper.setMembername(member.getFirstname().concat(" ").concat(member.getLastname()));
                                loanpaymentPendingApprovalWrapper.setMemberphone(member.getPhonenumber());
                                loanpaymentPendingApprovalWrapper.setReceiptnumber(p.getReceiptnumber());
                                loanpaymentPendingApprovalWrapper.setTransamount(p.getAmount());
                                loanpaymentPendingApprovalWrapper.setLoanpaymentid(p.getId());
                                loanpaymentPendingApprovalWrapper.setReceiptImageUrl(p.getReceiptImageUrl());
                                loanpaymentPendingApprovalWrapper.setPaymentDate(p.getCreatedOn());
                                loanpaymentPendingApprovalWrapper.setAppliedon(p.getCreatedOn());
                                loanpaymentPendingApprovalWrapper.setTransactionDate(p.getTransactionDate());
                                return loanpaymentPendingApprovalWrapper;
                            })
                            .collect(Collectors.toList());
            UniversalResponse response = new UniversalResponse("success", getResponseMessage("loanPaymentsPendingApprovalByGroup"),
                    loanpaymentPendingApprovalWrapperList);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("numofrecords", loanrepaymentpendingapprovalRepo.countpendingbyGroupid(groupid));
            response.setMetadata(metadata);
            return response;
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> getLoanPaymentsbyUser(String phonenumber, int page, int size) {
        return Mono.fromCallable(() -> {
            MemberWrapper member = chamaKycService.searchMonoMemberByPhoneNumber(phonenumber);
            if (member == null)
                return new UniversalResponse("fail", "Member not found");
            Pageable pageable = PageRequest.of(page, size);
            List<LoanpaymentsWrapper> loanpaymentsWrapperList = loansrepaymentRepo.findByMemberIdOrderByCreatedOnDesc(member.getId(), pageable)
                    .stream()
                    .map(p -> {
                        LoanpaymentsWrapper loanpaymentsWrapper = new LoanpaymentsWrapper();
                        loanpaymentsWrapper.setAmount(p.getAmount());
                        loanpaymentsWrapper.setLoandisbursedid(p.getLoansDisbursed().getId());
                        loanpaymentsWrapper.setLoanproductname(p.getLoansDisbursed().getLoanApplications().getLoanProducts().getProductname());
                        loanpaymentsWrapper.setMembername(member.getFirstname().concat(" ").concat(member.getLastname()));
                        loanpaymentsWrapper.setMemberphonenumber(member.getPhonenumber());
                        loanpaymentsWrapper.setNewamount(p.getNewamount());
                        loanpaymentsWrapper.setOldamount(p.getOldamount());
                        loanpaymentsWrapper.setReceiptnumber(p.getReceiptnumber());
                        loanpaymentsWrapper.setAppliedon(p.getCreatedOn());
                        loanpaymentsWrapper.setTransactionDate(p.getTransactionDate());
                        return loanpaymentsWrapper;
                    })
                    .collect(Collectors.toList());

            UniversalResponse response = new UniversalResponse("success", getResponseMessage("loanPaymentsByUser"),
                    loanpaymentsWrapperList);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("numofrecords", loansrepaymentRepo.countByMemberId(member.getId()));
            response.setMetadata(metadata);
            return response;
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> getLoanPaymentsbyGroupid(long groupid, int page, int size) {
        return Mono.fromCallable(() -> {
            GroupWrapper optionalGroup = chamaKycService.getMonoGroupById(groupid);
            if (optionalGroup == null) return new UniversalResponse("fail", getResponseMessage(GROUP_NOT_FOUND));
            Pageable pageable = PageRequest.of(page, size);
            List<LoanpaymentsWrapper> loanpaymentsWrapperList = loansrepaymentRepo.getloanpaymentsbyGroupid(groupid, pageable)
                    .stream()
                    .map(p -> {
                        MemberWrapper member = chamaKycService.getMonoMemberDetailsById(p.getMemberId());
                        if (member == null) return null;
                        LoanpaymentsWrapper loanpaymentsWrapper = new LoanpaymentsWrapper();
                        loanpaymentsWrapper.setAmount(p.getAmount());
                        loanpaymentsWrapper.setLoandisbursedid(p.getLoansDisbursed().getId());
                        loanpaymentsWrapper.setLoanproductname(p.getLoansDisbursed().getLoanApplications().getLoanProducts().getProductname());
                        loanpaymentsWrapper.setMembername(member.getFirstname().concat(" ").concat(member.getLastname()));
                        loanpaymentsWrapper.setMemberphonenumber(member.getPhonenumber());
                        loanpaymentsWrapper.setNewamount(p.getNewamount());
                        loanpaymentsWrapper.setOldamount(p.getOldamount());
                        loanpaymentsWrapper.setReceiptnumber(p.getReceiptnumber());
                        loanpaymentsWrapper.setAppliedon(p.getCreatedOn());
                        loanpaymentsWrapper.setTransactionDate(p.getTransactionDate());
                        return loanpaymentsWrapper;
                    })
                    .collect(Collectors.toList());

            UniversalResponse response = new UniversalResponse("success", getResponseMessage("loanRepaymentsByGroup"),
                    loanpaymentsWrapperList);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("numofrecords", loansrepaymentRepo.countloanpaymentsbyGroupid(groupid));
            response.setMetadata(metadata);
            return response;
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> getLoanPaymentsbyDisbursedloan(long disbursedloanid, int page, int size) {
        return Mono.fromCallable(() -> {
            Optional<LoansDisbursed> optionalLoansDisbursed = loansdisbursedRepo.findById(disbursedloanid);
            if (optionalLoansDisbursed.isEmpty())
                return new UniversalResponse("fail", getResponseMessage("loanDisbursedNotFound"));
            LoansDisbursed loansDisbursed = optionalLoansDisbursed.get();
            Pageable pageable = PageRequest.of(page, size);
            List<LoanpaymentsWrapper> loanpaymentsWrapperList =
                    loansrepaymentRepo.findByLoansDisbursedOrderByCreatedOnDesc(loansDisbursed, pageable)
                            .stream()
                            .map(p -> {
                                MemberWrapper member = chamaKycService.getMonoMemberDetailsById(p.getMemberId());
                                if (member == null) return null;
                                LoanpaymentsWrapper loanpaymentsWrapper = new LoanpaymentsWrapper();
                                loanpaymentsWrapper.setAmount(p.getAmount());
                                loanpaymentsWrapper.setLoandisbursedid(p.getLoansDisbursed().getId());
                                loanpaymentsWrapper.setLoanproductname(p.getLoansDisbursed().getLoanApplications().getLoanProducts().getProductname());
                                loanpaymentsWrapper.setMembername(member.getFirstname().concat(" ").concat(member.getLastname()));
                                loanpaymentsWrapper.setMemberphonenumber(member.getPhonenumber());
                                loanpaymentsWrapper.setNewamount(p.getNewamount());
                                loanpaymentsWrapper.setOldamount(p.getOldamount());
                                loanpaymentsWrapper.setReceiptnumber(p.getReceiptnumber());
                                loanpaymentsWrapper.setAppliedon(p.getCreatedOn());
                                loanpaymentsWrapper.setTrxdate(p.getCreatedOn());
                                loanpaymentsWrapper.setTransactionDate(p.getTransactionDate());
                                return loanpaymentsWrapper;
                            })
                            .collect(Collectors.toList());
            UniversalResponse response = new UniversalResponse("success", getResponseMessage("loanRepaymentsByLoanDisbursed"),
                    loanpaymentsWrapperList);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("numofrecords", loansrepaymentRepo.countByLoansDisbursed(loansDisbursed));
            response.setMetadata(metadata);
            return response;
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> getOverdueLoans(long groupid, int page, int size) {
        return Mono.fromCallable(() -> {
            GroupWrapper group = chamaKycService.getMonoGroupById(groupid);
            if (group == null)
                return new UniversalResponse("fail", "Group not found");
            Pageable pageable = PageRequest.of(page, size);
            List<DisbursedloansWrapper> disbursedloansWrapperList =
                    loansdisbursedRepo
                            .findByGroupIdAndDueamountGreaterThanAndDuedateLessThanOrderByCreatedOnDesc(groupid, 0.0, new Date(), pageable)
                            .stream()
                            .map(p -> {
                                MemberWrapper member = chamaKycService.getMonoMemberDetailsById(p.getMemberId());
                                if (member == null) return null;
                                DisbursedloansWrapper disbursedloansWrapper = new DisbursedloansWrapper();
                                disbursedloansWrapper.setContributionid(p.getLoanApplications().getLoanProducts().getContributions().getId());
                                disbursedloansWrapper.setContributionname(p.getLoanApplications().getLoanProducts().getContributions().getName());
                                disbursedloansWrapper.setDueamount(p.getDueamount());
                                disbursedloansWrapper.setGroupid(group.getId());
                                disbursedloansWrapper.setGroupname(group.getName());
                                disbursedloansWrapper.setDuedate(p.getDuedate());
                                long diff = new Date().getTime() - p.getDuedate().getTime();
                                long daysdue = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS);
                                disbursedloansWrapper.setDaysoverdue(daysdue);
                                disbursedloansWrapper.setInterest(p.getInterest());
                                disbursedloansWrapper.setLoanid(p.getId());
                                disbursedloansWrapper.setPrincipal(p.getPrincipal());
                                disbursedloansWrapper.setTransactionDate(p.getTransactionDate());
                                disbursedloansWrapper.setRecipient(member.getFirstname().concat(" ").concat(member.getLastname()));
                                disbursedloansWrapper.setRecipientsnumber(member.getPhonenumber());
                                return disbursedloansWrapper;
                            })
                            .collect(Collectors.toList());

            UniversalResponse response = new UniversalResponse("success", getResponseMessage("loansOverdue"),
                    disbursedloansWrapperList);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("numofrecords", loansdisbursedRepo.countByGroupIdAndDueamountGreaterThanAndDuedateLessThan(groupid, 0.0, new Date()));
            response.setMetadata(metadata);
            return response;
        }).publishOn(Schedulers.boundedElastic());
    }


    @Override
    public Mono<UniversalResponse> getGroupLoansPenalties(Long groupId) {
        return Mono.fromCallable(() -> {
            GroupWrapper optionalGroup = chamaKycService.getMonoGroupById(groupId);
            if (optionalGroup == null)
                return new UniversalResponse("fail", "Group not found");
            List<LoansDisbursed> loansDisbursedList = loansdisbursedRepo.findAllByGroupIdAndStatusAndSoftDeleteFalseOrderByIdAsc(groupId, PaymentEnum.YET_TO_PAY.name());
            List<LoanPenalty> loanPenalties = new ArrayList<>();
            loansDisbursedList.forEach(loansDisbursed -> {
                List<LoanPenalty> loanPenaltyList = loanPenaltyRepository.findAllByLoansDisbursed(loansDisbursed);
                loanPenalties.addAll(loanPenaltyList);
            });
            return getLoanPenalties(loanPenalties);
        }).publishOn(Schedulers.boundedElastic());
    }

    private UniversalResponse getLoanPenalties(List<LoanPenalty> loanPenaltyList) {
        List<LoanPenaltyWrapper> loanPenaltyWrappers = loanPenaltyList
                .parallelStream()
                .map(lp -> mapToLoanPenaltyWrapper().apply(lp))
                .collect(Collectors.toList());
        UniversalResponse response = new UniversalResponse("success", getResponseMessage("loanPenalties"),
                loanPenaltyWrappers);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("numofrecords", loanPenaltyWrappers.size());
        response.setMetadata(metadata);
        return response;
    }

    Function<LoanPenalty, LoanPenaltyWrapper> mapToLoanPenaltyWrapper() {
        return lp -> {
            MemberWrapper member = chamaKycService.getMemberDetailsById(lp.getMemberId()).orElse(null);
            if (member == null) return null;
            LoanPenaltyWrapper loanPenaltyWrapper = new LoanPenaltyWrapper();
            loanPenaltyWrapper.setLoanPenaltyId(lp.getId());
            loanPenaltyWrapper.setDueAmount(lp.getDueAmount());
            loanPenaltyWrapper.setLoanDueDate(lp.getLoanDueDate());
            loanPenaltyWrapper.setTransactionDate(lp.getTransactionDate());
            loanPenaltyWrapper.setMemberName(member.getFirstname().concat(" ").concat(member.getLastname()));
            loanPenaltyWrapper.setMemberPhoneNumber(member.getPhonenumber());
            return loanPenaltyWrapper;
        };
    }

    @Override
    public Mono<UniversalResponse> getMemberLoansPenalties(String phoneNumber) {
        return Mono.fromCallable(() -> {
            MemberWrapper member = chamaKycService.searchMonoMemberByPhoneNumber(phoneNumber);
            if (member == null)
                return new UniversalResponse("fail", getResponseMessage(MEMBER_NOT_FOUND));

            List<LoanPenalty> loanPenaltyList = loanPenaltyRepository.findAllByMemberId(member.getId());
            return getLoanPenalties(loanPenaltyList);
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> getMemberLoansPenalties(String phoneNumber, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return Mono.fromCallable(() -> {
            MemberWrapper member = chamaKycService.searchMonoMemberByPhoneNumber(phoneNumber);
            if (member == null)
                return new UniversalResponse("fail", getResponseMessage(MEMBER_NOT_FOUND));

            Page<LoanPenalty> pageData = loanPenaltyRepository.findAllByMemberId(member.getId(), pageable);

            List<LoanPenaltyWrapper> loanPenaltyWrappers = pageData.getContent()
                    .parallelStream()
                    .map(lp -> mapToLoanPenaltyWrapper().apply(lp))
                    .collect(Collectors.toList());

            return UniversalResponse.builder()
                    .status("success")
                    .message(getResponseMessage("loanPenaltiesList"))
                    .data(loanPenaltyWrappers)
                    .metadata(Map.of("currentPage", pageData.getNumber(), "numOfRecords", pageData.getNumberOfElements(), "totalPages", pageData.getTotalPages()))
                    .timestamp(new Date())
                    .build();
        }).publishOn(Schedulers.boundedElastic());
    }


    @Override
    public Mono<UniversalResponse> getInactiveGroupLoanProducts(Long groupId) {
        return Mono.fromCallable(() -> {
            GroupWrapper group = chamaKycService.getMonoGroupById(groupId);
            if (group == null)
                return new UniversalResponse("fail", "Group not found");
            List<LoanproductWrapper> loanproductWrapperList = loanproductsRepository.findAllByGroupIdAndIsActive(groupId, false)
                    .stream()
                    .map(p -> mapToLoanProductWrapper(group, p))
                    .collect(Collectors.toList());

            UniversalResponse response = new UniversalResponse("success", getResponseMessage("inactiveLoanProductsPerGroup"),
                    loanproductWrapperList);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("numofrecords", loanproductWrapperList.size());
            response.setMetadata(metadata);
            return response;
        }).publishOn(Schedulers.boundedElastic());
    }

    @NonNull
    private LoanproductWrapper mapToLoanProductWrapper(GroupWrapper group, LoanProducts p) {
        Accounts accounts = accountsRepository.findTopByGroupIdAndActiveTrueAndSoftDeleteFalseOrderByIdDesc(group.getId());
        if (!(accounts == null)) {
            LoanproductWrapper loanproductWrapper = new LoanproductWrapper();
            loanproductWrapper.setGroupname(group.getName());
            loanproductWrapper.setInteresttype(p.getInteresttype());
            loanproductWrapper.setInterestvalue(p.getInterestvalue());
            loanproductWrapper.setMax_principal(p.getMax_principal());
            loanproductWrapper.setMin_principal(p.getMin_principal());
            loanproductWrapper.setProductid(p.getId());
            loanproductWrapper.setProductname(p.getProductname());
            loanproductWrapper.setPaymentperiod(p.getPaymentperiod());
            loanproductWrapper.setPaymentperiodtype(p.getPaymentperiodtype());
            loanproductWrapper.setGroupid(group.getId());
            loanproductWrapper.setDescription(p.getDescription());
            loanproductWrapper.setContributionid(p.getContributions().getId());
            loanproductWrapper.setContributionname(p.getContributions().getName());
            loanproductWrapper.setIsguarantor(p.isGuarantor());
            loanproductWrapper.setHasPenalty(p.isPenalty());
            loanproductWrapper.setPenaltyvalue(p.getPenaltyValue());
            loanproductWrapper.setIspenaltypercentage(p.getIsPercentagePercentage());
            loanproductWrapper.setUsersavingvalue(p.getUserSavingValue());
            loanproductWrapper.setUserLoanLimit(loanLimit(p));
            loanproductWrapper.setIsActive(p.getIsActive());
            loanproductWrapper.setTransactionDate(p.getCreatedOn());
            loanproductWrapper.setDebitAccountId(accounts.getId());
            loanproductWrapper.setPenaltyPeriod(p.getPenaltyPeriod());
            return loanproductWrapper;
        }
        return null;

    }

    @Override
    public Mono<UniversalResponse> getGroupsLoanSummaryPayment(String groupName, Date startDate, Date endDate, Pageable pageable) {
        return Mono.fromCallable(() -> {
            List<GroupsLoanSummaryWrapper> loansList;
            if (!groupName.equalsIgnoreCase("all")) {
                GroupWrapper group = chamaKycService.getMonoGroupByName(groupName);
                if (group == null)
                    return new UniversalResponse("fail", getResponseMessage(GROUP_NOT_FOUND));

                loansList = loansrepaymentRepo.findAllByGroupIdAndCreatedOnBetweenAndSoftDeleteFalseOrderByCreatedOnDesc(group.getId(), startDate, endDate, pageable)
                        .stream()
                        .map(loan -> {
                            MemberWrapper member = chamaKycService.getMonoMemberDetailsById(loan.getMemberId());
                            if (member == null) return null;
                            return new GroupsLoanSummaryWrapper(member.getFirstname()
                                    , member.getLastname(), group.getName(),
                                    loan.getPaymentType(), loan.getAmount(), loan.getCreatedOn());
                        })
                        .collect(Collectors.toList());
            } else {
                loansList = loansrepaymentRepo.findAllByCreatedOnBetweenAndSoftDeleteFalseOrderByCreatedOnDesc(startDate, endDate, pageable)
                        .stream()
                        .map(loan -> {
                            MemberWrapper member = chamaKycService.getMonoMemberDetailsById(loan.getLoansDisbursed().getMemberId());
                            if (member == null) return null;
                            GroupWrapper group = chamaKycService.getMonoGroupById(loan.getLoansDisbursed().getGroupId());
                            if (group == null) return null;
                            return GroupsLoanSummaryWrapper.builder()
                                    .firstName(member.getFirstname())
                                    .lastName(member.getLastname())
                                    .groupName(group.getName())
                                    .paymentType(loan.getPaymentType())
                                    .amount(loan.getAmount())
                                    .paymentDate(loan.getCreatedOn())
                                    .build();
                        })
                        .collect(Collectors.toList());
            }
            return new UniversalResponse("success", String.format(getResponseMessage("loanRepaymentsFor"), groupName), loansList);
        }).publishOn(Schedulers.boundedElastic());
    }


    private UniversalResponse validateCoreAccount(String coreAccount, MemberWrapper member) {
        if (coreAccount.isBlank() && member.getLinkedAccounts() == null)
            return new UniversalResponse("fail", getResponseMessage("memeberHasNoLinkedAccounts"));

        if (coreAccount.isBlank() && Arrays.stream(member.getLinkedAccounts().split(",")).noneMatch(s -> s.equals(coreAccount)))
            return new UniversalResponse("fail", getResponseMessage("coreAccountDoesNotBelongToMember"));
        return null;
    }


    @Override
    public Mono<UniversalResponse> getLoanApplications(Long loanProductId, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return Mono.fromCallable(() -> {
            Page<LoanApplicationsProjection> loanApplications = loanapplicationsRepo.findLoanApplications(loanProductId, pageable);
            Map<String, Integer> metadata = Map.of(
                    "currentpage", loanApplications.getNumber(),
                    "totalcount", loanApplications.getTotalPages(),
                    "numofrecords", loanApplications.getNumberOfElements()
            );

            return new UniversalResponse("success", getResponseMessage("loanApplicationsForLoanProduct"), loanApplications.getContent(), new Date(), metadata);
        }).publishOn(Schedulers.boundedElastic());
    }


    @Override
    public Mono<UniversalResponse> getUserLoanApplications(String phoneNumber, Integer page, Integer size) {
        return Mono.fromCallable(() -> {
            MemberWrapper memberWrapper = chamaKycService.searchMonoMemberByPhoneNumber(phoneNumber);

            if (memberWrapper == null) return new UniversalResponse("fail", getResponseMessage(MEMBER_NOT_FOUND));

            List<LoanApplications> memberLoanApplications = loanapplicationsRepo.findAllByMemberId(memberWrapper.getId());

            return new UniversalResponse("success", getResponseMessage("memberLoanApplicationList"), memberLoanApplications);
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> getLoanPaymentsByLoanProductProduct(long loanProductId, int page, int size) {
        return Mono.fromCallable(() -> {
            Optional<LoanProducts> optionalLoanProduct = loanproductsRepository.findById(loanProductId);

            if (optionalLoanProduct.isEmpty())
                return new UniversalResponse("fail", getResponseMessage("loanProductNotFound"));

            Pageable pageable = PageRequest.of(page, size);
            Page<LoanRepaymentsProjection> repaymentsByLoanProduct = loansrepaymentRepo.findAllRepaymentsByLoanProduct(loanProductId, pageable);

            return UniversalResponse.builder()
                    .status("success")
                    .message("Loan repayments for loan product")
                    .data(repaymentsByLoanProduct.getContent())
                    .metadata(Map.of("numofrecords", repaymentsByLoanProduct.getTotalElements()))
                    .build();
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> getUserLoanProducts(String username) {
        return chamaKycService.getFluxGroupsMemberBelongs(username)
                .filter(GroupWrapper::isActive)
                .map(GroupWrapper::getId)
                .publishOn(Schedulers.boundedElastic())
                .map(loanproductsRepository::findAllByGroupIdAndSoftDeleteFalse)
                .flatMap(Flux::fromIterable)
                .mapNotNull(this::mapToLoanProductWrapper)
                .collectList()
                .map(lps -> UniversalResponse.builder()
                        .status("success")
                        .message("Loan Products for User")
                        .data(lps)
                        .build());
    }

    @Override
    public Mono<UniversalResponse> getActiveLoanProductsbyGroup(Long groupId, boolean isActive) {
        return Mono.fromCallable(() -> {
            GroupWrapper optionalGroup = chamaKycService.getMonoGroupById(groupId);

            if (optionalGroup == null)
                return new UniversalResponse("fail", getResponseMessage(GROUP_NOT_FOUND));

            List<LoanproductWrapper> loanProductsList = loanproductsRepository.findAllByGroupIdAndIsActive(groupId, true)
                    .stream()
                    .map(p -> {
                        return mapToLoanProductWrapper(optionalGroup, p);
                    })
                    .collect(Collectors.toList());

            UniversalResponse response = new UniversalResponse("success", getResponseMessage("loanProductsPerGroup"),
                    loanProductsList);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("numofrecords", loanProductsList.size());
            response.setMetadata(metadata);
            return response;
        }).publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UniversalResponse> loanLimit(LoanLimitWrapper wrapper, String username) {
        return Mono.fromCallable(() -> {
            MemberWrapper memberWrapper = chamaKycService.searchMonoMemberByPhoneNumber(username);

            if (memberWrapper == null)
                return new UniversalResponse("fail", getResponseMessage("memberNotFound"));

            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(wrapper.getGroupId());

            if (groupWrapper == null) {
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"));
            }
            Contributions contributions = contributionsRepository.findFirstByGroupIdAndActiveTrueAndSoftDeleteFalse(wrapper.getGroupId());

            if (contributions == null) {
                return new UniversalResponse("fail", getResponseMessage("contributionNotFound"));
            }

            LoanProducts loanProducts = loanproductsRepository.findFirstByGroupIdAndIsActiveTrueAndSoftDeleteFalse(wrapper.getGroupId());

            if (loanProducts == null)
                return new UniversalResponse("fail", getResponseMessage("loanProductNotFound"));

            MemberWrapper member = chamaKycService.searchMonoMemberByPhoneNumber(wrapper.getPhoneNumber());

            if (member == null)
                return new UniversalResponse("fail", getResponseMessage("memberNotFound"));

            GroupMemberWrapper groupMemberWrapper = chamaKycService.memberIsPartOfGroup(wrapper.getGroupId(), wrapper.getPhoneNumber());

            if (groupMemberWrapper == null)
                return new UniversalResponse("fail", getResponseMessage("memberIsNotPartOfGroup"));

            return new UniversalResponse("success", "success");
        }).publishOn(Schedulers.boundedElastic());

    }

    @Override
    public Mono<UniversalResponse> loanInterest(LoanInterestWrapper wrapper, String username) {
        return Mono.fromCallable(() -> {
            MemberWrapper memberWrapper = chamaKycService.searchMonoMemberByPhoneNumber(username);

            if (memberWrapper == null) {
                return new UniversalResponse("fail", getResponseMessage("memberNotFound"));
            }

            GroupWrapper groupWrapper = chamaKycService.getMonoGroupById(wrapper.getGroupId());

            if (groupWrapper == null) {
                return new UniversalResponse("fail", getResponseMessage("groupNotFound"));
            }

            long groupId = groupWrapper.getId();
            long memberId = memberWrapper.getId();

            GroupMemberWrapper groupMembership = chamaKycService.getMonoGroupMembershipByGroupIdAndMemberId(groupId, memberId);
            if (groupMembership == null) {
                return new UniversalResponse("fail", getResponseMessage("memberIsNotPartOfGroup"));
            }

            double interest = 0;
            Contributions contributions = contributionsRepository.findFirstByGroupIdAndActiveTrueAndSoftDeleteFalse(groupId);

            if (contributions == null) {
                return new UniversalResponse("fail", getResponseMessage("contributionNotFound"));
            }
            if (contributions.getPaymentPeriod() == null) {
                return new UniversalResponse("fail", getResponseMessage("contributionPeriodNotSet"));
            }

            LoanProducts loanProducts = loanproductsRepository.findFirstByGroupIdAndIsActiveTrueAndSoftDeleteFalse(groupId);

            if (loanProducts == null) {
                return new UniversalResponse("fail", getResponseMessage("loanProductNotFound"));
            }

            double period = Double.parseDouble(contributions.getPaymentPeriod());
            double interestvalue = Double.parseDouble(contributions.getLoanInterest());

            String interestType = loanProducts.getInteresttype() == null ? "simple" : loanProducts.getInteresttype();

            String frequency = contributions.getFrequency();

            if (interestType.toLowerCase().contains("simple")) {
                period = period * 30;
                interest = Math.ceil((wrapper.getLoanAmount() * interestvalue * period / 365) / 100);

            } else if (interestType.toLowerCase().contains("compound")) {
                if (frequency.equalsIgnoreCase("monthly")) {
                    interest = Math.ceil((1 + interestvalue / 100));

                    interestvalue = Math.ceil(Math.pow(interest, period / 12));

                    double totalAmount = wrapper.getLoanAmount() * interestvalue;
                    interest = totalAmount - wrapper.getLoanAmount();
                } else {
                    interest = 0;
                }

            } else if (interestType.toLowerCase().contains("flat")) {
                interest = interestvalue;
            }
            double totalLoan = wrapper.getLoanAmount() + interest;

            log.info("AMOUNT {}, INTEREST TYPE {}, FREQUENCY {}, INTEREST {}, INTEREST AMOUNT {}, TOTAL AMOUNT {}", wrapper.getLoanAmount(), interestType, frequency, interestvalue, interest, totalLoan);

            return new UniversalResponse("success", "Loan Interest", Map.of("amountApplied", wrapper.getLoanAmount(), "frequency", frequency, "interestRate", interestvalue, "interestType", interestType, "interestAmount", interest, "totalLoan", totalLoan));
        }).publishOn(Schedulers.boundedElastic());
    }

    private LoanproductWrapper mapToLoanProductWrapper(LoanProducts p) {
        Optional<String> optionalGroupName = chamaKycService.getGroupNameByGroupId(p.getGroupId());

        if (optionalGroupName.isEmpty()) return null;

        LoanproductWrapper loanproductWrapper = new LoanproductWrapper();
        loanproductWrapper.setGroupname(optionalGroupName.get());
        loanproductWrapper.setInteresttype(p.getInteresttype());
        loanproductWrapper.setInterestvalue(p.getInterestvalue());
        loanproductWrapper.setMax_principal(p.getMax_principal());
        loanproductWrapper.setMin_principal(p.getMin_principal());
        loanproductWrapper.setProductid(p.getId());
        loanproductWrapper.setProductname(p.getProductname());
        loanproductWrapper.setPaymentperiod(p.getPaymentperiod());
        loanproductWrapper.setPaymentperiodtype(p.getPaymentperiodtype());
        loanproductWrapper.setGroupid(p.getGroupId());
        loanproductWrapper.setDescription(p.getDescription());
        loanproductWrapper.setContributionid(p.getContributions().getId());
        loanproductWrapper.setContributionname(p.getContributions().getName());
        loanproductWrapper.setIsguarantor(p.isGuarantor());
        loanproductWrapper.setHasPenalty(p.isPenalty());
        loanproductWrapper.setPenaltyvalue(p.getPenaltyValue());
        loanproductWrapper.setIspenaltypercentage(p.getIsPercentagePercentage());
        loanproductWrapper.setUsersavingvalue(p.getUserSavingValue());
        loanproductWrapper.setUserLoanLimit(loanLimit(p));
        loanproductWrapper.setIsActive(p.getIsActive());
        loanproductWrapper.setDebitAccountId(p.getDebitAccountId().getId());
        loanproductWrapper.setPenaltyPeriod(p.getPenaltyPeriod());
        return loanproductWrapper;
    }

    private void groupAccountBalanceInquiry(GroupWrapper groupWrapper, Accounts account) {

        String transactionId = RandomStringUtils.randomNumeric(12);

        Map<String, String> balanceInquiryReq = getBalanceInquiryReq(
                groupWrapper.getCsbAccount(), "0", transactionId, transactionId);
        log.info("balance Inquiry Req {}", balanceInquiryReq);

        postBankWebClient.post().uri(postBankUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(gson.toJson(balanceInquiryReq))
                .retrieve()
                .bodyToMono(String.class)
                .subscribe(jsonString -> {
                    JsonObject jsonObject = new Gson().fromJson(jsonString, JsonObject.class);
                    if (!jsonObject.get("field39").getAsString().equals("00"))
                        return;
                    String availableBalance = jsonObject.get("Available_balance").getAsString();
                    String actualBalance = jsonObject.get("Actual_balance").getAsString();
                    account.setAccountbalance(Double.parseDouble(actualBalance));
                    account.setAvailableBal(Double.parseDouble(availableBalance));
                    account.setLastModifiedDate(new Date());
                    account.setBalanceRequestDate(new Date());
                    accountsRepository.save(account);
                });
    }

}
