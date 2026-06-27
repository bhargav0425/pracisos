# PRACISOS PLATFORM
## AUTH-SERVICE IMPLEMENTATION GUIDE
### Phase 1: Incremental Development — Service + Frontend Slice

---

## DOCUMENT CONTROL

| Field | Value |
|-------|-------|
| **Version** | 1.0.0 |
| **Status** | READY FOR IMPLEMENTATION |
| **Date** | 2026-06-24 |
| **Phase** | 1 of 7 |
| **Service** | auth-service |
| **Scope** | Authentication, Authorization, Tenant Management, User Management |
| **Prerequisite** | Master Specification v1.0.0 |

---

## 1. GOAL

Build a working **auth-service** with its corresponding **frontend slice** that enables:
- SYSTEM_ADMIN to create tenants
- All roles to login with JWT-based authentication
- CLINIC_OWNER to invite staff (practitioners, receptionists)
- Role-based access control on all endpoints
- No cross-tenant data leakage

**Validation:** You can create a tenant, login as different roles, and each role sees only their permitted data.

---

## 2. WHAT YOU WILL BUILD

### 2.1 Backend (Java 21 + Spring Boot 3.3)

```
auth-service/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/pracisos/auth/
│   │   │   ├── AuthServiceApplication.java
│   │   │   ├── config/
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   ├── JwtConfig.java
│   │   │   │   └── KafkaConfig.java
│   │   │   ├── controller/
│   │   │   │   ├── AuthController.java
│   │   │   │   ├── TenantController.java
│   │   │   │   └── UserController.java
│   │   │   ├── domain/
│   │   │   │   ├── entity/
│   │   │   │   │   ├── Tenant.java
│   │   │   │   │   ├── User.java
│   │   │   │   │   └── AuditLog.java
│   │   │   │   ├── repository/
│   │   │   │   │   ├── TenantRepository.java
│   │   │   │   │   ├── UserRepository.java
│   │   │   │   │   └── AuditLogRepository.java
│   │   │   │   └── enums/
│   │   │   │       ├── Role.java
│   │   │   │       ├── UserStatus.java
│   │   │   │       └── TenantStatus.java
│   │   │   ├── dto/
│   │   │   │   ├── request/
│   │   │   │   │   ├── LoginRequest.java
│   │   │   │   │   ├── TenantCreateRequest.java
│   │   │   │   │   └── UserInviteRequest.java
│   │   │   │   └── response/
│   │   │   │       ├── LoginResponse.java
│   │   │   │       ├── TenantResponse.java
│   │   │   │       └── UserResponse.java
│   │   │   ├── service/
│   │   │   │   ├── AuthService.java
│   │   │   │   ├── TenantService.java
│   │   │   │   ├── UserService.java
│   │   │   │   ├── JwtService.java
│   │   │   │   └── AuditService.java
│   │   │   ├── security/
│   │   │   │   ├── JwtAuthenticationFilter.java
│   │   │   │   ├── JwtAuthorizationFilter.java
│   │   │   │   └── CustomUserDetailsService.java
│   │   │   ├── event/
│   │   │   │   ├── TenantCreatedEvent.java
│   │   │   │   ├── UserCreatedEvent.java
│   │   │   │   └── EventPublisher.java
│   │   │   └── exception/
│   │   │       ├── GlobalExceptionHandler.java
│   │   │       ├── TenantNotFoundException.java
│   │   │       ├── DuplicateSlugException.java
│   │   │       └── UnauthorizedException.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       └── db/migration/
│   │           ├── V1__init.sql
│   │           └── V2__add_audit_logs.sql
│   └── test/
│       ├── java/com/pracisos/auth/
│       │   ├── unit/
│       │   │   ├── AuthServiceTest.java
│       │   │   ├── JwtServiceTest.java
│       │   │   └── TenantServiceTest.java
│       │   ├── integration/
│       │   │   ├── AuthControllerIT.java
│       │   │   ├── TenantControllerIT.java
│       │   │   └── CucumberTestRunner.java
│       │   └── bdd/
│       │       ├── features/
│       │       │   ├── auth.feature
│       │       │   └── tenant.feature
│       │       └── steps/
│       │           ├── AuthSteps.java
│       │           └── TenantSteps.java
│       └── resources/
│           └── application-test.yml
└── Dockerfile
```

