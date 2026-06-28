-- Seed practitioner in booking-service (mirrors the auth-service user)
INSERT INTO practitioners (practitioner_id, tenant_id, first_name, last_name, email, status)
VALUES (
    'b8dc1926-d27b-48c7-b8dc-1926d27b0437',
    'd76ba86a-f34e-480d-9b42-2e49f54b3cd5',
    'Bob',
    'Jones',
    'dr.bob@maple-health.com',
    'ACTIVE'
) ON CONFLICT (practitioner_id) DO NOTHING;
