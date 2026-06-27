package com.pracisos.auth.unit;

import com.pracisos.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private final String secret = "your-256-bit-secret-key-here-change-in-production-your-256-bit-secret-key-here-change-in-production";
    private final long accessExpiry = 900;
    private final long refreshExpiry = 604800;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(secret, accessExpiry, refreshExpiry);
    }

    @Test
    void generateAndValidateAccessToken_WithValidParams_Succeeds() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String email = "doctor@clinic.com";
        String role = "PRACTITIONER";
        String permissions = "READ_CHARTS,WRITE_CHARTS";

        String token = jwtService.generateAccessToken(userId, email, role, tenantId, permissions);
        assertNotNull(token);

        Claims claims = jwtService.validateToken(token);
        assertEquals(userId.toString(), claims.getSubject());
        assertEquals(email, claims.get("email", String.class));
        assertEquals(role, claims.get("role", String.class));
        assertEquals(tenantId.toString(), claims.get("tenant_id", String.class));
        assertEquals(permissions, claims.get("permissions", String.class));
    }

    @Test
    void extractClaims_ExtractsCorrectly() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String email = "owner@clinic.com";
        String role = "CLINIC_OWNER";
        String permissions = "ALL";

        String token = jwtService.generateAccessToken(userId, email, role, tenantId, permissions);

        assertEquals(userId, jwtService.extractUserId(token));
        assertEquals(role, jwtService.extractRole(token));
        assertEquals(tenantId, jwtService.extractTenantId(token));
    }

    @Test
    void generateAndValidateRefreshToken_Succeeds() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateRefreshToken(userId);
        assertNotNull(token);

        Claims claims = jwtService.validateToken(token);
        assertEquals(userId.toString(), claims.getSubject());
        assertEquals("refresh", claims.get("type", String.class));
    }

    @Test
    void validateToken_WithInvalidToken_ThrowsRuntimeException() {
        assertThrows(RuntimeException.class, () -> jwtService.validateToken("invalid.token.here"));
    }
}
