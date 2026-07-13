package com.trackai.backend.service.impl;

import com.trackai.backend.entity.PaymentTransaction;
import com.trackai.backend.service.MailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {

        private static final Logger log = LoggerFactory.getLogger(MailServiceImpl.class);

        private final JavaMailSender mailSender;

        @Value("${spring.mail.username}")
        private String fromEmail;

        @Value("${app.mail.from-name}")
        private String fromName;

        // ── shared brand tokens ─────────────────────────────────────────────
        private static final String BRAND_GRADIENT = "linear-gradient(135deg,#7c3aed,#4f46e5)";
        private static final String SUCCESS_GRADIENT = "linear-gradient(135deg,#16a34a,#059669)";
        private static final String FAILED_GRADIENT = "linear-gradient(135deg,#dc2626,#b91c1c)";
        private static final String BG_DARK = "#07060e";
        private static final String CARD_BG = "#0d0b1a";
        private static final String TEXT_MUTED = "#94a3b8";
        private static final String TEXT_BODY = "#cbd5e1";
        private static final String BORDER = "#221f33";
        private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

        // ── core sender ──────────────────────────────────────────────────────
        private void send(String toEmail, String subject, String htmlBody) {
                send(toEmail, subject, htmlBody, null, null);
        }

        private void send(String toEmail, String subject, String htmlBody, byte[] attachment,
                        String attachmentName) {
                try {
                        MimeMessage mimeMessage = mailSender.createMimeMessage();

                        MimeMessageHelper helper = new MimeMessageHelper(
                                        mimeMessage,
                                        MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                                        "UTF-8");

                        helper.setFrom(fromEmail, fromName);
                        helper.setTo(toEmail);
                        helper.setSubject(subject);
                        helper.setText(htmlBody, true);

                        if (attachment != null) {
                                helper.addAttachment(attachmentName,
                                                new org.springframework.core.io.ByteArrayResource(attachment));
                        }

                        mailSender.send(mimeMessage);

                } catch (MessagingException | java.io.UnsupportedEncodingException e) {
                        log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
                        throw new RuntimeException("Failed to send email", e);
                }
        }

        // ── shared HTML shell ────────────────────────────────────────────────
        // Table-based layout + inline styles only, because Gmail/Outlook strip
        // <style> blocks and modern CSS unpredictably. This renders consistently
        // across clients. accentGradient lets each email type (info/success/fail)
        // tint the badge + top accent bar without duplicating the whole shell.
        private String wrapShell(String badgeIcon, String headline, String bodyHtml, String footerNote,
                        String accentGradient) {
                return "<!DOCTYPE html>"
                                + "<html><body style=\"margin:0;padding:0;background-color:" + BG_DARK
                                + ";font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;\">"
                                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:"
                                + BG_DARK + ";padding:40px 16px;\">"
                                + "<tr><td align=\"center\">"

                                // ── logo row ──
                                + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-bottom:28px;\">"
                                + "<tr>"
                                + "<td style=\"width:40px;height:40px;border-radius:12px;background:" + BRAND_GRADIENT
                                + ";text-align:center;vertical-align:middle;\">"
                                + "<span style=\"color:#ffffff;font-size:13px;font-weight:800;letter-spacing:0.5px;\">AI</span>"
                                + "</td>"
                                + "<td style=\"padding-left:10px;color:#ffffff;font-size:18px;font-weight:800;letter-spacing:-0.3px;\">TrackAI</td>"
                                + "</tr></table>"

                                // ── card ──
                                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:500px;background-color:"
                                + CARD_BG + ";border:1px solid " + BORDER
                                + ";border-radius:20px;overflow:hidden;box-shadow:0 20px 60px rgba(0,0,0,0.4);\">"

                                // ── top accent bar ──
                                + "<tr><td style=\"height:4px;background:" + accentGradient
                                + ";font-size:0;line-height:0;\">&nbsp;</td></tr>"

                                + "<tr><td style=\"padding:40px 36px 32px;\">"

                                // ── icon badge ──
                                + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-bottom:24px;\">"
                                + "<tr><td style=\"width:60px;height:60px;border-radius:50%;background:"
                                + accentGradient
                                + ";text-align:center;vertical-align:middle;font-size:26px;box-shadow:0 8px 24px rgba(0,0,0,0.35);\">"
                                + "<span style=\"line-height:60px;\">" + badgeIcon + "</span>"
                                + "</td></tr></table>"

                                // ── headline ──
                                + "<h1 style=\"margin:0 0 10px 0;color:#ffffff;font-size:23px;font-weight:800;letter-spacing:-0.4px;line-height:1.3;\">"
                                + headline + "</h1>"

                                // ── body content (per-email) ──
                                + bodyHtml

                                + "</td></tr>"

                                // ── card footer strip ──
                                + "<tr><td style=\"padding:20px 36px;background-color:rgba(255,255,255,0.025);border-top:1px solid "
                                + BORDER + ";\">"
                                + "<p style=\"margin:0;color:" + TEXT_MUTED + ";font-size:12px;line-height:1.7;\">"
                                + footerNote + "</p>"
                                + "</td></tr>"

                                + "</table>"

                                // ── outer footer ──
                                + "<p style=\"margin:28px 0 0 0;color:#3f3a52;font-size:11px;letter-spacing:0.2px;\">TrackAI &middot; AI-powered workspace</p>"

                                + "</td></tr></table>"
                                + "</body></html>";
        }

        private String paragraph(String text) {
                return "<p style=\"margin:0 0 18px 0;color:" + TEXT_BODY + ";font-size:14.5px;line-height:1.7;\">"
                                + text + "</p>";
        }

        private String otpBlock(String otp) {
                return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:4px 0 20px 0;\">"
                                + "<tr><td style=\"background-color:rgba(124,58,237,0.1);border:1px solid rgba(124,58,237,0.35);border-radius:14px;padding:20px;text-align:center;\">"
                                + "<span style=\"display:inline-block;color:#ffffff;font-size:32px;font-weight:800;letter-spacing:10px;font-family:Consolas,monospace;\">"
                                + otp + "</span>"
                                + "</td></tr></table>";
        }

        private String linkButton(String link, String label, String gradient) {
                return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:6px 0 22px 0;\">"
                                + "<tr><td style=\"border-radius:12px;background:" + gradient
                                + ";box-shadow:0 8px 20px rgba(0,0,0,0.3);\">"
                                + "<a href=\"" + link
                                + "\" target=\"_blank\" style=\"display:inline-block;padding:14px 30px;color:#ffffff;font-size:14px;font-weight:700;text-decoration:none;\">"
                                + label + "</a>"
                                + "</td></tr></table>"
                                + "<p style=\"margin:0 0 18px 0;color:#565272;font-size:11px;word-break:break-all;line-height:1.6;\">Or paste this link in your browser:<br/>"
                                + link + "</p>";
        }

        private String expiryNote(long minutes) {
                return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-bottom:4px;\">"
                                + "<tr><td style=\"background-color:rgba(245,158,11,0.1);border:1px solid rgba(245,158,11,0.3);border-radius:10px;padding:11px 15px;\">"
                                + "<span style=\"color:#fbbf24;font-size:12.5px;font-weight:600;\">&#9201; Expires in "
                                + minutes + " minutes</span>"
                                + "</td></tr></table>";
        }

        // ── reusable "receipt" style key-value row block, used in both
        // success and failed payment emails so amounts/order IDs are easy
        // to scan instead of buried in a paragraph ──
        private String receiptRow(String label, String value, boolean strong) {
                String valueStyle = strong
                                ? "color:#ffffff;font-size:14.5px;font-weight:700;"
                                : "color:" + TEXT_BODY + ";font-size:13.5px;font-weight:500;";
                return "<tr>"
                                + "<td style=\"padding:9px 0;color:" + TEXT_MUTED
                                + ";font-size:12.5px;border-bottom:1px solid " + BORDER + ";\">" + label + "</td>"
                                + "<td align=\"right\" style=\"padding:9px 0;" + valueStyle + "border-bottom:1px solid "
                                + BORDER + ";\">" + value + "</td>"
                                + "</tr>";
        }

        private String receiptCard(String... rowsHtml) {
                StringBuilder rows = new StringBuilder();
                for (String r : rowsHtml)
                        rows.append(r);
                return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:rgba(255,255,255,0.02);border:1px solid "
                                + BORDER + ";border-radius:14px;padding:4px 18px;margin-bottom:22px;\">"
                                + "<tr><td>"
                                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">"
                                + rows
                                + "</table>"
                                + "</td></tr></table>";
        }

        // ── VERIFICATION EMAIL ──────────────────────────────────────────────
        @Override
        public void sendVerificationEmail(String userName, String toEmail, String verificationLink,
                        long expiryMinutes) {

                String body = paragraph("Hello <b style=\"color:#e2e8f0;\">" + userName + "</b>,")
                                + paragraph("Welcome to TrackAI! Please verify your email address to activate your account and start building.")
                                + linkButton(verificationLink, "Verify Email Address", BRAND_GRADIENT)
                                + expiryNote(expiryMinutes);

                String html = wrapShell(
                                "&#9993;",
                                "Verify your email",
                                body,
                                "If you did not create this account, you can safely ignore this email — no action will be taken.",
                                BRAND_GRADIENT);

                send(toEmail, "Verify Your TrackAI Account", html);
        }

        // ── RESEND VERIFICATION EMAIL ────────────────────────────────────────
        @Override
        public void sendResendVerificationEmail(String userName, String toEmail, String verificationLink,
                        long expiryMinutes) {

                String body = paragraph("Hello <b style=\"color:#e2e8f0;\">" + userName + "</b>,")
                                + paragraph("A new verification link has been generated for your TrackAI account.")
                                + linkButton(verificationLink, "Verify Email Address", BRAND_GRADIENT)
                                + expiryNote(expiryMinutes);

                String html = wrapShell(
                                "&#128260;",
                                "New verification link",
                                body,
                                "If you did not request this, please secure your account by changing your password.",
                                BRAND_GRADIENT);

                send(toEmail, "New Verification Link - TrackAI", html);
        }

        // ── FORGOT PASSWORD OTP ──────────────────────────────────────────────
        @Override
        public void sendForgotPasswordOtp(String userName, String toEmail, String otp, long expiryMinutes) {

                String body = paragraph("Hello <b style=\"color:#e2e8f0;\">" + userName + "</b>,")
                                + paragraph("We received a request to reset your TrackAI account password. Use the code below to continue:")
                                + otpBlock(otp)
                                + expiryNote(expiryMinutes);

                String html = wrapShell(
                                "&#128274;",
                                "Password reset code",
                                body,
                                "If you did not request a password reset, please ignore this email — your password will remain unchanged. Never share this code with anyone.",
                                BRAND_GRADIENT);

                send(toEmail, "TrackAI Password Reset OTP", html);
        }

        // ── ADMIN LOGIN OTP ──────────────────────────────────────────────────
        @Override
        @Async
        public void sendAdminLoginOtp(String userName, String toEmail, String otp, long expiryMinutes) {

                String body = paragraph("Hello <b style=\"color:#e2e8f0;\">" + userName + "</b>,")
                                + paragraph("An admin login was requested for your TrackAI account. Use the code below to continue:")
                                + otpBlock(otp)
                                + expiryNote(expiryMinutes);

                String html = wrapShell(
                                "&#128737;",
                                "Admin login code",
                                body,
                                "If you did not request this login, please secure your account immediately and contact support.",
                                BRAND_GRADIENT);

                send(toEmail, "TrackAI Admin Login OTP", html);
        }

        @Async // ✅ tumhare AsyncConfig ka default executor (mail-async-*) use hoga
        @Override
        public void sendAdminLoginOtpAsync(String name, String email, String otp, long expiryMinutes) {
                try {
                        sendAdminLoginOtp(name, email, otp, expiryMinutes); // existing synchronous method,
                                                                            // jaise-tha-waisa
                } catch (Exception e) {
                        log.error("Failed to send admin OTP email to {}", email, e);
                }
        }

        // ── PAYMENT SUCCESS ──────────────────────────────────────────────
        // REDESIGNED: was a single flat paragraph with inline bold amount —
        // now uses a proper "receipt card" (order id / payment id / amount /
        // date as aligned rows) so it reads like an actual invoice email
        // instead of a plain notice.
        @Override
        @Async
        public void sendPaymentSuccessEmail(String userName, String toEmail, PaymentTransaction txn,
                        byte[] invoicePdf) {

                String amountFormatted = txn.getCurrency() + " " + String.format("%,d", txn.getAmount());
                String dateFormatted = txn.getUpdatedAt() != null
                                ? txn.getUpdatedAt().format(DATE_FMT)
                                : txn.getCreatedAt().format(DATE_FMT);

                String body = paragraph("Hello <b style=\"color:#e2e8f0;\">" + userName + "</b>,")
                                + paragraph("Your payment was successful and your wallet has been credited. Here's your receipt:")
                                + receiptCard(
                                                receiptRow("Amount paid", amountFormatted, true),
                                                receiptRow("Order ID", txn.getOrderId(), false),
                                                receiptRow("Payment ID",
                                                                txn.getPaymentId() != null ? txn.getPaymentId() : "—",
                                                                false),
                                                receiptRow("Date", dateFormatted, false))
                                + paragraph("The detailed invoice is attached as a PDF to this email for your records.");

                String html = wrapShell("&#9989;", "Payment successful", body,
                                "Keep this invoice for your records. Contact support if you have any questions about this transaction.",
                                SUCCESS_GRADIENT);

                send(toEmail, "Payment Successful - TrackAI", html, invoicePdf, "invoice-" + txn.getOrderId() + ".pdf");
        }

        // ── PAYMENT FAILED ───────────────────────────────────────────────
        // REDESIGNED + refundSlaDays added: previously said "will be
        // auto-refunded within 5-7 business days" as a hardcoded footer
        // note regardless of whether money was actually deducted, with no
        // clear visual separation of the failure reason. Now shows a
        // receipt card + a dedicated refund-timeline callout only when
        // relevant.
        @Override
        @Async
        public void sendPaymentFailedEmail(String userName, String toEmail, PaymentTransaction txn, String reason,
                        byte[] invoicePdf, int refundSlaDays) {

                String amountFormatted = txn.getCurrency() + " " + String.format("%,d", txn.getAmount());
                String dateFormatted = txn.getUpdatedAt() != null
                                ? txn.getUpdatedAt().format(DATE_FMT)
                                : txn.getCreatedAt().format(DATE_FMT);

                String refundCallout = "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-bottom:22px;\">"
                                + "<tr><td style=\"background-color:rgba(59,130,246,0.08);border:1px solid rgba(59,130,246,0.25);border-radius:12px;padding:14px 16px;\">"
                                + "<p style=\"margin:0;color:#93c5fd;font-size:13px;font-weight:600;line-height:1.6;\">"
                                + "&#128179; If any amount was deducted from your account, it will be automatically refunded to your original payment method within <b>"
                                + refundSlaDays + " business days</b>."
                                + "</p>"
                                + "</td></tr></table>";

                String body = paragraph("Hello <b style=\"color:#e2e8f0;\">" + userName + "</b>,")
                                + paragraph("Unfortunately, your payment could not be completed. Here are the details:")
                                + receiptCard(
                                                receiptRow("Amount", amountFormatted, true),
                                                receiptRow("Order ID", txn.getOrderId(), false),
                                                receiptRow("Date", dateFormatted, false),
                                                receiptRow("Reason",
                                                                "<span style=\"color:#f87171;\">" + reason + "</span>",
                                                                false))
                                + refundCallout
                                + paragraph("A copy of this transaction summary is attached as a PDF for your records. You can retry the payment anytime from your wallet page.");

                String html = wrapShell("&#10060;", "Payment failed", body,
                                "If the amount isn't refunded within the stated window, please contact support with your Order ID.",
                                FAILED_GRADIENT);

                send(toEmail, "Payment Failed - TrackAI", html, invoicePdf,
                                "payment-failed-" + txn.getOrderId() + ".pdf");
        }

        @Override
        @Async
        public void sendAdminMessage(String userName, String toEmail, String subject, String message) {
                String safeName = escapeHtml(userName == null ? "there" : userName);
                String safeMessage = escapeHtml(message).replace("\n", "<br>");
                String body = "<p style=\"margin:0 0 16px;color:" + TEXT_BODY + ";\">Hi " + safeName + ",</p>"
                                + "<p style=\"margin:0;color:" + TEXT_BODY + ";line-height:1.7;\">" + safeMessage
                                + "</p>";
                send(toEmail, subject.trim(), wrapShell("i", escapeHtml(subject.trim()), body,
                                "This message was sent by the CareerForge AI support team.", BRAND_GRADIENT));
        }

        private String escapeHtml(String value) {
                if (value == null)
                        return "";
                return value.replace("&", "&amp;")
                                .replace("<", "&lt;")
                                .replace(">", "&gt;")
                                .replace("\"", "&quot;")
                                .replace("'", "&#39;");
        }

}