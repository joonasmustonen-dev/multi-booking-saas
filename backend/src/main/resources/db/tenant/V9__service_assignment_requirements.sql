ALTER TABLE services
    ADD COLUMN staff_requirement VARCHAR(16) NOT NULL DEFAULT 'FORBIDDEN',
    ADD COLUMN location_requirement VARCHAR(16) NOT NULL DEFAULT 'FORBIDDEN',
    ADD COLUMN resource_requirement VARCHAR(16) NOT NULL DEFAULT 'REQUIRED';

-- Preserve existing service capabilities without making requirements implicit at runtime.
UPDATE services s SET
    staff_requirement = CASE WHEN EXISTS (SELECT 1 FROM service_staff x WHERE x.service_id = s.id) THEN 'REQUIRED' ELSE 'FORBIDDEN' END,
    location_requirement = CASE WHEN EXISTS (SELECT 1 FROM service_locations x WHERE x.service_id = s.id) THEN 'REQUIRED' ELSE 'FORBIDDEN' END,
    resource_requirement = CASE
        WHEN NOT EXISTS (SELECT 1 FROM service_staff x WHERE x.service_id = s.id)
         AND NOT EXISTS (SELECT 1 FROM service_locations x WHERE x.service_id = s.id) THEN 'REQUIRED'
        WHEN EXISTS (SELECT 1 FROM service_resources x JOIN resources r ON r.id = x.resource_id
                     WHERE x.service_id = s.id AND r.type NOT IN ('STAFF', 'ROOM')) THEN 'OPTIONAL'
        ELSE 'FORBIDDEN' END;

ALTER TABLE services
    ADD CONSTRAINT chk_staff_requirement CHECK (staff_requirement IN ('REQUIRED', 'OPTIONAL', 'FORBIDDEN')),
    ADD CONSTRAINT chk_location_requirement CHECK (location_requirement IN ('REQUIRED', 'OPTIONAL', 'FORBIDDEN')),
    ADD CONSTRAINT chk_resource_requirement CHECK (resource_requirement IN ('REQUIRED', 'OPTIONAL', 'FORBIDDEN')),
    ADD CONSTRAINT chk_service_required_assignment CHECK (
        staff_requirement = 'REQUIRED' OR location_requirement = 'REQUIRED' OR resource_requirement = 'REQUIRED');

ALTER TABLE services ALTER COLUMN staff_requirement DROP DEFAULT,
    ALTER COLUMN location_requirement DROP DEFAULT,
    ALTER COLUMN resource_requirement DROP DEFAULT;
