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

CREATE TABLE IF NOT EXISTS gap_report (
    id              UUID PRIMARY KEY,
    tenant_id       VARCHAR(100) NOT NULL,
    framework       VARCHAR(200) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    total_chunks    INT NOT NULL,
    analyzed_chunks INT NOT NULL,
    failed_chunks   INT NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_gap_report_tenant ON gap_report (tenant_id, created_at DESC);

CREATE TABLE IF NOT EXISTS gap_finding (
    id          UUID PRIMARY KEY,
    report_id   UUID NOT NULL REFERENCES gap_report (id) ON DELETE CASCADE,
    position    INT NOT NULL,
    section     VARCHAR(300),
    requirement TEXT,
    status      VARCHAR(20) NOT NULL,
    severity    VARCHAR(10),
    finding     TEXT,
    evidence    TEXT
);
CREATE INDEX IF NOT EXISTS idx_gap_finding_report ON gap_finding (report_id, position);
