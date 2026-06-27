# PRACISOS PLATFORM
## MASTER TECHNICAL SPECIFICATION
### Single Source of Truth — Final Lockdown

---

## DOCUMENT CONTROL

| Field | Value |
|-------|-------|
| **Version** | 1.0.0 |
| **Status** | FINAL — Pre-Implementation Lockdown |
| **Date** | 2026-06-24 |
| **Classification** | Learning-First Enterprise Platform |
| **Delivery Mode** | Incremental Microservice Development |
| **Change Control** | Explicit approval required for any deviation |

---

## 1. EXECUTIVE SUMMARY

Pracisos is a multi-tenant healthcare practice management platform (Jane App clone) designed as a learning-first enterprise SaaS system. It demonstrates modern production patterns: strict tenant isolation, zero-trust networking, event-driven microservices, full observability, and enterprise-grade testing discipline.

**Development Approach:** Incremental microservice development — build one service + its frontend slice in isolation, validate, then proceed to the next. Design changes are permitted during development, but must be documented and approved.

---

## 2. PLATFORM PARADIGMS (IMMUTABLE RULES)

| # | Rule | Violation Consequence |
|---|------|----------------------|
| 1 | **Database Per Microservice** — No shared databases. Each service owns its isolated PostgreSQL schema. | Architectural failure |
| 2 | **No Cross-Service DB Reads** — Services communicate via async events or synchronous HTTP REST only. | Security/integrity failure |
| 3 | **Async Event Dominance** — Cross-domain state sync happens exclusively via Kafka. | Coupling failure |
| 4 | **Zero-Hardcoded Config** — All credentials injected via environment variables/secrets. | Security failure |
| 5 | **Zero-Trust Mesh** — All intra-cluster traffic encrypted via mTLS (STRICT). | Security failure |
| 6 | **Unified Local Execution** — Entire stack spins up deterministically via Docker Compose (dev) or Kubernetes (prod). | Operational failure |
| 7 | **Tenant Isolation** — Every table has `tenant_id UUID NOT NULL`. Every query filters by it. | Data breach |
| 8 | **Incremental Development** — One service + frontend slice at a time. No premature infrastructure. | Scope/quality failure |

---

## 3. ACTOR DEFINITIONS

| Actor | Role | Scope | Key Actions |
|-------|------|-------|-------------|
| **SYSTEM_ADMIN** | Platform administrator | Global (cross-tenant) | Create tenants, monitor telemetry, audit root actions, manual overrides |
| **CLINIC_OWNER** | Clinic business owner | Single tenant | Invite staff, delegate roles, view revenue, audit staff, manage invoices |
| **PRACTITIONER** | Healthcare provider | Single tenant | Set availability, access patient charts, write clinical notes, lock records, mark appointments complete/no-show |
| **PATIENT** | End user booking appointments | Single tenant | Book/cancel appointments, pay invoices, view own history |
| **RECEPTIONIST** | Front-desk staff | Single tenant | View/create bookings, view patients, view schedules — NO chart access |
| **SYSTEM** | Automated background processes | Global/tenant | Auto-lock notes, generate invoices, process events, run audits |

---

## 4. MICROSERVICE ARCHITECTURE

### 4.1 Service Topology

```
┌─────────────────────────────────────────┐
│  React 19 + Redux Toolkit + RTK Query   │
│  (Single Page Application via Vite)       │
└─────────────────┬───────────────────────┘
                  │ HTTPS (TLS 1.3)
                  ▼
┌─────────────────────────────────────────┐
│  Istio Ingress Gateway                  │
│  (Edge router, TLS termination, JWT    │
│   validation, rate limiting)            │
└─────────────────┬───────────────────────┘
                  │
    ┌─────────────┼─────────────┬─────────────┐
    │             │             │             │
    ▼             ▼             ▼             ▼
┌────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
│  Auth  │  │ Booking  │  │ Charting │  │ Billing  │
│Service │  │ Service  │  │ Service  │  │ Service  │
│(Java21)│  │(Java21)  │  │(Java21)  │  │(Java21)  │
│+ Envoy │  │ + Envoy  │  │ + Envoy  │  │ + Envoy  │
└───┬────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘
    │            │             │             │
    └────────────┴─────────────┴─────────────┘
                  │
                  ▼
        ┌─────────────────┐
        │  Kafka Cluster  │
        │  (mTLS between  │
        │   producers and │
        │   consumers)     │
        └─────────────────┘
                  │
    ┌─────────────┼─────────────┬─────────────┐
    ▼             ▼             ▼             ▼
┌────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
│  Auth  │  │ Booking  │  │ Charting │  │ Billing  │
│  DB    │  │  DB      │  │  DB      │  │  DB      │
│(Postgres)│ (Postgres)│  │(Postgres)│  │(Postgres)│
└────────┘  └──────────┘  └──────────┘  └──────────┘

Shared: Redis (sessions, rate limiting, availability cache)
```

### 4.2 Service Responsibilities

