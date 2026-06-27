package com.pracisos.billing.controller;

import com.pracisos.billing.dto.response.InvoiceResponse;
import com.pracisos.billing.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('PATIENT', 'CLINIC_OWNER')")
    public ResponseEntity<List<InvoiceResponse>> getInvoices(
        @RequestAttribute("tenantId") UUID tenantId,
        @RequestAttribute("userId") UUID userId,
        @RequestAttribute("role") String role
    ) {
        if ("PATIENT".equals(role)) {
            return ResponseEntity.ok(invoiceService.getInvoicesByPatient(tenantId, userId));
        }
        return ResponseEntity.ok(invoiceService.getAllInvoices(tenantId));
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasAnyRole('PATIENT', 'CLINIC_OWNER')")
    public ResponseEntity<InvoiceResponse> getInvoice(
        @RequestAttribute("tenantId") UUID tenantId,
        @PathVariable UUID id
    ) {
        return ResponseEntity.ok(invoiceService.getInvoice(tenantId, id));
    }
}
