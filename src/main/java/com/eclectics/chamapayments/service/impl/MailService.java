package com.eclectics.chamapayments.service.impl;


import com.eclectics.chamapayments.service.ChamaKycService;
import com.eclectics.chamapayments.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.util.IOUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.mail.Address;
import javax.mail.SendFailedException;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Slf4j
@Service
@Configuration
public class MailService {
    private final ChamaKycService chamaKycService;
    private final NotificationService notificationService;
    private final JavaMailSender javaMailSender;
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    public MailService(ChamaKycService chamaKycService, NotificationService notificationService, JavaMailSender javaMailSender) {
        this.chamaKycService = chamaKycService;
        this.notificationService = notificationService;
        this.javaMailSender = javaMailSender;
    }


    public void sendGroupStatement(ByteArrayInputStream data, String sendFrom, String toEmail, String groupName, long groupId, String memberName, String regNumber) {
        executorService.execute(() -> {
            try {
                ByteArrayResource resource = new ByteArrayResource(IOUtils.toByteArray(data));

                MimeMessage mimeMessage = javaMailSender.createMimeMessage();

                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
                helper.setTo(toEmail);
                helper.setFrom(sendFrom);

                helper.setSubject("GROUP ACCOUNT REPORT");
                helper.setText("Account Statement for".concat(" ") + groupName.concat(" as at ") + new Date());
                helper.addAttachment(regNumber + "-group-statement.pdf", resource);
                javaMailSender.send(mimeMessage);
                log.info("GROUP ACCOUNT REPORT EMAIL SEND SUCCESSFULLY TO {} ", toEmail);

                sendGroupStatementSms(toEmail, memberName, groupId, groupName);
            } catch (Exception ex) {
                log.info("GROUP ACCOUNT REPORT EMAIL SENT ERROR {}", ex.getMessage());
                assert ex instanceof MailSendException;
                detectInvalidAddress((MailSendException) ex);
            }
        });

    }

    private void sendGroupStatementSms(String toEmail, String memberName, long groupId, String groupName) {
        chamaKycService.getGroupOfficials(groupId)
                .subscribe(member -> notificationService.sendGroupStatement(member.getFirstname(), groupName, member.getPhonenumber(), member.getLanguage(), toEmail, memberName));
    }


    private void sendMemberStatementSms(String toEmail, String memberName, String phoneNumber, String group, String language) {
        notificationService.sendMemberStatement(toEmail, memberName, phoneNumber, language, group);
    }

    private void detectInvalidAddress(MailSendException me) {
        Exception[] messageExceptions = me.getMessageExceptions();
        if (messageExceptions.length > 0) {
            Exception messageException = messageExceptions[0];
            if (messageException instanceof SendFailedException) {
                SendFailedException sfe = (SendFailedException) messageException;
                Address[] invalidAddresses = sfe.getInvalidAddresses();
                StringBuilder addressStr = new StringBuilder();
                for (Address address : invalidAddresses) {
                    addressStr.append(address.toString()).append("; ");
                }
                log.error("invalid address(es)：{}", addressStr);
                return;
            }
        }

        log.error("exception while sending mail.", me);
    }

    @Async
    public void sendMemberStatement(ByteArrayInputStream bis, String sendFrom, String toEmail, String memberName, String phoneNumber, String groupName, String language) {
        executorService.execute(() -> {
            try {
                ByteArrayResource resource = new ByteArrayResource(IOUtils.toByteArray(bis));

                MimeMessage mimeMessage = javaMailSender.createMimeMessage();

                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
                helper.setTo(toEmail);
                helper.setFrom(sendFrom);

                helper.setSubject("MCHAMA MEMBER STATEMENT");
                helper.setText("Member Statement for " + memberName + "in group " + groupName + " as at " + new Date());
                helper.addAttachment("generated-member-statement.pdf", resource);
                javaMailSender.send(mimeMessage);
                log.info("MCHAMA MEMBER STATEMENT EMAIL SEND SUCCESSFULLY TO {} ", toEmail);
                sendMemberStatementSms(toEmail, memberName, phoneNumber, groupName, language);
            } catch (Exception ex) {
                log.info("MCHAMA MEMBER STATEMENT EMAIL SENT ERROR {}", ex.getMessage());
                assert ex instanceof MailSendException;
                detectInvalidAddress((MailSendException) ex);
            }
        });
    }


    public void sendChannelTransactionsReport(ByteArrayInputStream data, String transaction, String groupName, String sendFrom, String toEmail) {
        executorService.execute(() -> {
            try {
                String textHeader = String.format("%s %s %s", groupName, transaction, "REPORT");
                String fileName = String.format("%s-%s", groupName.toLowerCase(), transaction + ".PDF");
                ByteArrayResource resource = new ByteArrayResource(IOUtils.toByteArray(data));

                MimeMessage mimeMessage = javaMailSender.createMimeMessage();

                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
                helper.setTo(toEmail);
                helper.setFrom(sendFrom);

                helper.setSubject(transaction);
                helper.setText(textHeader);
                helper.addAttachment(fileName, resource);
                javaMailSender.send(mimeMessage);
                log.info("TRANSACTIONS REPORT EMAIL SEND SUCCESSFULLY TO {} ", toEmail);
            } catch (Exception ex) {
                log.info("TRANSACTIONS REPORT EMAIL ERROR {}", ex.getMessage());
                assert ex instanceof MailSendException;
                detectInvalidAddress((MailSendException) ex);
            }
        });
    }
}
