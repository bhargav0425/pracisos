package com.pracisos.auth.service;

import com.pracisos.auth.domain.entity.Tenant;
import com.pracisos.auth.domain.repository.TenantRepository;
import com.pracisos.auth.dto.request.TenantCreateRequest;
import com.pracisos.auth.dto.response.TenantResponse;
import com.pracisos.auth.event.EventPublisher;
import com.pracisos.auth.event.payload.TenantCreatedPayload;
import com.pracisos.auth.exception.DuplicateSlugException;
import com.pracisos.auth.exception.TenantNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class TenantService {

    private final TenantRepository tenantRepository;
    private final EventPublisher eventPublisher;

    public TenantResponse createTenant(TenantCreateRequest request) {
        if (tenantRepository.existsBySlug(request.slug())) {
            throw new DuplicateSlugException("Tenant slug already exists: " + request.slug());
        }

        Tenant tenant = new Tenant(request.slug(), request.name());
        tenant = tenantRepository.save(tenant);

        eventPublisher.publishTenantCreated(new TenantCreatedPayload(
            tenant.getTenantId(), tenant.getSlug(), tenant.getName(), tenant.getCreatedAt()
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
}
