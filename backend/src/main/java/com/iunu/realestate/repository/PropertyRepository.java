package com.iunu.realestate.repository;

import com.iunu.realestate.entity.Property;
import com.iunu.realestate.entity.PropertyType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PropertyRepository extends JpaRepository<Property, Long> {
    Page<Property> findByPublishedTrue(Pageable pageable);
    Page<Property> findByPublishedTrueAndType(PropertyType type, Pageable pageable);

    /**
     * Rows the backfill has work for: an Arabic field is NULL while the English
     * it would be translated from is present. Ids only, so the scan does not
     * pull every description into memory before a single translation is made.
     */
    /**
     * True when some property other than {@code excludeId} still points at
     * {@code url}, either as its cover or in its gallery.
     *
     * <p>Images are content-addressed, so the same file legitimately backs two
     * properties. This is the check that stops an edit to one of them deleting
     * the file out from under the other. It replaces a {@code findAll()} that
     * ran on every admin save and delete.
     *
     * <p>{@code member of} resolves against the property_images collection
     * table, which is indexed on property_id; the cover column is a plain
     * equality test. Both sides are parameters - nothing here is concatenated.
     */
    @Query("select count(p) > 0 from Property p "
            + "where p.id <> :excludeId and (p.coverImageUrl = :url or :url member of p.imageUrls)")
    boolean isImageUsedByOtherProperty(@Param("url") String url, @Param("excludeId") Long excludeId);

    /** Every property cover in use, for the orphan sweep's "is it still referenced?" check. */
    @Query("select p.coverImageUrl from Property p where p.coverImageUrl is not null")
    List<String> findAllCoverImageUrls();

    /** Every gallery image in use, across all properties. Duplicates are expected (shared images). */
    @Query("select u from Property p join p.imageUrls u")
    List<String> findAllGalleryImageUrls();

    /** Properties with a cover or gallery image under {@code prefix} - the legacy-upload migration's work list. */
    @Query("select distinct p.id from Property p left join p.imageUrls u "
            + "where p.coverImageUrl like concat(:prefix, '%') or u like concat(:prefix, '%')")
    List<Long> findIdsWithImageStartingWith(@Param("prefix") String prefix);

    @Query("select p.id from Property p where (p.titleAr is null and p.title <> '') "
            + "or (p.descriptionAr is null and p.description is not null and p.description <> '') "
            + "or (p.locationAr is null and p.location is not null and p.location <> '')")
    List<Long> findIdsMissingArabic();
}
