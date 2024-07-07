package com.eclectics.chamapayments.wrappers.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OtherTransactionApprovalRequest {
    private String action;
    private long id;
    private long groupId;
    private boolean approve;
}
