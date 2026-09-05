package com.iunu.realestate.repository;

import com.iunu.realestate.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    /** Public listing: published only, newest first. */
    Page<Project> findByPublishedTrueOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Public detail lookup. Filtering in the query (rather than loading and
     * checking published afterwards) keeps an unpublished project
     * indistinguishable from a non-existent one - both are a plain 404.
     */
    Optional<Project> findByIdAndPublishedTrue(Long id);

    Page<Project> findAllByOrderByCreatedAtDesc(Pageable pageable);

    boolean existsByCoverImageUrlAndIdNot(String coverImageUrl, Long id);
}