| Service | Domain | Database | Events Produced | Events Consumed |
|---------|--------|----------|-----------------|-----------------|
| **auth-service** | Tenants, Users, Roles, JWT issuance | `auth_db` | `TenantCreatedV1`, `UserCreatedV1`, `UserUpdatedV1`, `UserDeactivatedV1` | — |
| **booking-service** | Availability, Bookings, Practitioner cache | `booking_db` | `AvailabilityUpdatedV1`, `BookingConfirmedV1`, `BookingCancelledV1`, `AppointmentCompletedV1` | `UserCreatedV1`, `UserUpdatedV1`, `UserDeactivatedV1` |
| **charting-service** | Clinical notes, Amendments | `charting_db` | `ClinicalNoteLockedV1`, `ClinicalNoteAmendedV1` | `BookingConfirmedV1`, `BookingCancelledV1` |
| **billing-service** | Invoices, Payments, Revenue | `billing_db` | `InvoiceCreatedV1`, `PaymentConfirmedV1`, `PaymentFailedV1`, `PaymentRefundedV1` | `AppointmentCompletedV1`, `BookingCancelledV1` |

### 4.3 API Gateway Routing

| Path Prefix | Destination | Auth Required | Role Scope |
|-------------|-------------|---------------|------------|
| `/api/v1/auth/*` | auth-service | Varies | All |
| `/api/v1/booking/*` | booking-service | Yes | PATIENT, PRACTITIONER, RECEPTIONIST, CLINIC_OWNER |
| `/api/v1/charting/*` | charting-service | Yes | PRACTITIONER |
| `/api/v1/billing/*` | billing-service | Yes | PATIENT, CLINIC_OWNER |
| `/api/v1/billing/webhooks/stripe` | billing-service | No (Stripe signature) | Public |
| `/api/v1/admin/*` | auth-service | Yes | SYSTEM_ADMIN only |

---

## 5. TECHNOLOGY STACK

### 5.1 Frontend Tier

| Layer | Technology | Version | Purpose |
|-------|-----------|---------|---------|
| Runtime | React | 19 | UI framework |
| Build Tool | Vite | 6.x | Fast compilation, HMR |
| State Management | Redux Toolkit | 2.x | Global application state |
| Server State | RTK Query | (bundled with RTK) | Data fetching, caching, deduplication |
| Styling | Tailwind CSS | 3.x | Utility-first CSS |
| Components | shadcn/ui | latest | Headless UI primitives (Radix-based) |
| Forms | React Hook Form | 7.x | Form state management |
| Validation | Zod | 3.x | Schema validation (TypeScript) |
| Animation | Framer Motion | 11.x | UI transitions |
| Testing | Vitest | 2.x | Unit tests |
| Testing | React Testing Library | 16.x | Component tests |
| Testing | Mock Service Worker | 2.x | API mocking for integration tests |
| Testing | Playwright | 1.x | E2E browser tests |
| Language | TypeScript | 5.x | Type safety |

### 5.2 Backend Tier

| Layer | Technology | Version | Purpose |
|-------|-----------|---------|---------|
| Language | Java | 21 LTS | Runtime |
| Framework | Spring Boot | 3.3.x | Application framework |
| Security | Spring Security | 6.x | JWT, method-level security, RBAC |
| Data Access | Spring Data JPA | 3.x | ORM with Hibernate |
| Migrations | Flyway | 10.x | Database schema versioning |
| Events | Spring Kafka | 3.x | Kafka producer/consumer |
| Cache | Spring Data Redis | 3.x | Session storage, rate limiting |
| API Docs | SpringDoc OpenAPI | 2.x | OpenAPI 3.0 generation |
| Testing | JUnit 5 | 5.x | Unit tests |
| Testing | Mockito | 5.x | Mocking |
| Testing | Cucumber | 7.x | BDD tests |
| Testing | Testcontainers | 1.x | Ephemeral DBs/Kafka for tests |
| Testing | Spring Boot Test | 3.x | Integration test context |
| Resilience | Resilience4j | 2.x | Circuit breakers, retries |
| Observability | OpenTelemetry | 1.x | Tracing, metrics |
| Observability | Micrometer | 1.x | Metrics collection |
| Build | Apache Maven | 3.9.x | Build orchestration |

### 5.3 Infrastructure Tier

| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| Database | PostgreSQL | 16 | Primary persistent store |
| Cache | Redis | 7 | Sessions, rate limiting, availability |
| Message Broker | Apache Kafka | 3.7 | Event streaming |
| Schema Registry | Confluent Schema Registry | 7.x | Avro/JSON schema enforcement |
| Service Mesh | Istio | 1.22 | mTLS, traffic management, observability |
| Gateway | Istio Ingress Gateway | (bundled) | Edge routing, TLS termination |
| Orchestration | Kubernetes | 1.30 | Container orchestration |
| Local K8s | Kind | 0.23 | Local Kubernetes cluster |
| Packaging | Helm | 3.15 | K8s deployment templates |
| Containers | Docker | 24.x | Container runtime |
| Compose | Docker Compose | 2.x | Local development stack |
| Observability | Prometheus | 2.x | Metrics collection |
| Observability | Grafana | 10.x | Dashboards |
| Observability | Jaeger | 1.x | Distributed tracing |
| Payment | Stripe | (API) | Payment processing (sandbox) |

---

## 6. DATABASE DESIGN

### 6.1 Tenant Isolation Strategy

**Chosen:** Row-Level Security with `tenant_id` column + Application-Level Filtering

- Every table in every service has `tenant_id UUID NOT NULL` (except global tables)
- Every query MUST include `WHERE tenant_id = ?`
- No PostgreSQL RLS policies (application enforces for simplicity)
- UUID primary keys on all tables (`gen_random_uuid()`)

### 6.2 Auth-Service Schema

