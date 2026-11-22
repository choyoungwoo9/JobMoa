CREATE TABLE application_case (
    id VARCHAR(36) PRIMARY KEY,
    posting_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    applied_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_application_case_posting (posting_id),
    INDEX idx_application_case_status (status, updated_at),
    CONSTRAINT fk_application_case_posting FOREIGN KEY (posting_id) REFERENCES posting(id) ON DELETE CASCADE
);

-- 이전 관심 공고 화면에 저장된 지원 완료 이력을 새 지원 준비 공간으로 옮긴다.
INSERT INTO application_case (id, posting_id, status, started_at, applied_at, created_at, updated_at)
SELECT UUID(), posting_id, 'APPLIED', TIMESTAMP(applied_on, '00:00:00'), TIMESTAMP(applied_on, '00:00:00'), updated_at, updated_at
FROM posting_interest
WHERE application_status = 'APPLIED' AND applied_on IS NOT NULL;

CREATE TABLE application_question (
    id VARCHAR(36) PRIMARY KEY,
    application_id VARCHAR(36) NOT NULL,
    source_requirement_id VARCHAR(36),
    sort_order INT NOT NULL,
    prompt LONGTEXT NOT NULL,
    character_limit INT,
    source_quote LONGTEXT,
    answer_draft LONGTEXT NOT NULL,
    answer_fact_ids_json LONGTEXT NOT NULL,
    answer_task_id VARCHAR(36),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_application_question_order (application_id, sort_order, created_at),
    CONSTRAINT fk_application_question_case FOREIGN KEY (application_id) REFERENCES application_case(id) ON DELETE CASCADE,
    CONSTRAINT fk_application_question_requirement FOREIGN KEY (source_requirement_id) REFERENCES posting_application_requirement(id) ON DELETE SET NULL
);

CREATE TABLE application_material_draft (
    application_id VARCHAR(36) PRIMARY KEY,
    source_task_id VARCHAR(36) NOT NULL,
    draft_json LONGTEXT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_application_material_case FOREIGN KEY (application_id) REFERENCES application_case(id) ON DELETE CASCADE
);

CREATE TABLE application_interview_session (
    id VARCHAR(36) PRIMARY KEY,
    application_id VARCHAR(36) NOT NULL,
    status VARCHAR(24) NOT NULL,
    next_question_index INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_application_session_case (application_id, updated_at),
    CONSTRAINT fk_application_session_case FOREIGN KEY (application_id) REFERENCES application_case(id) ON DELETE CASCADE
);

CREATE TABLE application_interview_turn (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    position INT NOT NULL,
    basis VARCHAR(32) NOT NULL,
    question LONGTEXT NOT NULL,
    answer LONGTEXT NOT NULL,
    follow_up_question LONGTEXT,
    follow_up_answer LONGTEXT NOT NULL,
    proposed_title VARCHAR(500),
    proposed_summary LONGTEXT,
    source_excerpt VARCHAR(1000),
    promoted_material_id VARCHAR(36),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_application_turn_order (session_id, position),
    CONSTRAINT fk_application_turn_session FOREIGN KEY (session_id) REFERENCES application_interview_session(id) ON DELETE CASCADE
);

CREATE TABLE application_ai_task (
    id VARCHAR(36) PRIMARY KEY,
    application_id VARCHAR(36) NOT NULL,
    posting_id BIGINT NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    request_json LONGTEXT NOT NULL,
    result_json LONGTEXT,
    error_message LONGTEXT,
    progress_message VARCHAR(255),
    attempts INT NOT NULL DEFAULT 0,
    lease_token VARCHAR(36),
    lease_expires_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_application_ai_queue (status, created_at, id),
    INDEX idx_application_ai_case (application_id, task_type, created_at),
    INDEX idx_application_ai_lease (status, lease_expires_at),
    CONSTRAINT fk_application_ai_case FOREIGN KEY (application_id) REFERENCES application_case(id) ON DELETE CASCADE,
    CONSTRAINT fk_application_ai_posting FOREIGN KEY (posting_id) REFERENCES posting(id) ON DELETE CASCADE
);

ALTER TABLE application_question
    ADD CONSTRAINT fk_application_question_answer_task FOREIGN KEY (answer_task_id) REFERENCES application_ai_task(id) ON DELETE SET NULL;

ALTER TABLE application_material_draft
    ADD CONSTRAINT fk_application_material_source_task FOREIGN KEY (source_task_id) REFERENCES application_ai_task(id) ON DELETE CASCADE;

ALTER TABLE application_interview_turn
    ADD CONSTRAINT fk_application_turn_promoted_material FOREIGN KEY (promoted_material_id) REFERENCES career_material(id) ON DELETE SET NULL;
