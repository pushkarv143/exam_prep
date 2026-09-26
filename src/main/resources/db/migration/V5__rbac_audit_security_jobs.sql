-- =====================================================================================
-- V5 — Admin Portal 2.0, phase A1: access foundation
--   * roles become data; permissions + role_permissions; TEACHER subject scopes
--   * login sessions (device list, revoke one / all)
--   * TOTP 2FA, admin IP allow-list, generic system settings
--   * immutable, month-partitioned audit log
--   * maker-checker approvals (policies + requests)
--   * background jobs (+ artifacts), transactional outbox, idempotency keys
--
-- Index strategy (for the A13 docs): every list screen is served by a composite index whose
-- leading columns are its equality filters, and whose last column is its sort key (usually a
-- timestamp DESC). This lets keyset pagination walk the index with no sort step.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 1. Roles & permissions
-- -------------------------------------------------------------------------------------
ALTER TABLE roles DROP CONSTRAINT chk_roles_name;
ALTER TABLE roles
    ADD COLUMN display_name    VARCHAR(80),
    ADD COLUMN system          BOOLEAN     NOT NULL DEFAULT FALSE,   -- built-in: cannot be deleted/renamed
    ADD COLUMN staff           BOOLEAN     NOT NULL DEFAULT TRUE,    -- may use /api/v1/admin/**
    ADD COLUMN all_permissions BOOLEAN     NOT NULL DEFAULT FALSE,   -- SUPER_ADMIN: implicitly every permission
    ADD COLUMN subject_scoped  BOOLEAN     NOT NULL DEFAULT FALSE,   -- content limited to assigned subjects
    ADD COLUMN created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at      TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE roles ADD CONSTRAINT chk_roles_name CHECK (name ~ '^[A-Z][A-Z0-9_]{1,31}$');

UPDATE roles SET name = 'SUPER_ADMIN', display_name = 'Super admin', system = TRUE, all_permissions = TRUE,
                 description = 'Full access to everything, including roles and security settings'
WHERE name = 'ADMIN';
UPDATE roles SET display_name = 'Student', system = TRUE, staff = FALSE,
                 description = 'Takes tests; no admin access'
WHERE name = 'STUDENT';
UPDATE roles SET display_name = 'Teacher', system = TRUE, subject_scoped = TRUE,
                 description = 'Authors questions and tests for the subjects assigned to them'
WHERE name = 'TEACHER';

INSERT INTO roles (name, display_name, description, system) VALUES
    ('CONTENT_MANAGER', 'Content manager', 'Owns the question bank and tests; approves publishing', TRUE),
    ('REVIEWER',        'Reviewer',        'Reviews and approves questions', TRUE),
    ('TEST_OPERATOR',   'Test operator',   'Schedules and runs live tests; handles results', TRUE),
    ('SUPPORT_AGENT',   'Support agent',   'Helps students: tickets, access, account issues', TRUE),
    ('FINANCE',         'Finance',         'Payments, refunds and invoices', TRUE),
    ('MARKETING',       'Marketing',       'Coupons, website content and communications', TRUE);

CREATE TABLE permissions (
    code          VARCHAR(64) PRIMARY KEY,
    module        VARCHAR(32)  NOT NULL,
    description   VARCHAR(255) NOT NULL,
    sensitive     BOOLEAN      NOT NULL DEFAULT FALSE,   -- highlighted in the role editor
    display_order INT          NOT NULL DEFAULT 0,
    CONSTRAINT chk_permissions_code CHECK (code ~ '^[a-z][a-z0-9_.-]{2,63}$')
);

CREATE TABLE role_permissions (
    role_id         SMALLINT    NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_code VARCHAR(64) NOT NULL REFERENCES permissions (code) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_code)
);
CREATE INDEX ix_role_permissions_perm ON role_permissions (permission_code);

-- Subject scopes for subject-scoped roles (TEACHER): which subjects a user may author.
CREATE TABLE user_subject_scopes (
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    subject_id UUID        NOT NULL REFERENCES subjects (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, subject_id)
);

-- The permission catalogue. Later phases add codes with new migrations; SUPER_ADMIN gets them
-- automatically (all_permissions). "module" groups them in the role editor.
INSERT INTO permissions (code, module, description, sensitive, display_order) VALUES
    ('admin.access',        'platform',  'Open the admin portal', FALSE, 1),
    ('dashboard.view',      'platform',  'View the business dashboard', FALSE, 2),
    ('settings.manage',     'platform',  'Change system settings', TRUE, 3),
    ('featureflag.manage',  'platform',  'Manage feature flags and remote config', TRUE, 4),
    ('tenant.manage',       'platform',  'Manage partner organisations', TRUE, 5),
    ('job.view',            'platform',  'View background jobs', FALSE, 6),
    ('job.manage',          'platform',  'Retry or cancel any background job', FALSE, 7),

    ('user.view',           'people',    'View staff and student accounts', FALSE, 10),
    ('user.create',         'people',    'Create accounts', FALSE, 11),
    ('user.update',         'people',    'Edit account details', FALSE, 12),
    ('user.status',         'people',    'Activate, deactivate or lock accounts', TRUE, 13),
    ('user.roles',          'people',    'Assign roles and subject scopes', TRUE, 14),
    ('user.pii.view',       'people',    'See unmasked phone numbers and emails', TRUE, 15),
    ('user.impersonate',    'people',    'Impersonate a student (read-only, audited)', TRUE, 16),
    ('student.view',        'people',    'Open Student 360', FALSE, 17),
    ('student.manage',      'people',    'Grant access, extend validity, merge accounts', TRUE, 18),

    ('role.view',           'security',  'View roles and permissions', FALSE, 20),
    ('role.manage',         'security',  'Create roles and change their permissions', TRUE, 21),
    ('audit.view',          'security',  'Search the audit log', FALSE, 22),
    ('audit.export',        'security',  'Export the audit log', TRUE, 23),
    ('approval.view',       'security',  'See approval requests', FALSE, 24),
    ('approval.decide',     'security',  'Approve or reject requests (needs the action''s own permission too)', TRUE, 25),
    ('security.manage',     'security',  'IP allow-list, approval policies, reset a user''s 2FA', TRUE, 26),

    ('catalog.view',        'content',   'View exams, subjects, chapters and topics', FALSE, 30),
    ('catalog.manage',      'content',   'Edit the syllabus catalog', FALSE, 31),
    ('question.view',       'content',   'Browse the question bank', FALSE, 32),
    ('question.create',     'content',   'Create questions', FALSE, 33),
    ('question.update',     'content',   'Edit own questions', FALSE, 34),
    ('question.update.any', 'content',   'Edit anyone''s questions', FALSE, 35),
    ('question.archive',    'content',   'Archive questions', FALSE, 36),
    ('question.import',     'content',   'Bulk-import questions', FALSE, 37),
    ('question.review',     'content',   'Review questions and request changes', FALSE, 38),
    ('question.approve',    'content',   'Approve questions', FALSE, 39),
    ('question.publish',    'content',   'Publish approved questions', FALSE, 40),
    ('file.upload',         'content',   'Upload images and files', FALSE, 41),

    ('series.view',         'tests',     'View test series', FALSE, 50),
    ('series.manage',       'tests',     'Create and edit test series; manage enrollments', FALSE, 51),
    ('series.publish',      'tests',     'Publish, unpublish or archive test series', TRUE, 52),
    ('batch.view',          'tests',     'View batches', FALSE, 53),
    ('batch.manage',        'tests',     'Manage batches and members', FALSE, 54),
    ('test.view',           'tests',     'View tests', FALSE, 55),
    ('test.create',         'tests',     'Create tests', FALSE, 56),
    ('test.update',         'tests',     'Edit tests and their questions', FALSE, 57),
    ('test.delete',         'tests',     'Delete draft tests', TRUE, 58),
    ('test.publish',        'tests',     'Publish, unpublish or archive tests', TRUE, 59),
    ('test.schedule',       'tests',     'Schedule tests and change windows', FALSE, 60),
    ('liveops.view',        'tests',     'Watch the live test command centre', FALSE, 61),
    ('liveops.control',     'tests',     'Emergency controls during live tests', TRUE, 62),

    ('result.view',         'results',   'View results and test statistics', FALSE, 70),
    ('result.finalize',     'results',   'Compute and publish final ranks', TRUE, 71),
    ('result.regenerate',   'results',   'Re-evaluate attempts', TRUE, 72),
    ('answerkey.manage',    'results',   'Publish answer keys and revise them', TRUE, 73),
    ('challenge.manage',    'results',   'Handle answer-key challenges', FALSE, 74),

    ('support.view',        'support',   'View support tickets and doubts', FALSE, 80),
    ('support.manage',      'support',   'Reply to and resolve tickets and doubts', FALSE, 81),

    ('payment.view',        'commerce',  'View payments and orders', FALSE, 90),
    ('refund.request',      'commerce',  'Request refunds', FALSE, 91),
    ('refund.approve',      'commerce',  'Approve refunds', TRUE, 92),
    ('coupon.manage',       'commerce',  'Manage coupons and referral codes', FALSE, 93),
    ('invoice.view',        'commerce',  'Download GST invoices', FALSE, 94),

    ('insights.view',       'insights',  'View analytics dashboards', FALSE, 100),
    ('insights.manage',     'insights',  'Edit behaviour rules, concept maps and recommendations', FALSE, 101),
    ('cms.manage',          'marketing', 'Edit website content, banners and SEO', FALSE, 110),
    ('comms.send',          'marketing', 'Send email / SMS / WhatsApp campaigns', TRUE, 111);

-- Default permissions per built-in role (editable from the UI afterwards).
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code FROM roles r JOIN permissions p ON (
    (r.name = 'CONTENT_MANAGER' AND p.code IN (
        'admin.access', 'dashboard.view', 'job.view', 'catalog.view', 'catalog.manage', 'question.view',
        'question.create', 'question.update', 'question.update.any', 'question.archive', 'question.import',
        'question.review', 'question.approve', 'question.publish', 'file.upload', 'series.view', 'series.manage',
        'batch.view', 'test.view', 'test.create', 'test.update', 'test.delete', 'test.publish', 'test.schedule',
        'result.view', 'answerkey.manage', 'challenge.manage', 'approval.view', 'approval.decide', 'audit.view',
        'insights.view'))
 OR (r.name = 'TEACHER' AND p.code IN (
        'admin.access', 'catalog.view', 'question.view', 'question.create', 'question.update', 'question.archive',
        'question.import', 'file.upload', 'series.view', 'batch.view', 'test.view', 'test.create', 'test.update',
        'result.view', 'challenge.manage', 'support.view'))
 OR (r.name = 'REVIEWER' AND p.code IN (
        'admin.access', 'catalog.view', 'question.view', 'question.review', 'question.approve', 'test.view',
        'result.view', 'challenge.manage'))
 OR (r.name = 'TEST_OPERATOR' AND p.code IN (
        'admin.access', 'dashboard.view', 'catalog.view', 'question.view', 'series.view', 'batch.view',
        'test.view', 'test.publish', 'test.schedule', 'liveops.view', 'liveops.control', 'result.view',
        'result.finalize', 'result.regenerate', 'approval.view', 'job.view'))
 OR (r.name = 'SUPPORT_AGENT' AND p.code IN (
        'admin.access', 'user.view', 'student.view', 'support.view', 'support.manage', 'payment.view',
        'series.view', 'test.view', 'result.view'))
 OR (r.name = 'FINANCE' AND p.code IN (
        'admin.access', 'dashboard.view', 'payment.view', 'refund.request', 'refund.approve', 'invoice.view',
        'coupon.manage', 'approval.view', 'approval.decide', 'audit.view', 'series.view'))
 OR (r.name = 'MARKETING' AND p.code IN (
        'admin.access', 'cms.manage', 'comms.send', 'coupon.manage', 'insights.view', 'series.view'))
);

-- -------------------------------------------------------------------------------------
-- 2. Login sessions (one row per session id "sid", across refreshes)
-- -------------------------------------------------------------------------------------
CREATE TABLE user_sessions (
    id            VARCHAR(36) PRIMARY KEY,           -- the JWT "sid"
    user_id       UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ  NOT NULL,
    ip            VARCHAR(64),
    user_agent    VARCHAR(512),
    mfa_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    revoked_at    TIMESTAMPTZ,
    revoke_reason VARCHAR(64)
);
CREATE INDEX ix_user_sessions_user_active ON user_sessions (user_id, last_seen_at DESC) WHERE revoked_at IS NULL;
CREATE INDEX ix_user_sessions_expiry ON user_sessions (expires_at);

-- -------------------------------------------------------------------------------------
-- 3. TOTP two-factor authentication
-- -------------------------------------------------------------------------------------
CREATE TABLE user_mfa (
    user_id           UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    secret_ciphertext TEXT        NOT NULL,           -- AES-256-GCM, base64(iv || ciphertext)
    enabled           BOOLEAN     NOT NULL DEFAULT FALSE,
    enabled_at        TIMESTAMPTZ,
    last_used_step    BIGINT      NOT NULL DEFAULT 0, -- blocks replay of an already used code
    recovery_codes    JSONB       NOT NULL DEFAULT '[]'::jsonb,   -- SHA-256 hashes, removed when used
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- -------------------------------------------------------------------------------------
-- 4. Admin IP allow-list + generic key/value settings
-- -------------------------------------------------------------------------------------
CREATE TABLE admin_ip_allowlist (
    id         UUID PRIMARY KEY,
    cidr       VARCHAR(64)  NOT NULL UNIQUE,
    label      VARCHAR(120) NOT NULL,
    created_by UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE system_settings (
    key        VARCHAR(96) PRIMARY KEY,
    value      JSONB       NOT NULL,
    updated_by UUID REFERENCES users (id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO system_settings (key, value) VALUES ('security.admin-ip-allowlist.enabled', 'false'::jsonb);

-- -------------------------------------------------------------------------------------
-- 5. Audit log: immutable, partitioned by month
-- -------------------------------------------------------------------------------------
CREATE TABLE audit_log (
    id          UUID         NOT NULL,
    occurred_at TIMESTAMPTZ  NOT NULL,
    actor_id    UUID,
    actor_email VARCHAR(255),
    actor_roles VARCHAR(255),
    action      VARCHAR(96)  NOT NULL,           -- e.g. test.publish, auth.login, "PUT /admin/tests/{id}"
    entity_type VARCHAR(48),
    entity_id   VARCHAR(64),
    outcome     VARCHAR(16)  NOT NULL,           -- SUCCESS | FAILURE | DENIED
    http_method VARCHAR(8),
    path        VARCHAR(512),
    status_code SMALLINT,
    error_code  VARCHAR(64),
    reason      TEXT,
    before      JSONB,
    after       JSONB,
    changes     JSONB,                           -- computed diff: [{path, op, from, to}]
    metadata    JSONB,                           -- request body (masked), approval ids, ...
    ip          VARCHAR(64),
    user_agent  VARCHAR(512),
    request_id  VARCHAR(64),
    PRIMARY KEY (id, occurred_at),
    CONSTRAINT chk_audit_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED'))
) PARTITION BY RANGE (occurred_at);

CREATE INDEX ix_audit_time ON audit_log (occurred_at DESC, id DESC);
CREATE INDEX ix_audit_actor ON audit_log (actor_id, occurred_at DESC);
CREATE INDEX ix_audit_entity ON audit_log (entity_type, entity_id, occurred_at DESC);
CREATE INDEX ix_audit_action ON audit_log (action, occurred_at DESC);

-- Safety net: rows land here only if a monthly partition was missing.
CREATE TABLE audit_log_default PARTITION OF audit_log DEFAULT;

-- Creates missing monthly partitions from the month of p_from, p_months months ahead.
-- Called here and daily by AuditPartitionJob. Idempotent.
CREATE OR REPLACE FUNCTION audit_ensure_partitions(p_from DATE, p_months INT) RETURNS INT AS $$
DECLARE
    start_d DATE;
    end_d   DATE;
    pname   TEXT;
    made    INT := 0;
BEGIN
    FOR i IN 0..p_months LOOP
        start_d := (date_trunc('month', p_from) + make_interval(months => i))::date;
        end_d := (start_d + INTERVAL '1 month')::date;
        pname := format('audit_log_y%sm%s', to_char(start_d, 'YYYY'), to_char(start_d, 'MM'));
        IF to_regclass(pname) IS NULL THEN
            EXECUTE format('CREATE TABLE %I PARTITION OF audit_log FOR VALUES FROM (%L) TO (%L)',
                           pname, start_d, end_d);
            made := made + 1;
        END IF;
    END LOOP;
    RETURN made;
END;
$$ LANGUAGE plpgsql;

SELECT audit_ensure_partitions((current_date - INTERVAL '1 month')::date, 4);

-- Immutability: nobody (including the application) may change or delete audit rows.
-- Retention is handled by dropping whole monthly partitions (a deliberate DBA action).
CREATE OR REPLACE FUNCTION audit_log_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only (% blocked)', TG_OP USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_immutable
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_immutable();

-- -------------------------------------------------------------------------------------
-- 6. Maker-checker approvals
-- -------------------------------------------------------------------------------------
CREATE TABLE approval_policies (
    action             VARCHAR(64) PRIMARY KEY,
    enabled            BOOLEAN       NOT NULL DEFAULT TRUE,
    threshold          NUMERIC(14, 2),                  -- e.g. refund amount; NULL = always
    approve_permission VARCHAR(64)   NOT NULL REFERENCES permissions (code),
    expiry_hours       INT           NOT NULL DEFAULT 72,
    description        VARCHAR(255)  NOT NULL,
    updated_by         UUID REFERENCES users (id) ON DELETE SET NULL,
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_approval_expiry CHECK (expiry_hours BETWEEN 1 AND 720)
);
INSERT INTO approval_policies (action, enabled, approve_permission, description) VALUES
    ('test.publish',      TRUE,  'test.publish',      'Publishing a test'),
    ('result.finalize',   TRUE,  'result.finalize',   'Computing and publishing final ranks'),
    ('result.regenerate', FALSE, 'result.regenerate', 'Re-evaluating an attempt'),
    ('role.update',       FALSE, 'role.manage',       'Changing a role''s permissions');

CREATE TABLE approval_requests (
    id               UUID PRIMARY KEY,
    action           VARCHAR(64)  NOT NULL REFERENCES approval_policies (action),
    entity_type      VARCHAR(48)  NOT NULL,
    entity_id        VARCHAR(64)  NOT NULL,
    title            VARCHAR(255) NOT NULL,
    payload          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    reason           TEXT,
    status           VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    requested_by     UUID         NOT NULL REFERENCES users (id),
    requested_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ  NOT NULL,
    decided_by       UUID REFERENCES users (id),
    decided_at       TIMESTAMPTZ,
    decision_comment TEXT,
    executed_at      TIMESTAMPTZ,
    result           JSONB,
    error            TEXT,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_approval_status CHECK (status IN
        ('PENDING', 'APPROVED', 'REJECTED', 'EXECUTED', 'FAILED', 'CANCELLED', 'EXPIRED'))
);
CREATE INDEX ix_approval_status ON approval_requests (status, requested_at DESC);
CREATE INDEX ix_approval_requester ON approval_requests (requested_by, requested_at DESC);
CREATE INDEX ix_approval_entity ON approval_requests (entity_type, entity_id);
-- At most one open request per action and entity.
CREATE UNIQUE INDEX ux_approval_open ON approval_requests (action, entity_id) WHERE status = 'PENDING';

-- -------------------------------------------------------------------------------------
-- 7. Background jobs (DB-backed queue, claimed with SKIP LOCKED)
-- -------------------------------------------------------------------------------------
CREATE TABLE jobs (
    id               UUID PRIMARY KEY,
    type             VARCHAR(64)  NOT NULL,
    status           VARCHAR(16)  NOT NULL DEFAULT 'QUEUED',
    priority         SMALLINT     NOT NULL DEFAULT 5,      -- lower runs first
    params           JSONB        NOT NULL DEFAULT '{}'::jsonb,
    progress         SMALLINT     NOT NULL DEFAULT 0,
    progress_message VARCHAR(255),
    result           JSONB,
    error            TEXT,
    attempts         SMALLINT     NOT NULL DEFAULT 0,
    max_attempts     SMALLINT     NOT NULL DEFAULT 3,
    run_after        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    locked_by        VARCHAR(64),
    locked_until     TIMESTAMPTZ,
    cancel_requested BOOLEAN      NOT NULL DEFAULT FALSE,
    idempotency_key  VARCHAR(128),
    created_by       UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    started_at       TIMESTAMPTZ,
    finished_at      TIMESTAMPTZ,
    CONSTRAINT chk_jobs_status CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'DEAD')),
    CONSTRAINT chk_jobs_progress CHECK (progress BETWEEN 0 AND 100)
);
CREATE INDEX ix_jobs_claim ON jobs (priority, run_after) WHERE status = 'QUEUED';
CREATE INDEX ix_jobs_stale ON jobs (locked_until) WHERE status = 'RUNNING';
CREATE INDEX ix_jobs_list ON jobs (created_at DESC);
CREATE INDEX ix_jobs_owner ON jobs (created_by, created_at DESC);
CREATE UNIQUE INDEX ux_jobs_idempotency ON jobs (type, idempotency_key) WHERE idempotency_key IS NOT NULL;

CREATE TABLE job_artifacts (
    job_id       UUID PRIMARY KEY REFERENCES jobs (id) ON DELETE CASCADE,
    filename     VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    data         BYTEA        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- -------------------------------------------------------------------------------------
-- 8. Transactional outbox (notifications, webhooks). At-least-once delivery.
-- -------------------------------------------------------------------------------------
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY,
    event_type      VARCHAR(64)  NOT NULL,
    aggregate_type  VARCHAR(48),
    aggregate_id    VARCHAR(64),
    payload         JSONB        NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempts        SMALLINT     NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    locked_until    TIMESTAMPTZ,
    last_error      TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at         TIMESTAMPTZ,
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'SENT', 'DEAD'))
);
CREATE INDEX ix_outbox_due ON outbox_events (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX ix_outbox_created ON outbox_events (created_at DESC);

-- -------------------------------------------------------------------------------------
-- 9. Idempotency keys for money / rank-changing requests
-- -------------------------------------------------------------------------------------
CREATE TABLE idempotency_keys (
    user_id         UUID         NOT NULL,
    idem_key        VARCHAR(128) NOT NULL,
    scope           VARCHAR(160) NOT NULL,            -- "POST /api/v1/admin/tests/{testId}/rankings/finalize"
    request_hash    CHAR(64)     NOT NULL,
    status          VARCHAR(16)  NOT NULL,            -- IN_PROGRESS | COMPLETED
    response_status SMALLINT,
    response_body   JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (user_id, idem_key)
);
CREATE INDEX ix_idempotency_expiry ON idempotency_keys (expires_at);
