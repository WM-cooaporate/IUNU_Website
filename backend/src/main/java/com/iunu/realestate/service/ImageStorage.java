package com.iunu.realestate.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Somewhere to put uploaded images, addressed only by public URL.
 *
 * Callers never see paths, buckets or filenames - which is the point: moving
 * from the local filesystem to an object store would be a second
 * implementation of this interface, with no change at any call site.
 *
 * @see com.iunu.realestate.service.impl.LocalImageStorage
 */
public interface ImageStorage {

    /** Sub-directories under the storage root, one per owning entity type. */
    String PROPERTIES_FOLDER = "properties";
    String PROJECTS_FOLDER = "projects";

    /** Stores a property image. Returns the public URL of the stored file. */
    String store(MultipartFile file);

    /** Stores an image under {@code folder}. Returns the public URL of the stored file. */
    String store(MultipartFile file, String folder);

    /** No-op if the URL is not owned by this storage provider. */
    void deleteIfStored(String url);

    /** No-op if the URL is not owned by this storage provider under {@code folder}. */
    void deleteIfStored(String url, String folder);
}
