package com.eclectics.chamapayments.resource.ussd;

import com.eclectics.chamapayments.config.CustomAuthenticationUtil;
import com.eclectics.chamapayments.service.AccountingService;
import com.eclectics.chamapayments.wrappers.request.*;
import com.eclectics.chamapayments.wrappers.response.ContributionDetailsWrapper;
import com.eclectics.chamapayments.wrappers.response.UniversalResponse;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.validation.Valid;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/payment/ussd/contribution")
public class UssdContributionsResource {

    private final AccountingService accountingService;


    @PostMapping("/payment")
    public Mono<ResponseEntity<UniversalResponse>> makeContribution(@RequestBody ContributionPaymentDto dto, @RequestParam String phoneNumber) {
        return accountingService.makeContribution(dto, phoneNumber)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @PostMapping("/fine-payment")
    public Mono<ResponseEntity<UniversalResponse>> payForContributionPenalty(@RequestBody ContributionPaymentDto dto) {
        return accountingService.payForContributionPenalty(dto)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @PostMapping("/withdraw")
    public Mono<ResponseEntity<UniversalResponse>> recordWithdrawal(@RequestBody @Valid RequestwithdrawalWrapper requestwithdrawalWrapper) {
        return CustomAuthenticationUtil.getUsername()
                .flatMap(username -> accountingService.recordWithdrawal(requestwithdrawalWrapper))
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


    @PostMapping("/details")
    @ApiOperation(value = "Get contributions in a group", notes = "Just pass the id")
    public Mono<ResponseEntity<?>> getContribution(@RequestBody ContributionDetailsWrapper contributionDetailsWrapper) {
        return accountingService.getGroupContribution(contributionDetailsWrapper.getId())
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

}
