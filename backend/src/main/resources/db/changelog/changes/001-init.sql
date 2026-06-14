--liquibase formatted sql
-- changeset author:ba-backend-001 splitStatements:true
-- Enable PostGIS
CREATE EXTENSION IF NOT EXISTS postgis;