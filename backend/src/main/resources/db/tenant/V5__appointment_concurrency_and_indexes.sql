ALTER TABLE appointments
ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX idx_appointments_service
    ON appointments(service_id);

CREATE INDEX idx_appointments_status_time
    ON appointments(status, start_at, end_at);