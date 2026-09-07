package com.iunu.realestate.property;

import com.iunu.realestate.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The end-to-end contract behind "I added a project and it never showed up on
 * the site": a project created through the admin API must be readable on the
 * public endpoint the website actually fetches.
 *
 * The reported bug turned out to be client-side (the dashboard's demo mode
 * never called the API at all), so these tests passed before the fix too.
 * They are here to keep the server half of that path honest - an admin write
 * and a public read must stay pointed at the same rows.
 */
@DisplayName("Admin-created project -> public visibility")
class AdminToPublicVisibilityTest extends IntegrationTest {

    private static final String PUBLIC_TITLES = "$.content[*].title";

    private String createProject(String title, Boolean published) throws Exception {
        String body = published == null
                ? """
                  {"title":"%s","type":"RESIDENTIAL","location":"New Cairo"}
                  """.formatted(title)
                : """
                  {"title":"%s","type":"RESIDENTIAL","location":"New Cairo","published":%s}
                  """.formatted(title, published);

        return mockMvc.perform(post("/api/properties")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                // A real persisted row, not an echo of the request.
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("a project created with published=true is immediately on the public endpoint")
    void publishedOnCreateIsPubliclyVisible() throws Exception {
        String title = "Visible-" + System.nanoTime();
        createProject(title, true);

        // Exactly what the public Projects page and the homepage portfolio fetch.
        mockMvc.perform(get("/api/properties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(PUBLIC_TITLES, hasItem(title)));
    }

    @Test
    @DisplayName("a draft stays hidden until it is published, then appears")
    void draftBecomesVisibleOnPublish() throws Exception {
        String title = "Draft-" + System.nanoTime();
        String created = createProject(title, false);
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(get("/api/properties"))
                .andExpect(jsonPath(PUBLIC_TITLES, not(hasItem(title))));

        mockMvc.perform(put("/api/properties/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"title":"%s","type":"RESIDENTIAL","location":"New Cairo","published":true}
                                 """.formatted(title)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true));

        mockMvc.perform(get("/api/properties"))
                .andExpect(jsonPath(PUBLIC_TITLES, hasItem(title)));
    }

    /**
     * The admin form omits `published` only if the checkbox is removed; today it
     * always sends it. Pinning the server-side default documents which way an
     * omitted flag falls, so a future form change cannot quietly create
     * invisible projects.
     */
    @Test
    @DisplayName("omitting published on create defaults to published, not draft")
    void omittedPublishedDefaultsToPublished() throws Exception {
        String title = "Default-" + System.nanoTime();
        createProject(title, null);

        mockMvc.perform(get("/api/properties"))
                .andExpect(jsonPath(PUBLIC_TITLES, hasItem(title)));
    }

    @Test
    @DisplayName("the admin listing and the public listing read the same rows")
    void adminAndPublicShareTheSameSource() throws Exception {
        String title = "Shared-" + System.nanoTime();
        createProject(title, true);

        mockMvc.perform(get("/api/properties/admin")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(PUBLIC_TITLES, hasItem(title)));

        mockMvc.perform(get("/api/properties"))
                .andExpect(jsonPath(PUBLIC_TITLES, hasItem(title)));
    }
}
