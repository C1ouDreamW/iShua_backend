-- 试题表：答案来源与置信度（两阶段 AI 流程：阶段 1 抽题清洗 + 阶段 2 AI 解答）
-- 已有库执行本脚本；新库见 init_core_tables.sql
-- answer_source: ORIGINAL（原文有答案）/ MISSING（原文无答案）/ AI_GENERATED（AI 解答生成）
-- answer_confidence: HIGH/MEDIUM/LOW，仅 AI 解答流程写入，否则为 NULL

SET NAMES utf8mb4;

ALTER TABLE `question`
  ADD COLUMN `answer_source` VARCHAR(16) NULL DEFAULT 'ORIGINAL' COMMENT '答案来源: ORIGINAL/MISSING/AI_GENERATED' AFTER `answer_json`,
  ADD COLUMN `answer_confidence` VARCHAR(16) NULL DEFAULT NULL COMMENT '答案置信度: HIGH/MEDIUM/LOW' AFTER `answer_source`;
