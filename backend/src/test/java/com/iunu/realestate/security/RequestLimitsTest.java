package com.iunu.realestate.security;

import com.iunu.realestate.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The limits that stop one request from costing what a thousand should.
 *
 * <p>An uncapped page size and an unbounded request body are the two cheapest
 * denial-of-service tools a public API hands out, and neither looks like an
 * attack in a log - they are ordinary requests with one parameter changed.
 */
@DisplayName("Request limits")
class RequestLimitsTest extends IntegrationTest {

    @Test
    @DisplayName("an enormous page size is clamped rather than honoured")
    void pageSizeIsCapped() throws Exception {
        mockMvc.perform(get("/api/properties").param("size", "100000"))
                .andExpect(status().isOk())
                // The cap is what matters, not the exact count: without it this
                // one request selects the entire table and serialises it.
                .andExpect(jsonPath("$.content.length()").value(lessThanOrEqualTo(50)))
                .andExpect(jsonPath("$.size").value(lessThanOrEqualTo(50)));
    }

    @Test
    @DisplayName("the cap applies to the admin listing too")
    void adminPageSizeIsCapped() throws Exception {
        mockMvc.perform(get("/api/properties/admin")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, adminBearer())
                        .param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(lessThanOrEqualTo(50)));
    }

    @Test
    @DisplayName("a negative page number does not produce a 500")
    void negativePageIsHandled() throws Exception {
        mockMvc.perform(get("/api/properties").param("page", "-1"))
                .andExpect(status().isOk());
    }

    /**
     * Checked against Content-Length before the stream is read. The @Size
     * constraints on the DTO cannot do this job: they run after Jackson has
     * already parsed the whole body into objects.
     */
    @Test
    @DisplayName("a JSON body over 1MB is refused with 413 in the standard error shape")
    void oversizedJsonBodyIsRejected() throws Exception {
        String hugeBody = "{\"name\":\"" + "A".repeat(1_200_000) + "\"}";

        mockMvc.perform(post("/api/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hugeBody))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.error").value("Payload Too Large"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                // No stack trace, no exception class name.
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    @DisplayName("a body just under the limit is not refused by the size filter")
    void bodyUnderTheLimitIsNotRejected() throws Exception {
        // Well-formed but invalid content: the point is that it reaches
        // validation (400) rather than being cut off at the size filter (413).
        mockMvc.perform(post("/api/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "A".repeat(1000) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * The multipart cap itself is enforced by the servlet container, which
     * MockMvc does not run - a MockMultipartFile is handed to the controller
     * already parsed, so no size check ever fires. What is testable, and what
     * actually decides what the caller sees, is the mapping from the
     * container's exception to a response: without it this is a 500 and the
     * frontend's 413 message never appears.
     */
    @Test
    @DisplayName("an oversized upload surfaces as 413 in the standard error shape")
    void oversizedUploadMapsTo413() {
        var request = new org.springframework.mock.web.MockHttpServletRequest(
                "POST", "/api/properties/images");

        var response = new com.iunu.realestate.exception.GlobalExceptionHandler()
                .handleUploadTooLarge(new MaxUploadSizeExceededException(5 * 1024 * 1024), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(413);
        assertThat(response.getBody().error()).isEqualTo("Payload Too Large");
        // The message the admin sees must not name a class, a limit source or
        // a path on disk.
        assertThat(response.getBody().message()).doesNotContain("Exception").doesNotContain("org.spring");
    }
}
