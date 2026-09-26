-- =====================================================================================
-- V1004__dev_content_studio.sql — dev/demo data for Admin Portal A2 (Content Studio I)
-- =====================================================================================
-- Runs after V6 on an existing dev database, and after V1000–V1003 on a fresh one.
--   1. Seed questions of V1000–V1002 (inserted before the workflow existed) are published,
--      and get version 1 (V6's backfill, repeated here for fresh databases).
--   2. Three sample questions get a Hindi translation + metadata as published version 2;
--      the change is wording-only, so the published tests using them move to v2 as well.
--   3. Five new questions demonstrate every workflow state and the review queues:
--        201 IN_REVIEW, assigned to reviewer@          (numerical, answer range, bilingual)
--        202 IN_REVIEW, unassigned and OVERDUE         (PYQ metadata)
--        203 CHANGES_REQUESTED with reviewer comments  (multiple correct, proportional partial marks)
--        204 APPROVED, ready to publish                (bilingual)
--        205 DRAFT, Hindi only partly translated       (pinned "None of these" option)
--   4. Sample question 5 has an unpublished revision (v2 draft) while v1 stays live.
-- Users: teacher@ 00..02 (author), content@ 00..11 (content manager), reviewer@ 00..12.
-- =====================================================================================

-- 1. ---------------------------------------------------------------------------------
UPDATE questions SET status = 'PUBLISHED' WHERE current_version = 0;
SELECT content_backfill_versions();

-- 2. ---------------------------------------------------------------------------------
UPDATE questions SET
    translations = '{"HI": {"text": "एक कार विराम से $2\\,\\text{m/s}^2$ की दर से एकसमान त्वरित होती है। पहले $5\\,\\text{s}$ में तय की गई दूरी है:",
                            "options": {"A": "$10\\,\\text{m}$", "B": "$25\\,\\text{m}$", "C": "$50\\,\\text{m}$", "D": "$20\\,\\text{m}$"},
                            "solution": "$s = ut + \\frac{1}{2}at^2 = 0 + \\frac{1}{2}(2)(5^2) = 25\\,\\text{m}$"}}',
    sub_topic = 'Equations of motion', expected_time_sec = 90, cognitive_level = 'APPLY', source_type = 'ORIGINAL'
WHERE id = '14000000-0000-7000-8000-000000000001' AND current_version = 1;

UPDATE questions SET
    translations = '{"HI": {"text": "एक प्रक्षेप्य को $45^\\circ$ पर $20\\,\\text{m/s}$ की चाल से फेंका जाता है। $g = 10\\,\\text{m/s}^2$ लेने पर इसकी क्षैतिज परास है:",
                            "options": {"A": "$20\\,\\text{m}$", "B": "$30\\,\\text{m}$", "C": "$40\\,\\text{m}$", "D": "$80\\,\\text{m}$"},
                            "solution": "$R = \\frac{u^2 \\sin 2\\theta}{g} = \\frac{400 \\cdot 1}{10} = 40\\,\\text{m}$"}}',
    sub_topic = 'Range of a projectile', expected_time_sec = 90, cognitive_level = 'APPLY', source_type = 'ORIGINAL'
WHERE id = '14000000-0000-7000-8000-000000000002' AND current_version = 1;

UPDATE questions SET
    translations = '{"HI": {"text": "बोर मॉडल के अनुसार, हाइड्रोजन परमाणु की $n^{th}$ कक्षा की त्रिज्या किसके समानुपाती होती है?",
                            "options": {"A": "$n$", "B": "$n^2$", "C": "$\\frac{1}{n}$", "D": "$\\frac{1}{n^2}$"},
                            "solution": "$r_n = 0.529\\,\\frac{n^2}{Z}\\,\\text{\\AA}$, अतः $r_n \\propto n^2$।"}}',
    sub_topic = 'Radius of Bohr orbits', expected_time_sec = 45, cognitive_level = 'RECALL', source_type = 'ORIGINAL'
WHERE id = '14000000-0000-7000-8000-000000000004' AND current_version = 1;

INSERT INTO question_versions (question_id, version_no, snapshot, changed_fields, change_note, created_by, created_at,
                               published_at, published_by)
SELECT id, 2, question_snapshot_json(id),
       '["translations.HI", "subTopic", "expectedTimeSec", "cognitiveLevel", "sourceType"]'::jsonb,
       'Hindi translation and metadata', '00000000-0000-7000-8000-000000000011', now() - interval '1 day',
       now() - interval '1 day', '00000000-0000-7000-8000-000000000011'
