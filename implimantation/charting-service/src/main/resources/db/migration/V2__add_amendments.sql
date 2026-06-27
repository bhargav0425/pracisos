CREATE TABLE IF NOT EXISTS amendments (
    amendment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    note_id UUID NOT NULL REFERENCES clinical_notes(note_id) ON DELETE CASCADE,
    practitioner_id UUID NOT NULL,
    amendment_text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_amendments_note ON amendments(note_id);
CREATE INDEX IF NOT EXISTS idx_amendments_tenant ON amendments(tenant_id);
