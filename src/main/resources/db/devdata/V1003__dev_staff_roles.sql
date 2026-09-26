-- =====================================================================================
-- V1003__dev_staff_roles.sql — DEV / TEST ONLY
--   * one demo user per Admin Portal 2.0 staff role (password: Staff@123)
--   * subject scopes for the demo teacher (all three JEE Main subjects)
--
--   content@examprep.local    CONTENT_MANAGER  (can approve test publishing)
--   reviewer@examprep.local   REVIEWER
--   operator@examprep.local   TEST_OPERATOR    (can request publish / finalize ranks)
--   support@examprep.local    SUPPORT_AGENT
--   finance@examprep.local    FINANCE
--   marketing@examprep.local  MARKETING
-- =====================================================================================

INSERT INTO users (id, email, phone, password_hash, full_name, status, email_verified) VALUES
    ('00000000-0000-7000-8000-000000000011', 'content@examprep.local',   '9000000011',
     crypt('Staff@123', gen_salt('bf', 10)), 'Chitra (Content Manager)', 'ACTIVE', TRUE),
    ('00000000-0000-7000-8000-000000000012', 'reviewer@examprep.local',  '9000000012',
     crypt('Staff@123', gen_salt('bf', 10)), 'Ravi (Reviewer)', 'ACTIVE', TRUE),
    ('00000000-0000-7000-8000-000000000013', 'operator@examprep.local',  '9000000013',
     crypt('Staff@123', gen_salt('bf', 10)), 'Omkar (Test Operator)', 'ACTIVE', TRUE),
    ('00000000-0000-7000-8000-000000000014', 'support@examprep.local',   '9000000014',
     crypt('Staff@123', gen_salt('bf', 10)), 'Sana (Support)', 'ACTIVE', TRUE),
    ('00000000-0000-7000-8000-000000000015', 'finance@examprep.local',   '9000000015',
     crypt('Staff@123', gen_salt('bf', 10)), 'Farhan (Finance)', 'ACTIVE', TRUE),
    ('00000000-0000-7000-8000-000000000016', 'marketing@examprep.local', '9000000016',
     crypt('Staff@123', gen_salt('bf', 10)), 'Meenal (Marketing)', 'ACTIVE', TRUE);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM (VALUES
    ('00000000-0000-7000-8000-000000000011'::uuid, 'CONTENT_MANAGER'),
    ('00000000-0000-7000-8000-000000000012'::uuid, 'REVIEWER'),
    ('00000000-0000-7000-8000-000000000013'::uuid, 'TEST_OPERATOR'),
    ('00000000-0000-7000-8000-000000000014'::uuid, 'SUPPORT_AGENT'),
    ('00000000-0000-7000-8000-000000000015'::uuid, 'FINANCE'),
    ('00000000-0000-7000-8000-000000000016'::uuid, 'MARKETING')) AS u(id, role)
JOIN roles r ON r.name = u.role;

-- The demo teacher authors Physics, Chemistry and Mathematics for JEE Main.
INSERT INTO user_subject_scopes (user_id, subject_id) VALUES
    ('00000000-0000-7000-8000-000000000002', '11000000-0000-7000-8000-000000000001'),
    ('00000000-0000-7000-8000-000000000002', '11000000-0000-7000-8000-000000000002'),
    ('00000000-0000-7000-8000-000000000002', '11000000-0000-7000-8000-000000000003');
