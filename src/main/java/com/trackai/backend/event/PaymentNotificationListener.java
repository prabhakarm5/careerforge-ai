package com.trackai.backend.event;

import com.trackai.backend.entity.PaymentTransaction;
import com.trackai.backend.entity.SubscriptionPlan;
import com.trackai.backend.entity.User;
import com.trackai.backend.enums.PaymentStatus;
import com.trackai.backend.repository.PaymentTransactionRepository;
import com.trackai.backend.repository.SubscriptionPlanRepository;
import com.trackai.backend.repository.UserRepository;
import com.trackai.backend.service.MailService;
import com.trackai.backend.service.PdfInvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentNotificationListener {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final UserRepository userRepository;
    private final PdfInvoiceService pdfInvoiceService;
    private final MailService mailService;

    @Value("$" + "{app.payment.refund-sla-days:7}")
    private int refundSlaDays;

    // PDF and SMTP start only after the wallet transaction commits.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyUser(PaymentStatusChangedEvent event) {
        try {
            PaymentTransaction transaction = paymentTransactionRepository.findById(event.transactionId())
                    .orElseThrow();
            User user = userRepository.findById(transaction.getUserId()).orElseThrow();
            SubscriptionPlan plan = subscriptionPlanRepository.findById(transaction.getSubscriptionPlanId())
                    .orElseThrow();

            boolean success = event.status() == PaymentStatus.SUCCESS;
            byte[] invoice = pdfInvoiceService.generateInvoice(
                    user, transaction, plan, success, event.reason());

            if (success) {
                mailService.sendPaymentSuccessEmail(user.getName(), user.getEmail(), transaction, invoice);
            } else {
                mailService.sendPaymentFailedEmail(
                        user.getName(), user.getEmail(), transaction,
                        event.reason(), invoice, refundSlaDays);
            }
        } catch (Exception exception) {
            log.error("Payment notification failed for transaction {}: {}",
                    event.transactionId(), exception.getMessage());
        }
    }
}