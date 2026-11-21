import { useCallback, useEffect, useState } from 'react'
import { api } from '../api'
import type { Category } from '../types'
import './CareerAssistant.css'

type TaskStatus = 'WAITING_AI' | 'ANALYZING' | 'COMPLETED' | 'FAILED'
type TaskType = 'RECOMMENDATIONS' | 'POSTING_DRAFT'
interface FactEvidence { locationLabel: string; excerpt: string }
interface FactView { id: string; title: string; evidence: FactEvidence[] }
interface RecommendedPosting {
  id: number; company: string; title: string; location: string; url: string; categories: string[]; firstSeen: string
}
interface Recommendation {
  posting: RecommendedPosting
  fit: 'STRONG_MATCH' | 'POTENTIAL' | 'INSUFFICIENT_INFO'
  explanation: string
  matchingPoints: string[]
  gaps: string[]
  evidence: FactView[]
}
interface RecommendationResult { recommendations: Recommendation[] }
interface DraftSection { heading: string; content: string; evidence: FactView[] }
interface PortfolioProject { title: string; selectionReason: string; summary: string; evidence: FactView[] }
interface PostingDraft {
  posting: RecommendedPosting
  resumeHeadline: string
  resumeHeadlineEvidence: FactView[]
  resumeSummary: string
  resumeSummaryEvidence: FactView[]
  resumeSections: DraftSection[]
  portfolioProjects: PortfolioProject[]
  informationGaps: string[]
}
interface TaskView {
  id: string
  type: TaskType
  parentTaskId: string | null
  postingId: number | null
  status: TaskStatus
  errorMessage: string | null
  attempts: number
  createdAt: string
  updatedAt: string
  result: RecommendationResult | PostingDraft | null
  verifiedFactCount: number
  factsTruncated: boolean
  candidateCount: number
  candidatesTruncated: boolean
}
interface Readiness { verifiedFactCount: number; openPostingCount: number }
interface Preferences { count: number; categoryIds: string[]; companyKeywords: string; locationKeywords: string }

const STORAGE_KEY = 'jobmoa.career-assistant.preferences.v1'
const SELECTED_RECOMMENDATION_KEY = 'jobmoa.career-assistant.selected-recommendation.v1'
const DEFAULT_PREFERENCES: Preferences = { count: 10, categoryIds: [], companyKeywords: '', locationKeywords: '' }
const FIT_LABEL: Record<Recommendation['fit'], string> = {
  STRONG_MATCH: '잘 맞음', POTENTIAL: '검토해볼 만함', INSUFFICIENT_INFO: '자료가 부족함',
}
const TASK_LABEL: Record<TaskStatus, string> = {
  WAITING_AI: '분석 대기', ANALYZING: '분석 중', COMPLETED: '완료', FAILED: '실패',
}

function loadPreferences(): Preferences {
  try {
    const value = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}') as Partial<Preferences>
    return {
      count: [5, 10, 15, 20].includes(value.count ?? 0) ? value.count! : DEFAULT_PREFERENCES.count,
      categoryIds: Array.isArray(value.categoryIds) ? value.categoryIds.filter(id => typeof id === 'string') : [],
      companyKeywords: typeof value.companyKeywords === 'string' ? value.companyKeywords : '',
      locationKeywords: typeof value.locationKeywords === 'string' ? value.locationKeywords : '',
    }
  } catch { return DEFAULT_PREFERENCES }
}

