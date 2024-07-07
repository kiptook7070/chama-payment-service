package com.eclectics.chamapayments.wrappers.response;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PendingApprovalsWrapper {
    private Long groupId;
    private Integer page;
    private Integer size;
    private String action;
}
