-- =====================================================================================
-- V6__content_studio.sql — Admin Portal 2.0, phase A2: Content Studio I
-- =====================================================================================
--   * review workflow: DRAFT -> IN_REVIEW -> CHANGES_REQUESTED -> APPROVED -> PUBLISHED -> ARCHIVED
--     (the old ACTIVE state becomes PUBLISHED)
--   * richer metadata: sub-topic, expected time, source type / PYQ shift, cognitive level,
--     concept tags, Hindi/English translations
--   * immutable question versions; tests pin the version they were built with
--   * review queue fields (assignee, SLA due time) and an activity/comment log
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 1. Workflow states
-- -------------------------------------------------------------------------------------
ALTER TABLE questions DROP CONSTRAINT chk_questions_status;
UPDATE questions SET status = 'PUBLISHED' WHERE status = 'ACTIVE';
ALTER TABLE questions ALTER COLUMN status SET DEFAULT 'DRAFT';
ALTER TABLE questions ADD CONSTRAINT chk_questions_status
    CHECK (status IN ('DRAFT', 'IN_REVIEW', 'CHANGES_REQUESTED', 'APPROVED', 'PUBLISHED', 'ARCHIVED'));

-- -------------------------------------------------------------------------------------
-- 2. Metadata, translations, versions and review fields
-- -------------------------------------------------------------------------------------
ALTER TABLE questions
    ADD COLUMN sub_topic           VARCHAR(120),
    ADD COLUMN expected_time_sec   SMALLINT,
    ADD COLUMN source_type         VARCHAR(12),
    ADD COLUMN pyq_shift           VARCHAR(60),
    ADD COLUMN cognitive_level     VARCHAR(10),
    -- {"HI": {"text": ..., "options": {"A": ...}, "solution": ...}}: text only; ids, images
    -- and the answer key are shared with the primary language
    ADD COLUMN translations        JSONB       NOT NULL DEFAULT '{}'::jsonb,
    -- latest saved version (the working copy) and the version new tests use
    ADD COLUMN current_version     INT         NOT NULL DEFAULT 0,
    ADD COLUMN published_version   INT,
    ADD COLUMN published_at        TIMESTAMPTZ,
    ADD COLUMN reviewer_id         UUID REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN review_requested_at TIMESTAMPTZ,
    ADD COLUMN review_due_at       TIMESTAMPTZ,
    ADD COLUMN review_overdue_sent BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN submitted_by        UUID REFERENCES users (id) ON DELETE SET NULL,
    ADD CONSTRAINT chk_questions_expected_time CHECK (expected_time_sec IS NULL OR expected_time_sec BETWEEN 5 AND 3600),
    ADD CONSTRAINT chk_questions_source_type CHECK (source_type IS NULL OR source_type IN ('PYQ', 'COACHING', 'BOOK', 'ORIGINAL')),
    ADD CONSTRAINT chk_questions_cognitive CHECK (cognitive_level IS NULL OR cognitive_level IN ('RECALL', 'APPLY', 'ANALYSE')),
    ADD CONSTRAINT chk_questions_published_version CHECK (published_version IS NULL OR published_version BETWEEN 1 AND current_version);

-- The generator and the test picker only use questions that have a published version.
DROP INDEX ix_questions_topic_difficulty;
CREATE INDEX ix_questions_topic_difficulty ON questions (topic_id, difficulty)
    WHERE published_version IS NOT NULL AND status <> 'ARCHIVED';
-- Review queues: "assigned to me", "unassigned", "overdue".
CREATE INDEX ix_questions_review_queue ON questions (review_due_at) WHERE status = 'IN_REVIEW';
CREATE INDEX ix_questions_reviewer ON questions (reviewer_id) WHERE status = 'IN_REVIEW';
-- Sub-topic suggestions within a topic.
CREATE INDEX ix_questions_sub_topic ON questions (topic_id, sub_topic) WHERE sub_topic IS NOT NULL;

-- Concept tags. A9 (concept map) links these names to concept nodes.
CREATE TABLE question_concepts (
    question_id UUID        NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    concept     VARCHAR(80) NOT NULL,
    PRIMARY KEY (question_id, concept)
);
CREATE INDEX ix_question_concepts_concept ON question_concepts (concept);

-- -------------------------------------------------------------------------------------
-- 3. Versions: one immutable snapshot per save
-- -------------------------------------------------------------------------------------
CREATE TABLE question_versions (
    question_id    UUID         NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    version_no     INT          NOT NULL,
    -- full question as saved (type, metadata, content, answer key, translations, marks, tags)
    snapshot       JSONB        NOT NULL,
    -- top-level paths that changed against the previous version, e.g. ["content.text", "answerKey"]
    changed_fields JSONB        NOT NULL DEFAULT '[]'::jsonb,
    change_note    VARCHAR(500),
    restored_from  INT,
    created_by     UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    published_by   UUID REFERENCES users (id) ON DELETE SET NULL,
    PRIMARY KEY (question_id, version_no),
    CONSTRAINT chk_question_versions_no CHECK (version_no >= 1)
);

-- A saved version never changes; only its "published" stamp may be set later.
CREATE FUNCTION question_versions_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.snapshot IS DISTINCT FROM OLD.snapshot OR NEW.version_no <> OLD.version_no
            OR NEW.question_id <> OLD.question_id OR NEW.created_at <> OLD.created_at THEN
        RAISE EXCEPTION 'question_versions rows are immutable (question %, version %)', OLD.question_id, OLD.version_no;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_question_versions_immutable BEFORE UPDATE ON question_versions
    FOR EACH ROW EXECUTE FUNCTION question_versions_immutable();

