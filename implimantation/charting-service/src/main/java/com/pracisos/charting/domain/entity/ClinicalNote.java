package com.pracisos.charting.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "clinical_notes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClinicalNote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "note_id", updatable = false, nullable = false)
    private UUID noteId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "practitioner_id", nullable = false)
    private UUID practitionerId;

    @Column(name = "booking_id", nullable = false, unique = true)
    private UUID bookingId;

    @Column(name = "appointment_type", nullable = false, length = 50)
    private String appointmentType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private NoteContent content = new NoteContent();

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by", length = 50)
    private String lockedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "note", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Amendment> amendments = new ArrayList<>();

    public boolean isLocked() {
        return "IMMUTABLE".equals(status);
    }

    public boolean isAutoLockDue() {
        if (!"DRAFT".equals(status)) return false;
        return createdAt.plusSeconds(86400).isBefore(Instant.now());
    }

    public void lock(String lockedBy) {
        this.status = "IMMUTABLE";
        this.lockedAt = Instant.now();
        this.lockedBy = lockedBy;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NoteContent implements Serializable {
        private String subjective = "";
        private String objective = "";
        private String assessment = "";
        private String plan = "";
        private List<String> tags = new ArrayList<>();
    }
}
