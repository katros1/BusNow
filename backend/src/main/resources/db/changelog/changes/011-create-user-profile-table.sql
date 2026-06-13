-- liquibase formatted sql
-- changeset author:ba-backend-011

CREATE TABLE IF NOT EXISTS busnow_user_profile (
    id                      uuid PRIMARY KEY,
    up_external_id          varchar(128) NOT NULL UNIQUE, -- Keycloak Sub (Subject)
    up_username             varchar(64) NOT NULL UNIQUE,
    up_email                varchar(128),
    up_first_name           varchar(64),
    up_last_name            varchar(64),
    up_roles                text, -- Comma separated roles or JSON
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_busnow_user_profile_external_id ON busnow_user_profile (up_external_id);
CREATE INDEX IF NOT EXISTS idx_busnow_user_profile_email       ON busnow_user_profile (up_email);
