package com.iunu.realestate.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iunu.realestate.entity.Role;
import com.iunu.realestate.entity.User;
import com.iunu.realestate.repository.ProjectRepository;
import com.iunu.realestate.repository.UserRepository;
import com.iunu.realestate.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Shared setup for the API tests: full application context, MockMvc, and
 * helpers for creating users and minting their tokens.
 *
 * Tokens are minted directly through JwtService rather than by calling
 * /api/auth/login, so the tests exercise authorization without burning
 * through the login rate limiter (10/min per IP, shared across the cached
 * context). One dedicated test covers the real login round-trip.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UserRepository userRepository;
    @Autowired protected ProjectRepository projectRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected JwtService jwtService;

    protected User createUser(String email, String rawPassword, Role role) {
        return userRepository.save(User.builder()
                .fullName("Test " + role)
                .email(email.toLowerCase())
                .phone("+20 100 000 0000")
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .build());
    }

    protected String bearerFor(User user) {
        return "Bearer " + jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
    }

    /** Convenience: a fresh ADMIN plus its Authorization header value. */
    protected String adminBearer() {
        return bearerFor(createUser("admin-" + System.nanoTime() + "@iunu.test", "Password1", Role.ADMIN));
    }

    protected String userBearer() {
        return bearerFor(createUser("user-" + System.nanoTime() + "@iunu.test", "Password1", Role.USER));
    }

    /**
     * The 8-byte PNG signature followed by filler. ImageStorageService checks
     * the file signature rather than trusting the declared Content-Type, so
     * upload fixtures have to look like the format they claim to be.
     */
    protected static byte[] pngBytes() {
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        byte[] content = new byte[signature.length + 16];
        System.arraycopy(signature, 0, content, 0, signature.length);
        return content;
    }
}
