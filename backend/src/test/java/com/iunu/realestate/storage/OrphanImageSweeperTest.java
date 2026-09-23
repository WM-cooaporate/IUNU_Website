package com.iunu.realestate.storage;

import com.iunu.realestate.dto.response.ImageSweepResponse;
import com.iunu.realestate.exception.ConflictException;
import com.iunu.realestate.repository.ProjectRepository;
import com.iunu.realestate.repository.PropertyRepository;
import com.iunu.realestate.security.events.SecurityEvents;
import com.iunu.realestate.service.image.CloudinaryClient;
import com.iunu.realestate.service.image.CloudinaryClient.StoredAsset;
import com.iunu.realestate.service.image.ImageValidator;
import com.iunu.realestate.service.image.OrphanImageSweeper;
import com.iunu.realestate.service.impl.CloudinaryImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The orphan sweep's safety rails, one test each. A wrong delete here is a
 * broken image on the public site; a missed one costs a little free quota.
 */
@DisplayName("Orphan image sweep")
class OrphanImageSweeperTest {

    private static final String CLOUD = "iunu-cloud";
    private static final String ROOT = "iunu/dev";
    private static final Instant NOW = Instant.parse("2026-09-23T01:00:00Z");
    private static final Instant OLD = NOW.minus(Duration.ofDays(3));

    private CloudinaryClient client;
    private PropertyRepository properties;
    private ProjectRepository projects;
    private OrphanImageSweeper sweeper;

