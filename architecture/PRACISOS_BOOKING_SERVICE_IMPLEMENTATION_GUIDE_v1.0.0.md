# PRACISOS PLATFORM
## BOOKING-SERVICE IMPLEMENTATION GUIDE
### Phase 2: Availability, Slot Management, Booking Lifecycle

---

## DOCUMENT CONTROL

| Field | Value |
|-------|-------|
| **Version** | 1.0.0 |
| **Status** | READY FOR IMPLEMENTATION |
| **Date** | 2026-06-27 |
| **Phase** | 2 of 7 |
| **Service** | booking-service |
| **Scope** | Practitioner Cache, Availability Templates, Time Slots, Booking CRUD, Status Lifecycle |
| **Prerequisite** | Master Specification v1.0.0, Auth-Service Guide v1.0.0 |

---

## 1. GOAL

Build the booking-service with its frontend slice that enables:
- **PRACTITIONER** to create recurring weekly availability templates
- **PATIENT** to view available slots and book appointments
- **RECEPTIONIST** to view/create bookings on behalf of patients
- **Pessimistic locking** on slots to prevent double-booking
- **Booking status lifecycle**: CONFIRMED -> COMPLETED/CANCELLED/NO_SHOW
- **Event publishing**: BookingConfirmedV1, BookingCancelledV1, AppointmentCompletedV1
- **Zero cross-tenant data leakage** -- every query filtered by tenant_id

**Validation:** Create availability -> patient books slot -> slot locked -> practitioner marks complete -> billing invoice auto-generated.

---

## 2. WHAT YOU WILL BUILD

### 2.1 Backend (Java 21 + Spring Boot 3.3 + Lombok)

```
booking-service/
├── pom.xml
├── src/
│   ├── main/java/com/pracisos/booking/
│   │   ├── BookingServiceApplication.java
│   │   ├── config/
│   │   │   ├── SecurityConfig.java
│   │   │   ├── KafkaConsumerConfig.java
│   │   │   └── RedisConfig.java
│   │   ├── controller/
│   │   │   ├── PractitionerController.java
│   │   │   ├── AvailabilityController.java
│   │   │   └── BookingController.java
│   │   ├── domain/
│   │   │   ├── entity/
│   │   │   │   ├── Practitioner.java
│   │   │   │   ├── AvailabilityTemplate.java
│   │   │   │   ├── TimeSlot.java
│   │   │   │   └── Booking.java
│   │   │   ├── repository/
│   │   │   │   ├── PractitionerRepository.java
│   │   │   │   ├── AvailabilityTemplateRepository.java
│   │   │   │   ├── TimeSlotRepository.java
│   │   │   │   └── BookingRepository.java
│   │   │   └── enums/
│   │   │       ├── SlotStatus.java
│   │   │       └── BookingStatus.java
│   │   ├── dto/
│   │   │   ├── request/
│   │   │   │   ├── AvailabilityCreateRequest.java
│   │   │   │   ├── AvailabilityUpdateRequest.java
│   │   │   │   ├── BookingCreateRequest.java
│   │   │   │   └── BookingStatusUpdateRequest.java
│   │   │   └── response/
│   │   │       ├── PractitionerResponse.java
│   │   │       ├── SlotResponse.java
│   │   │       └── BookingResponse.java
│   │   ├── service/
│   │   │   ├── PractitionerService.java
│   │   │   ├── AvailabilityService.java
│   │   │   ├── TimeSlotService.java
│   │   │   ├── BookingService.java
│   │   │   └── EventPublisher.java
│   │   ├── event/
│   │   │   ├── AuthEventConsumer.java
│   │   │   ├── BookingConfirmedEvent.java
│   │   │   ├── BookingCancelledEvent.java
│   │   │   ├── AppointmentCompletedEvent.java
│   │   │   └── AvailabilityUpdatedEvent.java
│   │   ├── security/
│   │   │   ├── JwtAuthenticationFilter.java
│   │   │   └── CustomUserDetailsService.java
│   │   └── exception/
│   │       ├── GlobalExceptionHandler.java
│   │       ├── SlotNotAvailableException.java
│   │       └── BookingNotFoundException.java
│   └── resources/
│       ├── application.yml
│       └── db/migration/
│           ├── V1__init.sql
│           └── V2__add_booking_constraints.sql
└── Dockerfile
```

### 2.2 Frontend (React 19 + Vite + Tailwind -- Jane App Inspired)

```
frontend/src/features/booking/
├── components/
│   ├── PractitionerCard.tsx       # Jane-style practitioner profile card
│   ├── AvailabilityCalendar.tsx   # Weekly calendar view (Jane-inspired)
│   ├── SlotGrid.tsx               # Time slot picker grid
│   ├── BookingForm.tsx            # Patient booking form
│   ├── BookingList.tsx            # Appointment list with status badges
│   ├── BookingDetailModal.tsx     # Detail view with actions
│   ├── StatusBadge.tsx            # Color-coded status indicators
│   └── CalendarNavigation.tsx     # Week/month navigation
├── api.ts
├── slice.ts
└── types.ts
```

---

## 3. DATABASE DESIGN

### 3.1 Flyway Migration V1__init.sql

```sql
-- Denormalized practitioner cache (synced from auth events)
CREATE TABLE IF NOT EXISTS practitioners (
    practitioner_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, practitioner_id)
);

CREATE INDEX IF NOT EXISTS idx_practitioners_tenant ON practitioners(tenant_id);
CREATE INDEX IF NOT EXISTS idx_practitioners_status ON practitioners(status);

-- Availability templates (recurring weekly patterns)
CREATE TABLE IF NOT EXISTS availability_templates (
    template_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    practitioner_id UUID NOT NULL REFERENCES practitioners(practitioner_id),
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    slot_duration_minutes INTEGER NOT NULL DEFAULT 30,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, practitioner_id, day_of_week, start_time, end_time)
);

CREATE INDEX IF NOT EXISTS idx_availability_tenant ON availability_templates(tenant_id);
CREATE INDEX IF NOT EXISTS idx_availability_practitioner ON availability_templates(practitioner_id);
CREATE INDEX IF NOT EXISTS idx_availability_active ON availability_templates(is_active);

-- Concrete time slots (generated from templates)
CREATE TABLE IF NOT EXISTS time_slots (
    slot_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    practitioner_id UUID NOT NULL REFERENCES practitioners(practitioner_id),
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, practitioner_id, start_time)
);

CREATE INDEX IF NOT EXISTS idx_slots_tenant ON time_slots(tenant_id);
CREATE INDEX IF NOT EXISTS idx_slots_practitioner ON time_slots(practitioner_id);
CREATE INDEX IF NOT EXISTS idx_slots_time ON time_slots(start_time, end_time);
CREATE INDEX IF NOT EXISTS idx_slots_status ON time_slots(status);

-- Bookings
CREATE TABLE IF NOT EXISTS bookings (
    booking_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    slot_id UUID NOT NULL UNIQUE REFERENCES time_slots(slot_id),
    patient_id UUID NOT NULL,
    practitioner_id UUID NOT NULL REFERENCES practitioners(practitioner_id),
    appointment_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    cancelled_at TIMESTAMPTZ,
    cancellation_reason VARCHAR(255),
    completed_at TIMESTAMPTZ,
    no_show_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_bookings_tenant ON bookings(tenant_id);
CREATE INDEX IF NOT EXISTS idx_bookings_patient ON bookings(patient_id);
CREATE INDEX IF NOT EXISTS idx_bookings_practitioner ON bookings(practitioner_id);
CREATE INDEX IF NOT EXISTS idx_bookings_status ON bookings(status);
CREATE INDEX IF NOT EXISTS idx_bookings_slot ON bookings(slot_id);
```

