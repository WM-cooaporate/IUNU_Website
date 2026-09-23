package com.iunu.realestate.security.lockout;

/**
 * Failed-login bookkeeping, deliberately kept out of the database: a row write
 * per failed login would turn a password-guessing flood into a write load on
 * PostgreSQL, and - the bug behind N5 - a write inside the login transaction is
 * rolled back by the very exception that reports the failure.
 *
 * <p>Two locks, answering two different attacks:
 *
 * <ul>
 *   <li><strong>Per (account, client IP).</strong> One source guessing one
 *       account locks itself out, and nobody else. This is what makes M9's
 *       lockout denial of service stop working: an attacker from one address
 *       can no longer lock the real admin out of their own dashboard.</li>
 *   <li><strong>Per account, across all addresses.</strong> A safety net for a
 *       distributed attack that stays under the per-pair threshold from many
 *       addresses. It does lock the real owner out for the lock duration - the
 *       residual M9 risk - and raises ACCOUNT_LOCKED so a person hears about it.</li>
 * </ul>
 *
 * <p>An interface so the in-memory implementation can be swapped for Redis
 * when the app runs more than one instance; today each JVM has its own view,
 * like the rate limiter.
 */
public interface LoginAttemptStore {

    /** Whether a login for {@code accountKey} from {@code clientIp} must be refused right now. */
    boolean isLocked(String accountKey, String clientIp);

    /** Records one failed login and reports which locks, if any, it just engaged. */
    FailureOutcome recordFailure(String accountKey, String clientIp);

    /** A successful login from this address clears that pair's count. The account-wide window is kept. */
    void recordSuccess(String accountKey, String clientIp);

    /** Clears every lock and counter for the account - a completed password reset. */
    void clearAccount(String accountKey);

    /** Failed attempts currently counted against this pair. */
    int pairFailures(String accountKey, String clientIp);

    record FailureOutcome(boolean pairLockedNow, boolean accountLockedNow, int pairFailures, int accountFailuresLastHour) {
    }
}
