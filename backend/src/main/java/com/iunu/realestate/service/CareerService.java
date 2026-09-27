package com.iunu.realestate.service;

import org.springframework.web.multipart.MultipartFile;

public interface CareerService {
    void sendApplication(String fullName, String email, String phone, String position, String message, MultipartFile resume);
}
