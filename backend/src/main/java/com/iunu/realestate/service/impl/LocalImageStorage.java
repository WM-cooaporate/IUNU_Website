package com.iunu.realestate.service.impl;

import com.iunu.realestate.service.ImageStorage;
import com.iunu.realestate.service.image.ImageValidator;
import com.iunu.realestate.service.image.ImageValidator.ValidatedImage;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Content-addressed image storage on the local filesystem - the default
 * {@link ImageStorage} provider.
 *
 * Files are named by the SHA-256 of their bytes, so re-uploading the same
 * image is idempotent and a caller can never overwrite someone else's file
 * or smuggle a path through the original filename.
 *
 * Validation (allow-list, file signature, hashing) is shared with the other
 * providers through {@link ImageValidator}.
 *
 * NOTE: a container filesystem is ephemeral. In production the upload root
 * must be a persistent disk (Render: /var/data, see render.yaml); anywhere
 * else, every image uploaded since the last deploy is lost while the
 * database rows keep pointing at it. See ENV_VARS.md.
 */
@Slf4j
@Service
public class LocalImageStorage implements ImageStorage {

    private final Path uploadRoot;
    private final String publicBaseUrl;
    private final ImageValidator imageValidator;

    public LocalImageStorage(
            @Value("${app.file-storage.location:uploads}") String uploadLocation,
            @Value("${app.file-storage.public-base-url:http://localhost:8080}") String publicBaseUrl,
            ImageValidator imageValidator
    ) {
        this.imageValidator = imageValidator;
        this.uploadRoot = Paths.get(uploadLocation).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl.replaceAll("/$", "");
    }

    /**
     * Creates the upload root up front and proves it is writable, so a
     * container that cannot persist uploads fails at boot rather than
     * accepting traffic and 500-ing on the first admin upload.
     */
    @PostConstruct
    void prepareUploadRoot() {
        log.info("Storing uploaded images under {}", uploadRoot);
        try {
            Files.createDirectories(uploadRoot);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot create the image upload directory " + uploadRoot
                            + " (app.file-storage.location / UPLOAD_DIR). "
                            + "In production this must be a mounted, writable volume.", exception);
        }
        if (!Files.isWritable(uploadRoot)) {
            throw new IllegalStateException(
                    "The image upload directory " + uploadRoot
                            + " (app.file-storage.location / UPLOAD_DIR) is not writable. "
                            + "In production this must be a mounted, writable volume.");
        }
    }

    /** Stores a property image. Retained so existing property callers are unaffected. */
    @Override
    public String store(MultipartFile file) {
        return store(file, PROPERTIES_FOLDER);
    }

    /** Stores an image under {@code folder} and returns its public URL. */
    @Override
    public String store(MultipartFile file, String folder) {
        ValidatedImage image = imageValidator.validate(file);

        Path folderRoot = folderRoot(folder);
        try {
            Files.createDirectories(folderRoot);
            String filename = image.sha256Hex() + image.extension();
            Path target = folderRoot.resolve(filename).normalize();

            if (!target.startsWith(folderRoot)) {
                throw new IllegalArgumentException("Invalid image filename");
            }
            if (!Files.exists(target)) Files.write(target, image.bytes());
            return publicUrlPrefix(folder) + filename;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to store image", exception);
        }
    }

    @Override
    public void deleteIfStored(String imageUrl) {
        deleteIfStored(imageUrl, PROPERTIES_FOLDER);
    }

    /**
     * Deletes a file this service stored. URLs that don't point into
     * {@code folder} (e.g. an externally hosted image an admin pasted in)
     * are ignored rather than treated as an error.
     */
    @Override
    public void deleteIfStored(String imageUrl, String folder) {
        String prefix = publicUrlPrefix(folder);
        if (imageUrl == null || !imageUrl.startsWith(prefix)) return;

        Path folderRoot = folderRoot(folder);
        Path target = folderRoot.resolve(imageUrl.substring(prefix.length())).normalize();
        if (!target.startsWith(folderRoot)) return;
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to remove image", exception);
        }
    }

    private Path folderRoot(String folder) {
        Path folderRoot = uploadRoot.resolve(folder).normalize();
        if (!folderRoot.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("Invalid storage folder");
        }
        return folderRoot;
    }

    private String publicUrlPrefix(String folder) {
        return publicBaseUrl + "/uploads/" + folder + "/";
    }
}
