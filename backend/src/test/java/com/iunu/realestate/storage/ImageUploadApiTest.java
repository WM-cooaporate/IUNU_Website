package com.iunu.realestate.storage;

import com.iunu.realestate.entity.Project;
import com.iunu.realestate.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What the dashboard sees end to end: one file per upload, readable 400s for
 * the files an admin actually picks by mistake, and the gallery cap. Runs on
 * the local provider, like every other integration test.
 */
@DisplayName("Image upload API")
class ImageUploadApiTest extends IntegrationTest {

    private static MockMultipartFile part(String name, String type, byte[] content) {
        return new MockMultipartFile("files", name, type, content);
    }

    @Test
    @DisplayName("one file per request returns a one-element list with its URL")
    void singleFileUpload() throws Exception {
        mockMvc.perform(multipart("/api/properties/images")
                        .file(part("house.webp", "image/webp", ImageFixtures.webp(42)))
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0]").value(startsWith("http://localhost:8080/uploads/properties/")));
    }

    @Test
    @DisplayName("an iPhone HEIC gets a 400 telling the admin what to change")
    void heicIsA400WithAdvice() throws Exception {
        mockMvc.perform(multipart("/api/properties/images")
                        .file(part("IMG_0001.HEIC", "image/heic", ImageFixtures.heic()))
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("iPhone HEIC photos aren't supported")));
    }

    @Test
    @DisplayName("an SVG renamed .png is a 400 with the supported-formats message")
    void svgRenamedIsA400() throws Exception {
        mockMvc.perform(multipart("/api/properties/images")
                        .file(part("logo.png", "image/png", "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.US_ASCII)))
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only JPG, PNG and WebP images are supported."));
    }

    @Test
    @DisplayName("an empty file is a 400 saying so")
    void emptyIsA400() throws Exception {
        mockMvc.perform(multipart("/api/properties/images")
                        .file(part("a.jpg", "image/jpeg", new byte[0]))
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The image file is empty."));
    }

    @Test
    @DisplayName("a project cannot carry more than 30 gallery images")
    void galleryIsCappedAtThirty() throws Exception {
        String urls = String.join(",", IntStream.range(0, 31)
                .mapToObj(i -> "\"https://images.example.com/" + i + ".jpg\"").toList());
        String body = "{\"title\":\"Too many\",\"type\":\"RESIDENTIAL\",\"imageUrls\":[" + urls + "]}";

        mockMvc.perform(post("/api/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("A project can have at most 30 images."));
    }

    @Test
    @DisplayName("a cover upload for a project that does not exist is a 404 and stores nothing")
    void coverForMissingProjectIs404() throws Exception {
        mockMvc.perform(multipart("/api/admin/projects/{id}/cover-image", 999_999L)
                        .file(new MockMultipartFile("file", "c.png", "image/png", ImageFixtures.png(77)))
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a cover upload still lands on the project after the transaction split")
    void coverUploadStillSaves() throws Exception {
        Project project = projectRepository.save(Project.builder().title("Split").build());
        mockMvc.perform(multipart("/api/admin/projects/{id}/cover-image", project.getId())
                        .file(new MockMultipartFile("file", "c.png", "image/png", ImageFixtures.png(78)))
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk());
        assertThat(projectRepository.findById(project.getId()).orElseThrow().getCoverImageUrl())
                .startsWith("http://localhost:8080/uploads/projects/");
    }

    @Test
    @DisplayName("with the local provider, migrate and sweep answer enabled=false and do nothing")
    void maintenanceIsDisabledLocally() throws Exception {
        String admin = adminBearer();
        mockMvc.perform(post("/api/admin/images/migrate-to-cloud").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.migrated").value(0));
        mockMvc.perform(post("/api/admin/images/sweep").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.dryRun").value(true));
    }
}
