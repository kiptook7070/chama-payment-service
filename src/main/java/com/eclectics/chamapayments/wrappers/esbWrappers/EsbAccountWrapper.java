package com.eclectics.chamapayments.wrappers.esbWrappers;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EsbAccountWrapper {
    private long groupId;
    private String phoneNumber;
    private String userAccount;
}
