package com.iunu.realestate.service.image;

import java.util.regex.Pattern;

/**
 * The three parts of {@code CLOUDINARY_URL}
 * ({@code cloudinary://<api_key>:<api_secret>@<cloud_name>}).
 *
 * <p>{@link #toString()} is overridden so the secret cannot leak through a log
 * line or an exception message that happens to print this record.
 */
public record CloudinaryCredentials(String cloudName, String apiKey, String apiSecret) {

    private static final String SCHEME = "cloudinary://";

    static final String WHERE_TO_FIND_IT =
            "Copy it from the Cloudinary dashboard → API Keys → \"API environment variable\" "
                    + "(cloudinary://<api_key>:<api_secret>@<cloud_name>) and set it on the backend only. See ENV_VARS.md.";

    /** Cloud names are lower-case letters, digits, '-' and '_'. Anything else is a typo or an injection. */
    private static final Pattern CLOUD_NAME = Pattern.compile("[a-z0-9_-]{1,64}");

    /**
     * @throws IllegalStateException naming CLOUDINARY_URL, with no part of the
     *                               value in the message
     */
    public static CloudinaryCredentials parse(String url) {
        // "${CLOUDINARY_URL}" is what an unresolved placeholder binds as.
        if (url == null || url.isBlank() || url.trim().startsWith("${")) {
            throw invalid("CLOUDINARY_URL is not set, but FILE_STORAGE_PROVIDER is cloudinary.");
        }
        // Split by hand rather than with java.net.URI: URI treats a cloud name
        // containing '_' as a non-host authority and would report no host.
        String value = url.trim();
        if (!value.startsWith(SCHEME)) {
            throw invalid("CLOUDINARY_URL must start with cloudinary://.");
        }
        String rest = value.substring(SCHEME.length());
        int query = rest.indexOf('?');
        if (query >= 0) rest = rest.substring(0, query);
        int at = rest.lastIndexOf('@');
        String userInfo = at < 0 ? "" : rest.substring(0, at);
        String cloudName = at < 0 ? "" : rest.substring(at + 1);
        int colon = userInfo.indexOf(':');
        if (colon <= 0 || colon == userInfo.length() - 1 || !CLOUD_NAME.matcher(cloudName).matches()) {
            throw invalid("CLOUDINARY_URL must look like cloudinary://<api_key>:<api_secret>@<cloud_name>.");
        }
        return new CloudinaryCredentials(cloudName, userInfo.substring(0, colon), userInfo.substring(colon + 1));
    }

    /** Replaces the key and secret wherever they appear, for text that is about to be logged. */
    public String redact(String text) {
        if (text == null) return null;
        String redacted = text;
        if (!apiSecret.isEmpty()) redacted = redacted.replace(apiSecret, "***");
        if (!apiKey.isEmpty()) redacted = redacted.replace(apiKey, "***");
        return redacted;
    }

    @Override
    public String toString() {
        return "CloudinaryCredentials[cloudName=" + cloudName + ", apiKey=***, apiSecret=***]";
    }

    private static IllegalStateException invalid(String problem) {
        return new IllegalStateException(problem + " " + WHERE_TO_FIND_IT);
    }
}
