import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../api'
import type { Job } from '../types'
import { Icon } from './Icon'

type RequirementStatus = 'WAITING_PUBLIC' | 'FETCHING_PUBLIC' | 'SAVED' | 'FAILED'
type RequirementSource = 'POSTING_BODY' | 'USER_PASTED' | 'PUBLIC_PAGE'
interface Requirement {
  id: string
  source: RequirementSource
  sourceUrl: string | null
  rawText: string
  status: RequirementStatus
  attempts: number
  errorMessage: string | null
  createdAt: string
  updatedAt: string
  publicPageFetched: boolean
  versionSaved: boolean
  contentVersionId: string | null
}

const sourceLabel: Record<RequirementSource, string> = {
  POSTING_BODY: '이전 공고 본문 기록',
  USER_PASTED: '직접 입력한 원문',
  PUBLIC_PAGE: '자동 확인한 지원 페이지',
}

export function ApplicationRequirementsPanel({ job }: { job: Job }) {
  const [items, setItems] = useState<Requirement[]>([])
  const [selectedId, setSelectedId] = useState('')
  const [rawText, setRawText] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [dirty, setDirty] = useState(false)
  const [error, setError] = useState('')
  const selectedIdRef = useRef('')
  const dirtyRef = useRef(false)

  const refresh = useCallback(async () => {
    try {
      const next = await api<Requirement[]>(`/api/postings/${job.id}/application-requirements`)
      const history = next.filter(item => item.source !== 'POSTING_BODY')
      const versions = history.filter(item => item.versionSaved)
      setItems(next)
      const selected = versions.find(item => item.id === selectedIdRef.current)
      if (!selected && !dirtyRef.current) {
        const latest = history[0]
        const latestVersionId = latest?.contentVersionId ?? (latest?.versionSaved ? latest.id : '')
        const latestVersion = versions.find(item => item.id === latestVersionId)
        selectedIdRef.current = latestVersion?.id ?? ''
        setSelectedId(latestVersion?.id ?? '')
        setRawText(latestVersion?.rawText ?? '')
        dirtyRef.current = false
        setDirty(false)
      } else if (selected && !dirtyRef.current) {
        setRawText(selected.rawText)
      }
      setError('')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '지원서 원문을 불러오지 못했어요.')
    } finally {
      setLoading(false)
    }
  }, [job.id])

  useEffect(() => { void refresh() }, [refresh])
  const pending = items.some(item => item.source === 'PUBLIC_PAGE' && ['WAITING_PUBLIC', 'FETCHING_PUBLIC'].includes(item.status))
  useEffect(() => {
    if (!pending) return
    const timer = window.setInterval(() => void refresh(), 2500)
    return () => window.clearInterval(timer)
  }, [pending, refresh])

  const history = items.filter(item => item.source !== 'POSTING_BODY')
  const versions = history.filter(item => item.versionSaved)
  const latestActivity = history[0]
  const selected = versions.find(item => item.id === selectedId)
  const showVersionSelector = versions.length > 1 || (versions.length > 0 && latestActivity?.versionSaved !== true && !latestActivity?.contentVersionId)
  const changed = rawText.trim() !== (selected?.rawText ?? '')

  function selectVersion(id: string) {
    const item = versions.find(row => row.id === id)
    if (!item) return
    selectedIdRef.current = item.id
    setSelectedId(item.id)
    setRawText(item.rawText)
    dirtyRef.current = false
    setDirty(false)
  }

  async function save() {
    const text = rawText.trim()
    if (!text) { setError('저장할 지원서 원문을 입력해 주세요.'); return }
    if (text.length > 100_000) { setError('지원서 원문은 100,000자까지 저장할 수 있어요.'); return }
    setBusy(true)
    setError('')
    try {
      await api(`/api/postings/${job.id}/application-requirements`, {
        method: 'POST',
        body: JSON.stringify({ source: 'USER_PASTED', sourceUrl: job.url, rawText: text }),
      })
      selectedIdRef.current = ''
      dirtyRef.current = false
      setSelectedId('')
      setRawText('')
      setDirty(false)
      await refresh()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '새 원문 버전을 저장하지 못했어요.')
    } finally {
      setBusy(false)
    }
  }

  async function copy() {
    try {
      await navigator.clipboard.writeText(rawText)
      setError('')
    } catch {
      setError('클립보드에 복사하지 못했어요. 브라우저 권한을 확인해 주세요.')
    }
  }

  const statusLabel = latestActivity ? ({
    WAITING_PUBLIC: '지원 페이지 확인 대기',
    FETCHING_PUBLIC: '지원 페이지 확인 중',
    SAVED: '원문 저장됨',
    FAILED: '원문을 찾지 못함',
  } satisfies Record<RequirementStatus, string>)[latestActivity.status] : '원문 없음'

  const statusDetail = latestActivity?.status === 'FAILED'
    ? latestActivity.errorMessage || '공개 지원 페이지에서 원문을 가져오지 못했어요.'
    : latestActivity?.status === 'WAITING_PUBLIC'
      ? '공고 수집과 함께 지원 페이지를 확인하고 있어요.'
      : latestActivity?.status === 'FETCHING_PUBLIC'
        ? '공개 지원 페이지를 여는 중이에요.'
        : latestActivity?.status === 'SAVED' && !latestActivity.versionSaved && latestActivity.contentVersionId
          ? '기존 원문과 같아 새 버전을 저장하지 않았어요.'
          : latestActivity?.status === 'SAVED' && latestActivity.source === 'PUBLIC_PAGE'
            ? '공고 수집에서 새 원문 버전을 저장했어요.'
            : latestActivity?.status === 'SAVED'
              ? '직접 저장한 원문 버전이에요.'
            : '공고를 수집하면 지원 페이지 원문도 함께 확인해요.'

  return <section className="application-requirements" aria-label="지원서 원문">
    <div className="application-requirements-heading">
      <div><h3>지원서 원문</h3><p>수집 실행별 확인 상태와 원문 버전을 구분해 보여줘요. 내용이 같으면 새 버전은 만들지 않아요.</p></div>
      <span>원문 {versions.length}개</span>
    </div>
    <div className={`application-requirement-status-line ${latestActivity?.status.toLowerCase() ?? 'unknown'}`}>
      <div><strong>{statusLabel}</strong><span>{latestActivity ? `${sourceLabel[latestActivity.source]} · ${new Date(latestActivity.updatedAt).toLocaleString('ko-KR')}` : '자동 수집 기록 없음'}</span></div>
      <p>{statusDetail}</p>
      {latestActivity?.status === 'FAILED' && latestActivity.errorMessage && <small>실패 사유 · {latestActivity.errorMessage}</small>}
      {latestActivity?.source === 'PUBLIC_PAGE' && latestActivity.sourceUrl && <a href={latestActivity.sourceUrl} target="_blank" rel="noreferrer">지원 페이지 열기</a>}
    </div>
    <div className="application-requirement-editor-heading">
      <label htmlFor="application-requirements-text">지원 페이지 원문</label>
      {showVersionSelector && <label className="application-requirement-version">버전
        <select value={selected?.id ?? ''} onChange={event => selectVersion(event.target.value)} aria-label="지원서 원문 버전 선택">
          {!selected && <option value="" disabled>이전 원문 선택</option>}
          {versions.map(item => <option key={item.id} value={item.id}>{new Date(item.createdAt).toLocaleString('ko-KR')} · {sourceLabel[item.source]}</option>)}
        </select>
      </label>}
    </div>
    <textarea
      id="application-requirements-text"
      value={rawText}
      onChange={event => { setRawText(event.target.value); dirtyRef.current = true; setDirty(true) }}
      placeholder={latestActivity?.status === 'FAILED' && latestActivity.errorMessage ? `원문을 가져오지 못했어요. 실패 사유: ${latestActivity.errorMessage}\n\n직접 복사한 지원서 문항과 제출 안내를 붙여넣을 수 있어요.` : '공개 지원 페이지 원문이 아직 없어요. 공고 수집을 실행하면 자동으로 확인합니다. 필요하면 실제 지원 페이지에서 복사한 문항·제출 안내를 여기에 붙여넣으세요.'}
      rows={10}
      maxLength={100_000}
      disabled={loading}
      aria-label="지원서 원문 편집"
    />
    <div className="application-requirements-actions">
      <span>{rawText.length.toLocaleString()} / 100,000자</span>
      <div>
        <button className="button secondary" disabled={!rawText || busy || loading} onClick={() => void copy()}>원문 복사</button>
        <button className="button primary" disabled={!dirty || !changed || !rawText.trim() || busy || loading} onClick={() => void save()}>{busy ? '저장 중…' : '새 버전으로 저장'}</button>
      </div>
    </div>
    <p className="application-requirements-note"><Icon name="info" size={12} /> 지원 페이지 확인은 공고 수집 실행과 함께 진행돼요. 로그인·폼 입력·파일 업로드·제출은 하지 않습니다. 자동 확인에 실패하면 사유를 남기고, 실제 지원 페이지에서 복사한 원문을 이 칸에 붙여넣어 새 버전으로 저장할 수 있어요. 원문은 AI 분석 없이 로컬 JobMoa에 보관됩니다.</p>
    {error && <p className="application-requirements-error" role="alert">{error}</p>}
    {loading && <p className="application-requirements-empty">지원서 원문을 불러오는 중이에요.</p>}
  </section>
}
