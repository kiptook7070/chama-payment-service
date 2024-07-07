package com.eclectics.chamapayments.service.impl;

import com.eclectics.chamapayments.service.PublishingService;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import static com.eclectics.chamapayments.service.constants.KafkaChannelsConstants.*;

/**
 * @author Alex Maina
 * @created 07/12/2021
 */
@Service
@RequiredArgsConstructor
public class PublishingServiceImpl implements PublishingService {

    private final StreamBridge streamBridge;


    @Override
    public void sendPostBankEmail(String message, String toEmail, String groupName) {

        JsonObject emailObject = new JsonObject();
        emailObject.addProperty("message", message);
        emailObject.addProperty("toEmail", toEmail);
        emailObject.addProperty("title", groupName);
        emailObject.addProperty("type", "Channel Manager Notification");
        streamBridge.send(SEND_EMAIL_STATEMENT_TOPIC, emailObject.toString());
    }

    @Override
    public void sendPostBankText(String message, String phoneNumber) {
        JsonObject textObject = new JsonObject();
        textObject.addProperty("message", message);
        textObject.addProperty("phoneNumber", phoneNumber);
        streamBridge.send(SEND_POST_BANK_TEXT_SMS_TOPIC, textObject.toString());
    }

    @Override
    public void writeOffLoansAndPenalties(long memberId, long groupId) {
        Mono.fromRunnable(() -> {
            JsonObject memberInfo = new JsonObject();
            memberInfo.addProperty("memberId", memberId);
            memberInfo.addProperty("groupId", groupId);
            streamBridge.send(WRITE_OFF_LOANS_AND_PENALTIES_TOPIC, memberInfo.toString());
        }).publishOn(Schedulers.boundedElastic()).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }
}
