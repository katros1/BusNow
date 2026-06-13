CREATE TABLE IF NOT EXISTS busnow_route (
    id uuid PRIMARY KEY,
    route_name varchar(120) NOT NULL UNIQUE,
    route_geo geometry(LineString, 4326) NOT NULL,
    start_bus_park_id uuid NOT NULL REFERENCES busnow_bus_park(id),
    end_bus_park_id uuid NOT NULL REFERENCES busnow_bus_park(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_busnow_route_geo_srid CHECK (ST_SRID(route_geo) = 4326)
);

CREATE INDEX IF NOT EXISTS idx_busnow_route_geo_gist
    ON busnow_route USING GIST (route_geo);

CREATE INDEX IF NOT EXISTS idx_busnow_route_start_bus_park_id
    ON busnow_route (start_bus_park_id);

CREATE INDEX IF NOT EXISTS idx_busnow_route_end_bus_park_id
    ON busnow_route (end_bus_park_id);

CREATE TABLE IF NOT EXISTS busnow_route_stop (
    id uuid PRIMARY KEY,
    route_id uuid NOT NULL REFERENCES busnow_route(id) ON DELETE CASCADE,
    stop_id uuid NOT NULL REFERENCES busnow_bus_stop(id),
    rs_sequence integer NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_busnow_route_stop_sequence_positive CHECK (rs_sequence > 0),
    CONSTRAINT uk_busnow_route_stop_route_sequence UNIQUE (route_id, rs_sequence),
    CONSTRAINT uk_busnow_route_stop_route_stop UNIQUE (route_id, stop_id)
);

CREATE INDEX IF NOT EXISTS idx_busnow_route_stop_route_id
    ON busnow_route_stop (route_id);
