-- =========================================================
-- V7
-- Separate human staff and physical locations from the
-- generic resources table.
--
-- IMPORTANT:
-- Legacy STAFF and ROOM rows remain in resources for now.
-- Availability still depends on them and will be migrated
-- separately later.
-- =========================================================


-- =========================================================
-- 1. STAFF
-- =========================================================

CREATE TABLE staff_members (
    id UUID PRIMARY KEY,

    name VARCHAR(150) NOT NULL,

    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);


-- Preserve the UUIDs of existing STAFF resources.
INSERT INTO staff_members (
    id,
    name,
    active,
    created_at
)
SELECT
    id,
    name,
    active,
    created_at
FROM resources
WHERE type = 'STAFF';


-- =========================================================
-- 2. LOCATIONS
-- =========================================================

CREATE TABLE locations (
    id UUID PRIMARY KEY,

    name VARCHAR(150) NOT NULL,

    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);


-- Preserve the UUIDs of existing ROOM resources.
INSERT INTO locations (
    id,
    name,
    active,
    created_at
)
SELECT
    id,
    name,
    active,
    created_at
FROM resources
WHERE type = 'ROOM';


-- =========================================================
-- 3. APPOINTMENTS
-- =========================================================

-- Existing resource_id has to become optional because an
-- appointment can now have staff/location instead.

ALTER TABLE appointments
ALTER COLUMN resource_id DROP NOT NULL;


ALTER TABLE appointments
ADD COLUMN staff_id UUID;


ALTER TABLE appointments
ADD COLUMN location_id UUID;


ALTER TABLE appointments
ADD CONSTRAINT fk_appointment_staff
FOREIGN KEY (staff_id)
REFERENCES staff_members(id);


ALTER TABLE appointments
ADD CONSTRAINT fk_appointment_location
FOREIGN KEY (location_id)
REFERENCES locations(id);


-- ---------------------------------------------------------
-- Migrate old STAFF appointment assignments.
--
-- Before:
--   resource_id -> resource(type STAFF)
--
-- After:
--   staff_id -> staff_members
-- ---------------------------------------------------------

UPDATE appointments a
SET staff_id = r.id
FROM resources r
WHERE a.resource_id = r.id
  AND r.type = 'STAFF';


-- ---------------------------------------------------------
-- Migrate old ROOM appointment assignments.
--
-- Before:
--   resource_id -> resource(type ROOM)
--
-- After:
--   location_id -> locations
-- ---------------------------------------------------------

UPDATE appointments a
SET location_id = r.id
FROM resources r
WHERE a.resource_id = r.id
  AND r.type = 'ROOM';


-- resource_id should now represent only genuine generic
-- resources such as equipment/vehicles/other.
--
-- Clear STAFF and ROOM assignments from resource_id.

UPDATE appointments a
SET resource_id = NULL
FROM resources r
WHERE a.resource_id = r.id
  AND r.type IN ('STAFF', 'ROOM');


-- Useful query indexes.

CREATE INDEX idx_appointments_staff
ON appointments(staff_id);


CREATE INDEX idx_appointments_location
ON appointments(location_id);


-- =========================================================
-- 4. STAFF DOUBLE-BOOKING PROTECTION
-- =========================================================

-- btree_gist already exists from the appointment migration,
-- but keeping this here is harmless and makes V7 explicit.

CREATE EXTENSION IF NOT EXISTS btree_gist;


ALTER TABLE appointments
ADD CONSTRAINT no_overlapping_active_staff_appointments
EXCLUDE USING gist (
    staff_id WITH =,
    tstzrange(start_at, end_at, '[)') WITH &&
)
WHERE (
    status IN ('PENDING', 'CONFIRMED')
    AND staff_id IS NOT NULL
);


-- =========================================================
-- 5. LOCATION DOUBLE-BOOKING PROTECTION
-- =========================================================

ALTER TABLE appointments
ADD CONSTRAINT no_overlapping_active_location_appointments
EXCLUDE USING gist (
    location_id WITH =,
    tstzrange(start_at, end_at, '[)') WITH &&
)
WHERE (
    status IN ('PENDING', 'CONFIRMED')
    AND location_id IS NOT NULL
);


-- =========================================================
-- 6. SERVICE <-> STAFF
-- =========================================================

CREATE TABLE service_staff (
    service_id UUID NOT NULL,
    staff_id UUID NOT NULL,

    PRIMARY KEY (
        service_id,
        staff_id
    ),

    CONSTRAINT fk_service_staff_service
        FOREIGN KEY (service_id)
        REFERENCES services(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_service_staff_staff
        FOREIGN KEY (staff_id)
        REFERENCES staff_members(id)
);


-- Copy the existing service -> STAFF relationships.

INSERT INTO service_staff (
    service_id,
    staff_id
)
SELECT
    sr.service_id,
    sr.resource_id
FROM service_resources sr
JOIN resources r
    ON r.id = sr.resource_id
WHERE r.type = 'STAFF';


CREATE INDEX idx_service_staff_staff
ON service_staff(staff_id);


-- =========================================================
-- 7. SERVICE <-> LOCATION
-- =========================================================

CREATE TABLE service_locations (
    service_id UUID NOT NULL,
    location_id UUID NOT NULL,

    PRIMARY KEY (
        service_id,
        location_id
    ),

    CONSTRAINT fk_service_location_service
        FOREIGN KEY (service_id)
        REFERENCES services(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_service_location_location
        FOREIGN KEY (location_id)
        REFERENCES locations(id)
);


-- Copy the existing service -> ROOM relationships.

INSERT INTO service_locations (
    service_id,
    location_id
)
SELECT
    sr.service_id,
    sr.resource_id
FROM service_resources sr
JOIN resources r
    ON r.id = sr.resource_id
WHERE r.type = 'ROOM';


CREATE INDEX idx_service_locations_location
ON service_locations(location_id);


-- =========================================================
-- IMPORTANT: intentionally NOT done in V7
-- =========================================================
--
-- DO NOT:
--
-- DELETE FROM service_resources WHERE resource is STAFF/ROOM;
--
-- DELETE FROM resources WHERE type IN ('STAFF', 'ROOM');
--
-- remove STAFF or ROOM from ResourceType yet.
--
-- Availability still uses the old resource rows.
-- Those legacy relationships remain temporarily so that
-- the working availability engine is not destroyed while
-- we migrate the Java application.
-- =========================================================