```sql
-- Global table (no tenant_id)
CREATE TABLE tenants (
    tenant_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tenants_slug ON tenants(slug);

-- Global table (users belong to tenants)
CREATE TABLE users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID REFERENCES tenants(tenant_id), -- NULL for SYSTEM_ADMIN
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    role VARCHAR(50) NOT NULL, -- SYSTEM_ADMIN, CLINIC_OWNER, PRACTITIONER, RECEPTIONIST, PATIENT
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tenant_admin CHECK (
        (role = 'SYSTEM_ADMIN' AND tenant_id IS NULL) OR
        (role != 'SYSTEM_ADMIN' AND tenant_id IS NOT NULL)
    )
);

CREATE INDEX idx_users_tenant ON users(tenant_id);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);

-- Per-tenant audit (operational)
CREATE TABLE audit_logs (
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

CREATE INDEX idx_audit_tenant ON audit_logs(tenant_id, created_at);
CREATE INDEX idx_audit_actor ON audit_logs(actor_id);
CREATE INDEX idx_audit_target ON audit_logs(target_type, target_id);
```

### 6.3 Booking-Service Schema

```sql
-- Denormalized practitioner cache (from auth events)
CREATE TABLE practitioners (
    practitioner_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, practitioner_id)
);

CREATE INDEX idx_practitioners_tenant ON practitioners(tenant_id);

-- Availability templates (recurring)
CREATE TABLE availability_templates (
    template_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    practitioner_id UUID NOT NULL REFERENCES practitioners(practitioner_id),
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, practitioner_id, day_of_week, start_time, end_time)
);

CREATE INDEX idx_availability_tenant ON availability_templates(tenant_id);
CREATE INDEX idx_availability_practitioner ON availability_templates(practitioner_id);

-- Concrete time slots
CREATE TABLE time_slots (
    slot_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    practitioner_id UUID NOT NULL REFERENCES practitioners(practitioner_id),
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE', -- AVAILABLE, LOCKED, BOOKED
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, practitioner_id, start_time)
);

CREATE INDEX idx_slots_tenant ON time_slots(tenant_id);
CREATE INDEX idx_slots_practitioner ON time_slots(practitioner_id);
CREATE INDEX idx_slots_time ON time_slots(start_time, end_time);

-- Bookings
CREATE TABLE bookings (
    booking_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    slot_id UUID NOT NULL UNIQUE REFERENCES time_slots(slot_id),
    patient_id UUID NOT NULL,
    practitioner_id UUID NOT NULL REFERENCES practitioners(practitioner_id),
    appointment_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED', -- CONFIRMED, CANCELLED, COMPLETED, NO_SHOW
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    cancelled_at TIMESTAMPTZ,
    cancellation_reason VARCHAR(255),
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_bookings_tenant ON bookings(tenant_id);
CREATE INDEX idx_bookings_patient ON bookings(patient_id);
CREATE INDEX idx_bookings_practitioner ON bookings(practitioner_id);
CREATE INDEX idx_bookings_status ON bookings(status);
```

### 6.4 Charting-Service Schema

```sql
CREATE TABLE clinical_notes (
    note_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    practitioner_id UUID NOT NULL,
    booking_id UUID NOT NULL,
    appointment_type VARCHAR(50) NOT NULL,
    content JSONB NOT NULL DEFAULT '{}',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT, IMMUTABLE
    locked_at TIMESTAMPTZ,
    locked_by VARCHAR(50), -- 'PRACTITIONER' or 'SYSTEM'
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_no_update_if_locked CHECK (
        status = 'DRAFT' OR (status = 'IMMUTABLE' AND locked_at IS NOT NULL)
    )
);

CREATE INDEX idx_notes_tenant ON clinical_notes(tenant_id);
CREATE INDEX idx_notes_patient ON clinical_notes(patient_id);
CREATE INDEX idx_notes_practitioner ON clinical_notes(practitioner_id);
CREATE INDEX idx_notes_booking ON clinical_notes(booking_id);
CREATE INDEX idx_notes_status ON clinical_notes(status);
CREATE INDEX idx_notes_content ON clinical_notes USING GIN(content);

CREATE TABLE amendments (
    amendment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    note_id UUID NOT NULL REFERENCES clinical_notes(note_id),
    practitioner_id UUID NOT NULL,
    amendment_text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_amendments_note ON amendments(note_id);
CREATE INDEX idx_amendments_tenant ON amendments(tenant_id);
```

### 6.5 Billing-Service Schema

```sql
CREATE TABLE invoices (
    invoice_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    booking_id UUID NOT NULL UNIQUE,
    patient_id UUID NOT NULL,
    practitioner_id UUID NOT NULL,
    amount_cents INTEGER NOT NULL CHECK (amount_cents > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'ISSUED', -- ISSUED, PAID, OVERDUE, REFUNDED, CANCELLED
    description TEXT,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    paid_at TIMESTAMPTZ,
    stripe_payment_intent_id VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invoices_tenant ON invoices(tenant_id);
CREATE INDEX idx_invoices_booking ON invoices(booking_id);
CREATE INDEX idx_invoices_patient ON invoices(patient_id);
CREATE INDEX idx_invoices_status ON invoices(status);

CREATE TABLE payments (
    payment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    invoice_id UUID NOT NULL REFERENCES invoices(invoice_id),
    stripe_payment_intent_id VARCHAR(255) NOT NULL,
    amount_cents INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL, -- PENDING, SUCCEEDED, FAILED, REFUNDED
    stripe_response JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_invoice ON payments(invoice_id);
CREATE INDEX idx_payments_stripe ON payments(stripe_payment_intent_id);
```

---

## 7. EVENT CONTRACT SPECIFICATION