### 2.2 Frontend (React 19 + Vite + TypeScript)

```
frontend/
├── src/
│   ├── app/
│   │   ├── store.ts
│   │   ├── router.tsx
│   │   └── providers.tsx
│   ├── features/
│   │   └── auth/
│   │       ├── components/
│   │       │   ├── LoginForm.tsx
│   │       │   ├── TenantCreateForm.tsx
│   │       │   ├── UserInviteForm.tsx
│   │       │   ├── UserList.tsx
│   │       │   └── RoleBadge.tsx
│   │       ├── api.ts
│   │       ├── slice.ts
│   │       └── types.ts
│   ├── shared/
│   │   ├── ui/
│   │   ├── api/
│   │   │   └── baseQuery.ts
│   │   ├── hooks/
│   │   │   └── useAuth.ts
│   │   └── utils/
│   │       └── validators.ts
│   └── tests/
│       ├── unit/
│       │   ├── LoginForm.test.tsx
│       │   └── authSlice.test.ts
│       └── integration/
│           └── authFlow.test.tsx
├── public/
├── index.html
├── vite.config.ts
├── tailwind.config.ts
├── tsconfig.json
└── package.json
```

### 2.3 Infrastructure

```
docker-compose.yml          # Postgres 16 + auth-service + frontend
Dockerfile.auth             # Java 21 multi-stage
Dockerfile.frontend         # Nginx serve static
```

---

## 3. DETAILED IMPLEMENTATION

### 3.1 Maven POM (auth-service/pom.xml)

See full pom.xml in Master Specification Appendix.

### 3.2 Application Configuration (application.yml)

```yaml
server:
  port: 8080

spring:
  application:
    name: auth-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:auth_db}
    username: ${DB_USER:auth_user}
    password: ${DB_PASSWORD:auth_pass}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
    show-sql: true
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

jwt:
  secret: ${JWT_SECRET:change-me-in-production-min-256-bits-long}
  access-token-expiry: ${JWT_ACCESS_EXPIRY:900}
  refresh-token-expiry: ${JWT_REFRESH_EXPIRY:604800}

logging:
  level:
    com.pracisos.auth: DEBUG
    org.springframework.security: DEBUG
```

### 3.3 Database Migration (V1__init.sql)

```sql
CREATE TABLE IF NOT EXISTS tenants (
    tenant_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tenants_slug ON tenants(slug);

CREATE TABLE IF NOT EXISTS users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    role VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tenant_admin CHECK (
        (role = 'SYSTEM_ADMIN' AND tenant_id IS NULL) OR
        (role != 'SYSTEM_ADMIN' AND tenant_id IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_users_tenant ON users(tenant_id);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);

CREATE TABLE IF NOT EXISTS audit_logs (
    audit_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    actor_id UUID NOT NULL,
    actor_role VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id UUID NOT NULL,
    before_state JSONB,
    after_state JSONB,
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_tenant ON audit_logs(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_actor ON audit_logs(actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_action ON audit_logs(action);
```

### 3.4 Entity: Tenant.java

```java
package com.pracisos.auth.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tenant_id", updatable = false, nullable = false)
    private UUID tenantId;

    @Column(name = "slug", nullable = false, unique = true, length = 255)
    private String slug;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TenantStatus status = TenantStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Tenant() {}

    public Tenant(String slug, String name) {
        this.slug = slug;
        this.name = name;
    }

    // Getters and Setters
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public TenantStatus getStatus() { return status; }
    public void setStatus(TenantStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
```

### 3.5 Entity: User.java

```java
package com.pracisos.auth.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = true)
    private Tenant tenant;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "role", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private UserStatus status = UserStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public User() {}

    public User(Tenant tenant, String email, String passwordHash, 
                String firstName, String lastName, Role role) {
        this.tenant = tenant;
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
    }

    // Getters and Setters
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    public UUID getTenantId() { 
        return tenant != null ? tenant.getTenantId() : null; 
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public String getFullName() { 
        return firstName + " " + lastName; 
    }
}
```

