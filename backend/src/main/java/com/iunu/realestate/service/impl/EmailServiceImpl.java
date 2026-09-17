package com.iunu.realestate.service.impl;

import com.iunu.realestate.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final Environment environment;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Override
    public void sendPasswordResetEmail(String toEmail, String fullName, String resetLink) {
        if (!mailEnabled) {
            // SMTP not configured for this environment. Never fail the caller
            // for this - forgot-password always returns a generic success
            // response regardless of delivery.
            //
            // The link is a working password-reset token, so it is printed only
            // outside production. app.mail.enabled defaults to FALSE, which
            // means a deployment that never configured SMTP would otherwise
            // write a valid account-takeover link to its log stream on every
            // forgot-password request - readable by anyone with access to the
            // hosting dashboard, a log drain, or an exported log file. Locally
            // it is the only way to complete the flow, so there it still prints.
            if (isProduction()) {
                log.warn("app.mail.enabled=false in production: the password reset email for {} was NOT sent. "
                        + "Configure SMTP (MAIL_ENABLED=true) - password reset does not work without it.", toEmail);
            } else {
                log.info("app.mail.enabled=false - skipping real email send. Reset link for {}: {}",
                        toEmail, resetLink);
            }
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

    /**
     * Profile-based rather than a separate flag, so nobody has to remember to
     * set one: the environment that must not print tokens is exactly the one
     * already marked as production.
     */
    private boolean isProduction() {
        return environment.acceptsProfiles(Profiles.of("prod"));
    }
}
