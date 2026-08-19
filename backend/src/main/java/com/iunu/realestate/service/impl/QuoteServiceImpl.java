package com.iunu.realestate.service.impl;

import com.iunu.realestate.dto.request.QuoteRequestDto;
import com.iunu.realestate.dto.response.MessageResponse;
import com.iunu.realestate.dto.response.QuoteRequestResponse;
import com.iunu.realestate.entity.QuoteRequest;
import com.iunu.realestate.exception.ResourceNotFoundException;
import com.iunu.realestate.repository.QuoteRequestRepository;
import com.iunu.realestate.service.QuoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteServiceImpl implements QuoteService {

    private final QuoteRequestRepository quoteRequestRepository;

    @Override
    @Transactional
    public MessageResponse submit(QuoteRequestDto request) {
        QuoteRequest quoteRequest = QuoteRequest.builder()
                .name(request.name().trim())
                .phone(request.phone().trim())
                .city(request.city().trim())
                .email(request.email().trim().toLowerCase())
                .project(request.project())
                .whatsapp(request.whatsapp() != null ? request.whatsapp().trim() : null)
                .spaceType(request.spaceType())
                .build();

        quoteRequestRepository.save(quoteRequest);
        log.info("New quote request received from {}", quoteRequest.getEmail());

        return new MessageResponse("Your request has been submitted successfully.");
    }

    @Override
    @Transactional(readOnly = true)
    public Page<QuoteRequestResponse> listForAdmin(Pageable pageable) {
        return quoteRequestRepository.findAllByOrderByCreatedAtDesc(pageable).map(QuoteRequestResponse::from);
    }

    @Override
    @Transactional
    public QuoteRequestResponse markHandled(Long id) {
        QuoteRequest quoteRequest = quoteRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quote request not found"));
        quoteRequest.setHandled(true);
        quoteRequestRepository.save(quoteRequest);
        return QuoteRequestResponse.from(quoteRequest);
    }
}
