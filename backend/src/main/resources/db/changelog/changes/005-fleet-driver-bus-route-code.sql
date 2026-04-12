CREATE TABLE IF NOT EXISTS iots_driver (
    id uuid PRIMARY KEY,
    dr_first_name varchar(80) NOT NULL,
    dr_last_name varchar(80) NOT NULL,
    dr_gender varchar(16) NOT NULL,
    dr_phone_number varchar(32) NOT NULL UNIQUE,
    dr_license_number varchar(64) NOT NULL UNIQUE,
    dr_license_category varchar(8) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_iots_driver_gender CHECK (dr_gender IN ('MALE', 'FEMALE')),
    CONSTRAINT ck_iots_driver_license_category CHECK (dr_license_category IN ('A', 'B', 'C', 'D', 'D1', 'E', 'F'))
);

CREATE TABLE IF NOT EXISTS iots_route_code (
    id uuid PRIMARY KEY,
    rc_code varchar(32) NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE iots_route
    ADD COLUMN IF NOT EXISTS rt_code_id uuid REFERENCES iots_route_code(id),
    ADD COLUMN IF NOT EXISTS rt_direction varchar(16);

ALTER TABLE iots_route
    ADD CONSTRAINT ck_iots_route_rt_direction
        CHECK (rt_direction IN ('FORWARD', 'BACKWARD'));

CREATE UNIQUE INDEX IF NOT EXISTS uk_iots_route_rt_code_direction
    ON iots_route (rt_code_id, rt_direction);

CREATE INDEX IF NOT EXISTS idx_iots_route_rt_code_id
    ON iots_route (rt_code_id);

CREATE TABLE IF NOT EXISTS iots_bus (
    id uuid PRIMARY KEY,
    bus_plate_number varchar(32) NOT NULL UNIQUE,
    bus_gps_imei varchar(64) NOT NULL UNIQUE,
    bus_model varchar(120),
    bus_capacity integer,
    bus_current_latitude double precision,
    bus_current_longitude double precision,
    bus_current_driver_id uuid REFERENCES iots_driver(id),
    bus_route_code_id uuid REFERENCES iots_route_code(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_iots_bus_capacity_positive CHECK (bus_capacity IS NULL OR bus_capacity > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_iots_bus_current_driver_id
    ON iots_bus (bus_current_driver_id)
    WHERE bus_current_driver_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_iots_bus_route_code_id
    ON iots_bus (bus_route_code_id);
