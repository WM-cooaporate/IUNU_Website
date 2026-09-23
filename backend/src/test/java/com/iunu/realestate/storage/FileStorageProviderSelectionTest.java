package com.iunu.realestate.storage;

import com.iunu.realestate.config.FileStorageConfig;
import com.iunu.realestate.config.FileStorageProperties;
import com.iunu.realestate.security.events.SecurityEvents;
import com.iunu.realestate.service.ImageStorage;
import com.iunu.realestate.service.image.CloudinaryClient;
import com.iunu.realestate.service.image.ImageValidator;
import com.iunu.realestate.service.impl.CloudinaryImageStorage;
import com.iunu.realestate.service.impl.LocalImageStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Which ImageStorage bean exists for which configuration - and that a
 * cloudinary deployment without a usable CLOUDINARY_URL refuses to start
 * rather than 500-ing on the first upload.
 */
@DisplayName("File storage provider selection")
class FileStorageProviderSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Support.class, FileStorageConfig.class, LocalImageStorage.class, CloudinaryImageStorage.class)
            .withPropertyValues("app.file-storage.location=target/provider-selection-uploads");

    @Configuration
    @EnableConfigurationProperties(FileStorageProperties.class)
    static class Support {
        @Bean
        ImageValidator imageValidator() {
            return new ImageValidator(mock(SecurityEvents.class));
        }
    }

    @Test
    @DisplayName("local is the default")
    void localIsTheDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).getBean(ImageStorage.class).isInstanceOf(LocalImageStorage.class);
            assertThat(context).doesNotHaveBean(CloudinaryClient.class);
        });
    }

    @Test
    @DisplayName("cloudinary with a blank CLOUDINARY_URL fails at startup, naming the variable")
    void cloudinaryWithoutUrlFails() {
        runner.withPropertyValues("app.file-storage.provider=cloudinary", "app.file-storage.cloudinary.url=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause()
                            .hasMessageContaining("CLOUDINARY_URL")
                            .hasMessageContaining("API Keys");
                });
    }

    @Test
    @DisplayName("cloudinary with a malformed CLOUDINARY_URL fails without echoing the value")
    void cloudinaryWithBadUrlFails() {
        runner.withPropertyValues("app.file-storage.provider=cloudinary",
                        "app.file-storage.cloudinary.url=https://key:not-a-secret-shown@cloud")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause()
                            .hasMessageContaining("CLOUDINARY_URL")
                            .hasMessageNotContaining("not-a-secret-shown");
                });
    }

    @Test
    @DisplayName("cloudinary with a valid CLOUDINARY_URL wires the Cloudinary provider and no local one")
    void cloudinaryWithValidUrl() {
        runner.withPropertyValues("app.file-storage.provider=cloudinary",
                        "app.file-storage.cloudinary.url=cloudinary://123456789012345:abcdefghijklmnop@iunu_cloud",
                        "app.file-storage.cloudinary.folder-root=iunu/test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).getBean(ImageStorage.class).isInstanceOf(CloudinaryImageStorage.class);
                    assertThat(context).doesNotHaveBean(LocalImageStorage.class);
                    assertThat(context.getBean(CloudinaryClient.class).cloudName()).isEqualTo("iunu_cloud");
                    assertThat(context.getBean(CloudinaryImageStorage.class).folderRoot()).isEqualTo("iunu/test");
                });
    }

    @Test
    @DisplayName("an unknown provider fails at startup instead of leaving no ImageStorage at all")
    void unknownProviderFails() {
        runner.withPropertyValues("app.file-storage.provider=s3")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("FILE_STORAGE_PROVIDER");
                });
    }
}
