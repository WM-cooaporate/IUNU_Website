package com.iunu.realestate.service;

import com.iunu.realestate.dto.request.NewsletterRequest;
import com.iunu.realestate.dto.response.MessageResponse;

public interface NewsletterService {
    MessageResponse subscribe(NewsletterRequest request);
}
