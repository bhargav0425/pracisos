package com.pracisos.billing.dto.response;

public record RevenueResponse(
    Long totalRevenueCents,
    String formattedTotalRevenue,
    Long paidInvoiceCount,
    Long pendingRevenueCents,
    String formattedPendingRevenue,
    Long refundedRevenueCents,
    String formattedRefundedRevenue
) {}
