package com.eclectics.chamapayments.wrappers.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import java.util.Date;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoanPenaltyWrapper {
    private Long loanPenaltyId;
    private Double penaltyAmount;
    private String paymentStatus;
    private Double paidAmount;
    private Double dueAmount;
    private String transactionId;
    @JsonIgnore
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Africa/Nairobi")
    private Date loanDueDate;
    @JsonIgnore
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Africa/Nairobi")
    private Date lastPaymentDate;
    private String memberName;
    private String memberPhoneNumber;
    private Date transactionDate;
}
