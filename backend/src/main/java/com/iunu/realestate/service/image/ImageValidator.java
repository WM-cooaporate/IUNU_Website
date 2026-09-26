package com.iunu.realestate.service.image;

import com.iunu.realestate.exception.BadRequestException;
import com.iunu.realestate.security.events.SecurityEventType;
import com.iunu.realestate.security.events.SecurityEvents;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * The checks every {@link com.iunu.realestate.service.ImageStorage} provider
 * runs before it keeps a byte: allow-listed content type, a file signature
 * that agrees with it, and the SHA-256 the stored name is derived from.
 *
 * <p>One implementation for every provider, so moving storage elsewhere
 * cannot quietly loosen what an upload is allowed to be.
 */
@Component
public class ImageValidator {

    public static final String EMPTY_MESSAGE = "The image file is empty.";
    public static final String HEIC_MESSAGE =
            "iPhone HEIC photos aren't supported. Set Camera → Formats → Most Compatible, or export as JPG.";
    public static final String UNSUPPORTED_MESSAGE = "Only JPG, PNG and WebP images are supported.";

    private static final List<String> ALLOWED_TYPES = List.of("image/jpeg", "image/png", "image/webp");
    private static final List<String> HEIC_TYPES = List.of("image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence");
    /** ISO-BMFF brands an iPhone writes into the ftyp box at offset 8. */
    private static final List<String> HEIC_BRANDS = List.of("heic", "heix", "heif", "mif1", "msf1", "hevc", "hevx");

    private final SecurityEvents securityEvents;

    public ImageValidator(SecurityEvents securityEvents) {
        this.securityEvents = securityEvents;
    }

    /** What is left once an upload has passed every check. */
    public record ValidatedImage(byte[] bytes, String sha256Hex, String extension, String mimeType) {}

    /** @throws BadRequestException with a message fit to show the admin */
    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw rejected("empty", EMPTY_MESSAGE);
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read the uploaded image", exception);
        }
        return validate(content, file.getContentType(), file.getOriginalFilename());
    }

    /**
     * Same checks for bytes that did not arrive as a multipart part - the
     * legacy-upload migration reads them off disk.
     */
    public ValidatedImage validate(byte[] content, String contentType, String originalFilename) {
        if (content == null || content.length == 0) {
            throw rejected("empty", EMPTY_MESSAGE);
        }
        // Before the allow-list, so an iPhone photo gets advice the admin can
        // act on instead of the generic "wrong format".
        if (HEIC_TYPES.contains(contentType) || isHeic(content)) {
            throw rejected("heic", HEIC_MESSAGE);
        }
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw rejected("content_type_not_allowed", UNSUPPORTED_MESSAGE);
        }
        // Content-Type is whatever the client typed into the request, so
        // the bytes get the final say on whether this is really an image.
        if (!matchesDeclaredType(content, contentType)) {
            throw rejected("magic_bytes_mismatch", UNSUPPORTED_MESSAGE);
        }
        return new ValidatedImage(content, sha256(content), extensionFor(contentType, originalFilename), contentType);
    }

    /**
     * Counts and logs the rejection, then returns the exception to throw. Only
     * an admin token can reach an upload, so a run of these is either a
     * confused admin or a stolen token probing for a stored-XSS foothold -
     * worth seeing either way. The filename is never logged: it is the one
     * part of an upload the attacker fully controls.
     */
    private BadRequestException rejected(String reason, String message) {
        securityEvents.record(SecurityEventType.UPLOAD_REJECTED, null, null, Map.of("reason", reason));
        return new BadRequestException(message);
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

    /** "....ftypheic" and friends: a four-byte box size, "ftyp", then the brand. */
    private static boolean isHeic(byte[] content) {
        if (content.length < 12 || content[4] != 'f' || content[5] != 't' || content[6] != 'y' || content[7] != 'p') {
            return false;
        }
        String brand = new String(content, 8, 4, StandardCharsets.US_ASCII);
        return HEIC_BRANDS.contains(brand);
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
