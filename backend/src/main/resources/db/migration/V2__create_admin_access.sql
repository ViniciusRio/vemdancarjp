CREATE TABLE admin_access (
    id           UUID         PRIMARY KEY,
    google_sub   TEXT         NOT NULL UNIQUE,
    status       VARCHAR(8)   NOT NULL,
    requested_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    email        TEXT,
    name         TEXT,
    CONSTRAINT admin_access_status_chk CHECK (status IN ('PENDING', 'APPROVED'))
);
