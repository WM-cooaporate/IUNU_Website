package com.iunu.realestate.service;

import com.iunu.realestate.dto.request.QuoteRequestDto;
import com.iunu.realestate.dto.response.MessageResponse;
import com.iunu.realestate.dto.response.QuoteRequestResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface QuoteService {
    MessageResponse submit(QuoteRequestDto request);

    Page<QuoteRequestResponse> listForAdmin(Pageable pageable);

    QuoteRequestResponse markHandled(Long id);
}
