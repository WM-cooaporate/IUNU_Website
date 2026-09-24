package com.iunu.realestate.service.impl;

import com.iunu.realestate.config.FileStorageProperties;
import com.iunu.realestate.exception.ImageServiceUnavailableException;
import com.iunu.realestate.service.ImageStorage;
import com.iunu.realestate.service.image.CloudinaryClient;
import com.iunu.realestate.service.image.CloudinaryClient.CloudinaryClientException;
import com.iunu.realestate.service.image.CloudinaryClient.StoredAsset;
import com.iunu.realestate.service.image.ImageValidator;
import com.iunu.realestate.service.image.ImageValidator.ValidatedImage;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link ImageStorage} on Cloudinary - the production provider. Images survive
 * redeploys, and the public site is served from Cloudinary's CDN with
 * per-screen sizes (see src/utils/imageUrl.js) instead of full-size originals
 * off a 0.1-CPU instance.
 *
 * <p>The browser never talks to Cloudinary: uploads still come through
 * {@code POST /api/properties/images}, so admin-only access, magic-byte
 * validation ({@link ImageValidator}) and rate limiting all still apply.
 *
 * <p><strong>Content-addressed, like the local provider.</strong> The public ID
 * is the SHA-256 of the bytes with {@code overwrite=false}, so the same photo
 * uploaded twice is one asset and a re-upload is a no-op.
 *
 * <p><strong>Environment isolation.</strong> Everything is written under
 * {@code <folder-root>/<folder>} ({@code iunu/prod/properties}, ...), and a
 * delete only ever touches a URL that parses to exactly that shape on this
 * cloud. Another environment's folder, another cloud, a lookalike host or a
 * pasted external link is a no-op.
 *
 * <p><strong>Deletes run after commit, off the request thread.</strong> Callers
 * delete from inside a transaction; a network call there would hold a database
 * connection, and deleting before commit would lose an image the rolled-back
 * row still points at. Anything a failed or dropped delete leaves behind is
 * collected by the orphan sweep.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.file-storage.provider", havingValue = "cloudinary")
public class CloudinaryImageStorage implements ImageStorage {

    private static final String HOST = "res.cloudinary.com";
    private static final Set<String> FOLDERS = Set.of(PROPERTIES_FOLDER, PROJECTS_FOLDER);
    private static final Pattern CLOUDINARY_URL_IN_TEXT = Pattern.compile("cloudinary://\\S*");

    private final CloudinaryClient client;
    private final ImageValidator imageValidator;
    private final String folderRoot;
    private final int maxDimension;
    private final Executor deleteExecutor;
    private final ExecutorService ownedExecutor;
    /** Finds {@code <root>/<folder>/<sha256>} anywhere in a string - see {@link #referencedPublicIds}. */
    private final Pattern publicIdInText;

    @Autowired
    public CloudinaryImageStorage(CloudinaryClient client, ImageValidator imageValidator,
                                  FileStorageProperties properties) {
        this(client, imageValidator, properties.cloudinaryOrDefaults().folderRootOrDefault(),
                properties.cloudinaryOrDefaults().maxDimensionOrDefault(), null);
    }

