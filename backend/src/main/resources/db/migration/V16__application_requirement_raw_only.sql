-- 지원서 정보는 원문 이력만 남긴다. 기존 원문은 보존하고 AI 결과·검토 상태만 제거한다.
UPDATE posting_application_requirement
SET status = CASE
        WHEN source = 'POSTING_BODY' THEN 'FAILED'
        WHEN status IN ('WAITING_PUBLIC', 'FETCHING_PUBLIC') THEN 'WAITING_PUBLIC'
        WHEN status = 'FAILED' AND source = 'PUBLIC_PAGE' AND public_page_fetched = 0 THEN 'FAILED'
        WHEN CHAR_LENGTH(TRIM(raw_text)) > 0 THEN 'SAVED'
        ELSE 'FAILED'
    END,
    raw_text = CASE WHEN source = 'POSTING_BODY' THEN '' ELSE raw_text END,
    result_json = NULL,
    confirmed_at = NULL,
    error_message = CASE
        WHEN source = 'POSTING_BODY' THEN '이전 버전에서 공고 본문을 지원서 원문으로 저장한 기록입니다. 실제 지원 페이지 원문만 사용하세요.'
        WHEN status IN ('WAITING_PUBLIC', 'FETCHING_PUBLIC') THEN NULL
        WHEN status = 'FAILED' AND source = 'PUBLIC_PAGE' AND public_page_fetched = 0 THEN error_message
        WHEN CHAR_LENGTH(TRIM(raw_text)) > 0 THEN NULL
        ELSE COALESCE(error_message, '저장된 지원서 원문이 없습니다.')
    END,
    lease_token = NULL,
    lease_expires_at = NULL,
    updated_at = CURRENT_TIMESTAMP(6);
