import { useCallback, useEffect, useState } from 'react'
import { api } from '../api'
import type { Job } from '../types'

type RequirementStatus = 'WAITING_PUBLIC' | 'FETCHING_PUBLIC' | 'WAITING_AI' | 'ANALYZING' | 'REVIEW' | 'CONFIRMED' | 'FAILED'
type RequirementSource = 'POSTING_BODY' | 'USER_PASTED' | 'PUBLIC_PAGE'
interface Requirement {
  id: string
  source: RequirementSource
  sourceUrl: string | null
  rawText: string
  result: {
    documents: { name: string; requirement: string; format: string; notes: string; evidence: string }[]
    questions: { prompt: string; required: string; answerType: string; minLength: number | null; maxLength: number | null; instructions: string; evidence: string }[]
    notes: string
    confidence: string
  } | null
  status: RequirementStatus
  attempts: number
  errorMessage: string | null
  createdAt: string
}

const statusLabel: Record<RequirementStatus, string> = { WAITING_PUBLIC: '공개 페이지 확인 대기', FETCHING_PUBLIC: '공개 페이지 확인 중', WAITING_AI: 'AI 분석 대기', ANALYZING: 'AI 분석 중', REVIEW: '확인 필요', CONFIRMED: '확정됨', FAILED: '실패' }

