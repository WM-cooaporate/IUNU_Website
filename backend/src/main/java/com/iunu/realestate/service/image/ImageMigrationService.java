package com.iunu.realestate.service.image;

import com.iunu.realestate.config.FileStorageProperties;
import com.iunu.realestate.dto.response.ImageMigrationResponse;
import com.iunu.realestate.dto.response.ImageMigrationResponse.MissingImage;
import com.iunu.realestate.exception.BadRequestException;
import com.iunu.realestate.exception.ConflictException;
import com.iunu.realestate.service.ImageStorage;
import com.iunu.realestate.service.impl.CloudinaryImageStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Moves images uploaded under the local provider ({@code <PUBLIC_API_URL>/uploads/...})
 * to Cloudinary and rewrites the rows that point at them.
 *
 * <p>On Render's free plan most of those files are already gone - wiped by a
 * redeploy - so the useful output is often the {@code missing} list: which
 * projects need their photos re-uploaded. Missing rows are left untouched.
 *
 * <p>Deliberately not @Transactional: each upload happens with no transaction
 * open, then each row is rewritten in its own short transaction
 * ({@link ImageUrlRewriter}). A failure partway keeps what was already moved,
 * and a second run changes nothing that the first one finished.
 */
@Slf4j
@Service
public class ImageMigrationService {

    /** Only content-addressed names the local provider could have written. Anything else is not ours. */
    private static final Pattern LEGACY_PATH =
            Pattern.compile("(" + ImageStorage.PROPERTIES_FOLDER + "|" + ImageStorage.PROJECTS_FOLDER
                    + ")/([0-9a-f]{64}\\.(jpg|jpeg|png|webp))");

    private final ObjectProvider<CloudinaryImageStorage> cloudStorage;
    private final ImageUrlRewriter rewriter;
    private final FileStorageProperties properties;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ImageMigrationService(ObjectProvider<CloudinaryImageStorage> cloudStorage, ImageUrlRewriter rewriter,
                                 FileStorageProperties properties) {
        this.cloudStorage = cloudStorage;
        this.rewriter = rewriter;
        this.properties = properties;
    }

    public ImageMigrationResponse migrate() {
        CloudinaryImageStorage cloud = cloudStorage.getIfAvailable();
        if (cloud == null) {
            return new ImageMigrationResponse(false, 0, List.of());
        }
        if (!running.compareAndSet(false, true)) {
            throw new ConflictException("An image migration is already running. Wait for it to finish.");
        }
        try {
            return run(cloud);
        } finally {
            running.set(false);
        }
    }

    private ImageMigrationResponse run(CloudinaryImageStorage cloud) {
        String prefix = properties.publicBaseUrlOrDefault() + "/uploads/";
        Path uploadRoot = Paths.get(properties.locationOrDefault()).toAbsolutePath().normalize();
        // One upload per distinct legacy URL, however many rows share it.
        Map<String, Optional<String>> moved = new HashMap<>();
        Set<String> migrated = new HashSet<>();
        List<MissingImage> missing = new ArrayList<>();

        for (Long id : rewriter.propertyIdsWithImagesStartingWith(prefix)) {
            rewriter.readProperty(id).ifPresent(refs -> {
                Map<String, String> replacements = plan(cloud, uploadRoot, prefix, refs, moved,
                        url -> missing.add(new MissingImage("PROPERTY", id, refs.title(), url)));
                if (!replacements.isEmpty()) migrated.addAll(rewriter.rewriteProperty(id, replacements));
            });
        }
        for (Long id : rewriter.projectIdsWithCoverStartingWith(prefix)) {
            rewriter.readProject(id).ifPresent(refs -> {
                Map<String, String> replacements = plan(cloud, uploadRoot, prefix, refs, moved,
                        url -> missing.add(new MissingImage("PROJECT", id, refs.title(), url)));
                if (!replacements.isEmpty()) migrated.addAll(rewriter.rewriteProject(id, replacements));
            });
        }

        log.info("Image migration finished: migrated {}, missing {}", migrated.size(), missing.size());
        return new ImageMigrationResponse(true, migrated.size(), missing);
    }

    private Map<String, String> plan(CloudinaryImageStorage cloud, Path uploadRoot, String prefix,
                                     ImageUrlRewriter.ImageRefs refs, Map<String, Optional<String>> moved,
                                     Consumer<String> onMissing) {
        Map<String, String> replacements = new HashMap<>();
        for (String url : new LinkedHashSet<>(refs.urls())) {
            if (url == null || !url.startsWith(prefix)) continue;
            Optional<String> newUrl = moved.computeIfAbsent(url, legacy -> upload(cloud, uploadRoot, legacy.substring(prefix.length())));
            if (newUrl.isPresent()) replacements.put(url, newUrl.get());
            else onMissing.accept(url);
        }
        return replacements;
    }

    /** The Cloudinary URL for one legacy file, or empty when the file is gone or unusable. */
    private Optional<String> upload(CloudinaryImageStorage cloud, Path uploadRoot, String relativePath) {
        Matcher matcher = LEGACY_PATH.matcher(relativePath);
        if (!matcher.matches()) return Optional.empty();
        String folder = matcher.group(1);
        String filename = matcher.group(2);
        Path file = uploadRoot.resolve(folder).resolve(filename).normalize();
        if (!file.startsWith(uploadRoot) || !Files.isRegularFile(file)) return Optional.empty();

        byte[] content;
        try {
            content = Files.readAllBytes(file);
        } catch (IOException exception) {
            log.warn("Could not read legacy upload {}: {}", filename, exception.getMessage());
            return Optional.empty();
        }
        try {
            return Optional.of(cloud.storeBytes(content, contentTypeOf(matcher.group(3)), filename, folder));
        } catch (BadRequestException rejected) {
            // Corrupt, or not the image its name claims: treat it as lost.
            log.warn("Legacy upload {} failed validation: {}", filename, rejected.getMessage());
            return Optional.empty();
        }
    }

    private static String contentTypeOf(String extension) {
        return switch (extension) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }
}
