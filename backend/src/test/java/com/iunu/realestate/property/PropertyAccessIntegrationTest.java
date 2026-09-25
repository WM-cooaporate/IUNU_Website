package com.iunu.realestate.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.iunu.realestate.repository.PropertyRepository;
import com.iunu.realestate.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who can read and write properties, end to end through the real security
 * chain.
 *
 * <p>The public site fetches {@code /api/properties} and
 * {@code /api/properties/{id}} without a token; those must answer anonymous
 * visitors with published rows only, in the public shape. Drafts are read
 * only through {@code /api/properties/admin/**}, and every write is ADMIN-only.
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

    @Test
    @DisplayName("anonymous list: 200, published rows only")
    void anonymousListShowsOnlyPublished() throws Exception {
        List<Long> ids = walkIds("/api/properties", null);

        assertThat(ids).contains(publishedId).doesNotContain(draftId);
        // Not just "not this draft": no draft of any test is on the public list.
        assertThat(propertyRepository.findAllById(ids))
                .hasSize(ids.size())
                .allMatch(property -> property.isPublished());
    }

    @Test
    @DisplayName("anonymous details of a draft: 404, not 403")
    void anonymousDraftByIdIsNotFound() throws Exception {
        mockMvc.perform(get("/api/properties/{id}", draftId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("anonymous details of a missing id: 404")
    void anonymousMissingByIdIsNotFound() throws Exception {
        mockMvc.perform(get("/api/properties/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("anonymous details of a published row: 200 with no internal fields")
    void anonymousPublishedByIdHasNoInternalFields() throws Exception {
        mockMvc.perform(get("/api/properties/{id}", publishedId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(publishedId))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.published").doesNotExist())
                .andExpect(jsonPath("$.createdAt").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist());

        // Same shape on the list.
        mockMvc.perform(get("/api/properties").param("size", "1"))
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

    @Test
    @DisplayName("an admin token on the public endpoints still gets published rows only")
    void adminTokenDoesNotWidenPublicEndpoints() throws Exception {
        assertThat(walkIds("/api/properties", admin)).contains(publishedId).doesNotContain(draftId);

        mockMvc.perform(get("/api/properties/{id}", draftId)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("size=100000 is clamped to 50")
    void pageSizeIsClamped() throws Exception {
        mockMvc.perform(get("/api/properties").param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50));
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
