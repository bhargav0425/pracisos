package com.pracisos.auth.controller;

import com.pracisos.auth.dto.request.TenantCreateRequest;
import com.pracisos.auth.dto.response.TenantResponse;
import com.pracisos.auth.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping("/register")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<TenantResponse> createTenant(
        @Valid @RequestBody TenantCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(tenantService.createTenant(request));
    }

    @GetMapping("/tenants/{slug}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<TenantResponse> getTenant(@PathVariable String slug) {
        return ResponseEntity.ok(tenantService.getTenantBySlug(slug));
    }
}
