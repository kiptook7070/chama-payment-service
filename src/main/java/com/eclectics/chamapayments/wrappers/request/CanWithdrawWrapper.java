package com.eclectics.chamapayments.wrappers.request;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CanWithdrawWrapper {
    private boolean approve;
    private long groupId;
}
