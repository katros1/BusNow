CREATE TABLE IF NOT EXISTS busnow_bus_stop (
    id uuid PRIMARY KEY,
    bs_name varchar(120) NOT NULL UNIQUE,
    bs_geo geometry(Polygon, 4326) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_busnow_bus_stop_bs_geo_srid CHECK (ST_SRID(bs_geo) = 4326)
);

CREATE INDEX IF NOT EXISTS idx_busnow_bus_stop_bs_geo_gist
    ON busnow_bus_stop USING GIST (bs_geo);

CREATE TABLE IF NOT EXISTS busnow_bus_park (
    id uuid PRIMARY KEY,
    bp_name varchar(120) NOT NULL UNIQUE,
    bp_geo geometry(Polygon, 4326) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_busnow_bus_park_bp_geo_srid CHECK (ST_SRID(bp_geo) = 4326)
);

CREATE INDEX IF NOT EXISTS idx_busnow_bus_park_bp_geo_gist
    ON busnow_bus_park USING GIST (bp_geo);
