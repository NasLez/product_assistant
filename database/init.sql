CREATE TABLE IF NOT EXISTS product_snapshot (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    amazon_domain VARCHAR(64) NOT NULL,
    asin VARCHAR(16) NOT NULL,
    source_url VARCHAR(2048) NOT NULL,
    title VARCHAR(1024) NOT NULL,
    brand VARCHAR(255) NULL,
    category_path VARCHAR(1024) NULL,
    price_amount DECIMAL(19, 4) NULL,
    currency VARCHAR(8) NULL,
    main_image_url VARCHAR(2048) NULL,
    normalized_json JSON NOT NULL,
    raw_json JSON NOT NULL,
    fetched_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_domain_asin (amazon_domain, asin)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS analysis_result (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_snapshot_id BIGINT UNSIGNED NOT NULL,
    model VARCHAR(128) NOT NULL,
    prompt_version VARCHAR(32) NOT NULL,
    target_users JSON NOT NULL,
    use_cases JSON NOT NULL,
    pain_points JSON NOT NULL,
    core_selling_points JSON NOT NULL,
    video_script VARCHAR(600) NOT NULL,
    ai_raw_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_analysis_product_model_prompt
        (product_snapshot_id, model, prompt_version),
    CONSTRAINT fk_analysis_product
        FOREIGN KEY (product_snapshot_id) REFERENCES product_snapshot(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
