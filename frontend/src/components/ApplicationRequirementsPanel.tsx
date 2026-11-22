import { useCallback, useEffect, useState } from 'react'
import { api } from '../api'
import type { Job } from '../types'
import { Icon } from './Icon'

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
  publicPageFetched: boolean
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
  const hasPublicPageHistory = items.some(item => item.source === 'PUBLIC_PAGE')
  const latestPublicPage = items.find(item => item.source === 'PUBLIC_PAGE')
  const companyPageStatus = !latestPublicPage ? { label: '미확인', tone: 'unknown', detail: '아직 확인한 적이 없어요. 다음 공고 수집 실행에서 함께 확인해요.' }
    : latestPublicPage.status === 'FAILED' && /로그인/.test(latestPublicPage.errorMessage ?? '') ? { label: '로그인 필요', tone: 'login', detail: latestPublicPage.errorMessage ?? '' }
      : latestPublicPage.status === 'FAILED' && latestPublicPage.publicPageFetched ? { label: '페이지 확인 완료 · AI 분석 실패', tone: 'fetched', detail: latestPublicPage.errorMessage ?? '' }
        : latestPublicPage.status === 'FAILED' ? { label: '공개 페이지 확인 실패', tone: 'failed', detail: latestPublicPage.errorMessage ?? '' }
      : latestPublicPage.publicPageFetched && ['WAITING_AI', 'ANALYZING'].includes(latestPublicPage.status) ? { label: '페이지 확보 · 문항 분석 중', tone: 'pending', detail: '실제 지원 페이지 원문을 저장했고 항목을 정리하고 있어요.' }
        : latestPublicPage.publicPageFetched ? { label: '공개 페이지 확인 완료', tone: 'fetched', detail: '이 공고에서 실제 지원 페이지 원문을 저장했어요.' }
            : ['WAITING_PUBLIC', 'FETCHING_PUBLIC', 'WAITING_AI', 'ANALYZING'].includes(latestPublicPage.status) ? { label: latestPublicPage.status === 'WAITING_PUBLIC' ? '확인 대기' : latestPublicPage.status === 'FETCHING_PUBLIC' ? '페이지 여는 중' : '문항 분석 중', tone: 'pending', detail: '지원 페이지 확인은 공고 본문 수집과 별도로 진행돼요.' }
              : { label: '이전 확인 기록 · 재확인 권장', tone: 'unknown', detail: '이전 기록에는 실제 지원 페이지 확인 여부가 남아 있지 않아요.' }
  return <section className="application-requirements" aria-label="지원서 요구 정보">
    <div className="application-requirements-heading"><div><h3>지원서 요구 정보</h3><p>지원 전 필요한 서류와 문항을 공고별로 정리해요.</p></div><span>확인 이력 {items.length}건</span></div>
    <div className={`application-company-page-status ${companyPageStatus.tone}`}>
      <div><strong>{job.company} · 지원 페이지</strong><span>{companyPageStatus.label}</span></div>
      <p>{companyPageStatus.detail}</p>
      <small><Icon name="info" size={12} /> 이 상태는 현재 공고에서 확인한 결과예요. 같은 회사도 공고별 지원 경로가 다를 수 있어요.</small>
    </div>
    <label className="application-requirements-input-label" htmlFor="application-requirements-text">실제 지원 페이지의 문항 또는 제출 안내 붙여넣기</label>
    <textarea id="application-requirements-text" value={rawText} onChange={event => setRawText(event.target.value)} placeholder="지원서에 표시된 빈 문항과 제출 안내를 복사해 붙여넣으세요. 개인정보나 작성한 답변은 넣지 않아도 돼요." rows={5} maxLength={100000} />
    <div className="application-requirements-actions"><span>{rawText.length.toLocaleString()} / 100,000자</span><button className="button primary" disabled={busy || rawText.trim().length < 20} onClick={() => void submit(rawText, 'USER_PASTED')}>AI로 문항 분석</button></div>
    <div className="application-requirements-actions application-secondary-actions">
      <button className="button secondary" disabled={busy} onClick={() => void collectPublicPage()}>{hasPublicPageHistory ? '공개 지원 페이지 다시 확인' : '공개 지원 페이지 지금 확인'}</button>
    </div>
    <p className="application-requirements-note">공고 수집 실행 중 이 회사의 각 열린 공고도 함께 확인해요. 지원 경로를 열어 페이지 전체 텍스트를 저장하고 AI가 실제 문항·제출 서류를 정리해요. 공고 본문에 있는 “지원하세요” 같은 문장은 지원서 항목으로 취급하지 않아요. 로그인·폼 입력·파일 업로드·제출은 하지 않으며, 로그인이 필요하면 상태와 이유를 표시해요. 원문과 AI 분석 요청은 Codex 서비스로 전달돼요.</p>
    {error && <p className="application-requirements-error" role="alert">{error}</p>}
    {loading ? <p className="application-requirements-empty">지원서 정보를 불러오는 중이에요.</p> : items.length === 0 ? <p className="application-requirements-empty">아직 정리된 지원서 정보가 없어요.</p> : <div className="application-requirements-list">{items.map(item => <article className="application-requirement-card" key={item.id}>
      <header><span className={`application-requirement-status ${item.status.toLowerCase()}`}>{statusLabel[item.status]}</span><div className="application-requirement-meta">{item.sourceUrl && item.source === 'PUBLIC_PAGE' && <a href={item.sourceUrl} target="_blank" rel="noreferrer">확인한 공개 페이지</a>}<time>{new Date(item.createdAt).toLocaleString('ko-KR')}</time></div></header>
      {item.source === 'POSTING_BODY' && <p className="application-requirements-warning">이전 버전에서 공고 본문으로 만든 기록이에요. 공고의 “지원하세요” 같은 문장은 실제 지원서 항목이 아니므로 이 기록의 분석 결과는 확정하거나 사용하지 마세요. 실제 지원 페이지를 다시 확인하거나 문항을 직접 붙여넣어 주세요.</p>}
      {item.source === 'PUBLIC_PAGE' && !item.publicPageFetched && item.rawText && <p className="application-requirements-warning">이전 기록에서 실제 지원 페이지를 확인했는지 판별할 수 없어요. 이 기록은 지원 항목으로 확정하지 말고 다시 확인해 주세요.</p>}
      <details><summary>분석 원문 보기 · {item.source === 'POSTING_BODY' ? '저장된 공고 본문' : item.source === 'PUBLIC_PAGE' ? '공개 지원 페이지' : '직접 붙여넣기'}</summary><pre>{item.rawText || (item.status === 'WAITING_PUBLIC' || item.status === 'FETCHING_PUBLIC' ? '공개 페이지 확인 후 원문을 표시합니다.' : '저장된 원문이 없어요.')}</pre></details>
      {item.result && item.source !== 'POSTING_BODY' && <>
        {item.result.documents.length > 0 && <section><h4>제출 서류</h4>{item.result.documents.map((document, index) => <div className="application-requirement-row" key={`${index}-${document.name}`}><strong>{document.name || '이름 확인 필요'}</strong><span>{document.requirement === 'UNKNOWN' ? '필수 여부 미확인' : document.requirement === 'REQUIRED' ? '필수' : '선택'}</span>{document.format && <p>형식: {document.format}</p>}{document.notes && <p>{document.notes}</p>}{document.evidence && <blockquote>{document.evidence}</blockquote>}</div>)}</section>}
        {item.result.questions.length > 0 && <section><h4>지원서 문항 {item.result.questions.length}개</h4>{item.result.questions.map((question, index) => <div className="application-requirement-row" key={`${index}-${question.prompt}`}><strong>{index + 1}. {question.prompt}</strong><span>{question.required === 'UNKNOWN' ? '필수 여부 미확인' : question.required === 'REQUIRED' ? '필수' : '선택'}</span><p>{[question.answerType, question.minLength != null || question.maxLength != null ? `${question.minLength ?? 0}~${question.maxLength ?? '제한 없음'}자` : ''].filter(Boolean).join(' · ')}</p>{question.instructions && <p>{question.instructions}</p>}{question.evidence && <blockquote>{question.evidence}</blockquote>}</div>)}</section>}
        {item.result.documents.length === 0 && item.result.questions.length === 0 && <p>{item.source === 'PUBLIC_PAGE' && item.publicPageFetched ? '지원 페이지 원문은 저장했지만 실제 문항이나 제출 서류는 찾지 못했어요. 원문을 확인해 주세요.' : '실제 지원 페이지 항목을 확인하지 못했어요. 공고 본문 문장은 지원 항목으로 분류하지 않습니다.'}</p>}
        {item.result.notes && <p>{item.result.notes}</p>}<small>AI 분석 신뢰도: {item.result.confidence}</small>
      </>}
      {item.errorMessage && <p className="application-requirements-error">{item.errorMessage}</p>}
      <footer>{item.status === 'REVIEW' && item.source !== 'POSTING_BODY' && (item.source !== 'PUBLIC_PAGE' || item.publicPageFetched) && <button className="button primary" disabled={busy} onClick={() => void update(item.id, 'confirm')}>확인 완료</button>}{item.status === 'FAILED' && item.source !== 'POSTING_BODY' && <button className="button secondary" disabled={busy} onClick={() => void update(item.id, 'retry')}>{item.source === 'PUBLIC_PAGE' && !item.rawText ? '공개 페이지 다시 확인' : '다시 분석'}</button>}</footer>
    </article>)}</div>}
  </section>
}
