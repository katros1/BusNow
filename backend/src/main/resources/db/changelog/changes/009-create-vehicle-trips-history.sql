-- liquibase formatted sql
-- changeset author:ba-backend-009 splitStatements:true

DROP TABLE IF EXISTS vehicle_trips CASCADE;

CREATE TABLE vehicle_trips (
    id                      uuid PRIMARY KEY,
    vt_bus_id               uuid NOT NULL REFERENCES busnow_bus(id),
    vt_route_id             uuid NOT NULL REFERENCES busnow_route(id),
    vt_status               varchar(16) NOT NULL,
    vt_started_at           timestamptz NOT NULL,
    vt_ended_at             timestamptz,
    vt_passengers_in        integer NOT NULL DEFAULT 0,
    vt_passengers_out       integer NOT NULL DEFAULT 0,
    vt_on_board             integer NOT NULL DEFAULT 0,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_vehicle_trips_bus_id    ON vehicle_trips (vt_bus_id);
CREATE INDEX idx_vehicle_trips_route_id  ON vehicle_trips (vt_route_id);
CREATE INDEX idx_vehicle_trips_started_at ON vehicle_trips (vt_started_at);
