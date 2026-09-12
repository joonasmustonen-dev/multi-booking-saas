CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE appointments (
    id UUID PRIMARY KEY,

    customer_id UUID NOT NULL,
    service_id UUID NOT NULL,
    resource_id UUID NOT NULL,

    start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    end_at TIMESTAMP WITH TIME ZONE NOT NULL,

    status VARCHAR(30) NOT NULL,

    notes TEXT,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_appointment_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(id),

    CONSTRAINT fk_appointment_service
        FOREIGN KEY (service_id)
        REFERENCES services(id),

    CONSTRAINT fk_appointment_resource
        FOREIGN KEY (resource_id)
        REFERENCES resources(id),

    CONSTRAINT chk_appointment_time
        CHECK (end_at > start_at)
);

CREATE INDEX idx_appointments_resource_time
    ON appointments(resource_id, start_at, end_at);

CREATE INDEX idx_appointments_customer
    ON appointments(customer_id);

ALTER TABLE appointments
ADD CONSTRAINT no_overlapping_active_appointments
EXCLUDE USING gist (
    resource_id WITH =,
    tstzrange(start_at, end_at, '[)') WITH &&
)
WHERE (
    status IN ('PENDING', 'CONFIRMED')
);