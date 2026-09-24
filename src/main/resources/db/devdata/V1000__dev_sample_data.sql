-- =====================================================================================
-- V1000__dev_sample_data.sql — DEV / TEST ONLY sample data (not loaded in prod)
--   * demo teacher + student accounts
--   * JEE Main chapters/topics
--   * 10 sample questions (LaTeX content)
--   * a free, published test series with one published 10-question test
--
-- Demo credentials:
--   teacher@examprep.local / Teacher@123
--   student@examprep.local / Student@123
-- NOTE: JSON strings need "\\" for a literal backslash, so LaTeX \frac is written \\frac.
-- =====================================================================================

-- Demo users -------------------------------------------------------------------------
INSERT INTO users (id, email, phone, password_hash, full_name, status, email_verified, target_exam_code) VALUES
    ('00000000-0000-7000-8000-000000000002', 'teacher@examprep.local', '9000000002',
     crypt('Teacher@123', gen_salt('bf', 10)), 'Demo Teacher', 'ACTIVE', TRUE, NULL),
    ('00000000-0000-7000-8000-000000000003', 'student@examprep.local', '9000000003',
     crypt('Student@123', gen_salt('bf', 10)), 'Demo Student', 'ACTIVE', TRUE, 'JEE_MAIN');

INSERT INTO user_roles (user_id, role_id)
SELECT '00000000-0000-7000-8000-000000000002', id FROM roles WHERE name = 'TEACHER';
INSERT INTO user_roles (user_id, role_id)
SELECT '00000000-0000-7000-8000-000000000003', id FROM roles WHERE name = 'STUDENT';

-- Chapters (JEE Main) ----------------------------------------------------------------
INSERT INTO chapters (id, subject_id, name, display_order) VALUES
    ('12000000-0000-7000-8000-000000000001', '11000000-0000-7000-8000-000000000001', 'Kinematics',          1),
    ('12000000-0000-7000-8000-000000000002', '11000000-0000-7000-8000-000000000001', 'Laws of Motion',      2),
    ('12000000-0000-7000-8000-000000000003', '11000000-0000-7000-8000-000000000002', 'Atomic Structure',    1),
    ('12000000-0000-7000-8000-000000000004', '11000000-0000-7000-8000-000000000002', 'Chemical Bonding',    2),
    ('12000000-0000-7000-8000-000000000005', '11000000-0000-7000-8000-000000000003', 'Calculus',            1),
    ('12000000-0000-7000-8000-000000000006', '11000000-0000-7000-8000-000000000003', 'Algebra',             2);

-- Topics -----------------------------------------------------------------------------
INSERT INTO topics (id, chapter_id, name, display_order) VALUES
    ('13000000-0000-7000-8000-000000000001', '12000000-0000-7000-8000-000000000001', 'Motion in One Dimension', 1),
    ('13000000-0000-7000-8000-000000000002', '12000000-0000-7000-8000-000000000001', 'Projectile Motion',       2),
    ('13000000-0000-7000-8000-000000000003', '12000000-0000-7000-8000-000000000002', 'Friction',                1),
    ('13000000-0000-7000-8000-000000000004', '12000000-0000-7000-8000-000000000003', 'Bohr Model',              1),
    ('13000000-0000-7000-8000-000000000005', '12000000-0000-7000-8000-000000000004', 'Hybridization',           1),
    ('13000000-0000-7000-8000-000000000006', '12000000-0000-7000-8000-000000000004', 'VSEPR Theory',            2),
    ('13000000-0000-7000-8000-000000000007', '12000000-0000-7000-8000-000000000005', 'Limits',                  1),
    ('13000000-0000-7000-8000-000000000008', '12000000-0000-7000-8000-000000000005', 'Definite Integration',    2),
    ('13000000-0000-7000-8000-000000000009', '12000000-0000-7000-8000-000000000006', 'Quadratic Equations',     1);

