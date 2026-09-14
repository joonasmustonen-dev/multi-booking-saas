ALTER TABLE tenant_settings
    ADD COLUMN business_name varchar(254) NOT NULL DEFAULT 'Booking workspace',
    ADD COLUMN contact_email varchar(254) NOT NULL DEFAULT '',
    ADD COLUMN contact_phone varchar(254) NOT NULL DEFAULT '',
    ADD COLUMN default_currency varchar(254) NOT NULL DEFAULT 'EUR',
    ADD COLUMN slot_interval_minutes integer NOT NULL DEFAULT 15,
    ADD COLUMN minimum_notice_minutes integer NOT NULL DEFAULT 0,
    ADD COLUMN booking_horizon_days integer NOT NULL DEFAULT 365,
    ADD COLUMN calendar_start_hour integer NOT NULL DEFAULT 8,
    ADD COLUMN calendar_end_hour integer NOT NULL DEFAULT 18,
    ADD COLUMN week_starts_on integer NOT NULL DEFAULT 1,
    ADD COLUMN default_appointment_status varchar(254) NOT NULL DEFAULT 'PENDING';
ALTER TABLE tenant_settings ADD CONSTRAINT valid_booking_preferences CHECK (
    slot_interval_minutes BETWEEN 5 AND 120 AND minimum_notice_minutes BETWEEN 0 AND 43200
    AND booking_horizon_days BETWEEN 1 AND 730 AND calendar_start_hour BETWEEN 0 AND 23
    AND calendar_end_hour BETWEEN 1 AND 24 AND calendar_start_hour < calendar_end_hour
    AND week_starts_on IN (0, 1) AND default_appointment_status IN ('PENDING', 'CONFIRMED')
);
ALTER TABLE locations
    ADD COLUMN description varchar(1000) NOT NULL DEFAULT '',
    ADD COLUMN address_line varchar(200) NOT NULL DEFAULT '',
    ADD COLUMN city varchar(100) NOT NULL DEFAULT '',
    ADD COLUMN postal_code varchar(30) NOT NULL DEFAULT '',
    ADD COLUMN country_code varchar(2) NOT NULL DEFAULT '',
    ADD COLUMN phone varchar(40) NOT NULL DEFAULT '';
