package com.eclectics.chamapayments.service;

import com.eclectics.chamapayments.model.EsbRequestLog;

import java.util.Optional;

public interface ESBLoggingService {

    void logESBRequest(String body, String scope);

    Optional<EsbRequestLog> findByTransactionId(String transactionId);

    void updateCallBackReceived(EsbRequestLog esbLog);
}