### 3.2 Flyway Migration V2__add_booking_constraints.sql

```sql
-- Ensure slot status consistency via trigger
CREATE OR REPLACE FUNCTION check_slot_booking_consistency()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status = 'CONFIRMED' THEN
        UPDATE time_slots SET status = 'BOOKED', version = version + 1
        WHERE slot_id = NEW.slot_id AND status = 'AVAILABLE';
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Slot not available for booking';
        END IF;
    ELSIF NEW.status = 'CANCELLED' AND OLD.status = 'CONFIRMED' THEN
        UPDATE time_slots SET status = 'AVAILABLE', version = version + 1
        WHERE slot_id = NEW.slot_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_booking_slot_consistency
    AFTER INSERT OR UPDATE ON bookings
    FOR EACH ROW
    EXECUTE FUNCTION check_slot_booking_consistency();
```

---

## 4. DOMAIN ENTITIES (Lombok)

### 4.1 Practitioner Entity

```java
package com.pracisos.booking.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "practitioners")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Practitioner {

    @Id
    @Column(name = "practitioner_id", nullable = false, updatable = false)
    private UUID practitionerId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
```

### 4.2 AvailabilityTemplate Entity

```java
package com.pracisos.booking.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "availability_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvailabilityTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "template_id", updatable = false, nullable = false)
    private UUID templateId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "practitioner_id", nullable = false)
    private UUID practitionerId;

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "slot_duration_minutes", nullable = false)
    @Builder.Default
    private Integer slotDurationMinutes = 30;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
```

### 4.3 TimeSlot Entity

```java
package com.pracisos.booking.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "time_slots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "slot_id", updatable = false, nullable = false)
    private UUID slotId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "practitioner_id", nullable = false)
    private UUID practitionerId;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "AVAILABLE";

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
```

### 4.4 Booking Entity

```java
package com.pracisos.booking.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "booking_id", updatable = false, nullable = false)
    private UUID bookingId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "slot_id", nullable = false, unique = true)
    private UUID slotId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "practitioner_id", nullable = false)
    private UUID practitionerId;

    @Column(name = "appointment_type", nullable = false, length = 50)
    private String appointmentType;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "CONFIRMED";

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "no_show_at")
    private Instant noShowAt;

    public boolean isCancellable() {
        return "CONFIRMED".equals(status);
    }

    public boolean isCompletable() {
        return "CONFIRMED".equals(status);
    }
}
```

---

## 5. REPOSITORIES

### 5.1 PractitionerRepository

```java
package com.pracisos.booking.domain.repository;

import com.pracisos.booking.domain.entity.Practitioner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PractitionerRepository extends JpaRepository<Practitioner, UUID> {

    List<Practitioner> findAllByTenantIdAndStatus(UUID tenantId, String status);

    Optional<Practitioner> findByPractitionerIdAndTenantId(UUID practitionerId, UUID tenantId);

    boolean existsByPractitionerIdAndTenantId(UUID practitionerId, UUID tenantId);
}
```

### 5.2 AvailabilityTemplateRepository

```java
package com.pracisos.booking.domain.repository;

import com.pracisos.booking.domain.entity.AvailabilityTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AvailabilityTemplateRepository extends JpaRepository<AvailabilityTemplate, UUID> {

    List<AvailabilityTemplate> findAllByTenantIdAndPractitionerIdAndIsActive(
        UUID tenantId, UUID practitionerId, Boolean isActive);

    List<AvailabilityTemplate> findAllByTenantIdAndIsActive(UUID tenantId, Boolean isActive);
}
```

### 5.3 TimeSlotRepository

```java
package com.pracisos.booking.domain.repository;

import com.pracisos.booking.domain.entity.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, UUID> {

    @Query("SELECT s FROM TimeSlot s WHERE s.tenantId = :tenantId AND s.practitionerId = :practitionerId " +
           "AND s.status = 'AVAILABLE' AND s.startTime > :from AND s.startTime < :to ORDER BY s.startTime")
    List<TimeSlot> findAvailableSlots(
        @Param("tenantId") UUID tenantId,
        @Param("practitionerId") UUID practitionerId,
        @Param("from") Instant from,
        @Param("to") Instant to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM TimeSlot s WHERE s.slotId = :slotId AND s.tenantId = :tenantId")
    Optional<TimeSlot> findByIdWithLock(@Param("slotId") UUID slotId, @Param("tenantId") UUID tenantId);

    List<TimeSlot> findAllByTenantIdAndPractitionerIdAndStartTimeBetween(
        UUID tenantId, UUID practitionerId, Instant start, Instant end);
}
```

### 5.4 BookingRepository

```java
package com.pracisos.booking.domain.repository;

import com.pracisos.booking.domain.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @Query("SELECT b FROM Booking b WHERE b.tenantId = :tenantId AND b.patientId = :patientId ORDER BY b.createdAt DESC")
    List<Booking> findAllByTenantIdAndPatientId(@Param("tenantId") UUID tenantId, @Param("patientId") UUID patientId);

    @Query("SELECT b FROM Booking b WHERE b.tenantId = :tenantId AND b.practitionerId = :practitionerId ORDER BY b.createdAt DESC")
    List<Booking> findAllByTenantIdAndPractitionerId(@Param("tenantId") UUID tenantId, @Param("practitionerId") UUID practitionerId);

    @Query("SELECT b FROM Booking b WHERE b.tenantId = :tenantId ORDER BY b.createdAt DESC")
    List<Booking> findAllByTenantId(@Param("tenantId") UUID tenantId);

    Optional<Booking> findByBookingIdAndTenantId(UUID bookingId, UUID tenantId);

    boolean existsBySlotIdAndTenantId(UUID slotId, UUID tenantId);
}
```

---

## 6. DTOs (Records + Lombok)

### 6.1 Requests

```java
package com.pracisos.booking.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalTime;
import java.util.UUID;

public record AvailabilityCreateRequest(
    @NotNull UUID practitionerId,
    @NotNull @Min(0) @Max(6) Integer dayOfWeek,
    @NotNull LocalTime startTime,
    @NotNull LocalTime endTime,
    @NotNull @Min(15) @Max(120) Integer slotDurationMinutes
) {}
```

