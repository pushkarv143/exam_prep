-- =====================================================================================
-- V1__init.sql  —  Core schema for the ExamPrep test-series platform
-- =====================================================================================
-- Conventions
--   * Public entities use UUID primary keys (generated app-side as time-ordered UUIDv7,
--     which keeps B-tree inserts append-mostly instead of random).
--   * Every mutable table carries audit columns: created_at, updated_at, created_by,
--     updated_by, plus a `version` column for JPA optimistic locking.
--   * Enumerations are VARCHAR + CHECK constraints (easier to evolve than PG ENUM types).
--   * JSONB is used for question content / answers and denormalised analytics blobs.
--
-- Partitioning strategy (attempt_answers)
--   attempt_answers is the highest-volume table: ~90-180 rows per attempt, and a single
--   mock for 50k students produces ~9M rows. It is HASH-partitioned on attempt_id into
--   16 partitions:
--     - All hot queries (flush on submit, evaluation, solution review) filter by
--       attempt_id, so the planner prunes to exactly ONE partition.
--     - Writes spread evenly across partitions (no single hot index / page).
--     - Partitions can later be moved to separate tablespaces, or the table re-split
--       (e.g. 16 -> 64) with pg_partman / logical copy, without app changes.
--   Range partitioning by time was rejected because answers are always looked up by
--   attempt, never by date range; archival is done via attempts/results instead.
--   attempt_events (anti-cheat log) is append-only and can be range-partitioned by
--   server_ts once volume demands it; it is a plain table for now.
-- =====================================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid(), crypt() for seed data

-- -------------------------------------------------------------------------------------
-- Identity: users, roles
-- -------------------------------------------------------------------------------------
CREATE TABLE roles (
    id          SMALLSERIAL PRIMARY KEY,
    name        VARCHAR(32) NOT NULL UNIQUE,
    description VARCHAR(255),
    CONSTRAINT chk_roles_name CHECK (name IN ('STUDENT', 'TEACHER', 'ADMIN'))
);

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    phone           VARCHAR(20),
    password_hash   VARCHAR(100) NOT NULL,
    full_name       VARCHAR(150) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    email_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    avatar_url      VARCHAR(500),
    target_exam_code VARCHAR(32),
    city            VARCHAR(100),
    state           VARCHAR(100),
    last_login_at   TIMESTAMPTZ,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED'))
);
-- Case-insensitive uniqueness on email; phone unique when present.
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));
CREATE UNIQUE INDEX ux_users_phone ON users (phone) WHERE phone IS NOT NULL;
CREATE INDEX ix_users_created_at ON users (created_at DESC);

