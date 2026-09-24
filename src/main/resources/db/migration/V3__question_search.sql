-- =====================================================================================
-- V3__question_search.sql — fast substring search over question text
-- =====================================================================================
-- The question bank search box runs  lower(content->>'text') LIKE '%term%'.
-- A B-tree cannot serve a leading wildcard, but a trigram GIN index can.
-- The indexed expression MUST match the one rendered by the Hibernate function
-- `question_text_lower` (SqlFunctionsContributor) character for character.
-- pg_trgm is a "trusted" extension (PG13+), so the database owner can create it.
-- =====================================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX ix_questions_text_trgm
    ON questions USING gin (lower(jsonb_extract_path_text(content, 'text')) gin_trgm_ops);