### 3.6 Enums

```java
package com.pracisos.auth.domain.enums;

public enum Role {
    SYSTEM_ADMIN,
    CLINIC_OWNER,
    PRACTITIONER,
    RECEPTIONIST,
    PATIENT
}

public enum UserStatus {
    ACTIVE,
    INACTIVE,
    INVITED
}

public enum TenantStatus {
    ACTIVE,
    SUSPENDED,
    DELETED
}
```

### 3.7 Repository: UserRepository.java

```java
package com.pracisos.auth.domain.repository;

import com.pracisos.auth.domain.entity.User;
import com.pracisos.auth.domain.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.tenant.tenantId = :tenantId")
    List<User> findAllByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT u FROM User u WHERE u.tenant.tenantId = :tenantId AND u.role = :role")
    List<User> findAllByTenantIdAndRole(@Param("tenantId") UUID tenantId, @Param("role") Role role);
}
```

### 3.8 Repository: TenantRepository.java

```java
package com.pracisos.auth.domain.repository;

import com.pracisos.auth.domain.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlug(String slug);
    boolean existsBySlug(String slug);
}
```

### 3.9 DTOs

```java
// LoginRequest.java
package com.pracisos.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank @Email String email,
    @NotBlank String password
) {}

// TenantCreateRequest.java
package com.pracisos.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TenantCreateRequest(
    @NotBlank @Size(min = 3, max = 255) 
    @Pattern(regexp = "^[a-z0-9-]+$") String slug,
    @NotBlank @Size(max = 255) String name
) {}

// UserInviteRequest.java
package com.pracisos.auth.dto.request;

import com.pracisos.auth.domain.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserInviteRequest(
    @NotBlank @Email String email,
    @NotBlank String firstName,
    @NotBlank String lastName,
    @NotNull Role role
) {}

// LoginResponse.java
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

// TenantResponse.java
package com.pracisos.auth.dto.response;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
    UUID tenantId,
    String slug,
    String name,
    String status,
    Instant createdAt
) {}

// UserResponse.java
package com.pracisos.auth.dto.response;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID userId,
    String email,
    String firstName,
    String lastName,
    String fullName,
    String role,
    String status,
    UUID tenantId,
    Instant createdAt
) {}
```

### 3.10 JWT Service

```java
package com.pracisos.auth.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long accessTokenExpiry;
    private final long refreshTokenExpiry;

    public JwtService(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.access-token-expiry}") long accessTokenExpiry,
        @Value("${jwt.refresh-token-expiry}") long refreshTokenExpiry
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiry = accessTokenExpiry * 1000;
        this.refreshTokenExpiry = refreshTokenExpiry * 1000;
    }

    public String generateAccessToken(UUID userId, String email, String role, 
                                       UUID tenantId, String permissions) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(accessTokenExpiry);

        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("role", role)
            .claim("tenant_id", tenantId != null ? tenantId.toString() : null)
            .claim("permissions", permissions)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .id(UUID.randomUUID().toString())
            .signWith(secretKey)
            .compact();
    }

    public String generateRefreshToken(UUID userId) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(refreshTokenExpiry);

        return Jwts.builder()
            .subject(userId.toString())
            .claim("type", "refresh")
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .id(UUID.randomUUID().toString())
            .signWith(secretKey)
            .compact();
    }

    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (ExpiredJwtException e) {
            throw new RuntimeException("Token expired");
        } catch (JwtException e) {
            throw new RuntimeException("Invalid token");
        }
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(validateToken(token).getSubject());
    }

    public String extractRole(String token) {
        return validateToken(token).get("role", String.class);
    }

    public UUID extractTenantId(String token) {
        String tenantId = validateToken(token).get("tenant_id", String.class);
        return tenantId != null ? UUID.fromString(tenantId) : null;
    }
}
```

### 3.11 Auth Service