CREATE TABLE user_roles (
    user_id UUID     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id SMALLINT NOT NULL REFERENCES roles (id),
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX ix_user_roles_role ON user_roles (role_id);

-- -------------------------------------------------------------------------------------
-- Catalog: exam -> subject -> chapter -> topic
-- -------------------------------------------------------------------------------------
CREATE TABLE exams (
    id            UUID PRIMARY KEY,
    code          VARCHAR(32)  NOT NULL UNIQUE,
    name          VARCHAR(150) NOT NULL,
    description   TEXT,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order INT          NOT NULL DEFAULT 0,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID
);

CREATE TABLE subjects (
    id            UUID PRIMARY KEY,
    exam_id       UUID         NOT NULL REFERENCES exams (id),
    code          VARCHAR(32)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    display_order INT          NOT NULL DEFAULT 0,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    CONSTRAINT ux_subjects_exam_code UNIQUE (exam_id, code)
);

CREATE TABLE chapters (
    id            UUID PRIMARY KEY,
    subject_id    UUID         NOT NULL REFERENCES subjects (id),
    name          VARCHAR(200) NOT NULL,
    display_order INT          NOT NULL DEFAULT 0,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    CONSTRAINT ux_chapters_subject_name UNIQUE (subject_id, name)
);

CREATE TABLE topics (
    id            UUID PRIMARY KEY,
    chapter_id    UUID         NOT NULL REFERENCES chapters (id),
    name          VARCHAR(200) NOT NULL,
    display_order INT          NOT NULL DEFAULT 0,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    CONSTRAINT ux_topics_chapter_name UNIQUE (chapter_id, name)
);

-- -------------------------------------------------------------------------------------
-- Batches (cohorts of students; used to gate test-series access)
-- -------------------------------------------------------------------------------------
CREATE TABLE batches (
    id          UUID PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    exam_id     UUID REFERENCES exams (id),
    start_date  DATE,
    end_date    DATE,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID
);

CREATE TABLE batch_members (
    batch_id  UUID        NOT NULL REFERENCES batches (id) ON DELETE CASCADE,
    user_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (batch_id, user_id)
);
CREATE INDEX ix_batch_members_user ON batch_members (user_id);

-- -------------------------------------------------------------------------------------
-- Question bank
-- -------------------------------------------------------------------------------------
-- content JSONB (student-visible parts + solution; the API strips `solution` for students):
--   { "text": "LaTeX-capable markdown", "images": [{"url": "...", "alt": "..."}],
--     "options": [{"id": "A", "text": "...", "image": null}, ...],
--     "paragraph": "...",                              -- PARAGRAPH type (shared passage)
--     "matchLeft": [{"id":"P","text":".."}], "matchRight": [{"id":"1","text":".."}],
--     "solution": {"text": "...", "videoUrl": "..."} }
-- correct_answer JSONB (NEVER sent to students during a test):
--   SINGLE_CORRECT   {"options": ["B"]}
--   MULTIPLE_CORRECT {"options": ["A", "C"]}
--   NUMERICAL        {"value": 2.5, "tolerance": 0.01}      (tolerance optional)
--   MATCH            {"pairs": {"P": "2", "Q": "1", ...}}
CREATE TABLE questions (
    id                     UUID PRIMARY KEY,
    type                   VARCHAR(30)   NOT NULL,
    difficulty             VARCHAR(10)   NOT NULL DEFAULT 'MEDIUM',
    language               VARCHAR(5)    NOT NULL DEFAULT 'EN',
    exam_id                UUID          REFERENCES exams (id),
    subject_id             UUID          NOT NULL REFERENCES subjects (id),
    chapter_id             UUID          REFERENCES chapters (id),
    topic_id               UUID          REFERENCES topics (id),
    parent_id              UUID          REFERENCES questions (id),   -- paragraph group
    content                JSONB         NOT NULL,
    correct_answer         JSONB         NOT NULL,
    default_marks          NUMERIC(6, 2) NOT NULL DEFAULT 4,
    default_negative_marks NUMERIC(6, 2) NOT NULL DEFAULT 1,
    status                 VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    source                 VARCHAR(200),
    year                   SMALLINT,
    version                BIGINT        NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by             UUID,
    updated_by             UUID,
    CONSTRAINT chk_questions_type CHECK (type IN ('SINGLE_CORRECT', 'MULTIPLE_CORRECT', 'NUMERICAL', 'MATCH', 'PARAGRAPH')),
    CONSTRAINT chk_questions_difficulty CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    CONSTRAINT chk_questions_language CHECK (language IN ('EN', 'HI')),
    CONSTRAINT chk_questions_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT chk_questions_marks CHECK (default_marks >= 0 AND default_negative_marks >= 0)
);
-- Supports the auto-generator: "N questions of topic X, difficulty Y".
CREATE INDEX ix_questions_topic_difficulty ON questions (topic_id, difficulty) WHERE status = 'ACTIVE';
CREATE INDEX ix_questions_subject ON questions (subject_id, status);
CREATE INDEX ix_questions_chapter ON questions (chapter_id);
CREATE INDEX ix_questions_parent ON questions (parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX ix_questions_created_at ON questions (created_at DESC);

CREATE TABLE question_tags (
    question_id UUID        NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    tag         VARCHAR(50) NOT NULL,
    PRIMARY KEY (question_id, tag)
);
CREATE INDEX ix_question_tags_tag ON question_tags (tag);

-- -------------------------------------------------------------------------------------
-- Test series & tests
-- -------------------------------------------------------------------------------------
CREATE TABLE test_series (
    id            UUID PRIMARY KEY,
    exam_id       UUID           NOT NULL REFERENCES exams (id),
    name          VARCHAR(200)   NOT NULL,
    slug          VARCHAR(220)   NOT NULL UNIQUE,
    description   TEXT,
    thumbnail_url VARCHAR(500),
    price         NUMERIC(10, 2) NOT NULL DEFAULT 0,
    currency      VARCHAR(3)     NOT NULL DEFAULT 'INR',
    is_free       BOOLEAN        NOT NULL DEFAULT FALSE,
    validity_days INT,                         -- access length from enrollment date
    valid_until   TIMESTAMPTZ,                 -- or a hard end date (whichever is earlier)
    batch_restricted BOOLEAN     NOT NULL DEFAULT FALSE,
    status        VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    version       BIGINT         NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    CONSTRAINT chk_test_series_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT chk_test_series_price CHECK (price >= 0)
);
CREATE INDEX ix_test_series_exam_status ON test_series (exam_id, status);

CREATE TABLE test_series_batches (
    series_id UUID NOT NULL REFERENCES test_series (id) ON DELETE CASCADE,
    batch_id  UUID NOT NULL REFERENCES batches (id) ON DELETE CASCADE,
    PRIMARY KEY (series_id, batch_id)
);
CREATE INDEX ix_test_series_batches_batch ON test_series_batches (batch_id);

CREATE TABLE tests (
    id                  UUID PRIMARY KEY,
    series_id           UUID REFERENCES test_series (id),     -- NULL = standalone test
    exam_id             UUID          NOT NULL REFERENCES exams (id),
    title               VARCHAR(250)  NOT NULL,
    description         TEXT,
    instructions        TEXT,
    pattern             VARCHAR(30)   NOT NULL DEFAULT 'CUSTOM',
    duration_minutes    INT           NOT NULL,
    total_marks         NUMERIC(8, 2) NOT NULL DEFAULT 0,
    total_questions     INT           NOT NULL DEFAULT 0,
    start_at            TIMESTAMPTZ,              -- window open (NULL = any time)
    end_at              TIMESTAMPTZ,              -- window close (NULL = no end)
    status              VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    is_free             BOOLEAN       NOT NULL DEFAULT FALSE,
    shuffle_questions   BOOLEAN       NOT NULL DEFAULT FALSE,
    shuffle_options     BOOLEAN       NOT NULL DEFAULT FALSE,
    max_attempts        INT           NOT NULL DEFAULT 1,
    show_result_immediately BOOLEAN   NOT NULL DEFAULT TRUE,
    display_order       INT           NOT NULL DEFAULT 0,
    ranks_computed_at   TIMESTAMPTZ,
    version             BIGINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT chk_tests_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'LIVE', 'COMPLETED', 'ARCHIVED')),
    CONSTRAINT chk_tests_pattern CHECK (pattern IN ('JEE_MAIN', 'JEE_ADVANCED', 'NEET', 'CUSTOM')),
    CONSTRAINT chk_tests_duration CHECK (duration_minutes > 0),
    CONSTRAINT chk_tests_window CHECK (end_at IS NULL OR start_at IS NULL OR end_at > start_at),
    CONSTRAINT chk_tests_max_attempts CHECK (max_attempts >= 1)
);
CREATE INDEX ix_tests_series ON tests (series_id, display_order);
CREATE INDEX ix_tests_status_window ON tests (status, start_at, end_at);
-- Scheduler: find tests whose window has closed but ranks are not finalised yet.
CREATE INDEX ix_tests_rank_pending ON tests (end_at) WHERE ranks_computed_at IS NULL;

