package com.eclectics.chamapayments.resource.mobile;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class EmailConfig {
    @Value("${email.host}")
    private String host;
    @Value("${email.port}")
    private int port;
    @Value("${email.username}")
    private String username;
    @Value("${email.password}")
    private String password;
    @Value("${spring.mail.properties.mail.smtp.auth}")
    private String shouldPerformAuth;
    @Value("${spring.mail.properties.mail.smtp.starttls.enable}")
    private String shouldEnableTLS;

    @Bean
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(username);
        mailSender.setPassword(password);
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.debug", "true");
        props.put("mail.smtp.ehlo", "false");
        props.put("mail.smtp.auth", shouldPerformAuth);
        props.put("mail.smtp.starttls.enable", shouldEnableTLS);
        props.put("mail.transport.protocol", "smtp");
        mailSender.setJavaMailProperties(props);
        return mailSender;
    }
}

