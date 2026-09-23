package com.iunu.realestate.service;

import java.time.Instant;

public interface EmailService {
    void sendPasswordResetEmail(String toEmail, String fullName, String resetLink);

    /** Tells an admin their account was just used from an address it has not been used from recently. */
    void sendNewSignInAlert(String toEmail, String fullName, String clientIp, Instant when);
}
