package com.iunu.realestate;

import com.iunu.realestate.config.CorsProperties;
import com.iunu.realestate.config.FileStorageProperties;
import com.iunu.realestate.translation.GoogleTranslateProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties({CorsProperties.class, GoogleTranslateProperties.class, FileStorageProperties.class})
public class RealEstateBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(RealEstateBackendApplication.class, args);
    }
}
