package com.iunu.realestate.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Sends the "new sign-in to the dashboard" email off the request thread.
 *
 * <p>A bean of its own, rather than an @Async method on AuthServiceImpl,
 * because @Async works through a proxy and a call from inside the same class
 * never goes through it - the email would quietly be sent synchronously, and
 * an admin login from a new address would take an SMTP round trip longer than
 * one from a known address. That timing difference is itself a signal.
 */
@Component
@RequiredArgsConstructor
public class AdminSignInAlerter {

    private final EmailService emailService;

    @Async
    public void alertNewSignIn(String toEmail, String fullName, String clientIp, Instant when) {
        emailService.sendNewSignInAlert(toEmail, fullName, clientIp, when);
    }
}
