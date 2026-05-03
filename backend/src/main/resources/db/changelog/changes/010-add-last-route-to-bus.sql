-- liquibase formatted sql
-- changeset author:ba-backend-010

ALTER TABLE iots_bus ADD COLUMN IF NOT EXISTS bus_last_completed_route_id uuid;