```java
package com.pracisos.booking.dto.request;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record BookingCreateRequest(
    @NotNull UUID slotId,
    @NotNull UUID patientId,
    @NotNull UUID practitionerId,
    @NotBlank @Size(max = 50) String appointmentType,
    @Size(max = 500) String notes
) {}
```

```java
package com.pracisos.booking.dto.request;

import jakarta.validation.constraints.*;

public record BookingStatusUpdateRequest(
    @NotBlank String status,
    @Size(max = 255) String reason
) {}
```

### 6.2 Responses

```java
package com.pracisos.booking.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PractitionerResponse(
    UUID practitionerId,
    String firstName,
    String lastName,
    String fullName,
    String email,
    String status
) {}
```

```java
package com.pracisos.booking.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SlotResponse(
    UUID slotId,
    UUID practitionerId,
    Instant startTime,
    Instant endTime,
    String status
) {}
```

```java
package com.pracisos.booking.dto.response;

import java.time.Instant;
import java.util.UUID;

public record BookingResponse(
    UUID bookingId,
    UUID slotId,
    UUID patientId,
    UUID practitionerId,
    String practitionerName,
    String appointmentType,
    String status,
    String notes,
    Instant startTime,
    Instant endTime,
    Instant createdAt,
    Instant cancelledAt,
    Instant completedAt
) {}
```

---

## 7. SERVICES

### 7.1 PractitionerService

```java
package com.pracisos.booking.service;

import com.pracisos.booking.domain.entity.Practitioner;
import com.pracisos.booking.domain.repository.PractitionerRepository;
import com.pracisos.booking.dto.response.PractitionerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PractitionerService {

    private final PractitionerRepository practitionerRepository;

    @Transactional(readOnly = true)
    public List<PractitionerResponse> getPractitionersByTenant(UUID tenantId) {
        return practitionerRepository.findAllByTenantIdAndStatus(tenantId, "ACTIVE")
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PractitionerResponse getPractitioner(UUID tenantId, UUID practitionerId) {
        Practitioner practitioner = practitionerRepository
            .findByPractitionerIdAndTenantId(practitionerId, tenantId)
            .orElseThrow(() -> new RuntimeException("Practitioner not found"));
        return mapToResponse(practitioner);
    }

    private PractitionerResponse mapToResponse(Practitioner p) {
        return new PractitionerResponse(
            p.getPractitionerId(),
            p.getFirstName(),
            p.getLastName(),
            p.getFullName(),
            p.getEmail(),
            p.getStatus()
        );
    }
}
```

### 7.2 AvailabilityService

```java
package com.pracisos.booking.service;

import com.pracisos.booking.domain.entity.AvailabilityTemplate;
import com.pracisos.booking.domain.entity.TimeSlot;
import com.pracisos.booking.domain.repository.AvailabilityTemplateRepository;
import com.pracisos.booking.domain.repository.PractitionerRepository;
import com.pracisos.booking.dto.request.AvailabilityCreateRequest;
import com.pracisos.booking.event.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AvailabilityService {

    private final AvailabilityTemplateRepository templateRepository;
    private final PractitionerRepository practitionerRepository;
    private final TimeSlotService timeSlotService;
    private final EventPublisher eventPublisher;

    public AvailabilityTemplate createTemplate(UUID tenantId, AvailabilityCreateRequest request) {
        if (!practitionerRepository.existsByPractitionerIdAndTenantId(request.practitionerId(), tenantId)) {
            throw new RuntimeException("Practitioner not found in tenant");
        }

        AvailabilityTemplate template = AvailabilityTemplate.builder()
            .tenantId(tenantId)
            .practitionerId(request.practitionerId())
            .dayOfWeek(request.dayOfWeek())
            .startTime(request.startTime())
            .endTime(request.endTime())
            .slotDurationMinutes(request.slotDurationMinutes())
            .isActive(true)
            .build();

        template = templateRepository.save(template);
        log.info("Created availability template {} for practitioner {}",
            template.getTemplateId(), request.practitionerId());

        // Generate slots for next 30 days
        generateSlotsFromTemplate(template);
        eventPublisher.publishAvailabilityUpdated(tenantId, request.practitionerId());

        return template;
    }

    public void generateSlotsFromTemplate(AvailabilityTemplate template) {
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        List<TimeSlot> slots = new ArrayList<>();

        for (int i = 0; i < 30; i++) {
            LocalDate date = today.plusDays(i);
            if (date.getDayOfWeek().getValue() % 7 != template.getDayOfWeek()) continue;

            Instant dayStart = date.atTime(template.getStartTime()).atZone(ZoneId.of("UTC")).toInstant();
            Instant dayEnd = date.atTime(template.getEndTime()).atZone(ZoneId.of("UTC")).toInstant();
            long durationMs = template.getSlotDurationMinutes() * 60L * 1000L;

            for (Instant slotStart = dayStart; slotStart.isBefore(dayEnd); slotStart = slotStart.plusMillis(durationMs)) {
                Instant slotEnd = slotStart.plusMillis(durationMs);
                if (slotEnd.isAfter(dayEnd)) break;

                slots.add(TimeSlot.builder()
                    .tenantId(template.getTenantId())
                    .practitionerId(template.getPractitionerId())
                    .startTime(slotStart)
                    .endTime(slotEnd)
                    .status("AVAILABLE")
                    .build());
            }
        }

        timeSlotService.saveAllSlots(slots);
        log.info("Generated {} slots for template {}", slots.size(), template.getTemplateId());
    }

    @Transactional(readOnly = true)
    public List<AvailabilityTemplate> getTemplatesByPractitioner(UUID tenantId, UUID practitionerId) {
        return templateRepository.findAllByTenantIdAndPractitionerIdAndIsActive(tenantId, practitionerId, true);
    }
}
```

### 7.3 TimeSlotService

```java
package com.pracisos.booking.service;

import com.pracisos.booking.domain.entity.TimeSlot;
import com.pracisos.booking.domain.repository.TimeSlotRepository;
import com.pracisos.booking.dto.response.SlotResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TimeSlotService {

    private final TimeSlotRepository slotRepository;

    public void saveAllSlots(List<TimeSlot> slots) {
        slotRepository.saveAll(slots);
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> getAvailableSlots(UUID tenantId, UUID practitionerId, Instant from, Instant to) {
        return slotRepository.findAvailableSlots(tenantId, practitionerId, from, to)
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    public TimeSlot lockSlot(UUID slotId, UUID tenantId) {
        TimeSlot slot = slotRepository.findByIdWithLock(slotId, tenantId)
            .orElseThrow(() -> new RuntimeException("Slot not found"));

        if (!"AVAILABLE".equals(slot.getStatus())) {
            throw new RuntimeException("Slot is no longer available");
        }

        slot.setStatus("LOCKED");
        return slotRepository.save(slot);
    }

    public void unlockSlot(UUID slotId, UUID tenantId) {
        TimeSlot slot = slotRepository.findById(slotId)
            .orElseThrow(() -> new RuntimeException("Slot not found"));

        if (!slot.getTenantId().equals(tenantId)) {
            throw new RuntimeException("Cross-tenant access denied");
        }

        slot.setStatus("AVAILABLE");
        slotRepository.save(slot);
    }

    private SlotResponse mapToResponse(TimeSlot slot) {
        return new SlotResponse(
            slot.getSlotId(),
            slot.getPractitionerId(),
            slot.getStartTime(),
            slot.getEndTime(),
            slot.getStatus()
        );
    }
}
```

