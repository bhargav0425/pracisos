-- Seed default tenant
INSERT INTO tenants (tenant_id, slug, name, status)
VALUES (
    'd76ba86a-f34e-480d-9b42-2e49f54b3cd5',
    'maple-health',
    'Maple Health Clinic',
    'ACTIVE'
) ON CONFLICT (slug) DO NOTHING;

-- Seed CLINIC_OWNER
INSERT INTO users (user_id, tenant_id, email, password_hash, first_name, last_name, role, status)
VALUES (
    'a9386fd4-bcf8-416f-b912-a785c006d479',
    'd76ba86a-f34e-480d-9b42-2e49f54b3cd5',
    'owner@maple-health.com',
    '$2a$12$U6e.XaLTpWS3KEQbkYpXSu.F04kcB82lWKYybzC3TYO71IJuGRzgq', -- BCrypt of 'admin123'
    'Jane',
    'Owner',
    'CLINIC_OWNER',
    'ACTIVE'
) ON CONFLICT (email) DO NOTHING;

-- Seed PRACTITIONER
INSERT INTO users (user_id, tenant_id, email, password_hash, first_name, last_name, role, status)
VALUES (
    'b8dc1926-d27b-48c7-b8dc-1926d27b0437',
    'd76ba86a-f34e-480d-9b42-2e49f54b3cd5',
    'dr.bob@maple-health.com',
    '$2a$12$U6e.XaLTpWS3KEQbkYpXSu.F04kcB82lWKYybzC3TYO71IJuGRzgq', -- BCrypt of 'admin123'
    'Bob',
    'Jones',
    'PRACTITIONER',
    'ACTIVE'
) ON CONFLICT (email) DO NOTHING;

-- Seed PATIENT
INSERT INTO users (user_id, tenant_id, email, password_hash, first_name, last_name, role, status)
VALUES (
    'c8e88edb-1f85-48c7-b8dc-1926d27b0437',
    'd76ba86a-f34e-480d-9b42-2e49f54b3cd5',
    'patient@maple-health.com',
    '$2a$12$U6e.XaLTpWS3KEQbkYpXSu.F04kcB82lWKYybzC3TYO71IJuGRzgq', -- BCrypt of 'admin123'
    'Alice',
    'Smith',
    'PATIENT',
    'ACTIVE'
) ON CONFLICT (email) DO NOTHING;
