package com.iunu.realestate.util;

/**
 * Strips line breaks from user-supplied text before it reaches a log.
 *
 * <p>A log file is line-oriented, and anything that can put a newline into a
 * logged value can write its own log lines. A career applicant whose "position"
 * is
 *
 * <pre>
 * Developer\n2026-09-16 10:00:00 INFO  Admin login succeeded for ceo@iunu-eg.com
 * </pre>
 *
 * <p>produces a log that reads exactly like a real admin login. That is not a
 * disclosure bug - it is an integrity one: it makes the logs useless as evidence
 * of what happened, which matters most precisely when you are reading them
 * after an incident.
 *
 * <p>Most free-text fields here are already protected by validation that
 * rejects newlines ({@code @Email}, the phone {@code @Pattern}). This is for
 * the ones that are only length-limited.
 */
public final class LogSanitizer {

    private LogSanitizer() {
    }

    /** Replaces CR, LF and tab with a space. Null passes through unchanged. */
    public static String forLog(String value) {
        return value == null ? null : value.replaceAll("[\\r\\n\\t]", " ");
    }
}