```java
package com.pracisos.auth.service;

import com.pracisos.auth.domain.entity.User;
import com.pracisos.auth.domain.enums.Role;
import com.pracisos.auth.domain.enums.UserStatus;
import com.pracisos.auth.domain.repository.UserRepository;
import com.pracisos.auth.dto.request.LoginRequest;
import com.pracisos.auth.dto.response.LoginResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, JwtService jwtService, 
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new RuntimeException("Account is not active");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }

        String permissions = String.join("",", getPermissionsForRole(user.getRole()));

        String accessToken = jwtService.generateAccessToken(
            user.getUserId(), user.getEmail(), user.getRole().name(),
            user.getTenantId(), permissions
        );
        String refreshToken = jwtService.generateRefreshToken(user.getUserId());

        return new LoginResponse(
            accessToken, refreshToken, "Bearer", 900,
            user.getUserId(), user.getEmail(), user.getFullName(),
            user.getRole(), user.getTenantId(),
            user.getTenant() != null ? user.getTenant().getSlug() : null
        );
    }

    private String[] getPermissionsForRole(Role role) {
        return switch (role) {
            case SYSTEM_ADMIN -> new String[]{"ALL"};
            case CLINIC_OWNER -> new String[]{"READ_USERS", "WRITE_USERS", "READ_BOOKINGS", "READ_CHARTS", "READ_BILLING", "READ_AUDIT"};
            case PRACTITIONER -> new String[]{"READ_AVAILABILITY", "WRITE_AVAILABILITY", "READ_CHARTS", "WRITE_CHARTS", "READ_BOOKINGS"};
            case RECEPTIONIST -> new String[]{"READ_BOOKINGS", "WRITE_BOOKINGS", "READ_PATIENTS", "READ_AVAILABILITY"};
            case PATIENT -> new String[]{"READ_OWN_BOOKINGS", "WRITE_OWN_BOOKINGS", "READ_OWN_INVOICES", "WRITE_PAYMENTS"};
        };
    }
}
```

### 3.12 Tenant Service

```java
package com.pracisos.auth.service;

import com.pracisos.auth.domain.entity.Tenant;
import com.pracisos.auth.domain.repository.TenantRepository;
import com.pracisos.auth.dto.request.TenantCreateRequest;
import com.pracisos.auth.dto.response.TenantResponse;
import com.pracisos.auth.event.EventPublisher;
import com.pracisos.auth.event.TenantCreatedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TenantService {

    private final TenantRepository tenantRepository;
    private final EventPublisher eventPublisher;

    public TenantService(TenantRepository tenantRepository, EventPublisher eventPublisher) {
        this.tenantRepository = tenantRepository;
        this.eventPublisher = eventPublisher;
    }

    public TenantResponse createTenant(TenantCreateRequest request) {
        if (tenantRepository.existsBySlug(request.slug())) {
            throw new RuntimeException("Tenant slug already exists: " + request.slug());
        }

        Tenant tenant = new Tenant(request.slug(), request.name());
        tenant = tenantRepository.save(tenant);

        eventPublisher.publishTenantCreated(new TenantCreatedEvent(
            tenant.getTenantId(), tenant.getSlug(), tenant.getName(), tenant.getCreatedAt()
        ));

        return new TenantResponse(
            tenant.getTenantId(), tenant.getSlug(), tenant.getName(),
            tenant.getStatus().name(), tenant.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenantBySlug(String slug) {
        Tenant tenant = tenantRepository.findBySlug(slug)
            .orElseThrow(() -> new RuntimeException("Tenant not found: " + slug));
        return new TenantResponse(
            tenant.getTenantId(), tenant.getSlug(), tenant.getName(),
            tenant.getStatus().name(), tenant.getCreatedAt()
        );
    }
}
```

### 3.13 User Service

