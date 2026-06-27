package com.pracisos.billing.service;

import com.pracisos.billing.domain.entity.Invoice;
import com.pracisos.billing.domain.repository.InvoiceRepository;
import com.pracisos.billing.dto.response.RevenueResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RevenueService {

    private final InvoiceRepository invoiceRepository;

    public RevenueResponse getRevenue(UUID tenantId, Instant from, Instant to) {
        Long totalRevenue = invoiceRepository.calculateRevenue(tenantId, from, to);
        Long paidCount = invoiceRepository.countPaidInvoices(tenantId, from, to);

        // Calculate pending (issued but not paid)
        Long pendingRevenue = invoiceRepository.findAllByTenantIdAndStatus(tenantId, "ISSUED")
            .stream()
            .mapToLong(Invoice::getAmountCents)
            .sum();

        // Calculate refunded
        Long refundedRevenue = invoiceRepository.findAllByTenantIdAndStatus(tenantId, "REFUNDED")
            .stream()
            .mapToLong(i -> i.getRefundAmountCents() != null ? i.getRefundAmountCents() : 0)
            .sum();

        return new RevenueResponse(
            totalRevenue != null ? totalRevenue : 0,
            formatCents(totalRevenue != null ? totalRevenue : 0),
            paidCount != null ? paidCount : 0,
            pendingRevenue,
            formatCents(pendingRevenue),
            refundedRevenue,
            formatCents(refundedRevenue)
        );
    }

    private String formatCents(long cents) {
        return String.format("$%.2f", cents / 100.0);
    }
}
