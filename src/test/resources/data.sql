-- 精简种子数据：与 sql/data/seed_test_data.sql 中压测靶心账号/题库 ID 对齐

INSERT INTO sys_user (id, username, password_hash, email, nickname, role, email_verified_at, create_time, update_time, is_deleted)
VALUES (1, 'testuser', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'testuser@example.com', '测试同学', 'PREMIUM',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO bank_node (id, user_id, parent_id, node_kind, title, description, is_public, sort_no, question_count,
                       create_time, update_time, is_deleted)
VALUES (1, 1, NULL, 'LEAF', 'H2 测试公开题库', '轨道 A 集成测试用公开题库', 1, 0, 2,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO question (question_bank_id, question_type, stem, options_json, answer_json, analysis, sort_no,
                      create_time, update_time, is_deleted)
VALUES (1, 'SINGLE', 'HTTP 默认端口是？', '["21", "80", "443"]', '["B"]', 'HTTP 默认 80', 1,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO question (question_bank_id, question_type, stem, options_json, answer_json, analysis, sort_no,
                      create_time, update_time, is_deleted)
VALUES (1, 'SINGLE', 'TCP 属于 OSI 哪一层？', '["网络层", "传输层", "应用层"]', '["B"]', 'TCP 在传输层', 2,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
