package com.eclectics.chamapayments.service.impl;

import com.eclectics.chamapayments.service.ESBService;
import com.eclectics.chamapayments.wrappers.response.UniversalResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.Locale;
import java.util.Map;

import static com.eclectics.chamapayments.util.RequestConstructor.getBalanceInquiryReq;

@Service
@RequiredArgsConstructor
public class ESBServiceImpl implements ESBService {

    @Value("${esb.channel.uri}")
    private String esbURL;
    private final Gson gson;
    private WebClient webClient;
    private final ResourceBundleMessageSource source;

    @PostConstruct
    private void init() {
        webClient = WebClient
                .builder()
                .baseUrl(esbURL)
                .build();
    }

    private String getResponseMessage(String tag) {
        Locale locale = LocaleContextHolder.getLocale();
        return source.getMessage(tag, null, locale);
    }

    @Override
    public Mono<UniversalResponse> balanceInquiry(String account) {
        String transactionId = RandomStringUtils.randomAlphanumeric(12);
        Map<String, String> balanceInquiryReq = getBalanceInquiryReq(account, "0", transactionId, transactionId);


        return webClient
                .post()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(gson.toJson(balanceInquiryReq))
                .retrieve()
                .bodyToMono(String.class)
                .map(jsonString -> {
                    JsonObject jsonObject = new Gson().fromJson(jsonString, JsonObject.class);

                    if (jsonObject.get("responseCode").getAsString().equals("000")) {
                        String mainCBSbalance = jsonObject.get("accountDetails").getAsString();
                        String ledgerBalance = mainCBSbalance.substring(8, 20);
                        ledgerBalance = removeLeadingZeros(ledgerBalance);
                        Double actualAvailableBalance = Double.parseDouble(ledgerBalance);
                        actualAvailableBalance = actualAvailableBalance / 100;
                        String lgBalValue = mainCBSbalance.substring(7, 8);
                        if ("D".equalsIgnoreCase(lgBalValue)) {
                            actualAvailableBalance = Double.parseDouble("-" + actualAvailableBalance);
                        }
                        //end
                        String availableBalance = mainCBSbalance.substring(28, 40);
                        availableBalance = removeLeadingZeros(availableBalance);
                        Double availableBalance1 = Double.parseDouble(availableBalance);
                        availableBalance1 = availableBalance1 / 100;
                        String avBalValue = mainCBSbalance.substring(27, 28);
                        if ("D".equalsIgnoreCase(avBalValue)) {
                            availableBalance1 = Double.parseDouble("-" + availableBalance1);
                        }
                        jsonObject.addProperty("actualBalance", actualAvailableBalance);
                        jsonObject.addProperty("availableBalance", availableBalance1);
                        jsonObject.addProperty("accountNumber", jsonObject.get("accountSerial").getAsString().substring(0, 12));
                        jsonObject.addProperty("accountName", jsonObject.get("accountDetails").getAsString().substring(12, 42).trim());

                        return new UniversalResponse("success", "success ", jsonObject);
                    } else {
                        return new UniversalResponse("fail", getResponseMessage("balanceInquiryFailed"));
                    }

                }).onErrorReturn(new UniversalResponse("fail", getResponseMessage("serviceNotAvailable")));
    }

    private String removeLeadingZeros(String ledgerBalance) {
        return ledgerBalance.replaceFirst("^0+(?!$)", "");
    }
}
