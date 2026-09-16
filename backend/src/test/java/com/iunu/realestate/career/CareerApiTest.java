package com.iunu.realestate.career;

import com.iunu.realestate.service.impl.CareerServiceImpl;
import com.iunu.realestate.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("POST /api/careers")
class CareerApiTest extends IntegrationTest {

    private static final byte[] PDF = "%PDF-1.4 real enough".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("accepts a complete application with a real PDF")
    void acceptsValidApplication() throws Exception {
        mockMvc.perform(multipart("/api/careers")
                        .file(new MockMultipartFile("resume", "cv.pdf", MediaType.APPLICATION_PDF_VALUE, PDF))
                        .param("fullName", "Nadia Fahmy")
                        .param("email", "nadia@example.com")
                        .param("phone", "+20 100 000 0000")
                        .param("position", "Architect")
                        .param("message", "I would like to join the team."))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("accepts an application with no CV attached")
    void acceptsApplicationWithoutResume() throws Exception {
        mockMvc.perform(multipart("/api/careers")
                        .param("fullName", "Nadia Fahmy")
                        .param("email", "nadia@example.com")
                        .param("phone", "+20 100 000 0000")
                        .param("position", "Architect")
                        .param("message", "No CV yet."))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("a missing required field is a field-level 400, never a 500")
    void missingFieldIsBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/careers")
                        .param("email", "nadia@example.com")
                        .param("phone", "+20 100 000 0000")
                        .param("position", "Architect")
                        .param("message", "Missing my name."))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("fullName"));
    }

    @Test
    @DisplayName("rejects a malformed email address, which the old hasText check let through")
    void rejectsMalformedEmail() throws Exception {
        mockMvc.perform(multipart("/api/careers")
                        .param("fullName", "Nadia Fahmy")
                        .param("email", "not-an-email")
                        .param("phone", "+20 100 000 0000")
                        .param("position", "Architect")
                        .param("message", "Hello."))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
    }

    @Test
    @DisplayName("rejects a non-PDF that claims application/pdf")
    void rejectsSpoofedPdf() throws Exception {
        mockMvc.perform(multipart("/api/careers")
                        .file(new MockMultipartFile("resume", "cv.pdf", MediaType.APPLICATION_PDF_VALUE,
                                "MZ this is an executable".getBytes(StandardCharsets.UTF_8)))
                        .param("fullName", "Nadia Fahmy")
                        .param("email", "nadia@example.com")
                        .param("phone", "+20 100 000 0000")
                        .param("position", "Architect")
                        .param("message", "Hello."))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("a traversal filename is reduced to a plain attachment name")
    void sanitizesAttachmentName() {
        assertThat(CareerServiceImpl.safeAttachmentName("../../../../etc/passwd")).isEqualTo("passwd");
        assertThat(CareerServiceImpl.safeAttachmentName("..\\..\\windows\\system32\\cmd.exe")).isEqualTo("cmd.exe");
        assertThat(CareerServiceImpl.safeAttachmentName("my cv (final).pdf")).isEqualTo("my_cv__final_.pdf");
        assertThat(CareerServiceImpl.safeAttachmentName("../..")).isEqualTo("cv.pdf");
        assertThat(CareerServiceImpl.safeAttachmentName(null)).isEqualTo("cv.pdf");
    }
}
