package com.iunu.realestate.storage;

import com.iunu.realestate.exception.BadRequestException;
import com.iunu.realestate.exception.ImageServiceUnavailableException;
import com.iunu.realestate.security.events.SecurityEvents;
import com.iunu.realestate.service.ImageStorage;
import com.iunu.realestate.service.image.CloudinaryClient;
import com.iunu.realestate.service.image.CloudinaryClient.CloudinaryClientException;
import com.iunu.realestate.service.image.CloudinaryClient.StoredAsset;
import com.iunu.realestate.service.image.ImageValidator;
import com.iunu.realestate.service.impl.CloudinaryImageStorage;
import com.iunu.realestate.support.LogCapture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.iunu.realestate.storage.ImageFixtures.file;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Cloudinary image storage")
class CloudinaryImageStorageTest {

    private static final String CLOUD = "iunu-cloud";
    private static final String ROOT = "iunu/prod";
    /** Literals rather than "a".repeat(64): they appear in annotation values, which need constants. */
    private static final String HEX = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String NOT_HEX = "gggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggg";
    private static final String SHA = HEX;
    private static final String OWNED = "https://res.cloudinary.com/" + CLOUD + "/image/upload/v1712345678/"
            + ROOT + "/properties/" + SHA + ".jpg";

    private CloudinaryClient client;
    private CloudinaryImageStorage storage;

