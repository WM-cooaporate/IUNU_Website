package com.iunu.realestate.repository;

import com.iunu.realestate.entity.Property;
import com.iunu.realestate.entity.PropertyType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyRepository extends JpaRepository<Property, Long> {
    Page<Property> findByPublishedTrue(Pageable pageable);
    Page<Property> findByPublishedTrueAndType(PropertyType type, Pageable pageable);
}
