package com.iunu.realestate.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.iunu.realestate.repository.PropertyRepository;
import com.iunu.realestate.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who can read and write properties, end to end through the real security
 * chain.
 *
 * <p>The public site fetches {@code /api/properties} and
 * {@code /api/properties/{id}} without a token; external clients (the k6 smoke
 * test) use the {@code /api/v1/properties} alias of the same two reads. Both
 * must answer anonymous visitors with published rows only, in the public
 * shape - the public-read tests run against each. Drafts are read only through
 * {@code /api/properties/admin/**}, and every write is ADMIN-only.
 *
 * <p>An invalid or expired token on a public read is treated as anonymous
 * (200, published only), not refused: JwtAuthenticationFilter records the bad
 * token and lets the request continue unauthenticated, and only a rule that
 * needs authentication turns that into a 401. A visitor with a stale admin
 * token in their browser must still see the site.
 *
 * <p>Rows are created through the admin API rather than the repository so the
 * public caches are evicted exactly as they are in production. The suite
 * shares one database, so listings are walked page by page instead of
 * assuming this test's rows land on page 0.
 */
@DisplayName("Property access: anonymous vs admin")
class PropertyAccessIntegrationTest extends IntegrationTest {

    @Autowired private PropertyRepository propertyRepository;

    private String admin;
    private long publishedId;
    private long draftId;

    @BeforeEach
    void createRows() throws Exception {
        admin = adminBearer();
        publishedId = create("Access-published-" + System.nanoTime(), true);
        draftId = create("Access-draft-" + System.nanoTime(), false);
    }

    // ---------------------------------------------------------------------
    // Anonymous reads
    // ---------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/properties", "/api/v1/properties"})
    @DisplayName("anonymous list: 200, published rows only")
    void anonymousListShowsOnlyPublished(String base) throws Exception {
        List<Long> ids = walkIds(base, null);

        assertThat(ids).contains(publishedId).doesNotContain(draftId);
        // Not just "not this draft": no draft of any test is on the public list.
        assertThat(propertyRepository.findAllById(ids))
                .hasSize(ids.size())
                .allMatch(property -> property.isPublished());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/properties", "/api/v1/properties"})
    @DisplayName("anonymous details of a draft: 404, not 403")
    void anonymousDraftByIdIsNotFound(String base) throws Exception {
        mockMvc.perform(get(base + "/{id}", draftId))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/properties", "/api/v1/properties"})
    @DisplayName("anonymous details of a missing id: 404")
    void anonymousMissingByIdIsNotFound(String base) throws Exception {
        mockMvc.perform(get(base + "/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/properties", "/api/v1/properties"})
    @DisplayName("anonymous details of a published row: 200 with no internal fields")
    void anonymousPublishedByIdHasNoInternalFields(String base) throws Exception {
        mockMvc.perform(get(base + "/{id}", publishedId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(publishedId))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.published").doesNotExist())
                .andExpect(jsonPath("$.createdAt").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist());

        // Same shape on the list.
        mockMvc.perform(get(base).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].published").doesNotExist())
                .andExpect(jsonPath("$.content[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$.content[0].updatedAt").doesNotExist());
    }

    // ---------------------------------------------------------------------
    // Anonymous writes and admin reads
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("anonymous create / update / publish-toggle / delete / image upload: 401")
    void anonymousWritesAreUnauthorized() throws Exception {
        mockMvc.perform(post("/api/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Anonymous create", true)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/properties/{id}", publishedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Anonymous rename", true)))
                .andExpect(status().isUnauthorized());

        // Publishing is an update with published=true - the toggle the dashboard uses.
        mockMvc.perform(put("/api/properties/{id}", draftId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Anonymous publish", true)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/properties/{id}", publishedId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(multipart("/api/properties/images")
                        .file(new MockMultipartFile("files", "x.png", "image/png", pngBytes())))
                .andExpect(status().isUnauthorized());

        // And none of them landed.
        assertThat(propertyRepository.findById(draftId)).get()
                .matches(property -> !property.isPublished());
        assertThat(propertyRepository.existsById(publishedId)).isTrue();
    }

    @Test
    @DisplayName("anonymous admin listing and admin details: 401")
    void anonymousAdminReadsAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/properties/admin")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/properties/admin/{id}", draftId)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("admin listing: 200, includes drafts")
    void adminListingIncludesDrafts() throws Exception {
        assertThat(walkIds("/api/properties/admin", admin)).contains(publishedId, draftId);

        mockMvc.perform(get("/api/properties/admin/{id}", draftId)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(false));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/properties", "/api/v1/properties"})
    @DisplayName("an admin token on the public endpoints still gets published rows only")
    void adminTokenDoesNotWidenPublicEndpoints(String base) throws Exception {
        assertThat(walkIds(base, admin)).contains(publishedId).doesNotContain(draftId);

        mockMvc.perform(get(base + "/{id}", draftId)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/properties", "/api/v1/properties"})
    @DisplayName("size=100000 is clamped to 50")
    void pageSizeIsClamped(String base) throws Exception {
        mockMvc.perform(get(base).param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50));
    }

    // ---------------------------------------------------------------------
    // The /api/v1 alias: public reads only
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("anonymous writes to /api/v1/properties: 401")
    void anonymousV1WritesAreUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Anonymous v1 create", true)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/properties/{id}", draftId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Anonymous v1 publish", true)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/properties/{id}", publishedId))
                .andExpect(status().isUnauthorized());

        assertThat(propertyRepository.findById(draftId)).get()
                .matches(property -> !property.isPublished());
        assertThat(propertyRepository.existsById(publishedId)).isTrue();
    }

    @Test
    @DisplayName("/api/v1 has no admin surface: nothing there ever returns a draft")
    void v1HasNoAdminSurface() throws Exception {
        // One segment: public by rule, but it is only the details handler, and
        // "admin" is not an id - a 400, not the admin listing.
        mockMvc.perform(get("/api/v1/properties/admin"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/properties/admin").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isBadRequest());
        // Deeper paths are not covered by the public rule at all.
        mockMvc.perform(get("/api/v1/properties/admin/{id}", draftId))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/properties", "/api/v1/properties"})
    @DisplayName("an invalid token on a public read is treated as anonymous: 200, published only")
    void invalidTokenOnPublicReadIsAnonymous(String base) throws Exception {
        String garbage = "Bearer not.a.valid-token";

        List<Long> ids = walkIds(base, garbage);
        assertThat(ids).contains(publishedId).doesNotContain(draftId);

        mockMvc.perform(get(base + "/{id}", draftId).header(HttpHeaders.AUTHORIZATION, garbage))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("401 and 403 error bodies are declared as UTF-8 JSON")
    void errorResponsesAreUtf8() throws Exception {
        mockMvc.perform(get("/api/properties/admin"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/json")))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("charset=UTF-8")));

        mockMvc.perform(get("/api/properties/admin").header(HttpHeaders.AUTHORIZATION, userBearer()))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("charset=UTF-8")));
    }

    // ---------------------------------------------------------------------

    private long create(String title, boolean published) throws Exception {
        String json = mockMvc.perform(post("/api/properties")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(title, published)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private static String body(String title, boolean published) {
        return """
               {"title":"%s","type":"RESIDENTIAL","location":"New Cairo","published":%s}
               """.formatted(title, published);
    }

    /** Every id on every page of a listing, optionally as {@code bearer}. */
    private List<Long> walkIds(String path, String bearer) throws Exception {
        List<Long> ids = new ArrayList<>();
        int totalPages;
        int page = 0;
        do {
            var request = get(path).param("page", String.valueOf(page)).param("size", "50");
            if (bearer != null) {
                request.header(HttpHeaders.AUTHORIZATION, bearer);
            }
            JsonNode body = objectMapper.readTree(mockMvc.perform(request)
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            body.get("content").forEach(item -> ids.add(item.get("id").asLong()));
            totalPages = body.get("totalPages").asInt();
            page++;
        } while (page < totalPages);
        return ids;
    }
}
