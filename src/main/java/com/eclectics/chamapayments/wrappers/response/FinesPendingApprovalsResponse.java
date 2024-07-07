package com.eclectics.chamapayments.wrappers.response;

import lombok.*;

import java.util.Date;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinesPendingApprovalsResponse {
    private long id;
    private long groupId;
    private String fineName;
    private Double fineAmount;
    private long memberId;
    private String finedMember;
    private String finedMemberPhoneNumber;
    private String creator;
    private String creatorPhoneNumber;
    private Date createdOn;
}
