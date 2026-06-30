package com.pracisos.auth.unit;

import com.pracisos.auth.domain.entity.Tenant;
import com.pracisos.auth.domain.entity.User;
import com.pracisos.auth.domain.enums.Role;
import com.pracisos.auth.domain.enums.TenantStatus;
import com.pracisos.auth.domain.repository.TenantRepository;
import com.pracisos.auth.domain.repository.UserRepository;
import com.pracisos.auth.dto.request.TenantCreateRequest;
import com.pracisos.auth.dto.response.TenantResponse;
import com.pracisos.auth.event.EventPublisher;
import com.pracisos.auth.event.payload.TenantCreatedPayload;
import com.pracisos.auth.event.payload.UserCreatedPayload;
import com.pracisos.auth.exception.DuplicateSlugException;
import com.pracisos.auth.exception.TenantNotFoundException;
import com.pracisos.auth.service.TenantService;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private TenantService tenantService;

    private TenantCreateRequest createRequest;
    private Tenant testTenant;

    @BeforeEach
    void setUp() {
        createRequest = new TenantCreateRequest(
            "maple-health", "Maple Health Clinic",
            "owner@maple-health.com", "password123", "Jane", "Doe"
        );
        testTenant = new Tenant("maple-health", "Maple Health Clinic");
        testTenant.setTenantId(UUID.randomUUID());
        testTenant.setStatus(TenantStatus.ACTIVE);
    }

    @Test
    void createTenant_WithNewSlug_SavesAndPublishesEvent() {
        when(tenantRepository.existsBySlug(createRequest.slug())).thenReturn(false);
        when(userRepository.existsByEmail(createRequest.ownerEmail())).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenReturn(testTenant);
        when(passwordEncoder.encode(any(String.class))).thenReturn("hashed_password");
        
        User testOwner = new User(testTenant, "owner@maple-health.com", "hashed_password", "Jane", "Doe", Role.CLINIC_OWNER);
        when(userRepository.save(any(User.class))).thenReturn(testOwner);

        TenantResponse response = tenantService.createTenant(createRequest);

        assertNotNull(response);
        assertEquals(testTenant.getTenantId(), response.tenantId());
        assertEquals(createRequest.slug(), response.slug());
        assertEquals(createRequest.name(), response.name());
        assertEquals("ACTIVE", response.status());

        verify(tenantRepository).save(any(Tenant.class));
        verify(userRepository).save(any(User.class));
        verify(eventPublisher).publishTenantCreated(any(TenantCreatedPayload.class));
        verify(eventPublisher).publishUserCreated(any(UserCreatedPayload.class));
    }

    @Test
    void createTenant_WithExistingSlug_ThrowsDuplicateSlugException() {
        when(tenantRepository.existsBySlug(createRequest.slug())).thenReturn(true);

        assertThrows(DuplicateSlugException.class, () -> tenantService.createTenant(createRequest));

        verify(tenantRepository, never()).save(any(Tenant.class));
        verify(userRepository, never()).save(any(User.class));
        verify(eventPublisher, never()).publishTenantCreated(any(TenantCreatedPayload.class));
        verify(eventPublisher, never()).publishUserCreated(any(UserCreatedPayload.class));
    }

    @Test
    void getTenantBySlug_WhenExists_ReturnsTenantResponse() {
        when(tenantRepository.findBySlug("maple-health")).thenReturn(Optional.of(testTenant));

        TenantResponse response = tenantService.getTenantBySlug("maple-health");

        assertNotNull(response);
        assertEquals(testTenant.getTenantId(), response.tenantId());
        assertEquals("maple-health", response.slug());

        verify(tenantRepository).findBySlug("maple-health");
    }

    @Test
    void getTenantBySlug_WhenNotExists_ThrowsTenantNotFoundException() {
        when(tenantRepository.findBySlug("unknown-slug")).thenReturn(Optional.empty());

        assertThrows(TenantNotFoundException.class, () -> tenantService.getTenantBySlug("unknown-slug"));

        verify(tenantRepository).findBySlug("unknown-slug");
    }
}
