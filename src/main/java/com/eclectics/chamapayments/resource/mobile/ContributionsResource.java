package com.eclectics.chamapayments.resource.mobile;

import com.eclectics.chamapayments.config.CustomAuthenticationUtil;
import com.eclectics.chamapayments.service.AccountingService;
import com.eclectics.chamapayments.service.CallbackServicePublisher;
import com.eclectics.chamapayments.service.ChamaKycService;
import com.eclectics.chamapayments.service.enums.CanTransact;
import com.eclectics.chamapayments.wrappers.esbWrappers.EsbAccountWrapper;
import com.eclectics.chamapayments.wrappers.esbWrappers.EsbWrapper;
import com.eclectics.chamapayments.wrappers.request.*;
import com.eclectics.chamapayments.wrappers.response.ContributionDetailsWrapper;
import com.eclectics.chamapayments.wrappers.response.PendingApprovalsWrapper;
import com.eclectics.chamapayments.wrappers.response.UniversalResponse;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import springfox.documentation.annotations.ApiIgnore;

@Slf4j
@RestController
@RequestMapping("/api/v2/payment/contribution")
@RequiredArgsConstructor
public class ContributionsResource {

    private final ChamaKycService chamaKycService;
    private final AccountingService accountingService;
    private final CallbackServicePublisher callbackServicePublisher;

