-- 精简种子数据：与 sql/data/seed_test_data.sql 中压测靶心账号/题库 ID 对齐

INSERT INTO sys_user (id, username, password_hash, email, nickname, role, email_verified_at, create_time, update_time, is_deleted)
VALUES (1, 'testuser', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'testuser@example.com', '测试同学', 'PREMIUM',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO bank_node (id, user_id, parent_id, node_kind, title, description, is_public, sort_no, question_count,
                       create_time, update_time, is_deleted)
VALUES (1, 1, NULL, 'LEAF', 'H2 测试公开题库', '轨道 A 集成测试用公开题库', 1, 0, 2,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO question (question_bank_id, question_type, stem, options_json, answer_json, answer_source, analysis, sort_no,
                      create_time, update_time, is_deleted)
VALUES (1, 'SINGLE', 'HTTP 默认端口是？', '["21", "80", "443"]', '["B"]', 'ORIGINAL', 'HTTP 默认 80', 1,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO question (question_bank_id, question_type, stem, options_json, answer_json, answer_source, analysis, sort_no,
                      create_time, update_time, is_deleted)
VALUES (1, 'SINGLE', 'TCP 属于 OSI 哪一层？', '["网络层", "传输层", "应用层"]', '["B"]', 'ORIGINAL', 'TCP 在传输层', 2,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- AI 导入任务种子：一条 PARSED 任务，preview_json 含两道 MISSING 客观题供 AI 解答流程使用
INSERT INTO ai_import_task (task_id, user_id, bank_id, status, file_name, file_size, file_url, import_type,
                            question_count, preview_json, error_message, submitted_at, parsed_at, imported_at,
                            expired_at, mineru_duration_ms, llm_duration_ms, pipeline_duration_ms, create_time,
                            update_time, is_deleted)
VALUES ('test-import-task-001', 1, 1, 'PARSED', 'test-questions.txt', 1024, 'file://./test-questions.txt', 'file', 2,
        '[{"questionType":"SINGLE","stem":"HTTP 默认端口是？","options":["21","80","443"],"answer":[],"analysis":"","answerSource":"MISSING"},{"questionType":"JUDGE","stem":"TCP 是面向连接的协议。","options":["正确","错误"],"answer":[],"analysis":"","answerSource":"MISSING"}]',
        NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL, NULL, 1000, 2000, 3000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- AI 解答任务种子：一条 ANSWERED 任务，answered_json 含两道 AI_GENERATED 题
INSERT INTO ai_answer_task (answer_task_id, parent_task_id, user_id, bank_id, question_count, answered_count, status,
                            answered_json, error_message, llm_duration_ms, total_calls, submitted_at, answered_at,
                            imported_at, create_time, update_time, is_deleted)
VALUES ('test-answer-task-001', 'test-import-task-001', 1, 1, 2, 2, 'ANSWERED',
        '[{"questionType":"SINGLE","stem":"HTTP 默认端口是？","options":["21","80","443"],"answer":["B"],"analysis":"HTTP 默认 80","answerSource":"AI_GENERATED","answerConfidence":"HIGH"},{"questionType":"JUDGE","stem":"TCP 是面向连接的协议。","options":["正确","错误"],"answer":["T"],"analysis":"TCP 面向连接","answerSource":"AI_GENERATED","answerConfidence":"HIGH"}]',
        NULL, 4500, 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