```java
package com.pracisos.auth.service;

import com.pracisos.auth.domain.entity.Tenant;
import com.pracisos.auth.domain.entity.User;
import com.pracisos.auth.domain.enums.UserStatus;
import com.pracisos.auth.domain.repository.TenantRepository;
import com.pracisos.auth.domain.repository.UserRepository;
import com.pracisos.auth.dto.request.UserInviteRequest;
import com.pracisos.auth.dto.response.UserResponse;
import com.pracisos.auth.event.EventPublisher;
import com.pracisos.auth.event.UserCreatedEvent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final EventPublisher eventPublisher;

    public UserService(UserRepository userRepository, TenantRepository tenantRepository,
                       PasswordEncoder passwordEncoder, EventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    public UserResponse inviteUser(UUID tenantId, UserInviteRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new RuntimeException("Tenant not found"));

        if (userRepository.existsByEmail(request.email())) {
            throw new RuntimeException("Email already registered");
        }

        String tempPassword = UUID.randomUUID().toString().substring(0, 12);
        String passwordHash = passwordEncoder.encode(tempPassword);

        User user = new User(tenant, request.email(), passwordHash,
            request.firstName(), request.lastName(), request.role());
        user.setStatus(UserStatus.INVITED);
        user = userRepository.save(user);

        eventPublisher.publishUserCreated(new UserCreatedEvent(
            user.getUserId(), tenantId, user.getEmail(),
            user.getFirstName(), user.getLastName(),
            user.getRole().name(), user.getCreatedAt()
        ));

        return mapToResponse(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByTenant(UUID tenantId) {
        return userRepository.findAllByTenantId(tenantId)
            .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    private UserResponse mapToResponse(User user) {
        return new UserResponse(
            user.getUserId(), user.getEmail(), user.getFirstName(),
            user.getLastName(), user.getFullName(), user.getRole().name(),
            user.getStatus().name(), user.getTenantId(), user.getCreatedAt()
        );
    }
}
```

### 3.14 Security Config

```java
package com.pracisos.auth.config;

import com.pracisos.auth.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          UserDetailsService userDetailsService) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                .requestMatchers("/api/v1/auth/register").hasRole("SYSTEM_ADMIN")
                .requestMatchers("/api/v1/admin/**").hasRole("SYSTEM_ADMIN")
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return new ProviderManager(provider);
    }
}
```

### 3.15 JWT Authentication Filter

```java
package com.pracisos.auth.security;

import com.pracisos.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = jwtService.validateToken(token);
            String userId = claims.getSubject();
            String role = claims.get("role", String.class);
            String permissions = claims.get("permissions", String.class);

            List<SimpleGrantedAuthority> authorities = Arrays.stream(permissions.split(","))
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));

            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Invalid or expired token");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
```

### 3.16 Controllers

```java
// TenantController.java
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

// AuthController.java
package com.pracisos.auth.controller;

import com.pracisos.auth.dto.request.LoginRequest;
import com.pracisos.auth.dto.response.LoginResponse;
import com.pracisos.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}

// UserController.java
package com.pracisos.auth.controller;

import com.pracisos.auth.dto.request.UserInviteRequest;
import com.pracisos.auth.dto.response.UserResponse;
import com.pracisos.auth.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('CLINIC_OWNER')")
    public ResponseEntity<UserResponse> inviteUser(
        @RequestAttribute("tenantId") UUID tenantId,
        @Valid @RequestBody UserInviteRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(userService.inviteUser(tenantId, request));
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('CLINIC_OWNER')")
    public ResponseEntity<List<UserResponse>> getUsers(
        @RequestAttribute("tenantId") UUID tenantId
    ) {
        return ResponseEntity.ok(userService.getUsersByTenant(tenantId));
    }
}
```

### 3.17 Event Publisher and Events

```java
// EventPublisher.java
package com.pracisos.auth.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public EventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishTenantCreated(TenantCreatedEvent event) {
        kafkaTemplate.send("auth.tenant.created", event.tenantId().toString(), event);
    }

    public void publishUserCreated(UserCreatedEvent event) {
        kafkaTemplate.send("auth.user.created", event.tenantId().toString(), event);
    }
}

// TenantCreatedEvent.java
package com.pracisos.auth.event;

import java.time.Instant;
import java.util.UUID;

public record TenantCreatedEvent(
    UUID tenantId,
    String slug,
    String name,
    Instant createdAt
) {}

// UserCreatedEvent.java
package com.pracisos.auth.event;

import java.time.Instant;
import java.util.UUID;

public record UserCreatedEvent(
    UUID userId,
    UUID tenantId,
    String email,
    String firstName,
    String lastName,
    String role,
    Instant createdAt
) {}
```

---

## 4. FRONTEND IMPLEMENTATION

### 4.1 Package.json

