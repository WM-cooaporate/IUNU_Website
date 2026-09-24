package com.iunu.realestate.config;

import com.iunu.realestate.service.image.CloudinaryClient;
import com.iunu.realestate.service.image.CloudinaryCredentials;
import com.iunu.realestate.service.image.SdkCloudinaryClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks where uploaded images live. {@code app.file-storage.provider}
 * (FILE_STORAGE_PROVIDER) selects exactly one ImageStorage bean:
 * LocalImageStorage for {@code local} (the default outside prod) or
 * CloudinaryImageStorage for {@code cloudinary} (the prod default).
 *
 * <p>Fails at startup, not at the first upload: an unknown provider, or
 * cloudinary without a usable CLOUDINARY_URL, stops the container with a
 * message saying which variable to set and where to find its value.
 */
@Configuration
public class FileStorageConfig {

    public FileStorageConfig(FileStorageProperties properties) {
        String provider = properties.provider() == null ? "" : properties.provider().trim();
        if (!provider.isEmpty()
                && !FileStorageProperties.LOCAL.equalsIgnoreCase(provider)
                && !FileStorageProperties.CLOUDINARY.equalsIgnoreCase(provider)) {
            throw new IllegalStateException("FILE_STORAGE_PROVIDER must be 'local' or 'cloudinary'.");
        }
    }

    @Bean
    @ConditionalOnProperty(name = "app.file-storage.provider", havingValue = "cloudinary")
    public CloudinaryClient cloudinaryClient(FileStorageProperties properties) {
        FileStorageProperties.Cloudinary settings = properties.cloudinaryOrDefaults();
        CloudinaryCredentials credentials = CloudinaryCredentials.parse(settings.url());
        return new SdkCloudinaryClient(credentials,
                settings.connectTimeoutMsOrDefault(), settings.readTimeoutMsOrDefault());
    }
}
