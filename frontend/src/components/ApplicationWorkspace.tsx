import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api } from '../api'
import { dateLabel, sourceById } from '../data'
import type { Job } from '../types'
import { BrandMark } from './BrandMark'
import { Icon } from './Icon'
import { ApplicationRequirementsPanel } from './ApplicationRequirementsPanel'

type CaseStatus = 'PREPARING' | 'APPLIED'
type TaskStatus = 'WAITING_AI' | 'ANALYZING' | 'COMPLETED' | 'FAILED'
type TaskType = 'MATERIAL_DRAFT' | 'EXTRACT_QUESTIONS' | 'DRAFT_ANSWER' | 'INTERVIEW_RESEARCH' | 'INTERVIEW_FOLLOW_UP'
type ApplicationCase = {
  id: string; postingId: number; sourceId: string; company: string; title: string; location: string; url: string
  firstSeen: string; closed: boolean; status: CaseStatus; startedAt: string; appliedAt: string | null; updatedAt: string
}
type Task = { id: string; applicationId: string; type: TaskType; status: TaskStatus; errorMessage: string | null; progressMessage: string | null; attempts: number; createdAt: string; updatedAt: string; result: any }
type Question = { id: string; position: number; prompt: string; characterLimit: number | null; sourceRequirementId: string | null; sourceQuote: string | null; answerDraft: string; factIds: string[]; updatedAt: string }
type Turn = { id: string; sessionId: string; position: number; basis: string; question: string; answer: string; followUpQuestion: string | null; followUpAnswer: string; proposedTitle: string | null; proposedSummary: string | null; sourceExcerpt: string | null; promotedMaterialId: string | null; createdAt: string; updatedAt: string }
type Session = { session: { id: string; applicationId: string; status: 'ACTIVE' | 'COMPLETED'; nextQuestionIndex: number }; turns: Turn[] }
type Detail = {
  application: ApplicationCase; latestRequirementActivity: any; requirement: any; questions: Question[]; materials: any; materialsVersion: number | null
  materialsTask: Task | null; questionExtractionTask: Task | null; interviewResearchTask: Task | null; interviewSession: Session | null
  recentTasks: Task[]
}
type MaterialSection = { id: string; heading: string; content: string; included: boolean; order: number; factIds: string[]; evidence: Evidence[] }
type MaterialProject = { id: string; title: string; selectionReason: string; summary: string; included: boolean; order: number; factIds: string[]; evidence: Evidence[] }
type Evidence = { id?: string; title?: string; summary?: string; locationLabel?: string; excerpt?: string }
type MaterialDraft = { resumeHeadline: string; resumeSummary: string; resumeHeadlineEvidence: Evidence[]; resumeSummaryEvidence: Evidence[]; resumeSections: MaterialSection[]; portfolioProjects: MaterialProject[]; informationGaps: string[]; hasUnlinkedContent: boolean }
type Pane = 'materials' | 'questions' | 'interview' | 'source'
const paneLabels: Record<Pane, string> = { materials: '맞춤 이력서·포트폴리오', questions: '지원서 문항', interview: '면접 준비', source: '지원서 원문' }
const basisLabels: Record<string, string> = { POSTING: '공고 기반', RESUME: '이력서 기반', PORTFOLIO: '포트폴리오 기반', APPLICATION_ANSWER: '지원서 답변 기반', COMPANY_TREND: '회사·직무 경향' }
const taskLabels: Record<TaskType, string> = { MATERIAL_DRAFT: '맞춤 자료 구성', EXTRACT_QUESTIONS: '문항 추출', DRAFT_ANSWER: '답변 초안 작성', INTERVIEW_RESEARCH: '면접 경향 조사', INTERVIEW_FOLLOW_UP: '꼬리 질문 작성' }
const emptyDraft: MaterialDraft = { resumeHeadline: '', resumeSummary: '', resumeHeadlineEvidence: [], resumeSummaryEvidence: [], resumeSections: [], portfolioProjects: [], informationGaps: [], hasUnlinkedContent: false }

function taskState(task: Task | null | undefined) {
  if (!task) return null
  if (task.status === 'WAITING_AI') return 'AI 대기 중'
  if (task.status === 'ANALYZING') return task.progressMessage || 'AI 처리 중'
  if (task.status === 'FAILED') return '실패'
  return '완료'
}
function initialDraft(value: any): MaterialDraft {
  if (!value || typeof value !== 'object') return emptyDraft
  return {
    resumeHeadline: value.resumeHeadline ?? '', resumeSummary: value.resumeSummary ?? '',
    resumeHeadlineEvidence: Array.isArray(value.resumeHeadlineEvidence) ? value.resumeHeadlineEvidence : [],
    resumeSummaryEvidence: Array.isArray(value.resumeSummaryEvidence) ? value.resumeSummaryEvidence : [],
    resumeSections: Array.isArray(value.resumeSections) ? value.resumeSections.map((item: any, index: number) => ({ ...item, order: item.order ?? index, included: item.included !== false, factIds: item.factIds ?? [], evidence: item.evidence ?? [] })) : [],
    portfolioProjects: Array.isArray(value.portfolioProjects) ? value.portfolioProjects.map((item: any, index: number) => ({ ...item, order: item.order ?? index, included: item.included !== false, factIds: item.factIds ?? [], evidence: item.evidence ?? [] })) : [],
    informationGaps: Array.isArray(value.informationGaps) ? value.informationGaps : [],
    hasUnlinkedContent: value.hasUnlinkedContent === true,
  }
}
function json(body: unknown): RequestInit { return { body: JSON.stringify(body) } }