-- Questions --------------------------------------------------------------------------
INSERT INTO questions (id, type, difficulty, language, exam_id, subject_id, chapter_id, topic_id,
                       content, correct_answer, default_marks, default_negative_marks, source, created_by)
VALUES
-- Physics 1: SCQ
('14000000-0000-7000-8000-000000000001', 'SINGLE_CORRECT', 'EASY', 'EN',
 '10000000-0000-7000-8000-000000000001', '11000000-0000-7000-8000-000000000001',
 '12000000-0000-7000-8000-000000000001', '13000000-0000-7000-8000-000000000001',
 '{"text": "A car starts from rest and accelerates uniformly at $2\\,\\text{m/s}^2$. The distance covered in the first $5\\,\\text{s}$ is:",
   "options": [{"id": "A", "text": "$10\\,\\text{m}$"}, {"id": "B", "text": "$25\\,\\text{m}$"},
               {"id": "C", "text": "$50\\,\\text{m}$"}, {"id": "D", "text": "$20\\,\\text{m}$"}],
   "solution": {"text": "$s = ut + \\frac{1}{2}at^2 = 0 + \\frac{1}{2}(2)(5^2) = 25\\,\\text{m}$"}}',
 '{"options": ["B"]}', 4, 1, 'Sample', '00000000-0000-7000-8000-000000000002'),

-- Physics 2: SCQ
('14000000-0000-7000-8000-000000000002', 'SINGLE_CORRECT', 'MEDIUM', 'EN',
 '10000000-0000-7000-8000-000000000001', '11000000-0000-7000-8000-000000000001',
 '12000000-0000-7000-8000-000000000001', '13000000-0000-7000-8000-000000000002',
 '{"text": "A projectile is launched at $45^\\circ$ with speed $20\\,\\text{m/s}$. Taking $g = 10\\,\\text{m/s}^2$, its horizontal range is:",
   "options": [{"id": "A", "text": "$20\\,\\text{m}$"}, {"id": "B", "text": "$30\\,\\text{m}$"},
               {"id": "C", "text": "$40\\,\\text{m}$"}, {"id": "D", "text": "$80\\,\\text{m}$"}],
   "solution": {"text": "$R = \\frac{u^2 \\sin 2\\theta}{g} = \\frac{400 \\cdot 1}{10} = 40\\,\\text{m}$"}}',
 '{"options": ["C"]}', 4, 1, 'Sample', '00000000-0000-7000-8000-000000000002'),

-- Physics 3: NUMERICAL
('14000000-0000-7000-8000-000000000003', 'NUMERICAL', 'MEDIUM', 'EN',
 '10000000-0000-7000-8000-000000000001', '11000000-0000-7000-8000-000000000001',
 '12000000-0000-7000-8000-000000000002', '13000000-0000-7000-8000-000000000003',
 '{"text": "A block of mass $5\\,\\text{kg}$ rests on a horizontal surface with coefficient of static friction $\\mu_s = 0.4$. The minimum horizontal force (in N) needed to start moving it is ______. (Take $g = 10\\,\\text{m/s}^2$)",
   "solution": {"text": "$F = \\mu_s m g = 0.4 \\times 5 \\times 10 = 20\\,\\text{N}$"}}',
 '{"value": 20, "tolerance": 0}', 4, 0, 'Sample', '00000000-0000-7000-8000-000000000002'),

-- Chemistry 1: SCQ
('14000000-0000-7000-8000-000000000004', 'SINGLE_CORRECT', 'EASY', 'EN',
 '10000000-0000-7000-8000-000000000001', '11000000-0000-7000-8000-000000000002',
 '12000000-0000-7000-8000-000000000003', '13000000-0000-7000-8000-000000000004',
 '{"text": "According to Bohr''s model, the radius of the $n^{th}$ orbit of a hydrogen atom is proportional to:",
   "options": [{"id": "A", "text": "$n$"}, {"id": "B", "text": "$n^2$"},
               {"id": "C", "text": "$\\frac{1}{n}$"}, {"id": "D", "text": "$\\frac{1}{n^2}$"}],
   "solution": {"text": "$r_n = 0.529\\,\\frac{n^2}{Z}\\,\\text{\\AA}$, so $r_n \\propto n^2$."}}',
 '{"options": ["B"]}', 4, 1, 'Sample', '00000000-0000-7000-8000-000000000002'),

