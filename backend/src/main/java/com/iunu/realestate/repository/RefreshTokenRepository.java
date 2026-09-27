package com.iunu.realestate.repository;

import com.iunu.realestate.entity.RefreshToken;
import com.iunu.realestate.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken r set r.revoked = true where r.user = :user")
    void revokeAllForUser(User user);

    @Modifying
    @Query("delete from RefreshToken r where r.expiresAt < :now or r.revoked = true")
    void deleteExpiredOrRevoked(Instant now);
}
