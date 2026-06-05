--liquibase formatted sql

--changeset ba-lambert:013-fix-fare-settings-default
-- Ensure exactly one row exists with a sensible default.
-- If the table was seeded manually with a non-standard value (e.g. 50 from a test run),
-- this corrects it to 500 RWF so that fare calculations produce realistic amounts.
-- Rows with a custom value >= 100 are left untouched (admin may have set them intentionally).
UPDATE fare_settings
SET base_price_frw = 500.00, updated_at = NOW()
WHERE base_price_frw < 100;

-- Guard: if no row exists at all (migration 012 INSERT was skipped), create it.
INSERT INTO fare_settings (base_price_frw)
SELECT 500.00
WHERE NOT EXISTS (SELECT 1 FROM fare_settings);
