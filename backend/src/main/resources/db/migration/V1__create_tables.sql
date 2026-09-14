-- V1__create_tables.sql
-- Schema inicial do backend Vem Dançar JP.
-- Espelha as tabelas existentes no Supabase (events, variable_venues,
-- other_venues, admins, pending_admins). DDL apenas; dados virao em passo separado.

CREATE TABLE events (
    id           BIGSERIAL    PRIMARY KEY,
    day_id       VARCHAR(10)  NOT NULL,
    name         TEXT         NOT NULL,
    venue        TEXT         NOT NULL,
    neighborhood TEXT         NOT NULL,
    instagram    TEXT,
    frequency    TEXT,
    sort_order   INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT events_day_id_chk CHECK (day_id IN (
        'monday', 'tuesday', 'wednesday', 'thursday',
        'friday', 'saturday', 'sunday'
    ))
);

CREATE TABLE variable_venues (
    id           BIGSERIAL    PRIMARY KEY,
    name         TEXT         NOT NULL,
    neighborhood TEXT         NOT NULL,
    instagram    TEXT,
    days         TEXT         NOT NULL,
    sort_order   INTEGER      NOT NULL DEFAULT 0
);

CREATE TABLE other_venues (
    id           BIGSERIAL    PRIMARY KEY,
    name         TEXT         NOT NULL,
    neighborhood TEXT         NOT NULL,
    instagram    TEXT,
    sort_order   INTEGER      NOT NULL DEFAULT 0
);

CREATE TABLE admins (
    id    UUID PRIMARY KEY,
    email TEXT NOT NULL UNIQUE
);

CREATE TABLE pending_admins (
    id           UUID PRIMARY KEY,
    email        TEXT NOT NULL UNIQUE,
    name         TEXT,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_events_day_id     ON events(day_id);
CREATE INDEX idx_events_sort_order ON events(sort_order);
