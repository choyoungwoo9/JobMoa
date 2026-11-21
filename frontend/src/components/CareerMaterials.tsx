import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../api'
import { Icon } from './Icon'
import './CareerMaterials.css'

type MaterialStatus = 'WAITING_AI' | 'ANALYZING' | 'ANALYZED' | 'FAILED' | 'UNREADABLE'
interface MaterialSummary {
  id: string
  originalFilename: string
  detectedMediaType: string
  fileSize: number
  status: MaterialStatus
  errorMessage: string | null
  attempts: number
  createdAt: string
  updatedAt: string
  duplicate: boolean
}
interface Fragment { id: string; ordinal: number; locationLabel: string; content: string }
interface FactEvidence { id: string; fragmentId: string; locationLabel: string; excerpt: string }
interface CareerFact {
  id: string
  kind: 'EXPERIENCE' | 'PROJECT' | 'TECHNOLOGY' | 'ACHIEVEMENT' | 'EDUCATION' | 'OTHER'
  title: string
  summary: string
  status: 'UNVERIFIED' | 'VERIFIED' | 'REJECTED'
  evidence: FactEvidence[]
}
interface MaterialDetail { material: MaterialSummary; fragments: Fragment[]; facts: CareerFact[] }
interface WorkerStatus { connected: boolean; model: string }

const statusLabel: Record<MaterialStatus, string> = {
  WAITING_AI: '분석 대기', ANALYZING: '분석 중', ANALYZED: '분석 완료', FAILED: '분석 실패', UNREADABLE: '본문 확인 불가',
}
const kindLabel: Record<CareerFact['kind'], string> = {
  EXPERIENCE: '경력', PROJECT: '프로젝트', TECHNOLOGY: '기술', ACHIEVEMENT: '성과', EDUCATION: '학력·교육', OTHER: '기타',
}

