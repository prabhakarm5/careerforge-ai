package com.trackai.backend.service;

import com.trackai.backend.service.impl.MailServiceImpl;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
}