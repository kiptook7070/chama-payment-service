package com.eclectics.chamapayments.wrappers.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.util.Date;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtherTransactionsPendingApprovalsResponse {
    private long id;
    private long groupId;
    private double amount;
    private String creator;
    private boolean pending;
    private boolean approved;
    private String phoneNumber;
    private String memberName;
    private String paymentType;
    private long otherTransactionId;
    private String creatorPhoneNumber;
    @JsonFormat
            (shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy hh:mm:ss")
    private Date createdOn;
}
