# Pracisos Platform - Phase 1 MVP Architecture

This document outlines the architecture, components, database schema, and authentication flows of the **Pracisos Practice Management Platform** built so far.

---

## 1. System Topology
The platform uses an isolated multi-tenant architecture. All services are containerized and communicate over an internal Docker network, with Nginx acting as a reverse proxy for the frontend assets and backend API.

```mermaid
graph TD
    subgraph Client Browser
        Client([User Browser])
    end

    subgraph Docker Compose Environment
        Nginx["Nginx Proxy (Port 3000)"]
        Frontend["React 19 SPA (Port 80)"]
        Backend["Spring Boot Auth-Service (Port 8080)"]
        Postgres[("PostgreSQL DB (Port 5432)")]
        Kafka["Kafka Event Broker (Port 9092)"]
    end

    Client -->|Accesses http://localhost:3000| Nginx
    Nginx -->|Routes / | Frontend
    Nginx -->|Routes /api/v1/*| Backend
    Backend -->|Manages Schemas & Queries| Postgres
    Backend -->|Publishes Events| Kafka
```

---

## 2. Component Design

### Backend (Spring Boot 3.3 + Java 21)
* **Security & Auth:** Configured in `SecurityConfig` and `JwtAuthenticationFilter`. Uses JWT stateless sessions, whitelisting `/login` and `/refresh`, and protecting tenant-registration (`/register`) to `SYSTEM_ADMIN` and staff invitations (`/users`) to `CLINIC_OWNER`.
* **Tenant Service:** Validates, saves new tenant clinic spaces, and publishes `TenantCreatedEvent`.
* **User Service:** Generates temporary passwords, hashes passwords via BCrypt, registers user roles (`SYSTEM_ADMIN`, `CLINIC_OWNER`, `PRACTITIONER`, `RECEPTIONIST`, `PATIENT`), and publishes `UserCreatedEvent`.

### Frontend (React 19 + TypeScript + Redux Toolkit)
* **API Service:** Leverages RTK Query `authApi` to communicate with backend endpoints `/auth/login`, `/auth/register`, `/auth/users`, and `/auth/tenants/{slug}`.
* **Routing:** Managed via `React Router v6` in `router.tsx` to redirect users based on roles (e.g., `SYSTEM_ADMIN` -> `/admin/dashboard`, clinic staff -> `/:tenantSlug/dashboard`).
* **Design Aesthetic:** Tailored vanilla CSS rules matching a clean, light, teal-accented clinic dashboard layout.

---

## 3. Database Entity Relationship (ER) Schema
The relational database runs on Postgres 16 and utilizes Flyway migrations (`V1__init.sql` and `V2__seed_admin.sql`) to establish constraints, default values, and indexes.

```mermaid
erDiagram
    tenants {
        UUID tenant_id PK "DEFAULT gen_random_uuid()"
        VARCHAR slug UK "NOT NULL, Unique slug identifier"
        VARCHAR name "NOT NULL"
        VARCHAR status "ACTIVE, INACTIVE"
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }
    users {
        UUID user_id PK "DEFAULT gen_random_uuid()"
        UUID tenant_id FK "REFERENCES tenants(tenant_id)"
        VARCHAR email UK "NOT NULL, Unique login email"
        VARCHAR password_hash "NOT NULL"
        VARCHAR first_name "NOT NULL"
        VARCHAR last_name "NOT NULL"
        VARCHAR role "SYSTEM_ADMIN, CLINIC_OWNER, PRACTITIONER, RECEPTIONIST, PATIENT"
        VARCHAR status "ACTIVE, INVITED, SUSPENDED"
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }
    audit_logs {
        UUID audit_id PK "DEFAULT gen_random_uuid()"
        UUID tenant_id "NOT NULL"
        UUID actor_id "NOT NULL"
        VARCHAR actor_role "NOT NULL"
        VARCHAR action "NOT NULL"
        VARCHAR target_type "NOT NULL"
        UUID target_id "NOT NULL"
        JSONB before_state
        JSONB after_state
        INET ip_address
        TEXT user_agent
        TIMESTAMPTZ created_at
    }
    tenants ||--o{ users : "owns (nullable for SYSTEM_ADMIN)"
```

> [!IMPORTANT]
> **Check Constraint (`chk_tenant_admin`):**
> Enforces data consistency by verifying that:
> * `SYSTEM_ADMIN` accounts have no tenant mapping (`tenant_id IS NULL`).
> * Clinic-specific accounts (`CLINIC_OWNER`, `PRACTITIONER`, `RECEPTIONIST`, `PATIENT`) must belong to a tenant (`tenant_id IS NOT NULL`).

---

## 4. Authentication Sequence Flows

### User Login Flow
This sequence shows the path for a standard user logging in.

```mermaid
sequenceDiagram
    autonumber
    actor Client as Client Browser
    participant Nginx as Nginx Proxy
    participant Filter as JwtAuthenticationFilter
    participant Controller as AuthController
    participant Service as AuthService
    participant DB as Postgres DB

    Client->>Nginx: POST /api/v1/auth/login
    Nginx->>Filter: Forward Request
    Filter->>Filter: Bypass JWT Check (Permit All)
    Filter->>Controller: Forward to Controller
    Controller->>Service: login(credentials)
    Service->>DB: findByEmail(email)
    DB-->>Service: Return User & BCrypt Hash
    Service->>Service: Verify status is ACTIVE & password matches
    Service->>Client: Return 200 OK + JWT (Access & Refresh Tokens)
```

### Protected Request Flow (e.g. Invite Staff)
This sequence shows the authentication check for protected resources (like a Clinic Owner inviting a Practitioner).

```mermaid
sequenceDiagram
    autonumber
    actor Client as Clinic Owner Browser
    participant Nginx as Nginx Proxy
    participant Filter as JwtAuthenticationFilter
    participant Controller as UserController
    participant Service as UserService
    participant DB as Postgres DB

    Client->>Nginx: POST /api/v1/auth/users [Bearer Token]
    Nginx->>Filter: Forward Request [Bearer Token]
    Filter->>Filter: Validate Token Signature & Claims
    Filter->>Filter: Populate SecurityContext with Role & Tenant ID
    Filter->>Controller: Check PreAuthorize(hasRole('CLINIC_OWNER'))
    Controller->>Service: inviteUser(tenantId, inviteRequest)
    Service->>DB: Save User (Status: INVITED)
    Service->>Client: Return 201 Created + User Metadata
```
