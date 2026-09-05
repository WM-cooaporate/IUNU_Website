package com.iunu.realestate.project;

import com.iunu.realestate.entity.Project;
import com.iunu.realestate.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("/api/admin/projects")
class AdminProjectApiTest extends IntegrationTest {

    @BeforeEach
    void clean() {
        projectRepository.deleteAll();
    }

    // --- Authorization -----------------------------------------------------

    @Test
    @DisplayName("401s without a token")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/projects")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("403s for an authenticated non-admin")
    void forbidsNonAdmins() throws Exception {
        mockMvc.perform(get("/api/admin/projects").header(HttpHeaders.AUTHORIZATION, userBearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("403s on a garbage token rather than trusting it")
    void rejectsForgedToken() throws Exception {
        mockMvc.perform(get("/api/admin/projects").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    // --- CRUD --------------------------------------------------------------

    @Test
    @DisplayName("creates a draft by default and keeps it out of the public listing")
    void createsDraftByDefault() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "title", "Nile Tower",
                "description", "Mixed-use development",
                "location", "New Cairo"));

        String created = mockMvc.perform(post("/api/admin/projects")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.title").value("Nile Tower"))
                .andExpect(jsonPath("$.location").value("New Cairo"))
                .andReturn().getResponse().getContentAsString();

        assertThat(created).doesNotContain("password");

        mockMvc.perform(get("/api/projects"))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("honours published=true on create")
    void createsPublishedWhenAsked() throws Exception {
        mockMvc.perform(post("/api/admin/projects")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Live", "published", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.published").value(true));

        mockMvc.perform(get("/api/projects")).andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    @DisplayName("publishing via PUT makes a draft publicly visible")
    void publishTogglesPublicVisibility() throws Exception {
        Project draft = projectRepository.save(Project.builder().title("Draft").published(false).build());

        mockMvc.perform(get("/api/projects/{id}", draft.getId())).andExpect(status().isNotFound());

        mockMvc.perform(put("/api/admin/projects/{id}", draft.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Draft", "published", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true));

        mockMvc.perform(get("/api/projects/{id}", draft.getId())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("omitting published on update leaves the publish state alone")
    void updateWithoutPublishedKeepsState() throws Exception {
        Project live = projectRepository.save(Project.builder().title("Live").published(true).build());

        mockMvc.perform(put("/api/admin/projects/{id}", live.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Live renamed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Live renamed"))
                .andExpect(jsonPath("$.published").value(true));
    }

    @Test
    @DisplayName("admin listing includes drafts; delete removes the project")
    void listsDraftsAndDeletes() throws Exception {
        Project draft = projectRepository.save(Project.builder().title("Draft").published(false).build());
        String admin = adminBearer();

        mockMvc.perform(get("/api/admin/projects").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].published").value(false));

        mockMvc.perform(delete("/api/admin/projects/{id}", draft.getId())
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());

        assertThat(projectRepository.existsById(draft.getId())).isFalse();
    }

    @Test
    @DisplayName("404s when updating or deleting a project that does not exist")
    void missingProjectIs404() throws Exception {
        String admin = adminBearer();
        mockMvc.perform(delete("/api/admin/projects/{id}", 424242L)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
    }

    // --- Validation --------------------------------------------------------

    @Test
    @DisplayName("rejects a blank title with a field-level 400")
    void rejectsBlankTitle() throws Exception {
        mockMvc.perform(post("/api/admin/projects")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "  "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"));
    }

    // --- Cover image -------------------------------------------------------

    @Test
    @DisplayName("stores an uploaded cover image and saves its URL on the project")
    void uploadsCoverImage() throws Exception {
        Project project = projectRepository.save(Project.builder().title("With cover").build());

        MockMultipartFile file = new MockMultipartFile(
                "file", "cover.png", MediaType.IMAGE_PNG_VALUE, "fake-png-bytes".getBytes());

        mockMvc.perform(multipart("/api/admin/projects/{id}/cover-image", project.getId())
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverImageUrl").value(org.hamcrest.Matchers.startsWith(
                        "http://localhost:8080/uploads/projects/")))
                .andExpect(jsonPath("$.coverImageUrl").value(org.hamcrest.Matchers.endsWith(".png")))
                .andExpect(jsonPath("$.project.id").value(project.getId()))
                .andExpect(jsonPath("$.project.coverImageUrl").value(
                        org.hamcrest.Matchers.startsWith("http://localhost:8080/uploads/projects/")));

        assertThat(projectRepository.findById(project.getId()).orElseThrow().getCoverImageUrl())
                .startsWith("http://localhost:8080/uploads/projects/");
    }

    @Test
    @DisplayName("rejects a non-image upload with a 400, not a 500")
    void rejectsNonImageUpload() throws Exception {
        Project project = projectRepository.save(Project.builder().title("No cover").build());

        MockMultipartFile file = new MockMultipartFile(
                "file", "payload.pdf", MediaType.APPLICATION_PDF_VALUE, "not-an-image".getBytes());

        mockMvc.perform(multipart("/api/admin/projects/{id}/cover-image", project.getId())
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertThat(projectRepository.findById(project.getId()).orElseThrow().getCoverImageUrl()).isNull();
    }

    @Test
    @DisplayName("cover-image upload requires ROLE_ADMIN")
    void coverImageRequiresAdmin() throws Exception {
        Project project = projectRepository.save(Project.builder().title("Guarded").build());
        MockMultipartFile file = new MockMultipartFile(
                "file", "cover.png", MediaType.IMAGE_PNG_VALUE, "bytes".getBytes());

        mockMvc.perform(multipart("/api/admin/projects/{id}/cover-image", project.getId()).file(file))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(multipart("/api/admin/projects/{id}/cover-image", project.getId())
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, userBearer()))
                .andExpect(status().isForbidden());
    }
}
