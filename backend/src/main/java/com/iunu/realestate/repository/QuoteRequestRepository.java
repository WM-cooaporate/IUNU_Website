package com.iunu.realestate.repository;

import com.iunu.realestate.entity.QuoteRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteRequestRepository extends JpaRepository<QuoteRequest, Long> {
    Page<QuoteRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
