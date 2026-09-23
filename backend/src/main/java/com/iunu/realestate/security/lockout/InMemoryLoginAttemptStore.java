package com.iunu.realestate.security.lockout;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * {@link LoginAttemptStore} in bounded Caffeine caches, the same pattern as
 * the rate limiter's bucket store: an unbounded map keyed on client address
 * is an OOM an attacker can drive, so every store here has a maximum size and
 * expires idle entries.
 *
 * <p>Per-account windows hold at most {@code accountThreshold + 1}
 * timestamps, so a flood against one account costs a fixed amount of memory.
 */
@Component
public class InMemoryLoginAttemptStore implements LoginAttemptStore {

    private static final Duration ACCOUNT_WINDOW = Duration.ofHours(1);

    private final int pairThreshold;
    private final int accountThreshold;
    private final Duration lockDuration;
    private final Clock clock;

    private final Cache<String, PairState> pairs;
    private final Cache<String, Deque<Instant>> accountWindows;
    private final Cache<String, Instant> accountLocks;

    @Autowired
    public InMemoryLoginAttemptStore(
            @Value("${app.security.max-failed-attempts:5}") int pairThreshold,
            @Value("${app.security.account-lock-threshold-per-hour:50}") int accountThreshold,
            @Value("${app.security.lock-duration-minutes:15}") long lockDurationMinutes,
            @Value("${app.security.login-attempts.max-tracked:100000}") long maxTracked
    ) {
        this(pairThreshold, accountThreshold, Duration.ofMinutes(lockDurationMinutes), maxTracked, Clock.systemUTC());
    }

    /** For tests: an injectable clock, and a small cap to prove the bound. */
    public InMemoryLoginAttemptStore(int pairThreshold, int accountThreshold, Duration lockDuration,
                                     long maxTracked, Clock clock) {
        this.pairThreshold = pairThreshold;
        this.accountThreshold = accountThreshold;
        this.lockDuration = lockDuration;
        this.clock = clock;
        Duration idle = lockDuration.compareTo(ACCOUNT_WINDOW) > 0 ? lockDuration : ACCOUNT_WINDOW;
        this.pairs = Caffeine.newBuilder().maximumSize(maxTracked).expireAfterAccess(idle).build();
        this.accountWindows = Caffeine.newBuilder().maximumSize(maxTracked).expireAfterAccess(idle).build();
        this.accountLocks = Caffeine.newBuilder().maximumSize(maxTracked).expireAfterWrite(lockDuration).build();
    }

    @Override
    public boolean isLocked(String accountKey, String clientIp) {
        Instant now = clock.instant();
        Instant accountLockedUntil = accountLocks.getIfPresent(accountKey);
        if (accountLockedUntil != null && accountLockedUntil.isAfter(now)) {
            return true;
        }
        PairState pair = pairs.getIfPresent(pairKey(accountKey, clientIp));
        return pair != null && pair.lockedUntil(now) != null;
    }

    @Override
    public FailureOutcome recordFailure(String accountKey, String clientIp) {
        Instant now = clock.instant();

        PairState pair = pairs.get(pairKey(accountKey, clientIp), ignored -> new PairState());
        boolean pairLockedNow;
        int pairCount;
        synchronized (pair) {
            if (pair.lockedUntil != null && !pair.lockedUntil.isAfter(now)) {
                // A lock that has run out starts the pair from zero.
                pair.failures = 0;
                pair.lockedUntil = null;
            }
            pair.failures++;
            pairCount = pair.failures;
            pairLockedNow = pair.lockedUntil == null && pair.failures >= pairThreshold;
            if (pairLockedNow) {
                pair.lockedUntil = now.plus(lockDuration);
            }
        }

        Deque<Instant> window = accountWindows.get(accountKey, ignored -> new ArrayDeque<>());
        boolean accountLockedNow = false;
        int accountCount;
        synchronized (window) {
            Instant cutoff = now.minus(ACCOUNT_WINDOW);
            while (!window.isEmpty() && !window.peekFirst().isAfter(cutoff)) {
                window.pollFirst();
            }
            window.addLast(now);
            // Only the newest threshold+1 matter: that is all "more than
            // threshold in the last hour" needs, and it caps memory per account.
            while (window.size() > accountThreshold + 1) {
                window.pollFirst();
            }
            accountCount = window.size();
            if (accountCount > accountThreshold) {
                Instant existing = accountLocks.getIfPresent(accountKey);
                if (existing == null || !existing.isAfter(now)) {
                    accountLocks.put(accountKey, now.plus(lockDuration));
                    accountLockedNow = true;
                }
            }
        }
        return new FailureOutcome(pairLockedNow, accountLockedNow, pairCount, accountCount);
    }

    @Override
    public void recordSuccess(String accountKey, String clientIp) {
        pairs.invalidate(pairKey(accountKey, clientIp));
    }

    @Override
    public void clearAccount(String accountKey) {
        accountLocks.invalidate(accountKey);
        accountWindows.invalidate(accountKey);
        String prefix = accountKey + '|';
        pairs.asMap().keySet().removeIf(key -> key.startsWith(prefix));
    }

    @Override
    public int pairFailures(String accountKey, String clientIp) {
        PairState pair = pairs.getIfPresent(pairKey(accountKey, clientIp));
        return pair == null ? 0 : pair.failures;
    }

    /** Package-private so a test can see the bound hold. */
    long trackedPairs() {
        pairs.cleanUp();
        return pairs.estimatedSize();
    }

    private static String pairKey(String accountKey, String clientIp) {
        return accountKey + '|' + clientIp;
    }

    private static final class PairState {
        private int failures;
        private Instant lockedUntil;

        synchronized Instant lockedUntil(Instant now) {
            return lockedUntil != null && lockedUntil.isAfter(now) ? lockedUntil : null;
        }
    }
}
