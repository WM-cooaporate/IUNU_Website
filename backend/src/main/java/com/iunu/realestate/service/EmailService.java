package com.iunu.realestate.service;

public interface EmailService {
    void sendPasswordResetEmail(String toEmail, String fullName, String resetLink);
}