### 7.1 Topic Naming

`{service-domain}.{entity}.{action}` — lowercase, dot-separated

### 7.2 Topic Inventory

| Topic | Producer | Consumers | Retention | Partition Strategy |
|-------|----------|-----------|-----------|-------------------|
| `auth.tenant.created` | auth-service | all services | 7 days | by tenant_id |
| `auth.user.created` | auth-service | booking-service | 7 days | by tenant_id |
| `auth.user.updated` | auth-service | booking-service | 7 days | by tenant_id |
| `auth.user.deactivated` | auth-service | booking-service | 7 days | by tenant_id |
| `booking.availability.updated` | booking-service | — | 1 day | by practitioner_id |
| `booking.confirmed` | booking-service | charting-service, billing-service | 7 days | by tenant_id |
| `booking.cancelled` | booking-service | charting-service, billing-service | 7 days | by tenant_id |
| `booking.completed` | booking-service | billing-service | 7 days | by tenant_id |
| `charting.note.locked` | charting-service | — | 7 days | by tenant_id |
| `charting.note.amended` | charting-service | — | 7 days | by tenant_id |
| `billing.invoice.created` | billing-service | — | 7 days | by tenant_id |
| `billing.payment.confirmed` | billing-service | frontend (dashboard) | 7 days | by tenant_id |
| `billing.payment.failed` | billing-service | frontend | 7 days | by tenant_id |
| `billing.payment.refunded` | billing-service | — | 7 days | by tenant_id |

### 7.3 Event Schema (All Events)

```json
{
  "event_id": "uuid",
  "event_type": "string", // e.g., "BookingConfirmedV1"
  "tenant_id": "uuid",
  "timestamp": "iso-8601",
  "trace_id": "string", // OpenTelemetry trace context
  "payload": { ... }
}
```

### 7.4 Event Payload Examples

**TenantCreatedV1:**
```json
{
  "event_id": "uuid-e1",
  "event_type": "TenantCreatedV1",
  "tenant_id": "uuid-1",
  "timestamp": "2026-06-24T14:00:00Z",
  "trace_id": "abc123",
  "payload": {
    "tenant_id": "uuid-1",
    "slug": "maple-health",
    "name": "Maple Health Clinic",
    "created_at": "2026-06-24T14:00:00Z"
  }
}
```

**BookingConfirmedV1:**
```json
{
  "event_id": "uuid-e4",
  "event_type": "BookingConfirmedV1",
  "tenant_id": "uuid-1",
  "timestamp": "2026-06-24T14:15:00Z",
  "trace_id": "abc123",
  "payload": {
    "booking_id": "uuid-b1",
    "patient_id": "uuid-p1",
    "practitioner_id": "uuid-2",
    "slot_id": "uuid-s1",
    "start_time": "2026-06-30T09:00:00Z",
    "end_time": "2026-06-30T09:30:00Z",
    "appointment_type": "CONSULTATION"
  }
}
```

**AppointmentCompletedV1:**
```json
{
  "event_id": "uuid-e6",
  "event_type": "AppointmentCompletedV1",
  "tenant_id": "uuid-1",
  "timestamp": "2026-06-24T15:00:00Z",
  "trace_id": "abc123",
  "payload": {
    "booking_id": "uuid-b1",
    "patient_id": "uuid-p1",
    "practitioner_id": "uuid-2",
    "completed_at": "2026-06-24T15:00:00Z",
    "appointment_type": "CONSULTATION",
    "status": "COMPLETED"
  }
}
```

---

## 8. API CONTRACT SPECIFICATION

### 8.1 Auth-Service Endpoints

| Method | Path | Auth | Roles | Description |
|--------|------|------|-------|-------------|
| POST | `/api/v1/auth/register` | SYSTEM_ADMIN JWT | SYSTEM_ADMIN | Create tenant |
| POST | `/api/v1/auth/login` | None | All | Authenticate, return JWT |
| POST | `/api/v1/auth/refresh` | Refresh token | All | Refresh access token |
| POST | `/api/v1/auth/users` | JWT | CLINIC_OWNER | Invite user |
| GET | `/api/v1/auth/users` | JWT | CLINIC_OWNER | List staff |
| GET | `/api/v1/auth/users/{id}` | JWT | CLINIC_OWNER | Get user details |
| GET | `/api/v1/auth/tenants` | SYSTEM_ADMIN JWT | SYSTEM_ADMIN | List all tenants |
| GET | `/api/v1/auth/audit` | JWT | CLINIC_OWNER | Per-tenant audit logs |

### 8.2 Booking-Service Endpoints

| Method | Path | Auth | Roles | Description |
|--------|------|------|-------|-------------|
| GET | `/api/v1/booking/practitioners` | JWT | All (tenant) | List practitioners |
| GET | `/api/v1/booking/practitioners/{id}/slots` | JWT | PATIENT | Available slots |
| POST | `/api/v1/booking/appointments` | JWT | PATIENT | Create booking |
| GET | `/api/v1/booking/appointments` | JWT | All (tenant) | List bookings |
| GET | `/api/v1/booking/appointments/{id}` | JWT | All (tenant) | Get booking |
| PUT | `/api/v1/booking/appointments/{id}/cancel` | JWT | PATIENT | Cancel booking |
| PUT | `/api/v1/booking/appointments/{id}/complete` | JWT | PRACTITIONER | Mark complete |
| PUT | `/api/v1/booking/appointments/{id}/no-show` | JWT | PRACTITIONER, RECEPTIONIST | Mark no-show |
| POST | `/api/v1/booking/availability` | JWT | PRACTITIONER | Create template |
| GET | `/api/v1/booking/availability` | JWT | PRACTITIONER | List templates |
| PUT | `/api/v1/booking/availability/{id}` | JWT | PRACTITIONER | Update template |
| DELETE | `/api/v1/booking/availability/{id}` | JWT | PRACTITIONER | Delete template |