    /**
     * @param deleteExecutor where post-commit deletes run; null for the default
     *                       single background thread. Tests pass {@code Runnable::run}.
     */
    public CloudinaryImageStorage(CloudinaryClient client, ImageValidator imageValidator,
                                  String folderRoot, int maxDimension, Executor deleteExecutor) {
        this.client = client;
        this.imageValidator = imageValidator;
        this.folderRoot = folderRoot;
        this.maxDimension = maxDimension;
        if (deleteExecutor == null) {
            // One thread and a bounded queue: deletes are rare, never urgent,
            // and the sweep collects anything this drops.
            ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(500), runnable -> {
                        Thread thread = new Thread(runnable, "cloudinary-delete");
                        thread.setDaemon(true);
                        return thread;
                    });
            this.ownedExecutor = executor;
            this.deleteExecutor = executor;
        } else {
            this.ownedExecutor = null;
            this.deleteExecutor = deleteExecutor;
        }
        this.publicIdInText = Pattern.compile(
                Pattern.quote(folderRoot) + "/(?:" + PROPERTIES_FOLDER + "|" + PROJECTS_FOLDER + ")/[0-9a-f]{64}");
    }

    @PreDestroy
    void shutdown() {
        if (ownedExecutor != null) ownedExecutor.shutdown();
    }

    public String folderRoot() {
        return folderRoot;
    }

    @Override
    public String store(MultipartFile file) {
        return store(file, PROPERTIES_FOLDER);
    }

    @Override
    public String store(MultipartFile file, String folder) {
        requireKnownFolder(folder);
        return upload(imageValidator.validate(file), folder);
    }

    /** Stores bytes that did not arrive as an upload - the legacy migration reads them off disk. */
    public String storeBytes(byte[] content, String contentType, String filename, String folder) {
        requireKnownFolder(folder);
        return upload(imageValidator.validate(content, contentType, filename), folder);
    }

    private String upload(ValidatedImage image, String folder) {
        Map<String, Object> options = new HashMap<>();
        options.put("public_id", image.sha256Hex());
        options.put("folder", folderRoot + "/" + folder);
        options.put("overwrite", false);
        options.put("unique_filename", false);
        options.put("resource_type", "image");
        // Incoming transformation: what Cloudinary keeps is capped, whatever
        // the browser's own resize did or did not do.
        options.put("transformation", "c_limit,h_" + maxDimension + ",w_" + maxDimension);

        Map<String, Object> response;
        try {
            response = client.upload(image.bytes(), options);
        } catch (RuntimeException exception) {
            logFailure("upload", exception);
            throw new ImageServiceUnavailableException();
        }
        Object secureUrl = response == null ? null : response.get("secure_url");
        if (!(secureUrl instanceof String url) || url.isBlank()) {
            log.warn("Cloudinary upload returned no secure_url");
            throw new ImageServiceUnavailableException();
        }
        if (ownedPublicId(url, folder).isEmpty()) {
            // Happens if the account's folder mode puts assets somewhere the
            // folder parameter did not predict. Serving still works; cleanup
            // will never touch this asset, which is the safe direction.
            log.warn("Cloudinary returned a URL outside {}/{}; cleanup will not manage it", folderRoot, folder);
        }
        return url;
    }

    @Override
    public void deleteIfStored(String url) {
        deleteIfStored(url, PROPERTIES_FOLDER);
    }

    /**
     * Deletes the asset behind {@code url} if - and only if - it is one of
     * ours in {@code folder}. Never throws: a failed delete must not fail the
     * property update that triggered it.
     */
    @Override
    public void deleteIfStored(String url, String folder) {
        Optional<String> publicId = ownedPublicId(url, folder);
        if (publicId.isEmpty()) return;

        Runnable delete = () -> destroyQuietly(publicId.get());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(delete);
                }
            });
        } else {
            submit(delete);
        }
    }

    /**
     * The public ID behind {@code url} when it is exactly
     * {@code https://res.cloudinary.com/<our cloud>/image/upload/[v<digits>/]<root>/<folder>/<64 hex>.<ext>}.
     * Parsed with {@link URI} and compared whole - host and path - never with a
     * prefix test on the raw string, which "res.cloudinary.com.evil.com" or a
     * "../" would get past.
     */
    public Optional<String> ownedPublicId(String url, String folder) {
        if (url == null || !FOLDERS.contains(folder)) return Optional.empty();
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException exception) {
            return Optional.empty();
        }
        if (!"https".equals(uri.getScheme()) || !HOST.equals(uri.getHost()) || uri.getPort() != -1
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || uri.getRawPath() == null) {
            return Optional.empty();
        }
        Matcher matcher = Pattern.compile("/" + Pattern.quote(client.cloudName()) + "/image/upload/(?:v\\d+/)?"
                        + Pattern.quote(folderRoot + "/" + folder + "/") + "([0-9a-f]{64})\\.(?:jpg|jpeg|png|webp)")
                .matcher(uri.getRawPath());
        return matcher.matches() ? Optional.of(folderRoot + "/" + folder + "/" + matcher.group(1)) : Optional.empty();
    }

    /**
     * Every public ID of ours that appears anywhere in {@code urls}.
     * Deliberately looser than {@link #ownedPublicId}: this is the "is it still
     * in use?" side of the sweep, where over-counting keeps an image and
     * under-counting deletes one somebody is looking at. A transformed or
     * otherwise unusual URL still protects its asset.
     */
    public Set<String> referencedPublicIds(Collection<String> urls) {
        Set<String> ids = new HashSet<>();
        for (String url : urls) {
            if (url == null) continue;
            Matcher matcher = publicIdInText.matcher(url);
            while (matcher.find()) ids.add(matcher.group());
        }
        return ids;
    }

    /** Our assets older than {@code olderThan}. Never anything outside this environment's folder root. */
    public List<StoredAsset> listOwnAssetsOlderThan(Instant olderThan) {
        String prefix = folderRoot + "/";
        return client.listResources(prefix, olderThan).stream()
                // The client was asked for exactly this; checked again because
                // a delete decision must never rest on one layer.
                .filter(asset -> asset.publicId() != null && asset.publicId().startsWith(prefix))
                .filter(asset -> asset.createdAt() != null && asset.createdAt().isBefore(olderThan))
                .toList();
    }

    /** Deletes one asset now, on the caller's thread. For the sweep, which is never inside a transaction. */
    public void destroyNow(String publicId) {
        if (publicId == null || !publicId.startsWith(folderRoot + "/")) {
            throw new IllegalArgumentException("Refusing to delete outside " + folderRoot);
        }
        client.destroy(publicId);
    }

    private void submit(Runnable delete) {
        try {
            deleteExecutor.execute(delete);
        } catch (RejectedExecutionException exception) {
            log.warn("Cloudinary delete queue is full; the orphan sweep will remove the image later");
        }
    }

    private void destroyQuietly(String publicId) {
        try {
            client.destroy(publicId);
        } catch (RuntimeException exception) {
            logFailure("delete", exception);
        }
    }

    /**
     * Status and a redacted message only. CloudinaryClient already strips the
     * credentials from its own exceptions; anything else is logged by type
     * alone, because its message was never vetted.
     */
    private void logFailure(String operation, RuntimeException exception) {
        if (exception instanceof CloudinaryClientException clientException) {
            log.warn("Cloudinary {} failed: status {}, {}", operation, clientException.status(),
                    CLOUDINARY_URL_IN_TEXT.matcher(String.valueOf(clientException.getMessage())).replaceAll("cloudinary://***"));
        } else {
            log.warn("Cloudinary {} failed: status -1, {}", operation, exception.getClass().getSimpleName());
        }
    }

    private static void requireKnownFolder(String folder) {
        if (!FOLDERS.contains(folder)) {
            throw new IllegalArgumentException("Invalid storage folder");
        }
    }
}
