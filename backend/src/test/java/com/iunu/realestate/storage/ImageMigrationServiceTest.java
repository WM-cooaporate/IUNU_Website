package com.iunu.realestate.storage;

import com.iunu.realestate.config.FileStorageProperties;
import com.iunu.realestate.dto.response.ImageMigrationResponse;
import com.iunu.realestate.dto.response.ImageMigrationResponse.MissingImage;
import com.iunu.realestate.security.events.SecurityEvents;
import com.iunu.realestate.service.image.CloudinaryClient;
import com.iunu.realestate.service.image.ImageMigrationService;
import com.iunu.realestate.service.image.ImageUrlRewriter;
import com.iunu.realestate.service.image.ImageUrlRewriter.ImageRefs;
import com.iunu.realestate.service.image.ImageValidator;
import com.iunu.realestate.service.impl.CloudinaryImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Legacy upload migration")
class ImageMigrationServiceTest {

    private static final String BASE = "https://api.iunu.test";
    private static final String PRESENT = "b".repeat(64) + ".jpg";
    private static final String GONE = "c".repeat(64) + ".png";
    private static final String CLOUD_URL = "https://res.cloudinary.com/iunu-cloud/image/upload/v1/iunu/prod/properties/" + "d".repeat(64) + ".jpg";

    @TempDir Path uploads;

    private CloudinaryClient client;
    private ImageUrlRewriter rewriter;
    private ImageMigrationService service;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(uploads.resolve("properties"));
        Files.write(uploads.resolve("properties").resolve(PRESENT), ImageFixtures.jpeg(1));

        client = mock(CloudinaryClient.class);
        when(client.cloudName()).thenReturn("iunu-cloud");
        when(client.upload(any(byte[].class), any())).thenReturn(Map.of("secure_url", CLOUD_URL));
        CloudinaryImageStorage storage = new CloudinaryImageStorage(client,
                new ImageValidator(mock(SecurityEvents.class)), "iunu/prod", 2400, Runnable::run);
        @SuppressWarnings("unchecked")
        ObjectProvider<CloudinaryImageStorage> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(storage);

        rewriter = mock(ImageUrlRewriter.class);
        FileStorageProperties properties = new FileStorageProperties("cloudinary", uploads.toString(), BASE, null);
        service = new ImageMigrationService(provider, rewriter, properties);
    }

    @Test
    @DisplayName("moves a file that still exists, lists one that is gone, and leaves external links alone")
    void movesPresentAndListsMissing() {
        String present = BASE + "/uploads/properties/" + PRESENT;
        String gone = BASE + "/uploads/properties/" + GONE;
        when(rewriter.propertyIdsWithImagesStartingWith(BASE + "/uploads/")).thenReturn(List.of(7L));
        when(rewriter.readProperty(7L)).thenReturn(Optional.of(new ImageRefs("Palm Hills",
                List.of(present, gone, "https://images.example.com/pasted.jpg"))));
        when(rewriter.rewriteProperty(eq(7L), anyMap())).thenReturn(Set.of(present));
        when(rewriter.projectIdsWithCoverStartingWith(any())).thenReturn(List.of());

        ImageMigrationResponse result = service.migrate();

        verify(rewriter).rewriteProperty(7L, Map.of(present, CLOUD_URL));
        verify(client, times(1)).upload(any(byte[].class), any());
        assertThat(result.enabled()).isTrue();
        assertThat(result.migrated()).isEqualTo(1);
        assertThat(result.missing()).containsExactly(new MissingImage("PROPERTY", 7L, "Palm Hills", gone));
    }

    @Test
    @DisplayName("a second run changes nothing: no legacy URLs are left to find")
    void idempotent() {
        when(rewriter.propertyIdsWithImagesStartingWith(any())).thenReturn(List.of());
        when(rewriter.projectIdsWithCoverStartingWith(any())).thenReturn(List.of());

        ImageMigrationResponse result = service.migrate();

        assertThat(result).isEqualTo(new ImageMigrationResponse(true, 0, List.of()));
        verify(client, never()).upload(any(), any());
        verify(rewriter, never()).rewriteProperty(anyLong(), anyMap());
    }

    @Test
    @DisplayName("a path that is not a content-addressed name is never read off disk")
    void refusesTraversal() throws Exception {
        Files.write(uploads.resolve("secret.jpg"), ImageFixtures.jpeg(2));
        String sneaky = BASE + "/uploads/properties/../secret.jpg";
        when(rewriter.propertyIdsWithImagesStartingWith(any())).thenReturn(List.of(1L));
        when(rewriter.readProperty(1L)).thenReturn(Optional.of(new ImageRefs("x", List.of(sneaky))));
        when(rewriter.projectIdsWithCoverStartingWith(any())).thenReturn(List.of());

        ImageMigrationResponse result = service.migrate();

        verify(client, never()).upload(any(), any());
        assertThat(result.missing()).extracting(MissingImage::url).containsExactly(sneaky);
    }

    @Test
    @DisplayName("with the local provider it does nothing and says enabled=false")
    void disabledWithLocalProvider() {
        @SuppressWarnings("unchecked")
        ObjectProvider<CloudinaryImageStorage> none = mock(ObjectProvider.class);
        ImageMigrationService local = new ImageMigrationService(none, rewriter,
                new FileStorageProperties("local", uploads.toString(), BASE, null));

        assertThat(local.migrate()).isEqualTo(new ImageMigrationResponse(false, 0, List.of()));
        verify(rewriter, never()).propertyIdsWithImagesStartingWith(any());
    }
}
