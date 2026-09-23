package com.iunu.realestate.service.image;

import com.cloudinary.Cloudinary;
import com.cloudinary.api.ApiResponse;
import com.cloudinary.api.AuthorizationRequired;
import com.cloudinary.api.exceptions.AlreadyExists;
import com.cloudinary.api.exceptions.BadRequest;
import com.cloudinary.api.exceptions.NotAllowed;
import com.cloudinary.api.exceptions.NotFound;
import com.cloudinary.api.exceptions.RateLimited;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link CloudinaryClient} on the official SDK. Configured from explicit
 * values rather than the SDK's own read of the CLOUDINARY_URL environment
 * variable, so the credentials, timeouts and https-only delivery are decided
 * in one visible place.
 */
public class SdkCloudinaryClient implements CloudinaryClient {

    /** Stops a runaway listing from walking an unbounded number of pages. */
    private static final int MAX_LIST_PAGES = 20;
    private static final int PAGE_SIZE = 500;
    private static final int MAX_MESSAGE_LENGTH = 300;
    private static final Pattern STATUS_IN_MESSAGE = Pattern.compile("status code - (\\d{3})");

    private final Cloudinary cloudinary;
    private final CloudinaryCredentials credentials;
    private final int connectTimeoutSeconds;
    private final int readTimeoutSeconds;

    public SdkCloudinaryClient(CloudinaryCredentials credentials, int connectTimeoutMs, int readTimeoutMs) {
        this.credentials = credentials;
        this.connectTimeoutSeconds = Math.max(1, connectTimeoutMs / 1000);
        this.readTimeoutSeconds = Math.max(1, readTimeoutMs / 1000);

        Map<String, Object> config = new HashMap<>();
        config.put("cloud_name", credentials.cloudName());
        config.put("api_key", credentials.apiKey());
        config.put("api_secret", credentials.apiSecret());
        config.put("secure", true);
        // The upload strategy has a single timeout (seconds) that it applies to
        // connect and response alike; the read budget is the one that matters
        // for a multi-megabyte body.
        config.put("timeout", readTimeoutSeconds);
        this.cloudinary = new Cloudinary(config);
    }

    @Override
    public String cloudName() {
        return credentials.cloudName();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> upload(byte[] bytes, Map<String, Object> options) {
        try {
            return (Map<String, Object>) cloudinary.uploader().upload(bytes, new HashMap<>(options));
        } catch (Exception exception) {
            throw wrap(exception);
        }
    }

    @Override
    public void destroy(String publicId) {
        Map<String, Object> options = new HashMap<>();
        options.put("invalidate", true);
        options.put("resource_type", "image");
        options.put("type", "upload");
        try {
            // {"result":"not found"} is fine: the goal is that it is gone.
            cloudinary.uploader().destroy(publicId, options);
        } catch (Exception exception) {
            throw wrap(exception);
        }
    }

    @Override
    public List<StoredAsset> listResources(String prefix, Instant olderThan) {
        List<StoredAsset> assets = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < MAX_LIST_PAGES; page++) {
            Map<String, Object> options = new HashMap<>();
            options.put("resource_type", "image");
            options.put("type", "upload");
            options.put("prefix", prefix);
            options.put("max_results", PAGE_SIZE);
            options.put("timeout", readTimeoutSeconds);
            options.put("connect_timeout", connectTimeoutSeconds);
            if (cursor != null) options.put("next_cursor", cursor);

            ApiResponse response;
            try {
                response = cloudinary.api().resources(options);
            } catch (Exception exception) {
                throw wrap(exception);
            }
            Object resources = response.get("resources");
            if (resources instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> resource) {
                        toAsset(resource, olderThan, assets);
                    }
                }
            }
            Object next = response.get("next_cursor");
            if (!(next instanceof String nextCursor) || nextCursor.isBlank()) break;
            cursor = nextCursor;
        }
        return assets;
    }

    private static void toAsset(Map<?, ?> resource, Instant olderThan, List<StoredAsset> into) {
        Object publicId = resource.get("public_id");
        Object secureUrl = resource.get("secure_url");
        Object createdAt = resource.get("created_at");
        if (!(publicId instanceof String id) || !(createdAt instanceof String created)) return;
        Instant createdInstant;
        try {
            createdInstant = Instant.parse(created);
        } catch (DateTimeParseException exception) {
            return; // an unparseable age is never old enough to delete
        }
        if (createdInstant.isBefore(olderThan)) {
            into.add(new StoredAsset(id, secureUrl instanceof String url ? url : null, createdInstant));
        }
    }

    /** One exception type, a status where the SDK exposes one, and no credential in the message. */
    private CloudinaryClientException wrap(Exception exception) {
        String message = credentials.redact(String.valueOf(exception.getMessage()));
        if (message.length() > MAX_MESSAGE_LENGTH) message = message.substring(0, MAX_MESSAGE_LENGTH) + "...";
        return new CloudinaryClientException(statusOf(exception, message), exception.getClass().getSimpleName() + ": " + message);
    }

    private static int statusOf(Exception exception, String message) {
        if (exception instanceof BadRequest) return 400;
        if (exception instanceof AuthorizationRequired) return 401;
        if (exception instanceof NotAllowed) return 403;
        if (exception instanceof NotFound) return 404;
        if (exception instanceof AlreadyExists) return 409;
        if (exception instanceof RateLimited) return 420;
        Matcher matcher = STATUS_IN_MESSAGE.matcher(message);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }
}
