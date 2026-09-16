package com.iunu.realestate.property;

import com.iunu.realestate.repository.PropertyRepository;
import com.iunu.realestate.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.SpyBean;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The public property cache, and the one property that makes it safe to have
 * at all: a write is visible on the very next public read.
 *
 * <p>The failure this guards against is the expensive kind - an admin publishes
 * a property, checks the site, does not see it, and concludes the dashboard is
 * broken. Caching without eviction produces exactly that, for ten minutes at a
 * time, and only in production where there is traffic to warm the cache.
 */
@DisplayName("Public property cache")
class PropertyCacheTest extends IntegrationTest {

    @SpyBean
    private PropertyRepository propertyRepository;

    @Autowired
    private CacheManager cacheManager;

    /**
     * Every test starts cold. The application context is shared across the
     * whole suite, so a listing another test warmed would otherwise make the
     * first assertion here pass or fail depending on ordering.
     */
    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        clearInvocations(propertyRepository);
    }

    private Long createProperty(String title, boolean published) throws Exception {
        String response = mockMvc.perform(post("/api/properties")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"title":"%s","type":"RESIDENTIAL","location":"New Cairo","published":%s}
                                 """.formatted(title, published)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    @DisplayName("a repeated public listing hits the database once, not twice")
    void secondPublicListIsServedFromCache() throws Exception {
        mockMvc.perform(get("/api/properties")).andExpect(status().isOk());
        clearInvocations(propertyRepository);

        mockMvc.perform(get("/api/properties")).andExpect(status().isOk());

        verify(propertyRepository, times(0)).findByPublishedTrue(any());
    }

    @Test
    @DisplayName("a repeated public detail read hits the database once, not twice")
    void secondPublicDetailIsServedFromCache() throws Exception {
        Long id = createProperty("Cached-" + System.nanoTime(), true);

        mockMvc.perform(get("/api/properties/{id}", id)).andExpect(status().isOk());
        clearInvocations(propertyRepository);

        mockMvc.perform(get("/api/properties/{id}", id)).andExpect(status().isOk());

        verify(propertyRepository, times(0)).findById(id);
    }

    @Test
    @DisplayName("an admin update is visible on the public site immediately")
    void updateEvictsSoTheNewTitleShowsAtOnce() throws Exception {
        String originalTitle = "Before-" + System.nanoTime();
        Long id = createProperty(originalTitle, true);

        // Warm both caches with the old value.
        mockMvc.perform(get("/api/properties"))
                .andExpect(jsonPath("$.content[*].title", hasItem(originalTitle)));
        mockMvc.perform(get("/api/properties/{id}", id))
                .andExpect(jsonPath("$.title").value(originalTitle));

        String newTitle = "After-" + System.nanoTime();
        mockMvc.perform(put("/api/properties/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"title":"%s","type":"RESIDENTIAL","location":"New Cairo","published":true}
                                 """.formatted(newTitle)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/properties/{id}", id))
                .andExpect(jsonPath("$.title").value(newTitle));
        mockMvc.perform(get("/api/properties"))
                .andExpect(jsonPath("$.content[*].title", hasItem(newTitle)))
                .andExpect(jsonPath("$.content[*].title", not(hasItem(originalTitle))));
    }

    /**
     * The one that actually leaks data if eviction is wrong: a property the
     * admin has taken down must not keep being served out of a warm cache.
     */
    @Test
    @DisplayName("unpublishing removes a property from the public cache at once")
    void unpublishedPropertyIsNotServedFromCache() throws Exception {
        String title = "Retired-" + System.nanoTime();
        Long id = createProperty(title, true);

        mockMvc.perform(get("/api/properties"))
                .andExpect(jsonPath("$.content[*].title", hasItem(title)));
        mockMvc.perform(get("/api/properties/{id}", id)).andExpect(status().isOk());

        mockMvc.perform(put("/api/properties/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"title":"%s","type":"RESIDENTIAL","location":"New Cairo","published":false}
                                 """.formatted(title)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/properties"))
                .andExpect(jsonPath("$.content[*].title", not(hasItem(title))));
        mockMvc.perform(get("/api/properties/{id}", id)).andExpect(status().isNotFound());
    }

    /**
     * Admins work with drafts, and a draft changes under them constantly. A
     * cached admin listing would show them their own edits late, which is worse
     * than any query it saves.
     */
    @Test
    @DisplayName("the admin listing is never cached")
    void adminListingIsNotCached() throws Exception {
        String bearer = adminBearer();

        mockMvc.perform(get("/api/properties/admin").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
        clearInvocations(propertyRepository);

        mockMvc.perform(get("/api/properties/admin").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());

        verify(propertyRepository, times(1)).findAll(any(org.springframework.data.domain.Pageable.class));
    }

    /** A 404 must not be cached, or the property can never appear once created. */
    @Test
    @DisplayName("a missing property is not cached as missing")
    void missingPropertyIsNotCached() throws Exception {
        mockMvc.perform(get("/api/properties/{id}", 999_999_999L)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/properties/{id}", 999_999_999L)).andExpect(status().isNotFound());

        // Both reads reached the repository: nothing negative was stored.
        verify(propertyRepository, times(2)).findById(999_999_999L);
    }
}
