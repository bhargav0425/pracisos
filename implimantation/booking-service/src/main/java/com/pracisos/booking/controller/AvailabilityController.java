package com.pracisos.booking.controller;

import com.pracisos.booking.domain.entity.AvailabilityTemplate;
import com.pracisos.booking.dto.request.AvailabilityCreateRequest;
import com.pracisos.booking.dto.response.SlotResponse;
import com.pracisos.booking.service.AvailabilityService;
import com.pracisos.booking.service.TimeSlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/booking")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;
    private final TimeSlotService timeSlotService;

    @PostMapping("/availability")
    @PreAuthorize("hasRole('PRACTITIONER')")
    public ResponseEntity<AvailabilityTemplate> createAvailability(
        @RequestAttribute("tenantId") UUID tenantId,
        @RequestAttribute("userId") UUID practitionerId,
        @Valid @RequestBody AvailabilityCreateRequest request
    ) {
        // Ensure practitioner can only set their own availability
        if (!practitionerId.equals(request.practitionerId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(availabilityService.createTemplate(tenantId, request));
    }

    @GetMapping("/practitioners/{id}/slots")
    @PreAuthorize("hasAnyRole('PATIENT', 'PRACTITIONER', 'RECEPTIONIST', 'CLINIC_OWNER')")
    public ResponseEntity<List<SlotResponse>> getAvailableSlots(
        @RequestAttribute("tenantId") UUID tenantId,
        @PathVariable UUID id,
        @RequestParam Instant from,
        @RequestParam Instant to
    ) {
        return ResponseEntity.ok(timeSlotService.getAvailableSlots(tenantId, id, from, to));
    }

    @GetMapping("/availability")
    @PreAuthorize("hasRole('PRACTITIONER')")
    public ResponseEntity<List<AvailabilityTemplate>> getMyAvailability(
        @RequestAttribute("tenantId") UUID tenantId,
        @RequestAttribute("userId") UUID practitionerId
    ) {
        return ResponseEntity.ok(availabilityService.getTemplatesByPractitioner(tenantId, practitionerId));
    }
}
