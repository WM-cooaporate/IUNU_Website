package com.iunu.realestate.repository;

import com.iunu.realestate.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    boolean existsByCoverImageUrl(String coverImageUrl);

    /** Every cover in use, for the orphan sweep's "is it still referenced?" check. */
    @Query("select p.coverImageUrl from Project p where p.coverImageUrl is not null")
    List<String> findAllCoverImageUrls();

    /** Projects whose cover starts with {@code prefix} - the legacy-upload migration's work list. */
    @Query("select p.id from Project p where p.coverImageUrl like concat(:prefix, '%')")
    List<Long> findIdsWithCoverStartingWith(@Param("prefix") String prefix);
}
