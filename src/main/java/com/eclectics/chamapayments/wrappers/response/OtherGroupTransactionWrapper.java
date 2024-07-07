package com.eclectics.chamapayments.wrappers.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.util.Date;

/**
 * @author kiptoo joshua
 * @createdOn 15/02/2024
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OtherGroupTransactionWrapper implements Comparable<OtherGroupTransactionWrapper> {
    private String name;
    private String accountNumber;
    private double amount;
    private long groupId;
    private long otherTransactionId;
    private String transactionId;
    @JsonFormat
            (shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy hh:mm:ss")
    private Date transactiondate;

    @Override
    public int compareTo(OtherGroupTransactionWrapper log) {
        if (transactiondate == null || log.transactiondate == null)
            return 0;

        return transactiondate.compareTo(log.transactiondate);
    }
}
