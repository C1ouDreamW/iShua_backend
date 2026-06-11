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
