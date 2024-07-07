package com.eclectics.chamapayments.resource.ussd;

import com.eclectics.chamapayments.config.CustomAuthenticationUtil;
import com.eclectics.chamapayments.service.LoanService;
import com.eclectics.chamapayments.service.enums.CanTransact;
import com.eclectics.chamapayments.wrappers.request.*;
import com.eclectics.chamapayments.wrappers.response.LoanproductWrapper;
import com.eclectics.chamapayments.wrappers.response.UniversalResponse;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import javax.validation.Valid;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/payment/ussd/loan")
public class UssdLoanResource {

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

    @PostMapping(path = "/products")
    public Mono<ResponseEntity<?>> getLoanProducts(@RequestBody LoanProductRequest request, boolean isActive) {
        return Mono.just(ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(loanService.getActiveLoanProductsbyGroup(request.getGroupId(), isActive)));
    }

    @PostMapping(path = "/product/deactivated")
    public Mono<ResponseEntity<?>> getInactiveLoanProduct(@RequestBody LoanProductRequest request) {
        return loanService.getInactiveGroupLoanProducts(request.getId())
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @PostMapping(path = "/penalty/{filter}")
    public Mono<ResponseEntity<?>> getLoanPenalties(@RequestBody LoanPenaltiesRequest request) {
        switch (request.getFilter()) {
            case "group":
                return loanService.getGroupLoansPenalties(request.getFilterId().get())
                        .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
            case "user":
                return CustomAuthenticationUtil.getUsername()
                        .flatMap(loanService::getMemberLoansPenalties)
                        .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
            default:
                return Mono.just(ResponseEntity.ok().body(new UniversalResponse("Failed", "unsupported filter")));
        }
    }

    @PostMapping("/user-pending-approval")
    @ApiOperation(value = "Fetch user loans pending approval", notes = "Just pass the page and size.")
    public Mono<ResponseEntity<?>> getUserLoansPendingApproval(@RequestBody PageDataRequest pageDataRequest) {
        return CustomAuthenticationUtil.getUsername()
                .flatMap(username -> loanService.getLoansPendingApprovalbyUser(username, pageDataRequest.getPage(), pageDataRequest.getSize()))
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @PostMapping("/group-pending-approval")
    @ApiOperation(value = "Fetch user loans pending approval", notes = "Pass the group id, page and size.")
    public Mono<ResponseEntity<?>> getGroupLoansPendingApproval(@RequestBody PageDataRequest pageDataRequest) {
        return CustomAuthenticationUtil.getUsername()
                .flatMap(username -> loanService.getLoansPendingApprovalbyGroup(pageDataRequest.getId(), pageDataRequest.getPage(), pageDataRequest.getSize()))
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }


    @PostMapping("/pending-approval")
    @ApiOperation(value = "Fetch user loans pending approval", notes = "Pass the loan product id, page and size.")
    public Mono<ResponseEntity<?>> getLoansPendingApprovalByLoanProduct(@RequestBody PageDataRequest pageDataRequest) {
        return CustomAuthenticationUtil.getUsername()
                .flatMap(username -> loanService.getLoansPendingApprovalbyLoanProduct(pageDataRequest.getId(), username, pageDataRequest.getPage(), pageDataRequest.getSize()))
                .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
    }

    @PostMapping(path = "/disbursed")
    @ApiOperation(value = "Fetch disbursed loans for group, user or loan product.", notes = "To fetch user disbursed loans just pass the page and size.")
    public Mono<ResponseEntity<?>> getDisbursedLoans(@RequestBody LoanDisbursedRequest request) {
        switch (request.getFilter()) {
            case "group":
                return loanService.getDisbursedLoansperGroup(request.getFilterId(), request.getPage(), request.getSize())
                        .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
            case "loanproduct":
                return loanService.getDisbursedLoansperLoanproduct(request.getFilterId(), request.getPage(), request.getSize())
                        .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
            default:
                return CustomAuthenticationUtil.getUsername()
                        .flatMap(username -> loanService.getDisbursedLoansperUser(request.getFilterId(), username, request.getPage(), request.getSize()))
                        .map(res -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(res));
        }
    }
}
