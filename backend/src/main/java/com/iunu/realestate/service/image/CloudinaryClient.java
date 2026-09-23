package com.iunu.realestate.service.image;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The three Cloudinary calls this app makes, behind an interface so
 * {@link com.iunu.realestate.service.impl.CloudinaryImageStorage} can be
 * tested without the network. {@link SdkCloudinaryClient} is the real one.
 *
 * <p>Every failure surfaces as {@link CloudinaryClientException}, whose
 * message has already had the credentials redacted.
 */
public interface CloudinaryClient {

    /** The account these calls act on; part of every delivery URL. */
    String cloudName();

    /** Uploads {@code bytes} with the given upload-API options and returns the response. */
    Map<String, Object> upload(byte[] bytes, Map<String, Object> options);

    /** Deletes one image and invalidates its CDN copies. Deleting something already gone is not an error. */
    void destroy(String publicId);

    /**
     * Uploaded images whose public ID starts with {@code prefix} and that were
     * created strictly before {@code olderThan}.
     */
    List<StoredAsset> listResources(String prefix, Instant olderThan);

    record StoredAsset(String publicId, String secureUrl, Instant createdAt) {}

    /** A failed call. {@code status} is the HTTP status when one is known, otherwise -1. */
    class CloudinaryClientException extends RuntimeException {
        private final int status;

        public CloudinaryClientException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }
}
