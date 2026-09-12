CREATE TABLE availability_rules (
    id UUID PRIMARY KEY,
    resource_id UUID NOT NULL,
    day_of_week VARCHAR(10) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_availability_rule_resource
        FOREIGN KEY (resource_id)
        REFERENCES resources(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_availability_rule_time
        CHECK (end_time > start_time)
);

CREATE TABLE availability_exceptions (
    id UUID PRIMARY KEY,
    resource_id UUID NOT NULL,
    start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    end_at TIMESTAMP WITH TIME ZONE NOT NULL,
    available BOOLEAN NOT NULL,

    CONSTRAINT fk_availability_exception_resource
        FOREIGN KEY (resource_id)
        REFERENCES resources(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_availability_exception_time
        CHECK (end_at > start_at)
);

CREATE INDEX idx_availability_rules_resource
    ON availability_rules(resource_id);

CREATE INDEX idx_availability_exceptions_resource_time
    ON availability_exceptions(resource_id, start_at, end_at);