\connect platform_db

INSERT INTO workspace_memberships (
    id,
    tenant_id,
    identity_subject,
    email,
    role,
    status,
    created_at,
    updated_at
)
SELECT
    'e2000000-0000-4000-8000-000000000011',
    id,
    'e2000000-0000-4000-8000-000000000010',
    'e2e-admin@example.invalid',
    'TENANT_ADMIN',
    'ACTIVE',
    now(),
    now()
FROM tenants
WHERE slug = 'tenant-a'
ON CONFLICT (tenant_id, identity_subject) DO UPDATE SET
    email = EXCLUDED.email,
    role = EXCLUDED.role,
    status = 'ACTIVE',
    removed_at = NULL,
    updated_at = now();

\connect tenant_a

INSERT INTO customers (
    id, first_name, last_name, email, phone, created_at
) VALUES (
    'e2000000-0000-4000-8000-000000000001',
    'E2E',
    'Customer',
    'e2e-customer@example.invalid',
    '+358000000000',
    now()
) ON CONFLICT (id) DO UPDATE SET
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    email = EXCLUDED.email,
    phone = EXCLUDED.phone,
    erased_at = NULL,
    processing_restricted = false;

INSERT INTO resources (
    id, name, type, active, description, created_at
) VALUES (
    'e2000000-0000-4000-8000-000000000002',
    'E2E Resource',
    'EQUIPMENT',
    true,
    'Disposable browser-test fixture',
    now()
) ON CONFLICT (id) DO UPDATE SET active = true;

INSERT INTO services (
    id,
    name,
    description,
    duration_minutes,
    price,
    currency,
    active,
    created_at,
    staff_requirement,
    location_requirement,
    resource_requirement
) VALUES (
    'e2000000-0000-4000-8000-000000000003',
    'E2E Resource Booking',
    'Disposable browser-test fixture',
    60,
    25.00,
    'EUR',
    true,
    now(),
    'FORBIDDEN',
    'FORBIDDEN',
    'REQUIRED'
) ON CONFLICT (id) DO UPDATE SET active = true;

INSERT INTO service_resources (service_id, resource_id)
VALUES (
    'e2000000-0000-4000-8000-000000000003',
    'e2000000-0000-4000-8000-000000000002'
) ON CONFLICT DO NOTHING;

DELETE FROM availability_rules
WHERE resource_id = 'e2000000-0000-4000-8000-000000000002';

INSERT INTO availability_rules (
    id, resource_id, day_of_week, start_time, end_time, active
)
SELECT
    gen_random_uuid(),
    'e2000000-0000-4000-8000-000000000002',
    day_name,
    time '08:00',
    time '18:00',
    true
FROM unnest(ARRAY[
    'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY',
    'FRIDAY', 'SATURDAY', 'SUNDAY'
]) AS days(day_name);

UPDATE tenant_settings
SET
    time_zone = 'UTC',
    slot_interval_minutes = 30,
    minimum_notice_minutes = 0,
    booking_horizon_days = 365,
    default_appointment_status = 'CONFIRMED'
WHERE id = 1;