export function ApplicationRequirementsPanel({ job }: { job: Job }) {
  const [items, setItems] = useState<Requirement[]>([])
  const [rawText, setRawText] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const refresh = useCallback(async () => {
    try { setItems(await api<Requirement[]>(`/api/postings/${job.id}/application-requirements`)); setError('') }
    catch (cause) { setError(cause instanceof Error ? cause.message : '지원서 정보를 불러오지 못했어요.') }
    finally { setLoading(false) }
  }, [job.id])
  useEffect(() => { void refresh() }, [refresh])
  const pending = items.some(item => ['WAITING_PUBLIC', 'FETCHING_PUBLIC', 'WAITING_AI', 'ANALYZING'].includes(item.status))
  useEffect(() => { if (!pending) return; const timer = window.setInterval(() => void refresh(), 2500); return () => window.clearInterval(timer) }, [pending, refresh])

  async function submit(text: string, source: RequirementSource) {
    if (text.trim().length < 20) { setError('지원 안내 텍스트를 20자 이상 입력해 주세요.'); return }
    setBusy(true); setError('')
    try {
      await api(`/api/postings/${job.id}/application-requirements`, { method: 'POST', body: JSON.stringify({ source, sourceUrl: job.url, rawText: text }) })
      setRawText(''); await refresh()
    } catch (cause) { setError(cause instanceof Error ? cause.message : '분석 요청을 저장하지 못했어요.') }
    finally { setBusy(false) }
  }
  async function update(id: string, action: 'confirm' | 'retry') {
    setBusy(true); setError('')
    try { await api(`/api/application-requirements/${id}/${action}`, { method: 'POST', body: '{}' }); await refresh() }
    catch (cause) { setError(cause instanceof Error ? cause.message : '상태를 변경하지 못했어요.') }
    finally { setBusy(false) }
  }
  async function collectPublicPage() {
    setBusy(true); setError('')
    try {
      await api(`/api/postings/${job.id}/application-requirements/public-page`, { method: 'POST', body: '{}' })
      await refresh()
    } catch (cause) { setError(cause instanceof Error ? cause.message : '공개 지원 페이지 확인을 시작하지 못했어요.') }
    finally { setBusy(false) }
  }
  const savedBody = job.versions[0]?.body?.trim() ?? ''
  return <section className="application-requirements" aria-label="지원서 요구 정보">
    <div className="application-requirements-heading"><div><h3>지원서 요구 정보</h3><p>지원 전 필요한 서류와 문항을 공고별로 정리해요.</p></div><span>{items.length}건</span></div>
    <label className="application-requirements-input-label" htmlFor="application-requirements-text">공고의 지원 안내 또는 지원서 문항 붙여넣기</label>
    <textarea id="application-requirements-text" value={rawText} onChange={event => setRawText(event.target.value)} placeholder="지원서에 표시된 빈 문항과 제출 안내를 복사해 붙여넣으세요. 개인정보나 작성한 답변은 넣지 않아도 돼요." rows={5} maxLength={100000} />
    <div className="application-requirements-actions"><span>{rawText.length.toLocaleString()} / 100,000자</span><button className="button primary" disabled={busy || rawText.trim().length < 20} onClick={() => void submit(rawText, 'USER_PASTED')}>AI로 문항 분석</button></div>
    <div className="application-requirements-actions application-secondary-actions">
      <button className="button secondary" disabled={busy} onClick={() => void collectPublicPage()}>공개 지원 페이지 자동 확인</button>
      {savedBody.length >= 20 && <button className="text-button application-body-analysis" disabled={busy} onClick={() => void submit(savedBody, 'POSTING_BODY')}>저장된 공고 본문으로 분석</button>}
    </div>
    <p className="application-requirements-note">공개된 지원 링크만 열어 텍스트를 읽어요. 로그인 화면은 건너뛰고, 쿠키 동의·폼 입력·지원 제출은 하지 않아요. 자동 확인이나 AI 분석을 요청하면 원문이 Codex 서비스로 전달돼요. 개인정보나 작성한 답변은 넣지 마세요. 크롤러 또는 Codex 워커가 꺼져 있으면 작업은 대기해요.</p>
    {error && <p className="application-requirements-error" role="alert">{error}</p>}
    {loading ? <p className="application-requirements-empty">지원서 정보를 불러오는 중이에요.</p> : items.length === 0 ? <p className="application-requirements-empty">아직 정리된 지원서 정보가 없어요.</p> : <div className="application-requirements-list">{items.map(item => <article className="application-requirement-card" key={item.id}>
      <header><span className={`application-requirement-status ${item.status.toLowerCase()}`}>{statusLabel[item.status]}</span><div className="application-requirement-meta">{item.sourceUrl && item.source === 'PUBLIC_PAGE' && <a href={item.sourceUrl} target="_blank" rel="noreferrer">확인한 공개 페이지</a>}<time>{new Date(item.createdAt).toLocaleString('ko-KR')}</time></div></header>
      <details><summary>분석 원문 보기 · {item.source === 'POSTING_BODY' ? '저장된 공고 본문' : item.source === 'PUBLIC_PAGE' ? '공개 지원 페이지' : '직접 붙여넣기'}</summary><pre>{item.rawText || (item.status === 'WAITING_PUBLIC' || item.status === 'FETCHING_PUBLIC' ? '공개 페이지 확인 후 원문을 표시합니다.' : '저장된 원문이 없어요.')}</pre></details>
      {item.result && <>
        {item.result.documents.length > 0 && <section><h4>제출 서류</h4>{item.result.documents.map((document, index) => <div className="application-requirement-row" key={`${index}-${document.name}`}><strong>{document.name || '이름 확인 필요'}</strong><span>{document.requirement === 'UNKNOWN' ? '필수 여부 미확인' : document.requirement === 'REQUIRED' ? '필수' : '선택'}</span>{document.format && <p>형식: {document.format}</p>}{document.notes && <p>{document.notes}</p>}{document.evidence && <blockquote>{document.evidence}</blockquote>}</div>)}</section>}
        {item.result.questions.length > 0 && <section><h4>지원서 문항 {item.result.questions.length}개</h4>{item.result.questions.map((question, index) => <div className="application-requirement-row" key={`${index}-${question.prompt}`}><strong>{index + 1}. {question.prompt}</strong><span>{question.required === 'UNKNOWN' ? '필수 여부 미확인' : question.required === 'REQUIRED' ? '필수' : '선택'}</span><p>{[question.answerType, question.minLength != null || question.maxLength != null ? `${question.minLength ?? 0}~${question.maxLength ?? '제한 없음'}자` : ''].filter(Boolean).join(' · ')}</p>{question.instructions && <p>{question.instructions}</p>}{question.evidence && <blockquote>{question.evidence}</blockquote>}</div>)}</section>}
        {item.result.documents.length === 0 && item.result.questions.length === 0 && <p>지원서 문항이나 제출 서류를 찾지 못했어요. 원문을 확인해 주세요.</p>}
        {item.result.notes && <p>{item.result.notes}</p>}<small>AI 분석 신뢰도: {item.result.confidence}</small>
      </>}
      {item.errorMessage && <p className="application-requirements-error">{item.errorMessage}</p>}
      <footer>{item.status === 'REVIEW' && <button className="button primary" disabled={busy} onClick={() => void update(item.id, 'confirm')}>확인 완료</button>}{item.status === 'FAILED' && <button className="button secondary" disabled={busy} onClick={() => void update(item.id, 'retry')}>{item.source === 'PUBLIC_PAGE' && !item.rawText ? '공개 페이지 다시 확인' : '다시 분석'}</button>}</footer>
    </article>)}</div>}
  </section>
}