-- Chemistry 2: SCQ
('14000000-0000-7000-8000-000000000005', 'SINGLE_CORRECT', 'MEDIUM', 'EN',
 '10000000-0000-7000-8000-000000000001', '11000000-0000-7000-8000-000000000002',
 '12000000-0000-7000-8000-000000000004', '13000000-0000-7000-8000-000000000005',
 '{"text": "The hybridization of the central atom in $\\mathrm{SF_6}$ is:",
   "options": [{"id": "A", "text": "$sp^3$"}, {"id": "B", "text": "$sp^3d$"},
               {"id": "C", "text": "$sp^3d^2$"}, {"id": "D", "text": "$dsp^2$"}],
   "solution": {"text": "S has 6 bond pairs and 0 lone pairs: steric number 6, so $sp^3d^2$ (octahedral)."}}',
 '{"options": ["C"]}', 4, 1, 'Sample', '00000000-0000-7000-8000-000000000002'),

-- Chemistry 3: MULTIPLE_CORRECT (partial marking)
('14000000-0000-7000-8000-000000000006', 'MULTIPLE_CORRECT', 'HARD', 'EN',
 '10000000-0000-7000-8000-000000000001', '11000000-0000-7000-8000-000000000002',
 '12000000-0000-7000-8000-000000000004', '13000000-0000-7000-8000-000000000006',
 '{"text": "Which of the following molecules have a non-zero dipole moment? (One or more correct)",
   "options": [{"id": "A", "text": "$\\mathrm{NH_3}$"}, {"id": "B", "text": "$\\mathrm{CO_2}$"},
               {"id": "C", "text": "$\\mathrm{H_2O}$"}, {"id": "D", "text": "$\\mathrm{BF_3}$"}],
   "solution": {"text": "$\\mathrm{NH_3}$ (pyramidal) and $\\mathrm{H_2O}$ (bent) are polar; $\\mathrm{CO_2}$ (linear) and $\\mathrm{BF_3}$ (trigonal planar) are non-polar."}}',
 '{"options": ["A", "C"]}', 4, 2, 'Sample', '00000000-0000-7000-8000-000000000002'),

-- Maths 1: SCQ
('14000000-0000-7000-8000-000000000007', 'SINGLE_CORRECT', 'EASY', 'EN',
 '10000000-0000-7000-8000-000000000001', '11000000-0000-7000-8000-000000000003',
 '12000000-0000-7000-8000-000000000005', '13000000-0000-7000-8000-000000000007',
 '{"text": "$\\displaystyle \\lim_{x \\to 0} \\frac{\\sin 3x}{x}$ equals:",
   "options": [{"id": "A", "text": "$0$"}, {"id": "B", "text": "$1$"},
               {"id": "C", "text": "$3$"}, {"id": "D", "text": "$\\frac{1}{3}$"}],
   "solution": {"text": "$\\lim_{x\\to0}\\frac{\\sin 3x}{3x}\\cdot 3 = 3$"}}',
 '{"options": ["C"]}', 4, 1, 'Sample', '00000000-0000-7000-8000-000000000002'),

-- Maths 2: SCQ
('14000000-0000-7000-8000-000000000008', 'SINGLE_CORRECT', 'MEDIUM', 'EN',
 '10000000-0000-7000-8000-000000000001', '11000000-0000-7000-8000-000000000003',
 '12000000-0000-7000-8000-000000000005', '13000000-0000-7000-8000-000000000008',
 '{"text": "$\\displaystyle \\int_0^{\\pi/2} \\sin^2 x \\, dx$ equals:",
   "options": [{"id": "A", "text": "$\\frac{\\pi}{2}$"}, {"id": "B", "text": "$\\frac{\\pi}{4}$"},
               {"id": "C", "text": "$\\pi$"}, {"id": "D", "text": "$1$"}],
   "solution": {"text": "By symmetry $\\int_0^{\\pi/2}\\sin^2x\\,dx = \\int_0^{\\pi/2}\\cos^2x\\,dx$, and their sum is $\\frac{\\pi}{2}$, so each is $\\frac{\\pi}{4}$."}}',
 '{"options": ["B"]}', 4, 1, 'Sample', '00000000-0000-7000-8000-000000000002'),

