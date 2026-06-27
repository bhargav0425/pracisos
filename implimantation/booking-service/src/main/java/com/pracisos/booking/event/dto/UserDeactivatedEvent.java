package com.pracisos.booking.event.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDeactivatedEvent {
    private UUID userId;
    private UUID tenantId;
    private String reason;
    private Instant deactivatedAt;
}
