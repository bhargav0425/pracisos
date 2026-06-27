package com.pracisos.booking.event.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCreatedEvent {
    private UUID userId;
    private UUID tenantId;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
    private Instant createdAt;
}
