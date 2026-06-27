package com.pracisos.auth.service;

import com.pracisos.auth.domain.entity.Tenant;
import com.pracisos.auth.domain.repository.TenantRepository;
import com.pracisos.auth.dto.request.TenantCreateRequest;
import com.pracisos.auth.dto.response.TenantResponse;
import com.pracisos.auth.event.EventPublisher;
import com.pracisos.auth.event.TenantCreatedEvent;
import com.pracisos.auth.exception.DuplicateSlugException;
import com.pracisos.auth.exception.TenantNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TenantService {

    private final TenantRepository tenantRepository;
    private final EventPublisher eventPublisher;

    public TenantService(TenantRepository tenantRepository, EventPublisher eventPublisher) {
        this.tenantRepository = tenantRepository;
        this.eventPublisher = eventPublisher;
    }

    public TenantResponse createTenant(TenantCreateRequest request) {
        if (tenantRepository.existsBySlug(request.slug())) {
            throw new DuplicateSlugException("Tenant slug already exists: " + request.slug());
        }

        Tenant tenant = new Tenant(request.slug(), request.name());
        tenant = tenantRepository.save(tenant);

        eventPublisher.publishTenantCreated(new TenantCreatedEvent(
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