### 7.4 BookingService

```java
package com.pracisos.booking.service;

import com.pracisos.booking.domain.entity.Booking;
import com.pracisos.booking.domain.entity.TimeSlot;
import com.pracisos.booking.domain.repository.BookingRepository;
import com.pracisos.booking.domain.repository.TimeSlotRepository;
import com.pracisos.booking.dto.request.BookingCreateRequest;
import com.pracisos.booking.dto.response.BookingResponse;
import com.pracisos.booking.event.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class BookingService {

    private final BookingRepository bookingRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final TimeSlotService timeSlotService;
    private final EventPublisher eventPublisher;

    public BookingResponse createBooking(UUID tenantId, BookingCreateRequest request) {
        // Lock slot with pessimistic locking
        TimeSlot slot = timeSlotService.lockSlot(request.slotId(), tenantId);

        if (!slot.getPractitionerId().equals(request.practitionerId())) {
            timeSlotService.unlockSlot(request.slotId(), tenantId);
            throw new RuntimeException("Slot does not belong to specified practitioner");
        }

        Booking booking = Booking.builder()
            .tenantId(tenantId)
            .slotId(request.slotId())
            .patientId(request.patientId())
            .practitionerId(request.practitionerId())
            .appointmentType(request.appointmentType())
            .notes(request.notes())
            .status("CONFIRMED")
            .build();

        booking = bookingRepository.save(booking);
        log.info("Created booking {} for patient {} with practitioner {}",
            booking.getBookingId(), request.patientId(), request.practitionerId());

        eventPublisher.publishBookingConfirmed(booking, slot);
        return mapToResponse(booking, slot);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsByPatient(UUID tenantId, UUID patientId) {
        return bookingRepository.findAllByTenantIdAndPatientId(tenantId, patientId)
            .stream()
            .map(b -> mapToResponse(b, null))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsByPractitioner(UUID tenantId, UUID practitionerId) {
        return bookingRepository.findAllByTenantIdAndPractitionerId(tenantId, practitionerId)
            .stream()
            .map(b -> mapToResponse(b, null))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getAllBookings(UUID tenantId) {
        return bookingRepository.findAllByTenantId(tenantId)
            .stream()
            .map(b -> mapToResponse(b, null))
            .collect(Collectors.toList());
    }

    public BookingResponse cancelBooking(UUID tenantId, UUID bookingId, String reason) {
        Booking booking = bookingRepository.findByBookingIdAndTenantId(bookingId, tenantId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!booking.isCancellable()) {
            throw new RuntimeException("Booking cannot be cancelled");
        }

        booking.setStatus("CANCELLED");
        booking.setCancelledAt(Instant.now());
        booking.setCancellationReason(reason);
        booking = bookingRepository.save(booking);

        // Unlock the slot
        timeSlotService.unlockSlot(booking.getSlotId(), tenantId);

        eventPublisher.publishBookingCancelled(booking);
        log.info("Cancelled booking {} for tenant {}", bookingId, tenantId);

        return mapToResponse(booking, null);
    }

    public BookingResponse completeBooking(UUID tenantId, UUID bookingId) {
        Booking booking = bookingRepository.findByBookingIdAndTenantId(bookingId, tenantId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!booking.isCompletable()) {
            throw new RuntimeException("Booking cannot be completed");
        }

        booking.setStatus("COMPLETED");
        booking.setCompletedAt(Instant.now());
        booking = bookingRepository.save(booking);

        eventPublisher.publishAppointmentCompleted(booking);
        log.info("Completed booking {} for tenant {}", bookingId, tenantId);

        return mapToResponse(booking, null);
    }

    public BookingResponse markNoShow(UUID tenantId, UUID bookingId) {
        Booking booking = bookingRepository.findByBookingIdAndTenantId(bookingId, tenantId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!booking.isCompletable()) {
            throw new RuntimeException("Booking cannot be marked as no-show");
        }

        booking.setStatus("NO_SHOW");
        booking.setNoShowAt(Instant.now());
        booking = bookingRepository.save(booking);

        eventPublisher.publishAppointmentCompleted(booking); // Triggers billing with penalty
        log.info("Marked no-show for booking {} in tenant {}", bookingId, tenantId);

        return mapToResponse(booking, null);
    }

    private BookingResponse mapToResponse(Booking booking, TimeSlot slot) {
        Instant startTime = slot != null ? slot.getStartTime() : null;
        Instant endTime = slot != null ? slot.getEndTime() : null;

        return new BookingResponse(
            booking.getBookingId(),
            booking.getSlotId(),
            booking.getPatientId(),
            booking.getPractitionerId(),
            null, // practitionerName resolved at controller level if needed
            booking.getAppointmentType(),
            booking.getStatus(),
            booking.getNotes(),
            startTime,
            endTime,
            booking.getCreatedAt(),
            booking.getCancelledAt(),
            booking.getCompletedAt()
        );
    }
}
```

---

## 8. EVENT PUBLISHING

### 8.1 Event DTOs

```java
package com.pracisos.booking.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingConfirmedPayload {
    private UUID bookingId;
    private UUID tenantId;
    private UUID patientId;
    private UUID practitionerId;
    private UUID slotId;
    private Instant startTime;
    private Instant endTime;
    private String appointmentType;
}
```

```java
package com.pracisos.booking.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingCancelledPayload {
    private UUID bookingId;
    private UUID tenantId;
    private UUID patientId;
    private UUID practitionerId;
    private String cancellationReason;
    private Instant cancelledAt;
}
```

```java
package com.pracisos.booking.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentCompletedPayload {
    private UUID bookingId;
    private UUID tenantId;
    private UUID patientId;
    private UUID practitionerId;
    private String status;
    private Instant completedAt;
    private String appointmentType;
}
```

```java
package com.pracisos.booking.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityUpdatedPayload {
    private UUID tenantId;
    private UUID practitionerId;
    private Instant updatedAt;
}
```

### 8.2 Event Publisher

