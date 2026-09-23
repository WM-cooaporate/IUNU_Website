package com.iunu.realestate.security.lockout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryLoginAttemptStore")
class InMemoryLoginAttemptStoreTest {

    /** A clock the test moves by hand. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-09-23T12:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override public Instant instant() { return now; }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    }

    private final MovableClock clock = new MovableClock();

    private InMemoryLoginAttemptStore store(long maxTracked) {
        return new InMemoryLoginAttemptStore(5, 50, Duration.ofMinutes(15), maxTracked, clock);
    }

    @Test
    @DisplayName("five failures lock that (account, IP) pair and no other")
    void pairLock() {
        InMemoryLoginAttemptStore store = store(1000);
        for (int i = 1; i <= 4; i++) {
            assertThat(store.recordFailure("7", "1.1.1.1").pairLockedNow()).isFalse();
        }
        assertThat(store.recordFailure("7", "1.1.1.1").pairLockedNow()).isTrue();

        assertThat(store.isLocked("7", "1.1.1.1")).isTrue();
        assertThat(store.isLocked("7", "2.2.2.2")).as("another address").isFalse();
        assertThat(store.isLocked("8", "1.1.1.1")).as("another account").isFalse();
    }

    @Test
    @DisplayName("a pair lock runs out after the lock duration")
    void pairLockExpires() {
        InMemoryLoginAttemptStore store = store(1000);
        for (int i = 0; i < 5; i++) store.recordFailure("7", "1.1.1.1");
        clock.advance(Duration.ofMinutes(15).plusSeconds(1));
        assertThat(store.isLocked("7", "1.1.1.1")).isFalse();
    }

    @Test
    @DisplayName("the 51st failure across addresses within an hour locks the account, once")
    void accountLock() {
        InMemoryLoginAttemptStore store = store(1000);
        for (int i = 1; i <= 50; i++) {
            assertThat(store.recordFailure("7", "10.0.0." + i).accountLockedNow()).as("failure %d", i).isFalse();
        }
        assertThat(store.recordFailure("7", "10.0.0.51").accountLockedNow()).isTrue();
        assertThat(store.isLocked("7", "192.0.2.1")).as("a fresh address").isTrue();
        assertThat(store.recordFailure("7", "10.0.0.52").accountLockedNow()).as("already locked").isFalse();
    }

    @Test
    @DisplayName("failures older than an hour do not count toward the account lock")
    void accountWindowRolls() {
        InMemoryLoginAttemptStore store = store(1000);
        for (int i = 1; i <= 50; i++) store.recordFailure("7", "10.0.0." + i);
        clock.advance(Duration.ofMinutes(61));
        assertThat(store.recordFailure("7", "10.0.1.1").accountLockedNow()).isFalse();
        assertThat(store.isLocked("7", "192.0.2.1")).isFalse();
    }

    @Test
    @DisplayName("clearing the account lifts every lock and counter")
    void clearAccount() {
        InMemoryLoginAttemptStore store = store(1000);
        for (int i = 0; i < 5; i++) store.recordFailure("7", "1.1.1.1");
        for (int i = 1; i <= 51; i++) store.recordFailure("7", "10.0.0." + i);
        store.clearAccount("7");
        assertThat(store.isLocked("7", "1.1.1.1")).isFalse();
        assertThat(store.isLocked("7", "192.0.2.1")).isFalse();
        assertThat(store.pairFailures("7", "1.1.1.1")).isZero();
    }

    @Test
    @DisplayName("a success clears only that pair's count")
    void successClearsPair() {
        InMemoryLoginAttemptStore store = store(1000);
        for (int i = 0; i < 4; i++) store.recordFailure("7", "1.1.1.1");
        store.recordSuccess("7", "1.1.1.1");
        assertThat(store.pairFailures("7", "1.1.1.1")).isZero();
    }

    @Test
    @DisplayName("the store stays bounded however many addresses fail")
    void bounded() {
        InMemoryLoginAttemptStore store = store(16);
        for (int i = 0; i < 2000; i++) {
            store.recordFailure("7", "10." + (i / 250) + ".0." + (i % 250));
        }
        assertThat(store.trackedPairs()).isLessThanOrEqualTo(16);
    }
}