export function CareerMaterials({ demo }: { demo: boolean }) {
  const [items, setItems] = useState<MaterialSummary[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [detail, setDetail] = useState<MaterialDetail | null>(null)
  const [worker, setWorker] = useState<WorkerStatus | null>(null)
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const fileInput = useRef<HTMLInputElement>(null)

  const refresh = useCallback(async () => {
    if (demo) return
    try {
      const [list, status] = await Promise.all([
        api<MaterialSummary[]>('/api/career-materials'),
        api<WorkerStatus>('/api/career-materials/worker/status'),
      ])
      setItems(list)
      setWorker(status)
      setError('')
      setSelectedId(current => current && list.some(item => item.id === current) ? current : list[0]?.id ?? null)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '경력 자료를 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }, [demo])

  useEffect(() => { void refresh() }, [refresh])
  useEffect(() => {
    if (!items.some(item => item.status === 'WAITING_AI' || item.status === 'ANALYZING')) return
    const timer = window.setInterval(() => { void refresh() }, 2500)
    return () => window.clearInterval(timer)
  }, [items, refresh])

  useEffect(() => {
    if (!selectedId || demo) { setDetail(null); return }
    let cancelled = false
    api<MaterialDetail>(`/api/career-materials/${selectedId}`)
      .then(value => { if (!cancelled) setDetail(value) })
      .catch(cause => { if (!cancelled) setError(cause instanceof Error ? cause.message : '자료 상세를 불러오지 못했습니다.') })
    return () => { cancelled = true }
  }, [selectedId, demo, items])

  const upload = async (files: FileList | null) => {
    if (!files?.length) return
    setBusy(true); setError(''); setNotice('')
    const body = new FormData()
    Array.from(files).forEach(file => body.append('files', file))
    try {
      const added = await api<MaterialSummary[]>('/api/career-materials', { method: 'POST', body })
      await refresh()
      setSelectedId(added[0]?.id ?? null)
      const duplicates = added.filter(item => item.duplicate).length
      setNotice(duplicates ? `${added.length - duplicates}개를 새로 등록하고, 내용이 같아 이미 있던 ${duplicates}개는 기존 자료를 열었어요.` : `${added.length}개 파일을 등록했어요. 읽을 수 있는 본문은 분석 대기열에 넣었습니다.`)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '파일을 등록하지 못했습니다.')
    } finally {
      setBusy(false)
      if (fileInput.current) fileInput.current.value = ''
    }
  }

  const retry = async (id: string) => {
    setBusy(true); setError('')
    try { await api(`/api/career-materials/${id}/retry`, { method: 'POST' }); await refresh() }
    catch (cause) { setError(cause instanceof Error ? cause.message : '분석을 다시 시작하지 못했습니다.') }
    finally { setBusy(false) }
  }

  const remove = async (item: MaterialSummary) => {
    if (!window.confirm(`“${item.originalFilename}” 파일과 추출 내용을 삭제할까요?`)) return
    setBusy(true); setError('')
    try { await api(`/api/career-materials/${item.id}`, { method: 'DELETE' }); setDetail(null); await refresh() }
    catch (cause) { setError(cause instanceof Error ? cause.message : '파일을 삭제하지 못했습니다.') }
    finally { setBusy(false) }
  }

  const selected = items.find(item => item.id === selectedId) ?? null

  if (demo) return <section className="career-empty"><Icon name="file" size={28} /><h2>실제 워크스페이스에서 등록해요</h2><p>경력 자료는 로컬 DB와 파일 저장소에 보관됩니다. 샘플 화면에서는 개인 파일을 올리지 않아요.</p></section>

  return (
    <section className="career-workspace" aria-label="경력 자료">
      <div className="career-privacy-note"><Icon name="info" size={17} /><span>원본 파일은 이 컴퓨터에 보관해요. Codex 워커가 켜져 있으면 추출한 텍스트가 <strong>{worker?.model ?? 'gpt-6-luna'}</strong> 분석으로 전송됩니다. 분석된 내용은 모두 확인 전 후보예요.</span></div>
      {error && <div className="career-alert" role="alert">{error}</div>}
      {notice && <div className="career-notice" role="status">{notice}</div>}
      <div className="career-toolbar">
        <div><strong>{items.length}개 자료</strong><span> 형식은 자동 판별하고, 같은 파일은 중복 저장하지 않아요.</span></div>
        <button className="button primary" disabled={busy} onClick={() => fileInput.current?.click()}><Icon name="plus" size={17} />{busy ? '파일 처리 중' : '파일 추가'}</button>
        <input ref={fileInput} className="career-file-input" type="file" multiple onChange={event => void upload(event.target.files)} aria-label="경력 자료 파일 선택" />
      </div>
      {!worker?.connected && <div className="career-worker-state"><span className="career-worker-dot" /><div><strong>Codex 워커가 꺼져 있어요</strong><p>파일 등록과 본문 추출은 계속할 수 있고, AI 정리는 대기합니다. 처리하려면 로컬에서 <code>./start.sh --ai-worker</code>로 실행하세요.</p></div></div>}

      {loading ? <div className="career-loading">자료를 불러오는 중…</div> : items.length === 0 ? (
        <div className="career-empty"><Icon name="file" size={28} /><h2>아직 등록한 경력 자료가 없어요</h2><p>이력서, 포트폴리오, 프로젝트 문서 등 파일을 한 번에 올려주세요. 형식을 고르거나 자료 종류를 나누지 않아도 됩니다.</p><button className="button primary" onClick={() => fileInput.current?.click()}>첫 파일 등록</button></div>
      ) : (
        <div className="career-split">
          <aside className="career-material-list" aria-label="등록된 파일">
            {items.map(item => (
              <button key={item.id} className={`career-material-row ${item.id === selectedId ? 'selected' : ''}`} onClick={() => setSelectedId(item.id)}>
                <Icon name="file" size={19} />
                <span className="career-material-name"><strong title={item.originalFilename}>{item.originalFilename}</strong><small>{formatBytes(item.fileSize)} · {item.detectedMediaType}</small></span>
                <span className={`career-status status-${item.status.toLowerCase()}`}>{statusLabel[item.status]}</span>
              </button>
            ))}
          </aside>
          <article className="career-detail">
            {!selected ? <div className="career-loading">자료를 선택하세요.</div> : <>
              <header className="career-detail-header">
                <div><span className={`career-status status-${selected.status.toLowerCase()}`}>{statusLabel[selected.status]}</span><h2>{selected.originalFilename}</h2><p>{formatBytes(selected.fileSize)} · {selected.detectedMediaType} · {new Date(selected.createdAt).toLocaleString('ko-KR')}</p></div>
                <div className="career-detail-actions">
                  <a className="button secondary" href={`/api/career-materials/${selected.id}/original`} download><Icon name="external" size={15} />원본</a>
                  {selected.status === 'FAILED' && <button className="button secondary" disabled={busy} onClick={() => void retry(selected.id)}><Icon name="refresh" size={15} />다시 분석</button>}
                  <button className="career-delete-button" disabled={busy} onClick={() => void remove(selected)} aria-label="파일 삭제"><Icon name="trash" size={16} /></button>
                </div>
              </header>
              {selected.errorMessage && <div className={`career-material-message ${selected.status === 'FAILED' || selected.status === 'UNREADABLE' ? 'warning' : ''}`}>{selected.errorMessage}</div>}
              {selected.duplicate && <div className="career-material-message">이미 등록된 파일과 내용이 같아 기존 자료를 열었어요.</div>}
              {detail?.material.id === selected.id && selected.status === 'ANALYZED' ? <>
                <div className="career-section-heading"><h3>AI가 찾은 내용</h3><span>{detail.facts.length}개 · 확인 전</span></div>
                {detail.facts.length ? <div className="career-facts">{detail.facts.map(fact => <section className="career-fact" key={fact.id}>
                  <div className="career-fact-kind">{kindLabel[fact.kind]} <span>확인 전</span></div><h4>{fact.title}</h4><p>{fact.summary}</p>
                  <div className="career-evidence-list">{fact.evidence.map(source => <blockquote key={source.id}><span>{source.locationLabel}</span>{source.excerpt}</blockquote>)}</div>
                </section>)}</div> : <div className="career-empty compact"><h3>확인 가능한 사실 후보가 없어요</h3><p>원문에서 근거가 있는 경력·프로젝트·기술 내용을 찾지 못했어요.</p></div>}
              </> : selected.status === 'WAITING_AI' || selected.status === 'ANALYZING' ? <div className="career-processing"><span className={selected.status === 'ANALYZING' ? 'career-spinner' : 'career-worker-dot'} /><div><strong>{selected.status === 'ANALYZING' ? 'Codex가 자료를 정리하고 있어요' : worker?.connected ? '분석 대기 중' : '워커가 켜지면 자동으로 분석해요'}</strong><p>파일은 저장됐고, 자료 종류나 이력서·포트폴리오 구분 없이 본문에서 근거 있는 후보를 찾습니다.</p></div></div> : detail?.material.id === selected.id && detail.fragments.length > 0 ? <div className="career-text-preview"><div className="career-section-heading"><h3>추출한 본문</h3><span>{detail.fragments.length}개 구간</span></div>{detail.fragments.slice(0, 12).map(fragment => <section key={fragment.id}><span>{fragment.locationLabel}</span><p>{fragment.content}</p></section>)}{detail.fragments.length > 12 && <p className="career-more-hint">나머지 {detail.fragments.length - 12}개 구간은 AI 분석에 함께 전달됩니다.</p>}</div> : null}
            </>}
          </article>
        </div>
      )}
      <div className="career-file-footnote"><Icon name="info" size={14} /><span>모든 파일을 등록할 수 있지만 파일 내용 추출은 Apache Tika가 읽을 수 있는 형식에 한해 동작합니다. 이미지 안의 글자는 OCR 설치 상태와 화질에 따라 달라질 수 있어요. 읽지 못한 파일도 원본을 보관하며, 사실 후보는 출처 위치와 원문 인용을 확인한 뒤 저장합니다.</span></div>
    </section>
  )
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
