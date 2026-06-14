--liquibase formatted sql
-- changeset author:ba-backend-007 splitStatements:true
-- Add final passenger counters to trip (captured when trip ends)
ALTER TABLE busnow_trip
    ADD COLUMN IF NOT EXISTS tr_final_in  integer,
    ADD COLUMN IF NOT EXISTS tr_final_out integer;

-- GPS location history — one row per GPS frame received while gpsValid=true
CREATE TABLE IF NOT EXISTS busnow_vehicle_location (
    id                      uuid PRIMARY KEY,
    vl_bus_id               uuid NOT NULL REFERENCES busnow_bus(id),
    vl_trip_id              uuid REFERENCES busnow_trip(id),
    vl_latitude             double precision NOT NULL,
    vl_longitude            double precision NOT NULL,
    vl_speed_kmh            double precision,
    vl_heading_deg          double precision,
    vl_passengers_on_board  integer,
    vl_recorded_at          timestamptz NOT NULL,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_vl_bus_id      ON busnow_vehicle_location (vl_bus_id);
CREATE INDEX IF NOT EXISTS idx_vl_trip_id     ON busnow_vehicle_location (vl_trip_id);
CREATE INDEX IF NOT EXISTS idx_vl_recorded_at ON busnow_vehicle_location (vl_recorded_at DESC);
