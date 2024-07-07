package com.eclectics.chamapayments.resource.ussd;

import com.eclectics.chamapayments.service.AccountingService;
import com.eclectics.chamapayments.wrappers.request.*;
import com.eclectics.chamapayments.wrappers.response.PendingApprovalsWrapper;
import com.eclectics.chamapayments.wrappers.response.UniversalResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import javax.validation.Valid;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/payment/ussd/account")
public class UssdAccountingResource {
    private final AccountingService accountingService;

    //todo:: get group cbs balance
    @PostMapping("/group-balance")
    public Mono<ResponseEntity<UniversalResponse>> getGroupAccountBalance(@RequestParam Long groupId) {
        return accountingService.groupAccountBalance(groupId)
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
}