### 8.3 Charting-Service Endpoints

| Method | Path | Auth | Roles | Description |
|--------|------|------|-------|-------------|
| GET | `/api/v1/charting/notes` | JWT | PRACTITIONER | List notes (filtered) |
| GET | `/api/v1/charting/notes/{id}` | JWT | PRACTITIONER | Get note |
| POST | `/api/v1/charting/notes/{id}/draft` | JWT | PRACTITIONER | Save draft |
| POST | `/api/v1/charting/notes/{id}/lock` | JWT | PRACTITIONER | Manual lock |
| POST | `/api/v1/charting/notes/{id}/amendments` | JWT | PRACTITIONER | Add amendment |

### 8.4 Billing-Service Endpoints

| Method | Path | Auth | Roles | Description |
|--------|------|------|-------|-------------|
| GET | `/api/v1/billing/invoices` | JWT | PATIENT, CLINIC_OWNER | List invoices |
| GET | `/api/v1/billing/invoices/{id}` | JWT | PATIENT, CLINIC_OWNER | Get invoice |
| POST | `/api/v1/billing/invoices/{id}/pay` | JWT | PATIENT | Initiate payment |
| POST | `/api/v1/billing/webhooks/stripe` | Stripe sig | Public | Stripe webhook |
| GET | `/api/v1/billing/revenue` | JWT | CLINIC_OWNER | Revenue dashboard |

---

## 9. SECURITY SPECIFICATION

### 9.1 JWT Claims Structure

```json
{
  "sub": "user-uuid",
  "email": "user@example.com",
  "role": "PRACTITIONER",
  "tenant_id": "tenant-uuid",
  "permissions": ["READ_CHARTS", "WRITE_CHARTS", "READ_AVAILABILITY"],
  "iat": 1719230400,
  "exp": 1719234000,
  "jti": "token-uuid"
}
```

### 9.2 Role-Permission Matrix

| Role | Permissions |
|------|-------------|
| SYSTEM_ADMIN | All permissions globally |
| CLINIC_OWNER | `READ_USERS`, `WRITE_USERS`, `READ_BOOKINGS`, `READ_CHARTS`, `READ_BILLING`, `READ_AUDIT` |
| PRACTITIONER | `READ_AVAILABILITY`, `WRITE_AVAILABILITY`, `READ_CHARTS`, `WRITE_CHARTS`, `READ_BOOKINGS`, `WRITE_BOOKINGS` |
| RECEPTIONIST | `READ_BOOKINGS`, `WRITE_BOOKINGS`, `READ_PATIENTS`, `READ_AVAILABILITY` |
| PATIENT | `READ_OWN_BOOKINGS`, `WRITE_OWN_BOOKINGS`, `READ_OWN_INVOICES`, `WRITE_PAYMENTS` |

### 9.3 Security Rules

| Rule | Implementation |
|------|---------------|
| Deny-by-default | All endpoints require explicit `@PreAuthorize` |
| Method-level security | `@PreAuthorize("hasRole('PRACTITIONER') and @securityService.isTenantMember(#tenantId)")` |
| Token storage | JWT in Redux memory (not localStorage) |
| Token refresh | Silent refresh via httpOnly cookie or refresh token rotation |
| Password hashing | BCrypt with strength 12 |
| mTLS | STRICT mode in Istio PeerAuthentication |
| Rate limiting | Redis-based, 100 req/min per IP, 1000 req/min per user |

---

## 10. FRONTEND ARCHITECTURE

### 10.1 Folder Structure

```
frontend/
├── app/
│   ├── store.ts              # Redux store configuration
│   ├── router.tsx            # React Router with tenant-aware routes
│   └── providers.tsx         # Context providers (auth, theme)
├── features/
│   ├── auth/
│   │   ├── components/       # LoginForm, RegisterForm
│   │   ├── api.ts            # RTK Query endpoints
│   │   ├── slice.ts          # Redux slice
│   │   └── types.ts          # TypeScript interfaces
│   ├── booking/
│   │   ├── components/       # Calendar, SlotPicker, BookingList
│   │   ├── api.ts
│   │   ├── slice.ts
│   │   └── types.ts
│   ├── charting/
│   │   ├── components/       # NoteEditor, AmendmentList
│   │   ├── api.ts
│   │   ├── slice.ts
│   │   └── types.ts
│   ├── billing/
│   │   ├── components/       # InvoiceList, PaymentForm, RevenueDashboard
│   │   ├── api.ts
│   │   ├── slice.ts
│   │   └── types.ts
│   └── admin/
│       ├── components/       # TenantList, UserList, AuditLog
│       ├── api.ts
│       ├── slice.ts
│       └── types.ts
├── shared/
│   ├── ui/                   # shadcn/ui components
│   ├── hooks/                # Custom React hooks
│   ├── api/                  # Base RTK Query setup
│   ├── utils/                # Helpers, formatters
│   └── types/                # Global TypeScript types
├── tests/
│   ├── unit/                 # Vitest tests
│   ├── integration/          # MSW tests
│   └── e2e/                  # Playwright tests
├── public/
├── index.html
├── vite.config.ts
├── tailwind.config.ts
├── tsconfig.json
└── package.json
```

