package com.iunu.realestate.service;

import com.iunu.realestate.dto.request.CreateAdminUserRequest;
import com.iunu.realestate.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminUserService {

    Page<UserResponse> list(Pageable pageable);

    UserResponse create(CreateAdminUserRequest request);
}