FROM questions
WHERE id IN ('14000000-0000-7000-8000-000000000001', '14000000-0000-7000-8000-000000000002',
             '14000000-0000-7000-8000-000000000004')
  AND current_version = 1 AND translations <> '{}'::jsonb;

UPDATE questions SET current_version = 2, published_version = 2, published_at = now() - interval '1 day',
                     updated_by = '00000000-0000-7000-8000-000000000011'
WHERE id IN ('14000000-0000-7000-8000-000000000001', '14000000-0000-7000-8000-000000000002',
             '14000000-0000-7000-8000-000000000004')
  AND current_version = 1 AND translations <> '{}'::jsonb;

UPDATE test_questions SET question_version = 2
WHERE question_id IN ('14000000-0000-7000-8000-000000000001', '14000000-0000-7000-8000-000000000002',
                      '14000000-0000-7000-8000-000000000004')
  AND question_version = 1
  AND EXISTS (SELECT 1 FROM question_versions v WHERE v.question_id = test_questions.question_id AND v.version_no = 2);

INSERT INTO question_activity (id, question_id, version_no, actor_id, kind, body, meta, created_at)
SELECT gen_random_uuid(), v.question_id, 2, '00000000-0000-7000-8000-000000000011', k.kind, k.body, k.meta::jsonb,
       now() - interval '1 day' + k.offs
FROM question_versions v
CROSS JOIN (VALUES
    ('EDITED', 'Added the Hindi translation and metadata.', '{"from": "PUBLISHED"}', interval '0'),
    ('PUBLISHED', NULL, '{"version": 2, "previous": 1, "draftTestsUpdated": 0, "liveTestsUpdated": 1, "liveTestsKept": 0}',
     interval '1 minute')) AS k(kind, body, meta, offs)
WHERE v.version_no = 2 AND v.change_note = 'Hindi translation and metadata';

-- 3. ---------------------------------------------------------------------------------
INSERT INTO questions (id, type, difficulty, language, exam_id, subject_id, chapter_id, topic_id, sub_topic,
                       content, correct_answer, translations, default_marks, default_negative_marks, status,
                       source_type, source, year, pyq_shift, expected_time_sec, cognitive_level, current_version,
                       reviewer_id, submitted_by, review_requested_at, review_due_at,
                       created_by, updated_by, created_at, updated_at)
VALUES
-- 201: numerical with an answer range, bilingual, IN_REVIEW assigned to reviewer@
('14000000-0000-7000-8000-000000000201', 'NUMERICAL', 'MEDIUM', 'EN',
 '10000000-0000-7000-8000-000000000001', '11000000-0000-7000-8000-000000000001',
 '12000000-0000-7000-8000-000000000001', '13000000-0000-7000-8000-000000000001', 'Free fall',
 '{"text": "A ball is dropped from a height of $20\\,\\text{m}$. Taking $g = 9.8\\,\\text{m/s}^2$, the time (in s) it takes to reach the ground is ______.",
   "numericFormat": "DECIMAL",
   "solution": {"text": "$t = \\sqrt{2h/g} = \\sqrt{40/9.8} \\approx 2.02\\,\\text{s}$. Answers from 2.00 to 2.05 are accepted."}}',
 '{"min": 2.00, "max": 2.05}',
 '{"HI": {"text": "एक गेंद को $20\\,\\text{m}$ की ऊँचाई से गिराया जाता है। $g = 9.8\\,\\text{m/s}^2$ लेने पर, जमीन तक पहुँचने में लगा समय (s में) ______ है।",
          "solution": "$t = \\sqrt{2h/g} = \\sqrt{40/9.8} \\approx 2.02\\,\\text{s}$। 2.00 से 2.05 तक के उत्तर मान्य हैं।"}}',
 4, 0, 'IN_REVIEW', 'ORIGINAL', NULL, NULL, NULL, 120, 'APPLY', 1,
 '00000000-0000-7000-8000-000000000012', '00000000-0000-7000-8000-000000000002',
 now() - interval '2 hours', now() + interval '46 hours',
 '00000000-0000-7000-8000-000000000002', '00000000-0000-7000-8000-000000000002',
 now() - interval '3 hours', now() - interval '2 hours'),

