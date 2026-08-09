package com.iunu.realestate.service.impl;

import com.iunu.realestate.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Override
    public void sendPasswordResetEmail(String toEmail, String fullName, String resetLink) {
        if (!mailEnabled) {
            // SMTP not configured for this environment (e.g. local dev). Never
            // fail the caller for this - forgot-password always returns a
            // generic success response regardless of delivery.
            log.info("app.mail.enabled=false - skipping real email send. Reset link for {}: {}", toEmail, resetLink);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("Reset your IUNU account password");
            helper.setText("""
                    Hello %s,

                    We received a request to reset your password. Click the link below to choose a new one:

                    %s

                    This link expires in 30 minutes. If you did not request this, you can safely ignore this email.

                    - IUNU
                    """.formatted(fullName, resetLink));
            mailSender.send(message);
        } catch (MailException | jakarta.mail.MessagingException e) {
            // Delivery failure must never leak whether the account exists or
            // block the API response - just log it for operators to notice.
            log.error("Failed to send password reset email to {}", toEmail, e);
        }
    }
}