CREATE TABLE test_sections (
    id                       UUID PRIMARY KEY,
    test_id                  UUID          NOT NULL REFERENCES tests (id) ON DELETE CASCADE,
    subject_id               UUID REFERENCES subjects (id),
    name                     VARCHAR(150)  NOT NULL,
    instructions             TEXT,
    display_order            INT           NOT NULL DEFAULT 0,
    default_marks            NUMERIC(6, 2),     -- section-level default (overrides question)
    default_negative_marks   NUMERIC(6, 2),
    max_questions_to_attempt INT,              -- e.g. "attempt any 5 of 10"
    version                  BIGINT        NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by               UUID,
    updated_by               UUID
);
CREATE INDEX ix_test_sections_test ON test_sections (test_id, display_order);

CREATE TABLE test_questions (
    id               UUID PRIMARY KEY,
    test_id          UUID          NOT NULL REFERENCES tests (id) ON DELETE CASCADE,
    section_id       UUID          NOT NULL REFERENCES test_sections (id) ON DELETE CASCADE,
    question_id      UUID          NOT NULL REFERENCES questions (id),
    display_order    INT           NOT NULL DEFAULT 0,
    marks            NUMERIC(6, 2) NOT NULL,          -- effective marks (override resolved)
    negative_marks   NUMERIC(6, 2) NOT NULL DEFAULT 0,
    partial_marking  BOOLEAN       NOT NULL DEFAULT FALSE,
    version          BIGINT        NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_by       UUID,
    CONSTRAINT ux_test_questions_test_question UNIQUE (test_id, question_id),
    CONSTRAINT chk_test_questions_marks CHECK (marks >= 0 AND negative_marks >= 0)
);
CREATE INDEX ix_test_questions_section ON test_questions (section_id, display_order);
CREATE INDEX ix_test_questions_question ON test_questions (question_id);

