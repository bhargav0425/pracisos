package com.pracisos.booking.controller;

import com.pracisos.booking.dto.response.PractitionerResponse;
import com.pracisos.booking.service.PractitionerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/booking")
@RequiredArgsConstructor
public class PractitionerController {

    private final PractitionerService practitionerService;

    @GetMapping("/practitioners")
    @PreAuthorize("hasAnyRole('PATIENT', 'PRACTITIONER', 'RECEPTIONIST', 'CLINIC_OWNER')")
    public ResponseEntity<List<PractitionerResponse>> getPractitioners(
        @RequestAttribute("tenantId") UUID tenantId
    ) {
        return ResponseEntity.ok(practitionerService.getPractitionersByTenant(tenantId));
    }

    @GetMapping("/practitioners/{id}")
    @PreAuthorize("hasAnyRole('PATIENT', 'PRACTITIONER', 'RECEPTIONIST', 'CLINIC_OWNER')")
    public ResponseEntity<PractitionerResponse> getPractitioner(
        @RequestAttribute("tenantId") UUID tenantId,
        @PathVariable UUID id
    ) {
        return ResponseEntity.ok(practitionerService.getPractitioner(tenantId, id));
    }
}