-- 202: PYQ, IN_REVIEW, nobody assigned, past its SLA
('14000000-0000-7000-8000-000000000202', 'SINGLE_CORRECT', 'EASY', 'EN',
 '10000000-0000-7000-8000-000000000001', '11000000-0000-7000-8000-000000000002',
 '12000000-0000-7000-8000-000000000004', '13000000-0000-7000-8000-000000000005', 'VSEPR shapes',
 '{"text": "According to VSEPR theory, the shape of $\\mathrm{XeF_4}$ is:",
   "options": [{"id": "A", "text": "Tetrahedral"}, {"id": "B", "text": "Square planar"},
               {"id": "C", "text": "See-saw"}, {"id": "D", "text": "Octahedral"}],
   "solution": {"text": "Xe has 4 bond pairs and 2 lone pairs: octahedral electron geometry, square planar shape."}}',
 '{"options": ["B"]}', '{}',
 4, 1, 'IN_REVIEW', 'PYQ', 'JEE Main', 2023, '29 Jan 2023, Shift 2', 60, 'RECALL', 1,
 NULL, '00000000-0000-7000-8000-000000000002', now() - interval '3 days', now() - interval '1 day',
 '00000000-0000-7000-8000-000000000002', '00000000-0000-7000-8000-000000000002',
 now() - interval '3 days', now() - interval '3 days'),

-- 203: multiple correct with proportional partial marks, sent back for changes
('14000000-0000-7000-8000-000000000203', 'MULTIPLE_CORRECT', 'MEDIUM', 'EN',
 '10000000-0000-7000-8000-000000000001', '11000000-0000-7000-8000-000000000002',
 '12000000-0000-7000-8000-000000000004', '13000000-0000-7000-8000-000000000006', 'Polarity of molecules',
 '{"text": "Which of the following molecules have a zero dipole moment? (One or more correct)",
   "options": [{"id": "A", "text": "$\\mathrm{CH_4}$"}, {"id": "B", "text": "$\\mathrm{CHCl_3}$"},
               {"id": "C", "text": "$\\mathrm{CCl_4}$"}, {"id": "D", "text": "$\\mathrm{NH_3}$"}],
   "solution": {"text": "Symmetric tetrahedral molecules have zero dipole moment."}}',
 '{"options": ["A"], "partial": "PROPORTIONAL"}', '{}',
 4, 2, 'CHANGES_REQUESTED', 'ORIGINAL', NULL, NULL, NULL, 90, 'ANALYSE', 1,
 '00000000-0000-7000-8000-000000000012', '00000000-0000-7000-8000-000000000002',
 now() - interval '1 day', NULL,
 '00000000-0000-7000-8000-000000000002', '00000000-0000-7000-8000-000000000012',
 now() - interval '26 hours', now() - interval '20 hours'),

-- 204: approved by reviewer@, waiting for a publisher; bilingual
('14000000-0000-7000-8000-000000000204', 'SINGLE_CORRECT', 'MEDIUM', 'EN',
 '10000000-0000-7000-8000-000000000001', '11000000-0000-7000-8000-000000000003',
 '12000000-0000-7000-8000-000000000005', '13000000-0000-7000-8000-000000000007', 'Standard limits',
 '{"text": "$\\displaystyle \\lim_{x \\to 0} \\frac{1 - \\cos x}{x^2}$ equals:",
   "options": [{"id": "A", "text": "$0$"}, {"id": "B", "text": "$\\frac{1}{2}$"},
               {"id": "C", "text": "$1$"}, {"id": "D", "text": "$2$"}],
   "solution": {"text": "$1 - \\cos x = 2\\sin^2\\frac{x}{2}$, so the limit is $2 \\cdot \\frac{1}{4} = \\frac{1}{2}$."}}',
 '{"options": ["B"]}',
 '{"HI": {"text": "$\\displaystyle \\lim_{x \\to 0} \\frac{1 - \\cos x}{x^2}$ का मान है:",
          "options": {"A": "$0$", "B": "$\\frac{1}{2}$", "C": "$1$", "D": "$2$"},
          "solution": "$1 - \\cos x = 2\\sin^2\\frac{x}{2}$, अतः सीमा $2 \\cdot \\frac{1}{4} = \\frac{1}{2}$ है।"}}',
 4, 1, 'APPROVED', 'COACHING', 'Kota practice sheet', NULL, NULL, 75, 'APPLY', 1,
 '00000000-0000-7000-8000-000000000012', '00000000-0000-7000-8000-000000000002',
 now() - interval '2 days', NULL,
 '00000000-0000-7000-8000-000000000002', '00000000-0000-7000-8000-000000000012',
 now() - interval '2 days', now() - interval '1 day'),

