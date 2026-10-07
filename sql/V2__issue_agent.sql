ALTER TABLE `code_repository`
    ADD COLUMN `execution_mode` VARCHAR(16) NOT NULL DEFAULT 'LOCAL'
        COMMENT 'LOCAL / SANDBOX' AFTER `source_type`;

ALTER TABLE `issue_investigation_task`
    ADD COLUMN `trigger_type` VARCHAR(32) NOT NULL DEFAULT 'API'
        COMMENT 'API / AUTO_EVENT / WEBHOOK_COMMAND' AFTER `command`,
    ADD COLUMN `execution_mode` VARCHAR(16) NOT NULL DEFAULT 'LOCAL'
        COMMENT 'LOCAL / SANDBOX' AFTER `trigger_type`,
    ADD COLUMN `base_ref` VARCHAR(255) DEFAULT NULL AFTER `execution_mode`,
    ADD COLUMN `base_sha` VARCHAR(64) DEFAULT NULL AFTER `base_ref`,
    ADD COLUMN `effort` VARCHAR(16) NOT NULL DEFAULT 'MEDIUM' AFTER `base_sha`,
    ADD COLUMN `background` TEXT DEFAULT NULL AFTER `effort`,
    ADD COLUMN `model_config_id` BIGINT DEFAULT NULL AFTER `background`,
    ADD COLUMN `model_name` VARCHAR(128) DEFAULT NULL AFTER `model_config_id`,
    MODIFY COLUMN `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING / RUNNING / SUCCEEDED / FAILED / CANCELLED',
    ADD COLUMN `input_tokens` BIGINT NOT NULL DEFAULT 0 AFTER `status`,
    ADD COLUMN `output_tokens` BIGINT NOT NULL DEFAULT 0 AFTER `input_tokens`,
    ADD COLUMN `summary` TEXT DEFAULT NULL AFTER `output_tokens`,
    ADD COLUMN `report_json` MEDIUMTEXT DEFAULT NULL AFTER `summary`,
    ADD COLUMN `report_markdown` MEDIUMTEXT DEFAULT NULL AFTER `report_json`,
    ADD COLUMN `execution_log` MEDIUMTEXT DEFAULT NULL AFTER `report_markdown`,
    ADD COLUMN `external_comment_id` VARCHAR(64) DEFAULT NULL AFTER `error_message`,
    ADD COLUMN `started_at` DATETIME DEFAULT NULL AFTER `external_comment_id`,
    ADD COLUMN `finished_at` DATETIME DEFAULT NULL AFTER `started_at`;
