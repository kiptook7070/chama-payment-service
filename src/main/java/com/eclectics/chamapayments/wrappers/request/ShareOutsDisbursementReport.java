package com.eclectics.chamapayments.wrappers.request;

import lombok.*;

import java.util.Date;


@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareOutsDisbursementReport {
    private long id;
    private long groupId;
    private String status;
    private double amount;
    private String message;
    private String groupName;
    private String phoneNumber;
    private String coreAccount;
    private boolean disbursed;
    private boolean active;
    private Date createdOn;
    private Date modifiedOn;
}
