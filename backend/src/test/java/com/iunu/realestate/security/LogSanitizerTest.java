package com.iunu.realestate.security;

import com.iunu.realestate.util.LogSanitizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogSanitizer.maskEmail")
class LogSanitizerTest {

    @Test
    void masksLocalPartKeepsDomain() {
        assertThat(LogSanitizer.maskEmail("michael@iunu-eg.com")).isEqualTo("m***@iunu-eg.com");
        assertThat(LogSanitizer.maskEmail("  Michael@IUNU-EG.com ")).isEqualTo("M***@IUNU-EG.com");
    }

    @Test
    void masksAnythingWithoutAUsableAtCompletely() {
        assertThat(LogSanitizer.maskEmail("not-an-email")).isEqualTo("***");
        assertThat(LogSanitizer.maskEmail("@iunu-eg.com")).isEqualTo("***");
        assertThat(LogSanitizer.maskEmail("someone@")).isEqualTo("***");
        assertThat(LogSanitizer.maskEmail(null)).isNull();
    }

    @Test
    void cannotSmuggleANewlineThroughTheDomain() {
        assertThat(LogSanitizer.maskEmail("a@x.com\nINFO forged")).doesNotContain("\n");
    }
}