-- -------------------------------------------------------------------------------------
-- Payments & enrollments
-- -------------------------------------------------------------------------------------
CREATE TABLE payments (
    id                  UUID PRIMARY KEY,
    user_id             UUID           NOT NULL REFERENCES users (id),
    series_id           UUID           NOT NULL REFERENCES test_series (id),
    amount              NUMERIC(10, 2) NOT NULL,
    currency            VARCHAR(3)     NOT NULL DEFAULT 'INR',
    provider            VARCHAR(20)    NOT NULL,
    status              VARCHAR(20)    NOT NULL DEFAULT 'CREATED',
    receipt             VARCHAR(64)    NOT NULL UNIQUE,
    provider_order_id   VARCHAR(100)   UNIQUE,
    provider_payment_id VARCHAR(100),
    provider_signature  VARCHAR(256),
    failure_reason      VARCHAR(500),
    paid_at             TIMESTAMPTZ,
    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT chk_payments_provider CHECK (provider IN ('RAZORPAY', 'MOCK')),
    CONSTRAINT chk_payments_status CHECK (status IN ('CREATED', 'PAID', 'FAILED', 'REFUNDED')),
    CONSTRAINT chk_payments_amount CHECK (amount >= 0)
);
CREATE INDEX ix_payments_user ON payments (user_id, created_at DESC);
CREATE INDEX ix_payments_status_paid_at ON payments (status, paid_at);

-- Webhook idempotency: each provider event is processed exactly once.
CREATE TABLE payment_events (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider          VARCHAR(20)  NOT NULL,
    provider_event_id VARCHAR(100) NOT NULL,
    event_type        VARCHAR(100) NOT NULL,
    payload           JSONB        NOT NULL,
    received_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_payment_events UNIQUE (provider, provider_event_id)
);

CREATE TABLE enrollments (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id),
    series_id   UUID        NOT NULL REFERENCES test_series (id),
    payment_id  UUID REFERENCES payments (id),
    source      VARCHAR(20) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    enrolled_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    CONSTRAINT ux_enrollments_user_series UNIQUE (user_id, series_id),
    CONSTRAINT chk_enrollments_source CHECK (source IN ('FREE', 'PAYMENT', 'ADMIN', 'BATCH')),
    CONSTRAINT chk_enrollments_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'CANCELLED'))
);
CREATE INDEX ix_enrollments_series ON enrollments (series_id);

-- -------------------------------------------------------------------------------------
-- Attempts (test-taking engine)
-- -------------------------------------------------------------------------------------
CREATE TABLE attempts (
    id                    UUID PRIMARY KEY,
    test_id               UUID        NOT NULL REFERENCES tests (id),
    user_id               UUID        NOT NULL REFERENCES users (id),
    attempt_no            INT         NOT NULL DEFAULT 1,
    status                VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    started_at            TIMESTAMPTZ NOT NULL,
    deadline_at           TIMESTAMPTZ NOT NULL,   -- server-authoritative end time
    submitted_at          TIMESTAMPTZ,
    submit_type           VARCHAR(10),
    evaluated_at          TIMESTAMPTZ,
    shuffle_seed          BIGINT      NOT NULL DEFAULT 0,
    tab_switch_count      INT         NOT NULL DEFAULT 0,
    fullscreen_exit_count INT         NOT NULL DEFAULT 0,
    client_ip             VARCHAR(64),
    user_agent            VARCHAR(500),
    version               BIGINT      NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by            UUID,
    updated_by            UUID,
    CONSTRAINT ux_attempts_test_user_no UNIQUE (test_id, user_id, attempt_no),
    CONSTRAINT chk_attempts_status CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'EVALUATED', 'CANCELLED')),
    CONSTRAINT chk_attempts_submit_type CHECK (submit_type IS NULL OR submit_type IN ('MANUAL', 'AUTO'))
);
CREATE INDEX ix_attempts_test_user ON attempts (test_id, user_id);
CREATE INDEX ix_attempts_user ON attempts (user_id, started_at DESC);
-- At most one running attempt per student per test.
CREATE UNIQUE INDEX ux_attempts_one_in_progress ON attempts (test_id, user_id) WHERE status = 'IN_PROGRESS';
-- Auto-submit scheduler scans expired running attempts.
CREATE INDEX ix_attempts_in_progress_deadline ON attempts (deadline_at) WHERE status = 'IN_PROGRESS';

