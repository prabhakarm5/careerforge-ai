package com.trackai.backend.service.impl;

import com.trackai.backend.service.MailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

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
        private static final String BG_DARK = "#07060e";
        private static final String CARD_BG = "#0d0b1a";
        private static final String TEXT_MUTED = "#94a3b8";
        private static final String BORDER = "#221f33";

        // ── core sender ──────────────────────────────────────────────────────
        private void send(String toEmail, String subject, String htmlBody) {
                try {
                        MimeMessage mimeMessage = mailSender.createMimeMessage();

                        // multipart = true lets us also attach a plain-text fallback
                        MimeMessageHelper helper = new MimeMessageHelper(
                                        mimeMessage,
                                        MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                                        "UTF-8");

                        helper.setFrom(fromEmail, fromName);
                        helper.setTo(toEmail);
                        helper.setSubject(subject);
                        helper.setText(htmlBody, true); // true = isHtml

                        mailSender.send(mimeMessage);

                } catch (MessagingException | java.io.UnsupportedEncodingException e) {
                        log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
                        throw new RuntimeException("Failed to send email", e);
                }
        }

        // ── shared HTML shell ────────────────────────────────────────────────
        // Table-based layout + inline styles only, because Gmail/Outlook strip
        // <style> blocks and modern CSS unpredictably. This renders consistently
        // across clients.
        private String wrapShell(String badgeIcon, String headline, String bodyHtml, String footerNote) {
                return "<!DOCTYPE html>"
                                + "<html><body style=\"margin:0;padding:0;background-color:" + BG_DARK
                                + ";font-family:Segoe UI,Roboto,Helvetica,Arial,sans-serif;\">"
                                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:"
                                + BG_DARK + ";padding:40px 16px;\">"
                                + "<tr><td align=\"center\">"

                                // ── logo row ──
                                + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-bottom:28px;\">"
                                + "<tr><td style=\"width:40px;height:40px;border-radius:12px;background:"
                                + BRAND_GRADIENT + ";text-align:center;vertical-align:middle;\">"
                                + "<span style=\"color:#ffffff;font-size:13px;font-weight:800;letter-spacing:0.5px;\">AI</span>"
                                + "</td>"
                                + "<td style=\"padding-left:10px;color:#ffffff;font-size:18px;font-weight:800;letter-spacing:-0.3px;\">TrackAI</td>"
                                + "</tr></table>"

                                // ── card ──
                                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:480px;background-color:"
                                + CARD_BG + ";border:1px solid " + BORDER + ";border-radius:20px;overflow:hidden;\">"
                                + "<tr><td style=\"padding:36px 32px;\">"

                                // ── icon badge ──
                                + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-bottom:22px;\">"
                                + "<tr><td style=\"width:56px;height:56px;border-radius:50%;background-color:rgba(124,58,237,0.15);border:1px solid rgba(124,58,237,0.3);text-align:center;vertical-align:middle;font-size:24px;\">"
                                + badgeIcon
                                + "</td></tr></table>"

                                // ── headline ──
                                + "<h1 style=\"margin:0 0 8px 0;color:#ffffff;font-size:22px;font-weight:800;letter-spacing:-0.3px;\">"
                                + headline + "</h1>"

                                // ── body content (per-email) ──
                                + bodyHtml

                                + "</td></tr>"

                                // ── card footer strip ──
                                + "<tr><td style=\"padding:18px 32px;background-color:rgba(255,255,255,0.02);border-top:1px solid "
                                + BORDER + ";\">"
                                + "<p style=\"margin:0;color:" + TEXT_MUTED + ";font-size:12px;line-height:1.6;\">"
                                + footerNote + "</p>"
                                + "</td></tr>"

                                + "</table>"

                                // ── outer footer ──
                                + "<p style=\"margin:24px 0 0 0;color:#475569;font-size:11px;\">TrackAI &middot; AI-powered workspace</p>"

                                + "</td></tr></table>"
                                + "</body></html>";
        }

        private String paragraph(String text) {
                return "<p style=\"margin:0 0 18px 0;color:" + TEXT_MUTED + ";font-size:14px;line-height:1.65;\">"
                                + text + "</p>";
        }

        private String otpBlock(String otp) {
                return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:4px 0 20px 0;\">"
                                + "<tr><td style=\"background-color:rgba(124,58,237,0.1);border:1px solid rgba(124,58,237,0.35);border-radius:14px;padding:18px;text-align:center;\">"
                                + "<span style=\"display:inline-block;color:#ffffff;font-size:32px;font-weight:800;letter-spacing:10px;font-family:Consolas,monospace;\">"
                                + otp + "</span>"
                                + "</td></tr></table>";
        }

        private String linkButton(String link, String label) {
                return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:6px 0 22px 0;\">"
                                + "<tr><td style=\"border-radius:12px;background:" + BRAND_GRADIENT + ";\">"
                                + "<a href=\"" + link
                                + "\" target=\"_blank\" style=\"display:inline-block;padding:13px 28px;color:#ffffff;font-size:14px;font-weight:700;text-decoration:none;\">"
                                + label + "</a>"
                                + "</td></tr></table>"
                                + "<p style=\"margin:0 0 18px 0;color:#475569;font-size:11px;word-break:break-all;\">Or paste this link in your browser:<br/>"
                                + link + "</p>";
        }

        private String expiryNote(long minutes) {
                return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-bottom:4px;\">"
                                + "<tr><td style=\"background-color:rgba(245,158,11,0.1);border:1px solid rgba(245,158,11,0.3);border-radius:10px;padding:10px 14px;\">"
                                + "<span style=\"color:#fbbf24;font-size:12px;font-weight:600;\">&#9201; Expires in "
                                + minutes + " minutes</span>"
                                + "</td></tr></table>";
        }

        // ── VERIFICATION EMAIL ──────────────────────────────────────────────
        @Override
        public void sendVerificationEmail(String userName, String toEmail, String verificationLink,
                        long expiryMinutes) {

                String body = paragraph("Hello <b style=\"color:#e2e8f0;\">" + userName + "</b>,")
                                + paragraph("Welcome to TrackAI! Please verify your email address to activate your account and start building.")
                                + linkButton(verificationLink, "Verify Email Address")
                                + expiryNote(expiryMinutes);

                String html = wrapShell(
                                "&#9993;",
                                "Verify your email",
                                body,
                                "If you did not create this account, you can safely ignore this email — no action will be taken.");

                send(toEmail, "Verify Your TrackAI Account", html);
        }

        // ── RESEND VERIFICATION EMAIL ────────────────────────────────────────
        @Override
        public void sendResendVerificationEmail(String userName, String toEmail, String verificationLink,
                        long expiryMinutes) {

                String body = paragraph("Hello <b style=\"color:#e2e8f0;\">" + userName + "</b>,")
                                + paragraph("A new verification link has been generated for your TrackAI account.")
                                + linkButton(verificationLink, "Verify Email Address")
                                + expiryNote(expiryMinutes);

                String html = wrapShell(
                                "&#128260;",
                                "New verification link",
                                body,
                                "If you did not request this, please secure your account by changing your password.");

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
                                "If you did not request a password reset, please ignore this email — your password will remain unchanged. Never share this code with anyone.");

                send(toEmail, "TrackAI Password Reset OTP", html);
        }

        // ── ADMIN LOGIN OTP ──────────────────────────────────────────────────
        @Override
        public void sendAdminLoginOtp(String userName, String toEmail, String otp, long expiryMinutes) {

                String body = paragraph("Hello <b style=\"color:#e2e8f0;\">" + userName + "</b>,")
                                + paragraph("An admin login was requested for your TrackAI account. Use the code below to continue:")
                                + otpBlock(otp)
                                + expiryNote(expiryMinutes);

                String html = wrapShell(
                                "&#128737;",
                                "Admin login code",
                                body,
                                "If you did not request this login, please secure your account immediately and contact support.");

                send(toEmail, "TrackAI Admin Login OTP", html);
        }
}