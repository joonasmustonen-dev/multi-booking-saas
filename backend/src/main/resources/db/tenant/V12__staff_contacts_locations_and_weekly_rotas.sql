ALTER TABLE staff_members
    ADD COLUMN email varchar(254) NOT NULL DEFAULT '',
    ADD COLUMN phone varchar(40) NOT NULL DEFAULT '',
    ADD COLUMN free_agent boolean NOT NULL DEFAULT true,
    ADD COLUMN removed boolean NOT NULL DEFAULT false;
CREATE TABLE staff_locations (
    staff_id uuid NOT NULL REFERENCES staff_members(id),
    location_id uuid NOT NULL REFERENCES locations(id),
    PRIMARY KEY (staff_id, location_id)
);
CREATE INDEX idx_staff_locations_location ON staff_locations(location_id);
ALTER TABLE resources ADD COLUMN description varchar(1000) NOT NULL DEFAULT '';
ALTER TABLE availability_exceptions ADD COLUMN schedule_week date;
CREATE INDEX idx_staff_weekly_exceptions ON availability_exceptions(staff_id, schedule_week) WHERE schedule_week IS NOT NULL;
ALTER TABLE availability_exceptions ADD CONSTRAINT weekly_exceptions_are_staff_owned
    CHECK (schedule_week IS NULL OR (staff_id IS NOT NULL AND location_id IS NULL AND resource_id IS NULL));