### 10.2 Tenant-Aware Routing

- URL format: `/login` (public) → `/{tenant-slug}/dashboard` (authenticated)
- Tenant context resolved from URL path on app load
- Stored in Redux state, injected into all API calls
- Unauthorized routes hidden based on role

### 10.3 Optimistic UI Pattern

```typescript
// RTK Query with optimistic update
bookAppointment: builder.mutation({
  query: (body) => ({
    url: '/booking/appointments',
    method: 'POST',
    body,
  }),
  async onQueryStarted(body, { dispatch, queryFulfilled }) {
    // Optimistically update cache
    const patchResult = dispatch(
      bookingApi.util.updateQueryData('getSlots', body.practitioner_id, (draft) => {
        const slot = draft.find(s => s.slot_id === body.slot_id);
        if (slot) slot.status = 'BOOKED';
      })
    );
    try {
      await queryFulfilled;
    } catch {
      // Rollback on failure
      patchResult.undo();
    }
  }
})
```

---

## 11. TESTING STRATEGY

### 11.1 Testing Pyramid

```
▲ E2E Tests (Playwright)
│   Full browser journeys in Istio mesh
│   Multi-step: Login → Book → Pay → Verify
╱╲
╱│╲ BDD/Integration Tests (Cucumber + Testcontainers)
╱││╲   Given/When/Then scenarios
╱│││╲   Real PostgreSQL, Kafka, Redis in Docker
╱││││╲
──────── Unit Tests (JUnit 5 + Mockito / Vitest + RTL)
       Pure business logic, no containers
```

### 11.2 Test Requirements

| Layer | Framework | Coverage | Target |
|-------|-----------|----------|--------|
| Backend Unit | JUnit 5 + Mockito | 80%+ | Domain services, validation, utilities |
| Backend Integration | Cucumber + Testcontainers | 100% of features | Feature files → real DB assertions |
| Frontend Unit | Vitest + RTL | 80%+ | Components, reducers, forms |
| Frontend Integration | MSW | 100% of API flows | Network interception, error states |
| E2E | Playwright | Critical paths | Full user journeys |

### 11.3 Testcontainers Configuration

```java
@TestConfiguration
public class TestConfig {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("test_db")
        .withUsername("test")
        .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer("confluentinc/cp-kafka:7.5.0");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);
}
```

---

## 12. OBSERVABILITY SPECIFICATION

### 12.1 Three Pillars

| Pillar | Tool | Data Captured |
|--------|------|--------------|
| **Metrics** | Prometheus + Micrometer | Request count, latency histograms, error rates, JVM metrics, Kafka lag |
| **Logs** | Loki / ELK | Structured JSON logs with trace_id, tenant_id, user_id |
| **Traces** | Jaeger + OpenTelemetry | Full request lifecycle across services |

### 12.2 Trace Context Propagation

```
[React] X-Request-ID: abc123
    ↓
[Istio Gateway] injects traceparent header
    ↓
[Auth Service] reads traceparent, adds to logs + spans
    ↓
[Kafka Producer] includes trace_id in message metadata
    ↓
[Booking Service Consumer] extracts trace_id, continues span
    ↓
[PostgreSQL] trace_id in query logs (via pgaudit)
```

### 12.3 Dashboards

| Dashboard | Audience | Metrics |
|-----------|----------|---------|
| Platform Health | SYSTEM_ADMIN | Cluster CPU/memory, pod status, Kafka lag, error rate |
| Tenant Overview | SYSTEM_ADMIN | Active tenants, bookings per tenant, revenue per tenant |
| Clinic Operations | CLINIC_OWNER | Appointments today, practitioner utilization, revenue |
| API Performance | Developers | p50/p95/p99 latency per endpoint, error rate per service |

---

## 13. KUBERNETES & ISTIO SPECIFICATION

### 13.1 Namespace Structure

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: pracisos
  labels:
    istio-injection: enabled
```

### 13.2 Istio Resources

| Resource | Purpose |
|----------|---------|
| `Gateway` | Ingress entry point, TLS termination |
| `VirtualService` | URL routing to services |
| `DestinationRule` | Traffic policies, circuit breakers |
| `PeerAuthentication` | STRICT mTLS enforcement |
| `AuthorizationPolicy` | Service-to-service RBAC |
| `Telemetry` | Metrics and tracing configuration |

### 13.3 mTLS Configuration

```yaml
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
  namespace: pracisos
spec:
  mtls:
    mode: STRICT
```

### 13.4 Helm Chart Structure

```
charts/
└── spring-boot-microservice/
    ├── templates/
    │   ├── deployment.yaml
    │   ├── service.yaml
    │   ├── istio-gateway.yaml
    │   ├── istio-virtualservice.yaml
    │   ├── istio-security.yaml
    │   └── configmap.yaml
    └── values.yaml
