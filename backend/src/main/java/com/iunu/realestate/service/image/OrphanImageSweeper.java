package com.iunu.realestate.service.image;

import com.iunu.realestate.config.FileStorageProperties;
import com.iunu.realestate.dto.response.ImageSweepResponse;
import com.iunu.realestate.exception.ConflictException;
import com.iunu.realestate.repository.ProjectRepository;
import com.iunu.realestate.repository.PropertyRepository;
import com.iunu.realestate.service.image.CloudinaryClient.StoredAsset;
import com.iunu.realestate.service.impl.CloudinaryImageStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Deletes Cloudinary images nothing points at - uploaded from the dashboard,
 * then abandoned when the admin cancelled the edit. Uploads now happen as soon
 * as files are picked rather than on Save, so this is the other half of that
 * design.
 *
 * <p>Every rail below exists because a wrong delete here is a broken image on
 * the public site, while a missed one costs a few hundred KB of free quota:
 * <ul>
 *   <li>never anything younger than {@code sweep-min-age} (24h) - that is an
 *       edit still in progress;</li>
 *   <li>never anything referenced by any property cover, property gallery or
 *       project cover, compared by public ID - including an image two
 *       properties share;</li>
 *   <li>never outside this environment's folder root: a dev run cannot see,
 *       let alone delete, {@code iunu/prod};</li>
 *   <li>zero references while properties exist means the reference query is
 *       broken, not that the site is empty - abort;</li>
 *   <li>at most {@value #MAX_DELETES_PER_RUN} deletions per run.</li>
 * </ul>
 */
@Slf4j
@Service
public class OrphanImageSweeper {

    public static final int MAX_DELETES_PER_RUN = 200;

    private final ObjectProvider<CloudinaryImageStorage> cloudStorage;
    private final PropertyRepository propertyRepository;
    private final ProjectRepository projectRepository;
    private final Duration minAge;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Autowired
    public OrphanImageSweeper(ObjectProvider<CloudinaryImageStorage> cloudStorage, PropertyRepository propertyRepository,
                              ProjectRepository projectRepository, FileStorageProperties properties) {
        this(cloudStorage, propertyRepository, projectRepository,
                properties.cloudinaryOrDefaults().sweepMinAgeOrDefault(), Clock.systemUTC());
    }

    public OrphanImageSweeper(ObjectProvider<CloudinaryImageStorage> cloudStorage, PropertyRepository propertyRepository,
                              ProjectRepository projectRepository, Duration minAge, Clock clock) {
        this.cloudStorage = cloudStorage;
        this.propertyRepository = propertyRepository;
        this.projectRepository = projectRepository;
        this.minAge = minAge;
        this.clock = clock;
    }

    /** Daily at 03:00 Cairo time, for real. The endpoint defaults to a dry run; this does not. */
    @Scheduled(cron = "0 0 3 * * *", zone = "Africa/Cairo")
    public void scheduledSweep() {
        if (cloudStorage.getIfAvailable() == null) return;
        try {
            sweep(false);
        } catch (RuntimeException exception) {
            log.error("Scheduled image sweep failed: {}", exception.getMessage());
        }
    }

    public ImageSweepResponse sweep(boolean dryRun) {
        CloudinaryImageStorage cloud = cloudStorage.getIfAvailable();
        if (cloud == null) {
            return new ImageSweepResponse(false, 0, 0, 0, dryRun);
        }
        if (!running.compareAndSet(false, true)) {
            throw new ConflictException("An image sweep is already running. Wait for it to finish.");
        }
        try {
            return run(cloud, dryRun);
        } finally {
            running.set(false);
        }
    }

    private ImageSweepResponse run(CloudinaryImageStorage cloud, boolean dryRun) {
        Instant cutoff = clock.instant().minus(minAge);
        // Listed before the references are read, so a Save that lands in
        // between is seen as a reference rather than missed.
        List<StoredAsset> candidates = cloud.listOwnAssetsOlderThan(cutoff);

        List<String> references = new ArrayList<>();
        references.addAll(propertyRepository.findAllCoverImageUrls());
        references.addAll(propertyRepository.findAllGalleryImageUrls());
        references.addAll(projectRepository.findAllCoverImageUrls());
        if (references.isEmpty() && (propertyRepository.count() > 0 || projectRepository.count() > 0)) {
            log.error("Image sweep aborted: no image references found although properties exist");
            throw new ConflictException("Image sweep aborted: no image references were found although projects exist.");
        }
        Set<String> referenced = cloud.referencedPublicIds(references);

        List<StoredAsset> orphans = candidates.stream()
                .filter(asset -> !referenced.contains(asset.publicId()))
                .toList();

        int deleted = 0;
        if (!dryRun) {
            for (StoredAsset orphan : orphans) {
                if (deleted >= MAX_DELETES_PER_RUN) break;
                try {
                    cloud.destroyNow(orphan.publicId());
                    deleted++;
                } catch (RuntimeException exception) {
                    // Status and type only, as everywhere else Cloudinary errors are logged.
                    log.warn("Image sweep could not delete one asset: {}", exception.getClass().getSimpleName());
                }
            }
        }
        log.info("Image sweep: scanned {}, orphans {}, deleted {}, dryRun {}",
                candidates.size(), orphans.size(), deleted, dryRun);
        return new ImageSweepResponse(true, candidates.size(), orphans.size(), deleted, dryRun);
    }
}
