package com.eclectics.chamapayments.wrappers.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StatementWrapper {
    private String username;
    private Date startDate;
    private Date endDate;
    private String emailAddress;
    private long groupId;
}