```

---

## 14. INCREMENTAL DEVELOPMENT PLAN

### 14.1 Philosophy

**Build one service + its frontend slice in isolation. Validate. Then proceed.**

During development, design changes are permitted and expected. Any deviation from this specification must be:
1. Documented in the service's `CHANGELOG.md`
2. Approved by explicit acknowledgment (self-approval for solo development)
3. Reflected in the master specification (version bump)

### 14.2 Phase Breakdown

| Phase | Service | Frontend Slice | Infrastructure | Validation Criteria |
|-------|---------|---------------|----------------|-------------------|
| **1** | auth-service | Login, Register, Tenant Creation | Docker Compose (Postgres + auth-service + frontend) | JWT issuance, role-based access, tenant isolation |
| **2** | booking-service | Practitioner Calendar, Patient Booking | + Postgres + Redis | Availability CRUD, slot locking, no double-booking |
| **3** | Event wiring | — | + Kafka | Auth → Booking event sync, practitioner cache |
| **4** | charting-service | Note Editor, Amendment UI | + Postgres | Draft autosave, 24h auto-lock, immutability |
| **5** | billing-service | Invoice List, Payment Form, Revenue Dashboard | + Stripe sandbox | Auto-invoice on completion, payment flow, refunds |
| **6** | Full mesh | All features | Kubernetes + Istio (Kind) | mTLS, traffic routing, E2E tests in mesh |
| **7** | Observability | — | Prometheus, Grafana, Jaeger | Traces visible, metrics collected, alerts working |

### 14.3 Phase 1 Detail (Auth-Service + Frontend)

**Goal:** Working login/registration for all roles, JWT-based auth, tenant creation by SYSTEM_ADMIN.

**Backend Deliverables:**
- Maven multi-module `pom.xml` with Spring Boot 3.3, Spring Security, JPA, Flyway
- `Tenant` entity + CRUD API
- `User` entity + registration/login API
- JWT issuance with claims (sub, role, tenant_id, permissions)
- Password hashing (BCrypt)
- Method-level security (`@PreAuthorize`)
- Flyway migrations (V1__init.sql)
- Unit tests (JUnit + Mockito)
- Integration tests (Cucumber + Testcontainers)

**Frontend Deliverables:**
- Vite + React 19 + TypeScript scaffold
- Redux Toolkit store with auth slice
- RTK Query base setup with JWT interceptor
- Login form (email/password) with Zod validation
- Role-based route guarding
- Tenant context from URL path
- MSW setup for integration tests
- Unit tests (Vitest + RTL)

**Infrastructure:**
- `docker-compose.yml` with PostgreSQL 16
- `Dockerfile` for auth-service (multi-stage)
- `Dockerfile` for frontend (nginx serve)

**Validation:**
- SYSTEM_ADMIN can create tenant
- CLINIC_OWNER can login and see tenant dashboard
- PRACTITIONER can login and see practitioner dashboard
- PATIENT can login and see patient portal
- JWT correctly scopes all API calls
- No cross-tenant data leakage

---

## 15. DECISION LOG

| # | Decision | Rationale | Date |
|---|----------|-----------|------|
| 1 | Row-level tenant isolation (not schema-per-tenant) | Balanced isolation vs. complexity for learning | 2026-06-24 |
| 2 | UUID primary keys on all tables | Safe for distributed systems, event-driven architecture | 2026-06-24 |
| 3 | Auto-invoice on appointment completion (not manual) | Reduces clinic owner workload, simplifies flow | 2026-06-24 |
| 4 | Invoice status starts at ISSUED (not DRAFT) | Auto-issuance means no draft state | 2026-06-24 |
| 5 | Cancellation: full refund if >24h, no refund if <24h | Simple rule, teaches conditional logic | 2026-06-24 |
| 6 | RECEPTIONIST as separate role | Clear permission boundaries, real-world pattern | 2026-06-24 |
| 7 | Path-based tenant routing (`/{slug}/dashboard`) | Simplest for learning, no DNS needed | 2026-06-24 |
| 8 | Per-service audit endpoints (not central audit-service) | Simpler for incremental development | 2026-06-24 |
| 9 | No-show triggers invoice with penalty fee | Real-world business rule | 2026-06-24 |
| 10 | Optimistic UI with rollback on failure | Best UX practice, teaches Redux patterns | 2026-06-24 |
| 11 | Kafka topics partitioned by tenant_id | Ensures tenant event ordering | 2026-06-24 |
| 12 | JWT stored in Redux memory (not localStorage) | Security best practice | 2026-06-24 |

---

## 16. GLOSSARY

| Term | Definition |
|------|-----------|
| **Tenant** | A clinic/organization with isolated data |
| **mTLS** | Mutual TLS — both client and server authenticate |
| **Event** | Immutable record of something that happened |
| **Optimistic UI** | UI updates before backend confirms, rolls back on failure |
| **Testcontainers** | Java library for spinning up Docker containers during tests |
| **PeerAuthentication** | Istio resource enforcing mTLS mode |
| **Flyway** | Database migration tool |
| **RTK Query** | Redux Toolkit's data fetching and caching solution |
| **MSW** | Mock Service Worker — intercepts HTTP requests in tests |
| **BDD** | Behavior-Driven Development — Given/When/Then test format |

---

## 17. APPENDIX: COMPLETE DATA FLOW REFERENCE

### 17.1 Tenant Creation Flow

```
[SYSTEM_ADMIN] → POST /api/v1/auth/register
  → Auth Service: validate role, check slug uniqueness
  → INSERT tenants (tenant_id, slug, name)
  → EMIT TenantCreatedV1 → Kafka
  → All services: consume, register tenant in local cache
  → Response: 201 Created { tenant_id, slug, name }
```

### 17.2 User Invitation Flow

```
[CLINIC_OWNER] → POST /api/v1/auth/users
  → Auth Service: validate JWT, extract tenant_id, verify ownership
  → INSERT users (user_id, tenant_id, email, role, status='INVITED')
  → EMIT UserCreatedV1 → Kafka
  → Booking Service: consume → INSERT practitioners (practitioner_id=user_id, ...)
  → Response: 201 Created { user_id, email, role }
