ALTER TABLE customers
    ADD COLUMN processing_restricted boolean NOT NULL DEFAULT false,
    ADD COLUMN legal_hold boolean NOT NULL DEFAULT false,
    ADD COLUMN erased_at timestamptz,
    ADD COLUMN last_activity_at timestamptz NOT NULL DEFAULT now();

CREATE TABLE privacy_policy (
    id integer PRIMARY KEY CHECK (id = 1),
    customer_retention_days integer NOT NULL DEFAULT 0 CHECK (customer_retention_days BETWEEN 0 AND 36500),
    notes_retention_days integer NOT NULL DEFAULT 0 CHECK (notes_retention_days BETWEEN 0 AND 36500),
    staff_retention_days integer NOT NULL DEFAULT 0 CHECK (staff_retention_days BETWEEN 0 AND 36500),
    audit_retention_days integer NOT NULL DEFAULT 0 CHECK (audit_retention_days BETWEEN 0 AND 36500),
    scheduled_retention boolean NOT NULL DEFAULT false
);

INSERT INTO privacy_policy (id) VALUES (1);

ALTER TABLE staff_members ADD COLUMN removed_at timestamptz;

CREATE TABLE security_audit_event (
    id uuid PRIMARY KEY,
    occurred_at timestamptz NOT NULL,
    actor_id varchar(128) NOT NULL,
    action varchar(80) NOT NULL,
    record_id uuid,
    request_id varchar(36),
    outcome integer NOT NULL
);

CREATE INDEX security_audit_event_time_idx ON security_audit_event (occurred_at DESC);
CREATE INDEX customer_retention_idx ON customers (last_activity_at) WHERE erased_at IS NULL AND legal_hold = false;
