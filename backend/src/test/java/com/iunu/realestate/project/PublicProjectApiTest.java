package com.iunu.realestate.project;

import com.iunu.realestate.entity.Project;
import com.iunu.realestate.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("GET /api/projects (public, no auth)")
class PublicProjectApiTest extends IntegrationTest {

    private Long draftId;
    private Long newerPublishedId;

    @BeforeEach
    void seed() {
        projectRepository.deleteAll();

        Project older = projectRepository.save(Project.builder()
                .title("Older published").published(true).build());
        Project newer = projectRepository.save(Project.builder()
                .title("Newer published").published(true).build());
        Project draft = projectRepository.save(Project.builder()
                .title("Unpublished draft").published(false).build());

        // @CreationTimestamp gives near-identical instants inside one test, so
        // pin them explicitly to make the ordering assertion meaningful.
        older.setCreatedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        newer.setCreatedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        projectRepository.saveAll(java.util.List.of(older, newer));

        draftId = draft.getId();
        newerPublishedId = newer.getId();
    }

    @Test
    @DisplayName("returns only published projects, newest first, without a token")
    void listsPublishedNewestFirst() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].title").value("Newer published"))
                .andExpect(jsonPath("$.content[1].title").value("Older published"))
                .andExpect(jsonPath("$.content[0].published").value(true));
    }

    @Test
    @DisplayName("never exposes a draft in the public listing")
    void hidesDraftsFromListing() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.title == 'Unpublished draft')]").isEmpty());
    }

    @Test
    @DisplayName("returns a published project by id")
    void getsPublishedById() throws Exception {
        mockMvc.perform(get("/api/projects/{id}", newerPublishedId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(newerPublishedId))
                .andExpect(jsonPath("$.title").value("Newer published"));
    }

    @Test
    @DisplayName("404s on a draft, exactly as it would for a missing project")
    void draftIsIndistinguishableFromMissing() throws Exception {
        mockMvc.perform(get("/api/projects/{id}", draftId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));

        mockMvc.perform(get("/api/projects/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
