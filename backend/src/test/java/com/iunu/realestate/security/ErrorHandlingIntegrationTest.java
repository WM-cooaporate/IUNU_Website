package com.iunu.realestate.security;

import com.iunu.realestate.exception.GlobalExceptionHandler;
import com.iunu.realestate.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every kind of malformed input a scanner sends, against every endpoint that
 * takes input, answered with a 4xx and a body that says nothing about the
 * implementation.
 *
 * <p>Started life as the reproduction for the ZAP scan of September 2026,
 * where the API answered ~35% of fuzzed requests with a 5xx. Each case is a
 * separate dynamic test, so a regression names the exact request.
 */
@DisplayName("Error handling: malformed input is a clean 4xx")
class ErrorHandlingIntegrationTest extends IntegrationTest {

    /** Anything that would tell a caller what the server is built from. */
    private static final List<String> LEAKS = List.of("Exception", "at org.", "SQL", "java.", "springframework",
            "hibernate", "jackson", "com.iunu");

    private static final String LONG = "x".repeat(10_001);

    @Autowired private GlobalExceptionHandler exceptionHandler;

    private String admin;
    private String user;

    @BeforeEach
    void tokens() {
        admin = adminBearer();
        user = userBearer();
    }

    private record Case(String name, MockHttpServletRequestBuilder request, int expected) {
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, admin);
    }

    /** JSON endpoints: path, whether they need the admin token, and a body that is otherwise valid. */
    private List<Case> jsonBodyCases() {
        record Endpoint(HttpMethod method, String path, boolean admin, String validBody, String stringField,
                        boolean hasRequiredFields) {
            Endpoint(HttpMethod method, String path, boolean admin, String validBody, String stringField) {
                this(method, path, admin, validBody, stringField, true);
            }
        }
        List<Endpoint> endpoints = List.of(
                new Endpoint(HttpMethod.POST, "/api/auth/register", false,
                        "{\"fullName\":\"A\",\"email\":\"a@b.co\",\"phone\":\"+20 100 000 0000\",\"password\":\"Password1\"}",
                        "fullName"),
                new Endpoint(HttpMethod.POST, "/api/auth/login", false,
                        "{\"email\":\"nobody@iunu.test\",\"password\":\"Password1\"}", "password"),
                new Endpoint(HttpMethod.POST, "/api/auth/refresh", false, "{\"refreshToken\":\"abc\"}", "refreshToken"),
                new Endpoint(HttpMethod.POST, "/api/auth/logout", false, "{\"refreshToken\":\"abc\"}", "refreshToken"),
                new Endpoint(HttpMethod.POST, "/api/auth/forgot-password", false, "{\"email\":\"a@b.co\"}", "email"),
                new Endpoint(HttpMethod.POST, "/api/auth/reset-password", false,
                        "{\"token\":\"abc\",\"newPassword\":\"Password1\"}", "token"),
                new Endpoint(HttpMethod.POST, "/api/contact", false,
                        "{\"firstName\":\"A\",\"lastName\":\"B\",\"phone\":\"+20 100 000 0000\",\"email\":\"a@b.co\",\"message\":\"hi\"}",
                        "firstName"),
                new Endpoint(HttpMethod.POST, "/api/quotes", false,
                        "{\"name\":\"A\",\"phone\":\"+20 100 000 0000\",\"city\":\"Cairo\",\"email\":\"a@b.co\","
                                + "\"project\":\"residential\",\"spaceType\":\"villa\"}",
                        "city"),
                new Endpoint(HttpMethod.POST, "/api/newsletter", false, "{\"email\":\"a@b.co\"}", "email"),
                new Endpoint(HttpMethod.POST, "/api/admin/users", true,
                        "{\"fullName\":\"A\",\"email\":\"a@b.co\",\"password\":\"Password1\"}", "fullName"),
                new Endpoint(HttpMethod.POST, "/api/admin/projects", true, "{\"title\":\"T\"}", "title"),
                new Endpoint(HttpMethod.PUT, "/api/admin/projects/999999", true, "{\"title\":\"T\"}", "title"),
                // Every field is optional: an empty preview is a valid (empty) request.
                new Endpoint(HttpMethod.POST, "/api/admin/translations/preview", true, "{\"title\":\"T\"}", "title", false),
                new Endpoint(HttpMethod.POST, "/api/properties", true, "{\"title\":\"T\",\"type\":\"RESIDENTIAL\"}", "title"),
                new Endpoint(HttpMethod.PUT, "/api/properties/999999", true, "{\"title\":\"T\",\"type\":\"RESIDENTIAL\"}", "title"));

        List<Case> cases = new ArrayList<>();
        for (Endpoint endpoint : endpoints) {
            String label = endpoint.method() + " " + endpoint.path() + ": ";
            java.util.function.Function<MockHttpServletRequestBuilder, MockHttpServletRequestBuilder> auth =
                    builder -> endpoint.admin() ? asAdmin(builder) : builder;
            java.util.function.Supplier<MockHttpServletRequestBuilder> base =
                    () -> auth.apply(request(endpoint.method(), endpoint.path()));

            cases.add(new Case(label + "malformed JSON", json(base.get(), "{\"a\": "), 400));
            cases.add(new Case(label + "not an object", json(base.get(), "[1,2,3]"), 400));
            cases.add(new Case(label + "JSON null", json(base.get(), "null"), 400));
            cases.add(new Case(label + "empty body", base.get().contentType(MediaType.APPLICATION_JSON), 400));
            cases.add(new Case(label + "missing required fields", json(base.get(), "{}"), endpoint.hasRequiredFields() ? 400 : 200));
            cases.add(new Case(label + "object where a string belongs",
                    json(base.get(), "{\"" + endpoint.stringField() + "\":{\"$gt\":\"\"}}"), 400));
            cases.add(new Case(label + "oversized string",
                    json(base.get(), endpoint.validBody().replaceFirst(
                            "\"" + endpoint.stringField() + "\":\"[^\"]*\"",
                            "\"" + endpoint.stringField() + "\":\"" + LONG + "\"")), 400));
            cases.add(new Case(label + "deeply nested JSON",
                    json(base.get(), "[".repeat(5000) + "]".repeat(5000)), 400));
            cases.add(new Case(label + "text/plain body",
                    base.get().contentType(MediaType.TEXT_PLAIN).content(endpoint.validBody()), 415));
            cases.add(new Case(label + "XML body",
                    base.get().contentType(MediaType.APPLICATION_XML).content("<a>1</a>"), 415));
            cases.add(new Case(label + "unparseable Content-Type",
                    base.get().header(HttpHeaders.CONTENT_TYPE, "garbage").content(endpoint.validBody()), 415));
        }

        // Wrong field types that are specific to one DTO.
        cases.add(new Case("POST /api/properties: price is a string",
                asAdmin(json(post("/api/properties"), "{\"title\":\"T\",\"type\":\"RESIDENTIAL\",\"price\":\"abc\"}")), 400));
        cases.add(new Case("POST /api/properties: unknown enum",
                asAdmin(json(post("/api/properties"), "{\"title\":\"T\",\"type\":\"CASTLE\"}")), 400));
        cases.add(new Case("POST /api/properties: imageUrls is a string",
                asAdmin(json(post("/api/properties"), "{\"title\":\"T\",\"type\":\"RESIDENTIAL\",\"imageUrls\":\"x\"}")), 400));
        cases.add(new Case("POST /api/properties: published is not a boolean",
                asAdmin(json(post("/api/properties"), "{\"title\":\"T\",\"type\":\"RESIDENTIAL\",\"published\":\"maybe\"}")), 400));
        cases.add(new Case("POST /api/properties: price beyond the column (1e20)",
                asAdmin(json(post("/api/properties"), "{\"title\":\"T\",\"type\":\"RESIDENTIAL\",\"price\":1e20}")), 400));
        cases.add(new Case("POST /api/properties: area beyond the column (1e15)",
                asAdmin(json(post("/api/properties"), "{\"title\":\"T\",\"type\":\"RESIDENTIAL\",\"area\":1e15}")), 400));
        cases.add(new Case("POST /api/properties: price with an absurd exponent",
                asAdmin(json(post("/api/properties"), "{\"title\":\"T\",\"type\":\"RESIDENTIAL\",\"price\":1E+999999999}")), 400));
        cases.add(new Case("POST /api/properties: price with too many decimals",
                asAdmin(json(post("/api/properties"), "{\"title\":\"T\",\"type\":\"RESIDENTIAL\",\"price\":1.123456}")), 400));
        cases.add(new Case("POST /api/admin/projects: unknown status",
                asAdmin(json(post("/api/admin/projects"), "{\"title\":\"T\",\"status\":\"MAYBE\"}")), 400));
        cases.add(new Case("POST /api/auth/login: very long password",
                json(post("/api/auth/login"), "{\"email\":\"a@b.co\",\"password\":\"" + LONG + "\"}"), 400));
        cases.add(new Case("POST /api/auth/login: email is a number",
                json(post("/api/auth/login"), "{\"email\":123,\"password\":\"Password1\"}"), 400));
        cases.add(new Case("POST /api/auth/change-password: very long current password",
                json(post("/api/auth/change-password"), "{\"currentPassword\":\"" + LONG + "\",\"newPassword\":\"Password1\"}")
                        .header(HttpHeaders.AUTHORIZATION, user), 400));
        cases.add(new Case("POST /api/auth/change-password: malformed JSON",
                json(post("/api/auth/change-password"), "{").header(HttpHeaders.AUTHORIZATION, user), 400));
        cases.add(new Case("POST /api/newsletter: very long email",
                json(post("/api/newsletter"), "{\"email\":\"" + "a".repeat(300) + "@b.co\"}"), 400));
        cases.add(new Case("POST /api/contact: charset nobody has heard of",
                post("/api/contact").header(HttpHeaders.CONTENT_TYPE, "application/json;charset=nope").content("{}"), 415));
        return cases;
    }

    private List<Case> pathAndQueryCases() {
        List<Case> cases = new ArrayList<>();
        for (String id : List.of("abc", "1.5", "99999999999999999999999", "%00", "' OR 1=1--")) {
            cases.add(new Case("GET /api/projects/" + id, get("/api/projects/{id}", id), 400));
            cases.add(new Case("GET /api/properties/" + id, get("/api/properties/{id}", id), 400));
            cases.add(new Case("GET /api/properties/admin/" + id, asAdmin(get("/api/properties/admin/{id}", id)), 400));
            cases.add(new Case("GET /api/admin/projects/" + id, asAdmin(get("/api/admin/projects/{id}", id)), 400));
            cases.add(new Case("DELETE /api/admin/projects/" + id, asAdmin(delete("/api/admin/projects/{id}", id)), 400));
            cases.add(new Case("DELETE /api/properties/" + id, asAdmin(delete("/api/properties/{id}", id)), 400));
            cases.add(new Case("PATCH /api/admin/contacts/" + id + "/handled",
                    asAdmin(patch("/api/admin/contacts/{id}/handled", id)), 400));
            cases.add(new Case("PATCH /api/admin/quotes/" + id + "/handled",
                    asAdmin(patch("/api/admin/quotes/{id}/handled", id)), 400));
        }
        cases.add(new Case("GET /api/projects/-1 (no such row)", get("/api/projects/-1"), 404));
        cases.add(new Case("GET /api/properties/0 (no such row)", get("/api/properties/0"), 404));
        cases.add(new Case("GET /api/admin/projects/999999 (no such row)", asAdmin(get("/api/admin/projects/999999")), 404));
        cases.add(new Case("PATCH /api/admin/contacts/999999/handled (no such row)",
                asAdmin(patch("/api/admin/contacts/999999/handled")), 404));

        cases.add(new Case("GET /api/properties?type=NOPE", get("/api/properties").param("type", "NOPE"), 400));
        cases.add(new Case("GET /api/properties?sort=password", get("/api/properties").param("sort", "password"), 400));
        // "sideways" is not a direction, so Spring Data reads it as a second property.
        cases.add(new Case("GET /api/properties?sort=title,sideways", get("/api/properties").param("sort", "title,sideways"), 400));
        cases.add(new Case("GET /api/properties?page=abc&size=abc", get("/api/properties").param("page", "abc").param("size", "abc"), 200));
        cases.add(new Case("GET /api/projects?sort=')--", get("/api/projects").param("sort", "')--"), 400));
        cases.add(new Case("GET /api/admin/audit-log?page=abc", asAdmin(get("/api/admin/audit-log").param("page", "abc")), 400));
        cases.add(new Case("GET /api/admin/audit-log?size=99999999999", asAdmin(get("/api/admin/audit-log").param("size", "99999999999")), 400));
        cases.add(new Case("GET /api/admin/audit-log?action=NOPE", asAdmin(get("/api/admin/audit-log").param("action", "NOPE")), 400));
        cases.add(new Case("POST /api/admin/images/sweep?dryRun=maybe", asAdmin(post("/api/admin/images/sweep").param("dryRun", "maybe")), 400));
        return cases;
    }

    private List<Case> methodMediaAndRoutingCases() {
        List<Case> cases = new ArrayList<>();
        // Wrong verb on a route that exists: 405. Anonymous callers still get
        // the 401 the security rules give any non-public route - that is the
        // intended answer, not a leak of what exists.
        cases.add(new Case("PUT /api/auth/login", json(put("/api/auth/login"), "{}"), 405));
        cases.add(new Case("GET /api/auth/login", get("/api/auth/login"), 405));
        cases.add(new Case("DELETE /api/contact (signed in)", delete("/api/contact").header(HttpHeaders.AUTHORIZATION, user), 405));
        cases.add(new Case("PATCH /api/admin/projects", asAdmin(json(patch("/api/admin/projects"), "{}")), 405));
        cases.add(new Case("PUT /api/admin/users", asAdmin(json(put("/api/admin/users"), "{}")), 405));
        cases.add(new Case("DELETE /api/contact (anonymous)", delete("/api/contact"), 401));

        // Asking for a representation the API does not produce.
        // Validation runs before content negotiation, so this is the 400 -
        // written as application/problem+json whatever the caller accepts.
        cases.add(new Case("POST /api/contact Accept: application/xml",
                json(post("/api/contact"), "{}").accept(MediaType.APPLICATION_XML), 400));
        cases.add(new Case("GET /api/properties Accept: text/html",
                get("/api/properties").accept(MediaType.TEXT_HTML), 406));
        cases.add(new Case("GET /api/projects/abc Accept: application/xml",
                get("/api/projects/abc").accept(MediaType.APPLICATION_XML), 400));

        // Unknown routes.
        cases.add(new Case("GET /api/does-not-exist (admin)", asAdmin(get("/api/does-not-exist")), 404));
        cases.add(new Case("POST /api/admin/does-not-exist (admin)", asAdmin(json(post("/api/admin/does-not-exist"), "{}")), 404));
        cases.add(new Case("GET /api/does-not-exist (anonymous)", get("/api/does-not-exist"), 401));
        cases.add(new Case("GET /api/auth/does-not-exist (public prefix)", get("/api/auth/does-not-exist"), 404));
        cases.add(new Case("GET /api/projects/1/extra (public prefix)", get("/api/projects/1/extra"), 404));

        // Multipart endpoints.
        cases.add(new Case("POST /api/properties/images without files",
                asAdmin(multipart("/api/properties/images").param("x", "y")), 400));
        cases.add(new Case("POST /api/properties/images as JSON",
                asAdmin(json(post("/api/properties/images"), "{}")), 415));
        cases.add(new Case("POST /api/properties/images with a non-image",
                asAdmin(multipart("/api/properties/images").file(
                        new MockMultipartFile("files", "a.png", "image/png", "not an image".getBytes()))), 400));
        cases.add(new Case("POST /api/admin/projects/abc/cover-image",
                asAdmin(multipart("/api/admin/projects/abc/cover-image").file(
                        new MockMultipartFile("file", "a.png", "image/png", pngBytes()))), 400));
        cases.add(new Case("POST /api/admin/projects/999999/cover-image without file",
                asAdmin(multipart("/api/admin/projects/999999/cover-image")), 400));
        cases.add(new Case("POST /api/careers as JSON", json(post("/api/careers"), "{}"), 415));
        cases.add(new Case("POST /api/careers with no fields", multipart("/api/careers"), 400));
        cases.add(new Case("POST /api/careers with an oversized message",
                multipart("/api/careers").param("fullName", "A").param("email", "a@b.co")
                        .param("phone", "+20 100 000 0000").param("position", "P").param("message", LONG), 400));
        cases.add(new Case("POST /api/careers with a bad email",
                multipart("/api/careers").param("fullName", "A").param("email", "not-an-email")
                        .param("phone", "+20 100 000 0000").param("position", "P").param("message", "m"), 400));
        return cases;
    }

    @TestFactory
    @DisplayName("every malformed request is answered with the expected 4xx and a clean body")
    Stream<DynamicTest> malformedRequests() {
        return Stream.of(jsonBodyCases(), pathAndQueryCases(), methodMediaAndRoutingCases())
                .flatMap(List::stream)
                .map(testCase -> DynamicTest.dynamicTest(testCase.name(), () -> {
                    MockHttpServletResponse response = mockMvc.perform(testCase.request()).andReturn().getResponse();
                    String body = response.getContentAsString();
                    assertThat(response.getStatus())
                            .as("%s -> body %s", testCase.name(), body)
                            .isEqualTo(testCase.expected());
                    assertClean(body);
                }));
    }

    @Test
    @DisplayName("errors are RFC 7807 problem details that keep the fields the dashboard reads")
    void problemDetailShape() throws Exception {
        mockMvc.perform(json(post("/api/contact"), "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.instance").value("/api/contact"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')].message").value("Email is required"));
    }

    @Test
    @DisplayName("a wrong-typed JSON field gets a fixed message, not the parser's text")
    void wrongTypeMessageIsFixed() throws Exception {
        mockMvc.perform(json(post("/api/properties"), "{\"title\":\"T\",\"type\":\"RESIDENTIAL\",\"price\":\"abc\"}")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(GlobalExceptionHandler.MALFORMED_BODY));
    }

    @Test
    @DisplayName("405 and 415 say what the route does accept")
    void methodAndMediaTypeHeaders() throws Exception {
        mockMvc.perform(put("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("POST")));
        mockMvc.perform(post("/api/contact").contentType(MediaType.TEXT_PLAIN).content("hi"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string(HttpHeaders.ACCEPT, containsString("application/json")));
    }

    /**
     * Nothing reachable over HTTP still throws something unplanned, which is
     * the point - so the catch-all is driven directly, with an exception
     * whose message is exactly what must never reach a caller.
     */
    @Test
    @DisplayName("an unexpected exception is a 500 with only a correlation id, which is the request id")
    void unexpectedExceptionReturnsOnlyACorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/anything");
        MDC.put(RequestIdFilter.MDC_KEY, "4f9d7c1e-0000-4000-8000-000000000001");
        try {
            ResponseEntity<ProblemDetail> response = exceptionHandler.handleUnexpected(
                    new IllegalStateException("SELECT password FROM users at org.hibernate.Foo java.sql.SQLException"),
                    request);

            assertThat(response.getStatusCode().value()).isEqualTo(500);
            ProblemDetail body = response.getBody();
            assertThat(body.getDetail()).isEqualTo("Unexpected error");
            assertThat(body.getProperties()).containsEntry("correlationId", "4f9d7c1e-0000-4000-8000-000000000001");
            assertClean(objectMapper.writeValueAsString(body));
        } finally {
            MDC.remove(RequestIdFilter.MDC_KEY);
        }
    }

    static void assertClean(String body) {
        for (String leak : LEAKS) {
            assertThat(body).as("response body must not contain \"%s\"", leak).doesNotContain(leak);
        }
    }
}
