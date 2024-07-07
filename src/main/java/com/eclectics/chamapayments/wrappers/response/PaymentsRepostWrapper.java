package com.eclectics.chamapayments.wrappers.response;

import lombok.*;

import java.util.Date;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentsRepostWrapper {
    private  long id;
    private long groupId;
    private String transactionId;
    private String paymentStatus;
    private int amount;
    private String paymentType;
    private String sourceAccount;
    private String destinationAccount;
    private String narration;
    private Date transactionDate;
    private String creator;
    private String creatorPhoneNumber;
}
