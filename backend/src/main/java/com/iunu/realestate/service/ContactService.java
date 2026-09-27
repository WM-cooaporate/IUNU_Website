package com.iunu.realestate.service;

import com.iunu.realestate.dto.request.ContactRequest;
import com.iunu.realestate.dto.response.ContactMessageResponse;
import com.iunu.realestate.dto.response.MessageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ContactService {
    MessageResponse submit(ContactRequest request);

    Page<ContactMessageResponse> listForAdmin(Pageable pageable);

    ContactMessageResponse markHandled(Long id);
}
