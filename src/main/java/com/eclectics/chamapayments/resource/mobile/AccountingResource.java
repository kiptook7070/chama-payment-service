package com.eclectics.chamapayments.resource.mobile;

import com.eclectics.chamapayments.service.AccountingService;
import com.eclectics.chamapayments.wrappers.esbWrappers.EsbWrapper;
import com.eclectics.chamapayments.wrappers.request.*;
import com.eclectics.chamapayments.wrappers.response.PendingApprovalsWrapper;
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
@RequestMapping("/api/v2/payment/account")
public class AccountingResource {

    private final AccountingService accountingService;

    @GetMapping("/balance")
    public Mono<ResponseEntity<UniversalResponse>> getMemberWalletBalance() {
        return accountingService.userWalletBalance().map(res -> ResponseEntity.ok().body(res));
    }

    @PostMapping("/group-balance")
    public Mono<ResponseEntity<UniversalResponse>> getGroupAccountBalance(@RequestParam Long groupId) {
        return accountingService.groupAccountBalance(groupId)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @PostMapping("/group-account")
    @ApiOperation(value = "get the accounts attached to a group, the optional parameter `accounttypeid` allows filter by accounttype")
    public Mono<ResponseEntity<UniversalResponse>> getGroupAccounts(@RequestBody EsbWrapper esbWrapper) {
        return Mono.fromCallable(() -> accountingService.getAccountbyGroup(esbWrapper.getGroupId()))
                .publishOn(Schedulers.boundedElastic())
                .map(res -> ResponseEntity.ok().body(new UniversalResponse("success", "Group accounts found", res)));
    }


    @GetMapping("/user-groups")
    public Mono<ResponseEntity<?>> getGroupAccountsMemberBelongsTo(@RequestParam String username) {
        return accountingService.getGroupAccountsMemberBelongsTo(username)
                .map(res -> ResponseEntity.ok().body(new UniversalResponse("success", "Amount types", res)));
    }


    @PostMapping("/transactions/{filter}")
    @ApiOperation(value = "Fetch transactions using a filter",
            notes = "The filters applicable include: group, user, userandcontribution and userandgroup. The filter id may be for the group id or contribution id")
    public Mono<ResponseEntity<?>> getTransactions(@PathVariable String filter, @RequestParam(required = false, defaultValue = "0") Long filterId, @RequestParam Integer page, @RequestParam Integer size, @RequestParam String username) {
        switch (filter) {
            case "group":
                return accountingService.getGroupTransactions(filterId, page, size, username)
                        .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
            case "user":
                return accountingService.getGroupTransactionsPerUser(filterId, username, page, size)
                        .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
            case "userandcontribution":
                return accountingService.getUserTransactionsByContribution(username, filterId, page, size)
                        .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
            case "userandgroup":
                return accountingService.getUserTransactionsByGroup(username, filterId, page, size)
                        .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
            case "other":
                return accountingService.getOtherGroupAccountTransactions(filterId, page, size, username)
                        .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
            default:
                return Mono.just(ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                        .body(new UniversalResponse("fail", "Wrong filter provided")));
        }
    }


    @GetMapping("/user-summary")
    public Mono<ResponseEntity<?>> getUserAccountingSummary(@RequestParam String phone, @RequestParam Long contributionId) {
        return accountingService.getUserSummary(phone, contributionId)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    //todo::get kit balance for saving,project,loan,welfare,fine from contributionPayment table these are payment type
    @PostMapping("/kit-balance")
    public Mono<ResponseEntity<?>> getKitBalance(@RequestBody KitBalanceWrapper req) {
        return accountingService.getKitBalance(req)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    //todo::kit transfer from one kit to another
    @PostMapping("/kit-transfer")
    public Mono<ResponseEntity<?>> kitTransfer(@RequestBody @Valid KitTransferWrapper req) {
        return accountingService.kitTransfer(req)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @PostMapping("/approve-reject/kit-transfer")
    public Mono<ResponseEntity<?>> approveKitTransfer(@RequestBody KitTransferApprovalDto request, @RequestParam String username) {
        return accountingService.approveKitTransfer(request, username)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @PostMapping("/kit-transfer/pending-approvals")
    public Mono<ResponseEntity<?>> kitTransferPendingApprovals(@RequestBody PendingApprovalsWrapper request, @RequestParam String user) {
        return accountingService.kitTransferPendingApprovals(request, user)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    //todo:: assign of other transactions
    @PostMapping("/assign-transaction")
    public Mono<ResponseEntity<?>> assignTransaction(@RequestBody AssignOtherTransactionRequest request, @RequestParam String username) {
        return accountingService.assignTransaction(request.getMemberTransactions(), request.getGroupId(), username)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    //todo::list of other transactions pending approvals
    @PostMapping("/other-transactions/pending-approvals")
    Mono<ResponseEntity<UniversalResponse>> listOtherTransactionsPendingApprovals(@RequestBody OtherTransactionsPendingApprovalsWrapper wrapper, @RequestParam String username) {
        return accountingService.listOtherTransactionsPendingApprovals(wrapper, username)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    //todo:: approve assigned other transactions
    @PostMapping("/approve-decline/other-transactions")
    Mono<ResponseEntity<UniversalResponse>> approveDeclineOtherTransaction(@RequestBody OtherTransactionApprovalRequest request, @RequestParam String username) {
        return accountingService.approveDeclineOtherTransaction(request, username)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }


    //TODO::SHARE OUTS PREVIEW
    @PostMapping("/share-outs-preview")
    public Mono<ResponseEntity<UniversalResponse>> shareOutsPreview(@Valid @RequestParam String userName, @RequestParam Long groupId, @RequestParam(required = false, defaultValue = "0") Integer page, @RequestParam Integer size) {
        return accountingService.shareOutsPreview(userName, groupId, page, size)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    //TODO:: EXECUTE SHARE OUTS
    @PostMapping("/share/outs")
    public Mono<ResponseEntity<UniversalResponse>> shareOuts(@Valid @RequestBody ShareOutsWrapper wrapper) {
        return accountingService.shareOuts(wrapper)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));

    }

    //TODO:: SHARE OUTS BALANCE
    @PostMapping("/share-outs-balance")
    public Mono<ResponseEntity<UniversalResponse>> shareOutsAccountBalance(@RequestParam Long groupId) {
        return accountingService.shareOutsAccountBalance(groupId)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    //TODO:: GROUP ACCOUNT STATEMENT
    @PostMapping("/group/account/statement")
    public Mono<ResponseEntity<UniversalResponse>> groupStatement(@RequestBody AccountStatementDto statementDto) {
        return accountingService.groupStatement(statementDto)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    //TODO:: GROUP MEMBER ACCOUNT STATEMENT
    @PostMapping(value = "/member/account/statement")
    public Mono<ResponseEntity<UniversalResponse>> memberAccountStatement(@RequestBody AccountStatementDto statementDto) {
        return accountingService.memberAccountStatement(statementDto)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @PostMapping("/mchama/account-validation")
    public Mono<ResponseEntity<UniversalResponse>> mchamaAccountValidation(@RequestHeader String userName, @RequestHeader String password, @RequestParam String account) {
        if (userName.equals("e011edb66a40") && password.equals("e393b760-e634-4e34-b548-dd06cf75466b")) {
            return accountingService.mchamaAccountValidation(account)
                    .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
        } else {
            return Mono.just(ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body(new UniversalResponse("fail", "Wrong headers provided")));
        }
    }

}
