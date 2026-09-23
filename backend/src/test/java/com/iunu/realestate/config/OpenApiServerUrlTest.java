package com.iunu.realestate.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * With forward-headers-strategy none, anything built from the request says
 * http:// behind Render. The OpenAPI server URL is the only such URL this app
 * produces, so it is pinned to PUBLIC_API_URL instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "app.file-storage.public-base-url=https://api.example.test/",
        "spring.datasource.url=jdbc:h2:mem:iunu-openapi;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;"
                + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("OpenAPI server URL")
class OpenApiServerUrlTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("comes from PUBLIC_API_URL, not from the request")
    void serverUrlIsConfigured() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        var servers = objectMapper.readTree(body).get("servers");
        assertThat(servers).hasSize(1);
        assertThat(servers.get(0).get("url").asText()).isEqualTo("https://api.example.test");
    }
}
