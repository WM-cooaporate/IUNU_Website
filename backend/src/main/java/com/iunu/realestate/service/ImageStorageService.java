package com.iunu.realestate.service;

import org.springframework.beans.factory.annotation.Value;
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
 * Content-addressed image storage on the local filesystem.
 *
 * Files are named by the SHA-256 of their bytes, so re-uploading the same
 * image is idempotent and a caller can never overwrite someone else's file
 * or smuggle a path through the original filename.
 *
 * Everything the outside world sees is a URL, and every caller goes through
 * {@link #store}/{@link #deleteIfStored} rather than touching paths - so
 * swapping this for S3 or Cloud Storage later means reimplementing these
 * two methods, with no change at the call sites.
 */
@Service
public class ImageStorageService {

    private static final List<String> ALLOWED_TYPES = List.of("image/jpeg", "image/png", "image/webp");

    /** Sub-directories under the upload root, one per owning entity type. */
    public static final String PROPERTIES_FOLDER = "properties";
    public static final String PROJECTS_FOLDER = "projects";

    private final Path uploadRoot;
    private final String publicBaseUrl;

    public ImageStorageService(
            @Value("${app.file-storage.location:uploads}") String uploadLocation,
            @Value("${app.file-storage.public-base-url:http://localhost:8080}") String publicBaseUrl
    ) {
        this.uploadRoot = Paths.get(uploadLocation).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl.replaceAll("/$", "");
    }

    /** Stores a property image. Retained so existing property callers are unaffected. */
    public String store(MultipartFile file) {
        return store(file, PROPERTIES_FOLDER);
    }

    /** Stores an image under {@code folder} and returns its public URL. */
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

    public void deleteIfStored(String imageUrl) {
        deleteIfStored(imageUrl, PROPERTIES_FOLDER);
    }

    /**
     * Deletes a file this service stored. URLs that don't point into
     * {@code folder} (e.g. an externally hosted image an admin pasted in)
     * are ignored rather than treated as an error.
     */
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

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
