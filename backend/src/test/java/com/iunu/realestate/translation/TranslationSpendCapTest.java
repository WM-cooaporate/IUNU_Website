package com.iunu.realestate.translation;

import com.iunu.realestate.exception.ConflictException;
import com.iunu.realestate.metrics.AbuseMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The two guards on the paid translation API.
 *
 * <p>Neither protects against a busy admin. Both protect against a loop - a
 * compromised token or a bug in the dashboard hitting preview or backfill
 * repeatedly, where every call is billable and nothing in the app would
 * otherwise stop it.
 */
@DisplayName("Translation spend caps")
class TranslationSpendCapTest {

    private static AbuseMetrics metrics() {
        return new AbuseMetrics(new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("the budget allows spending up to the limit and refuses past it")
    void budgetStopsAtTheLimit() {
        TranslationBudget budget = new TranslationBudget(100, metrics());

        assertThat(budget.tryReserve(60)).isTrue();
        assertThat(budget.tryReserve(40)).isTrue();
        assertThat(budget.tryReserve(1)).isFalse();
        assertThat(budget.charsUsedToday()).isEqualTo(100);
    }

    /**
     * A single request larger than the whole budget must be refused outright,
     * not partially allowed - otherwise one enormous description walks straight
     * past the cap.
     */
    @Test
    @DisplayName("a single oversized reservation is refused rather than clamped")
    void oversizedSingleReservationIsRefused() {
        TranslationBudget budget = new TranslationBudget(100, metrics());

        assertThat(budget.tryReserve(500)).isFalse();
        assertThat(budget.charsUsedToday()).isZero();
    }

    @Test
    @DisplayName("the budget resets at UTC midnight")
    void budgetRollsOverAtUtcMidnight() {
        AtomicReference<Instant> now =
                new AtomicReference<>(Instant.parse("2026-03-01T23:59:00Z"));
        TranslationBudget budget = new TranslationBudget(100, metrics(), new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        });

        assertThat(budget.tryReserve(100)).isTrue();
        assertThat(budget.tryReserve(1)).isFalse();

        now.set(Instant.parse("2026-03-02T00:01:00Z"));

        assertThat(budget.charsUsedToday()).isZero();
        assertThat(budget.tryReserve(100)).isTrue();
    }

    /**
     * Zero means "a real quota is configured in the Google Cloud Console" -
     * being double-capped by a number in a YAML file helps nobody.
     */
    @Test
    @DisplayName("a limit of zero means unlimited, not blocked")
    void zeroLimitMeansUnlimited() {
        TranslationBudget budget = new TranslationBudget(0, metrics());

        assertThat(budget.tryReserve(10_000_000)).isTrue();
    }

    @Test
    @DisplayName("a second concurrent backfill is refused with 409 rather than queued")
    void concurrentBackfillIsRefused() throws Exception {
        TranslationService translator = mock(TranslationService.class);
        when(translator.isEnabled()).thenReturn(true);

        PropertyArabicWriter writer = mock(PropertyArabicWriter.class);
        CountDownLatch firstRunStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstRun = new CountDownLatch(1);

        // Block the first run inside the guarded section, so the second call
        // genuinely overlaps it rather than racing a run that already finished.
        when(writer.idsMissingArabic()).thenAnswer(invocation -> {
            firstRunStarted.countDown();
            releaseFirstRun.await(5, TimeUnit.SECONDS);
            return List.of();
        });
        when(writer.readMissing(anyLong())).thenReturn(Optional.empty());
        when(translator.translateEnToAr(any())).thenReturn(List.of());

        PropertyTranslationBackfillService service =
                new PropertyTranslationBackfillService(writer, translator);

        Thread firstRun = new Thread(service::backfill, "first-backfill");
        firstRun.start();
        assertThat(firstRunStarted.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(service::backfill)
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already running");

        releaseFirstRun.countDown();
        firstRun.join(5_000);

        // And the guard is released afterwards: the endpoint is usable again
        // rather than stuck at 409 until the next restart.
        assertThat(service.backfill().enabled()).isTrue();
    }

    @Test
    @DisplayName("a backfill that throws still releases the guard")
    void failedBackfillReleasesTheGuard() {
        TranslationService translator = mock(TranslationService.class);
        when(translator.isEnabled()).thenReturn(true);

        PropertyArabicWriter writer = mock(PropertyArabicWriter.class);
        when(writer.idsMissingArabic()).thenThrow(new IllegalStateException("database down"));

        PropertyTranslationBackfillService service =
                new PropertyTranslationBackfillService(writer, translator);

        assertThatThrownBy(service::backfill).isInstanceOf(IllegalStateException.class);
        // Not a ConflictException: the guard was released, so this is a second
        // real attempt rather than a rejection.
        assertThatThrownBy(service::backfill).isInstanceOf(IllegalStateException.class);
    }
}
