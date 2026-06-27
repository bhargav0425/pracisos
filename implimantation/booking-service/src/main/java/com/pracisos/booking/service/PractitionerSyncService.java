package com.pracisos.booking.service;

import com.pracisos.booking.domain.entity.Practitioner;
import com.pracisos.booking.domain.repository.PractitionerRepository;
import com.pracisos.booking.event.dto.UserCreatedEvent;
import com.pracisos.booking.event.dto.UserDeactivatedEvent;
import com.pracisos.booking.event.dto.UserUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PractitionerSyncService {

    private final PractitionerRepository practitionerRepository;

    public void syncUserCreated(UserCreatedEvent event) {
        if (!"PRACTITIONER".equals(event.getRole()) && !"RECEPTIONIST".equals(event.getRole())) {
            return;
        }

        if (practitionerRepository.existsById(event.getUserId())) {
            log.warn("Practitioner {} already exists, skipping", event.getUserId());
            return;
        }

        Practitioner practitioner = Practitioner.builder()
            .practitionerId(event.getUserId())
            .tenantId(event.getTenantId())
            .firstName(event.getFirstName())
            .lastName(event.getLastName())
            .email(event.getEmail())
            .status("ACTIVE")
            .build();

        practitionerRepository.save(practitioner);
        log.info("Synced practitioner {} to booking-service", event.getUserId());
    }

    public void syncUserUpdated(UserUpdatedEvent event) {
        practitionerRepository.findById(event.getUserId()).ifPresentOrElse(
            practitioner -> {
                practitioner.setFirstName(event.getFirstName());
                practitioner.setLastName(event.getLastName());
                practitioner.setEmail(event.getEmail());
                if (event.getStatus() != null) {
                    practitioner.setStatus(event.getStatus());
                }
                practitionerRepository.save(practitioner);
                log.info("Updated practitioner {} in booking-service", event.getUserId());
            },
            () -> log.warn("Practitioner {} not found for update", event.getUserId())
        );
    }

    public void syncUserDeactivated(UserDeactivatedEvent event) {
        practitionerRepository.findById(event.getUserId()).ifPresent(practitioner -> {
            practitioner.setStatus("INACTIVE");
            practitionerRepository.save(practitioner);
            log.info("Deactivated practitioner {} in booking-service", event.getUserId());
        });
    }
}