```json
{
  "name": "pracisos-frontend",
  "private": true,
  "version": "1.0.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "preview": "vite preview",
    "test": "vitest",
    "test:e2e": "playwright test"
  },
  "dependencies": {
    "@reduxjs/toolkit": "^2.2.5",
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
    "react-redux": "^9.1.2",
    "react-router-dom": "^6.23.1",
    "react-hook-form": "^7.52.0",
    "zod": "^3.23.8",
    "@hookform/resolvers": "^3.6.0",
    "class-variance-authority": "^0.7.0",
    "clsx": "^2.1.1",
    "tailwind-merge": "^2.3.0",
    "lucide-react": "^0.395.0"
  },
  "devDependencies": {
    "@types/react": "^19.0.0",
    "@types/react-dom": "^19.0.0",
    "@vitejs/plugin-react": "^4.3.1",
    "typescript": "^5.4.5",
    "vite": "^6.0.0",
    "tailwindcss": "^3.4.4",
    "postcss": "^8.4.38",
    "autoprefixer": "^10.4.19",
    "vitest": "^2.0.0",
    "@testing-library/react": "^16.0.0",
    "@testing-library/jest-dom": "^6.4.6",
    "jsdom": "^24.1.0",
    "msw": "^2.3.1"
  }
}
```

### 4.2 Redux Store

```typescript
// store.ts
import { configureStore } from '@reduxjs/toolkit';
import { authApi } from '../features/auth/api';
import authReducer from '../features/auth/slice';

export const store = configureStore({
  reducer: {
    auth: authReducer,
    [authApi.reducerPath]: authApi.reducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware().concat(authApi.middleware),
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
```

### 4.3 Auth Slice

```typescript
// slice.ts
import { createSlice, PayloadAction } from '@reduxjs/toolkit';

interface AuthState {
  user: {
    userId: string;
    email: string;
    fullName: string;
    role: string;
    tenantId: string | null;
    tenantSlug: string | null;
  } | null;
  accessToken: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
}

const initialState: AuthState = {
  user: null,
  accessToken: null,
  refreshToken: null,
  isAuthenticated: false,
  isLoading: false,
};

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    setCredentials: (
      state,
      action: PayloadAction<{
        user: AuthState['user'];
        accessToken: string;
        refreshToken: string;
      }>
    ) => {
      state.user = action.payload.user;
      state.accessToken = action.payload.accessToken;
      state.refreshToken = action.payload.refreshToken;
      state.isAuthenticated = true;
    },
    logout: (state) => {
      state.user = null;
      state.accessToken = null;
      state.refreshToken = null;
      state.isAuthenticated = false;
    },
  },
});

export const { setCredentials, logout } = authSlice.actions;
export default authSlice.reducer;
```

### 4.4 RTK Query Base

```typescript
// baseQuery.ts
import { fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import type { RootState } from '../../app/store';

export const baseQuery = fetchBaseQuery({
  baseUrl: import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1',
  prepareHeaders: (headers, { getState }) => {
    const token = (getState() as RootState).auth.accessToken;
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }
    return headers;
  },
});
```

### 4.5 Auth API

```typescript
// api.ts
import { createApi } from '@reduxjs/toolkit/query/react';
import { baseQuery } from '../../shared/api/baseQuery';
import { setCredentials, logout } from './slice';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  userId: string;
  email: string;
  fullName: string;
  role: string;
  tenantId: string | null;
  tenantSlug: string | null;
}

export const authApi = createApi({
  reducerPath: 'authApi',
  baseQuery: baseQuery,
  endpoints: (builder) => ({
    login: builder.mutation<LoginResponse, LoginRequest>({
      query: (credentials) => ({
        url: '/auth/login',
        method: 'POST',
        body: credentials,
      }),
      async onQueryStarted(_, { dispatch, queryFulfilled }) {
        try {
          const { data } = await queryFulfilled;
          dispatch(
            setCredentials({
              user: {
                userId: data.userId,
                email: data.email,
                fullName: data.fullName,
                role: data.role,
                tenantId: data.tenantId,
                tenantSlug: data.tenantSlug,
              },
              accessToken: data.accessToken,
              refreshToken: data.refreshToken,
            })
          );
        } catch {
          dispatch(logout());
        }
      },
    }),
  }),
});

export const { useLoginMutation } = authApi;
```

