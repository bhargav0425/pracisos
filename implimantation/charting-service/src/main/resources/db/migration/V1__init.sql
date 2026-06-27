CREATE TABLE IF NOT EXISTS clinical_notes (
    note_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    practitioner_id UUID NOT NULL,
    booking_id UUID NOT NULL UNIQUE,
    appointment_type VARCHAR(50) NOT NULL,
    content JSONB NOT NULL DEFAULT '{}',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    locked_at TIMESTAMPTZ,
    locked_by VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_no_update_if_locked CHECK (
        status = 'DRAFT' OR (status = 'IMMUTABLE' AND locked_at IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_notes_tenant ON clinical_notes(tenant_id);
CREATE INDEX IF NOT EXISTS idx_notes_patient ON clinical_notes(patient_id);
CREATE INDEX IF NOT EXISTS idx_notes_practitioner ON clinical_notes(practitioner_id);
CREATE INDEX IF NOT EXISTS idx_notes_booking ON clinical_notes(booking_id);
CREATE INDEX IF NOT EXISTS idx_notes_status ON clinical_notes(status);
CREATE INDEX IF NOT EXISTS idx_notes_content ON clinical_notes USING GIN(content);
CREATE INDEX IF NOT EXISTS idx_notes_lock_check ON clinical_notes(status, created_at) 
    WHERE status = 'DRAFT';
