import { useEffect, useRef, useState } from 'react'
import { api } from '../api'
import { dateLabel } from '../data'

type Counts = Record<string, number>
interface Preview { token: string; exportedAt: string; expiresAt: string; counts: Counts; captureBytes: number; careerBytes: number; careerMaterialsIncluded: boolean }
const labels: Record<string, string> = { postings: '수집 공고', versions: '본문 버전', runs: '수집 기록', interests: '관심 표시', management: '개인 관리 정보', presets: '수집 프리셋', read: '읽은 공고', captureFiles: '캡처 파일', careerMaterials: '경력 자료', careerFacts: '경력 후보', careerFiles: '경력 원본' }
const scopes = [
  { id: 'interests', title: '관심 공고 초기화', description: '관심 표시와 모든 공고의 메모·우선순위·지원 기록을 지워요. 관심을 해제한 공고의 관리 정보도 포함해요. 공고와 캡처는 유지돼요.' },
  { id: 'postings', title: '수집 공고 초기화', description: '전체 공고·관심 정보·메모·지원 기록·본문 버전·캡처·수집 기록·통계 관측을 지워요. 수집 프리셋은 유지돼요.' },
  { id: 'read', title: '읽음 상태 초기화', description: '모든 공고를 안 읽음으로 바꿔요. 공고와 관심 정보는 유지돼요.' },
  { id: 'presets', title: '수집 프리셋 초기화', description: '저장한 직군·회사 선택 조합만 지워요.' },
  { id: 'career', title: '경력 자료 초기화', description: '등록한 원본 파일·추출 본문·AI가 찾은 확인 전 후보를 지워요. 채용 공고와 관심 정보는 유지돼요.' },
]
const sizeLabel = (size: number) => `${(size / 1024 / 1024).toFixed(1)} MB`
export function MyPage({ demo, running }: { demo: boolean; running: boolean }) {
  const [counts, setCounts] = useState<Counts>({})
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const [action, setAction] = useState<string | null>(null)
  const [confirmation, setConfirmation] = useState('')
  useEffect(() => { if (action) document.querySelector('.workspace-confirm')?.scrollIntoView({ behavior: 'smooth', block: 'center' }) }, [action])
  const [preview, setPreview] = useState<Preview | null>(null)
  const currentPreview = useRef<Preview | null>(null)
  const lock = useRef(false)
  async function refresh() { try { setCounts(await api<Counts>('/api/workspace')) } catch (e) { setError(String(e instanceof Error ? e.message : e)) } }
  useEffect(() => {
    let disposed = false
    if (!demo) void api<Counts>('/api/workspace').then(value => { if (!disposed) setCounts(value) }).catch(e => { if (!disposed) setError(e.message) })
    return () => { disposed = true; if (currentPreview.current) void api(`/api/workspace/backups/${currentPreview.current.token}`, { method: 'DELETE' }).catch(() => {}) }
  }, [demo])
  async function perform(work: () => Promise<void>) {
    if (lock.current) return
    lock.current = true; setBusy(true); setError(''); setNotice('')
    try { await work() } catch (e) { setError(e instanceof Error ? e.message : '요청을 처리하지 못했어요.') }
    finally { lock.current = false; setBusy(false) }
  }
  function chooseAction(value: string | null) { setAction(value); setConfirmation(''); setError('') }
  async function download() {
    await perform(async () => {
      const response = await fetch('/api/workspace/backup')
      if (!response.ok) throw new Error((await response.json().catch(() => null))?.message ?? '백업을 만들지 못했어요.')
      const url = URL.createObjectURL(await response.blob()), link = document.createElement('a')
      link.href = url; link.download = `jobmoa-${new Date().toISOString().slice(0, 10)}.zip`; link.click(); setTimeout(() => URL.revokeObjectURL(url), 60000)
      setNotice('백업 파일 다운로드를 시작했어요. 공고와 캡처, 경력 자료 원본, 개인 관리 정보, 프리셋과 수집 기록이 포함돼요.')
    })
  }
  async function inspect(file?: File) {
    if (!file) return
    await perform(async () => {
      if (currentPreview.current) await api(`/api/workspace/backups/${currentPreview.current.token}`, { method: 'DELETE' })
      setPreview(null); currentPreview.current = null
      const body = new FormData(); body.append('file', file)
      const result = await api<Preview>('/api/workspace/backups/preview', { method: 'POST', body })
      currentPreview.current = result; setPreview(result); chooseAction('restore')
    })
  }
  const disabled = demo || running || busy
  const selected = scopes.find(item => item.id === action)
  const word = action === 'restore' ? '복원' : action === 'cleanup' ? '정리' : '초기화'
  return <div className="my-page">
    <section className="workspace-card"><h2>내 데이터</h2><p>이 PC에 저장된 채용 공고와 경력 자료, 개인 관리 정보를 관리해요.</p>
      {demo ? <p className="workspace-warning">샘플 화면에서는 실제 데이터의 초기화·백업·복원을 사용할 수 없어요.</p> : <div className="workspace-counts">{Object.entries(counts).filter(([key]) => labels[key]).map(([key, value]) => <div key={key}><span>{labels[key]}</span><strong>{value.toLocaleString()}<small>개</small></strong></div>)}</div>}
      {!!counts.captureBytes && <small>캡처 저장 공간 {sizeLabel(counts.captureBytes)}</small>}
      {!!counts.careerBytes && <small>경력 원본 저장 공간 {sizeLabel(counts.careerBytes)}</small>}
      {running && <p className="workspace-warning">수집 중이에요. 수집이 끝나면 초기화·백업·복원을 사용할 수 있어요.</p>}
    </section>
    <section className="workspace-card"><h2>백업 · 복원</h2><p>공고·본문 버전·캡처·경력 자료 원본과 추출 결과·메모·지원 기록·프리셋·수집 기록을 ZIP 하나로 옮겨요. 복원하면 현재 데이터 전체가 백업 내용으로 교체돼요. 먼저 현재 데이터를 백업해두세요.</p>
      <div className="workspace-actions"><button className="button primary" disabled={disabled} onClick={() => void download()}>백업 다운로드</button><label className={`button secondary ${disabled ? 'disabled' : ''}`}>복원할 ZIP 선택<input type="file" accept=".zip,application/zip" aria-label="복원할 백업 파일" disabled={disabled} onChange={e => { void inspect(e.target.files?.[0]); e.target.value = '' }} hidden /></label><small>ZIP 2 GiB 이하 · 같은 백업 스키마 버전 지원</small></div>
      {preview && <div className="backup-preview"><h3>복원 미리보기</h3><p>백업 시각 {dateLabel(preview.exportedAt, true)} · 파일 무결성 확인 완료</p><div className="workspace-counts">{Object.entries(preview.counts).map(([key, value]) => <div key={key}><span>{labels[key] ?? key}</span><strong>{value}</strong></div>)}</div>{!preview.careerMaterialsIncluded && <p className="workspace-warning">이전 백업 형식이라 경력 자료를 포함하지 않아요. 복원하면 현재 경력 자료도 제거됩니다.</p>}<p>미리보기 유효 시간: {dateLabel(preview.expiresAt, true)}</p><button className="button secondary small" disabled={disabled} onClick={() => chooseAction('restore')}>이 백업으로 복원</button> <button className="text-button" disabled={busy} onClick={() => { void perform(async () => { await api(`/api/workspace/backups/${preview.token}`, { method: 'DELETE' }); setPreview(null); currentPreview.current = null; chooseAction(null) }) }}>복원 취소</button></div>}
    </section>
    <section className="workspace-card"><h2>데이터별 초기화</h2><p>한 번 지운 데이터는 백업 파일이 있어야 복원할 수 있어요.</p><div className="reset-list">{scopes.map(scope => <div key={scope.id}><div><h3>{scope.title}</h3><p>{scope.description}</p></div><button className="button secondary small danger" disabled={disabled} onClick={() => chooseAction(scope.id)}>초기화 선택</button></div>)}</div>
      <div className="orphan-cleanup"><span>이전 복원·저장 중단 등으로 남은 미사용 캡처와 경력 원본 파일만 정리할 수 있어요.</span><button className="text-button" disabled={disabled} onClick={() => chooseAction('cleanup')}>미사용 파일 정리</button></div>
    </section>
    {action && <section className="workspace-confirm" aria-label="데이터 변경 확인"><h3>{action === 'restore' ? '현재 전체 데이터를 백업 내용으로 교체합니다' : action === 'cleanup' ? '어떤 공고·작업에서도 참조하지 않는 캡처만 삭제합니다' : selected?.title}</h3><p>{action === 'restore' ? '현재 공고·메모·지원 기록·프리셋도 교체됩니다. 백업 다운로드를 먼저 완료했는지 확인하세요.' : selected?.description}</p><label>계속하려면 ‘{word}’ 입력<input autoComplete="off" aria-label="데이터 변경 확인 문구" value={confirmation} onChange={e => setConfirmation(e.target.value)} /></label><div className="workspace-actions"><button className="button primary danger" disabled={disabled || confirmation !== word || action === 'restore' && !preview} onClick={() => void perform(async () => {
      if (action === 'restore') { await api(`/api/workspace/backups/${preview!.token}/restore`, { method: 'POST', body: JSON.stringify({ confirmation }) }); currentPreview.current = null; sessionStorage.setItem('jobmoa.workspace.notice', '백업 복원을 완료했어요.'); window.location.reload() }
      else if (action === 'cleanup') { const result = await api<{ deleted: number }>('/api/workspace/captures/cleanup', { method: 'POST', body: JSON.stringify({ confirmation }) }); setNotice(`미사용 캡처 ${result.deleted}개를 정리했어요.`); chooseAction(null); await refresh() }
      else { const result = await api<{ cleanup: number }>('/api/workspace/reset', { method: 'POST', body: JSON.stringify({ scope: action, confirmation }) }); sessionStorage.setItem('jobmoa.workspace.notice', result.cleanup === -1 ? '초기화 완료. 남은 미사용 캡처는 마이페이지에서 정리할 수 있어요.' : '선택한 데이터 초기화를 완료했어요.'); window.location.reload() }
    })}>{word} 실행</button><button className="button secondary" disabled={busy} onClick={() => chooseAction(null)}>취소</button></div></section>}
    {busy && <p role="status">처리하고 있어요. 파일이 크면 잠시 걸릴 수 있어요.</p>}{error && <p role="alert" className="workspace-warning">{error}</p>}{notice && <p role="status" className="workspace-notice">{notice}</p>}
  </div>
}
