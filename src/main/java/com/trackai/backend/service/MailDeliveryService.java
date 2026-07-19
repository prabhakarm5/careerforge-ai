package com.trackai.backend.service;

public interface MailDeliveryService {

    void send(String toEmail, String subject, String htmlBody, byte[] attachment, String attachmentName);
}