package com.iunu.realestate.repository;

import com.iunu.realestate.entity.Property;
import com.iunu.realestate.entity.PropertyType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PropertyRepository extends JpaRepository<Property, Long> {
    Page<Property> findByPublishedTrue(Pageable pageable);
    Page<Property> findByPublishedTrueAndType(PropertyType type, Pageable pageable);

    /**
     * Rows the backfill has work for: an Arabic field is NULL while the English
     * it would be translated from is present. Ids only, so the scan does not
     * pull every description into memory before a single translation is made.
     */
    @Query("select p.id from Property p where (p.titleAr is null and p.title <> '') "
            + "or (p.descriptionAr is null and p.description is not null and p.description <> '') "
            + "or (p.locationAr is null and p.location is not null and p.location <> '')")
    List<Long> findIdsMissingArabic();
}
