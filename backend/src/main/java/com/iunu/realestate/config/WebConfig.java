package com.iunu.realestate.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.filter.ShallowEtagHeaderFilter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import java.time.Duration;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.file-storage.location:uploads}")
    private String uploadLocation;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadLocation).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location)
                // Uploaded files are content-addressed: the filename is the
                // SHA-256 of the bytes, so a given URL can never return
                // different content - changing the image changes the URL.
                // That is the one case where a year-long immutable cache is
                // exactly right.
                //
                // Without this Spring Security's default no-store applies and
                // every visitor re-downloads every image on every page view.
                // On a listing page that is most of the bytes, and under load
                // it is origin bandwidth spent re-sending files the browser
                // already has - which is the resource an attacker is trying to
                // exhaust.
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }

    /**
     * Adds an ETag to the public property reads so a repeat visitor - and
     * Cloudflare revalidating a 60-second-old edge copy - gets a 304 with no
     * body instead of the JSON again. On a listing page that is most of the
     * bytes.
     *
     * <p>Scoped tightly, for two reasons. The filter buffers the whole
     * response in memory to hash it, which is waste on anything that is not
     * cacheable; and /api/properties/admin/** serves drafts, where a
     * revalidating cache is exactly what you do not want. The URL pattern
     * cannot express "except admin", so the exclusion is in
     * {@code shouldNotFilter}.
     */
    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> publicPropertyEtagFilter() {
        ShallowEtagHeaderFilter filter = new ShallowEtagHeaderFilter() {
            @Override
            protected boolean shouldNotFilter(HttpServletRequest request) {
                return request.getRequestURI().startsWith("/api/properties/admin");
            }
        };

        FilterRegistrationBean<ShallowEtagHeaderFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/properties", "/api/properties/*");
        registration.setName("publicPropertyEtagFilter");
        return registration;
    }
}
