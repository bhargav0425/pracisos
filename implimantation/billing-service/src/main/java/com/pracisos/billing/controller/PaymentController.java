package com.pracisos.billing.controller;

import com.pracisos.billing.dto.request.RefundRequest;
import com.pracisos.billing.dto.response.PaymentResponse;
import com.pracisos.billing.service.PaymentService;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/invoices/{id}/pay")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<Map<String, Object>> initiatePayment(
        @RequestAttribute("tenantId") UUID tenantId,
        @PathVariable UUID id
    ) {
        PaymentIntent intent = paymentService.createPaymentIntent(tenantId, id);
        Map<String, Object> response = new HashMap<>();
        response.put("id", intent.getId());
        response.put("client_secret", intent.getClientSecret());
        response.put("status", intent.getStatus());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/invoices/{id}/simulate-payment")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<Void> simulatePayment(
        @RequestAttribute("tenantId") UUID tenantId,
        @PathVariable UUID id
    ) {
        paymentService.simulatePaymentSuccess(tenantId, id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/invoices/{id}/refund")
    @PreAuthorize("hasRole('CLINIC_OWNER')")
    public ResponseEntity<Map<String, Object>> processRefund(
        @RequestAttribute("tenantId") UUID tenantId,
        @PathVariable UUID id,
        @RequestBody RefundRequest request
    ) {
        Refund refund = paymentService.processRefund(tenantId, id, request.amountCents(), request.reason());
        Map<String, Object> response = new HashMap<>();
        response.put("id", refund.getId());
        response.put("status", refund.getStatus());
        response.put("amount", refund.getAmount());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/invoices/{id}/payments")
    @PreAuthorize("hasAnyRole('PATIENT', 'CLINIC_OWNER')")
    public ResponseEntity<List<PaymentResponse>> getPayments(
        @PathVariable UUID id
    ) {
        return ResponseEntity.ok(paymentService.getPaymentsByInvoice(id));
    }
}
