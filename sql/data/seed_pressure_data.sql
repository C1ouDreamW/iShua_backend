-- ============================================
-- 压测数据种子脚本
-- 适用场景：测试环境压测
-- 默认账号：pt_user_0001 ~ pt_user_N
-- 默认密码：123456
-- ============================================

SET NAMES utf8mb4;

-- 清理旧压测数据
DELETE aat
FROM ai_answer_task aat
JOIN ai_import_task ait ON ait.task_id = aat.parent_task_id
JOIN bank_node qb ON qb.id = ait.bank_id
WHERE qb.title LIKE '压测题库-%';

DELETE ait
FROM ai_import_task ait
JOIN bank_node qb ON qb.id = ait.bank_id
WHERE qb.title LIKE '压测题库-%';

DELETE wq
FROM wrong_question wq
JOIN question q ON q.id = wq.question_id
JOIN bank_node qb ON qb.id = q.question_bank_id
WHERE qb.title LIKE '压测题库-%';

DELETE q
FROM question q
JOIN bank_node qb ON qb.id = q.question_bank_id
WHERE qb.title LIKE '压测题库-%';

DELETE FROM bank_node
WHERE title LIKE '压测题库-%';

DELETE FROM sys_user
WHERE username LIKE 'pt_user_%';

DROP PROCEDURE IF EXISTS seed_pressure_data;

DELIMITER //

CREATE PROCEDURE seed_pressure_data(
    IN p_user_count INT,
    IN p_bank_count INT,
    IN p_questions_per_bank INT
)
BEGIN
    DECLARE u INT DEFAULT 1;
    DECLARE b INT DEFAULT 1;
    DECLARE q INT DEFAULT 1;

    DECLARE v_owner_index INT;
    DECLARE v_owner_id BIGINT UNSIGNED;
    DECLARE v_bank_id BIGINT UNSIGNED;
    DECLARE v_username VARCHAR(64);

    -- 1. 创建压测用户
    WHILE u <= p_user_count DO
        SET v_username = CONCAT('pt_user_', LPAD(u, 4, '0'));

        INSERT INTO sys_user (
            username,
            password_hash,
            email,
            nickname,
            role,
            email_verified_at,
            create_time,
            update_time,
            is_deleted
        )
        VALUES (
            v_username,
            '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi',
            CONCAT(v_username, '@pressure.example.com'),
            CONCAT('压测用户', u),
            'PREMIUM',
            NOW(),
            NOW(),
            NOW(),
            0
        );

        SET u = u + 1;
    END WHILE;

    -- 2. 创建压测题库 + 题目
    WHILE b <= p_bank_count DO
        SET v_owner_index = ((b - 1) MOD p_user_count) + 1;

        SELECT id INTO v_owner_id
        FROM sys_user
        WHERE username = CONCAT('pt_user_', LPAD(v_owner_index, 4, '0'))
          AND is_deleted = 0
        LIMIT 1;

        INSERT INTO bank_node (
            user_id,
            parent_id,
            node_kind,
            title,
            description,
            is_public,
            sort_no,
            question_count,
            create_time,
            update_time,
            is_deleted
        )
        VALUES (
            v_owner_id,
            NULL,
            'LEAF',
            CONCAT('压测题库-', LPAD(b, 5, '0')),
            CONCAT('用于压测的公开题库，每个题库包含 ', p_questions_per_bank, ' 道题。'),
            1,
            0,
            p_questions_per_bank,
            NOW(),
            NOW(),
            0
        );

        SET v_bank_id = LAST_INSERT_ID();
        SET q = 1;

        WHILE q <= p_questions_per_bank DO
            IF MOD(q, 3) = 1 THEN

                INSERT INTO question (
                    question_bank_id,
                    question_type,
                    stem,
                    options_json,
                    answer_json,
                    analysis,
                    sort_no,
                    create_time,
                    update_time,
                    is_deleted
                )
                VALUES (
                    v_bank_id,
                    'SINGLE',
                    CONCAT('【压测单选题】题库 ', b, ' 第 ', q, ' 题：以下哪项说法正确？'),
                    '["A 选项内容", "B 选项内容", "C 选项内容", "D 选项内容"]',
                    '["A"]',
                    '压测解析：本题标准答案为 A。',
                    q,
                    NOW(),
                    NOW(),
                    0
                );

            ELSEIF MOD(q, 3) = 2 THEN

                INSERT INTO question (
                    question_bank_id,
                    question_type,
                    stem,
                    options_json,
                    answer_json,
                    analysis,
                    sort_no,
                    create_time,
                    update_time,
                    is_deleted
                )
                VALUES (
                    v_bank_id,
                    'MULTI',
                    CONCAT('【压测多选题】题库 ', b, ' 第 ', q, ' 题：以下哪些选项正确？'),
                    '["A 选项内容", "B 选项内容", "C 选项内容", "D 选项内容"]',
                    '["A", "C"]',
                    '压测解析：本题标准答案为 A、C。',
                    q,
                    NOW(),
                    NOW(),
                    0
                );

            ELSE

                INSERT INTO question (
                    question_bank_id,
                    question_type,
                    stem,
                    options_json,
                    answer_json,
                    analysis,
                    sort_no,
                    create_time,
                    update_time,
                    is_deleted
                )
                VALUES (
                    v_bank_id,
                    'JUDGE',
                    CONCAT('【压测判断题】题库 ', b, ' 第 ', q, ' 题：Redis 可以用于热点数据缓存。'),
                    '["正确", "错误"]',
                    '["T"]',
                    '压测解析：Redis 常用于热点数据缓存。',
                    q,
                    NOW(),
                    NOW(),
                    0
                );

            END IF;

            SET q = q + 1;
        END WHILE;

        SET b = b + 1;
    END WHILE;
END//

DELIMITER ;

-- 参数含义：用户数、题库数、每个题库题目数
-- 例如：20 个用户，50 个题库，每个题库 500 道题 = 25000 道题
START TRANSACTION;
CALL seed_pressure_data(20, 50, 500);
COMMIT;

DROP PROCEDURE IF EXISTS seed_pressure_data;