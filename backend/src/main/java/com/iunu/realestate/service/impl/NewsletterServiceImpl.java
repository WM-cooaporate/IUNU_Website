package com.iunu.realestate.service.impl;

import com.iunu.realestate.dto.request.NewsletterRequest;
import com.iunu.realestate.dto.response.MessageResponse;
import com.iunu.realestate.entity.NewsletterSubscriber;
import com.iunu.realestate.repository.NewsletterSubscriberRepository;
import com.iunu.realestate.service.NewsletterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NewsletterServiceImpl implements NewsletterService {

    private final NewsletterSubscriberRepository newsletterSubscriberRepository;

    @Override
    @Transactional
    public MessageResponse subscribe(NewsletterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (!newsletterSubscriberRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            NewsletterSubscriber subscriber = NewsletterSubscriber.builder()
                    .email(normalizedEmail)
                    .build();
            newsletterSubscriberRepository.save(subscriber);
        }

        // Idempotent response either way - do not reveal whether the email was already subscribed.
        return new MessageResponse("You have been subscribed successfully.");
    }
}
