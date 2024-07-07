package com.eclectics.chamapayments.resource.portal;

import com.eclectics.chamapayments.config.CustomAuthenticationUtil;
import com.eclectics.chamapayments.service.AccountingService;
import com.eclectics.chamapayments.wrappers.request.CanWithdrawWrapper;
import com.eclectics.chamapayments.wrappers.request.ShareOutsDisbursementWrapper;
import com.eclectics.chamapayments.wrappers.request.ShareOutsWrapper;
import com.eclectics.chamapayments.wrappers.response.UniversalResponse;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.validation.Valid;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.Collections;

import static com.eclectics.chamapayments.service.constants.StringConstantsUtil.PHONE_NUMBER_MATCH;

@Validated
@RestController
@RequestMapping("/portal/payments")
@RequiredArgsConstructor
public class PortalContributionsResource {

    private final AccountingService accountingService;

    @PreAuthorize("hasAnyAuthority('VIEW_DASHBOARD')")
    @GetMapping("/user-contributions")
    public Mono<ResponseEntity<?>> getUserContributionPayments(
            @RequestParam @Size(max = 12, message = "Phone number length can only be of length 12")
            @Pattern(regexp = PHONE_NUMBER_MATCH, message = "Phone number cannot contain special characters and letters") String phoneNumber,
            @RequestParam int page,
            @RequestParam int size) {
        return accountingService.getUserContributionPayments(phoneNumber, page, size)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @PreAuthorize("hasAnyAuthority('VIEW_DASHBOARD')")
    @GetMapping("/transactions/{filter}")
    @ApiOperation(value = "the value `filter` is an enum for the selections which transactions can be fetched by." +
            "These include `group`, `contribution`, `user`, `account`. The query parameter `value` takes the value for " +
            "the filter ")
    public Mono<ResponseEntity<?>> getTransactions(@PathVariable String filter,
                                                   @RequestParam String filterid,
                                                   @RequestParam int page, @RequestParam int size,
                                                   @RequestParam(required = false) String username) {
        Pageable pageable = PageRequest.of(page, size);
        switch (filter) {
            case "group":
                return accountingService.getGroupTransactions(Long.valueOf(filterid), page, size, username)
                        .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
            case "contribution":
                return Mono.fromCallable(() -> accountingService.getTransactionsByContributions(Long.parseLong(filterid), pageable, ""))
                        .publishOn(Schedulers.boundedElastic())
                        .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
            case "user":
                return Mono.fromCallable(() -> accountingService.getTransactionsByUser(filterid, pageable))
                        .publishOn(Schedulers.boundedElastic())
                        .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
            case "account":
                return Mono.fromCallable(() -> accountingService.getTransactionsByAccount(Long.valueOf(filterid), pageable, ""))
                        .publishOn(Schedulers.boundedElastic())
                        .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
            case "other":
                return Mono.fromCallable(() -> accountingService.getGroupTransactionsFromOtherChannels(Long.valueOf(filterid), pageable, ""))
                        .publishOn(Schedulers.boundedElastic())
                        .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
            default:
                return Mono.just(new UniversalResponse("failed",
                                "only values accepted on the path param are `group`, `contribution`, `user`", Collections.emptyList()))
                        .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
        }
    }

    @PreAuthorize("hasAnyAuthority('VIEW_DASHBOARD')")
    @GetMapping("/withdrawals/{filter}")
    @ApiOperation(value = "the value `filter` is an enum for the selections which transactions can be fetched by." +
            "These include `group`, `contribution`, `user`, `account`. The query parameter `value` takes the value for " +
            "the filter ")
    public Mono<ResponseEntity<?>> getWithdrawals(@PathVariable String filter,
                                                  @RequestParam @Pattern(regexp = "\\d", message = "Filter id needs to be an integer value") String filterid,
                                                  @RequestParam int page, @RequestParam int size) {
        Pageable pageable = PageRequest.of(page, size);
        switch (filter) {
            case "group":
                return Mono.fromCallable(() -> accountingService.getWithdrawalsbyGroup(Long.parseLong(filterid), pageable))
                        .publishOn(Schedulers.boundedElastic())
                        .map(res -> new UniversalResponse("success", "group withdrawals", res))
                        .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
            case "contribution":
                return Mono.fromCallable(() -> accountingService.getWithdrawalsbyContribution(Long.valueOf(filterid), pageable))
                        .publishOn(Schedulers.boundedElastic())
                        .map(res -> new UniversalResponse("success", "group withdrawals by contribution", res))
                        .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
            case "user":
                return Mono.fromCallable(() -> accountingService.getWithdrawalsbyUser(filterid, pageable))
                        .publishOn(Schedulers.boundedElastic())
                        .map(res -> new UniversalResponse("success", "group withdrawals by user", res))
                        .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
            case "account":
                return Mono.fromCallable(() -> accountingService.getWithdrawalsbyAccount(Long.valueOf(filterid), pageable))
                        .publishOn(Schedulers.boundedElastic())
                        .map(res -> new UniversalResponse("success", "group withdrawals by account", res))
                        .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
            default:
                return Mono.just(new UniversalResponse("failed",
                                "only values accepted on the path param are `group`, `contribution`, `user`,`account`", Collections.emptyList()))
                        .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
        }
    }

    @PreAuthorize("hasAnyAuthority('VIEW_DASHBOARD')")
    @GetMapping("/contribution-types")
    @ApiOperation(value = "get the types of contributions")
    public Mono<ResponseEntity<?>> getContributionTypes() {
        return Mono.fromCallable(accountingService::getContributionTypes)
                .publishOn(Schedulers.boundedElastic())
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }



    @PreAuthorize("hasAnyAuthority('VIEW_DASHBOARD')")
    @GetMapping("/amount-types")
    @ApiOperation(value = "get the amount supported on the platform")
    public Mono<ResponseEntity<?>> getAmountTypes() {
        return Mono.fromCallable(accountingService::getAmounttypes)
                .publishOn(Schedulers.boundedElastic())
                .map(res -> new UniversalResponse("Success", "Amount types", res))
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }


    @GetMapping("/account-types")
    @ApiOperation(value = "retrieve account types supported on the platform")
    public Mono<ResponseEntity<?>> getAccountTypes() {
        return Mono.fromCallable(accountingService::findAccountTypes)
                .publishOn(Schedulers.boundedElastic())
                .map(res -> new UniversalResponse("Success", "Account types", res))
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }


    @GetMapping("/group-contributions")
    public Mono<ResponseEntity<?>> getGroupContributions(@RequestParam Long groupId) {
        return accountingService.getGroupContributions(groupId)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @PreAuthorize("hasAnyAuthority('VIEW_DASHBOARD')")
    @GetMapping("/group-accounts")
    public Mono<ResponseEntity<?>> groupAccounts(@RequestParam Long groupId) {
        return Mono.fromCallable(() -> accountingService.getAccountbyGroup(groupId))
                .publishOn(Schedulers.boundedElastic())
                .map(res -> new UniversalResponse("Success", "Group accounts", res))
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

  //TODO::  SHARE OUTS
    @PreAuthorize("hasAnyAuthority('ENABLE_GROUP','DISABLE_GROUP')")
    @PostMapping("/manual/share/outs")
    public Mono<ResponseEntity<UniversalResponse>> shareOuts(@Valid @RequestBody ShareOutsWrapper wrapper) {
        return accountingService.shareOuts(wrapper)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));

    }

    @PreAuthorize("hasAnyAuthority('VIEW_DASHBOARD')")
    @PostMapping("/share-outs/disbursement/{filter}")
    @ApiOperation(value = "the value `filter` is an enum for the selections which share outs can be fetched by." +
            "These include group, member, and status. The query parameter `value` takes the value for " +
            "the filter ")
    public Mono<ResponseEntity<?>> shareOutsDisbursement(@Valid @RequestBody ShareOutsDisbursementWrapper wrapper) {
        return Mono.fromCallable(() -> {
                    switch (wrapper.getFilter()) {
                        case "group":
                            return CustomAuthenticationUtil.getUsername()
                                    .flatMap(username -> accountingService.getIndividualGroupMembersShareOutsDisbursement(username, wrapper))
                                    .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));

                        case "member":
                            return CustomAuthenticationUtil.getUsername()
                                    .flatMap(username -> accountingService.getGroupMemberShareOutsDisbursement(username, wrapper))
                                    .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));

                        case "all":
                            return CustomAuthenticationUtil.getUsername()
                                    .flatMap(username -> accountingService.getGroupsMembersShareOutsDisbursement(username, wrapper))
                                    .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));

                        default:
                            return Mono.just(new UniversalResponse("failed",
                                            "only values accepted on the path param are `group`, `member`,`status`", Collections.emptyList()))
                                    .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
                    }

                })
                .flatMap(res -> res)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }


    @PreAuthorize("hasAnyAuthority('ENABLE_GROUP','DISABLE_GROUP')")
    @PostMapping("/canWithdraw")
    public Mono<ResponseEntity<?>> canWithdraw(@RequestBody @Valid CanWithdrawWrapper req) {
        return CustomAuthenticationUtil.getUsername()
                .flatMap(username -> accountingService.approveAccount(req, username))
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));

    }
}
