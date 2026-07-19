package com.trackai.backend.service.impl;

import com.trackai.backend.service.MailDeliveryService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailDeliveryServiceImpl implements MailDeliveryService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.mail.from-name}")
    private String fromName;

    @Value("${app.mail.reply-to:${spring.mail.username}}")
    private String replyTo;

    // Authentication, validation, OTP persistence, and message composition finish before this async boundary.
    @Async
    @Override
    public void send(String toEmail, String subject, String htmlBody, byte[] attachment, String attachmentName) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setReplyTo(replyTo, fromName);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setSentDate(new Date());
            helper.setText(toPlainText(htmlBody), htmlBody);
            mimeMessage.setHeader("X-Auto-Response-Suppress", "All");

            if (attachment != null && attachment.length > 0) {
                helper.addAttachment(attachmentName, new ByteArrayResource(attachment));
            }
            mailSender.send(mimeMessage);
        } catch (MessagingException | UnsupportedEncodingException | RuntimeException error) {
            // Delivery failures are logged without rolling back a request that already passed validation and committed.
            log.error("Async email delivery failed for {}: {}", toEmail, error.getMessage(), error);
        }
    }

    private String toPlainText(String html) {
        return html
                .replaceAll("(?is)<(style|script)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|h1|h2|tr|table)>", "\n")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&middot;", "-")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}