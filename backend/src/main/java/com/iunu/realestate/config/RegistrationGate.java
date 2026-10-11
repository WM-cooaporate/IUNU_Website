package com.iunu.realestate.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Public self-registration is off unless app.auth.registration-enabled
 * (APP_REGISTRATION_ENABLED) is true. Accounts come from the startup
 * bootstrap (ADMIN_EMAIL / ADMIN_PASSWORD) and from an admin
 * (POST /api/admin/users); nobody signs themselves up.
 *
 * <p>While it is off, POST /api/auth/register answers 404 as if it did not
 * exist. The endpoint and AuthService.register are kept so it can be switched
 * back on without a rewrite.
 *
 * <p>This is an interceptor rather than the first line of the controller
 * method because Spring reads and validates the @Valid body before the method
 * runs. A bad body would otherwise get a 400 naming the fields - which tells
 * the caller the endpoint is there. preHandle runs before any of that, and
 * GlobalExceptionHandler turns the exception into the usual problem body.
 */
@Component
public class RegistrationGate implements HandlerInterceptor {

    public static final String PATH = "/api/auth/register";

    private final boolean registrationEnabled;

    public RegistrationGate(@Value("${app.auth.registration-enabled:false}") boolean registrationEnabled) {
        this.registrationEnabled = registrationEnabled;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!registrationEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return true;
    }
}
