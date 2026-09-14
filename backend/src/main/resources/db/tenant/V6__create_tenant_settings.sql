CREATE TABLE tenant_settings (
    id SMALLINT PRIMARY KEY,
    time_zone VARCHAR(100) NOT NULL,

    CONSTRAINT chk_single_tenant_settings
        CHECK (id = 1)
);

INSERT INTO tenant_settings (
    id,
    time_zone
)
VALUES (
    1,
    'UTC'
);