    @PostMapping("/new")
    public Mono<ResponseEntity<?>> createContribution(@RequestBody ContributionDetailsWrapper contributionDetailsWrapper) {
        return accountingService.addContribution(contributionDetailsWrapper)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @PostMapping("/edit")
    public Mono<ResponseEntity<?>> editContribution(@RequestBody ContributionDetailsWrapper contributionDetailsWrapper, @RequestParam String username) {
        return accountingService.editContribution(contributionDetailsWrapper, username)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }


    @PostMapping("/user-loan-limit")
    @ApiOperation(value = "Get users loan limit", notes = "Pass the group id, contribution id and loan product id")
    public Mono<ResponseEntity<UniversalResponse>> getUserLoanLimit(@RequestBody LoanLimitRequest loanLimitRequest, @RequestParam String username) {
        return accountingService.checkLoanLimit(username, loanLimitRequest.getGroupId(), loanLimitRequest.getContributionId(), loanLimitRequest.getLoanProductId())
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }


    @PostMapping("/pay-for-other")
    @CanTransact
    public Mono<ResponseEntity<UniversalResponse>> makeContributionForOther(@RequestBody ContributionPaymentDto dto, @ModelAttribute String usernameParam) {
        return CustomAuthenticationUtil.getUsername()
                .flatMap(username -> accountingService.makeContributionForOtherMember(dto, username))
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }


    @PostMapping(value = "/payment", consumes = {"application/json"})
    public Mono<ResponseEntity<UniversalResponse>> makeContribution(@RequestBody ContributionPaymentDto dto, @RequestParam String phoneNumber) {
        return accountingService.makeContribution(dto, phoneNumber)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }


    @PostMapping("/fine-payment")
    public Mono<ResponseEntity<UniversalResponse>> payForContributionPenalty(@RequestBody ContributionPaymentDto dto) {
        return accountingService.payForContributionPenalty(dto)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @ApiIgnore
    @PostMapping("/ft-callback")
    public Mono<ResponseEntity<?>> fundsTransferCallback(@RequestBody String body) {
        callbackServicePublisher.publishCallback(body);
        return Mono.just(ResponseEntity.ok().build());
    }


    @PostMapping("/esb-account-validation")
    public Mono<ResponseEntity<?>> esbAccountValidation(@RequestBody EsbAccountWrapper wrapper) {
        return accountingService.esbAccountValidation(wrapper)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @PostMapping("/withdraw")
    public Mono<ResponseEntity<UniversalResponse>> recordWithdrawal(@RequestBody RequestwithdrawalWrapper
                                                                            requestwithdrawalWrapper) {
        return accountingService.recordWithdrawal(requestwithdrawalWrapper)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @PostMapping("/approve")
    public Mono<ResponseEntity<?>> approveContributionWithdrawal(@RequestBody WithdrawalApprovalRequest request, @RequestParam String username) {
        return accountingService.approveWithdrawalRequest(request, username)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }


    @PostMapping("/group-pending-withdrawal")
    public Mono<ResponseEntity<?>> getPendingWithdrawals(@RequestBody PendingWithdrawalsRequest request) {
        return Mono.fromCallable(() -> accountingService.getPendingWithdrawalRequestByGroupId(request.getGroupId(),
                        request.getPage(), request.getSize()))
                .publishOn(Schedulers.boundedElastic())
                .map(res -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new UniversalResponse("success", "withdrawals pending approval", res.getContent())));
    }

    @GetMapping("/pending-withdrawal")
    public Mono<ResponseEntity<?>> approveContributionByReceipt(@RequestBody WithdrawalApprovalRequest request, @RequestParam String usernameParam) {
        return accountingService.approveContributionPayment(request.getRequestId(), request.getApprove(), usernameParam)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @GetMapping("/user-contribution-payment")
    public Mono<ResponseEntity<?>> getUserContributionPayments(@RequestParam String username) {
        return accountingService.getUserContributionPayments(username)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }


    @PostMapping("/group-contribution-payment")
    public Mono<ResponseEntity<?>> getGroupContributionPayments(@RequestBody GroupContributionsRequest request) {
        return accountingService.getGroupContributionPayments(request.getContributionId(), request.getPage(), request.getSize())
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @PostMapping("/upcoming/{groupId}")
    public Mono<ResponseEntity<?>> getUserUpcomingContributionPayments(@RequestBody EsbWrapper esbWrapper, @RequestParam String username) {
        return accountingService.getUserUpcomingPayments(username, esbWrapper.getGroupId())
                .map(res -> ResponseEntity.ok().body(res));
    }


    @PostMapping("/upcoming/user")
    public Mono<ResponseEntity<?>> getUserUpcomingContributionPayment(@RequestParam String username) {
        return accountingService.getUserUpcomingPayments(username)
                .map(res -> ResponseEntity.ok().body(res));
    }

    @PostMapping("/upcoming")
    public Mono<ResponseEntity<?>> getAllUserUpcomingContributionPayments(@RequestParam String username) {
        return accountingService.getAllUserUpcomingPayments(username)
                .map(res -> ResponseEntity.ok().body(res));
    }

    @GetMapping("/members")
    public Mono<ResponseEntity<?>> getMembers() {
        return Mono.fromCallable(chamaKycService::getFluxMembers)
                .flatMap(Flux::collectList)
                .publishOn(Schedulers.boundedElastic())
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @GetMapping("/user/analytics")
    public Mono<ResponseEntity<?>> getUserTotalContributionsInGroup(@RequestParam String username) {
        return accountingService.getUserContributionsPerGroup(username)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @GetMapping("/penalties")
    public Mono<ResponseEntity<?>> getMemberContributionPenalties(@RequestParam String username) {
        return accountingService.getAllMemberPenalties(username)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @GetMapping("/group/penalties")
    public Mono<ResponseEntity<?>> getGroupContributionPenalties(@RequestParam long groupId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return accountingService.getGroupContributionPenalties(groupId, page, size)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @PostMapping("/all")
    public Mono<ResponseEntity<?>> getContributionsInGroup(@RequestParam Long groupId) {
        return accountingService.getGroupContributions(groupId)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @GetMapping("/{cid}")
    public Mono<ResponseEntity<?>> getContribution(@PathVariable Long cid) {
        return accountingService.getGroupContribution(cid)
                .doOnNext(res -> log.info(res.getStatus()))
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @PostMapping("/user-overpaid-contribution")
    public Mono<ResponseEntity<?>> getUserOverpaidContributions(@RequestParam String username) {
        return accountingService.getOverpaidContributions(username)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    //edit contribution
    @PostMapping("/edit-contribution")
    public Mono<ResponseEntity<?>> editContribution(@RequestBody EditContributionWrapper req, @RequestParam String username) {
        return accountingService.editContributionPostBank(req, username)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @PostMapping("/approve-decline-edit-contribution")
    public Mono<ResponseEntity<?>> approveDeclineContribution(@RequestBody ContributionsApprovalRequest req, @RequestParam String approvedBy) {
        return accountingService.approveDeclineContribution(req, approvedBy)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @PostMapping("/edit-contribution/pending-approvals")
    public Mono<ResponseEntity<?>> editContributionPendingApprovals(@RequestBody PendingApprovalsWrapper request, @RequestParam String user) {
        return accountingService.editContributionPendingApprovals(request, user)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    //todo:: get group contribution
    @PostMapping("/group-contribution")
    public Mono<ResponseEntity<?>> getGroupContribution(@RequestBody EsbWrapper esbWrapper) {
        return accountingService.getGroupContribution(esbWrapper.getGroupId())
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }
}
