package com.iunu.realestate.service.impl;

import com.iunu.realestate.dto.request.CreateAdminUserRequest;
import com.iunu.realestate.dto.response.UserResponse;
import com.iunu.realestate.entity.AuditAction;
import com.iunu.realestate.entity.Role;
import com.iunu.realestate.entity.User;
import com.iunu.realestate.exception.BadRequestException;
import com.iunu.realestate.repository.UserRepository;
import com.iunu.realestate.service.AdminUserService;
import com.iunu.realestate.service.AuditLogService;
import com.iunu.realestate.util.LogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> list(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserResponse::from);
    }

    @Override
    @Transactional
    public UserResponse create(CreateAdminUserRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BadRequestException("An account with this email already exists");
        }

        User user = User.builder()
                .fullName(request.fullName().trim())
                .email(normalizedEmail)
                .phone(request.phone() == null ? null : request.phone().trim())
                .password(passwordEncoder.encode(request.password()))
                .role(request.role() == null ? Role.ADMIN : request.role())
                .build();

        userRepository.save(user);
        log.info("Admin created a new {} account: {}", user.getRole(), LogSanitizer.maskEmail(normalizedEmail));
        // The single most important row in the trail: a second admin account
        // is how a takeover makes itself permanent.
        auditLogService.record(AuditAction.ADMIN_USER_CREATED, "USER", user.getId(),
                "account created; role " + user.getRole());

        // UserResponse deliberately has no password field, so the hash cannot
        // leak through this (or any other) endpoint.
        return UserResponse.from(user);
    }
}
