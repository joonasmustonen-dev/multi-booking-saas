CREATE TABLE waitlist_entries (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    customer_id uuid NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    service_id uuid NOT NULL REFERENCES services(id),
    preferred_staff_id uuid REFERENCES staff_members(id) ON DELETE SET NULL,
    preferred_location_id uuid REFERENCES locations(id) ON DELETE SET NULL,
    window_start timestamptz NOT NULL,
    window_end timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    status varchar(16) NOT NULL,
    notification_consent boolean NOT NULL,
    consent_recorded_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT waitlist_window_valid CHECK (
        window_end > window_start
        AND window_end <= window_start + interval '31 days'
        AND expires_at <= window_end
    ),
    CONSTRAINT waitlist_status_valid CHECK (
        status IN ('WAITING', 'OFFERED', 'ACCEPTED', 'EXPIRED', 'REMOVED')
    ),
    CONSTRAINT waitlist_consent_recorded CHECK (
        notification_consent = false OR consent_recorded_at IS NOT NULL
    )
);

CREATE INDEX waitlist_match_idx ON waitlist_entries (
    service_id, status, window_start, window_end, created_at
) WHERE status = 'WAITING';

CREATE INDEX waitlist_customer_idx ON waitlist_entries (customer_id, created_at DESC);

CREATE TABLE waitlist_offers (
    id uuid PRIMARY KEY,
    entry_id uuid NOT NULL UNIQUE REFERENCES waitlist_entries(id) ON DELETE CASCADE,
    staff_id uuid REFERENCES staff_members(id) ON DELETE SET NULL,
    location_id uuid REFERENCES locations(id) ON DELETE SET NULL,
    resource_id uuid REFERENCES resources(id) ON DELETE SET NULL,
    start_at timestamptz NOT NULL,
    end_at timestamptz NOT NULL,
    status varchar(16) NOT NULL,
    notification_channel varchar(16) NOT NULL,
    notification_queued_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    appointment_id uuid UNIQUE REFERENCES appointments(id) ON DELETE SET NULL,
    CONSTRAINT waitlist_offer_time_valid CHECK (
        end_at > start_at AND expires_at > notification_queued_at
    ),
    CONSTRAINT waitlist_offer_status_valid CHECK (
        status IN ('OFFERED', 'ACCEPTED', 'EXPIRED', 'REMOVED')
    ),
    CONSTRAINT waitlist_notification_channel_valid CHECK (
        notification_channel IN ('EMAIL', 'SMS', 'IN_APP')
    )
);

CREATE INDEX waitlist_offer_expiry_idx ON waitlist_offers (expires_at)
WHERE status = 'OFFERED';
