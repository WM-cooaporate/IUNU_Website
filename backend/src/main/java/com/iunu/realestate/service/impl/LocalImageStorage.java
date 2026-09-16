package com.iunu.realestate.service.impl;

import com.iunu.realestate.service.ImageStorage;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Content-addressed image storage on the local filesystem - the default
 * {@link ImageStorage} provider.
 *
 * Files are named by the SHA-256 of their bytes, so re-uploading the same
 * image is idempotent and a caller can never overwrite someone else's file
 * or smuggle a path through the original filename.
 *
 * NOTE: a container filesystem is ephemeral. In production the upload root
 * (app.file-storage.location / UPLOAD_DIR) must point at a mounted volume,
 * or every image uploaded since the last deploy is lost while the database
 * rows keep pointing at it. See ENV_VARS.md.
 */
@Service
@ConditionalOnProperty(name = "app.file-storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalImageStorage implements ImageStorage {

    private static final List<String> ALLOWED_TYPES = List.of("image/jpeg", "image/png", "image/webp");

    private final Path uploadRoot;
    private final String publicBaseUrl;

    public LocalImageStorage(
            @Value("${app.file-storage.location:uploads}") String uploadLocation,
            @Value("${app.file-storage.public-base-url:http://localhost:8080}") String publicBaseUrl
    ) {
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
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file cannot be empty");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Only JPG, PNG and WEBP images are supported");
        }

        Path folderRoot = folderRoot(folder);
        try {
            Files.createDirectories(folderRoot);
            byte[] content = file.getBytes();

            // Content-Type is whatever the client typed into the request, so
            // the bytes get the final say on whether this is really an image.
            if (!matchesDeclaredType(content, file.getContentType())) {
                throw new IllegalArgumentException("Only JPG, PNG and WEBP images are supported");
            }

            String extension = extensionFor(file.getContentType(), file.getOriginalFilename());
            String filename = sha256(content) + extension;
            Path target = folderRoot.resolve(filename).normalize();

            if (!target.startsWith(folderRoot)) {
                throw new IllegalArgumentException("Invalid image filename");
            }
            if (!Files.exists(target)) Files.write(target, content);
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

    /**
     * Derives the extension from the (already allow-listed) content type, never
     * blindly from the uploaded filename - otherwise a caller could pick the
     * extension a static file gets served under. The original name is only
     * consulted to keep ".jpeg" as-is instead of rewriting it to ".jpg".
     */
    private static String extensionFor(String contentType, String originalFilename) {
        if ("image/png".equals(contentType)) return ".png";
        if ("image/webp".equals(contentType)) return ".webp";
        String extension = StringUtils.getFilenameExtension(originalFilename);
        return "jpeg".equalsIgnoreCase(extension) ? ".jpeg" : ".jpg";
    }

    /**
     * Checks the file signature against the (already allow-listed) declared
     * content type. Cheap, and enough to stop an HTML or script payload being
     * stored under an image extension by simply lying about Content-Type.
     */
    private static boolean matchesDeclaredType(byte[] content, String contentType) {
        if ("image/png".equals(contentType)) {
            return startsWith(content, new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        }
        if ("image/webp".equals(contentType)) {
            // RIFF....WEBP - the four-byte length in between is not fixed.
            return startsWith(content, new int[]{0x52, 0x49, 0x46, 0x46})
                    && content.length >= 12
                    && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P';
        }
        // image/jpeg
        return startsWith(content, new int[]{0xFF, 0xD8, 0xFF});
    }

    private static boolean startsWith(byte[] content, int[] signature) {
        if (content.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if ((content[i] & 0xFF) != signature[i]) return false;
        }
        return true;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
