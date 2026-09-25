package com.iunu.realestate.service;

import com.iunu.realestate.dto.request.PropertyRequest;
import com.iunu.realestate.dto.response.PropertyPublicResponse;
import com.iunu.realestate.dto.response.PropertyResponse;
import com.iunu.realestate.entity.PropertyType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PropertyService {
    /** Published rows only, in the public shape. Never drafts, whoever is asking. */
    Page<PropertyPublicResponse> listPublished(PropertyType type, Pageable pageable);

    /** A published row in the public shape; a missing or draft id is not found. */
    PropertyPublicResponse getPublishedById(Long id);

    Page<PropertyResponse> listAllForAdmin(Pageable pageable);

    PropertyResponse getByIdForAdmin(Long id);

    PropertyResponse create(PropertyRequest request);

    PropertyResponse update(Long id, PropertyRequest request);

    void delete(Long id);
}
