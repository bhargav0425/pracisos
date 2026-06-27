package com.pracisos.booking.domain.repository;

import com.pracisos.booking.domain.entity.Practitioner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PractitionerRepository extends JpaRepository<Practitioner, UUID> {

    List<Practitioner> findAllByTenantIdAndStatus(UUID tenantId, String status);

    Optional<Practitioner> findByPractitionerIdAndTenantId(UUID practitionerId, UUID tenantId);

    boolean existsByPractitionerIdAndTenantId(UUID practitionerId, UUID tenantId);
}
