CREATE TABLE expense_categories (
                                    id          UUID PRIMARY KEY,
                                    name        VARCHAR(80)  NOT NULL UNIQUE,
                                    active      BOOLEAN      NOT NULL DEFAULT TRUE,
                                    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE workshop_expenses (
                                   id            UUID PRIMARY KEY,
                                   bus_id        UUID          NOT NULL,
                                   branch_id     UUID          NOT NULL,
                                   service_date  DATE          NOT NULL,
                                   total         NUMERIC(12,2) NOT NULL,
                                   created_by    UUID          NOT NULL,
                                   created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE workshop_expense_lines (
                                        id           UUID PRIMARY KEY,
                                        expense_id   UUID          NOT NULL REFERENCES workshop_expenses (id),
                                        category_id  UUID          NOT NULL REFERENCES expense_categories (id),
                                        description  VARCHAR(255),
                                        type         VARCHAR(10)   NOT NULL,
                                        amount       NUMERIC(12,2) NOT NULL
);

CREATE TABLE depreciation_rates (
                                    id              UUID PRIMARY KEY,
                                    amount_per_km   NUMERIC(10,4) NOT NULL,
                                    effective_from  TIMESTAMPTZ   NOT NULL,
                                    created_by      UUID          NOT NULL
);

CREATE TABLE trip_depreciations (
                                    trip_id        UUID PRIMARY KEY,
                                    bus_id         UUID          NOT NULL,
                                    branch_id      UUID          NOT NULL,
                                    km             NUMERIC(12,2) NOT NULL,
                                    rate_applied   NUMERIC(10,4) NOT NULL,
                                    amount         NUMERIC(12,2) NOT NULL,
                                    calculated_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE outbox_events (
                               id              UUID PRIMARY KEY,
                               aggregate_type  VARCHAR(50)  NOT NULL,
                               aggregate_id    UUID         NOT NULL,
                               event_type      VARCHAR(80)  NOT NULL,
                               topic           VARCHAR(80)  NOT NULL,
                               payload         JSONB        NOT NULL,
                               created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
                               published_at    TIMESTAMPTZ,
                               attempts        INTEGER      NOT NULL DEFAULT 0,
                               last_error      TEXT
);

CREATE TABLE processed_events (
                                  event_id      UUID PRIMARY KEY,
                                  event_type    VARCHAR(80) NOT NULL,
                                  processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO expense_categories (id, name) VALUES
    (gen_random_uuid(), 'Llantas'),
    (gen_random_uuid(), 'Motor'),
    (gen_random_uuid(), 'Frenos'),
    (gen_random_uuid(), 'Suspensión'),
    (gen_random_uuid(), 'Cambio de aceite'),
    (gen_random_uuid(), 'Eléctrico'),
    (gen_random_uuid(), 'Carrocería'),
    (gen_random_uuid(), 'Otros');