-- Hash-partitioned; see strategy notes at the top of this file.
CREATE TABLE attempt_answers (
    attempt_id         UUID        NOT NULL,
    question_id        UUID        NOT NULL,
    section_id         UUID        NOT NULL,
    answer             JSONB,                  -- NULL = not answered
    state              VARCHAR(25) NOT NULL DEFAULT 'NOT_VISITED',
    time_spent_seconds INT         NOT NULL DEFAULT 0,
    visit_count        INT         NOT NULL DEFAULT 0,
    outcome            VARCHAR(15),            -- set by evaluation
    marks_awarded      NUMERIC(6, 2),
    last_updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (attempt_id, question_id),
    CONSTRAINT fk_attempt_answers_attempt FOREIGN KEY (attempt_id) REFERENCES attempts (id) ON DELETE CASCADE,
    CONSTRAINT chk_attempt_answers_state CHECK (state IN ('NOT_VISITED', 'NOT_ANSWERED', 'ANSWERED', 'MARKED_FOR_REVIEW', 'ANSWERED_AND_MARKED')),
    CONSTRAINT chk_attempt_answers_outcome CHECK (outcome IS NULL OR outcome IN ('CORRECT', 'INCORRECT', 'PARTIAL', 'UNATTEMPTED'))
) PARTITION BY HASH (attempt_id);

DO $$
BEGIN
    FOR i IN 0..15 LOOP
        EXECUTE format(
            'CREATE TABLE attempt_answers_p%s PARTITION OF attempt_answers FOR VALUES WITH (MODULUS 16, REMAINDER %s)',
            lpad(i::text, 2, '0'), i);
    END LOOP;
END $$;

CREATE TABLE attempt_events (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    attempt_id UUID        NOT NULL REFERENCES attempts (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    payload    JSONB,
    client_ts  TIMESTAMPTZ,
    server_ts  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_attempt_events_attempt ON attempt_events (attempt_id, server_ts);

-- -------------------------------------------------------------------------------------
-- Results
-- -------------------------------------------------------------------------------------
CREATE TABLE results (
    id                 UUID PRIMARY KEY,
    attempt_id         UUID          NOT NULL UNIQUE REFERENCES attempts (id),
    test_id            UUID          NOT NULL REFERENCES tests (id),
    user_id            UUID          NOT NULL REFERENCES users (id),
    score              NUMERIC(8, 2) NOT NULL,
    max_score          NUMERIC(8, 2) NOT NULL,
    correct_count      INT           NOT NULL DEFAULT 0,
    incorrect_count    INT           NOT NULL DEFAULT 0,
    partial_count      INT           NOT NULL DEFAULT 0,
    unattempted_count  INT           NOT NULL DEFAULT 0,
    accuracy           NUMERIC(5, 2) NOT NULL DEFAULT 0,   -- correct / attempted * 100
    time_taken_seconds INT           NOT NULL DEFAULT 0,
    is_ranked          BOOLEAN       NOT NULL DEFAULT TRUE, -- only first attempt is ranked
    rank               INT,                                 -- final (after window closes)
    percentile         NUMERIC(6, 3),
    rank_final         BOOLEAN       NOT NULL DEFAULT FALSE,
    section_scores     JSONB         NOT NULL DEFAULT '[]'::jsonb,
    topic_scores       JSONB         NOT NULL DEFAULT '[]'::jsonb,
    evaluated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version            BIGINT        NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID
);
-- Rank computation & leaderboards: ORDER BY score DESC within a test.
CREATE INDEX ix_results_test_score ON results (test_id, score DESC, time_taken_seconds ASC) WHERE is_ranked;
CREATE INDEX ix_results_test_user ON results (test_id, user_id);
CREATE INDEX ix_results_user ON results (user_id, evaluated_at DESC);

-- -------------------------------------------------------------------------------------
-- Notifications (outbox-style log of every message we send)
-- -------------------------------------------------------------------------------------
CREATE TABLE notifications (
    id            UUID PRIMARY KEY,
    user_id       UUID REFERENCES users (id) ON DELETE SET NULL,
    channel       VARCHAR(10)  NOT NULL,
    template      VARCHAR(50)  NOT NULL,
    recipient     VARCHAR(255) NOT NULL,
    subject       VARCHAR(255),
    body          TEXT         NOT NULL,
    status        VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    attempt_count INT          NOT NULL DEFAULT 0,
    last_error    VARCHAR(1000),
    sent_at       TIMESTAMPTZ,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    CONSTRAINT chk_notifications_channel CHECK (channel IN ('EMAIL', 'SMS', 'PUSH')),
    CONSTRAINT chk_notifications_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);
CREATE INDEX ix_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX ix_notifications_retry ON notifications (status, created_at) WHERE status <> 'SENT';
