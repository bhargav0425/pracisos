package com.pracisos.booking.domain.repository;

import com.pracisos.booking.domain.entity.AvailabilityTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AvailabilityTemplateRepository extends JpaRepository<AvailabilityTemplate, UUID> {

    List<AvailabilityTemplate> findAllByTenantIdAndPractitionerIdAndIsActive(
        UUID tenantId, UUID practitionerId, Boolean isActive);

    List<AvailabilityTemplate> findAllByTenantIdAndIsActive(UUID tenantId, Boolean isActive);
}
