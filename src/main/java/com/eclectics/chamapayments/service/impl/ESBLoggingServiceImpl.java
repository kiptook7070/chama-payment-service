package com.eclectics.chamapayments.service.impl;

import com.eclectics.chamapayments.model.EsbRequestLog;
import com.eclectics.chamapayments.repository.EsbRequestLogRepository;
import com.eclectics.chamapayments.service.ESBLoggingService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ESBLoggingServiceImpl implements ESBLoggingService {
    Gson gson = new Gson();
    private final EsbRequestLogRepository esbRequestLogRepository;

    @Override
    public void logESBRequest(String body, String scope) {
        Mono.fromRunnable(() -> {
            JsonObject request = gson.fromJson(body, JsonObject.class);
            log.info("ESB BODY REQUEST TO BE SAVED ON LOGGING {} ", request);
            EsbRequestLog requestLog = new EsbRequestLog();
            requestLog.setField0(request.get("field0").getAsString());
            requestLog.setField2(request.get("field2").getAsString());
            requestLog.setField3(request.get("field3").getAsString());
            requestLog.setField4(request.get("field4").getAsString());
            requestLog.setField6(request.get("field100").getAsString());
            requestLog.setField24(request.get("field24").getAsString());
            requestLog.setField32(request.get("field32").getAsString());
            requestLog.setField37(request.get("field37").getAsString());
            requestLog.setField43(request.get("field43").getAsString());
            if (request.get("field65").getAsString().length() < 13) {
                requestLog.setUSSDMNO(request.get("field65").getAsString());

            } else {
                requestLog.setUSSDMNO(" ");
            }

            requestLog.setField46(request.get("field46").getAsString());
            requestLog.setField49(request.get("field49").getAsString());
            requestLog.setField60(request.get("field60").getAsString());
            requestLog.setField65(request.get("field65").getAsString());
            requestLog.setField68(request.get("field68").getAsString());
            requestLog.setField100(request.get("field100").getAsString());
            requestLog.setField102(request.get("field102").getAsString());
            requestLog.setField103(request.get("field65").getAsString());
            requestLog.setField123(request.get("field32").getAsString());
            requestLog.setChannel(request.get("channel").getAsString());
            requestLog.setAccountType(request.get("accountType").getAsString());
            requestLog.setChargeAmount(request.get("chargeAmount").getAsString());
            requestLog.setScope(scope);
            esbRequestLogRepository.save(requestLog);
        }).subscribeOn(Schedulers.boundedElastic()).publishOn(Schedulers.boundedElastic()).subscribe();
    }

    @Override
    public Optional<EsbRequestLog> findByTransactionId(String transactionId) {
        return esbRequestLogRepository.findFirstByField37(transactionId);
    }

    @Override
    public void updateCallBackReceived(EsbRequestLog esbLog) {
        //TODO::UPDATE CALL BACK
        esbLog.setCallbackReceived(true);
        esbRequestLogRepository.save(esbLog);
    }
}
