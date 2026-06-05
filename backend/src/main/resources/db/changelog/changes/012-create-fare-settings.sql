--liquibase formatted sql

--changeset ba-lambert:012-create-fare-settings
CREATE TABLE fare_settings (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    base_price_frw NUMERIC(10, 2) NOT NULL DEFAULT 500.00,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Seed the one-and-only settings row
INSERT INTO fare_settings (base_price_frw) VALUES (500.00);
