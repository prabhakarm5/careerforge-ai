package com.trackai.backend.service;

import com.trackai.backend.service.impl.MailServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;

class MailServiceImplTest {

    @Test
    void preparesValidatedMessageBeforeAsyncDeliveryHandoff() {
        MailDeliveryService deliveryService = mock(MailDeliveryService.class);
        MailService service = new MailServiceImpl(deliveryService);

        service.sendForgotPasswordOtp("Candidate", "candidate@example.com", "123456", 10);

        verify(deliveryService).send(
                eq("candidate@example.com"),
                eq("CareerForge AI Password Reset OTP"),
                any(String.class),
                eq((byte[]) null),
                eq((String) null));
    }

    @Test
    void adminEmailContainsOnlySecureRevealButtonNotTheOtp() {
        MailDeliveryService deliveryService = mock(MailDeliveryService.class);
        MailServiceImpl service = new MailServiceImpl(deliveryService);
        ReflectionTestUtils.setField(service, "frontendUrl", "https://app.example.com");
        String revealToken = "abcdefghijklmnopqrstuvwxyzABCDEFGHijklmnoPQ";

        service.sendAdminLoginOtp("Admin", "admin@example.com", revealToken, 5);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(deliveryService).send(eq("admin@example.com"), eq("CareerForge AI Admin Login OTP"),
                html.capture(), eq((byte[]) null), eq((String) null));
        assertThat(html.getValue())
                .contains("https://app.example.com/admin/otp?token=" + revealToken)
                .contains("View secure login code")
                .doesNotContain("otpBlock")
                .doesNotContain("123456");
    }}