package com.eclectics.chamapayments.wrappers.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KitTransferApprovalDto {
    private String action;
    private Long groupId;
    private Long requestId;
    private Boolean approve;
}
