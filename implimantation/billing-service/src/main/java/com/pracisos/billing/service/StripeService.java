package com.pracisos.billing.service;

import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StripeService {

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
    }

    public PaymentIntent createPaymentIntent(int amountCents, String customerId, String metadataInvoiceId) {
        try {
            PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount((long) amountCents)
                .setCurrency("cad")
                .setAutomaticPaymentMethods(
                    PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .build()
                )
                .putMetadata("invoice_id", metadataInvoiceId);

            if (customerId != null) {
                paramsBuilder.setCustomer(customerId);
            }

            PaymentIntent intent = PaymentIntent.create(paramsBuilder.build());
            log.info("Created Stripe PaymentIntent {} for invoice {}", intent.getId(), metadataInvoiceId);
            return intent;
        } catch (Exception e) {
            log.error("Failed to create Stripe PaymentIntent: {}", e.getMessage(), e);
            throw new RuntimeException("Payment processing failed", e);
        }
    }

    public Refund createRefund(String paymentIntentId, int amountCents, String reason) {
        try {
            RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId)
                .setAmount((long) amountCents)
                .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
                .putMetadata("reason", reason != null ? reason : "Customer request")
                .build();

            Refund refund = Refund.create(params);
            log.info("Created Stripe Refund {} for PaymentIntent {}", refund.getId(), paymentIntentId);
            return refund;
        } catch (Exception e) {
            log.error("Failed to create Stripe Refund: {}", e.getMessage(), e);
            throw new RuntimeException("Refund processing failed", e);
        }
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }
}
