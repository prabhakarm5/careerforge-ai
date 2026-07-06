package com.trackai.backend.service;

import com.trackai.backend.entity.PaymentTransaction;
import com.trackai.backend.entity.SubscriptionPlan;
import com.trackai.backend.entity.User;

public interface PdfInvoiceService {

    byte[] generateInvoice(User user, PaymentTransaction txn, SubscriptionPlan plan,
            boolean success, String failureReason);
}