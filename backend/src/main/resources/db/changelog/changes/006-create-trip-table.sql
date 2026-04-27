CREATE TABLE IF NOT EXISTS iots_trip (
    id                      uuid PRIMARY KEY,
    tr_bus_id               uuid NOT NULL REFERENCES iots_bus(id),
    tr_route_id             uuid NOT NULL REFERENCES iots_route(id),
    tr_status               varchar(16) NOT NULL,
    tr_started_at           timestamptz NOT NULL,
    tr_ended_at             timestamptz,
    tr_snapshot_in          integer NOT NULL DEFAULT 0,
    tr_snapshot_out         integer NOT NULL DEFAULT 0,
    tr_passengers_on_board  integer NOT NULL DEFAULT 0,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_iots_trip_status CHECK (tr_status IN ('ACTIVE', 'COMPLETED')),
    CONSTRAINT ck_iots_trip_counts_positive CHECK (
        tr_snapshot_in >= 0 AND tr_snapshot_out >= 0 AND tr_passengers_on_board >= 0
    )
);

CREATE INDEX IF NOT EXISTS idx_iots_trip_bus_id    ON iots_trip (tr_bus_id);
CREATE INDEX IF NOT EXISTS idx_iots_trip_route_id  ON iots_trip (tr_route_id);
CREATE INDEX IF NOT EXISTS idx_iots_trip_status    ON iots_trip (tr_status);

-- Only one ACTIVE trip per bus at a time
CREATE UNIQUE INDEX IF NOT EXISTS uk_iots_trip_active_bus
    ON iots_trip (tr_bus_id)
    WHERE tr_status = 'ACTIVE';
