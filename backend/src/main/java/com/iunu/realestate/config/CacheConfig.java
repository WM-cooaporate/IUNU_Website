package com.iunu.realestate.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * In-process caching of the public property reads - the two endpoints every
 * visitor hits and the only ones whose answer is the same for everybody.
 *
 * <p><strong>This cache is per JVM.</strong> It is deliberately Caffeine and
 * not Redis, because the backend runs as a single instance. If a second
 * instance is ever added, this cache and the rate limiter's bucket store both
 * become per-instance and wrong: an admin's save would evict one instance's
 * copy and leave the other serving the old listing for up to
 * {@link #TTL}. Moving both to Redis is the prerequisite for scaling out, not
 * an optimisation to do afterwards. See the backend README.
 *
 * <p>Entries are {@code PropertyResponse} / {@code Page<PropertyResponse>} -
 * immutable DTOs, never JPA entities. Caching an entity would keep a lazy
 * collection alive past its persistence context and throw
 * {@code LazyInitializationException} on the next reader.
 *
 * <p>A cache miss or failure is never an error: Spring falls straight through
 * to the repository. The only correctness requirement is the other direction -
 * a write must not leave a stale read behind, which is why every mutating
 * method in {@code PropertyServiceImpl} evicts both caches entirely.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Public listings of published properties, keyed by type + page + size + sort. */
    public static final String PUBLIC_PROPERTY_LIST = "publicPropertyList";

    /** Single published properties, keyed by id. */
    public static final String PUBLIC_PROPERTY_BY_ID = "publicPropertyById";

    /**
     * Short enough that even a cache that somehow misses an eviction (a second
     * instance, a restart mid-write) self-corrects quickly; long enough that a
     * burst of traffic on the home page collapses onto one query.
     */
    private static final Duration TTL = Duration.ofMinutes(10);

    @Bean
    public CaffeineCacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        // Registered explicitly rather than created on demand, so a typo in a
        // @Cacheable name fails loudly (no such cache) instead of quietly
        // creating a third cache that nothing ever evicts.
        //
        // A listing key is (type x page x size x sort), so the key space is
        // wider per entry but the useful part of it is small - the first few
        // pages of each type. Ids are the opposite: many distinct keys, each
        // cheap. Hence 500 vs 1000. Both are bounded, which is the point: an
        // unbounded cache fed by attacker-chosen page numbers is a memory leak
        // with extra steps.
        manager.registerCustomCache(PUBLIC_PROPERTY_LIST, build(500));
        manager.registerCustomCache(PUBLIC_PROPERTY_BY_ID, build(1000));
        return manager;
    }

    /**
     * recordStats() is what makes hit rates show up under
     * /actuator/metrics/cache.gets - without it a load test cannot tell a warm
     * cache from a cold one, which is most of what you want to know.
     */
    private static com.github.benmanes.caffeine.cache.Cache<Object, Object> build(long maximumSize) {
        return Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(TTL)
                .recordStats()
                .build();
    }
}
