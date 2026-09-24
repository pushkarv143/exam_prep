-- =====================================================================================
-- V2__reference_data.sql — reference data required in EVERY environment
--   * roles
--   * bootstrap admin (credentials injected via Flyway placeholders; see application.yml
--     `spring.flyway.placeholders`. In prod set ADMIN_EMAIL / ADMIN_INITIAL_PASSWORD and
--     rotate the password after first login.)
--   * exams + subjects
-- Demo users, chapters/topics, questions and a sample test live in db/devdata and are
-- loaded only by the dev/test profiles.
-- =====================================================================================

INSERT INTO roles (name, description) VALUES
    ('STUDENT', 'Takes tests, views results and analytics'),
    ('TEACHER', 'Manages question bank and tests'),
    ('ADMIN',   'Full platform administration');

-- pgcrypto's crypt(.., gen_salt('bf')) produces a $2a$ bcrypt hash compatible with
-- Spring Security's BCryptPasswordEncoder.
INSERT INTO users (id, email, password_hash, full_name, status, email_verified)
VALUES ('00000000-0000-7000-8000-000000000001', '${adminEmail}',
        crypt('${adminPassword}', gen_salt('bf', 10)), 'Platform Admin', 'ACTIVE', TRUE);

INSERT INTO user_roles (user_id, role_id)
SELECT '00000000-0000-7000-8000-000000000001', id FROM roles WHERE name = 'ADMIN';

-- Exams ------------------------------------------------------------------------------
INSERT INTO exams (id, code, name, description, display_order) VALUES
    ('10000000-0000-7000-8000-000000000001', 'JEE_MAIN', 'JEE Main',
     'Joint Entrance Examination (Main) — NTA. 90 questions, 300 marks, 3 hours.', 1),
    ('10000000-0000-7000-8000-000000000002', 'JEE_ADVANCED', 'JEE Advanced',
     'Joint Entrance Examination (Advanced) for IITs. Two papers of 3 hours each.', 2),
    ('10000000-0000-7000-8000-000000000003', 'NEET', 'NEET UG',
     'National Eligibility cum Entrance Test (UG). 180 questions, 720 marks, 200 minutes.', 3);

-- Subjects ---------------------------------------------------------------------------
INSERT INTO subjects (id, exam_id, code, name, display_order) VALUES
    -- JEE Main
    ('11000000-0000-7000-8000-000000000001', '10000000-0000-7000-8000-000000000001', 'PHY',  'Physics',     1),
    ('11000000-0000-7000-8000-000000000002', '10000000-0000-7000-8000-000000000001', 'CHEM', 'Chemistry',   2),
    ('11000000-0000-7000-8000-000000000003', '10000000-0000-7000-8000-000000000001', 'MATH', 'Mathematics', 3),
    -- JEE Advanced
    ('11000000-0000-7000-8000-000000000011', '10000000-0000-7000-8000-000000000002', 'PHY',  'Physics',     1),
    ('11000000-0000-7000-8000-000000000012', '10000000-0000-7000-8000-000000000002', 'CHEM', 'Chemistry',   2),
    ('11000000-0000-7000-8000-000000000013', '10000000-0000-7000-8000-000000000002', 'MATH', 'Mathematics', 3),
    -- NEET
    ('11000000-0000-7000-8000-000000000021', '10000000-0000-7000-8000-000000000003', 'PHY',  'Physics',     1),
    ('11000000-0000-7000-8000-000000000022', '10000000-0000-7000-8000-000000000003', 'CHEM', 'Chemistry',   2),
    ('11000000-0000-7000-8000-000000000023', '10000000-0000-7000-8000-000000000003', 'BOT',  'Botany',      3),
    ('11000000-0000-7000-8000-000000000024', '10000000-0000-7000-8000-000000000003', 'ZOO',  'Zoology',     4);
