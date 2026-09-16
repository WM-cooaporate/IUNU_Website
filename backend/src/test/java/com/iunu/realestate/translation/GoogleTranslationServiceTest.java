package com.iunu.realestate.translation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The translator against a stubbed Google. Two properties matter more than the
 * happy path here: the API key must travel in the header and never in the URL,
 * and nothing the remote end does may throw - a save has to survive a 403.
 */
@DisplayName("Google translation service")
class GoogleTranslationServiceTest {

    private static final String BASE_URL = "https://translate.test";
    private static final String ENDPOINT = BASE_URL + "/language/translate/v2";

    private MockRestServiceServer server;

    private GoogleTranslationService serviceWith(String apiKey) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new GoogleTranslationService(
                builder, new GoogleTranslateProperties(apiKey, BASE_URL, 1000, 1000));
    }

    private static String responseWith(String... translations) {
        String items = Arrays.stream(translations)
                .map(text -> "{\"translatedText\":\"" + text + "\"}")
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        return "{\"data\":{\"translations\":[" + items + "]}}";
    }

    @Test
    @DisplayName("sends the key in X-goog-api-key, never in the URL, with source/target/format set")
    void sendsTheDocumentedRequest() {
        GoogleTranslationService service = serviceWith("secret-key");

        // requestTo() is an exact match, so a key smuggled into the query
        // string would fail this expectation rather than pass unnoticed.
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("X-goog-api-key", "secret-key"))
                .andExpect(jsonPath("$.source").value("en"))
                .andExpect(jsonPath("$.target").value("ar"))
                // Without format=text Google treats the input as HTML and
                // returns &#39; entities instead of apostrophes.
                .andExpect(jsonPath("$.format").value("text"))
                .andExpect(jsonPath("$.q[0]").value("New Cairo"))
                .andRespond(withSuccess(responseWith("القاهرة الجديدة"), MediaType.APPLICATION_JSON));

        assertThat(service.translateEnToAr(List.of("New Cairo"))).containsExactly("القاهرة الجديدة");
        server.verify();
    }

    @Test
    @DisplayName("keeps the input order and maps blank inputs to null without sending them")
    void preservesOrderAndSkipsBlanks() {
        GoogleTranslationService service = serviceWith("secret-key");

        server.expect(requestTo(ENDPOINT))
                .andExpect(jsonPath("$.q[0]").value("First"))
                .andExpect(jsonPath("$.q[1]").value("Second"))
                // Only the two non-blank inputs are sent; Google rejects an empty q.
                .andExpect(jsonPath("$.q[2]").doesNotExist())
                .andRespond(withSuccess(responseWith("الأول", "الثاني"), MediaType.APPLICATION_JSON));

        List<String> result = service.translateEnToAr(Arrays.asList("First", "  ", "Second", null));

        assertThat(result).containsExactly("الأول", null, "الثاني", null);
        server.verify();
    }

    @Test
    @DisplayName("chunks text over the request limit and rejoins it on the paragraph break it split")
    void chunksLongTextAndKeepsParagraphBreaks() {
        GoogleTranslationService service = serviceWith("secret-key");

        String paragraph = "x".repeat(2000);
        String longText = paragraph + "\n\n" + paragraph + "\n\n" + paragraph;
        assertThat(longText.length()).isGreaterThan(GoogleTranslationService.MAX_CHARS);

        server.expect(ExpectedCount.twice(), requestTo(ENDPOINT))
                .andRespond(withSuccess(responseWith("فقرة"), MediaType.APPLICATION_JSON));

        List<String> result = service.translateEnToAr(List.of(longText));

        // Two chunks came back, rejoined by the "\n\n" they were split on.
        assertThat(result).containsExactly("فقرة\n\nفقرة");
        server.verify();
    }

    @Test
    @DisplayName("returns nulls and does not throw when Google answers 403")
    void forbiddenLeavesArabicNull() {
        GoogleTranslationService service = serviceWith("secret-key");
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThat(service.translateEnToAr(List.of("New Cairo"))).containsExactly((String) null);
        server.verify();
    }

    @Test
    @DisplayName("returns nulls and does not throw when Google answers 500")
    void serverErrorLeavesArabicNull() {
        GoogleTranslationService service = serviceWith("secret-key");
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(service.translateEnToAr(List.of("New Cairo"))).containsExactly((String) null);
        server.verify();
    }

    @Test
    @DisplayName("is disabled with a blank key and makes no HTTP call at all")
    void blankKeyDisablesTheProvider() {
        GoogleTranslationService service = serviceWith("   ");

        assertThat(service.isEnabled()).isFalse();
        assertThat(service.translateEnToAr(List.of("New Cairo"))).containsExactly((String) null);
        // No expectation was registered, so any request would have failed above.
        server.verify();
    }
}
