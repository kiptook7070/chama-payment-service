package com.eclectics.chamapayments.resource.mobile;

import com.eclectics.chamapayments.service.LoanService;
import com.eclectics.chamapayments.wrappers.esbWrappers.EsbWrapper;
import com.eclectics.chamapayments.wrappers.request.ApplyLoanWrapper;
import com.eclectics.chamapayments.wrappers.request.ApproveLoanWrapper;
import com.eclectics.chamapayments.wrappers.request.LoanInterestWrapper;
import com.eclectics.chamapayments.wrappers.request.LoanLimitWrapper;
import com.eclectics.chamapayments.wrappers.response.LoanproductWrapper;
import com.eclectics.chamapayments.wrappers.response.UniversalResponse;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import javax.validation.Valid;
import java.util.Optional;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/payment/loan")
public class LoanResource {

    private final LoanService loanService;

    @PostMapping("/limit")
    public Mono<ResponseEntity<?>> loanLimit(@RequestBody LoanLimitWrapper wrapper, @RequestParam String username) {
        return loanService.loanLimit(wrapper, username)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @PostMapping("/apply")
    public Mono<ResponseEntity<?>> applyLoan(@RequestBody ApplyLoanWrapper applyLoanWrapper, @RequestParam String username) {
        return loanService.applyLoan(applyLoanWrapper, username)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }


    @PostMapping(path = "/approve")
    public Mono<ResponseEntity<?>> approveLoanApplication(@RequestBody @Valid ApproveLoanWrapper approveLoanWrapper, @RequestParam String username) {
        return loanService.approveLoanApplication(approveLoanWrapper.isApprove(), approveLoanWrapper.getLoanapplicationid(), username)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @PostMapping(path = "/loan-interest")
    public Mono<ResponseEntity<?>> loanInterest(@RequestBody @Valid LoanInterestWrapper wrapper, @RequestParam String username) {
        return loanService.loanInterest(wrapper, username)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @PostMapping(path = "/create-product")
    public Mono<ResponseEntity<?>> createLoanProduct(@RequestBody @Valid LoanproductWrapper loanproductWrapper, @RequestParam String username) {
        return loanService.createLoanProduct(loanproductWrapper, username)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }


    @PostMapping(path = "/product-edit")
    public Mono<ResponseEntity<?>> editLoanProduct(@RequestBody @Valid LoanproductWrapper loanproductWrapper, @RequestParam String username) {
        return loanService.editLoanProduct(loanproductWrapper, username)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }


    @PostMapping(path = "/product-activate")
    public Mono<ResponseEntity<?>> activateLoanProduct(@RequestBody LoanproductWrapper loanproductWrapper, @RequestParam String username) {
        return loanService.activateDeactivateLoanProduct(loanproductWrapper, username, true)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }


    @PostMapping(path = "/product-deactivate")
    public Mono<ResponseEntity<?>> deActivateLoanProduct(@RequestBody LoanproductWrapper loanproductWrapper, @RequestParam String username) {
        return loanService.activateDeactivateLoanProduct(loanproductWrapper, username, false)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }


    @PostMapping(path = "/products")
    public Mono<ResponseEntity<?>> getLoanProductsByGroup(@RequestBody EsbWrapper esbWrapper) {
        return Mono.just(ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(loanService.getLoanProductsbyGroup(esbWrapper.getGroupId())));
    }

    @PostMapping(path = "/products/user")
    public Mono<ResponseEntity<?>> getLoanProducts(@RequestParam String username) {
        return loanService.getUserLoanProducts(username)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }


    @PostMapping(path = "/product/deactivated")
    public Mono<ResponseEntity<?>> getInactiveLoanProduct(@RequestParam long groupId) {
        return loanService.getInactiveGroupLoanProducts(groupId)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @PostMapping(path = "/penalty/{filter}")
    public Mono<ResponseEntity<?>> getLoanPenalties(@PathVariable String filter, @RequestParam Optional<Long> filterId, @RequestParam String username) {
        switch (filter) {
            case "group":
                return loanService.getGroupLoansPenalties(filterId.get())
                        .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
            case "user":
                return loanService.getMemberLoansPenalties(username)
                        .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
            default:
                return Mono.just(ResponseEntity.ok().body(new UniversalResponse("fail", "unsupported filter")));
        }
    }


    @GetMapping("/loan-repayment")
    @ApiOperation(value = "Fetch loan repayments per user and/or loandisbursed id.", notes = "Pass the loan disbursed id if you wish to fetch the repayments for a particular loan disbursed")
    public Mono<ResponseEntity<?>> getLoanPaymentsByUser(@RequestParam Optional<Long> filterId, @RequestParam Integer page, @RequestParam Integer size, @RequestParam Optional<String> username) {
        if (filterId.isPresent()) {
            Long loanDisbursedId = filterId.get();
            return loanService.getLoanPaymentsbyDisbursedloan(loanDisbursedId, page, size)
                    .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
        } else {
            String requestedUsername = username.orElseThrow(() -> new IllegalArgumentException("Username is required"));
            return loanService.getLoanPaymentsbyUser(requestedUsername, page, size)
                    .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
        }
    }


    @GetMapping("/pay/{groupId}")
    public Mono<ResponseEntity<?>> getLoanPaymentsByGroup(@PathVariable Long groupId) {
        return loanService.getLoanProductsbyGroup(groupId)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @PostMapping(path = "/disbursed/{filter}")
    public Mono<ResponseEntity<?>> getDisbursedLoans(@PathVariable String filter, @RequestParam Optional<Long> filterid,
                                                     @RequestParam int page, @RequestParam int size, @RequestParam String username) {
        switch (filter) {
            case "group":
                return loanService.getDisbursedLoansperGroup(filterid.get(), page, size)
                        .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
            case "loanproduct":
                return loanService.getDisbursedLoansperLoanproduct(filterid.get(), page, size)
                        .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
            case "user":
                return loanService.getDisbursedLoansperUser(filterid.get(), username, page, size)
                        .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
            default:
                return Mono.just(ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(new UniversalResponse("fail", "Unsupported filter")));
        }
    }


    @PostMapping("/user-pending-approval")
    public Mono<ResponseEntity<?>> getUserLoansPendingApproval(@RequestParam Integer page, @RequestParam Integer size, @RequestParam String username) {
        return loanService.getLoansPendingApprovalbyUser(username, page, size)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }


    @PostMapping("/group-pending-approval")
    public Mono<ResponseEntity<?>> getGroupLoansPendingApproval(@RequestParam long groupId, @RequestParam Integer page, @RequestParam Integer size, @RequestParam String username) {
        return loanService.getLoansPendingApprovalbyGroup(groupId, page, size)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }


    @GetMapping("/pending-approval")
    @ApiOperation(value = "Loans pending approval by loan product")
    public Mono<ResponseEntity<?>> getLoansPendingApprovalByLoanProduct(@RequestParam Long pid, @RequestParam Integer page, @RequestParam Integer size, @RequestParam String username) {
        return loanService.getLoansPendingApprovalbyLoanProduct(pid, username, page, size)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }


    @GetMapping("/user-payments-pending-approval")
    @ApiOperation(value = "Get user payments pending approval")
    public Mono<ResponseEntity<?>> getLoanPaymentPendingApprovalByUser(@RequestParam Integer page, @RequestParam Integer size, @RequestParam String username) {
        return loanService.getLoanPaymentPendingApprovalByUser(username, page, size)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }


    @GetMapping("/group-payments-pending-approval")
    @ApiOperation(value = "Get group payments pending approval")
    public Mono<ResponseEntity<?>> getLoanPaymentPendingApprovalByGroup(@RequestParam Long groupId, @RequestParam Integer page, @RequestParam Integer size, @RequestParam String username) {
        return loanService.getLoanPaymentPendingApprovalByGroup(groupId, username, page, size)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }


    @GetMapping("/group-loan-repayments")
    @ApiOperation(value = "Get group loan repayments")
    public Mono<ResponseEntity<?>> getLoanPaymentsByGroup(@RequestParam Long groupId, @RequestParam Integer page, @RequestParam Integer size) {
        return loanService.getLoanPaymentsbyGroupid(groupId, page, size)
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

}
