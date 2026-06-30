package com.pracisos.auth.service;

import com.pracisos.auth.domain.entity.Tenant;
import com.pracisos.auth.domain.entity.User;
import com.pracisos.auth.domain.enums.Role;
import com.pracisos.auth.domain.enums.UserStatus;
import com.pracisos.auth.domain.repository.TenantRepository;
import com.pracisos.auth.domain.repository.UserRepository;
import com.pracisos.auth.dto.request.TenantCreateRequest;
import com.pracisos.auth.dto.response.TenantResponse;
import com.pracisos.auth.event.EventPublisher;
import com.pracisos.auth.event.payload.TenantCreatedPayload;
import com.pracisos.auth.event.payload.UserCreatedPayload;
import com.pracisos.auth.exception.DuplicateSlugException;
import com.pracisos.auth.exception.TenantNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EventPublisher eventPublisher;

    public TenantResponse createTenant(TenantCreateRequest request) {
        if (tenantRepository.existsBySlug(request.slug())) {
            throw new DuplicateSlugException("Tenant slug already exists: " + request.slug());
        }
        if (userRepository.existsByEmail(request.ownerEmail())) {
            throw new RuntimeException("Email already registered: " + request.ownerEmail());
        }

        Tenant tenant = new Tenant(request.slug(), request.name());
        tenant = tenantRepository.save(tenant);

        // Create the owner user
        String passwordHash = passwordEncoder.encode(request.ownerPassword());
        User owner = new User(
            tenant,
            request.ownerEmail(),
            passwordHash,
            request.ownerFirstName(),
            request.ownerLastName(),
            Role.CLINIC_OWNER
        );
        owner.setStatus(UserStatus.ACTIVE);
        owner = userRepository.save(owner);

        // Publish events
        eventPublisher.publishTenantCreated(new TenantCreatedPayload(
            tenant.getTenantId(), tenant.getSlug(), tenant.getName(), tenant.getCreatedAt()
        ));

        eventPublisher.publishUserCreated(new UserCreatedPayload(
            owner.getUserId(), tenant.getTenantId(), owner.getEmail(),
            owner.getFirstName(), owner.getLastName(),
            owner.getRole().name(), owner.getCreatedAt()
        ));

        return new TenantResponse(
            tenant.getTenantId(), tenant.getSlug(), tenant.getName(),
            tenant.getStatus().name(), tenant.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenantBySlug(String slug) {
        Tenant tenant = tenantRepository.findBySlug(slug)
            .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + slug));
        return new TenantResponse(
            tenant.getTenantId(), tenant.getSlug(), tenant.getName(),
            tenant.getStatus().name(), tenant.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> getAllTenants() {
        return tenantRepository.findAll().stream()
            .map(tenant -> new TenantResponse(
                tenant.getTenantId(), tenant.getSlug(), tenant.getName(),
                tenant.getStatus().name(), tenant.getCreatedAt()
            ))
            .collect(Collectors.toList());
    }
}
