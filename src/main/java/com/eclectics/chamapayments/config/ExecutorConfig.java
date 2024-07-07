package com.eclectics.chamapayments.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class ExecutorConfig {

    private static final int CORE_POOL_SIZE=4;
    private static final int MAX_POOL_SIZE=10;
    private static final int QUEUE_CAPACITY=50;

    @Bean
    public Executor remindersExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("reminders-send-async");
        executor.initialize();

        return executor;
    }

}
