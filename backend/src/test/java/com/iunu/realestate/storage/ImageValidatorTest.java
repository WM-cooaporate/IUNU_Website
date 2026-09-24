package com.iunu.realestate.storage;

import com.iunu.realestate.exception.BadRequestException;
import com.iunu.realestate.security.events.SecurityEventType;
import com.iunu.realestate.security.events.SecurityEvents;
import com.iunu.realestate.service.image.ImageValidator;
import com.iunu.realestate.service.image.ImageValidator.ValidatedImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.iunu.realestate.storage.ImageFixtures.ascii;
import static com.iunu.realestate.storage.ImageFixtures.file;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("Image validation")
class ImageValidatorTest {

    private SecurityEvents securityEvents;
    private ImageValidator validator;

    @BeforeEach
    void setUp() {
        securityEvents = mock(SecurityEvents.class);
        validator = new ImageValidator(securityEvents);
    }

    @Test
    @DisplayName("JPEG, PNG and WebP are accepted, named by SHA-256 with an extension from the content type")
    void acceptsTheThreeFormats() {
        ValidatedImage jpeg = validator.validate(file("a.jpg", "image/jpeg", ImageFixtures.jpeg(1)));
        ValidatedImage png = validator.validate(file("b.png", "image/png", ImageFixtures.png(2)));
        ValidatedImage webp = validator.validate(file("c.webp", "image/webp", ImageFixtures.webp(3)));

        assertThat(jpeg.extension()).isEqualTo(".jpg");
        assertThat(png.extension()).isEqualTo(".png");
        assertThat(webp.extension()).isEqualTo(".webp");
        assertThat(jpeg.sha256Hex()).matches("[0-9a-f]{64}");
        assertThat(jpeg.mimeType()).isEqualTo("image/jpeg");
        verify(securityEvents, never()).record(any(), any(), any(), any(Map.class));
    }

    @Test
    @DisplayName("the same bytes always hash the same, whatever the filename")
    void hashIsContentAddressed() {
        byte[] bytes = ImageFixtures.png(9);
        assertThat(validator.validate(file("one.png", "image/png", bytes)).sha256Hex())
                .isEqualTo(validator.validate(file("two.png", "image/png", bytes)).sha256Hex());
    }

    @Test
    @DisplayName(".jpeg keeps its extension; the filename never picks any other one")
    void extensionComesFromTheType() {
        assertThat(validator.validate(file("x.jpeg", "image/jpeg", ImageFixtures.jpeg(1))).extension()).isEqualTo(".jpeg");
        assertThat(validator.validate(file("x.html", "image/png", ImageFixtures.png(1))).extension()).isEqualTo(".png");
    }

    @Test
    @DisplayName("an SVG renamed .png is rejected")
    void svgPretendingToBePng() {
        MockMultipartFile svg = ascii("logo.png", "image/png", "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>");
        assertThatThrownBy(() -> validator.validate(svg))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(ImageValidator.UNSUPPORTED_MESSAGE);
        verify(securityEvents).record(eq(SecurityEventType.UPLOAD_REJECTED), isNull(), isNull(),
                eq(Map.of("reason", "magic_bytes_mismatch")));
    }

    @Test
    @DisplayName("an SVG declared as SVG is rejected by the allow-list")
    void svgDeclaredAsSvg() {
        assertThatThrownBy(() -> validator.validate(ascii("logo.svg", "image/svg+xml", "<svg/>")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(ImageValidator.UNSUPPORTED_MESSAGE);
    }

    @Test
    @DisplayName("HTML behind a PNG signature is rejected when it claims to be a JPEG")
    void htmlBehindPngMagicDeclaredAsJpeg() {
        byte[] png = ImageFixtures.png(0);
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.US_ASCII);
        byte[] polyglot = new byte[8 + html.length];
        System.arraycopy(png, 0, polyglot, 0, 8);
        System.arraycopy(html, 0, polyglot, 8, html.length);

        assertThatThrownBy(() -> validator.validate(file("page.jpg", "image/jpeg", polyglot)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(ImageValidator.UNSUPPORTED_MESSAGE);
        assertThatThrownBy(() -> validator.validate(file("page.html", "text/html", polyglot)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(ImageValidator.UNSUPPORTED_MESSAGE);
    }

    @Test
    @DisplayName("an empty file gets the empty-file message")
    void emptyFile() {
        assertThatThrownBy(() -> validator.validate(file("a.jpg", "image/jpeg", new byte[0])))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("The image file is empty.");
    }

    @Test
    @DisplayName("an iPhone HEIC gets advice, whether it is labelled HEIC or passed off as a JPEG")
    void heicGetsItsOwnMessage() {
        assertThatThrownBy(() -> validator.validate(file("IMG_0001.HEIC", "image/heic", ImageFixtures.heic())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(ImageValidator.HEIC_MESSAGE);
        assertThatThrownBy(() -> validator.validate(file("IMG_0001.jpg", "image/jpeg", ImageFixtures.heic())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(ImageValidator.HEIC_MESSAGE);
        assertThat(ImageValidator.HEIC_MESSAGE).contains("HEIC").contains("Most Compatible");
    }
}
