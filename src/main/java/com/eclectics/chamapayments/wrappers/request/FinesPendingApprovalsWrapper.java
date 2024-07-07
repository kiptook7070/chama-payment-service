package com.eclectics.chamapayments.wrappers.request;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinesPendingApprovalsWrapper {
    private String action;
    private long groupId;
    private int page;
    private int size;
}
