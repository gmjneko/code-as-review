USE `code_as_review`;

ALTER TABLE `webhook_event`
    ADD COLUMN `rag_task_id` BIGINT DEFAULT NULL AFTER `task_id`;

CREATE TABLE IF NOT EXISTS `rag_item`
(
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `repository_id`      BIGINT       NOT NULL,
    `item_type`          VARCHAR(16)  NOT NULL COMMENT 'ISSUE / PR',
    `item_number`        VARCHAR(64)  NOT NULL,
    `title`              TEXT         NOT NULL,
    `body`               MEDIUMTEXT,
    `state`              VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    `author_login`       VARCHAR(255) DEFAULT NULL,
    `html_url`           VARCHAR(1024) DEFAULT NULL,
    `source_updated_at`  DATETIME     DEFAULT NULL,
    `content_hash`       CHAR(64)     NOT NULL,
    `indexed_at`         DATETIME     DEFAULT NULL,
    `deleted`            TINYINT      NOT NULL DEFAULT 0,
    `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rag_item` (`repository_id`, `item_type`, `item_number`),
    KEY `idx_rag_item_search` (`repository_id`, `item_type`, `state`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='Issue/PR snapshots for RAG';

CREATE TABLE IF NOT EXISTS `rag_chunk`
(
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT,
    `item_id`               BIGINT       NOT NULL,
    `repository_id`         BIGINT       NOT NULL,
    `item_type`             VARCHAR(16)  NOT NULL,
    `item_number`           VARCHAR(64)  NOT NULL,
    `state`                 VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    `chunk_type`            VARCHAR(16)  NOT NULL COMMENT 'BODY / COMMENT',
    `external_comment_id`   VARCHAR(64)  DEFAULT NULL,
    `comment_kind`          VARCHAR(32)  DEFAULT NULL COMMENT 'ISSUE_COMMENT',
    `author_login`          VARCHAR(255) DEFAULT NULL,
    `text_content`          MEDIUMTEXT   NOT NULL,
    `content_hash`          CHAR(64)     NOT NULL,
    `embedding_version`     VARCHAR(128) DEFAULT NULL,
    `indexed_at`            DATETIME     DEFAULT NULL,
    `deleted`               TINYINT      NOT NULL DEFAULT 0,
    `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rag_chunk` (`repository_id`, `item_type`, `item_number`, `chunk_type`, `external_comment_id`),
    KEY `idx_rag_chunk_item` (`item_id`),
    KEY `idx_rag_chunk_lookup` (`repository_id`, `item_type`, `item_number`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='RAG chunks';

CREATE TABLE IF NOT EXISTS `rag_task`
(
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `repository_id`      BIGINT       NOT NULL,
    `user_id`            BIGINT       NOT NULL,
    `task_type`          VARCHAR(24)  NOT NULL COMMENT 'FULL_SYNC / ITEM_SYNC / ITEM_DELETE',
    `item_type`          VARCHAR(16)  DEFAULT NULL,
    `item_number`        VARCHAR(64)  DEFAULT NULL,
    `webhook_event_id`   BIGINT       DEFAULT NULL,
    `dedupe_key`         VARCHAR(255) NOT NULL,
    `status`             VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    `items_total`        INT          NOT NULL DEFAULT 0,
    `items_done`         INT          NOT NULL DEFAULT 0,
    `chunks_done`        INT          NOT NULL DEFAULT 0,
    `error_message`      TEXT,
    `started_at`         DATETIME     DEFAULT NULL,
    `finished_at`        DATETIME     DEFAULT NULL,
    `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rag_task_dedupe` (`repository_id`, `dedupe_key`),
    KEY `idx_rag_task_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='RAG indexing and association tasks';

CREATE TABLE IF NOT EXISTS `rag_relation`
(
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT,
    `repository_id`         BIGINT       NOT NULL,
    `source_type`           VARCHAR(16)  NOT NULL,
    `source_number`         VARCHAR(64)  NOT NULL,
    `target_type`           VARCHAR(16)  NOT NULL,
    `target_number`         VARCHAR(64)  NOT NULL,
    `relation_kind`         VARCHAR(32)  NOT NULL COMMENT 'DUPLICATE_ISSUE / RELATED_PR / EXPLICIT_REFERENCE',
    `origin`                VARCHAR(32)  NOT NULL COMMENT 'RAG / EXPLICIT_REFERENCE',
    `confidence`            DECIMAL(8,6) DEFAULT NULL,
    `reason`                TEXT,
    `evidence_json`         MEDIUMTEXT,
    `source_comment_id`     VARCHAR(64) DEFAULT NULL,
    `target_comment_id`     VARCHAR(64) DEFAULT NULL,
    `source_publish_status` VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    `target_publish_status` VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    `publish_error`         TEXT,
    `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rag_relation` (`repository_id`, `source_type`, `source_number`, `target_type`, `target_number`, `relation_kind`),
    KEY `idx_rag_relation_source` (`repository_id`, `source_type`, `source_number`),
    KEY `idx_rag_relation_target` (`repository_id`, `target_type`, `target_number`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='RAG Issue/PR relationships';