    @BeforeEach
    void setUp() {
        client = mock(CloudinaryClient.class);
        when(client.cloudName()).thenReturn(CLOUD);
        CloudinaryImageStorage storage = new CloudinaryImageStorage(client,
                new ImageValidator(mock(SecurityEvents.class)), ROOT, 2400, Runnable::run);

        @SuppressWarnings("unchecked")
        ObjectProvider<CloudinaryImageStorage> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(storage);

        properties = mock(PropertyRepository.class);
        projects = mock(ProjectRepository.class);
        when(properties.count()).thenReturn(2L);
        when(properties.findAllCoverImageUrls()).thenReturn(List.of());
        when(properties.findAllGalleryImageUrls()).thenReturn(List.of("https://images.example.com/pasted.jpg"));
        when(projects.findAllCoverImageUrls()).thenReturn(List.of());

        sweeper = new OrphanImageSweeper(provider, properties, projects, Duration.ofHours(24),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static String id(String folder, char hex) {
        return ROOT + "/" + folder + "/" + String.valueOf(hex).repeat(64);
    }

    private static String url(String publicId) {
        return "https://res.cloudinary.com/" + CLOUD + "/image/upload/v1700000000/" + publicId + ".jpg";
    }

    private void cloudHolds(StoredAsset... assets) {
        when(client.listResources(eq(ROOT + "/"), any())).thenReturn(List.of(assets));
    }

    @Test
    @DisplayName("deletes an old, unreferenced image in its own root")
    void deletesAnOrphan() {
        cloudHolds(new StoredAsset(id("properties", 'a'), url(id("properties", 'a')), OLD));

        ImageSweepResponse result = sweeper.sweep(false);

        verify(client).destroy(id("properties", 'a'));
        assertThat(result).isEqualTo(new ImageSweepResponse(true, 1, 1, 1, false));
    }

    @Test
    @DisplayName("a dry run reports orphans and deletes nothing")
    void dryRunDeletesNothing() {
        cloudHolds(new StoredAsset(id("properties", 'a'), url(id("properties", 'a')), OLD));

        ImageSweepResponse result = sweeper.sweep(true);

        verify(client, never()).destroy(anyString());
        assertThat(result).isEqualTo(new ImageSweepResponse(true, 1, 1, 0, true));
    }

    @Test
    @DisplayName("never deletes anything younger than 24h, even if the listing returns it")
    void neverDeletesYoungImages() {
        cloudHolds(new StoredAsset(id("properties", 'a'), url(id("properties", 'a')), NOW.minus(Duration.ofHours(23))));

        ImageSweepResponse result = sweeper.sweep(false);

        verify(client).listResources(ROOT + "/", NOW.minus(Duration.ofHours(24)));
        verify(client, never()).destroy(anyString());
        assertThat(result.scanned()).isZero();
    }

    @Test
    @DisplayName("never deletes a referenced image - property cover, gallery, project cover, or one shared by two properties")
    void neverDeletesReferencedImages() {
        String cover = id("properties", 'a');
        String gallery = id("properties", 'b');
        String shared = id("properties", 'c');
        String projectCover = id("projects", 'd');
        String orphan = id("properties", 'e');
        cloudHolds(new StoredAsset(cover, url(cover), OLD), new StoredAsset(gallery, url(gallery), OLD),
                new StoredAsset(shared, url(shared), OLD), new StoredAsset(projectCover, url(projectCover), OLD),
                new StoredAsset(orphan, url(orphan), OLD));
        when(properties.findAllCoverImageUrls()).thenReturn(List.of(url(cover)));
        // The shared image is in two galleries - the query returns it twice.
        when(properties.findAllGalleryImageUrls()).thenReturn(List.of(url(gallery), url(shared), url(shared)));
        when(projects.findAllCoverImageUrls()).thenReturn(List.of(
                // A transformed URL still counts as a reference.
                "https://res.cloudinary.com/" + CLOUD + "/image/upload/f_auto,w_800/" + projectCover + ".jpg"));

        ImageSweepResponse result = sweeper.sweep(false);

        verify(client, times(1)).destroy(anyString());
        verify(client).destroy(orphan);
        assertThat(result).isEqualTo(new ImageSweepResponse(true, 5, 1, 1, false));
    }

    @Test
    @DisplayName("never looks outside its own folder root: a dev run cannot see or delete iunu/prod")
    void staysInItsOwnRoot() {
        String prod = "iunu/prod/properties/" + "a".repeat(64);
        String prefixTwin = "iunu/dev2/properties/" + "b".repeat(64);
        cloudHolds(new StoredAsset(prod, url(prod), OLD), new StoredAsset(prefixTwin, url(prefixTwin), OLD));

        ImageSweepResponse result = sweeper.sweep(false);

        verify(client).listResources(eq("iunu/dev/"), any());
        verify(client, never()).destroy(anyString());
        assertThat(result.scanned()).isZero();
    }

    @Test
    @DisplayName("zero references while properties exist aborts - that is a broken query, not an empty site")
    void abortsOnZeroReferences() {
        cloudHolds(new StoredAsset(id("properties", 'a'), url(id("properties", 'a')), OLD));
        when(properties.findAllGalleryImageUrls()).thenReturn(List.of());

        assertThatThrownBy(() -> sweeper.sweep(false)).isInstanceOf(ConflictException.class);
        verify(client, never()).destroy(anyString());
    }

    @Test
    @DisplayName("zero references with no properties and no projects is fine: everything is an orphan")
    void emptySiteIsNotAnAbort() {
        cloudHolds(new StoredAsset(id("properties", 'a'), url(id("properties", 'a')), OLD));
        when(properties.findAllGalleryImageUrls()).thenReturn(List.of());
        when(properties.count()).thenReturn(0L);
        when(projects.count()).thenReturn(0L);

        assertThat(sweeper.sweep(false).deleted()).isEqualTo(1);
    }

    @Test
    @DisplayName("deletes at most 200 per run")
    void capsDeletionsPerRun() {
        List<StoredAsset> many = new ArrayList<>();
        IntStream.range(0, 250).forEach(i -> {
            String publicId = ROOT + "/properties/" + String.format("%064x", i);
            many.add(new StoredAsset(publicId, url(publicId), OLD));
        });
        when(client.listResources(eq(ROOT + "/"), any())).thenReturn(many);

        ImageSweepResponse result = sweeper.sweep(false);

        verify(client, times(OrphanImageSweeper.MAX_DELETES_PER_RUN)).destroy(anyString());
        assertThat(result).isEqualTo(new ImageSweepResponse(true, 250, 250, 200, false));
    }

    @Test
    @DisplayName("with the local provider it does nothing and says enabled=false")
    void disabledWithLocalProvider() {
        @SuppressWarnings("unchecked")
        ObjectProvider<CloudinaryImageStorage> none = mock(ObjectProvider.class);
        OrphanImageSweeper local = new OrphanImageSweeper(none, properties, projects, Duration.ofHours(24), Clock.systemUTC());

        assertThat(local.sweep(false)).isEqualTo(new ImageSweepResponse(false, 0, 0, 0, false));
        verify(client, never()).listResources(anyString(), any());
    }
}
