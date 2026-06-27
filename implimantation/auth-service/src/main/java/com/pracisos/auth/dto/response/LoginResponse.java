package com.pracisos.auth.dto.response;

import com.pracisos.auth.domain.enums.Role;
import java.util.UUID;

public record LoginResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    UUID userId,
    String email,
    String fullName,
    Role role,
    UUID tenantId,
    String tenantSlug
) {}
