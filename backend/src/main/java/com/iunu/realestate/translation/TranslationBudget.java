package com.iunu.realestate.translation;

import com.iunu.realestate.metrics.AbuseMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * A daily ceiling on characters sent to Google Translate, which bills per
 * character.
 *
 * <p>The threat is not a busy admin. It is a loop: a compromised admin token,
 * or a bug in the dashboard, hitting the preview or backfill endpoint
 * repeatedly. Each call is a few cents; a few hundred thousand of them is a
 * bill the client did not agree to. The per-minute rate limit slows that down,
 * this puts an absolute number on the day.
 *
 * <p>When the budget is spent the translator reports itself disabled, which is
 * a path the whole feature already handles: saves succeed, the Arabic column
 * stays null, and the site falls back to English. Nothing fails.
 *
 * <p><strong>This is a backstop, not the real cap.</strong> It lives in one
 * JVM's memory, so it resets on every deploy and every restart, and it cannot
 * see spending by anything other than this process. The real cap is a quota
 * and a budget alert in the Google Cloud Console - see docs/DDOS_RUNBOOK.md.
 */
@Slf4j
@Component
public class TranslationBudget {

    private final long dailyCharLimit;
    private final Clock clock;

    /** Guarded by {@code this}; the two fields must move together. */
    private LocalDate windowDay;
    private long charsUsed;
    private boolean exhaustionLogged;

    private final AbuseMetrics metrics;

    // Explicit, because the package-private test constructor below makes this
    // class ambiguous to Spring's "one constructor, use it" rule.
    @Autowired
    public TranslationBudget(
            @Value("${app.translation.google.daily-char-limit:200000}") long dailyCharLimit,
            AbuseMetrics metrics
    ) {
        this(dailyCharLimit, metrics, Clock.systemUTC());
    }

    /** Test seam: a fixed clock makes the midnight rollover assertable. */
    TranslationBudget(long dailyCharLimit, AbuseMetrics metrics, Clock clock) {
        this.dailyCharLimit = dailyCharLimit;
        this.metrics = metrics;
        this.clock = clock;
        this.windowDay = today();
    }

    /**
     * Reserves {@code characters} against today's budget, returning false when
     * that would exceed it.
     *
     * <p>Reserve-before-spend rather than record-after: an over-budget call must
     * not be made at all, and checking afterwards means the money is already
     * gone. A call that then fails at Google has still consumed budget, which is
     * the safe direction to be wrong in.
     *
     * <p>A zero or negative limit means unlimited - that is what a deployment
     * that has set a real quota in the Google Console would want, rather than
     * being double-capped by a number in a YAML file.
     */
    public synchronized boolean tryReserve(long characters) {
        if (dailyCharLimit <= 0) {
            return true;
        }

        rolloverIfNewDay();

        if (charsUsed + characters > dailyCharLimit) {
            // WARN once per day, not once per rejected call: a loop hitting a
            // spent budget would otherwise fill the log with the same line,
            // which is its own small denial of service on whoever reads them.
            if (!exhaustionLogged) {
                log.warn("Daily translation budget of {} characters is spent; translation is off until UTC midnight. "
                        + "Saves continue to succeed with English fallback.", dailyCharLimit);
                exhaustionLogged = true;
            }
            metrics.translationBudgetExceeded();
            return false;
        }

        charsUsed += characters;
        return true;
    }

    /** Characters reserved so far in the current UTC day. */
    public synchronized long charsUsedToday() {
        rolloverIfNewDay();
        return charsUsed;
    }

    public long dailyCharLimit() {
        return dailyCharLimit;
    }

    private void rolloverIfNewDay() {
        LocalDate today = today();
        if (!today.equals(windowDay)) {
            windowDay = today;
            charsUsed = 0;
            exhaustionLogged = false;
        }
    }

    /**
     * UTC, not the server's zone. Google's own quota resets on Pacific time,
     * but a fixed, stated zone is what makes this predictable - a budget that
     * rolls over at whatever the container's TZ happens to be is a budget
     * nobody can reason about.
     */
    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }
}
