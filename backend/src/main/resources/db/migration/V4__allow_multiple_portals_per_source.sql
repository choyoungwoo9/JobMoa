-- 기업군 하나에 여러 계열사 포털이 있으므로 (실행, 기업군)은 더 이상 고유하지 않다.
-- 포털 작업의 중복은 실행 ID와 포털 ID를 결합한 기본 키(id)가 방지한다.
-- 외래 키가 사용할 대체 인덱스를 먼저 만든 뒤 기존 고유 인덱스를 제거한다.
ALTER TABLE collection_run_source
    ADD INDEX idx_run_source_group (run_id, source_id, status);

ALTER TABLE collection_run_source
    DROP INDEX uk_run_source;
