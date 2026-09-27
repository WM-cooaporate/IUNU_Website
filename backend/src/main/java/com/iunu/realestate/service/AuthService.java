package com.iunu.realestate.service;

import com.iunu.realestate.dto.request.*;
import com.iunu.realestate.dto.response.AuthResponse;
import com.iunu.realestate.dto.response.MessageResponse;
import com.iunu.realestate.dto.response.UserResponse;

public interface AuthService {
    MessageResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refresh(RefreshTokenRequest request);

    void logout(RefreshTokenRequest request);

    MessageResponse forgotPassword(ForgotPasswordRequest request);

    MessageResponse resetPassword(ResetPasswordRequest request);

    MessageResponse changePassword(String currentUserEmail, ChangePasswordRequest request);

    UserResponse getCurrentUser(String email);
}