```java
package com.pracisos.booking.event;

import com.pracisos.booking.domain.entity.Booking;
import com.pracisos.booking.domain.entity.TimeSlot;
import com.pracisos.booking.event.dto.*;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Tracer tracer;

    private String currentTraceId() {
        var span = tracer.currentSpan();
        return span != null ? span.context().traceId() : "no-trace";
    }

    public void publishBookingConfirmed(Booking booking, TimeSlot slot) {
        var payload = new BookingConfirmedPayload(
            booking.getBookingId(), booking.getTenantId(), booking.getPatientId(),
            booking.getPractitionerId(), slot.getSlotId(),
            slot.getStartTime(), slot.getEndTime(), booking.getAppointmentType()
        );
        var event = new PracisosEvent<>("BookingConfirmedV1", booking.getTenantId(), currentTraceId(), payload);
        kafkaTemplate.send("booking.confirmed", booking.getTenantId().toString(), event);
        log.info("Published BookingConfirmedV1 for booking {}", booking.getBookingId());
    }

    public void publishBookingCancelled(Booking booking) {
        var payload = new BookingCancelledPayload(
            booking.getBookingId(), booking.getTenantId(), booking.getPatientId(),
            booking.getPractitionerId(), booking.getCancellationReason(),
            booking.getCancelledAt()
        );
        var event = new PracisosEvent<>("BookingCancelledV1", booking.getTenantId(), currentTraceId(), payload);
        kafkaTemplate.send("booking.cancelled", booking.getTenantId().toString(), event);
        log.info("Published BookingCancelledV1 for booking {}", booking.getBookingId());
    }

    public void publishAppointmentCompleted(Booking booking) {
        var payload = new AppointmentCompletedPayload(
            booking.getBookingId(), booking.getTenantId(), booking.getPatientId(),
            booking.getPractitionerId(), booking.getStatus(),
            Instant.now(), booking.getAppointmentType()
        );
        var event = new PracisosEvent<>("AppointmentCompletedV1", booking.getTenantId(), currentTraceId(), payload);
        kafkaTemplate.send("booking.completed", booking.getTenantId().toString(), event);
        log.info("Published AppointmentCompletedV1 for booking {}", booking.getBookingId());
    }

    public void publishAvailabilityUpdated(UUID tenantId, UUID practitionerId) {
        var payload = new AvailabilityUpdatedPayload(tenantId, practitionerId, Instant.now());
        var event = new PracisosEvent<>("AvailabilityUpdatedV1", tenantId, currentTraceId(), payload);
        kafkaTemplate.send("booking.availability.updated", tenantId.toString(), event);
        log.info("Published AvailabilityUpdatedV1 for practitioner {}", practitionerId);
    }
}
```

### 8.3 Auth Event Consumer (Practitioner Cache Sync)

```java
package com.pracisos.booking.event;

import com.pracisos.booking.domain.entity.Practitioner;
import com.pracisos.booking.domain.repository.PractitionerRepository;
import com.pracisos.booking.event.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthEventConsumer {

    private final PractitionerRepository practitionerRepository;

    @KafkaListener(topics = "auth.user.created", groupId = "booking-service")
    @Transactional
    public void handleUserCreated(UserCreatedEvent event, Acknowledgment ack) {
        log.info("Received UserCreated event: {} role={}", event.getUserId(), event.getRole());

        if (!"PRACTITIONER".equals(event.getRole()) && !"RECEPTIONIST".equals(event.getRole())) {
            ack.acknowledge();
            return;
        }

        if (practitionerRepository.existsById(event.getUserId())) {
            log.warn("Practitioner {} already exists, skipping", event.getUserId());
            ack.acknowledge();
            return;
        }

        Practitioner practitioner = Practitioner.builder()
            .practitionerId(event.getUserId())
            .tenantId(event.getTenantId())
            .firstName(event.getFirstName())
            .lastName(event.getLastName())
            .email(event.getEmail())
            .status("ACTIVE")
            .build();

        practitionerRepository.save(practitioner);
        log.info("Synced practitioner {} to booking-service", event.getUserId());
        ack.acknowledge();
    }

    @KafkaListener(topics = "auth.user.updated", groupId = "booking-service")
    @Transactional
    public void handleUserUpdated(UserUpdatedEvent event, Acknowledgment ack) {
        log.info("Received UserUpdated event: {}", event.getUserId());

        practitionerRepository.findById(event.getUserId()).ifPresentOrElse(
            practitioner -> {
                practitioner.setFirstName(event.getFirstName());
                practitioner.setLastName(event.getLastName());
                practitioner.setEmail(event.getEmail());
                if (event.getStatus() != null) {
                    practitioner.setStatus(event.getStatus());
                }
                practitionerRepository.save(practitioner);
                log.info("Updated practitioner {} in booking-service", event.getUserId());
            },
            () -> log.warn("Practitioner {} not found for update", event.getUserId())
        );
        ack.acknowledge();
    }

    @KafkaListener(topics = "auth.user.deactivated", groupId = "booking-service")
    @Transactional
    public void handleUserDeactivated(UserDeactivatedEvent event, Acknowledgment ack) {
        log.info("Received UserDeactivated event: {}", event.getUserId());

        practitionerRepository.findById(event.getUserId()).ifPresent(practitioner -> {
            practitioner.setStatus("INACTIVE");
            practitionerRepository.save(practitioner);
            log.info("Deactivated practitioner {} in booking-service", event.getUserId());
        });
        ack.acknowledge();
    }
}
```

### 8.4 Event DTOs (Mirrored from Auth)

```java
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
```

```java
package com.pracisos.booking.event.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdatedEvent {
    private UUID userId;
    private UUID tenantId;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
    private String status;
    private Instant updatedAt;
}
```

```java
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
```

---

## 9. CONTROLLERS

### 9.1 PractitionerController

```java
package com.pracisos.booking.controller;

import com.pracisos.booking.dto.response.PractitionerResponse;
import com.pracisos.booking.service.PractitionerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/booking")
@RequiredArgsConstructor
public class PractitionerController {

    private final PractitionerService practitionerService;

    @GetMapping("/practitioners")
    @PreAuthorize("hasAnyRole('PATIENT', 'PRACTITIONER', 'RECEPTIONIST', 'CLINIC_OWNER')")
    public ResponseEntity<List<PractitionerResponse>> getPractitioners(
        @RequestAttribute("tenantId") UUID tenantId
    ) {
        return ResponseEntity.ok(practitionerService.getPractitionersByTenant(tenantId));
    }

    @GetMapping("/practitioners/{id}")
    @PreAuthorize("hasAnyRole('PATIENT', 'PRACTITIONER', 'RECEPTIONIST', 'CLINIC_OWNER')")
    public ResponseEntity<PractitionerResponse> getPractitioner(
        @RequestAttribute("tenantId") UUID tenantId,
        @PathVariable UUID id
    ) {
        return ResponseEntity.ok(practitionerService.getPractitioner(tenantId, id));
    }
}
```

### 9.2 AvailabilityController