### 4.6 Login Form

```tsx
// LoginForm.tsx
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useLoginMutation } from './api';
import { useNavigate } from 'react-router-dom';

const loginSchema = z.object({
  email: z.string().email('Invalid email address'),
  password: z.string().min(1, 'Password is required'),
});

type LoginFormData = z.infer<typeof loginSchema>;

export function LoginForm() {
  const navigate = useNavigate();
  const [login, { isLoading, error }] = useLoginMutation();

  const { register, handleSubmit, formState: { errors } } = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
  });

  const onSubmit = async (data: LoginFormData) => {
    try {
      const response = await login(data).unwrap();
      if (response.role === 'SYSTEM_ADMIN') {
        navigate('/admin/dashboard');
      } else if (response.tenantSlug) {
        navigate(`/${response.tenantSlug}/dashboard`);
      }
    } catch (err) {
      // Error handled by RTK Query
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
      <div>
        <label htmlFor="email" className="block text-sm font-medium">Email</label>
        <input {...register('email')} type="email" id="email"
          className="mt-1 block w-full rounded-md border px-3 py-2" />
        {errors.email && <p className="text-red-500 text-sm">{errors.email.message}</p>}
      </div>

      <div>
        <label htmlFor="password" className="block text-sm font-medium">Password</label>
        <input {...register('password')} type="password" id="password"
          className="mt-1 block w-full rounded-md border px-3 py-2" />
        {errors.password && <p className="text-red-500 text-sm">{errors.password.message}</p>}
      </div>

      {error && <p className="text-red-500 text-sm">{(error as any).data?.message || 'Login failed'}</p>}

      <button type="submit" disabled={isLoading}
        className="w-full rounded-md bg-blue-600 px-4 py-2 text-white hover:bg-blue-700 disabled:opacity-50">
        {isLoading ? 'Signing in...' : 'Sign In'}
      </button>
    </form>
  );
}
```

### 4.7 Router

```tsx
// router.tsx
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { useSelector } from 'react-redux';
import { RootState } from './store';
import { LoginForm } from '../features/auth/components/LoginForm';

function ProtectedRoute({ children, allowedRoles }: { 
  children: React.ReactNode; 
  allowedRoles?: string[];
}) {
  const { isAuthenticated, user } = useSelector((state: RootState) => state.auth);

  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (allowedRoles && user && !allowedRoles.includes(user.role)) {
    return <Navigate to="/unauthorized" replace />;
  }
  return <>{children}</>;
}

export const router = createBrowserRouter([
  { path: '/login', element: <LoginForm /> },
  { path: '/admin', element: (
    <ProtectedRoute allowedRoles={['SYSTEM_ADMIN']}>
      <div>Admin Dashboard</div>
    </ProtectedRoute>
  )},
  { path: '/:tenantSlug', element: (
    <ProtectedRoute allowedRoles={['CLINIC_OWNER', 'PRACTITIONER', 'RECEPTIONIST', 'PATIENT']}>
      <div>Clinic Dashboard</div>
    </ProtectedRoute>
  )},
  { path: '/', element: <Navigate to="/login" replace /> },
]);
```

---

## 5. INFRASTRUCTURE

### 5.1 Docker Compose

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    container_name: pracisos-auth-db
    environment:
      POSTGRES_DB: auth_db
      POSTGRES_USER: auth_user
      POSTGRES_PASSWORD: auth_pass
    ports:
      - "5432:5432"
    volumes:
      - auth_postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U auth_user -d auth_db"]
      interval: 5s
      timeout: 5s
      retries: 5

  auth-service:
    build:
      context: ./auth-service
      dockerfile: Dockerfile
    container_name: pracisos-auth-service
    environment:
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: auth_db
      DB_USER: auth_user
      DB_PASSWORD: auth_pass
      JWT_SECRET: your-256-bit-secret-key-here-change-in-production
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    container_name: pracisos-frontend
    ports:
      - "3000:80"
    depends_on:
      - auth-service

volumes:
  auth_postgres_data:
