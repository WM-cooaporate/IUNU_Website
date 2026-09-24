package com.iunu.realestate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Bound from app.file-storage.*.
 *
 * @param provider       {@code local} (development, tests) or {@code cloudinary}
 *                       (production). Selects the one {@code ImageStorage} bean.
 * @param location       Upload root for the local provider. Also where the
 *                       legacy-upload migration looks for files to move.
 * @param publicBaseUrl  Public origin of this API; local-provider URLs, and the
 *                       legacy ones the migration rewrites, start with it.
 * @param cloudinary     Settings read only when the provider is cloudinary.
 */
@ConfigurationProperties(prefix = "app.file-storage")
public record FileStorageProperties(
        String provider,
        String location,
        String publicBaseUrl,
        Cloudinary cloudinary
) {

    public static final String LOCAL = "local";
    public static final String CLOUDINARY = "cloudinary";

    public boolean isCloudinary() {
        return CLOUDINARY.equalsIgnoreCase(provider == null ? "" : provider.trim());
    }

    public Cloudinary cloudinaryOrDefaults() {
        return cloudinary != null ? cloudinary : new Cloudinary(null, null, null, null, null, null);
    }

    public String publicBaseUrlOrDefault() {
        String base = (publicBaseUrl == null || publicBaseUrl.isBlank()) ? "http://localhost:8080" : publicBaseUrl.trim();
        return base.replaceAll("/+$", "");
    }

    public String locationOrDefault() {
        return (location == null || location.isBlank()) ? "uploads" : location.trim();
    }

    /**
     * @param url              {@code cloudinary://<key>:<secret>@<cloud_name>} (CLOUDINARY_URL).
     *                         A credential: never logged, never sent to the browser.
     * @param folderRoot       Every asset this environment writes lives under it
     *                         ({@code iunu/prod}, {@code iunu/dev}); nothing outside
     *                         it is ever deleted.
     * @param connectTimeoutMs TCP connect budget for one API call.
     * @param readTimeoutMs    Response budget for one API call.
     * @param maxDimension     Incoming-transformation cap on the long edge - a
     *                         backstop for the browser's own resize.
     * @param sweepMinAge      How old an unreferenced asset must be before the
     *                         orphan sweep may delete it. Covers the gap between
     *                         an upload and the Save that references it.
     */
    public record Cloudinary(
            String url,
            String folderRoot,
            Integer connectTimeoutMs,
            Integer readTimeoutMs,
            Integer maxDimension,
            Duration sweepMinAge
    ) {
        public String folderRootOrDefault() {
            String root = (folderRoot == null || folderRoot.isBlank()) ? "iunu/dev" : folderRoot.trim();
            return root.replaceAll("^/+|/+$", "");
        }

        public int connectTimeoutMsOrDefault() {
            return connectTimeoutMs == null ? 5000 : connectTimeoutMs;
        }

        public int readTimeoutMsOrDefault() {
            return readTimeoutMs == null ? 30000 : readTimeoutMs;
        }

        public int maxDimensionOrDefault() {
            return maxDimension == null ? 2400 : maxDimension;
        }

        public Duration sweepMinAgeOrDefault() {
            return sweepMinAge == null ? Duration.ofHours(24) : sweepMinAge;
        }
    }
}
