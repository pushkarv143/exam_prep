-- =====================================================================================
-- V4__test_builder.sql — test-builder metadata + payment lookup index
-- =====================================================================================
-- Sections created from an exam pattern remember what they expect, so that the
-- auto-generator can fill them and publish validation can check them:
--   question_type : e.g. SINGLE_CORRECT for "Physics - Section A", NUMERICAL for "Section B"
--   target_count  : e.g. 20 / 10
-- Both are NULL for free-form (CUSTOM) sections.
-- =====================================================================================

ALTER TABLE test_sections
    ADD COLUMN question_type VARCHAR(30),
    ADD COLUMN target_count  INT,
    ADD CONSTRAINT chk_test_sections_question_type CHECK (question_type IS NULL OR question_type IN
        ('SINGLE_CORRECT', 'MULTIPLE_CORRECT', 'NUMERICAL', 'MATCH')),
    ADD CONSTRAINT chk_test_sections_target_count CHECK (target_count IS NULL OR target_count > 0);

-- "Does this user already have an open order for this series?" (order reuse / idempotency)
CREATE INDEX ix_payments_user_series_status ON payments (user_id, series_id, status, created_at DESC);
