# 일반 직군 분류 v0.5 구현 검증

검증일: 2026-11-15. 외부 사이트 수집은 실행하지 않는다. 440개는 고정 평가 스냅샷 수이며 현재 운영 데이터 수를 의미하지 않는다.

## 규칙과 정답

- 제품 원본: [규칙 리소스](../../backend/src/main/resources/classification/rules-v0.5.json). v0.5-draft에서 버전·상태 메타데이터만 승격했고 판정 조건을 유지했다.
- 정답: [gold-v1 manifest](classification-gold-v1.json). 기존 AI 라벨은 수정하지 않았다.
- 실제 Kotlin 분류기에 원본 제목·본문 440개를 입력해 Python 기준 출력과 대조했다. 직군 집합과 구역 추출 모드 440/440 일치, 차이 0개.
- 고정 정답과는 378/440개 완전 일치(85.91%), TP 530 / FP 47 / FN 24, micro F1 93.72%. 규칙 도출에 쓴 데이터의 회귀 평가이며 새 공고의 정확도로 일반화하지 않는다.
- 결과는 432개 분류, 8개 미분류, 577개 연결이다. 정답의 439개 분류/554개 연결과 구별한다. 기존 정답 감사 후보 4건은 그대로 남겨 평가했다.

## 로컬 검증

Kotlin 컴파일 및 프론트 프로덕션 빌드 통과. DB 통합·화면 검증과 사용자 요청 초기화 결과는 확인 후 아래에 추가한다.

재현하려면 루트에서 `./backend/gradlew -p backend compileKotlin processResources copyRuntimeLibs --console=plain` 후 JDK 21 이상으로 실행한다.

```sh
java -cp 'backend/build/classes/kotlin/main:backend/build/resources/main:backend/build/runtime-libs/*' \
  scripts/analysis/EvaluateKotlinClassifier.java /path/corpus.jsonl /tmp/kotlin-predictions.json
python3 scripts/analysis/evaluate-classification-rules.py \
  --corpus /path/corpus.jsonl --output /tmp/rule-evaluation.json
```

원본 본문 JSONL은 저장 당시의 440개 입력이며 저장소에 원문 전체를 커밋하지 않는다. 고정 정답에는 본문 SHA-256이 있어 같은 입력인지 확인할 수 있다. 제품은 정답 파일이나 ID 목록을 로딩하지 않는다.