export function ApplicationWorkspace({ jobs, demo = false, initialApplicationId, onCasesChanged, onSelectApplication, onOpenCareer, onBrowseJobs }: {
  jobs: Job[]; demo?: boolean; initialApplicationId?: string | null; onCasesChanged?: (cases: ApplicationCase[]) => void; onSelectApplication?: (id: string | null) => void
  onOpenCareer?: () => void; onBrowseJobs?: () => void
}) {
  const [cases, setCases] = useState<ApplicationCase[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(initialApplicationId ?? null)
  const [detail, setDetail] = useState<Detail | null>(null)
  const [pane, setPane] = useState<Pane>('materials')
  const [draft, setDraft] = useState<MaterialDraft>(emptyDraft)
  const [questionEdits, setQuestionEdits] = useState<Record<string, { prompt: string; characterLimit: string; answer: string }>>({})
  const [newPrompt, setNewPrompt] = useState('')
  const [newLimit, setNewLimit] = useState('')
  const [activeTask, setActiveTask] = useState<Task | null>(null)
  const [workerOnline, setWorkerOnline] = useState<boolean | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const detailRef = useRef<Detail | null>(null)
  const materialsVersionRef = useRef<number | null>(null)
  const selectedJob = useMemo(() => jobs.find(job => Number(job.id) === detail?.application.postingId) ?? null, [jobs, detail?.application.postingId])

  const refreshCases = useCallback(async () => {
    const rows = await api<ApplicationCase[]>('/api/applications')
    setCases(rows); onCasesChanged?.(rows)
    setSelectedId(current => current && rows.some(row => row.id === current) ? current : (initialApplicationId && rows.some(row => row.id === initialApplicationId) ? initialApplicationId : rows[0]?.id ?? null))
    return rows
  }, [initialApplicationId, onCasesChanged])
  const refreshDetail = useCallback(async (id: string) => {
    const value = await api<Detail>(`/api/applications/${id}`)
    const previous = detailRef.current
    detailRef.current = value
    setDetail(value)
    if (value.materialsVersion !== materialsVersionRef.current) { materialsVersionRef.current = value.materialsVersion; setDraft(initialDraft(value.materials)) }
    setQuestionEdits(current => {
      const next = { ...current }
      value.questions.forEach(q => {
        const old = previous?.questions.find(item => item.id === q.id)
        const local = next[q.id]
        if (!local || (old && local.answer === old.answerDraft && local.prompt === old.prompt)) next[q.id] = { prompt: q.prompt, characterLimit: q.characterLimit?.toString() ?? '', answer: q.answerDraft }
      })
      return next
    })
    const running = value.recentTasks.find(task => task.status === 'WAITING_AI' || task.status === 'ANALYZING')
    if (running) setActiveTask(current => current && current.status !== 'COMPLETED' && current.status !== 'FAILED' ? current : running)
    return value
  }, [])

  useEffect(() => {
    if (demo) { setLoading(false); return }
    let alive = true
    void Promise.all([refreshCases(), api<{ connected: boolean }>('/api/ai/status')]).then(([, status]) => { if (alive) setWorkerOnline(status.connected) }).catch(e => { if (alive) setError(e instanceof Error ? e.message : '지원 준비 데이터를 불러오지 못했어요.') }).finally(() => { if (alive) setLoading(false) })
    const timer = window.setInterval(() => { void api<{ connected: boolean }>('/api/ai/status').then(status => alive && setWorkerOnline(status.connected)).catch(() => undefined) }, 10000)
    return () => { alive = false; window.clearInterval(timer) }
  }, [demo, refreshCases])
  useEffect(() => {
    if (demo || !initialApplicationId) return
    setSelectedId(initialApplicationId)
  }, [initialApplicationId, demo])
  useEffect(() => { onSelectApplication?.(selectedId) }, [selectedId, onSelectApplication])
  useEffect(() => {
    if (demo || !selectedId) { setDetail(null); return }
    let alive = true
    setLoading(true); setError(''); setActiveTask(null)
    detailRef.current = null; materialsVersionRef.current = null
    void refreshDetail(selectedId).catch(e => { if (alive) setError(e instanceof Error ? e.message : '지원 건을 불러오지 못했어요.') }).finally(() => { if (alive) setLoading(false) })
    return () => { alive = false }
  }, [selectedId, demo])
  useEffect(() => {
    if (!activeTask || !['WAITING_AI', 'ANALYZING'].includes(activeTask.status)) return
    let alive = true
    const poll = async () => {
      try {
        const task = await api<Task>(`/api/applications/${activeTask.applicationId}/tasks/${activeTask.id}`)
        if (!alive) return
        setActiveTask(task)
        if (task.status === 'COMPLETED' || task.status === 'FAILED') {
          await refreshDetail(task.applicationId)
          await refreshCases()
          if (task.status === 'COMPLETED') setNotice(`${taskLabels[task.type]}을(를) 마쳤어요.`)
          else setError(task.errorMessage || `${taskLabels[task.type]}에 실패했어요.`)
        }
      } catch (e) { if (alive) setError(e instanceof Error ? e.message : 'AI 작업 상태를 확인하지 못했어요.') }
    }
    const timer = window.setInterval(() => void poll(), 1800)
    void poll()
    return () => { alive = false; window.clearInterval(timer) }
  }, [activeTask?.id, activeTask?.status])

  async function run(action: () => Promise<void>, success?: string) {
    setBusy(true); setError(''); setNotice('')
    try { await action(); if (success) setNotice(success) } catch (e) { setError(e instanceof Error ? e.message : '요청을 처리하지 못했어요.') } finally { setBusy(false) }
  }
  async function queueTask(path: string) {
    const task = await api<Task>(path, { method: 'POST' })
    setActiveTask(task)
    setNotice(workerOnline === false ? `${taskLabels[task.type]}을 저장했어요. AI 워커를 켜면 이어서 처리합니다.` : `${taskLabels[task.type]}을 AI 대기열에 넣었어요.`)
  }
  async function updateStatus(status: CaseStatus) {
    if (!detail) return
    await api(`/api/applications/${detail.application.id}/status`, { method: 'PATCH', ...json({ status }) })
    await refreshCases(); await refreshDetail(detail.application.id)
  }
  async function addQuestion() {
    if (!detail || !newPrompt.trim()) return
    const q = await api<Question>(`/api/applications/${detail.application.id}/questions`, { method: 'POST', ...json({ prompt: newPrompt, characterLimit: newLimit ? Number(newLimit) : null }) })
    setNewPrompt(''); setNewLimit(''); await refreshDetail(detail.application.id); setQuestionEdits(current => ({ ...current, [q.id]: { prompt: q.prompt, characterLimit: q.characterLimit?.toString() ?? '', answer: '' } }))
  }
  async function saveQuestion(q: Question) {
    if (!detail) return
    const value = questionEdits[q.id] ?? { prompt: q.prompt, characterLimit: q.characterLimit?.toString() ?? '', answer: q.answerDraft }
    await api(`/api/applications/${detail.application.id}/questions/${q.id}`, { method: 'PUT', ...json({ prompt: value.prompt, characterLimit: value.characterLimit ? Number(value.characterLimit) : null, answer: value.answer }) })
    await refreshDetail(detail.application.id)
  }
  async function draftAllAnswers() {
    if (!detail) return
    const unanswered = detail.questions.filter(question => !(questionEdits[question.id]?.answer ?? question.answerDraft).trim())
    if (!unanswered.length) { setNotice('아직 답변 초안을 만들지 않은 문항이 없어요.'); return }
    let queued = 0
    const failures: string[] = []
    for (const question of unanswered) {
      try {
        await api<Task>(`/api/applications/${detail.application.id}/questions/${question.id}/answer-draft`, { method: 'POST' })
        queued += 1
      } catch (cause) { failures.push(cause instanceof Error ? cause.message : question.prompt) }
    }
    await refreshDetail(detail.application.id)
    if (queued) setNotice(`${queued}개 문항의 답변 초안을 순서대로 만들도록 AI 대기열에 넣었어요.`)
    if (failures.length) setError(`${failures.length}개 문항은 대기열에 넣지 못했어요: ${failures[0]}`)
  }
  async function saveMaterials() {
    if (!detail) return
    const body = { resumeHeadline: draft.resumeHeadline, resumeSummary: draft.resumeSummary,
      resumeSections: draft.resumeSections.map((item, order) => ({ id: item.id, heading: item.heading, content: item.content, included: item.included, order })),
      portfolioProjects: draft.portfolioProjects.map((item, order) => ({ id: item.id, title: item.title, selectionReason: item.selectionReason, summary: item.summary, included: item.included, order })) }
    const saved = await api<{ draft: any; version: number }>(`/api/applications/${detail.application.id}/materials`, { method: 'PUT', ...json(body) })
    setDraft(initialDraft(saved.draft)); await refreshDetail(detail.application.id)
  }
  async function retry(task: Task) {
    if (!detail) return
    const updated = await api<Task>(`/api/applications/${detail.application.id}/tasks/${task.id}/retry`, { method: 'POST' }); setActiveTask(updated)
  }
  async function answerInterview(turn: Turn, text: string) {
    if (!detail?.interviewSession || !detail.interviewSession.session) return
    const task = await api<Task>(`/api/applications/${detail.application.id}/interview/sessions/${turn.sessionId}/turns/${turn.id}/answer`, { method: 'POST', ...json({ answer: text }) })
    setActiveTask(task)
  }
  async function answerFollowUp(turn: Turn, text: string) {
    if (!detail) return
    await api(`/api/applications/${detail.application.id}/interview/sessions/${turn.sessionId}/turns/${turn.id}/follow-up-answer`, { method: 'PUT', ...json({ answer: text }) })
    await refreshDetail(detail.application.id)
  }
  async function promote(turn: Turn, title: string, summary: string) {
    if (!detail) return
    await api(`/api/applications/${detail.application.id}/interview/sessions/${turn.sessionId}/turns/${turn.id}/promote`, { method: 'POST', ...json({ title, summary }) })
    await refreshDetail(detail.application.id); setNotice('경험을 미확인 경력 자료로 저장했어요. 경력 자료에서 내용을 확인하고 수정할 수 있어요.')
  }
  function changeQuestion(id: string, patch: Partial<{ prompt: string; characterLimit: string; answer: string }>) {
    setQuestionEdits(current => ({ ...current, [id]: { ...(current[id] ?? { prompt: '', characterLimit: '', answer: '' }), ...patch } }))
  }
  function moveItem<T extends { order: number }>(items: T[], index: number, offset: number) {
    const next = [...items]; const target = index + offset
    if (target < 0 || target >= next.length) return next
    const current = next[index]; next[index] = next[target]; next[target] = current
    return next.map((item, order) => ({ ...item, order }))
  }

  if (demo) return <div className="application-empty"><h2>지원 준비 페이지</h2><p>샘플 데이터에서는 지원서와 면접 기록을 만들지 않아요. 실제 공고 모드에서 지원 준비를 시작해 주세요.</p></div>
  const currentTask = activeTask && ['WAITING_AI', 'ANALYZING'].includes(activeTask.status) ? activeTask : null
  const selectedCount = cases.length
  const research = detail?.interviewResearchTask?.result
  const researchPatterns = research?.reviewPatterns ?? []
  const researchQuestions = research?.questions ?? []
  const activeTurn = detail?.interviewSession?.turns.find(turn => !turn.answer || (turn.followUpQuestion && !turn.followUpAnswer))

  return <section className="application-workspace" aria-label="지원 준비 공간">
    <aside className="application-case-list">
      <div className="application-case-list-heading"><div><strong>지원 건</strong><span>{selectedCount}개</span></div><small>관심 목록과 별도로 관리해요.</small></div>
      {!cases.length && !loading && <div className="application-list-empty"><h3>시작한 지원이 없어요</h3><p>공고 상세에서 ‘지원 준비 시작’을 누르면 이곳에 저장돼요.</p></div>}
      {cases.map(item => <button key={item.id} className={`application-case-card ${item.id === selectedId ? 'active' : ''}`} onClick={() => { setSelectedId(item.id); setPane('materials'); onSelectApplication?.(item.id) }}>
        <span className="application-case-company"><BrandMark sourceId={item.sourceId as any} small />{item.company}<span className={`application-status-badge ${item.status.toLowerCase()}`}>{item.status === 'APPLIED' ? '지원 완료' : '준비 중'}</span></span>
        <strong>{item.title}</strong><small>{item.location || '근무지 미정'} · 시작 {dateLabel(item.startedAt)}</small>
      </button>)}
      {loading && <p className="application-list-loading">지원 건을 불러오는 중…</p>}
    </aside>
    <div className="application-case-detail">
      {!detail && !loading && <div className="application-empty"><span>✦</span><h2>지원 준비를 시작해보세요</h2><p>공고 상세에서 지원 준비를 시작하면 맞춤 자료와 면접 준비를 이곳에 모아둘게요.</p><button className="button secondary" onClick={onBrowseJobs}>공고 모아보기</button></div>}
      {detail && <>
        <header className="application-detail-heading">
          <div className="application-detail-title"><BrandMark sourceId={detail.application.sourceId as any} /><div><span>{detail.application.company} · {sourceById(detail.application.sourceId as any)?.name ?? ''}</span><h2>{detail.application.title}</h2><small>{detail.application.location || '근무지 미정'} · 지원 준비 시작 {dateLabel(detail.application.startedAt, true)}</small></div></div>
          <label className="application-status-control">지원 상태<select value={detail.application.status} onChange={event => void run(() => updateStatus(event.target.value as CaseStatus))}><option value="PREPARING">지원 준비</option><option value="APPLIED">지원 완료</option></select></label>
        </header>
        <div className="application-detail-actions"><a className="button secondary small" href={detail.application.url} target="_blank" rel="noreferrer">공고 원문 열기 <Icon name="external" size={14} /></a><span>{detail.application.closed && <b className="closed-badge">공고 마감 · 지원 기록은 보관 중</b>}AI 모델 · gpt-6-luna{workerOnline === false && <em> · AI 워커가 꺼져 있어 작업은 대기열에 저장돼요</em>}</span></div>
        <nav className="application-panes" role="tablist" aria-label="지원 준비 항목">{(Object.keys(paneLabels) as Pane[]).map(id => <button key={id} role="tab" aria-selected={pane === id} className={pane === id ? 'active' : ''} onClick={() => setPane(id)}>{paneLabels[id]}</button>)}</nav>
        {currentTask && <div className="application-ai-progress" role="status"><span className="application-ai-spinner" /><div><strong>{taskLabels[currentTask.type]} · {taskState(currentTask)}</strong><small>브라우저를 닫아도 작업은 취소되지 않아요. 이 화면을 다시 열면 저장된 상태를 이어서 확인합니다.</small></div><button className="text-button" onClick={() => void refreshDetail(detail.application.id)}>새로고침</button></div>}
        {activeTask?.status === 'FAILED' && <div className="application-task-error"><div><strong>{taskLabels[activeTask.type]} 실패</strong><p>{activeTask.errorMessage}</p></div><button className="button secondary small" disabled={busy} onClick={() => void run(() => retry(activeTask))}>실패한 작업 재시도</button></div>}
        {pane === 'materials' && <div className="application-pane-content">
          <div className="application-pane-heading"><div><h3>공고 맞춤 자료</h3><p>여러 경력 자료에서 관련 경험을 찾아 초안을 만들어요. 원문 근거 링크는 가능한 문장에 연결하고, 연결이 빠진 곳은 확인 표시를 남겨요.</p></div>{!detail.materials && <button className="button primary" disabled={busy || !!currentTask} onClick={() => void run(() => queueTask(`/api/applications/${detail.application.id}/materials/generate`))}>맞춤 이력서·포트폴리오 만들기</button>}</div>
          {!detail.materials && <div className="application-empty-inline"><strong>맞춤 자료 초안이 없어요</strong><p>등록된 경력 자료에서 공고와 관련된 경험을 찾아 이력서와 포트폴리오를 구성해요. 자료와 대조가 필요한 부분은 초안에 표시합니다.</p><button className="text-button" onClick={onOpenCareer}>경력 자료 열기</button></div>}
          {detail.materials && <>
            <div className="application-material-card"><div className="application-material-card-heading"><h4>이력서</h4><span>초안 v{detail.materialsVersion}</span></div>
              <label>이력서 제목<input maxLength={500} value={draft.resumeHeadline} onChange={e => setDraft(value => ({ ...value, resumeHeadline: e.target.value }))} /></label><EvidenceList evidence={draft.resumeHeadlineEvidence} />
              <label>요약<textarea rows={4} maxLength={3000} value={draft.resumeSummary} onChange={e => setDraft(value => ({ ...value, resumeSummary: e.target.value }))} /></label><EvidenceList evidence={draft.resumeSummaryEvidence} />
              {draft.resumeSections.map((section, index) => <article className={`application-edit-item ${section.included ? '' : 'excluded'}`} key={section.id}><div className="application-edit-item-heading"><label><input type="checkbox" checked={section.included} onChange={e => setDraft(value => ({ ...value, resumeSections: value.resumeSections.map(item => item.id === section.id ? { ...item, included: e.target.checked } : item) }))} /> 이력서에 포함</label><div><button aria-label="이력서 항목 위로" disabled={index === 0} onClick={() => setDraft(value => ({ ...value, resumeSections: moveItem(value.resumeSections, index, -1) }))}>↑</button><button aria-label="이력서 항목 아래로" disabled={index === draft.resumeSections.length - 1} onClick={() => setDraft(value => ({ ...value, resumeSections: moveItem(value.resumeSections, index, 1) }))}>↓</button></div></div><input aria-label="이력서 항목 제목" value={section.heading} onChange={e => setDraft(value => ({ ...value, resumeSections: value.resumeSections.map(item => item.id === section.id ? { ...item, heading: e.target.value } : item) }))} /><textarea aria-label="이력서 항목 내용" rows={4} value={section.content} onChange={e => setDraft(value => ({ ...value, resumeSections: value.resumeSections.map(item => item.id === section.id ? { ...item, content: e.target.value } : item) }))} /><EvidenceList evidence={section.evidence} /></article>)}
            </div>
            <div className="application-material-card"><div className="application-material-card-heading"><h4>포트폴리오</h4><span>{draft.portfolioProjects.length}개 프로젝트</span></div>
              {draft.portfolioProjects.map((project, index) => <article className={`application-edit-item ${project.included ? '' : 'excluded'}`} key={project.id}><div className="application-edit-item-heading"><label><input type="checkbox" checked={project.included} onChange={e => setDraft(value => ({ ...value, portfolioProjects: value.portfolioProjects.map(item => item.id === project.id ? { ...item, included: e.target.checked } : item) }))} /> 포트폴리오에 포함</label><div><button aria-label="프로젝트 위로" disabled={index === 0} onClick={() => setDraft(value => ({ ...value, portfolioProjects: moveItem(value.portfolioProjects, index, -1) }))}>↑</button><button aria-label="프로젝트 아래로" disabled={index === draft.portfolioProjects.length - 1} onClick={() => setDraft(value => ({ ...value, portfolioProjects: moveItem(value.portfolioProjects, index, 1) }))}>↓</button></div></div><input aria-label="프로젝트 제목" value={project.title} onChange={e => setDraft(value => ({ ...value, portfolioProjects: value.portfolioProjects.map(item => item.id === project.id ? { ...item, title: e.target.value } : item) }))} /><textarea aria-label="프로젝트 설명" rows={4} value={project.summary} onChange={e => setDraft(value => ({ ...value, portfolioProjects: value.portfolioProjects.map(item => item.id === project.id ? { ...item, summary: e.target.value } : item) }))} /><label>선정 이유<input value={project.selectionReason} onChange={e => setDraft(value => ({ ...value, portfolioProjects: value.portfolioProjects.map(item => item.id === project.id ? { ...item, selectionReason: e.target.value } : item) }))} /></label><EvidenceList evidence={project.evidence} /></article>)}
              {!draft.portfolioProjects.length && <p className="application-no-evidence">기존 경력 자료에서 공고와 연결할 프로젝트를 찾지 못했어요.</p>}
            </div>
            {!!draft.informationGaps.length && <div className="application-information-gaps"><strong>추가 확인이 필요한 내용</strong><ul>{draft.informationGaps.map((gap, index) => <li key={index}>{gap}</li>)}</ul></div>}
            <div className="application-save-bar"><span>원본 이력서·포트폴리오는 바뀌지 않아요. 수정 내용은 이 지원 건의 초안 v{detail.materialsVersion}에 저장됩니다.</span><button className="button primary" disabled={busy} onClick={() => void run(saveMaterials, '맞춤 자료를 저장했어요.')}>{busy ? '저장 중…' : '초안 저장'}</button></div>
          </>}
        </div>}
        {pane === 'questions' && <div className="application-pane-content">
          <div className="application-pane-heading"><div><h3>지원서 문항별 답변</h3><p>원문에서 문항을 뽑은 뒤, 미작성 답변을 한 번에 초안으로 만들 수 있어요.</p></div><div className="application-pane-actions"><button className="button secondary" disabled={busy || !!currentTask || !detail.questions.some(question => !(questionEdits[question.id]?.answer ?? question.answerDraft).trim())} onClick={() => void run(draftAllAnswers)}>미작성 답변 전체 초안</button><button className="button primary" disabled={busy || !!currentTask || !detail.requirement} onClick={() => void run(() => queueTask(`/api/applications/${detail.application.id}/questions/extract`))}>원문에서 문항 추출</button></div></div>
          {!detail.requirement && <div className="application-empty-inline"><strong>저장된 지원서 원문이 필요해요</strong><p>‘지원서 원문’ 탭에서 공개 지원 페이지 원문을 확인하거나 직접 붙여넣어 저장하세요.</p><button className="text-button" onClick={() => setPane('source')}>지원서 원문 열기</button></div>}
          {detail.questionExtractionTask && <TaskNotice task={detail.questionExtractionTask} retry={() => void run(() => retry(detail.questionExtractionTask!))} />}
          <div className="application-add-question"><h4>문항 직접 추가</h4><textarea rows={3} maxLength={8000} placeholder="지원서 질문을 입력하세요" value={newPrompt} onChange={e => setNewPrompt(e.target.value)} /><div><label>글자 수 제한<input type="number" min={1} max={12000} placeholder="선택" value={newLimit} onChange={e => setNewLimit(e.target.value)} /></label><button className="button secondary small" disabled={busy || !newPrompt.trim()} onClick={() => void run(addQuestion, '문항을 추가했어요.')}>문항 추가</button></div></div>
          {!detail.questions.length && <p className="application-no-evidence">아직 지원서 문항이 없어요. 원문에서 추출하거나 직접 추가할 수 있어요.</p>}
          {detail.questions.map((question, index) => { const value = questionEdits[question.id] ?? { prompt: question.prompt, characterLimit: question.characterLimit?.toString() ?? '', answer: question.answerDraft }; return <article className="application-question-card" key={question.id}><header><span>문항 {index + 1}</span><button className="text-button danger-text" disabled={busy} onClick={() => void run(async () => { await api(`/api/applications/${detail.application.id}/questions/${question.id}`, { method: 'DELETE' }); await refreshDetail(detail.application.id) })}>삭제</button></header><label>질문<textarea rows={3} maxLength={8000} value={value.prompt} onChange={e => changeQuestion(question.id, { prompt: e.target.value })} /></label><label className="application-limit-field">글자 수 제한<input type="number" min={1} max={12000} value={value.characterLimit} onChange={e => changeQuestion(question.id, { characterLimit: e.target.value })} placeholder="제한 없음" /></label>{question.sourceQuote && <blockquote><small>원문 근거</small>{question.sourceQuote}</blockquote>}<label>답변 초안<textarea rows={8} maxLength={question.characterLimit ?? 12000} value={value.answer} onChange={e => changeQuestion(question.id, { answer: e.target.value })} placeholder="경력 자료에서 관련 경험을 찾아 답변 초안을 만들어요." /></label><div className="application-answer-footer"><span>{value.answer.length.toLocaleString()}{question.characterLimit ? ` / ${question.characterLimit}` : ''}자 · 근거 {question.factIds.length ? `${question.factIds.length}개 연결` : '미연결'}</span><div><button className="button secondary small" disabled={busy || !value.prompt.trim()} onClick={() => void run(() => queueTask(`/api/applications/${detail.application.id}/questions/${question.id}/answer-draft`))}>AI 초안</button><button className="button primary small" disabled={busy} onClick={() => void run(() => saveQuestion(question), '문항과 답변을 저장했어요.')}>저장</button></div></div>{question.factIds.length > 0 ? <p className="application-fact-note">사용한 경력 근거 ID: {question.factIds.join(', ')} · 답변 초안은 저장된 자료에서 수정할 수 있어요.</p> : question.answerDraft.trim() && <p className="application-fact-warning">근거가 자동 연결되지 않았어요. 경력 자료의 내용과 수치를 확인한 뒤 사용하세요.</p>}</article> })}
        </div>}
        {pane === 'interview' && <div className="application-pane-content">
          <div className="application-pane-heading"><div><h3>공개 정보 기반 면접 준비</h3><p>공식 사실, 공개 후기, 공고에서 추론한 주제를 분리해 조사합니다. 실제 질문을 보장하지 않으며 검색한 출처와 확인 시점을 보여줘요.</p></div><button className="button primary" disabled={busy || !!currentTask} onClick={() => void run(() => queueTask(`/api/applications/${detail.application.id}/interview/research`))}>{research ? '다시 조사' : '면접 경향 조사'}</button></div>
          {detail.interviewResearchTask && <TaskNotice task={detail.interviewResearchTask} retry={() => void run(() => retry(detail.interviewResearchTask!))} />}
          {research && <><div className="application-research-meta">검색 {research.searchPerformed ? '수행함' : '검색 확인 불가'} · 조사 시각 {research.searchedAt ? dateLabel(research.searchedAt, true) : '기록 없음'}</div>
            {!!(research.officialFacts ?? []).length && <ResearchSourceGroup title="공식 안내·기술 블로그" items={research.officialFacts} />}
            {!!researchPatterns.length && <section className="application-research-group"><h4>여러 후기에서 반복된 경향</h4>{researchPatterns.map((item: any, index: number) => <article key={index}><strong>{item.pattern}</strong><p>{item.summary}</p><small>근거 {item.sourceCount}개 · 신뢰도 {item.confidence}</small><SourceLinks items={item.sources ?? []} /></article>)}</section>}
            {!!(research.individualReviews ?? []).length && <ResearchSourceGroup title="개별 지원자 후기" items={research.individualReviews} />}
            {!!(research.inferredTopics ?? []).length && <section className="application-research-group"><h4>공고에서 추론한 예상 주제</h4>{research.inferredTopics.map((item: any, index: number) => <article key={index}><strong>{item.topic}</strong><p>{item.reason}</p><small>공고 내용에서 추론한 예상이며 공개 후기와는 구분됩니다.</small></article>)}</section>}
            <section className="application-research-group"><h4>근거 유형별 질문</h4><InterviewQuestionBank questions={researchQuestions} />{!researchQuestions.length && <p>현재 자료만으로 만들 수 있는 질문이 없어요.</p>}</section>
            <div className="application-interview-start"><div><strong>한 번에 하나씩 경험 인터뷰</strong><p>답변마다 역할·판단·결과를 구체화하는 꼬리 질문을 받고, 정리한 경험은 승인할 때만 경력 자료에 저장해요.</p></div><button className="button secondary" disabled={busy || !researchQuestions.length} onClick={() => void run(async () => { const session = await api<Session>(`/api/applications/${detail.application.id}/interview/sessions`, { method: 'POST' }); await refreshDetail(detail.application.id); setNotice(`경험 인터뷰를 시작했어요. 첫 질문 ${session.turns[0]?.position ? '을 확인하세요.' : '이 준비됐어요.'}`) })}>경험 인터뷰 시작</button></div>
          </>}
          {!research && !currentTask && <div className="application-empty-inline"><strong>아직 조사 결과가 없어요</strong><p>조사를 시작하면 공개 웹 검색을 통해 회사 공식 자료와 지원자 후기를 살펴보고, 공고 기반 추론을 따로 표시해요.</p></div>}
          {detail.interviewSession && <InterviewSession session={detail.interviewSession} activeTurn={activeTurn} busy={busy || !!currentTask} onAnswer={(turn, answer) => void run(() => answerInterview(turn, answer))} onAnswerFollowUp={(turn, answer) => void run(() => answerFollowUp(turn, answer))} onNext={() => void run(async () => { await api(`/api/applications/${detail.application.id}/interview/sessions/${detail.interviewSession!.session.id}/next`, { method: 'POST' }); await refreshDetail(detail.application.id) })} onPromote={(turn, title, summary) => void run(() => promote(turn, title, summary))} />}
        </div>}
        {pane === 'source' && <div className="application-pane-content"><div className="application-pane-heading"><div><h3>지원서 원문</h3><p>원문은 로컬에 저장됩니다. AI 문항 추출 시에만 저장된 최신 원문을 분석 작업으로 전달해요.</p></div></div>{selectedJob ? <ApplicationRequirementsPanel job={selectedJob} onSaved={() => void refreshDetail(detail.application.id)} /> : <p>공고 원문을 불러올 수 없어요.</p>}</div>}
      </>}
      {error && <div className="application-global-error" role="alert">{error}</div>}{notice && <div className="application-global-notice" role="status">{notice}</div>}
    </div>
  </section>
}

function EvidenceList({ evidence }: { evidence: Evidence[] }) {
  if (!evidence.length) return <p className="application-no-evidence">이 항목에는 원문 근거가 연결되지 않았어요. 경력 자료와 대조해 확인하세요.</p>
  return <details className="application-evidence"><summary>경력 근거 {evidence.length}개 보기</summary>{evidence.map((item, index) => <blockquote key={item.id ?? index}><strong>{item.title ?? item.locationLabel ?? `근거 ${index + 1}`}</strong>{item.excerpt ?? item.summary ?? ''}</blockquote>)}</details>
}
function TaskNotice({ task, retry }: { task: Task; retry: () => void }) {
  return <div className={`application-task-notice ${task.status.toLowerCase()}`}><div><strong>{taskLabels[task.type]} · {taskState(task)}</strong>{task.errorMessage && <p>{task.errorMessage}</p>}{task.status === 'WAITING_AI' && <small>AI 워커가 실행되면 자동으로 처리돼요. 대기 작업은 취소되지 않습니다.</small>}</div>{task.status === 'FAILED' && <button className="button secondary small" onClick={retry}>다시 시도</button>}</div>
}
function SourceLinks({ items }: { items: any[] }) {
  if (!items.length) return null
  return <ul className="application-source-links">{items.map((item, index) => <li key={`${item.url}-${index}`}><a href={item.url} target="_blank" rel="noreferrer">{item.title || item.url} <Icon name="external" size={12} /></a>{item.publishedAt && <small>{item.publishedAt}</small>}{item.reason && <p>{item.reason}</p>}{item.statement && <p>{item.statement}</p>}</li>)}</ul>
}
function ResearchSourceGroup({ title, items }: { title: string; items: any[] }) {
  return <section className="application-research-group"><h4>{title}</h4>{items.map((item, index) => <article key={`${item.url}-${index}`}><strong>{item.statement}</strong><small>{item.sourceKind === 'COMPANY_BLOG' ? '회사 기술 블로그' : item.sourceKind === 'OFFICIAL' ? '공식 자료' : '지원자 경험'} · 신뢰도 {item.confidence}{item.publishedAt ? ` · ${item.publishedAt}` : ''}</small><SourceLinks items={[item]} /></article>)}</section>
}
function InterviewQuestionBank({ questions }: { questions: any[] }) {
  const bases = ['POSTING', 'RESUME', 'PORTFOLIO', 'APPLICATION_ANSWER', 'COMPANY_TREND']
  return <div className="application-question-bank-groups">{bases.map(basis => {
    const group = questions.filter(item => item.basis === basis)
    return group.length ? <section key={basis}><h5>{basisLabels[basis]}</h5><div className="application-question-bank">{group.map((item, index) => <article key={`${basis}-${index}`}><strong>{item.question}</strong><p>{item.reason}</p>{item.sourceUrls?.map((url: string) => <a key={url} href={url} target="_blank" rel="noreferrer">질문 근거 출처 <Icon name="external" size={12} /></a>)}</article>)}</div></section> : null
  })}</div>
}
function InterviewSession({ session, activeTurn, busy, onAnswer, onAnswerFollowUp, onNext, onPromote }: {
  session: Session; activeTurn?: Turn; busy: boolean; onAnswer: (turn: Turn, answer: string) => void; onAnswerFollowUp: (turn: Turn, answer: string) => void; onNext: () => void; onPromote: (turn: Turn, title: string, summary: string) => void
}) {
  return <section className="application-interview-session"><header><div><strong>경험 인터뷰 {session.session.status === 'ACTIVE' ? '진행 중' : '완료'}</strong><small>{session.turns.length}개 질문</small></div>{session.session.status === 'ACTIVE' && activeTurn?.followUpQuestion && activeTurn.followUpAnswer && <button className="button secondary small" disabled={busy} onClick={onNext}>다음 질문</button>}</header>
    {session.turns.map(turn => <InterviewTurn key={turn.id} turn={turn} current={turn.id === activeTurn?.id} active={session.session.status === 'ACTIVE'} busy={busy} onAnswer={onAnswer} onAnswerFollowUp={onAnswerFollowUp} onPromote={onPromote} />)}
  </section>
}
function InterviewTurn({ turn, current, active, busy, onAnswer, onAnswerFollowUp, onPromote }: {
  turn: Turn; current: boolean; active: boolean; busy: boolean; onAnswer: (turn: Turn, answer: string) => void; onAnswerFollowUp: (turn: Turn, answer: string) => void; onPromote: (turn: Turn, title: string, summary: string) => void
}) {
  const [answer, setAnswer] = useState('')
  const [followUpAnswer, setFollowUpAnswer] = useState('')
  const [title, setTitle] = useState(turn.proposedTitle ?? '')
  const [summary, setSummary] = useState(turn.proposedSummary ?? turn.answer)
  return <article className="application-interview-turn"><span>{basisLabels[turn.basis] ?? turn.basis}</span><h4>{turn.question}</h4>{turn.answer ? <blockquote>{turn.answer}</blockquote> : current && active ? <><p>역할·선택한 판단·결과를 구체적으로 답해보세요.</p><textarea rows={5} maxLength={12000} value={answer} onChange={event => setAnswer(event.target.value)} placeholder="상황과 내가 맡은 역할, 판단과 결과를 적어보세요." /><button className="button primary small" disabled={busy || answer.trim().length < 10} onClick={() => onAnswer(turn, answer)}>답변 보내고 꼬리 질문 받기</button></> : <p>이전 질문을 마치면 이어서 답할 수 있어요.</p>}{turn.followUpQuestion && <div className="application-follow-up"><strong>꼬리 질문</strong><p>{turn.followUpQuestion}</p>{turn.followUpAnswer ? <blockquote>{turn.followUpAnswer}</blockquote> : current ? <><textarea rows={4} maxLength={12000} value={followUpAnswer} onChange={event => setFollowUpAnswer(event.target.value)} placeholder="추가로 구체적인 근거와 결과를 적어보세요." /><button className="button secondary small" disabled={busy || followUpAnswer.trim().length < 10} onClick={() => onAnswerFollowUp(turn, followUpAnswer)}>꼬리 질문 답변 저장</button></> : <p>현재 질문을 마친 뒤 답할 수 있어요.</p>}</div>}{turn.proposedSummary && <div className="application-experience-proposal"><strong>경력 자료 저장 후보</strong><label>경험 제목<input value={title} onChange={event => setTitle(event.target.value)} maxLength={500} /></label><label>정리한 경험<textarea rows={4} value={summary} onChange={event => setSummary(event.target.value)} maxLength={8000} /></label>{turn.sourceExcerpt && <small>내 답변 근거 · “{turn.sourceExcerpt}”</small>}{turn.promotedMaterialId ? <span className="application-status-badge applied">경력 자료에 저장됨</span> : <button className="button secondary small" disabled={busy || !turn.sourceExcerpt || !title.trim() || !summary.trim()} onClick={() => onPromote(turn, title, summary)}>확인하고 미확인 경력 자료로 저장</button>}</div>}</article>
}