```java
package com.pracisos.booking.controller;

import com.pracisos.booking.domain.entity.AvailabilityTemplate;
import com.pracisos.booking.dto.request.AvailabilityCreateRequest;
import com.pracisos.booking.dto.response.SlotResponse;
import com.pracisos.booking.service.AvailabilityService;
import com.pracisos.booking.service.TimeSlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/booking")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;
    private final TimeSlotService timeSlotService;

    @PostMapping("/availability")
    @PreAuthorize("hasRole('PRACTITIONER')")
    public ResponseEntity<AvailabilityTemplate> createAvailability(
        @RequestAttribute("tenantId") UUID tenantId,
        @RequestAttribute("userId") UUID practitionerId,
        @Valid @RequestBody AvailabilityCreateRequest request
    ) {
        // Ensure practitioner can only set their own availability
        if (!practitionerId.equals(request.practitionerId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(availabilityService.createTemplate(tenantId, request));
    }

    @GetMapping("/practitioners/{id}/slots")
    @PreAuthorize("hasAnyRole('PATIENT', 'PRACTITIONER', 'RECEPTIONIST', 'CLINIC_OWNER')")
    public ResponseEntity<List<SlotResponse>> getAvailableSlots(
        @RequestAttribute("tenantId") UUID tenantId,
        @PathVariable UUID id,
        @RequestParam Instant from,
        @RequestParam Instant to
    ) {
        return ResponseEntity.ok(timeSlotService.getAvailableSlots(tenantId, id, from, to));
    }

    @GetMapping("/availability")
    @PreAuthorize("hasRole('PRACTITIONER')")
    public ResponseEntity<List<AvailabilityTemplate>> getMyAvailability(
        @RequestAttribute("tenantId") UUID tenantId,
        @RequestAttribute("userId") UUID practitionerId
    ) {
        return ResponseEntity.ok(availabilityService.getTemplatesByPractitioner(tenantId, practitionerId));
    }
}
```

### 9.3 BookingController

```java
package com.pracisos.booking.controller;

import com.pracisos.booking.dto.request.BookingCreateRequest;
import com.pracisos.booking.dto.request.BookingStatusUpdateRequest;
import com.pracisos.booking.dto.response.BookingResponse;
import com.pracisos.booking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/booking")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping("/appointments")
    @PreAuthorize("hasAnyRole('PATIENT', 'RECEPTIONIST')")
    public ResponseEntity<BookingResponse> createBooking(
        @RequestAttribute("tenantId") UUID tenantId,
        @Valid @RequestBody BookingCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(bookingService.createBooking(tenantId, request));
    }

    @GetMapping("/appointments")
    @PreAuthorize("hasAnyRole('PATIENT', 'PRACTITIONER', 'RECEPTIONIST', 'CLINIC_OWNER')")
    public ResponseEntity<List<BookingResponse>> getBookings(
        @RequestAttribute("tenantId") UUID tenantId,
        @RequestAttribute("userId") UUID userId,
        @RequestAttribute("role") String role
    ) {
        return switch (role) {
            case "PATIENT" -> ResponseEntity.ok(bookingService.getBookingsByPatient(tenantId, userId));
            case "PRACTITIONER" -> ResponseEntity.ok(bookingService.getBookingsByPractitioner(tenantId, userId));
            default -> ResponseEntity.ok(bookingService.getAllBookings(tenantId));
        };
    }

    @PutMapping("/appointments/{id}/cancel")
    @PreAuthorize("hasAnyRole('PATIENT', 'RECEPTIONIST')")
    public ResponseEntity<BookingResponse> cancelBooking(
        @RequestAttribute("tenantId") UUID tenantId,
        @PathVariable UUID id,
        @RequestBody BookingStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(bookingService.cancelBooking(tenantId, id, request.status()));
    }

    @PutMapping("/appointments/{id}/complete")
    @PreAuthorize("hasRole('PRACTITIONER')")
    public ResponseEntity<BookingResponse> completeBooking(
        @RequestAttribute("tenantId") UUID tenantId,
        @PathVariable UUID id
    ) {
        return ResponseEntity.ok(bookingService.completeBooking(tenantId, id));
    }

    @PutMapping("/appointments/{id}/no-show")
    @PreAuthorize("hasAnyRole('PRACTITIONER', 'RECEPTIONIST')")
    public ResponseEntity<BookingResponse> markNoShow(
        @RequestAttribute("tenantId") UUID tenantId,
        @PathVariable UUID id
    ) {
        return ResponseEntity.ok(bookingService.markNoShow(tenantId, id));
    }
}
```

---

## 10. FRONTEND: JANE APP INSPIRED DESIGN

### 10.1 Design Tokens (Tailwind Config)

```typescript
// tailwind.config.ts extensions
const janeTokens = {
  colors: {
    primary: {
      50: '#f0f9ff',
      100: '#e0f2fe',
      200: '#bae6fd',
      300: '#7dd3fc',
      400: '#38bdf8',
      500: '#0ea5e9',
      600: '#0284c7',
      700: '#0369a1',
      800: '#075985',
      900: '#0c4a6e',
    },
    success: {
      50: '#f0fdf4',
      100: '#dcfce7',
      500: '#22c55e',
      600: '#16a34a',
    },
    warning: {
      50: '#fffbeb',
      100: '#fef3c7',
      500: '#f59e0b',
      600: '#d97706',
    },
    surface: {
      50: '#fafaf9',
      100: '#f5f5f4',
      200: '#e7e5e4',
      300: '#d6d3d1',
    },
    status: {
      confirmed: '#0ea5e9',
      completed: '#22c55e',
      cancelled: '#ef4444',
      noShow: '#f97316',
      draft: '#6b7280',
    }
  },
  fontFamily: {
    sans: ['Inter', 'system-ui', 'sans-serif'],
  },
  borderRadius: {
    jane: '0.5rem',
    'jane-lg': '0.75rem',
  }
};
```

### 10.2 RTK Query API

```typescript
// features/booking/api.ts
import { createApi } from '@reduxjs/toolkit/query/react';
import { baseQuery } from '../../shared/api/baseQuery';

export interface Practitioner {
  practitionerId: string;
  firstName: string;
  lastName: string;
  fullName: string;
  email: string;
  status: string;
}

export interface TimeSlot {
  slotId: string;
  practitionerId: string;
  startTime: string;
  endTime: string;
  status: string;
}

export interface Booking {
  bookingId: string;
  slotId: string;
  patientId: string;
  practitionerId: string;
  appointmentType: string;
  status: 'CONFIRMED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';
  notes: string;
  startTime: string;
  endTime: string;
  createdAt: string;
  cancelledAt: string | null;
  completedAt: string | null;
}

export const bookingApi = createApi({
  reducerPath: 'bookingApi',
  baseQuery: baseQuery,
  tagTypes: ['Practitioner', 'Slot', 'Booking'],
  endpoints: (builder) => ({
    getPractitioners: builder.query<Practitioner[], void>({
      query: () => '/booking/practitioners',
      providesTags: ['Practitioner'],
    }),
    getPractitioner: builder.query<Practitioner, string>({
      query: (id) => `/booking/practitioners/${id}`,
      providesTags: (result, error, id) => [{ type: 'Practitioner', id }],
    }),
    getAvailableSlots: builder.query<TimeSlot[], { practitionerId: string; from: string; to: string }>({
      query: ({ practitionerId, from, to }) =>
        `/booking/practitioners/${practitionerId}/slots?from=${from}&to=${to}`,
      providesTags: ['Slot'],
    }),
    createBooking: builder.mutation<Booking, Partial<Booking>>({
      query: (body) => ({
        url: '/booking/appointments',
        method: 'POST',
        body,
      }),
      invalidatesTags: ['Slot', 'Booking'],
    }),
    getBookings: builder.query<Booking[], void>({
      query: () => '/booking/appointments',
      providesTags: ['Booking'],
    }),
    cancelBooking: builder.mutation<Booking, { bookingId: string; reason: string }>({
      query: ({ bookingId, reason }) => ({
        url: `/booking/appointments/${bookingId}/cancel`,
        method: 'PUT',
        body: { status: 'CANCELLED', reason },
      }),
      invalidatesTags: ['Slot', 'Booking'],
    }),
    completeBooking: builder.mutation<Booking, string>({
      query: (bookingId) => ({
        url: `/booking/appointments/${bookingId}/complete`,
        method: 'PUT',
      }),
      invalidatesTags: ['Booking'],
    }),
    markNoShow: builder.mutation<Booking, string>({
      query: (bookingId) => ({
        url: `/booking/appointments/${bookingId}/no-show`,
        method: 'PUT',
      }),
      invalidatesTags: ['Booking'],
    }),
  }),
});

export const {
  useGetPractitionersQuery,
  useGetPractitionerQuery,
  useGetAvailableSlotsQuery,
  useCreateBookingMutation,
  useGetBookingsQuery,
  useCancelBookingMutation,
  useCompleteBookingMutation,
  useMarkNoShowMutation,
} = bookingApi;
```

