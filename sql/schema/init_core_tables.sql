SET NAMES utf8mb4;

DROP TABLE IF EXISTS `wrong_question`;
DROP TABLE IF EXISTS `question`;
DROP TABLE IF EXISTS `ai_import_task`;
DROP TABLE IF EXISTS `bank_node`;
DROP TABLE IF EXISTS `sys_user`;

CREATE TABLE `sys_user` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'primary key',
  `username` VARCHAR(64) NOT NULL COMMENT 'login account',
  `password_hash` VARCHAR(255) NOT NULL COMMENT 'bcrypt password hash',
  `email` VARCHAR(254) NOT NULL COMMENT 'email address',
  `nickname` VARCHAR(64) DEFAULT NULL COMMENT 'nickname',
  `role` VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT 'USER / PREMIUM / ADMIN',
  `email_verified_at` DATETIME DEFAULT NULL COMMENT 'email verified time',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'logical delete flag',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`, `is_deleted`),
  UNIQUE KEY `uk_email` (`email`, `is_deleted`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='sys_user';

CREATE TABLE `bank_node` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'primary key',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT 'owner user id',
  `parent_id` BIGINT UNSIGNED DEFAULT NULL COMMENT 'parent node id, NULL for forest root',
  `node_kind` VARCHAR(16) NOT NULL COMMENT 'FOLDER | LEAF',
  `title` VARCHAR(200) NOT NULL COMMENT 'node title',
  `description` VARCHAR(1000) DEFAULT NULL COMMENT 'node description',
  `is_public` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'public flag, meaningful for LEAF',
  `sort_no` INT NOT NULL DEFAULT 0 COMMENT 'sort order among siblings',
  `question_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'denormalized question count, LEAF only',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'logical delete flag',
  PRIMARY KEY (`id`),
  KEY `idx_user_parent_sort` (`user_id`, `parent_id`, `sort_no`, `is_deleted`),
  KEY `idx_parent_sort` (`parent_id`, `sort_no`, `is_deleted`),
  KEY `idx_root_public` (`parent_id`, `is_public`, `node_kind`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='bank_node';

CREATE TABLE `ai_import_task` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'primary key',
  `task_id` VARCHAR(64) NOT NULL COMMENT 'business task id',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT 'submitter user id',
  `bank_id` BIGINT UNSIGNED NOT NULL COMMENT 'target LEAF node id',
  `status` VARCHAR(32) NOT NULL COMMENT 'SUBMITTED/PROCESSING/PARSED/IMPORTING/IMPORTED/FAILED/EXPIRED',
  `file_name` VARCHAR(255) DEFAULT NULL COMMENT 'source file name',
  `file_size` BIGINT UNSIGNED DEFAULT NULL COMMENT 'file size bytes',
  `file_url` VARCHAR(512) DEFAULT NULL COMMENT 'uploaded file path',
  `import_type` VARCHAR(16) NOT NULL DEFAULT 'file' COMMENT 'file/text',
  `question_count` INT UNSIGNED DEFAULT NULL COMMENT 'parsed question count',
  `preview_json` LONGTEXT DEFAULT NULL COMMENT 'preview questions json',
  `error_message` VARCHAR(500) DEFAULT NULL COMMENT 'error message',
  `submitted_at` DATETIME NOT NULL COMMENT 'submitted time',
  `parsed_at` DATETIME DEFAULT NULL COMMENT 'parsed time',
  `imported_at` DATETIME DEFAULT NULL COMMENT 'imported time',
  `expired_at` DATETIME DEFAULT NULL COMMENT 'expired time',
  `mineru_duration_ms` INT UNSIGNED DEFAULT NULL COMMENT 'mineru duration ms',
  `llm_duration_ms` INT UNSIGNED DEFAULT NULL COMMENT 'llm duration ms',
  `pipeline_duration_ms` INT UNSIGNED DEFAULT NULL COMMENT 'pipeline duration ms',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'logical delete flag',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_id` (`task_id`, `is_deleted`),
  KEY `idx_user_status_time` (`user_id`, `status`, `submitted_at`, `is_deleted`),
  KEY `idx_bank_status_time` (`bank_id`, `status`, `submitted_at`, `is_deleted`),
  KEY `idx_parsed_expire` (`status`, `parsed_at`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ai_import_task';

CREATE TABLE `question` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'primary key',
  `question_bank_id` BIGINT UNSIGNED NOT NULL COMMENT 'LEAF bank_node id',
  `question_type` VARCHAR(32) NOT NULL DEFAULT 'SINGLE' COMMENT 'question type',
  `stem` LONGTEXT NOT NULL COMMENT 'question stem',
  `options_json` JSON DEFAULT NULL COMMENT 'options json',
  `answer_json` JSON DEFAULT NULL COMMENT 'answer json',
  `analysis` LONGTEXT DEFAULT NULL COMMENT 'analysis',
  `raw_llm_json` LONGTEXT DEFAULT NULL COMMENT 'raw llm json backup',
  `sort_no` INT NOT NULL DEFAULT 0 COMMENT 'sort number',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'logical delete flag',
  PRIMARY KEY (`id`),
  KEY `idx_bank_sort` (`question_bank_id`, `sort_no`, `is_deleted`),
  KEY `idx_bank_id` (`question_bank_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='question';

CREATE TABLE `wrong_question` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'primary key',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT 'user id',
  `question_id` BIGINT UNSIGNED NOT NULL COMMENT 'question id',
  `wrong_count` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT 'wrong count',
  `last_wrong_time` DATETIME DEFAULT NULL COMMENT 'last wrong time',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'logical delete flag',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_question` (`user_id`, `question_id`),
  KEY `idx_user_create` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='wrong_question';
