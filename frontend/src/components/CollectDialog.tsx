import { useState } from 'react'
import { SOURCES, STAGES } from '../data'
import type { FailureStage, SourceId } from '../types'
import { BrandMark } from './BrandMark'
import { Icon } from './Icon'
import { Modal } from './Modal'

export function CollectDialog({
  onClose,
  onStart,
  demo,
  availableSources,
}: {
  onClose: () => void
  onStart: (ids: SourceId[], failure: FailureStage | null) => void
  demo: boolean
  availableSources: SourceId[]
}) {
  const [selected, setSelected] = useState<SourceId[]>(availableSources)
  const [failure, setFailure] = useState(false)
  const [stage, setStage] = useState<FailureStage>('capture')
  const allSelected = selected.length === availableSources.length
  return (
    <Modal onClose={onClose} labelId="collect-title" className="collect-dialog">
      <div className="modal-heading">
        <div className="modal-symbol">
          <Icon name="layers" size={24} />
        </div>
        <button
          className="icon-button"
          onClick={onClose}
          aria-label="수집 창 닫기"
        >
          <Icon name="close" />
        </button>
      </div>
      <h2 id="collect-title">새로운 공고를 모아볼까요?</h2>
      <p className="modal-description">
        {demo ? '선택한 기업군의 계열사까지 개발자 공고를 수집해요.' : '선택한 기업군과 연결된 계열사 채용 포털에서 공고를 모아요.'}
      </p>
      <div className="source-choice-heading">
        <strong>
          수집할 기업군 <span>{selected.length}</span>
        </strong>
        <button
          className="text-button"
          onClick={() =>
            setSelected(allSelected ? [] : availableSources)
          }
        >
          {allSelected ? '전체 해제' : '전체 선택'}
        </button>
      </div>
      <div className="source-choices">
        {SOURCES.map((source) => (
          <label
            key={source.id}
            className={`source-choice ${selected.includes(source.id) ? 'selected' : ''}`}
          >
            <BrandMark sourceId={source.id} small />
            <span>{source.name}{!availableSources.includes(source.id) && ' · 준비 중'}</span>
            <input
              type="checkbox"
              disabled={!availableSources.includes(source.id)}
              checked={selected.includes(source.id)}
              onChange={() =>
                setSelected((current) =>
                  current.includes(source.id)
                    ? current.filter((id) => id !== source.id)
                    : [...current, source.id],
                )
              }
            />
          </label>
        ))}
      </div>
      <div className="collection-rule">
        <Icon name="check" size={16} />
        <span>명확한 개발 공고만 수집 · 애매한 공고는 제외</span>
      </div>
      {demo && <div className="demo-note">
        <Icon name="info" size={17} />
        <div>
          <strong>지금은 화면 검토용이에요</strong>
          <p>
            실제 사이트에 접속하지 않고 샘플 공고와 변경 버전을 추가해요. 캡처
            이미지도 예시예요.
          </p>
          <label className="failure-option">
            <input
              type="checkbox"
              checked={failure}
              onChange={(e) => setFailure(e.target.checked)}
            />
            일부 수집 실패 상황도 보기
          </label>
          {failure && (
            <label className="failure-stage-select">
              실패 단계
              <select
                value={stage}
                onChange={(e) => setStage(e.target.value as FailureStage)}
              >
                {Object.entries(STAGES).map(([key, name]) => (
                  <option key={key} value={key}>
                    {name}
                  </option>
                ))}
              </select>
            </label>
          )}
        </div>
      </div>
      }
      {!demo && <div className="demo-note"><Icon name="info" size={17} /><div><strong>사이트별 기준으로 개발 공고를 판별해요</strong><p>실제 사이트를 열어 본문과 캡처를 저장해요. 명확하지 않은 공고는 제외하고, 일부 실패해도 성공한 결과는 보관해요.</p></div></div>}
      <div className="modal-actions">
        <button className="button secondary" onClick={onClose}>
          취소
        </button>
        <button
          className="button primary"
          disabled={!selected.length}
          onClick={() => onStart(selected, failure ? stage : null)}
        >
          <Icon name="refresh" size={16} />
          {selected.length}개 기업군 {demo ? '수집 미리보기' : '수집 시작'}
        </button>
      </div>
    </Modal>
  )
}
