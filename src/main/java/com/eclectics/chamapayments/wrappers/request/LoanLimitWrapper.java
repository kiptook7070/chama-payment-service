package com.eclectics.chamapayments.wrappers.request;

import com.eclectics.chamapayments.model.Guarantors;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Class name: Loan Limit
 * Creater: kiptoo
 * Date:4/22/2024
 */
@Getter
@Setter
public class LoanLimitWrapper {
    private long groupId;
    private double amount;
    private String phoneNumber;
}
