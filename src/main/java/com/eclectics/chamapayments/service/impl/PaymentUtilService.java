package com.eclectics.chamapayments.service.impl;

import com.eclectics.chamapayments.service.PaymentUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * @author Alex Maina
 * @created 24/12/2021
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentUtilService implements PaymentUtil {


    @Value("${app-configs.esb-url}")
    public String esbUrl;

}
