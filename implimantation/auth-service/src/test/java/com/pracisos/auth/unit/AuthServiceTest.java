package com.pracisos.auth.unit;

import com.pracisos.auth.domain.entity.Tenant;
import com.pracisos.auth.domain.entity.User;
import com.pracisos.auth.domain.enums.Role;
import com.pracisos.auth.domain.enums.UserStatus;
import com.pracisos.auth.domain.repository.UserRepository;
import com.pracisos.auth.dto.request.LoginRequest;
import com.pracisos.auth.dto.response.LoginResponse;
import com.pracisos.auth.service.AuthService;
import com.pracisos.auth.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private JwtService jwtService;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private AuthService authService;
    
    private User testUser;
    
    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant("maple-health", "Maple Health Clinic");
        tenant.setTenantId(UUID.randomUUID());
        
        testUser = new User(tenant, "admin@maple-health.com", "hashedPassword",
            "Alice", "Smith", Role.CLINIC_OWNER);
        testUser.setUserId(UUID.randomUUID());
        testUser.setStatus(UserStatus.ACTIVE);
    }
    
    @Test
    void login_WithValidCredentials_ReturnsTokens() {
        LoginRequest request = new LoginRequest("admin@maple-health.com", "password123");
        
        when(userRepository.findByEmail("admin@maple-health.com"))
            .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", "hashedPassword"))
            .thenReturn(true);
        when(jwtService.generateAccessToken(any(), any(), any(), any(), any()))
            .thenReturn("access-token-123");
        when(jwtService.generateRefreshToken(any()))
            .thenReturn("refresh-token-456");
        
        LoginResponse response = authService.login(request);
        
        assertNotNull(response);
        assertEquals("access-token-123", response.accessToken());
        assertEquals("Alice Smith", response.fullName());
    }
    
    @Test
    void login_WithInvalidPassword_ThrowsException() {
        LoginRequest request = new LoginRequest("admin@maple-health.com", "wrongpassword");
        
        when(userRepository.findByEmail("admin@maple-health.com"))
            .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongpassword", "hashedPassword"))
            .thenReturn(false);
        
        assertThrows(RuntimeException.class, () -> authService.login(request));
    }
    
    @Test
    void login_WithInactiveUser_ThrowsException() {
        testUser.setStatus(UserStatus.INACTIVE);
        LoginRequest request = new LoginRequest("admin@maple-health.com", "password123");
        
        when(userRepository.findByEmail("admin@maple-health.com"))
            .thenReturn(Optional.of(testUser));
        
        assertThrows(RuntimeException.class, () -> authService.login(request));
    }
}
