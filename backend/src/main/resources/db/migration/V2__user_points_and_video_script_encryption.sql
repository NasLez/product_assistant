ALTER TABLE analysis_result
    MODIFY COLUMN video_script VARCHAR(600) NULL,
    ADD COLUMN video_script_ciphertext BLOB NULL AFTER video_script,
    ADD COLUMN video_script_iv BINARY(12) NULL AFTER video_script_ciphertext,
    ADD COLUMN video_script_key_version VARCHAR(32) NULL AFTER video_script_iv;

CREATE TABLE app_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    points INT UNSIGNED NOT NULL DEFAULT 10,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_email (email),
    CONSTRAINT chk_app_user_points CHECK (points >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE point_transaction (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    request_id CHAR(36)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    transaction_type VARCHAR(32)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    delta INT NOT NULL,
    balance_after INT UNSIGNED NOT NULL,
    analysis_result_id BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    settled_at DATETIME(3) NULL,
    refunded_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_point_transaction_user_request (user_id, request_id),
    KEY idx_point_transaction_status_created (status, created_at),
    KEY idx_point_transaction_analysis (analysis_result_id),
    CONSTRAINT fk_point_transaction_user
        FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_point_transaction_analysis
        FOREIGN KEY (analysis_result_id) REFERENCES analysis_result(id),
    CONSTRAINT chk_point_transaction_type
        CHECK (transaction_type IN ('REGISTER_BONUS', 'ANALYSIS_DEBIT')),
    CONSTRAINT chk_point_transaction_status
        CHECK (status IN ('SETTLED', 'RESERVED', 'REFUNDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_analysis_access (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    analysis_result_id BIGINT UNSIGNED NOT NULL,
    point_transaction_id BIGINT UNSIGNED NOT NULL,
    request_id CHAR(36)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_analysis_access_request (user_id, request_id),
    KEY idx_user_analysis_access_result (user_id, analysis_result_id),
    CONSTRAINT fk_user_analysis_access_user
        FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_user_analysis_access_result
        FOREIGN KEY (analysis_result_id) REFERENCES analysis_result(id),
    CONSTRAINT fk_user_analysis_access_transaction
        FOREIGN KEY (point_transaction_id) REFERENCES point_transaction(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