    @BeforeEach
    void setUp() {
        client = mock(CloudinaryClient.class);
        when(client.cloudName()).thenReturn(CLOUD);
        // Runnable::run: the post-commit delete happens inline, so it can be verified.
        storage = new CloudinaryImageStorage(client, new ImageValidator(mock(SecurityEvents.class)), ROOT, 2400, Runnable::run);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> uploadOptions() {
        ArgumentCaptor<Map<String, Object>> options = ArgumentCaptor.forClass(Map.class);
        verify(client).upload(any(byte[].class), options.capture());
        return options.getValue();
    }

    private void respondWith(String url) {
        when(client.upload(any(byte[].class), any())).thenReturn(Map.of("secure_url", url));
    }

    // ------------------------------------------------------------------ store

    @Test
    @DisplayName("uploads with public ID = SHA-256, folder = <root>/<folder>, no overwrite, and a size cap")
    void uploadOptionsAreContentAddressed() {
        respondWith(OWNED);
        String url = storage.store(file("house.jpg", "image/jpeg", ImageFixtures.jpeg(1)), ImageStorage.PROPERTIES_FOLDER);

        Map<String, Object> options = uploadOptions();
        assertThat(url).isEqualTo(OWNED);
        assertThat((String) options.get("public_id")).matches("[0-9a-f]{64}");
        assertThat(options.get("folder")).isEqualTo("iunu/prod/properties");
        assertThat(options.get("overwrite")).isEqualTo(false);
        assertThat(options.get("unique_filename")).isEqualTo(false);
        assertThat(options.get("resource_type")).isEqualTo("image");
        assertThat(options.get("transformation")).isEqualTo("c_limit,h_2400,w_2400");
    }

    @Test
    @DisplayName("projects go to <root>/projects")
    void projectFolder() {
        respondWith(OWNED);
        storage.store(file("cover.png", "image/png", ImageFixtures.png(1)), ImageStorage.PROJECTS_FOLDER);
        assertThat(uploadOptions().get("folder")).isEqualTo("iunu/prod/projects");
    }

    @Test
    @DisplayName("the same bytes twice give the same public ID")
    @SuppressWarnings("unchecked")
    void sameBytesSamePublicId() {
        respondWith(OWNED);
        byte[] bytes = ImageFixtures.webp(4);
        storage.store(file("one.webp", "image/webp", bytes));
        storage.store(file("renamed.webp", "image/webp", bytes));

        ArgumentCaptor<Map<String, Object>> options = ArgumentCaptor.forClass(Map.class);
        verify(client, times(2)).upload(any(byte[].class), options.capture());
        assertThat(options.getAllValues().get(0).get("public_id"))
                .isEqualTo(options.getAllValues().get(1).get("public_id"));
    }

    @Test
    @DisplayName("validation runs first: a rejected file never reaches Cloudinary")
    void validatesBeforeUploading() {
        assertThatThrownBy(() -> storage.store(ImageFixtures.ascii("x.png", "image/png", "<svg/>")))
                .isInstanceOf(BadRequestException.class);
        verify(client, never()).upload(any(), any());
    }

    @Test
    @DisplayName("an unknown folder is refused")
    void unknownFolder() {
        assertThatThrownBy(() -> storage.store(file("a.jpg", "image/jpeg", ImageFixtures.jpeg(1)), "../users"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("an SDK failure becomes a 502 exception, and the log carries status and message but no secret")
    void sdkFailureIsA502WithNoSecretLogged() {
        when(client.upload(any(byte[].class), any())).thenThrow(
                new CloudinaryClientException(401, "RuntimeException: Invalid Signature. cloudinary://123456:s3cr3t-value@iunu-cloud"));

        try (LogCapture logs = new LogCapture(CloudinaryImageStorage.class.getName())) {
            assertThatThrownBy(() -> storage.store(file("a.jpg", "image/jpeg", ImageFixtures.jpeg(1))))
                    .isInstanceOf(ImageServiceUnavailableException.class)
                    .hasMessage("Image service is temporarily unavailable. Please try again.");

            assertThat(logs.messages()).singleElement().satisfies(line -> {
                assertThat(line).contains("status 401").contains("Invalid Signature");
                assertThat(line).doesNotContain("s3cr3t-value").doesNotContain("123456");
            });
        }
    }

    @Test
    @DisplayName("any other exception is logged by type only - its message was never vetted")
    void unexpectedExceptionMessageIsNotLogged() {
        when(client.upload(any(byte[].class), any())).thenThrow(new IllegalStateException("api_secret=s3cr3t-value"));

        try (LogCapture logs = new LogCapture(CloudinaryImageStorage.class.getName())) {
            assertThatThrownBy(() -> storage.store(file("a.jpg", "image/jpeg", ImageFixtures.jpeg(1))))
                    .isInstanceOf(ImageServiceUnavailableException.class);
            assertThat(String.join("\n", logs.messages())).doesNotContain("s3cr3t-value").contains("IllegalStateException");
        }
    }

    @Test
    @DisplayName("a response with no secure_url is a 502, not a null URL saved to the database")
    void missingSecureUrl() {
        when(client.upload(any(byte[].class), any())).thenReturn(Map.of("public_id", "x"));
        assertThatThrownBy(() -> storage.store(file("a.jpg", "image/jpeg", ImageFixtures.jpeg(1))))
                .isInstanceOf(ImageServiceUnavailableException.class);
    }

    // ------------------------------------------------------------ deleteIfStored

    @Test
    @DisplayName("deletes exactly the one asset it owns, with or without a version segment")
    void deletesItsOwn() {
        storage.deleteIfStored(OWNED, ImageStorage.PROPERTIES_FOLDER);
        storage.deleteIfStored("https://res.cloudinary.com/" + CLOUD + "/image/upload/" + ROOT + "/projects/" + SHA + ".webp",
                ImageStorage.PROJECTS_FOLDER);

        verify(client).destroy(ROOT + "/properties/" + SHA);
        verify(client).destroy(ROOT + "/projects/" + SHA);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            // another cloud
            "https://res.cloudinary.com/someone-else/image/upload/v1/iunu/prod/properties/" + HEX + ".jpg",
            // lookalike hosts
            "https://res.cloudinary.com.evil.com/iunu-cloud/image/upload/v1/iunu/prod/properties/" + HEX + ".jpg",
            "https://evil.com/res.cloudinary.com/iunu-cloud/image/upload/v1/iunu/prod/properties/" + HEX + ".jpg",
            "https://res.cloudinary.com@evil.com/iunu-cloud/image/upload/v1/iunu/prod/properties/" + HEX + ".jpg",
            // plain http, an explicit port
            "http://res.cloudinary.com/iunu-cloud/image/upload/v1/iunu/prod/properties/" + HEX + ".jpg",
            "https://res.cloudinary.com:8443/iunu-cloud/image/upload/v1/iunu/prod/properties/" + HEX + ".jpg",
            // another environment's folder
            "https://res.cloudinary.com/iunu-cloud/image/upload/v1/iunu/dev/properties/" + HEX + ".jpg",
            "https://res.cloudinary.com/iunu-cloud/image/upload/v1/iunu/prod2/properties/" + HEX + ".jpg",
            // the wrong folder of this environment
            "https://res.cloudinary.com/iunu-cloud/image/upload/v1/iunu/prod/projects/" + HEX + ".jpg",
            // traversal and encoding tricks
            "https://res.cloudinary.com/iunu-cloud/image/upload/v1/iunu/prod/properties/../projects/" + HEX + ".jpg",
            "https://res.cloudinary.com/iunu-cloud/image/upload/v1/iunu%2Fprod/properties/" + HEX + ".jpg",
            // a transformation, a query string
            "https://res.cloudinary.com/iunu-cloud/image/upload/w_800/iunu/prod/properties/" + HEX + ".jpg",
            "https://res.cloudinary.com/iunu-cloud/image/upload/v1/iunu/prod/properties/" + HEX + ".jpg?x=1",
            // a non-hex or short public ID
            "https://res.cloudinary.com/iunu-cloud/image/upload/v1/iunu/prod/properties/" + NOT_HEX + ".jpg",
            "https://res.cloudinary.com/iunu-cloud/image/upload/v1/iunu/prod/properties/sample.jpg",
            // legacy local upload, pasted external link, garbage
            "http://localhost:8080/uploads/properties/" + HEX + ".jpg",
            "https://images.example.com/house.jpg",
            "not a url at all",
            "",
    })
    @DisplayName("ignores anything it does not own")
    void ignoresWhatItDoesNotOwn(String url) {
        storage.deleteIfStored(url, ImageStorage.PROPERTIES_FOLDER);
        verify(client, never()).destroy(anyString());
    }

    @Test
    @DisplayName("null is a no-op")
    void nullIsANoOp() {
        storage.deleteIfStored(null, ImageStorage.PROPERTIES_FOLDER);
        storage.deleteIfStored(null);
        verify(client, never()).destroy(anyString());
    }

    @Test
    @DisplayName("a failed delete is swallowed, so it can never fail the property update behind it")
    void failedDeleteIsSwallowed() {
        doThrow(new CloudinaryClientException(500, "boom")).when(client).destroy(anyString());
        storage.deleteIfStored(OWNED);
        verify(client).destroy(ROOT + "/properties/" + SHA);
    }

    @Test
    @DisplayName("inside a transaction the delete waits for commit, and a rollback cancels it")
    void deleteWaitsForCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            storage.deleteIfStored(OWNED);
            verify(client, never()).destroy(anyString());

            List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);
            synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(client, never()).destroy(anyString());

            synchronizations.forEach(TransactionSynchronization::afterCommit);
            verify(client).destroy(ROOT + "/properties/" + SHA);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ------------------------------------------------------------------ sweep helpers

    @Test
    @DisplayName("listing asks for this root with a trailing slash, and drops anything outside it or too young")
    void listingStaysInItsRoot() {
        Instant cutoff = Instant.parse("2026-01-02T00:00:00Z");
        when(client.listResources(eq("iunu/prod/"), eq(cutoff))).thenReturn(List.of(
                new StoredAsset("iunu/prod/properties/" + SHA, OWNED, cutoff.minusSeconds(60)),
                new StoredAsset("iunu/dev/properties/" + SHA, "x", cutoff.minusSeconds(60)),
                new StoredAsset("iunu/prod2/properties/" + SHA, "x", cutoff.minusSeconds(60)),
                new StoredAsset("iunu/prod/properties/" + "b".repeat(64), "x", cutoff.plusSeconds(1))));

        assertThat(storage.listOwnAssetsOlderThan(cutoff)).extracting(StoredAsset::publicId)
                .containsExactly("iunu/prod/properties/" + SHA);
    }

    @Test
    @DisplayName("destroyNow refuses anything outside the root")
    void destroyNowRefusesOtherRoots() {
        assertThatThrownBy(() -> storage.destroyNow("iunu/dev/properties/" + SHA)).isInstanceOf(IllegalArgumentException.class);
        verify(client, never()).destroy(anyString());
    }

    @Test
    @DisplayName("references are found conservatively - a transformed URL still protects its asset")
    void referencesAreConservative() {
        assertThat(storage.referencedPublicIds(List.of(
                OWNED,
                "https://res.cloudinary.com/" + CLOUD + "/image/upload/f_auto,w_800/" + ROOT + "/projects/" + "c".repeat(64) + ".jpg",
                "https://images.example.com/house.jpg")))
                .containsExactlyInAnyOrder(ROOT + "/properties/" + SHA, ROOT + "/projects/" + "c".repeat(64));
    }
}