### 10.3 PractitionerCard (Jane-Style)

```tsx
// features/booking/components/PractitionerCard.tsx
import { Practitioner } from '../api';

interface Props {
  practitioner: Practitioner;
  onSelect: (practitioner: Practitioner) => void;
  isSelected?: boolean;
}

export function PractitionerCard({ practitioner, onSelect, isSelected }: Props) {
  return (
    <button
      onClick={() => onSelect(practitioner)}
      className={`w-full rounded-jane-lg border p-4 text-left transition-all hover:shadow-md ${
        isSelected
          ? 'border-primary-500 bg-primary-50 ring-2 ring-primary-200'
          : 'border-surface-200 bg-white hover:border-primary-300'
      }`}
    >
      <div className="flex items-center gap-4">
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-primary-100 text-primary-700 font-semibold text-lg">
          {practitioner.firstName[0]}{practitioner.lastName[0]}
        </div>
        <div className="flex-1">
          <h3 className="font-semibold text-slate-800">{practitioner.fullName}</h3>
          <p className="text-sm text-slate-500">{practitioner.email}</p>
        </div>
        <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${
          practitioner.status === 'ACTIVE'
            ? 'bg-success-100 text-success-600'
            : 'bg-surface-200 text-slate-500'
        }`}>
          {practitioner.status}
        </span>
      </div>
    </button>
  );
}
```

### 10.4 SlotGrid (Jane-Style Time Picker)

```tsx
// features/booking/components/SlotGrid.tsx
import { TimeSlot } from '../api';
import { format } from 'date-fns';

interface Props {
  slots: TimeSlot[];
  selectedSlotId?: string;
  onSelect: (slot: TimeSlot) => void;
}

