import { useEffect, useState } from 'react'
import { api } from '../api'
import { dateLabel } from '../data'
import type { CollectionRun } from '../types'
export interface ChangeSummary { runId: string; finished: boolean; legacy: boolean; new: string[]; changed: string[]; closed: string[]; interestChanged: string[]; interestClosed: string[] }
const kinds = [
  { key: 'new', label: '신규', interest: false },
  { key: 'interestChanged', label: '관심 변경', interest: true },
  { key: 'interestClosed', label: '관심 마감', interest: true },
  { key: 'changed', label: '전체 변경', interest: false },
  { key: 'closed', label: '전체 마감', interest: false },
] as const
export function CollectionChanges({ run, onBrowse, compact = false, onOpenRun }: { run: CollectionRun; onBrowse: (ids: string[], label: string) => void; compact?: boolean; onOpenRun?: () => void }) {
  const [summary, setSummary] = useState<ChangeSummary | null>(null)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  useEffect(() => {
    let disposed = false
    if (run.finishedAt) void api<ChangeSummary>(`/api/collections/${run.id}/changes`).then(data => { if (!disposed) { setSummary(data); setError('') } }).catch(e => { if (!disposed) setError(e.message) })
    return () => { disposed = true }
  }, [run.id, run.finishedAt, retry])
  if (!run.finishedAt) return null
  if (error) return <p role="alert" className={compact ? 'collection-changes-message' : undefined}>변화 요약을 불러오지 못했어요. <button className="text-button" onClick={() => setRetry(n => n + 1)}>다시 시도</button></p>
  if (!summary) return <p className={compact ? 'collection-changes-message' : 'diff-caption'}>최근 수집 변화를 불러오는 중…</p>
  const failed = run.results.flatMap(result => result.failures).length
  return <section className={`collection-changes${compact ? ' collection-changes-compact' : ''}`} aria-label={compact ? '최근 수집 요약' : '수집 후 변화 요약'}>
    {compact ? <>
      <div className="collection-changes-heading"><strong>최근 수집</strong><span>{dateLabel(run.startedAt, true)}</span></div>
      <div className="change-links">{kinds.map(({ key, label, interest }) => <button className={interest ? 'interest-change' : ''} disabled={!summary[key].length || summary.legacy && interest} key={key} onClick={() => onBrowse(summary[key], label === '신규' ? '신규 공고' : label)}>{label}<b>{summary.legacy && interest ? '—' : summary[key].length}</b></button>)}<button className="collection-failure-link" disabled={!failed} onClick={onOpenRun}>실패<b>{failed}</b></button></div>
      <button className="collection-run-link" onClick={onOpenRun}>수집 기록 <span aria-hidden="true">›</span></button>
      {summary.legacy && <small className="collection-legacy-note">이전 수집은 관심 공고 수치를 확인할 수 없어요.</small>}
    </> : <>
      <div><strong>이번 수집에서 달라진 점</strong><small>수집 당시 관심 표시 기준 · 숫자를 누르면 해당 공고만 보여요.</small></div>
      <div className="change-links">{kinds.map(({ key, label, interest }) => <button className={interest ? 'interest-change' : ''} disabled={!summary[key].length || summary.legacy && interest} key={key} onClick={() => onBrowse(summary[key], label === '신규' ? '신규 공고' : label)}>{label}<b>{summary.legacy && interest ? '—' : summary[key].length}</b></button>)}</div>
      {summary.legacy && <small>이전 수집 기록에는 당시 관심 여부와 마감 전환 정보가 없어 해당 수치를 확인할 수 없어요.</small>}
    </>}
  </section>
}