-- Maths 3: NUMERICAL (decimal with tolerance)
('14000000-0000-7000-8000-000000000009', 'NUMERICAL', 'MEDIUM', 'EN',
 '10000000-0000-7000-8000-000000000001', '11000000-0000-7000-8000-000000000003',
 '12000000-0000-7000-8000-000000000005', '13000000-0000-7000-8000-000000000008',
 '{"text": "The value of $\\displaystyle \\int_1^{e} \\frac{\\ln x}{x}\\,dx$ is ______ (correct to two decimal places).",
   "solution": {"text": "Put $t = \\ln x$: $\\int_0^1 t\\,dt = \\frac{1}{2} = 0.50$"}}',
 '{"value": 0.5, "tolerance": 0.01}', 4, 0, 'Sample', '00000000-0000-7000-8000-000000000002'),

-- Maths 4: SCQ
('14000000-0000-7000-8000-000000000010', 'SINGLE_CORRECT', 'HARD', 'EN',
 '10000000-0000-7000-8000-000000000001', '11000000-0000-7000-8000-000000000003',
 '12000000-0000-7000-8000-000000000006', '13000000-0000-7000-8000-000000000009',
 '{"text": "If $\\alpha$ and $\\beta$ are roots of $x^2 - 5x + 6 = 0$, then $\\alpha^2 + \\beta^2$ equals:",
   "options": [{"id": "A", "text": "$13$"}, {"id": "B", "text": "$25$"},
               {"id": "C", "text": "$12$"}, {"id": "D", "text": "$19$"}],
   "solution": {"text": "$\\alpha^2+\\beta^2 = (\\alpha+\\beta)^2 - 2\\alpha\\beta = 25 - 12 = 13$"}}',
 '{"options": ["A"]}', 4, 1, 'Sample', '00000000-0000-7000-8000-000000000002');

INSERT INTO question_tags (question_id, tag) VALUES
    ('14000000-0000-7000-8000-000000000001', 'kinematics'),
    ('14000000-0000-7000-8000-000000000002', 'projectile'),
    ('14000000-0000-7000-8000-000000000003', 'friction'),
    ('14000000-0000-7000-8000-000000000006', 'dipole-moment'),
    ('14000000-0000-7000-8000-000000000007', 'limits'),
    ('14000000-0000-7000-8000-000000000008', 'integration'),
    ('14000000-0000-7000-8000-000000000009', 'integration'),
    ('14000000-0000-7000-8000-000000000010', 'quadratic');

-- Test series + test -----------------------------------------------------------------
INSERT INTO test_series (id, exam_id, name, slug, description, price, is_free, validity_days, status, created_by)
VALUES ('15000000-0000-7000-8000-000000000001', '10000000-0000-7000-8000-000000000001',
        'JEE Main Free Starter Series', 'jee-main-free-starter-series',
        'Free starter mocks to get familiar with the NTA interface.', 0, TRUE, 365, 'PUBLISHED',
        '00000000-0000-7000-8000-000000000001');

INSERT INTO tests (id, series_id, exam_id, title, description, instructions, pattern, duration_minutes,
                   total_marks, total_questions, status, is_free, max_attempts, created_by)
