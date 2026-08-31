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

@Service
public class ImageStorageService {

    private static final List<String> ALLOWED_TYPES = List.of("image/jpeg", "image/png", "image/webp");
    private final Path uploadRoot;
    private final String publicBaseUrl;

    public ImageStorageService(
            @Value("${app.file-storage.location:uploads}") String uploadLocation,
            @Value("${app.file-storage.public-base-url:http://localhost:8080}") String publicBaseUrl
    ) {
        this.uploadRoot = Paths.get(uploadLocation).toAbsolutePath().normalize().resolve("properties");
        this.publicBaseUrl = publicBaseUrl.replaceAll("/$", "");
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file cannot be empty");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Only JPG, PNG and WEBP images are supported");
        }

        try {
            Files.createDirectories(uploadRoot);
            byte[] content = file.getBytes();
            String extension = extensionFor(file.getContentType(), file.getOriginalFilename());
            String filename = sha256(content) + extension;
            Path target = uploadRoot.resolve(filename).normalize();

            if (!target.startsWith(uploadRoot)) {
                throw new IllegalArgumentException("Invalid image filename");
            }
            if (!Files.exists(target)) Files.write(target, content);
            return publicBaseUrl + "/uploads/properties/" + filename;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to store image", exception);
        }
    }

    public void deleteIfStored(String imageUrl) {
        String prefix = publicBaseUrl + "/uploads/properties/";
        if (imageUrl == null || !imageUrl.startsWith(prefix)) return;

        Path target = uploadRoot.resolve(imageUrl.substring(prefix.length())).normalize();
        if (!target.startsWith(uploadRoot)) return;
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to remove image", exception);
        }
    }

    private static String extensionFor(String contentType, String originalFilename) {
        if ("image/png".equals(contentType)) return ".png";
        if ("image/webp".equals(contentType)) return ".webp";
        String extension = StringUtils.getFilenameExtension(originalFilename);
        return extension == null ? ".jpg" : "." + extension.toLowerCase(Locale.ROOT);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