-- Tests pin the version they were built with. DEFAULT 1 only serves rows inserted by old
-- seed scripts; the application always sets the pin explicitly.
ALTER TABLE test_questions
    ADD COLUMN question_version INT NOT NULL DEFAULT 1,
    ADD COLUMN passage_version  INT;

-- -------------------------------------------------------------------------------------
-- 4. Activity: workflow events and reviewer comments per question
-- -------------------------------------------------------------------------------------
CREATE TABLE question_activity (
    id          UUID PRIMARY KEY,
    question_id UUID        NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    version_no  INT,
    actor_id    UUID REFERENCES users (id) ON DELETE SET NULL,
    kind        VARCHAR(24) NOT NULL,
    body        TEXT,
    -- what a comment is about, e.g. "content.text", "options.B", "solution", "translation.HI"
    field       VARCHAR(80),
    resolved_at TIMESTAMPTZ,
    resolved_by UUID REFERENCES users (id) ON DELETE SET NULL,
    meta        JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_question_activity_kind CHECK (kind IN ('CREATED', 'EDITED', 'COMMENT', 'SUBMITTED', 'ASSIGNED',
        'CHANGES_REQUESTED', 'APPROVED', 'PUBLISHED', 'ARCHIVED', 'RESTORED', 'ROLLED_BACK', 'OVERDUE')),
    CONSTRAINT chk_question_activity_body CHECK (body IS NULL OR length(body) <= 5000)
);
CREATE INDEX ix_question_activity_question ON question_activity (question_id, created_at);
CREATE INDEX ix_question_activity_open ON question_activity (question_id)
    WHERE kind IN ('COMMENT', 'CHANGES_REQUESTED') AND resolved_at IS NULL;

-- -------------------------------------------------------------------------------------
-- 5. Settings (editable in the review queue page)
-- -------------------------------------------------------------------------------------
INSERT INTO system_settings (key, value) VALUES
    ('content.review.required', 'true'::jsonb),     -- false: publishers may publish drafts directly
    ('content.review.sla-hours', '48'::jsonb),      -- reviewer SLA from submission
    ('content.review.auto-assign', 'true'::jsonb)   -- pick the least-loaded eligible reviewer
ON CONFLICT (key) DO NOTHING;

-- -------------------------------------------------------------------------------------
-- 6. Backfill: version 1 for every question that has none, and passage pins.
--    Idempotent; dev seed data (V1000+) runs after this migration on a fresh database,
--    so the devdata script V1004 calls it again.
-- -------------------------------------------------------------------------------------
-- The snapshot of a question's working copy, in the format of QuestionSnapshot (Java).
CREATE FUNCTION question_snapshot_json(p_id UUID) RETURNS JSONB LANGUAGE sql STABLE AS $$
    SELECT jsonb_build_object(
               'type', q.type, 'difficulty', q.difficulty, 'language', q.language,
               'examId', q.exam_id, 'subjectId', q.subject_id, 'chapterId', q.chapter_id, 'topicId', q.topic_id,
               'subTopic', q.sub_topic, 'parentId', q.parent_id,
               'content', q.content, 'answerKey', q.correct_answer, 'translations', q.translations,
               'marks', q.default_marks, 'negativeMarks', q.default_negative_marks,
               'sourceType', q.source_type, 'source', q.source, 'year', q.year, 'pyqShift', q.pyq_shift,
               'expectedTimeSec', q.expected_time_sec, 'cognitiveLevel', q.cognitive_level,
               'tags', COALESCE((SELECT jsonb_agg(t.tag ORDER BY t.tag) FROM question_tags t WHERE t.question_id = q.id),
                                '[]'::jsonb),
               'concepts', COALESCE((SELECT jsonb_agg(c.concept ORDER BY c.concept) FROM question_concepts c
                                     WHERE c.question_id = q.id), '[]'::jsonb))
    FROM questions q
    WHERE q.id = p_id
$$;

CREATE FUNCTION content_backfill_versions() RETURNS INT LANGUAGE plpgsql AS $$
DECLARE
    n INT;
BEGIN
    INSERT INTO question_versions (question_id, version_no, snapshot, change_note, created_by, created_at,
                                   published_at, published_by)
    SELECT q.id, 1, question_snapshot_json(q.id), 'Initial version', q.created_by, q.created_at,
           CASE WHEN q.status IN ('PUBLISHED', 'ARCHIVED') THEN q.created_at END,
           CASE WHEN q.status IN ('PUBLISHED', 'ARCHIVED') THEN q.created_by END
    FROM questions q
    WHERE q.current_version = 0;
    GET DIAGNOSTICS n = ROW_COUNT;

    UPDATE questions
    SET current_version   = 1,
        published_version = CASE WHEN status IN ('PUBLISHED', 'ARCHIVED') THEN 1 END,
        published_at      = CASE WHEN status IN ('PUBLISHED', 'ARCHIVED') THEN created_at END
    WHERE current_version = 0;

    UPDATE test_questions tq
    SET passage_version = COALESCE(p.published_version, p.current_version)
    FROM questions c
    JOIN questions p ON p.id = c.parent_id
    WHERE tq.question_id = c.id AND tq.passage_version IS NULL;

    RETURN n;
END $$;

SELECT content_backfill_versions();
