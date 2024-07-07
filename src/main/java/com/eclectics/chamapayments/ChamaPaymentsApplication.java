package com.eclectics.chamapayments;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;

import java.text.NumberFormat;
import java.util.Date;


@EnableAsync
@EnableCaching
@EnableScheduling
@SpringBootApplication
@LoadBalancerClient(name = "PAYMENTS-SERVICE")
@ConfigurationProperties(prefix = "spring.mail")
public class ChamaPaymentsApplication implements CommandLineRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChamaPaymentsApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(ChamaPaymentsApplication.class, args);
    }

    @Bean
    public Gson gson() {
        return new Gson();
    }

    @Bean
    public NumberFormat numberFormat() {
        NumberFormat numberFormat = NumberFormat.getInstance();
        numberFormat.setGroupingUsed(true);
        return numberFormat;
    }

    @Bean
    @LoadBalanced
    public WebClient loadBalancedWebClientBuilder() {
        return WebClient.builder().build();
    }

    @Override
    public void run(String... args) {
        LOGGER.info("PAYMENT SERVICE STARTED SUCCESSFULLY AT : {} ", new Date(System.currentTimeMillis()));
    }
}
