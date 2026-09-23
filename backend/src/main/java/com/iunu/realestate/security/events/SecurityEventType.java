package com.iunu.realestate.security.events;

/**
 * Everything the app treats as security-relevant, as a closed set.
 *
 * <p>The enum name is the {@code type} tag on {@code iunu.security.events} and
 * the {@code event} field of the SECURITY log line. Being an enum is the point:
 * no request can add a value, so no request can add a time series. Renaming a
 * constant renames a metric that alert rules match on - see
 * monitoring/alert-rules.yml before doing it.
 */
public enum SecurityEventType {
    LOGIN_FAILED,
    ACCOUNT_LOCKED,
    LOGIN_SUCCEEDED_ADMIN,
    ADMIN_LOGIN_NEW_IP,
    REFRESH_REUSE_DETECTED,
    REFRESH_RACE_LOST,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_COMPLETED,
    PASSWORD_CHANGED,
    RATE_LIMITED,
    EDGE_SECRET_REJECTED,
    UPLOAD_REJECTED,
    ACCESS_DENIED,
    TOKEN_INVALID
}
