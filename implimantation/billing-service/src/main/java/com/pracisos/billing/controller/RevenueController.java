package com.pracisos.billing.controller;

import com.pracisos.billing.dto.response.RevenueResponse;
import com.pracisos.billing.service.RevenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class RevenueController {

    private final RevenueService revenueService;

    @GetMapping("/revenue")
    @PreAuthorize("hasRole('CLINIC_OWNER')")
    public ResponseEntity<RevenueResponse> getRevenue(
        @RequestAttribute("tenantId") UUID tenantId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ResponseEntity.ok(revenueService.getRevenue(tenantId, from, to));
    }
}
