package com.eclectics.chamapayments.wrappers.response;

import lombok.*;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BranchesWrapper {
    private long id;
    private String branchCode;
    private String branchName;
    private String street;
    private String county;
}
