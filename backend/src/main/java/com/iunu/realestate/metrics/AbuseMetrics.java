package com.iunu.realestate.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * The counters that make abuse visible on a dashboard instead of only in the
 * logs. Each one answers a question you actually ask during an incident:
 * "is the limiter firing, and on what?", "is someone guessing passwords?",
 * "is the paid translation API being looped?".
 *
 * <p><strong>No tag is ever derived from user input.</strong> Not the client
 * IP, not the email, not the request path. Micrometer keeps one time series
 * per distinct tag combination in memory forever, so a tag an attacker can
 * vary is itself a memory-exhaustion vector - the exact thing these counters
 * exist to detect. Every tag below comes from a fixed, small set.
 */
@Component
public class AbuseMetrics {

    /** Rate-limit buckets, as a closed set - these become metric tag values. */
    public enum Bucket {
        LOGIN("login"),
        WRITE("write"),
        PUBLIC("public"),
        ADMIN("admin"),
        TRANSLATION("translation");

        private final String tag;

        Bucket(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }

    private final MeterRegistry registry;
    private final Counter loginFailed;
    private final Counter accountLocked;
    private final Counter translationCalls;
    private final Counter translationChars;
    private final Counter translationBudgetExceeded;

    public AbuseMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.loginFailed = Counter.builder("iunu.auth.login.failed")
                .description("Login attempts rejected for bad credentials")
                .register(registry);
        this.accountLocked = Counter.builder("iunu.auth.account.locked")
                .description("Accounts locked after repeated failed logins")
                .register(registry);
        this.translationCalls = Counter.builder("iunu.translation.calls")
                .description("Requests sent to the paid translation API")
                .register(registry);
        this.translationChars = Counter.builder("iunu.translation.chars")
                .description("Characters sent to the paid translation API (what Google bills on)")
                .register(registry);
        this.translationBudgetExceeded = Counter.builder("iunu.translation.budget.exceeded")
                .description("Translation requests refused because the daily character budget was spent")
                .register(registry);

        // Registered up front for every bucket so a flat line reads as "nothing
        // was rejected" rather than "the metric does not exist yet".
        for (Bucket bucket : Bucket.values()) {
            rejectionCounter(bucket);
        }
    }

    public void rateLimitRejected(Bucket bucket) {
        rejectionCounter(bucket).increment();
    }

    public void loginFailed() {
        loginFailed.increment();
    }

    public void accountLocked() {
        accountLocked.increment();
    }

    /** One translation API call carrying {@code characters} billable characters. */
    public void translationCall(long characters) {
        translationCalls.increment();
        translationChars.increment(characters);
    }

    public void translationBudgetExceeded() {
        translationBudgetExceeded.increment();
    }

    private Counter rejectionCounter(Bucket bucket) {
        return Counter.builder("iunu.ratelimit.rejected")
                .description("Requests refused by the rate limiter")
                .tag("bucket", bucket.tag())
                .register(registry);
    }
}
