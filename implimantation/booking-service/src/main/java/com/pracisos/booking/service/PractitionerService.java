package com.pracisos.booking.service;

import com.pracisos.booking.domain.entity.Practitioner;
import com.pracisos.booking.domain.repository.PractitionerRepository;
import com.pracisos.booking.dto.response.PractitionerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PractitionerService {

    private final PractitionerRepository practitionerRepository;

    @Transactional(readOnly = true)
    public List<PractitionerResponse> getPractitionersByTenant(UUID tenantId) {
        return practitionerRepository.findAllByTenantIdAndStatus(tenantId, "ACTIVE")
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PractitionerResponse getPractitioner(UUID tenantId, UUID practitionerId) {
        Practitioner practitioner = practitionerRepository
            .findByPractitionerIdAndTenantId(practitionerId, tenantId)
            .orElseThrow(() -> new RuntimeException("Practitioner not found"));
        return mapToResponse(practitioner);
    }

    private PractitionerResponse mapToResponse(Practitioner p) {
        return new PractitionerResponse(
            p.getPractitionerId(),
            p.getFirstName(),
            p.getLastName(),
            p.getFullName(),
            p.getEmail(),
            p.getStatus()
        );
    }
}