VALUES ('16000000-0000-7000-8000-000000000001', '15000000-0000-7000-8000-000000000001',
        '10000000-0000-7000-8000-000000000001', 'Sample Mock Test 1 (JEE-style)',
        'A 10-question mini mock covering Physics, Chemistry and Mathematics.',
        E'1. The test is of 30 minutes duration.\n2. Single-correct: +4 for correct, -1 for incorrect.\n3. Multiple-correct: +4 if all correct; +1 per correct option if no wrong option is chosen; -2 otherwise.\n4. Numerical: +4 for correct, no negative marking.\n5. The timer is controlled by the server. The test is auto-submitted when time runs out.',
        'CUSTOM', 30, 40, 10, 'PUBLISHED', TRUE, 1, '00000000-0000-7000-8000-000000000001');

INSERT INTO test_sections (id, test_id, subject_id, name, display_order) VALUES
    ('17000000-0000-7000-8000-000000000001', '16000000-0000-7000-8000-000000000001', '11000000-0000-7000-8000-000000000001', 'Physics',     1),
    ('17000000-0000-7000-8000-000000000002', '16000000-0000-7000-8000-000000000001', '11000000-0000-7000-8000-000000000002', 'Chemistry',   2),
    ('17000000-0000-7000-8000-000000000003', '16000000-0000-7000-8000-000000000001', '11000000-0000-7000-8000-000000000003', 'Mathematics', 3);

INSERT INTO test_questions (id, test_id, section_id, question_id, display_order, marks, negative_marks, partial_marking) VALUES
    ('18000000-0000-7000-8000-000000000001', '16000000-0000-7000-8000-000000000001', '17000000-0000-7000-8000-000000000001', '14000000-0000-7000-8000-000000000001', 1, 4, 1, FALSE),
    ('18000000-0000-7000-8000-000000000002', '16000000-0000-7000-8000-000000000001', '17000000-0000-7000-8000-000000000001', '14000000-0000-7000-8000-000000000002', 2, 4, 1, FALSE),
    ('18000000-0000-7000-8000-000000000003', '16000000-0000-7000-8000-000000000001', '17000000-0000-7000-8000-000000000001', '14000000-0000-7000-8000-000000000003', 3, 4, 0, FALSE),
    ('18000000-0000-7000-8000-000000000004', '16000000-0000-7000-8000-000000000001', '17000000-0000-7000-8000-000000000002', '14000000-0000-7000-8000-000000000004', 1, 4, 1, FALSE),
    ('18000000-0000-7000-8000-000000000005', '16000000-0000-7000-8000-000000000001', '17000000-0000-7000-8000-000000000002', '14000000-0000-7000-8000-000000000005', 2, 4, 1, FALSE),
    ('18000000-0000-7000-8000-000000000006', '16000000-0000-7000-8000-000000000001', '17000000-0000-7000-8000-000000000002', '14000000-0000-7000-8000-000000000006', 3, 4, 2, TRUE),
    ('18000000-0000-7000-8000-000000000007', '16000000-0000-7000-8000-000000000001', '17000000-0000-7000-8000-000000000003', '14000000-0000-7000-8000-000000000007', 1, 4, 1, FALSE),
    ('18000000-0000-7000-8000-000000000008', '16000000-0000-7000-8000-000000000001', '17000000-0000-7000-8000-000000000003', '14000000-0000-7000-8000-000000000008', 2, 4, 1, FALSE),
    ('18000000-0000-7000-8000-000000000009', '16000000-0000-7000-8000-000000000001', '17000000-0000-7000-8000-000000000003', '14000000-0000-7000-8000-000000000009', 3, 4, 0, FALSE),
    ('18000000-0000-7000-8000-000000000010', '16000000-0000-7000-8000-000000000001', '17000000-0000-7000-8000-000000000003', '14000000-0000-7000-8000-000000000010', 4, 4, 1, FALSE);

-- Enroll the demo student in the free series.
INSERT INTO enrollments (id, user_id, series_id, source, status)
VALUES ('19000000-0000-7000-8000-000000000001', '00000000-0000-7000-8000-000000000003',
        '15000000-0000-7000-8000-000000000001', 'FREE', 'ACTIVE');
