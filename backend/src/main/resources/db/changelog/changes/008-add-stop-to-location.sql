--liquibase formatted sql
-- changeset author:ba-backend-008 splitStatements:true
-- Link each recorded GPS frame to the bus stop it was in (null when between stops)
ALTER TABLE busnow_vehicle_location
    ADD COLUMN IF NOT EXISTS vl_stop_id uuid REFERENCES busnow_bus_stop(id);

CREATE INDEX IF NOT EXISTS idx_vl_stop_id ON busnow_vehicle_location (vl_stop_id);