export function CareerAssistant({
  categories, workerConnected, onBackToMaterials, onOpenPosting,
}: {
  categories: Category[]
  workerConnected: boolean
  onBackToMaterials: () => void
  onOpenPosting: (postingId: number) => void
}) {
  const [preferences, setPreferences] = useState<Preferences>(loadPreferences)
  const [readiness, setReadiness] = useState<Readiness | null>(null)
  const [recommendations, setRecommendations] = useState<TaskView[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(() => localStorage.getItem(SELECTED_RECOMMENDATION_KEY))
  const [drafts, setDrafts] = useState<TaskView[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [copiedId, setCopiedId] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    try {
      const [list, status] = await Promise.all([
        api<TaskView[]>('/api/career-assistant/recommendations'),
        api<Readiness>('/api/career-assistant/readiness'),
      ])
      setRecommendations(list)
      setReadiness(status)
      setSelectedId(current => current && list.some(task => task.id === current) ? current : list[0]?.id ?? null)
      setError('')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '추천 작업을 불러오지 못했습니다.')
    }
  }, [])

  const refreshDrafts = useCallback(async (recommendationId: string) => {
    try { setDrafts(await api<TaskView[]>(`/api/career-assistant/recommendations/${recommendationId}/drafts`)) }
    catch (cause) { setError(cause instanceof Error ? cause.message : '맞춤 자료를 불러오지 못했습니다.') }
  }, [])

  useEffect(() => {
    void refresh()
    return () => { /* component unmount keeps queued tasks alive on the server */ }
  }, [refresh])

  useEffect(() => { localStorage.setItem(STORAGE_KEY, JSON.stringify(preferences)) }, [preferences])
  useEffect(() => {
    if (selectedId) localStorage.setItem(SELECTED_RECOMMENDATION_KEY, selectedId)
    else localStorage.removeItem(SELECTED_RECOMMENDATION_KEY)
  }, [selectedId])

  useEffect(() => {
    setDrafts([])
    if (selectedId) void refreshDrafts(selectedId)
  }, [selectedId, refreshDrafts])

  const hasPending = recommendations.some(task => task.status === 'WAITING_AI' || task.status === 'ANALYZING') ||
    drafts.some(task => task.status === 'WAITING_AI' || task.status === 'ANALYZING')
  useEffect(() => {
    if (!hasPending) return
    const timer = window.setInterval(() => {
      void refresh()
      if (selectedId) void refreshDrafts(selectedId)
    }, 2500)
    return () => window.clearInterval(timer)
  }, [hasPending, refresh, refreshDrafts, selectedId])

  const changePreferences = (next: Partial<Preferences>) => setPreferences(value => ({ ...value, ...next }))

  const requestRecommendations = async () => {
    setBusy(true); setError(''); setNotice('')
    try {
      const task = await api<TaskView>('/api/career-assistant/recommendations', {
        method: 'POST',
        body: JSON.stringify({
          count: preferences.count,
          categoryIds: preferences.categoryIds,
          companyKeywords: preferences.companyKeywords,
          locationKeywords: preferences.locationKeywords,
        }),
      })
      setRecommendations(items => [task, ...items.filter(item => item.id !== task.id)])
      setSelectedId(task.id)
      setNotice(workerConnected ? '추천 작업을 대기열에 등록했어요.' : '워커가 연결되면 추천 작업을 처리해요. 작업은 저장되어 있어요.')
    } catch (cause) { setError(cause instanceof Error ? cause.message : '추천 작업을 시작하지 못했습니다.') }
    finally { setBusy(false) }
  }

  const createDraft = async (recommendationId: string, postingId: number) => {
    setBusy(true); setError('')
    try {
      const task = await api<TaskView>(`/api/career-assistant/recommendations/${recommendationId}/drafts`, {
        method: 'POST', body: JSON.stringify({ postingId }),
      })
      setDrafts(items => [task, ...items.filter(item => item.id !== task.id)])
      setNotice(workerConnected ? '공고별 맞춤 초안을 대기열에 등록했어요.' : '워커가 연결되면 맞춤 초안을 만들어요. 작업은 저장되어 있어요.')
    } catch (cause) { setError(cause instanceof Error ? cause.message : '맞춤 초안을 시작하지 못했습니다.') }
    finally { setBusy(false) }
  }

  const retry = async (taskId: string) => {
    setBusy(true); setError('')
    try {
      const task = await api<TaskView>(`/api/career-assistant/tasks/${taskId}/retry`, { method: 'POST' })
      if (task.type === 'RECOMMENDATIONS') setRecommendations(items => items.map(item => item.id === task.id ? task : item))
      else setDrafts(items => items.map(item => item.id === task.id ? task : item))
    } catch (cause) { setError(cause instanceof Error ? cause.message : '작업을 다시 시작하지 못했습니다.') }
    finally { setBusy(false) }
  }

  const copyDraft = async (taskId: string, draft: PostingDraft) => {
    const text = [
      draft.resumeHeadline,
      draft.resumeSummary,
      ...draft.resumeSections.flatMap(section => [section.heading, section.content]),
      ...draft.portfolioProjects.flatMap(project => [project.title, project.summary, project.selectionReason]),
    ].filter(Boolean).join('\n\n')
    try {
      await navigator.clipboard.writeText(text)
      setCopiedId(taskId); window.setTimeout(() => setCopiedId(current => current === taskId ? null : current), 1800)
    } catch { setError('클립보드에 복사하지 못했습니다. 초안 내용을 선택해 복사하세요.') }
  }

  const selected = recommendations.find(task => task.id === selectedId) ?? null
  const result = selected?.status === 'COMPLETED' ? selected.result as RecommendationResult | null : null
  const roleCategories = categories.filter(category => category.id !== 'all')

  return <section className="career-assistant" aria-label="추천 공고와 맞춤 이력서·포트폴리오">
    {error && <div className="career-alert" role="alert">{error}</div>}
    {notice && <div className="career-notice" role="status">{notice}</div>}
    {readiness?.verifiedFactCount === 0 ? <div className="career-plan-prerequisite">
      <span className="career-plan-icon">01</span>
      <div><strong>먼저 경력 후보를 확인해 주세요</strong><p>AI가 읽은 후보를 수정하고 ‘확정’한 정보만 추천과 맞춤 초안에 사용해요. 확인 전 후보는 추천에 전달하지 않습니다.</p></div>
      <button className="button secondary" onClick={onBackToMaterials}>경력 자료에서 확인</button>
    </div> : <>
      <section className="career-plan-settings">
        <div className="career-plan-heading"><div><span className="career-plan-eyebrow">추천 기준</span><h2>다음에 지원할 곳을 찾아볼까요?</h2><p>{readiness?.verifiedFactCount ?? '—'}개의 확정 경력 정보와 현재 {readiness?.openPostingCount ?? '—'}개의 열린 공고를 바탕으로 추천해요.</p></div>
          <label className="career-count">추천 개수<select value={preferences.count} onChange={event => changePreferences({ count: Number(event.target.value) })}>{[5, 10, 15, 20].map(count => <option key={count} value={count}>{count}개</option>)}</select></label>
        </div>
        <fieldset className="career-category-filters"><legend>희망 직군 <span>비우면 전체 직군에서 찾아요</span></legend>
          <button type="button" className={`career-all-categories ${preferences.categoryIds.length === 0 ? 'active' : ''}`} onClick={() => changePreferences({ categoryIds: [] })}>전체 직군</button>
          <div>{roleCategories.map(category => <label key={category.id}><input type="checkbox" checked={preferences.categoryIds.includes(category.id)} onChange={event => changePreferences({ categoryIds: event.target.checked ? [...new Set([...preferences.categoryIds, category.id])] : preferences.categoryIds.filter(id => id !== category.id) })} />{category.name}</label>)}</div>
        </fieldset>
        <div className="career-keyword-filters">
          <label>관심 기업<input value={preferences.companyKeywords} maxLength={200} placeholder="예: 토스, 카카오 (쉼표로 구분)" onChange={event => changePreferences({ companyKeywords: event.target.value })} /></label>
          <label>근무지<input value={preferences.locationKeywords} maxLength={200} placeholder="예: 서울, 판교 (쉼표로 구분)" onChange={event => changePreferences({ locationKeywords: event.target.value })} /></label>
        </div>
        <div className="career-plan-actions"><p>마감 공고는 제외해요. 추천은 ‘잘 맞음 / 검토해볼 만함 / 자료가 부족함’으로 보여주고, 이유와 경력 근거를 함께 붙입니다.</p>
          <button className="button primary" disabled={busy || !readiness || readiness.verifiedFactCount === 0 || readiness.openPostingCount === 0} onClick={() => void requestRecommendations()}>{busy ? '작업 등록 중…' : '추천 공고 찾기'}</button>
        </div>
        {!workerConnected && <div className="career-plan-worker"><strong>Codex 워커가 연결되지 않았어요.</strong><span>요청은 대기열에 저장되며 워커를 켜면 자동으로 처리됩니다.</span></div>}
      </section>

      {recommendations.length > 0 && <section className="career-recommendation-results">
        <header className="career-plan-list-heading"><div><h2>추천 결과</h2><span>최근 {recommendations.length}회</span></div>
          {recommendations.length > 1 && <label>결과<select value={selectedId ?? ''} onChange={event => setSelectedId(event.target.value)}>{recommendations.map(task => <option key={task.id} value={task.id}>{new Date(task.createdAt).toLocaleString('ko-KR')} · {TASK_LABEL[task.status]}</option>)}</select></label>}
        </header>
        {selected && selected.status !== 'COMPLETED' && <TaskStatusCard task={selected} workerConnected={workerConnected} busy={busy} onRetry={() => void retry(selected.id)} />}
        {selected?.factsTruncated && <p className="career-context-note">확정된 경력 정보 {selected.verifiedFactCount}개 중 최근 80개를 추천에 사용했어요.</p>}
        {selected?.candidatesTruncated && <p className="career-context-note">본문이 저장된 조건 일치 공고 {selected.candidateCount}개 중 경력과 제목·본문의 연관도가 높은 최대 36개를 비교했어요.</p>}
        {result && (result.recommendations.length ? <div className="career-recommendation-list">{result.recommendations.map(item => <RecommendationCard key={item.posting.id} item={item} recommendationId={selected?.id ?? ''} draft={drafts.find(task => task.postingId === item.posting.id) ?? null} busy={busy} copiedId={copiedId} workerConnected={workerConnected} onOpenPosting={onOpenPosting} onCreateDraft={createDraft} onRetry={retry} onCopy={copyDraft} />)}</div> : <div className="career-plan-empty"><strong>근거를 들어 추천할 공고가 없어요.</strong><span>다른 직군·기업·근무지 조건을 선택해 다시 찾아보세요.</span></div>)}
      </section>}
    </>}
  </section>
}

