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
