package com.eclectics.chamapayments.resource.ussd;

import com.eclectics.chamapayments.service.AccountingService;
import com.eclectics.chamapayments.wrappers.request.*;
import com.eclectics.chamapayments.wrappers.response.UniversalResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/payment/ussd/fines")
public class UssdFineResource {

    private final AccountingService accountingService;

    //todo:: create a list of fines
    @PostMapping("/create/list")
    Mono<ResponseEntity<?>> createFines(@RequestBody FineDto fineDto, @RequestParam String username) {
        return accountingService.createFines(fineDto.getFineWrapperList(), username)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    //todo:: list of fines pending approvals
    @PostMapping("/list-fines/pending-approvals")
    Mono<ResponseEntity<UniversalResponse>> listFinesPendingApprovals(@RequestBody FinesPendingApprovalsWrapper wrapper, @RequestParam String username) {
        return accountingService.listFinesPendingApprovals(wrapper, username)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @PostMapping("/approve-decline/fine-request")
    Mono<ResponseEntity<UniversalResponse>> approveDeclineFineRequest(@RequestBody FineApprovalRequest request, @RequestParam String username) {
        return accountingService.approveDeclineFineRequest(request, username)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @PostMapping("/view")
    Mono<ResponseEntity<?>> viewFines(@RequestParam String phoneNumber, Long groupId) {
        return accountingService.viewFines(phoneNumber, groupId)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @PostMapping("/view/{id}")
    Mono<ResponseEntity<?>> viewFineById(@PathVariable Long id) {
        return accountingService.viewFineById(id)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    //todo:: update fine
    @PostMapping("/update/{id}")
    Mono<ResponseEntity<?>> updateFine(@PathVariable Long id, @RequestBody FineWrapper fineWrapper) {
        return accountingService.updateFine(id, fineWrapper)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    //todo:: view all group fines
    @PostMapping("/view-group-fines")
    Mono<ResponseEntity<?>> viewGroupFines(@RequestParam Long groupId) {
        return accountingService.viewGroupFines(groupId)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    //todo:: account look up using phone number
    @PostMapping("/account-lookup")
    //todo::for testing purpose
    Mono<ResponseEntity<?>> accountLookup(@RequestBody LookUpWrapper lookUpWrapper) {
        return accountingService.accountLookup(lookUpWrapper.getPhoneNumber())
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

}
