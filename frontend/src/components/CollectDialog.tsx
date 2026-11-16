import { api } from '../api'
import { useEffect, useState } from 'react'
import { SOURCES, STAGES } from '../data'
import type { Category, Company, FailureStage, SourceId } from '../types'
import { BrandMark } from './BrandMark'
import { Icon } from './Icon'
import { Modal } from './Modal'

interface Preset { id: string; name: string; categoryIds: string[]; companyIds: string[] }
export function CollectDialog({ onClose, onStart, demo, availableSources, categories, companies, initialCategoryId }: {
  onClose: () => void
  onStart: (ids: SourceId[], failure: FailureStage | null, categoryId: string, companyIds: string[], categoryIds?: string[]) => void
  demo: boolean
  availableSources: SourceId[]
  categories: Category[]
  companies: Company[]
  initialCategoryId: string
}) {
  const [presets, setPresets] = useState<Preset[]>([])
  const [presetName, setPresetName] = useState('')
  const [editing, setEditing] = useState<string | null>(null)
  const [presetError, setPresetError] = useState('')
  const [presetBusy, setPresetBusy] = useState(false)
  const [presetNotice, setPresetNotice] = useState('')
  useEffect(() => { if (!demo) void api<Preset[]>('/api/collection-presets').then(setPresets).catch(e => setPresetError(e.message)) }, [demo])
  const [step, setStep] = useState(1)
  const [categoryId, setCategoryId] = useState(initialCategoryId)
  const [scope, setScope] = useState<string[]>([initialCategoryId])
  const supported = companies.filter(company => availableSources.includes(company.sourceId) && scope.some(id => company.supportedCategoryIds.includes(id)))
  const [selected, setSelected] = useState<string[]>(() => supported.map(company => company.id))
  const [failure, setFailure] = useState(false)
  const [stage, setStage] = useState<FailureStage>('capture')
  const selectedCompanies = supported.filter(company => selected.includes(company.id))
  const allSelected = supported.length > 0 && selectedCompanies.length === supported.length
  const category = categories.find(item => item.id === categoryId)
  function chooseCategory(id: string) {
    setCategoryId(id)
    setScope([id])
    setSelected(companies.filter(company => availableSources.includes(company.sourceId) && company.supportedCategoryIds.includes(id)).map(company => company.id))
  }
  function toggle(ids: string[]) {
    setSelected(current => ids.every(id => current.includes(id)) ? current.filter(id => !ids.includes(id)) : [...new Set([...current, ...ids])])
  }
  return (
    <Modal onClose={onClose} labelId="collect-title" className="collect-dialog">
      <div className="collect-dialog-header">
      <div className="modal-heading">
        <div className="modal-symbol"><Icon name="layers" size={24} /></div>
        <span className="collection-step">{step} / 2 · {step === 1 ? '분야 선택' : '기업 선택'}</span>
        <button className="icon-button" onClick={onClose} aria-label="수집 창 닫기"><Icon name="close" /></button>
      </div>
      <h2 id="collect-title">{step === 1 ? '어떤 공고를 모아볼까요?' : scope.length > 1 ? `${scope.length}개 직군 수집` : `${category?.name ?? '공고'} 수집`}</h2>
      <p className="modal-description">{step === 1 ? '관심 분야를 고르면, 그 분야의 수집을 지원하는 기업을 보여드려요.' : '기업군을 펼쳐 계열사를 선택하세요. 선택한 분야의 수집을 지원하는 기업만 표시해요.'}</p>
      </div>
      <div className="collect-dialog-body">
      {!demo && step === 1 && <section className="preset-list"><h3>저장한 수집 프리셋</h3>{!presets.length && <p>기업 선택 단계에서 자주 쓰는 조합을 저장해보세요.</p>}{presets.map(preset => <div key={preset.id}><span><strong>{preset.name}</strong><small>{preset.categoryIds.map(id => categories.find(c => c.id === id)?.name ?? id).join(' · ')} · {preset.companyIds.length}개 기업</small></span><button className="button secondary small" disabled={presetBusy} onClick={() => { setCategoryId(preset.categoryIds[0]); setScope(preset.categoryIds); setSelected(preset.companyIds); setEditing(preset.id); setPresetName(preset.name); setStep(2) }}>편집</button><button className="text-button" disabled={presetBusy} onClick={async () => { setPresetBusy(true); setPresetError(''); try { await api(`/api/collection-presets/${preset.id}`, { method: 'DELETE' }); setPresets(items => items.filter(p => p.id !== preset.id)) } catch (e) { setPresetError(e instanceof Error ? e.message : '삭제 실패') } finally { setPresetBusy(false) } }}>삭제</button><button className="button primary small" disabled={presetBusy} onClick={() => onStart([...new Set(companies.filter(c => preset.companyIds.includes(c.id)).map(c => c.sourceId))], null, preset.categoryIds[0], preset.companyIds, preset.categoryIds)}>바로 수집</button></div>)}</section>}
      {presetError && <p className="workspace-warning" role="alert">{presetError}</p>}{presetNotice && <p role="status">{presetNotice}</p>}
      {step === 1 ? (
        <div className="category-choices">
          {categories.map(item => (
            <label key={item.id} className={`category-choice ${categoryId === item.id ? 'selected' : ''}`}>
              <input type="radio" name="collection-category" value={item.id} checked={categoryId === item.id} onChange={() => chooseCategory(item.id)} />
              <span><strong>{item.name}</strong><small>{!companies.some(company => company.supportedCategoryIds.includes(item.id)) && '조회·자동 분류 지원 · '}{item.description ?? '해당 분야의 수집을 지원하는 기업에서 공고를 모아요.'}</small></span>
              <span className="category-support-count">{companies.filter(company => availableSources.includes(company.sourceId) && company.supportedCategoryIds.includes(item.id)).length}개 기업</span>
            </label>
          ))}
        </div>
      ) : (
        <>
          {!demo && <details className="preset-scope"><summary>함께 수집할 직군 · {scope.length}개 선택</summary><p>선택한 직군 중 하나라도 일치하는 공고를 한 번만 수집해요. ‘데이터 직군 전체’ 같은 프리셋도 만들 수 있어요.</p><div>{categories.map(item => <label key={item.id}><input type="checkbox" checked={scope.includes(item.id)} disabled={!companies.some(c => availableSources.includes(c.sourceId) && c.supportedCategoryIds.includes(item.id))} onChange={e => { const next = e.target.checked ? item.id === 'all' ? ['all'] : [...scope.filter(id => id !== 'all'), item.id] : scope.filter(id => id !== item.id); setScope(next); setCategoryId(next[0] ?? 'all') }} />{item.name}</label>)}</div></details>}
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
                <details className="company-group" key={source.id}>
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
            {!supported.length && <p className="company-empty">이 직군은 전체 공고 수집 후 자동 분류로 확인할 수 있어요. 직군별 수집을 지원하는 기업은 아직 없어요.</p>}
          </div>
          {!demo && <div className="preset-save"><label>이 선택을 프리셋으로 저장<input aria-label="수집 프리셋 이름" maxLength={80} placeholder="예: 백엔드 관심 기업" value={presetName} onChange={e => setPresetName(e.target.value)} /></label><button className="button secondary small" disabled={presetBusy || !presetName.trim() || !selectedCompanies.length || !scope.every(id => selectedCompanies.some(c => c.supportedCategoryIds.includes(id)))} onClick={async () => { setPresetBusy(true); setPresetError(''); try { const saved = await api<Preset>(`/api/collection-presets${editing ? `/${editing}` : ''}`, { method: editing ? 'PUT' : 'POST', body: JSON.stringify({ name: presetName.trim(), categoryIds: scope, companyIds: selectedCompanies.map(c => c.id) }) }); setPresets(items => [saved, ...items.filter(p => p.id !== saved.id)]); setEditing(saved.id); setPresetNotice('프리셋을 저장했어요.') } catch (e) { setPresetError(e instanceof Error ? e.message : '저장 실패') } finally { setPresetBusy(false) } }}>{editing ? '프리셋 수정 저장' : '프리셋 저장'}</button>{editing && <button className="text-button" onClick={() => { setEditing(null); setPresetName(''); setPresetNotice('') }}>새 프리셋으로 저장</button>}</div>}
          {scope.some(id => !selectedCompanies.some(c => c.supportedCategoryIds.includes(id))) && <p className="workspace-warning">선택한 각 직군의 수집을 지원하는 회사를 한 곳 이상 선택하세요.</p>}
          <div className="collection-rule"><Icon name="check" size={16} /><span>지원 여부는 수집 기능 기준이에요. 현재 공고가 없으면 0건으로 기록해요.</span></div>
          <div className="demo-note"><Icon name="info" size={17} /><div><strong>{demo ? '화면 검토용 샘플 수집이에요' : '같은 공고는 함께 관리해요'}</strong><p>{demo ? '실제 사이트에 접속하지 않고 샘플 공고를 추가해요.' : '저장할 때 전체 직군 규칙을 적용해요. 내용이 같으면 캡처와 본문을 중복 저장하지 않아요.'}</p>
            {demo && <><label className="failure-option"><input type="checkbox" checked={failure} onChange={event => setFailure(event.target.checked)} />일부 수집 실패 상황도 보기</label>{failure && <label className="failure-stage-select">실패 단계<select value={stage} onChange={event => setStage(event.target.value as FailureStage)}>{Object.entries(STAGES).map(([key, name]) => <option key={key} value={key}>{name}</option>)}</select></label>}</>}
          </div></div>
        </>
      )}
      </div>
      <div className="modal-actions">
        <button className="button secondary" onClick={step === 1 ? onClose : () => setStep(1)}>{step === 1 ? '취소' : '이전'}</button>
        {step === 1 ? <button className="button primary" disabled={!supported.length} onClick={() => setStep(2)}>기업 선택<Icon name="chevron" size={14} /></button> : <button className="button primary" disabled={!selectedCompanies.length || !scope.every(id => selectedCompanies.some(c => c.supportedCategoryIds.includes(id)))} onClick={() => onStart([...new Set(selectedCompanies.map(company => company.sourceId))], failure ? stage : null, categoryId, selectedCompanies.map(company => company.id), scope)}><Icon name="refresh" size={16} />{selectedCompanies.length}개 기업 {demo ? '수집 미리보기' : '수집 시작'}</button>}
      </div>
    </Modal>
  )
}