```

### 5.2 Dockerfiles

```dockerfile
# auth-service/Dockerfile
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/auth-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```dockerfile
# frontend/Dockerfile
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

```nginx
# nginx.conf
server {
    listen 80;
    server_name localhost;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api {
        proxy_pass http://auth-service:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## 6. TESTING (continued)
### 6.1 Unit Test: AuthServiceTest.java
java
package com.pracisos.auth.unit;

import com.pracisos.auth.domain.entity.Tenant;
import com.pracisos.auth.domain.entity.User;
import com.pracisos.auth.domain.enums.Role;
import com.pracisos.auth.domain.enums.UserStatus;
import com.pracisos.auth.domain.repository.UserRepository;
import com.pracisos.auth.dto.request.LoginRequest;
import com.pracisos.auth.dto.response.LoginResponse;
import com.pracisos.auth.service.AuthService;
import com.pracisos.auth.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private JwtService jwtService;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private AuthService authService;
    
    private User testUser;
    
    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant("maple-health", "Maple Health Clinic");
        tenant.setTenantId(UUID.randomUUID());
        
        testUser = new User(tenant, "admin@maple-health.com", "hashedPassword",
            "Alice", "Smith", Role.CLINIC_OWNER);
        testUser.setUserId(UUID.randomUUID());
        testUser.setStatus(UserStatus.ACTIVE);
    }
    
    @Test
    void login_WithValidCredentials_ReturnsTokens() {
        LoginRequest request = new LoginRequest("admin@maple-health.com", "password123");
        
        when(userRepository.findByEmail("admin@maple-health.com"))
            .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", "hashedPassword"))
            .thenReturn(true);
        when(jwtService.generateAccessToken(any(), any(), any(), any(), any()))
            .thenReturn("access-token-123");
        when(jwtService.generateRefreshToken(any()))
            .thenReturn("refresh-token-456");
        
        LoginResponse response = authService.login(request);
        
        assertNotNull(response);
        assertEquals("access-token-123", response.accessToken());
        assertEquals("Alice Smith", response.fullName());
    }
    
    @Test
    void login_WithInvalidPassword_ThrowsException() {
        LoginRequest request = new LoginRequest("admin@maple-health.com", "wrongpassword");
        
        when(userRepository.findByEmail("admin@maple-health.com"))
            .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongpassword", "hashedPassword"))
            .thenReturn(false);
        
        assertThrows(RuntimeException.class, () -> authService.login(request));
    }
    
    @Test
    void login_WithInactiveUser_ThrowsException() {
        testUser.setStatus(UserStatus.INACTIVE);
        LoginRequest request = new LoginRequest("admin@maple-health.com", "password123");
        
        when(userRepository.findByEmail("admin@maple-health.com"))
            .thenReturn(Optional.of(testUser));
        
        assertThrows(RuntimeException.class, () -> authService.login(request));
    }
}
### 6.2 BDD Feature: auth.feature
gherkin
Feature: Authentication and Authorization

  Background:
    Given the auth service is running
    And a tenant "maple-health" exists

  Scenario: SYSTEM_ADMIN creates a new tenant
    Given I am logged in as SYSTEM_ADMIN
    When I submit a tenant creation request with slug "downtown-dental" and name "Downtown Dental"
    Then the response status should be 201
    And the tenant slug should be "downtown-dental"

  Scenario: CLINIC_OWNER invites a practitioner
    Given I am logged in as CLINIC_OWNER for tenant "maple-health"
    When I invite a user with email "dr.bob@maple-health.com" and role "PRACTITIONER"
    Then the response status should be 201
    And the user role should be "PRACTITIONER"

  Scenario: Patient logs in successfully
    Given a patient "john@example.com" exists for tenant "maple-health"
    When I login with email "john@example.com" and password "password123"
    Then the response status should be 200
    And I should receive an access token

  Scenario: Patient cannot access admin endpoints
    Given I am logged in as PATIENT for tenant "maple-health"
    When I attempt to create a tenant with slug "hacker-clinic"
    Then the response status should be 403

  Scenario: Cross-tenant access is blocked
    Given I am logged in as CLINIC_OWNER for tenant "maple-health"
    When I attempt to list users for tenant "downtown-dental"
    Then the response status should be 403