DROP TABLE IF EXISTS ai_answer_task;
DROP TABLE IF EXISTS ai_import_task;
DROP TABLE IF EXISTS wrong_question;
DROP TABLE IF EXISTS question;
DROP TABLE IF EXISTS bank_node;
DROP TABLE IF EXISTS sys_user;

CREATE TABLE sys_user (
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username          VARCHAR(64)  NOT NULL,
    password_hash     VARCHAR(255) NOT NULL,
    email             VARCHAR(254) NOT NULL,
    nickname          VARCHAR(64),
    role              VARCHAR(32)  NOT NULL DEFAULT 'USER',
    email_verified_at TIMESTAMP    NULL,
    create_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted        TINYINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_username ON sys_user (username, is_deleted);
CREATE UNIQUE INDEX uk_email ON sys_user (email, is_deleted);
CREATE INDEX idx_create_time ON sys_user (create_time);

CREATE TABLE bank_node (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    parent_id       BIGINT,
    node_kind       VARCHAR(16)  NOT NULL,
    title           VARCHAR(200) NOT NULL,
    description     VARCHAR(1000),
    is_public       TINYINT      NOT NULL DEFAULT 0,
    sort_no         INT          NOT NULL DEFAULT 0,
    question_count  INT          NOT NULL DEFAULT 0,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted      TINYINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_user_parent_sort ON bank_node (user_id, parent_id, sort_no, is_deleted);
CREATE INDEX idx_parent_sort ON bank_node (parent_id, sort_no, is_deleted);
CREATE INDEX idx_root_public ON bank_node (parent_id, is_public, node_kind, is_deleted);

CREATE TABLE question (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    question_bank_id BIGINT       NOT NULL,
    question_type    VARCHAR(32)  NOT NULL DEFAULT 'SINGLE',
    stem             CLOB         NOT NULL,
    options_json     VARCHAR(4000),
    answer_json      VARCHAR(1000),
    answer_source    VARCHAR(16)  DEFAULT 'ORIGINAL',
    answer_confidence VARCHAR(16) DEFAULT NULL,
    analysis         CLOB,
    raw_llm_json     CLOB,
    sort_no          INT          NOT NULL DEFAULT 0,
    create_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted       TINYINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_bank_sort ON question (question_bank_id, sort_no, is_deleted);
CREATE INDEX idx_bank_id ON question (question_bank_id);

CREATE TABLE wrong_question (
    id              BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT    NOT NULL,
    question_id     BIGINT    NOT NULL,
    wrong_count     INT       NOT NULL DEFAULT 1,
    last_wrong_time TIMESTAMP,
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted      TINYINT   NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_user_question ON wrong_question (user_id, question_id);
CREATE INDEX idx_user_create ON wrong_question (user_id, create_time);

-- AI 导入任务表
CREATE TABLE ai_import_task (
    id                   BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id              VARCHAR(64)  NOT NULL,
    user_id              BIGINT       NOT NULL,
    bank_id              BIGINT       NOT NULL,
    status               VARCHAR(32)  NOT NULL,
    file_name            VARCHAR(255),
    file_size            BIGINT,
    file_url             VARCHAR(512),
    import_type          VARCHAR(16)  NOT NULL DEFAULT 'file',
    question_count       INT,
    preview_json         CLOB,
    error_message        VARCHAR(500),
    submitted_at         TIMESTAMP    NOT NULL,
    parsed_at            TIMESTAMP,
    imported_at          TIMESTAMP,
    expired_at           TIMESTAMP,
    mineru_duration_ms   INT,
    llm_duration_ms      INT,
    pipeline_duration_ms INT,
    create_time          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted           TINYINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_task_id ON ai_import_task (task_id, is_deleted);
CREATE INDEX idx_user_status_time ON ai_import_task (user_id, status, submitted_at, is_deleted);
CREATE INDEX idx_bank_status_time ON ai_import_task (bank_id, status, submitted_at, is_deleted);

-- AI 解答任务表
CREATE TABLE ai_answer_task (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    answer_task_id  VARCHAR(64)  NOT NULL,
    parent_task_id  VARCHAR(64)  NOT NULL,
    user_id         BIGINT       NOT NULL,
    bank_id         BIGINT       NOT NULL,
    question_count  INT          NOT NULL,
    answered_count  INT          NOT NULL DEFAULT 0,
    status          VARCHAR(32)  NOT NULL,
    answered_json   CLOB,
    error_message   VARCHAR(500),
    llm_duration_ms INT,
    total_calls     INT,
    submitted_at    TIMESTAMP    NOT NULL,
    answered_at     TIMESTAMP,
    imported_at     TIMESTAMP,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted      TINYINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_answer_task_id ON ai_answer_task (answer_task_id, is_deleted);
CREATE INDEX idx_parent ON ai_answer_task (parent_task_id, is_deleted);
CREATE INDEX idx_user_status ON ai_answer_task (user_id, status, submitted_at, is_deleted);
