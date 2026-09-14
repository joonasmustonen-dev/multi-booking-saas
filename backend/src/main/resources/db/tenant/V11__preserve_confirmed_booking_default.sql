-- Preserve the existing immediate-confirmation behavior when introducing workspace preferences.
-- Both workspace-preference migrations run before the settings API becomes available.
ALTER TABLE tenant_settings ALTER COLUMN default_appointment_status SET DEFAULT 'CONFIRMED';
UPDATE tenant_settings SET default_appointment_status = 'CONFIRMED';
