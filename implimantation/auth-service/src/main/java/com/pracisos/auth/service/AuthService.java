package com.pracisos.auth.service;

import com.pracisos.auth.domain.entity.User;
import com.pracisos.auth.domain.enums.Role;
import com.pracisos.auth.domain.enums.UserStatus;
import com.pracisos.auth.domain.repository.UserRepository;
import com.pracisos.auth.dto.request.LoginRequest;
import com.pracisos.auth.dto.response.LoginResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, JwtService jwtService, 
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new RuntimeException("Account is not active");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }

        String permissions = String.join(",", getPermissionsForRole(user.getRole()));

        String accessToken = jwtService.generateAccessToken(
            user.getUserId(), user.getEmail(), user.getRole().name(),
            user.getTenantId(), permissions
        );
        String refreshToken = jwtService.generateRefreshToken(user.getUserId());

        return new LoginResponse(
            accessToken, refreshToken, "Bearer", 900,
            user.getUserId(), user.getEmail(), user.getFullName(),
            user.getRole(), user.getTenantId(),
            user.getTenant() != null ? user.getTenant().getSlug() : null
        );
    }

    private String[] getPermissionsForRole(Role role) {
        return switch (role) {
            case SYSTEM_ADMIN -> new String[]{"ALL"};
            case CLINIC_OWNER -> new String[]{"READ_USERS", "WRITE_USERS", "READ_BOOKINGS", "READ_CHARTS", "READ_BILLING", "READ_AUDIT"};
            case PRACTITIONER -> new String[]{"READ_AVAILABILITY", "WRITE_AVAILABILITY", "READ_CHARTS", "WRITE_CHARTS", "READ_BOOKINGS"};
            case RECEPTIONIST -> new String[]{"READ_BOOKINGS", "WRITE_BOOKINGS", "READ_PATIENTS", "READ_AVAILABILITY"};
            case PATIENT -> new String[]{"READ_OWN_BOOKINGS", "WRITE_OWN_BOOKINGS", "READ_OWN_INVOICES", "WRITE_PAYMENTS"};
        };
    }
}
