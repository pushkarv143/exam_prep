-- =====================================================================================
-- V1001__dev_paid_series.sql — DEV / TEST ONLY data for enrollment and payment flows
--   * a PAID series (Rs 499, 180 days) with one paid test and one free sample test
--   * a batch "JEE-2027-A" containing the demo student
--   * a BATCH-RESTRICTED series (Rs 999) that batch members get for free
-- =====================================================================================

-- Paid series ------------------------------------------------------------------------
INSERT INTO test_series (id, exam_id, name, slug, description, price, is_free, validity_days, status, created_by)
VALUES ('15000000-0000-7000-8000-000000000002', '10000000-0000-7000-8000-000000000001',
        'JEE Main Full Mock Series 2027', 'jee-main-full-mock-series-2027',
        'Full-length mocks on the latest NTA pattern with detailed analysis.', 499.00, FALSE, 180, 'PUBLISHED',
        '00000000-0000-7000-8000-000000000001');

INSERT INTO tests (id, series_id, exam_id, title, description, instructions, pattern, duration_minutes,
                   total_marks, total_questions, status, is_free, max_attempts, display_order, created_by)
VALUES
    ('16000000-0000-7000-8000-000000000002', '15000000-0000-7000-8000-000000000002',
     '10000000-0000-7000-8000-000000000001', 'Full Mock 1 (paid)', 'Paid mock for enrolled students.',
     'Paid test instructions.', 'CUSTOM', 20, 12, 3, 'PUBLISHED', FALSE, 1, 2,
     '00000000-0000-7000-8000-000000000001'),
    ('16000000-0000-7000-8000-000000000003', '15000000-0000-7000-8000-000000000002',
     '10000000-0000-7000-8000-000000000001', 'Free Sample Test', 'Try before you buy.',
     'Sample test instructions.', 'CUSTOM', 10, 8, 2, 'PUBLISHED', TRUE, 1, 1,
     '00000000-0000-7000-8000-000000000001');

INSERT INTO test_sections (id, test_id, subject_id, name, display_order) VALUES
    ('17000000-0000-7000-8000-000000000011', '16000000-0000-7000-8000-000000000002', '11000000-0000-7000-8000-000000000001', 'Physics', 1),
    ('17000000-0000-7000-8000-000000000021', '16000000-0000-7000-8000-000000000003', '11000000-0000-7000-8000-000000000003', 'Mathematics', 1);

INSERT INTO test_questions (id, test_id, section_id, question_id, display_order, marks, negative_marks) VALUES
    ('18000000-0000-7000-8000-000000000011', '16000000-0000-7000-8000-000000000002', '17000000-0000-7000-8000-000000000011', '14000000-0000-7000-8000-000000000001', 1, 4, 1),
    ('18000000-0000-7000-8000-000000000012', '16000000-0000-7000-8000-000000000002', '17000000-0000-7000-8000-000000000011', '14000000-0000-7000-8000-000000000002', 2, 4, 1),
    ('18000000-0000-7000-8000-000000000013', '16000000-0000-7000-8000-000000000002', '17000000-0000-7000-8000-000000000011', '14000000-0000-7000-8000-000000000003', 3, 4, 0),
    ('18000000-0000-7000-8000-000000000021', '16000000-0000-7000-8000-000000000003', '17000000-0000-7000-8000-000000000021', '14000000-0000-7000-8000-000000000007', 1, 4, 1),
    ('18000000-0000-7000-8000-000000000022', '16000000-0000-7000-8000-000000000003', '17000000-0000-7000-8000-000000000021', '14000000-0000-7000-8000-000000000008', 2, 4, 1);

-- Batch + batch-restricted series ----------------------------------------------------
INSERT INTO batches (id, code, name, description, exam_id, active, created_by)
VALUES ('1a000000-0000-7000-8000-000000000001', 'JEE-2027-A', 'JEE 2027 Batch A',
        'Classroom batch; gets the batch series for free.', '10000000-0000-7000-8000-000000000001', TRUE,
        '00000000-0000-7000-8000-000000000001');

INSERT INTO batch_members (batch_id, user_id)
VALUES ('1a000000-0000-7000-8000-000000000001', '00000000-0000-7000-8000-000000000003');

INSERT INTO test_series (id, exam_id, name, slug, description, price, is_free, validity_days, batch_restricted,
                         status, created_by)
VALUES ('15000000-0000-7000-8000-000000000003', '10000000-0000-7000-8000-000000000001',
        'Batch A Weekly Tests', 'batch-a-weekly-tests', 'Weekly tests for classroom batch A.', 999.00, FALSE,
        365, TRUE, 'PUBLISHED', '00000000-0000-7000-8000-000000000001');

INSERT INTO test_series_batches (series_id, batch_id)
VALUES ('15000000-0000-7000-8000-000000000003', '1a000000-0000-7000-8000-000000000001');

INSERT INTO tests (id, series_id, exam_id, title, instructions, pattern, duration_minutes, total_marks,
                   total_questions, status, is_free, max_attempts, created_by)
VALUES ('16000000-0000-7000-8000-000000000004', '15000000-0000-7000-8000-000000000003',
        '10000000-0000-7000-8000-000000000001', 'Weekly Test 1', 'Batch test.', 'CUSTOM', 15, 8, 2,
        'PUBLISHED', FALSE, 1, '00000000-0000-7000-8000-000000000001');

INSERT INTO test_sections (id, test_id, subject_id, name, display_order)
VALUES ('17000000-0000-7000-8000-000000000031', '16000000-0000-7000-8000-000000000004',
        '11000000-0000-7000-8000-000000000002', 'Chemistry', 1);

INSERT INTO test_questions (id, test_id, section_id, question_id, display_order, marks, negative_marks) VALUES
    ('18000000-0000-7000-8000-000000000031', '16000000-0000-7000-8000-000000000004', '17000000-0000-7000-8000-000000000031', '14000000-0000-7000-8000-000000000004', 1, 4, 1),
    ('18000000-0000-7000-8000-000000000032', '16000000-0000-7000-8000-000000000004', '17000000-0000-7000-8000-000000000031', '14000000-0000-7000-8000-000000000005', 2, 4, 1);
