# 일반 직군 분류 v0.5 구현 검증

검증일: 2026-11-15. 외부 사이트 수집은 실행하지 않는다. 440개는 고정 평가 스냅샷 수이며 현재 운영 데이터 수를 의미하지 않는다.

## 규칙과 정답

- 제품 원본: [규칙 리소스](../../backend/src/main/resources/classification/rules-v0.5.json). v0.5-draft에서 버전·상태 메타데이터만 승격했고 판정 조건을 유지했다.
- 정답: [gold-v1 manifest](classification-gold-v1.json). 기존 AI 라벨은 수정하지 않았다.
- 실제 Kotlin 분류기에 원본 제목·본문 440개를 입력해 Python 기준 출력과 대조했다. 직군 집합과 구역 추출 모드 440/440 일치, 차이 0개.
- 고정 정답과는 378/440개 완전 일치(85.91%), TP 530 / FP 47 / FN 24, micro F1 93.72%. 규칙 도출에 쓴 데이터의 회귀 평가이며 새 공고의 정확도로 일반화하지 않는다.
- 결과는 432개 분류, 8개 미분류, 577개 연결이다. 정답의 439개 분류/554개 연결과 구별한다. 기존 정답 감사 후보 4건은 그대로 남겨 평가했다.

## 로컬 검증

- Kotlin 컴파일, 프론트 TypeScript/Vite 프로덕션 빌드, 두 Docker 이미지 빌드와 healthcheck 통과.
- 실제 `TaskService.finish`를 로컬 저장소 대역으로 실행해 신규·변경·동일 저장을 확인했다. 전체 수집(`all`)에서도 복수 직군이 붙고 이전 태그가 제거되며, 동일 본문은 새 버전을 만들지 않았다. 읽음·최초 발견일과 마감 갱신도 확인했다. 정식 테스트 소스는 추가하지 않았다.
- MySQL/Flyway V7 적용 후 실제 API 재분류: requested/completed 440, 실패 0, 연결 577, 미분류 8. 모든 공고의 태그를 고정 평가 출력과 전수 대조했다.
- 재분류 전후 공고 ID·제목·회사·URL·근무지·최초 발견일·읽음·마감·본문 버전 요약·캡처 경로·수집 기록이 같았다. DB의 모든 본문 버전 SHA-256·contentHash·savedAt도 같았다. 이후 화면 검증에서 일부 공고를 열어 읽음이 변경되는 것은 기존 제품 동작이다.
- 잘못된 기존 태그 1개를 넣고 다시 재분류해 해당 연결이 제거되고 총 연결 577개가 유지되는 것을 확인했다. 재실행으로 중복 연결/본문 버전이 생기지 않았다.
- 브라우저에서 미분류 8개와 미분류 이유, 풀스택+백엔드+프론트엔드 복수 표시, 실제 판별 문장, 자동 읽음과 다음 공고 이동, 저장 본문을 확인했다. 저장 캡처 URL은 이미지 응답 200이었다. 수집 버튼은 누르지 않았다.
- 새 AI 응용 직군 통계/조회 31개와 미분류 통계 8개를 실제 API/화면으로 확인했다. 이는 초기화 전 검증 스냅샷이다.
- 1440×1000, 390×844에서 가로 넘침이 없었다. 새 직군 중 분야 수집 미지원 직군에서는 수집 창을 전체 공고로 시작해 오해를 줄였다.
- 구현된 분류/수집 회사 조합과 실사이트 검증은 별개다. 이번 변경에서는 외부 수집 0회.

기계 판독 요약은 [검증 JSON](classification-implementation-v0.5.json)이다.

재현하려면 루트에서 `./backend/gradlew -p backend compileKotlin processResources copyRuntimeLibs --console=plain` 후 JDK 21 이상으로 실행한다.

```sh
java -cp 'backend/build/classes/kotlin/main:backend/build/resources/main:backend/build/runtime-libs/*' \
  scripts/analysis/EvaluateKotlinClassifier.java /path/corpus.jsonl /tmp/kotlin-predictions.json
python3 scripts/analysis/evaluate-classification-rules.py \
  --corpus /path/corpus.jsonl --output /tmp/rule-evaluation.json
```

원본 본문 JSONL은 저장 당시의 440개 입력이며 저장소에 원문 전체를 커밋하지 않는다. 고정 정답에는 본문 SHA-256이 있어 같은 입력인지 확인할 수 있다. 제품은 정답 파일이나 ID 목록을 로딩하지 않는다.

## 검증 후 데이터 초기화

2026-11-15 사용자 요청에 따라 검증 종료 후 수집기를 중지하고 운영 데이터를 삭제했다. 공고 0, 본문 버전 0, 직군 연결 0, 상태 관측 0, 작업 0, 포털 실행 0, 수집 기록 0, 활성 실행 잠금 0을 DB에서 확인했다. 캡처 파일 480개를 삭제했고 남은 캡처 파일은 0개다. 분류 규칙·회사 카탈로그·Flyway 이력·고정 정답셋·평가 문서는 유지했다.

백엔드를 다시 생성해 실행하고 프론트 프록시를 통한 `/api/state`와 `/api/statistics`에서 공고/기록 0개와 빈 통계를 확인했다. 21개 직군과 전체 공고, 63개 회사 카탈로그는 유지된다. 실제 수집은 사용자가 직접 시작하며 이 작업에서는 신규 수집을 실행하지 않았다. 위의 440/577/8 수치는 초기화 이전 구현 검증 결과다.