-- 205: draft with a pinned "None of these" option; Hindi only partly translated
('14000000-0000-7000-8000-000000000205', 'SINGLE_CORRECT', 'EASY', 'EN',
 '10000000-0000-7000-8000-000000000001', '11000000-0000-7000-8000-000000000001',
 '12000000-0000-7000-8000-000000000001', '13000000-0000-7000-8000-000000000002', 'Maximum height',
 '{"text": "A projectile is launched at $30^\\circ$ with speed $20\\,\\text{m/s}$. Taking $g = 10\\,\\text{m/s}^2$, the maximum height it reaches is:",
   "options": [{"id": "A", "text": "$5\\,\\text{m}$"}, {"id": "B", "text": "$10\\,\\text{m}$"},
               {"id": "C", "text": "$20\\,\\text{m}$"}, {"id": "D", "text": "None of these", "pinned": true}],
   "solution": {"text": "$H = \\frac{u^2 \\sin^2\\theta}{2g} = \\frac{400 \\cdot \\frac{1}{4}}{20} = 5\\,\\text{m}$"}}',
 '{"options": ["A"]}',
 '{"HI": {"text": "एक प्रक्षेप्य को $30^\\circ$ पर $20\\,\\text{m/s}$ की चाल से फेंका जाता है। $g = 10\\,\\text{m/s}^2$ लेने पर, इसकी अधिकतम ऊँचाई है:"}}',
 4, 1, 'DRAFT', 'ORIGINAL', NULL, NULL, NULL, 60, 'APPLY', 1,
 NULL, NULL, NULL, NULL,
 '00000000-0000-7000-8000-000000000002', '00000000-0000-7000-8000-000000000002',
 now() - interval '5 hours', now() - interval '5 hours');

INSERT INTO question_tags (question_id, tag) VALUES
    ('14000000-0000-7000-8000-000000000201', 'free-fall'),
    ('14000000-0000-7000-8000-000000000202', 'pyq'),
    ('14000000-0000-7000-8000-000000000202', 'vsepr'),
    ('14000000-0000-7000-8000-000000000203', 'dipole-moment'),
    ('14000000-0000-7000-8000-000000000204', 'limits'),
    ('14000000-0000-7000-8000-000000000205', 'projectile');

INSERT INTO question_concepts (question_id, concept) VALUES
    ('14000000-0000-7000-8000-000000000201', 'equations of motion'),
    ('14000000-0000-7000-8000-000000000202', 'vsepr theory'),
    ('14000000-0000-7000-8000-000000000203', 'molecular symmetry'),
    ('14000000-0000-7000-8000-000000000204', 'standard limits'),
    ('14000000-0000-7000-8000-000000000205', 'projectile motion');

INSERT INTO question_versions (question_id, version_no, snapshot, change_note, created_by, created_at)
SELECT id, 1, question_snapshot_json(id), 'Created', created_by, created_at
FROM questions
WHERE id IN ('14000000-0000-7000-8000-000000000201', '14000000-0000-7000-8000-000000000202',
             '14000000-0000-7000-8000-000000000203', '14000000-0000-7000-8000-000000000204',
             '14000000-0000-7000-8000-000000000205');