export function SlotGrid({ slots, selectedSlotId, onSelect }: Props) {
  // Group slots by date
  const grouped = slots.reduce((acc, slot) => {
    const date = format(new Date(slot.startTime), 'yyyy-MM-dd');
    if (!acc[date]) acc[date] = [];
    acc[date].push(slot);
    return acc;
  }, {} as Record<string, TimeSlot[]>);

  return (
    <div className="space-y-6">
      {Object.entries(grouped).map(([date, daySlots]) => (
        <div key={date}>
          <h4 className="mb-3 text-sm font-semibold text-slate-600 uppercase tracking-wide">
            {format(new Date(date), 'EEEE, MMMM d')}
          </h4>
          <div className="grid grid-cols-3 gap-2 sm:grid-cols-4 md:grid-cols-6">
            {daySlots.map((slot) => (
              <button
                key={slot.slotId}
                onClick={() => onSelect(slot)}
                disabled={slot.status !== 'AVAILABLE'}
                className={`rounded-jane border px-3 py-2 text-sm font-medium transition-all ${
                  selectedSlotId === slot.slotId
                    ? 'border-primary-500 bg-primary-500 text-white shadow-md'
                    : slot.status === 'AVAILABLE'
                    ? 'border-primary-200 bg-primary-50 text-primary-700 hover:bg-primary-100'
                    : 'border-surface-200 bg-surface-100 text-slate-400 cursor-not-allowed'
                }`}
              >
                {format(new Date(slot.startTime), 'h:mm a')}
              </button>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
```

### 10.5 BookingList with Status Badges

```tsx
// features/booking/components/BookingList.tsx
import { Booking } from '../api';
import { format } from 'date-fns';

interface Props {
  bookings: Booking[];
  onCancel: (booking: Booking) => void;
  onComplete: (booking: Booking) => void;
  onNoShow: (booking: Booking) => void;
  userRole: string;
}

const statusConfig = {
  CONFIRMED: { bg: 'bg-primary-50', text: 'text-primary-700', label: 'Confirmed', dot: 'bg-primary-500' },
  COMPLETED: { bg: 'bg-success-50', text: 'text-success-700', label: 'Completed', dot: 'bg-success-500' },
  CANCELLED: { bg: 'bg-red-50', text: 'text-red-700', label: 'Cancelled', dot: 'bg-red-500' },
  NO_SHOW: { bg: 'bg-orange-50', text: 'text-orange-700', label: 'No Show', dot: 'bg-orange-500' },
};

export function BookingList({ bookings, onCancel, onComplete, onNoShow, userRole }: Props) {
  if (!bookings.length) {
    return (
      <div className="rounded-jane-lg border border-surface-200 bg-surface-50 p-8 text-center">
        <p className="text-slate-500">No appointments found</p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {bookings.map((booking) => {
        const status = statusConfig[booking.status];
        return (
          <div
            key={booking.bookingId}
            className="rounded-jane-lg border border-surface-200 bg-white p-4 hover:shadow-sm transition-shadow"
          >
            <div className="flex items-start justify-between">
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-2">
                  <span className={`h-2 w-2 rounded-full ${status.dot}`} />
                  <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${status.bg} ${status.text}`}>
                    {status.label}
                  </span>
                  <span className="text-xs text-slate-400">
                    {format(new Date(booking.startTime), 'MMM d, yyyy h:mm a')}
                  </span>
                </div>
                <h4 className="font-semibold text-slate-800">{booking.appointmentType}</h4>
                {booking.notes && (
                  <p className="mt-1 text-sm text-slate-500">{booking.notes}</p>
                )}
              </div>
              <div className="flex gap-2">
                {booking.status === 'CONFIRMED' && userRole === 'PATIENT' && (
                  <button
                    onClick={() => onCancel(booking)}
                    className="rounded-jane border border-red-200 px-3 py-1.5 text-sm text-red-600 hover:bg-red-50"
                  >
                    Cancel
                  </button>
                )}
                {booking.status === 'CONFIRMED' && userRole === 'PRACTITIONER' && (
                  <>
                    <button
                      onClick={() => onComplete(booking)}
                      className="rounded-jane bg-success-500 px-3 py-1.5 text-sm text-white hover:bg-success-600"
                    >
                      Complete
                    </button>
                    <button
                      onClick={() => onNoShow(booking)}
                      className="rounded-jane bg-orange-500 px-3 py-1.5 text-sm text-white hover:bg-orange-600"
                    >
                      No Show
                    </button>
                  </>
                )}
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
```

### 10.6 BookingForm (Patient Booking)

```tsx
// features/booking/components/BookingForm.tsx
import { useState } from 'react';
import { useCreateBookingMutation, useGetPractitionersQuery, useGetAvailableSlotsQuery } from '../api';
import { PractitionerCard } from './PractitionerCard';
import { SlotGrid } from './SlotGrid';

export function BookingForm() {
  const { data: practitioners } = useGetPractitionersQuery();
  const [selectedPractitioner, setSelectedPractitioner] = useState<string | null>(null);
  const [selectedSlot, setSelectedSlot] = useState<string | null>(null);
  const [appointmentType, setAppointmentType] = useState('CONSULTATION');
  const [notes, setNotes] = useState('');
  const [createBooking, { isLoading }] = useCreateBookingMutation();

  // Get slots for next 7 days
  const from = new Date().toISOString();
  const to = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString();
  const { data: slots } = useGetAvailableSlotsQuery(
    { practitionerId: selectedPractitioner!, from, to },
    { skip: !selectedPractitioner }
  );

  const handleSubmit = async () => {
    if (!selectedPractitioner || !selectedSlot) return;
    await createBooking({
      slotId: selectedSlot,
      practitionerId: selectedPractitioner,
      patientId: 'current-user-id',
      appointmentType,
      notes,
    });
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div className="rounded-jane-lg border border-surface-200 bg-white p-6">
        <h2 className="mb-4 text-lg font-semibold text-slate-800">Select Practitioner</h2>
        <div className="grid gap-3 sm:grid-cols-2">
          {practitioners?.map((p) => (
            <PractitionerCard
              key={p.practitionerId}
              practitioner={p}
              isSelected={selectedPractitioner === p.practitionerId}
              onSelect={(prac) => {
                setSelectedPractitioner(prac.practitionerId);
                setSelectedSlot(null);
              }}
            />
          ))}
        </div>
      </div>

      {selectedPractitioner && slots && (
        <div className="rounded-jane-lg border border-surface-200 bg-white p-6">
          <h2 className="mb-4 text-lg font-semibold text-slate-800">Select Time</h2>
          <SlotGrid
            slots={slots}
            selectedSlotId={selectedSlot || undefined}
            onSelect={(slot) => setSelectedSlot(slot.slotId)}
          />
        </div>
      )}

      {selectedSlot && (
        <div className="rounded-jane-lg border border-surface-200 bg-white p-6">
          <h2 className="mb-4 text-lg font-semibold text-slate-800">Booking Details</h2>
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-slate-700">Appointment Type</label>
              <select
                value={appointmentType}
                onChange={(e) => setAppointmentType(e.target.value)}
                className="mt-1 w-full rounded-jane border border-surface-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
              >
                <option value="CONSULTATION">Consultation</option>
                <option value="FOLLOW_UP">Follow-up</option>
                <option value="PROCEDURE">Procedure</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700">Notes</label>
              <textarea
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                rows={3}
                className="mt-1 w-full rounded-jane border border-surface-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
                placeholder="Any special requests..."
              />
            </div>
            <button
              onClick={handleSubmit}
              disabled={isLoading}
              className="w-full rounded-jane bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-primary-700 disabled:opacity-50"
            >
              {isLoading ? 'Booking...' : 'Confirm Booking'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
```

---

## 11. VALIDATION CHECKLIST

| # | Test | Action | Expected Result |
|---|------|--------|-----------------|
| 1 | Create availability | POST /booking/availability | Template created, slots generated for 30 days |
| 2 | View slots | GET /practitioners/{id}/slots | Lists AVAILABLE slots only |
| 3 | Book appointment | POST /booking/appointments | Booking created, slot status = BOOKED |
| 4 | Double-booking prevention | Try booking same slot twice | Second request fails with 409 Conflict |
| 5 | View bookings (patient) | GET /booking/appointments | Lists only patient's bookings |
| 6 | View bookings (practitioner) | GET /booking/appointments | Lists only practitioner's bookings |
| 7 | Cancel booking | PUT /appointments/{id}/cancel | Status = CANCELLED, slot = AVAILABLE |
| 8 | Complete booking | PUT /appointments/{id}/complete | Status = COMPLETED, emits AppointmentCompletedV1 |
| 9 | Mark no-show | PUT /appointments/{id}/no-show | Status = NO_SHOW, emits AppointmentCompletedV1 |
| 10 | Cross-tenant isolation | Request with wrong tenant_id | 403 Forbidden |
| 11 | Practitioner cache sync | Create user in auth-service | Appears in booking practitioners list |
| 12 | Role-based access | Patient tries to complete booking | 403 Forbidden |
| 13 | Slot generation | Create Mon-Fri 9-5 template | 40 slots generated per day x 30 days |
| 14 | Frontend booking flow | Select practitioner -> select slot -> confirm | Booking created, UI updates optimistically |

---

## 12. DOCKER COMPOSE UPDATE

Add to docker-compose.yml from Phase 3 (Event Wiring):

```yaml
  # --- Booking PostgreSQL ---
  postgres-booking:
    image: postgres:16-alpine
    container_name: pracisos-booking-db
    environment:
      POSTGRES_DB: booking_db
      POSTGRES_USER: booking_user
      POSTGRES_PASSWORD: booking_pass
    ports:
      - "5434:5432"
    volumes:
      - booking_postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U booking_user -d booking_db"]
      interval: 5s
      timeout: 5s
      retries: 5

  # --- Booking Service ---
  booking-service:
    build:
      context: ./booking-service
      dockerfile: Dockerfile
    container_name: pracisos-booking-service
    environment:
      DB_HOST: postgres-booking
      DB_PORT: 5432
      DB_NAME: booking_db
      DB_USER: booking_user
      DB_PASSWORD: booking_pass
      JWT_SECRET: ${JWT_SECRET:-change-me-in-production-min-256-bits-long}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      KAFKA_BOOTSTRAP: kafka:29092
    ports:
      - "8081:8080"
    depends_on:
      postgres-booking:
        condition: service_healthy
      redis:
        condition: service_healthy
      kafka:
        condition: service_healthy
```

---

## END OF BOOKING-SERVICE GUIDE

**Version:** 1.0.0 | **Status:** READY FOR IMPLEMENTATION | **Next:** Phase 5 -- Billing Service
