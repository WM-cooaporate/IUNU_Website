package com.iunu.realestate.service.impl;

import com.iunu.realestate.dto.request.ContactRequest;
import com.iunu.realestate.dto.response.ContactMessageResponse;
import com.iunu.realestate.dto.response.MessageResponse;
import com.iunu.realestate.entity.ContactMessage;
import com.iunu.realestate.exception.ResourceNotFoundException;
import com.iunu.realestate.repository.ContactMessageRepository;
import com.iunu.realestate.service.ContactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactServiceImpl implements ContactService {

    private final ContactMessageRepository contactMessageRepository;

    @Override
    @Transactional
    public MessageResponse submit(ContactRequest request) {
        ContactMessage message = ContactMessage.builder()
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .phone(request.phone().trim())
                .email(request.email().trim().toLowerCase())
                .message(request.message().trim())
                .build();

        contactMessageRepository.save(message);
        log.info("New contact message received from {}", message.getEmail());

        return new MessageResponse("Thank you for reaching out. We will get back to you shortly.");
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ContactMessageResponse> listForAdmin(Pageable pageable) {
        return contactMessageRepository.findAllByOrderByCreatedAtDesc(pageable).map(ContactMessageResponse::from);
    }

    @Override
    @Transactional
    public ContactMessageResponse markHandled(Long id) {
        ContactMessage message = contactMessageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contact message not found"));
        message.setHandled(true);
        contactMessageRepository.save(message);
        return ContactMessageResponse.from(message);
    }
}