INSERT INTO question_activity (id, question_id, version_no, actor_id, kind, body, field, meta, created_at) VALUES
    -- 201
    (gen_random_uuid(), '14000000-0000-7000-8000-000000000201', 1, '00000000-0000-7000-8000-000000000002', 'CREATED', NULL, NULL, NULL, now() - interval '3 hours'),
    (gen_random_uuid(), '14000000-0000-7000-8000-000000000201', 1, '00000000-0000-7000-8000-000000000002', 'SUBMITTED',
     'Range answer, please check the limits.', NULL, '{"assignee": "00000000-0000-7000-8000-000000000012"}', now() - interval '2 hours'),
    -- 202
    (gen_random_uuid(), '14000000-0000-7000-8000-000000000202', 1, '00000000-0000-7000-8000-000000000002', 'CREATED', NULL, NULL, NULL, now() - interval '3 days'),
    (gen_random_uuid(), '14000000-0000-7000-8000-000000000202', 1, '00000000-0000-7000-8000-000000000002', 'SUBMITTED', NULL, NULL, NULL, now() - interval '3 days' + interval '5 minutes'),
    -- 203
    (gen_random_uuid(), '14000000-0000-7000-8000-000000000203', 1, '00000000-0000-7000-8000-000000000002', 'CREATED', NULL, NULL, NULL, now() - interval '26 hours'),
    (gen_random_uuid(), '14000000-0000-7000-8000-000000000203', 1, '00000000-0000-7000-8000-000000000002', 'SUBMITTED', NULL, NULL,
     '{"assignee": "00000000-0000-7000-8000-000000000012"}', now() - interval '1 day'),
    (gen_random_uuid(), '14000000-0000-7000-8000-000000000203', 1, '00000000-0000-7000-8000-000000000012', 'COMMENT',
     '$\mathrm{CCl_4}$ is also symmetric (tetrahedral), so C is correct too.', 'answerKey', NULL, now() - interval '20 hours' - interval '2 minutes'),
    (gen_random_uuid(), '14000000-0000-7000-8000-000000000203', 1, '00000000-0000-7000-8000-000000000012', 'COMMENT',
     'Please add a line on why $\mathrm{CHCl_3}$ is polar.', 'solution', NULL, now() - interval '20 hours' - interval '1 minute'),
    (gen_random_uuid(), '14000000-0000-7000-8000-000000000203', 1, '00000000-0000-7000-8000-000000000012', 'CHANGES_REQUESTED',
     'The answer key is incomplete (see my comments).', NULL, NULL, now() - interval '20 hours'),
    -- 204
    (gen_random_uuid(), '14000000-0000-7000-8000-000000000204', 1, '00000000-0000-7000-8000-000000000002', 'CREATED', NULL, NULL, NULL, now() - interval '2 days'),
    (gen_random_uuid(), '14000000-0000-7000-8000-000000000204', 1, '00000000-0000-7000-8000-000000000002', 'SUBMITTED', NULL, NULL,
     '{"assignee": "00000000-0000-7000-8000-000000000012"}', now() - interval '2 days' + interval '5 minutes'),
    (gen_random_uuid(), '14000000-0000-7000-8000-000000000204', 1, '00000000-0000-7000-8000-000000000012', 'APPROVED',
     'Correct, and the Hindi version reads well.', NULL, NULL, now() - interval '1 day'),
    -- 205
    (gen_random_uuid(), '14000000-0000-7000-8000-000000000205', 1, '00000000-0000-7000-8000-000000000002', 'CREATED', NULL, NULL, NULL, now() - interval '5 hours');

-- 4. ---------------------------------------------------------------------------------
UPDATE questions SET
    content = jsonb_set(content, '{solution,text}',
        '"S has 6 bond pairs and 0 lone pairs: steric number 6, so $sp^3d^2$ (octahedral). The same holds for $\\mathrm{SF_6}$ and $[\\mathrm{PF_6}]^-$."'),
    source_type = 'PYQ', source = 'JEE Main', year = 2019,
    status = 'DRAFT', current_version = 2, updated_by = '00000000-0000-7000-8000-000000000011',
    updated_at = now() - interval '30 minutes'
WHERE id = '14000000-0000-7000-8000-000000000005' AND current_version = 1;

INSERT INTO question_versions (question_id, version_no, snapshot, changed_fields, change_note, created_by, created_at)
SELECT id, 2, question_snapshot_json(id), '["content.solution", "sourceType", "source", "year"]'::jsonb,
       'Better solution; marked as PYQ', '00000000-0000-7000-8000-000000000011', now() - interval '30 minutes'
FROM questions
WHERE id = '14000000-0000-7000-8000-000000000005' AND current_version = 2
  AND NOT EXISTS (SELECT 1 FROM question_versions v WHERE v.question_id = questions.id AND v.version_no = 2);

INSERT INTO question_activity (id, question_id, version_no, actor_id, kind, body, meta, created_at)
SELECT gen_random_uuid(), '14000000-0000-7000-8000-000000000005', 2, '00000000-0000-7000-8000-000000000011', 'EDITED',
       'New revision v2 started; v1 stays live until it is published.', '{"from": "PUBLISHED"}'::jsonb,
       now() - interval '30 minutes'
WHERE EXISTS (SELECT 1 FROM question_versions v WHERE v.question_id = '14000000-0000-7000-8000-000000000005'
                                                  AND v.version_no = 2);
