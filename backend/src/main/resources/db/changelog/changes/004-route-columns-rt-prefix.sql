--liquibase formatted sql
-- changeset author:ba-backend-004 splitStatements:true
ALTER TABLE busnow_route RENAME COLUMN route_name TO rt_name;
ALTER TABLE busnow_route RENAME COLUMN route_geo TO rt_geo;
ALTER TABLE busnow_route RENAME COLUMN start_bus_park_id TO rt_start_bus_park_id;
ALTER TABLE busnow_route RENAME COLUMN end_bus_park_id TO rt_end_bus_park_id;

ALTER TABLE busnow_route RENAME CONSTRAINT ck_busnow_route_geo_srid TO ck_busnow_route_rt_geo_srid;

ALTER INDEX idx_busnow_route_start_bus_park_id RENAME TO idx_busnow_route_rt_start_bus_park_id;
ALTER INDEX idx_busnow_route_end_bus_park_id RENAME TO idx_busnow_route_rt_end_bus_park_id;
