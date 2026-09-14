-- =========================================================
-- V8
-- Availability can now belong independently to:
--   - staff
--   - location
--   - generic resource
--
-- Exactly one target must be set on every row.
-- =========================================================


-- =========================================================
-- AVAILABILITY RULES
-- =========================================================

ALTER TABLE availability_rules
ADD COLUMN staff_id UUID;

ALTER TABLE availability_rules
ADD COLUMN location_id UUID;


ALTER TABLE availability_rules
ALTER COLUMN resource_id DROP NOT NULL;


ALTER TABLE availability_rules
ADD CONSTRAINT fk_availability_rule_staff
FOREIGN KEY (staff_id)
REFERENCES staff_members(id);


ALTER TABLE availability_rules
ADD CONSTRAINT fk_availability_rule_location
FOREIGN KEY (location_id)
REFERENCES locations(id);


-- Move legacy STAFF rules to staff_id.
UPDATE availability_rules ar
SET
    staff_id = ar.resource_id,
    resource_id = NULL
FROM resources r
WHERE ar.resource_id = r.id
  AND r.type = 'STAFF';


-- Move legacy ROOM rules to location_id.
UPDATE availability_rules ar
SET
    location_id = ar.resource_id,
    resource_id = NULL
FROM resources r
WHERE ar.resource_id = r.id
  AND r.type = 'ROOM';


ALTER TABLE availability_rules
ADD CONSTRAINT chk_availability_rule_one_target
CHECK (
    num_nonnulls(
        staff_id,
        location_id,
        resource_id
    ) = 1
);


CREATE INDEX idx_availability_rules_staff
ON availability_rules(staff_id);

CREATE INDEX idx_availability_rules_location
ON availability_rules(location_id);


-- =========================================================
-- AVAILABILITY EXCEPTIONS
-- =========================================================

ALTER TABLE availability_exceptions
ADD COLUMN staff_id UUID;

ALTER TABLE availability_exceptions
ADD COLUMN location_id UUID;


ALTER TABLE availability_exceptions
ALTER COLUMN resource_id DROP NOT NULL;


ALTER TABLE availability_exceptions
ADD CONSTRAINT fk_availability_exception_staff
FOREIGN KEY (staff_id)
REFERENCES staff_members(id);


ALTER TABLE availability_exceptions
ADD CONSTRAINT fk_availability_exception_location
FOREIGN KEY (location_id)
REFERENCES locations(id);


UPDATE availability_exceptions ae
SET
    staff_id = ae.resource_id,
    resource_id = NULL
FROM resources r
WHERE ae.resource_id = r.id
  AND r.type = 'STAFF';


UPDATE availability_exceptions ae
SET
    location_id = ae.resource_id,
    resource_id = NULL
FROM resources r
WHERE ae.resource_id = r.id
  AND r.type = 'ROOM';


ALTER TABLE availability_exceptions
ADD CONSTRAINT chk_availability_exception_one_target
CHECK (
    num_nonnulls(
        staff_id,
        location_id,
        resource_id
    ) = 1
);


CREATE INDEX idx_availability_exceptions_staff
ON availability_exceptions(staff_id);

CREATE INDEX idx_availability_exceptions_location
ON availability_exceptions(location_id);