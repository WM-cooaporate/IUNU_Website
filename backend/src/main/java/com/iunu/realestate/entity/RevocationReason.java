package com.iunu.realestate.entity;

/**
 * Why a refresh token was revoked. Stored as its name, so reordering these is
 * safe and renaming one is a data migration.
 *
 * <p>Only {@link #ROTATED} carries meaning for reuse detection: a token that
 * was exchanged for a new pair, presented again after the grace window, was
 * copied by someone. Every other reason is a plain "no".
 */
public enum RevocationReason {
    /** Exchanged for a new pair by /api/auth/refresh. */
    ROTATED,
    LOGOUT,
    PASSWORD_CHANGED,
    PASSWORD_RESET,
    /** Part of a family revoked because a rotated token was replayed. */
    REUSE_DETECTED,
    ADMIN_ACTION
}
