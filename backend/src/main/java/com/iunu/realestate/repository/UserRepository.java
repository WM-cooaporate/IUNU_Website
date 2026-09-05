package com.iunu.realestate.repository;

import com.iunu.realestate.entity.Role;
import com.iunu.realestate.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    /** Gates the one-time admin bootstrap on startup. */
    boolean existsByRole(Role role);
}