function TaskStatusCard({ task, workerConnected, busy, onRetry }: { task: TaskView; workerConnected: boolean; busy: boolean; onRetry: () => void }) {
  return <div className={`career-task-status status-${task.status.toLowerCase()}`} role="status">
    <span className={task.status === 'ANALYZING' ? 'career-spinner' : 'career-worker-dot'} />
    <div><strong>{task.status === 'WAITING_AI' && !workerConnected ? '워커가 연결되면 처리해요' : TASK_LABEL[task.status]}</strong>
      {task.errorMessage && <p>{task.errorMessage}</p>}
      {task.status === 'FAILED' && <button className="button secondary small" disabled={busy} onClick={onRetry}>다시 시도</button>}
    </div>
  </div>
}

function RecommendationCard({ item, recommendationId, draft, busy, copiedId, workerConnected, onOpenPosting, onCreateDraft, onRetry, onCopy }: {
  item: Recommendation; recommendationId: string; draft: TaskView | null; busy: boolean; copiedId: string | null; workerConnected: boolean
  onOpenPosting: (postingId: number) => void
  onCreateDraft: (recommendationId: string, postingId: number) => Promise<void>
  onRetry: (taskId: string) => Promise<void>
  onCopy: (taskId: string, draft: PostingDraft) => Promise<void>
}) {
  const readyDraft = draft?.status === 'COMPLETED' ? draft.result as PostingDraft : null
  return <article className="career-recommendation-card">
    <div className="career-recommendation-title"><div><span className={`career-fit fit-${item.fit.toLowerCase()}`}>{FIT_LABEL[item.fit]}</span><small>{item.posting.company} · {item.posting.location || '근무지 미기재'}</small><h3>{item.posting.title}</h3></div>
      <div className="career-recommendation-actions"><button className="button secondary small" onClick={() => onOpenPosting(item.posting.id)}>공고 상세</button><a className="career-original-link" href={item.posting.url} target="_blank" rel="noreferrer">원문</a></div>
    </div>
    <p className="career-recommendation-explanation">{item.explanation}</p>
    <div className="career-recommendation-columns">
      <section><h4>맞는 지점</h4>{item.matchingPoints.length ? <ul>{item.matchingPoints.map((point, index) => <li key={index}>{point}</li>)}</ul> : <p>뚜렷한 공통 항목이 없어요.</p>}</section>
      <section><h4>더 확인할 점</h4>{item.gaps.length ? <ul>{item.gaps.map((gap, index) => <li key={index}>{gap}</li>)}</ul> : <p>확정 경력 정보에서 눈에 띄는 공백은 없어요.</p>}</section>
    </div>
    {!!item.evidence.length && <div className="career-recommendation-evidence"><h4>연결된 경력 근거</h4>{item.evidence.map(fact => <div key={fact.id}><strong>{fact.title}</strong>{fact.evidence.slice(0, 2).map((source, index) => <blockquote key={index}><span>{source.locationLabel}</span>{source.excerpt}</blockquote>)}</div>)}</div>}
    <div className="career-draft-launch"><div><strong>이 공고에 맞춘 이력서·포트폴리오 구성</strong><span>새 내용을 지어내지 않고 확정된 경력 정보만 재배치·표현해요.</span></div>
      {!draft && <button className="button primary small" disabled={busy} onClick={() => void onCreateDraft(recommendationId, item.posting.id)}>맞춤 초안 만들기</button>}
      {draft && <span className={`career-status status-${draft.status.toLowerCase()}`}>{TASK_LABEL[draft.status]}</span>}
    </div>
    {draft && draft.status !== 'COMPLETED' && <TaskStatusCard task={draft} workerConnected={workerConnected} busy={busy} onRetry={() => void onRetry(draft.id)} />}
    {draft && readyDraft && <div className="career-draft-result">
      <div className="career-draft-result-heading"><h4>공고별 초안</h4><button className="button secondary small" onClick={() => void onCopy(draft.id, readyDraft)}>{copiedId === draft.id ? '복사했어요' : '초안 복사'}</button></div>
      {draft.factsTruncated && <p className="career-context-note">확정된 경력 정보 {draft.verifiedFactCount}개 중 최근 80개를 사용했어요.</p>}
      {readyDraft.resumeHeadline && <section><h5>이력서 한 줄 소개</h5><p>{readyDraft.resumeHeadline}</p><Evidence facts={readyDraft.resumeHeadlineEvidence} /></section>}
      {readyDraft.resumeSummary && <section><h5>이력서 요약</h5><p>{readyDraft.resumeSummary}</p><Evidence facts={readyDraft.resumeSummaryEvidence} /></section>}
      {!!readyDraft.resumeSections.length && <section><h5>이력서 구성</h5>{readyDraft.resumeSections.map((part, index) => <div className="career-draft-section" key={index}><strong>{part.heading}</strong><p>{part.content}</p><Evidence facts={part.evidence} /></div>)}</section>}
      {!!readyDraft.portfolioProjects.length && <section><h5>포트폴리오 프로젝트 순서</h5>{readyDraft.portfolioProjects.map((project, index) => <div className="career-portfolio-project" key={index}><span>{index + 1}</span><div><strong>{project.title}</strong><p>{project.selectionReason}</p><p>{project.summary}</p><Evidence facts={project.evidence} /></div></div>)}</section>}
      {!readyDraft.resumeSections.length && !readyDraft.portfolioProjects.length && !readyDraft.resumeSummary && <p className="career-context-note">확정 자료로 뒷받침할 초안을 구성하지 못했어요. 아래 추가 확인 항목을 살펴보세요.</p>}
      {!!readyDraft.informationGaps.length && <section className="career-draft-gaps"><h5>초안에 넣기 전 확인할 정보</h5><ul>{readyDraft.informationGaps.map((gap, index) => <li key={index}>{gap}</li>)}</ul></section>}
      <p className="career-draft-disclaimer">AI 초안입니다. 제출 전 원문 근거와 표현을 직접 확인해 주세요. 이력서 파일이나 포트폴리오 문서는 자동으로 덮어쓰지 않습니다.</p>
    </div>}
  </article>
}

function Evidence({ facts }: { facts: FactView[] }) {
  if (!facts.length) return null
  return <div className="career-draft-evidence">{facts.map(fact => <div key={fact.id}><strong>{fact.title}</strong>{fact.evidence.slice(0, 1).map((source, index) => <blockquote key={index}>{source.excerpt}<span>{source.locationLabel}</span></blockquote>)}</div>)}</div>
}
