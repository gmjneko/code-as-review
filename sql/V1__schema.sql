-- Code Review business tables. AgentScope-managed tables are not defined here (see README.md).

CREATE DATABASE IF NOT EXISTS `code_as_review` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `code_as_review`;

CREATE TABLE IF NOT EXISTS `sys_user`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `username`      VARCHAR(64)  NOT NULL,
    `email`         VARCHAR(128)          DEFAULT NULL,
    `password_hash` VARCHAR(100) NOT NULL,
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='Registered users';

CREATE TABLE IF NOT EXISTS `scm_credential`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT       NOT NULL,
    `name`          VARCHAR(64)  NOT NULL,
    `provider`      VARCHAR(16)  NOT NULL COMMENT 'GITHUB / GITLAB',
    `auth_type`     VARCHAR(16)  NOT NULL COMMENT 'PAT / GITHUB_APP / OAUTH',
    `host`          VARCHAR(255) NOT NULL COMMENT 'e.g. github.com or a self-hosted GitLab host',
    `secret_cipher` TEXT         NOT NULL COMMENT 'AES-GCM encrypted token or private key',
    `expires_at`    DATETIME              DEFAULT NULL,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='Credentials for remote code hosts (reserved for GitHub/GitLab)';

CREATE TABLE IF NOT EXISTS `code_repository`
(
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`               BIGINT       NOT NULL,
    `name`                  VARCHAR(128) NOT NULL,
    `source_type`           VARCHAR(16)  NOT NULL COMMENT 'LOCAL / GITHUB / GITLAB',
    `local_path`            VARCHAR(1024)         DEFAULT NULL COMMENT 'Server-side path for LOCAL repositories',
    `remote_url`            VARCHAR(1024)         DEFAULT NULL,
    `external_full_name`    VARCHAR(255)          DEFAULT NULL COMMENT 'owner/repo or group/project',
    `default_branch`        VARCHAR(255)          DEFAULT NULL,
    `credential_id`         BIGINT                DEFAULT NULL,
    `webhook_secret_cipher` TEXT                  DEFAULT NULL,
    `last_synced_at`        DATETIME              DEFAULT NULL,
    `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`               TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_external` (`source_type`, `external_full_name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='Repositories registered for review';

CREATE TABLE IF NOT EXISTS `llm_model_config`
(
    `id`             BIGINT        NOT NULL AUTO_INCREMENT,
    `user_id`        BIGINT        NOT NULL,
    `name`           VARCHAR(64)   NOT NULL,
    `base_url`       VARCHAR(512)  NOT NULL COMMENT 'OpenAI-compatible endpoint',
    `model_name`     VARCHAR(128)  NOT NULL,
    `api_key_cipher` TEXT          NOT NULL,
    `is_default`     TINYINT       NOT NULL DEFAULT 0,
    `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`        TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='Per-user LLM endpoints';

CREATE TABLE IF NOT EXISTS `review_task`
(
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`               BIGINT       NOT NULL,
    `repository_id`         BIGINT       NOT NULL,
    `target_type`           VARCHAR(32)  NOT NULL COMMENT 'LOCAL_WORKING_TREE / COMMIT_RANGE / PULL_REQUEST / ISSUE',
    `trigger_type`          VARCHAR(32)  NOT NULL COMMENT 'API / WEBHOOK_COMMAND',
    `base_ref`              VARCHAR(255)          DEFAULT NULL,
    `head_ref`              VARCHAR(255)          DEFAULT NULL,
    `base_sha`              VARCHAR(64)           DEFAULT NULL,
    `head_sha`              VARCHAR(64)           DEFAULT NULL,
    `external_ref`          VARCHAR(64)           DEFAULT NULL COMMENT 'PR/MR/Issue number for remote targets',
    `effort`                VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM' COMMENT 'LOW / MEDIUM / HIGH',
    `background`            TEXT                  DEFAULT NULL COMMENT 'Requirement background supplied by the requester',
    `model_config_id`       BIGINT                DEFAULT NULL COMMENT 'NULL means the system default model',
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / RUNNING / SUCCEEDED / FAILED / CANCELLED',
    `files_changed`         INT          NOT NULL DEFAULT 0,
    `files_reviewed`        INT          NOT NULL DEFAULT 0,
    `comment_count`         INT          NOT NULL DEFAULT 0,
    `input_tokens`          BIGINT       NOT NULL DEFAULT 0,
    `output_tokens`         BIGINT       NOT NULL DEFAULT 0,
    `rounds_completed`      INT          NOT NULL DEFAULT 0,
    `plan_result`           MEDIUMTEXT            DEFAULT NULL COMMENT 'Output of the plan phase, when it ran',
    `summary`               TEXT                  DEFAULT NULL,
    `error_message`         TEXT                  DEFAULT NULL,
    `started_at`            DATETIME              DEFAULT NULL,
    `finished_at`           DATETIME              DEFAULT NULL,
    `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`               TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_created` (`user_id`, `created_at`),
    KEY `idx_repository` (`repository_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='One review run';

CREATE TABLE IF NOT EXISTS `review_comment`
(
    `id`                  BIGINT        NOT NULL AUTO_INCREMENT,
    `task_id`             BIGINT        NOT NULL,
    `file_path`           VARCHAR(1024) NOT NULL,
    `start_line`          INT                    DEFAULT NULL COMMENT 'Line in the new file; NULL when it could not be located',
    `end_line`            INT                    DEFAULT NULL,
    `category`            VARCHAR(32)            DEFAULT NULL,
    `severity`            VARCHAR(16)            DEFAULT NULL,
    `content`             TEXT          NOT NULL,
    `existing_code`       TEXT                   DEFAULT NULL,
    `suggestion_code`     TEXT                   DEFAULT NULL,
    `round`               INT           NOT NULL DEFAULT 1,
    `status`              VARCHAR(16)   NOT NULL COMMENT 'CONFIRMED / FILTERED',
    `filter_reason`       TEXT                   DEFAULT NULL,
    `external_comment_id` VARCHAR(64)            DEFAULT NULL COMMENT 'Set once published to a remote host',
    `created_at`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`             TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_task_status` (`task_id`, `status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='Review findings';

CREATE TABLE IF NOT EXISTS `webhook_event`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `provider`      VARCHAR(16)  NOT NULL COMMENT 'GITHUB / GITLAB',
    `delivery_id`   VARCHAR(128) NOT NULL,
    `event_type`    VARCHAR(64)  NOT NULL,
    `repository_id` BIGINT                DEFAULT NULL,
    `payload`       JSON                  DEFAULT NULL,
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'RECEIVED' COMMENT 'RECEIVED / IGNORED / DISPATCHED / FAILED',
    `task_id`       BIGINT                DEFAULT NULL,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_delivery` (`provider`, `delivery_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='Inbound webhook deliveries (reserved for GitHub/GitLab)';
