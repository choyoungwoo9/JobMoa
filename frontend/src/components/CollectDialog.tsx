import { useState } from 'react'
import { SOURCES, STAGES } from '../data'
import type { Category, Company, FailureStage, SourceId } from '../types'
import { BrandMark } from './BrandMark'
import { Icon } from './Icon'
import { Modal } from './Modal'

export function CollectDialog({ onClose, onStart, demo, availableSources, categories, companies, initialCategoryId }: {
  onClose: () => void
  onStart: (ids: SourceId[], failure: FailureStage | null, categoryId: string, companyIds: string[]) => void
  demo: boolean
  availableSources: SourceId[]
  categories: Category[]
  companies: Company[]
  initialCategoryId: string
}) {
  const [step, setStep] = useState(1)
  const [categoryId, setCategoryId] = useState(initialCategoryId)
  const supported = companies.filter(company => availableSources.includes(company.sourceId) && company.supportedCategoryIds.includes(categoryId))
  const [selected, setSelected] = useState<string[]>(() => supported.map(company => company.id))
  const [failure, setFailure] = useState(false)
  const [stage, setStage] = useState<FailureStage>('capture')
  const selectedCompanies = supported.filter(company => selected.includes(company.id))
  const allSelected = supported.length > 0 && selectedCompanies.length === supported.length
  const category = categories.find(item => item.id === categoryId)
  function chooseCategory(id: string) {
    setCategoryId(id)
    setSelected(companies.filter(company => availableSources.includes(company.sourceId) && company.supportedCategoryIds.includes(id)).map(company => company.id))
  }
  function toggle(ids: string[]) {
    setSelected(current => ids.every(id => current.includes(id)) ? current.filter(id => !ids.includes(id)) : [...new Set([...current, ...ids])])
  }
  return (
    <Modal onClose={onClose} labelId="collect-title" className="collect-dialog">
      <div className="modal-heading">
        <div className="modal-symbol"><Icon name="layers" size={24} /></div>
        <span className="collection-step">{step} / 2 · {step === 1 ? '분야 선택' : '기업 선택'}</span>
        <button className="icon-button" onClick={onClose} aria-label="수집 창 닫기"><Icon name="close" /></button>
      </div>
      <h2 id="collect-title">{step === 1 ? '어떤 공고를 모아볼까요?' : `${category?.name ?? '공고'} 수집`}</h2>
      <p className="modal-description">{step === 1 ? '관심 분야를 고르면, 그 분야의 수집을 지원하는 기업을 보여드려요.' : '기업군을 펼쳐 계열사를 선택하세요. 선택한 분야의 수집을 지원하는 기업만 표시해요.'}</p>
      {step === 1 ? (
        <div className="category-choices">
          {categories.map(item => (
            <label key={item.id} className={`category-choice ${categoryId === item.id ? 'selected' : ''}`}>
              <input type="radio" name="collection-category" value={item.id} checked={categoryId === item.id} onChange={() => chooseCategory(item.id)} />
              <span><strong>{item.name}</strong><small>{item.id === 'web-backend' ? '서비스 API·서버 로직 개발 · 풀스택 제외' : '현재 지원하는 개발 분야의 공고를 함께 수집'}</small></span>
              <span className="category-support-count">{companies.filter(company => availableSources.includes(company.sourceId) && company.supportedCategoryIds.includes(item.id)).length}개 기업</span>
            </label>
          ))}
        </div>
      ) : (
        <>
          <div className="source-choice-heading">
            <strong>수집할 기업 <span>{selectedCompanies.length} / {supported.length}</span></strong>
            <button className="text-button" onClick={() => setSelected(allSelected ? [] : supported.map(company => company.id))}>{allSelected ? '전체 해제' : '전체 선택'}</button>
          </div>
          <div className="company-groups">
            {SOURCES.map(source => {
              const members = supported.filter(company => company.sourceId === source.id)
              if (!members.length) return null
              const count = members.filter(company => selected.includes(company.id)).length
              return (
                <details className="company-group" key={source.id} open>
                  <summary>
                    <input type="checkbox" checked={count === members.length} ref={element => { if (element) element.indeterminate = count > 0 && count < members.length }} aria-label={`${source.name} 기업군 전체 선택`} onClick={event => event.stopPropagation()} onChange={() => toggle(members.map(company => company.id))} />
                    <BrandMark sourceId={source.id} small /><strong>{source.name}</strong><span>{count} / {members.length}</span><Icon name="down" size={14} />
                  </summary>
                  <div className="company-members">
                    {members.map(company => (
                      <label key={company.id} className={selected.includes(company.id) ? 'selected' : ''}>
                        <input type="checkbox" checked={selected.includes(company.id)} onChange={() => toggle([company.id])} /><span>{company.name}</span>
                      </label>
                    ))}
                  </div>
                </details>
              )
            })}
            {!supported.length && <p className="company-empty">이 분야의 수집을 지원하는 기업이 아직 없어요.</p>}
          </div>
          <div className="collection-rule"><Icon name="check" size={16} /><span>지원 여부는 수집 기능 기준이에요. 현재 공고가 없으면 0건으로 기록해요.</span></div>
          <div className="demo-note"><Icon name="info" size={17} /><div><strong>{demo ? '화면 검토용 샘플 수집이에요' : '같은 공고는 함께 관리해요'}</strong><p>{demo ? '실제 사이트에 접속하지 않고 샘플 공고를 추가해요.' : '이미 저장한 공고는 분야를 추가하고, 내용이 같으면 캡처와 본문을 중복 저장하지 않아요.'}</p>
            {demo && <><label className="failure-option"><input type="checkbox" checked={failure} onChange={event => setFailure(event.target.checked)} />일부 수집 실패 상황도 보기</label>{failure && <label className="failure-stage-select">실패 단계<select value={stage} onChange={event => setStage(event.target.value as FailureStage)}>{Object.entries(STAGES).map(([key, name]) => <option key={key} value={key}>{name}</option>)}</select></label>}</>}
          </div></div>
        </>
      )}
      <div className="modal-actions">
        <button className="button secondary" onClick={step === 1 ? onClose : () => setStep(1)}>{step === 1 ? '취소' : '이전'}</button>
        {step === 1 ? <button className="button primary" disabled={!supported.length} onClick={() => setStep(2)}>기업 선택<Icon name="chevron" size={14} /></button> : <button className="button primary" disabled={!selectedCompanies.length} onClick={() => onStart([...new Set(selectedCompanies.map(company => company.sourceId))], failure ? stage : null, categoryId, selectedCompanies.map(company => company.id))}><Icon name="refresh" size={16} />{selectedCompanies.length}개 기업 {demo ? '수집 미리보기' : '수집 시작'}</button>}
      </div>
    </Modal>
  )
}
