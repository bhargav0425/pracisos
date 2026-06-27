-- Seed SYSTEM_ADMIN user (tenant_id is NULL for SYSTEM_ADMIN)
-- Email: admin@pracisos.com, Password: admin123
INSERT INTO users (user_id, tenant_id, email, password_hash, first_name, last_name, role, status)
VALUES (
    'a3e390c2-55cb-4340-a15d-3d441113b28b',
    NULL,
    'admin@pracisos.com',
    '$2a$12$U6e.XaLTpWS3KEQbkYpXSu.F04kcB82lWKYybzC3TYO71IJuGRzgq', -- BCrypt hash of 'admin123'
    'Platform',
    'Admin',
    'SYSTEM_ADMIN',
    'ACTIVE'
) ON CONFLICT (email) DO NOTHING;
