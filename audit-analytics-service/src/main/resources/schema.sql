CREATE TABLE IF NOT EXISTS audit_event (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id   VARCHAR(100) NOT NULL,
    user_id     VARCHAR(100),
    event_type  VARCHAR(50)  NOT NULL,
    query       TEXT,
    context     TEXT,
    answer      TEXT,
    latency_ms  BIGINT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_audit_tenant_created ON audit_event (tenant_id, created_at DESC);
