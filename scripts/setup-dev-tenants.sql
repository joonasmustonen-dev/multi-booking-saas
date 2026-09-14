-- Local development only. Run after the backend has initialized platform_db.
-- psql autocommit is required for CREATE DATABASE; do not wrap in a transaction.
SELECT 'CREATE DATABASE tenant_a OWNER booking'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'tenant_a')
\gexec
SELECT 'CREATE DATABASE tenant_b OWNER booking'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'tenant_b')
\gexec
INSERT INTO tenants (id, slug, name, database_name, status, created_at)
VALUES
 ('11111111-1111-4111-8111-111111111111', 'tenant-a', 'Demo workspace A', 'tenant_a', 'ACTIVE', now()),
 ('22222222-2222-4222-8222-222222222222', 'tenant-b', 'Demo workspace B', 'tenant_b', 'ACTIVE', now())
ON CONFLICT (slug) DO NOTHING;
\connect tenant_a
CREATE TABLE IF NOT EXISTS routing_test_data (tenant_marker text PRIMARY KEY);
INSERT INTO routing_test_data VALUES ('THIS_IS_TENANT_A') ON CONFLICT DO NOTHING;
\connect tenant_b
CREATE TABLE IF NOT EXISTS routing_test_data (tenant_marker text PRIMARY KEY);
INSERT INTO routing_test_data VALUES ('THIS_IS_TENANT_B') ON CONFLICT DO NOTHING;
