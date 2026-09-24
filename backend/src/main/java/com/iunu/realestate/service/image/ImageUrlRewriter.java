package com.iunu.realestate.service.image;

import com.iunu.realestate.config.CacheConfig;
import com.iunu.realestate.entity.Project;
import com.iunu.realestate.entity.Property;
import com.iunu.realestate.repository.ProjectRepository;
import com.iunu.realestate.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The short database steps of the legacy-upload migration, on their own bean
 * so ImageMigrationService calls them through the Spring proxy (a self-invoked
 * {@code @Transactional} method is not transactional at all). The same
 * pattern as PropertyArabicWriter: nothing here touches the network, and each
 * write is one short transaction per row.
 */
@Component
@RequiredArgsConstructor
public class ImageUrlRewriter {

    private final PropertyRepository propertyRepository;
    private final ProjectRepository projectRepository;

    /** A row's title and every image URL on it, cover first. */
    public record ImageRefs(String title, List<String> urls) {}

    @Transactional(readOnly = true)
    public List<Long> propertyIdsWithImagesStartingWith(String prefix) {
        return propertyRepository.findIdsWithImageStartingWith(prefix);
    }

    @Transactional(readOnly = true)
    public List<Long> projectIdsWithCoverStartingWith(String prefix) {
        return projectRepository.findIdsWithCoverStartingWith(prefix);
    }

    @Transactional(readOnly = true)
    public Optional<ImageRefs> readProperty(Long id) {
        return propertyRepository.findById(id).map(property -> {
            List<String> urls = new ArrayList<>();
            if (property.getCoverImageUrl() != null) urls.add(property.getCoverImageUrl());
            if (property.getImageUrls() != null) urls.addAll(property.getImageUrls());
            return new ImageRefs(property.getTitle(), urls);
        });
    }

    @Transactional(readOnly = true)
    public Optional<ImageRefs> readProject(Long id) {
        return projectRepository.findById(id).map(project -> new ImageRefs(project.getTitle(),
                project.getCoverImageUrl() == null ? List.of() : List.of(project.getCoverImageUrl())));
    }

    /**
     * Swaps each URL in {@code replacements} for its new value, re-reading the
     * row first so an admin edit made while the upload was in flight wins.
     *
     * @return the old URLs actually replaced on this row
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @CacheEvict(cacheNames = {CacheConfig.PUBLIC_PROPERTY_LIST, CacheConfig.PUBLIC_PROPERTY_BY_ID}, allEntries = true)
    public Set<String> rewriteProperty(Long id, Map<String, String> replacements) {
        Property property = propertyRepository.findById(id).orElse(null);
        if (property == null) return Set.of();
        Set<String> replaced = new HashSet<>();

        String cover = property.getCoverImageUrl();
        if (cover != null && replacements.containsKey(cover)) {
            property.setCoverImageUrl(replacements.get(cover));
            replaced.add(cover);
        }
        if (property.getImageUrls() != null) {
            List<String> gallery = new ArrayList<>(property.getImageUrls().size());
            for (String url : property.getImageUrls()) {
                if (url != null && replacements.containsKey(url)) {
                    gallery.add(replacements.get(url));
                    replaced.add(url);
                } else {
                    gallery.add(url);
                }
            }
            property.setImageUrls(gallery);
        }
        if (!replaced.isEmpty()) propertyRepository.save(property);
        return replaced;
    }

    /** Same as {@link #rewriteProperty} for a project's cover. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Set<String> rewriteProject(Long id, Map<String, String> replacements) {
        Project project = projectRepository.findById(id).orElse(null);
        if (project == null) return Set.of();
        String cover = project.getCoverImageUrl();
        if (cover == null || !replacements.containsKey(cover)) return Set.of();
        project.setCoverImageUrl(replacements.get(cover));
        projectRepository.save(project);
        return Set.of(cover);
    }
}
