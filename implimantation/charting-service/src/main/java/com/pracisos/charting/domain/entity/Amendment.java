package com.pracisos.charting.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "amendments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Amendment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "amendment_id", updatable = false, nullable = false)
    private UUID amendmentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id", nullable = false)
    private ClinicalNote note;

    @Column(name = "practitioner_id", nullable = false)
    private UUID practitionerId;

    @Column(name = "amendment_text", nullable = false, columnDefinition = "TEXT")
    private String amendmentText;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
