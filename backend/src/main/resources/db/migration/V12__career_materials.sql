CREATE TABLE career_material (
    id VARCHAR(36) PRIMARY KEY,
    original_filename VARCHAR(512) NOT NULL,
    detected_media_type VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    storage_key VARCHAR(36) NOT NULL,
    status VARCHAR(24) NOT NULL,
    error_message LONGTEXT,
    attempts INT NOT NULL DEFAULT 0,
    lease_token VARCHAR(36),
    lease_expires_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_career_material_hash (content_hash),
    UNIQUE KEY uk_career_material_storage (storage_key),
    INDEX idx_career_material_status (status, created_at),
    INDEX idx_career_material_lease (status, lease_expires_at)
);

CREATE TABLE career_material_fragment (
    id VARCHAR(36) PRIMARY KEY,
    material_id VARCHAR(36) NOT NULL,
    ordinal INT NOT NULL,
    location_label VARCHAR(255) NOT NULL,
    content LONGTEXT NOT NULL,
    UNIQUE KEY uk_career_fragment_ordinal (material_id, ordinal),
    FOREIGN KEY (material_id) REFERENCES career_material(id) ON DELETE CASCADE
);

CREATE TABLE career_fact (
    id VARCHAR(36) PRIMARY KEY,
    material_id VARCHAR(36) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    title VARCHAR(500) NOT NULL,
    summary LONGTEXT NOT NULL,
    verification_status VARCHAR(24) NOT NULL DEFAULT 'UNVERIFIED',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_career_fact_material (material_id, created_at),
    FOREIGN KEY (material_id) REFERENCES career_material(id) ON DELETE CASCADE
);

CREATE TABLE career_fact_evidence (
    id VARCHAR(36) PRIMARY KEY,
    fact_id VARCHAR(36) NOT NULL,
    fragment_id VARCHAR(36) NOT NULL,
    excerpt VARCHAR(1000) NOT NULL,
    UNIQUE KEY uk_career_evidence_fragment (fact_id, fragment_id),
    FOREIGN KEY (fact_id) REFERENCES career_fact(id) ON DELETE CASCADE,
    FOREIGN KEY (fragment_id) REFERENCES career_material_fragment(id) ON DELETE CASCADE
);