```

### 17.3 Availability Creation Flow

```
[PRACTITIONER] → POST /api/v1/booking/availability
  → Booking Service: validate JWT, extract practitioner_id, check overlaps
  → INSERT availability_templates
  → Generate concrete time_slots (next 30 days)
  → EMIT AvailabilityUpdatedV1 → Kafka
  → Response: 201 Created { template_id, slots_generated }
```

### 17.4 Booking Flow (Full)

```
[PATIENT] → GET /api/v1/booking/practitioners
  → Booking Service: SELECT * FROM practitioners WHERE tenant_id = ?
  → Response: [ { practitioner_id, name, ... } ]

[PATIENT] → GET /api/v1/booking/practitioners/{id}/slots
  → Booking Service: SELECT * FROM time_slots 
    WHERE tenant_id = ? AND practitioner_id = ? 
    AND status = 'AVAILABLE' AND start_time > NOW()
  → Response: [ { slot_id, start_time, end_time } ]

[PATIENT] → POST /api/v1/booking/appointments
  → Booking Service: BEGIN TX
    → SELECT ... FROM time_slots WHERE slot_id = ? FOR UPDATE
    → UPDATE time_slots SET status = 'BOOKED', version = version + 1
    → INSERT bookings (booking_id, tenant_id, slot_id, patient_id, ...)
  → COMMIT
  → EMIT BookingConfirmedV1 → Kafka
  → Charting Service: consume → INSERT clinical_notes (DRAFT)
  → Response: 201 Created { booking_id, status: 'CONFIRMED' }
```

### 17.5 Appointment Completion → Invoice Flow

```
[PRACTITIONER] → PUT /api/v1/booking/appointments/{id}/complete
  → Booking Service: validate ownership, UPDATE status = 'COMPLETED'
  → EMIT AppointmentCompletedV1 → Kafka
  → Billing Service: consume
    → Determine fee from appointment_type (CONSULTATION: $150)
    → INSERT invoices (status='ISSUED', amount_cents=15000)
    → EMIT InvoiceCreatedV1 → Kafka
  → Patient Portal: polling/WS receives InvoiceCreatedV1
  → Shows "Pay $150.00" button
```

### 17.6 Payment Flow

```
[PATIENT] → POST /api/v1/billing/invoices/{id}/pay
  → Billing Service: validate ownership, call Stripe API
  → Stripe: create PaymentIntent, return client_secret
  → Frontend: Stripe.js confirms payment
  → Stripe: sends webhook payment_intent.succeeded
  → Billing Service: verify signature, UPDATE invoices status='PAID'
  → INSERT payments (status='SUCCEEDED')
  → EMIT PaymentConfirmedV1 → Kafka
  → Clinic Owner Dashboard: update revenue total
```

### 17.7 Cancellation Flow

```
[PATIENT] → PUT /api/v1/booking/appointments/{id}/cancel
  → Booking Service: validate ownership, check cancellation window
  → BEGIN TX
    → UPDATE bookings SET status='CANCELLED', cancelled_at=NOW(), reason=?
    → UPDATE time_slots SET status='AVAILABLE'
  → COMMIT
  → EMIT BookingCancelledV1 → Kafka
  → Charting Service: consume → DELETE clinical_notes WHERE status='DRAFT'
  → Billing Service: consume
    → If invoice ISSUED and >24h: UPDATE status='REFUNDED', trigger Stripe refund
    → If invoice ISSUED and <24h: UPDATE status='CANCELLED'
  → Response: 200 OK { refund_eligible: true/false }
```

### 17.8 Clinical Note Locking Flow

```
[PRACTITIONER] → POST /api/v1/charting/notes/{id}/lock
  → Charting Service: validate ownership, verify status='DRAFT'
  → UPDATE clinical_notes SET status='IMMUTABLE', locked_at=NOW(), locked_by='PRACTITIONER'
  → EMIT ClinicalNoteLockedV1 → Kafka
  → Response: 200 OK { note_id, status: 'IMMUTABLE' }

[OR: Auto-lock daemon]
  → Hourly cron: SELECT * FROM clinical_notes 
    WHERE status='DRAFT' AND created_at < NOW() - INTERVAL '24 hours'
  → For each: UPDATE status='IMMUTABLE', locked_by='SYSTEM'
  → EMIT ClinicalNoteLockedV1 → Kafka
```

### 17.9 Amendment Flow

```
[PRACTITIONER] → POST /api/v1/charting/notes/{id}/amendments
  → Charting Service: validate ownership, verify note is IMMUTABLE
  → INSERT amendments (note_id, practitioner_id, amendment_text)
  → EMIT ClinicalNoteAmendedV1 → Kafka
  → Response: 201 Created { amendment_id, created_at }
  → Original note row is NEVER modified
```

### 17.10 Security Interceptor Flow

```
[ANY REQUEST] → Istio Gateway
  → Extract JWT from Authorization header
  → Validate signature, expiry
  → Extract claims: sub, role, tenant_id, permissions
  → @PreAuthorize: check role matches required
  → Check tenant_id matches resource's tenant_id
  → If ANY check fails: 403 + audit log entry
  → If all pass: proceed to controller
```

---

## END OF MASTER SPECIFICATION

**Version:** 1.0.0
**Status:** FINAL
**Next Step:** Document 2 — Auth-Service Implementation Guide

---
