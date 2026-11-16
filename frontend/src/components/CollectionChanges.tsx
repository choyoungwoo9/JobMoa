import { useEffect, useState } from 'react'
import { api } from '../api'
import type { CollectionRun } from '../types'
export interface ChangeSummary { runId: string; finished: boolean; legacy: boolean; new: string[]; changed: string[]; closed: string[]; interestChanged: string[]; interestClosed: string[] }
const kinds = [{ key: 'interestChanged', label: '관심 공고 내용 변경' }, { key: 'interestClosed', label: '관심 공고 새로 마감' }, { key: 'new', label: '신규 공고' }, { key: 'changed', label: '전체 내용 변경' }, { key: 'closed', label: '전체 새로 마감' }] as const
export function CollectionChanges({ run, onBrowse }: { run: CollectionRun; onBrowse: (ids: string[], label: string) => void }) {
  const [summary, setSummary] = useState<ChangeSummary | null>(null)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  useEffect(() => {
    let disposed = false
    if (run.finishedAt) void api<ChangeSummary>(`/api/collections/${run.id}/changes`).then(data => { if (!disposed) { setSummary(data); setError('') } }).catch(e => { if (!disposed) setError(e.message) })
    return () => { disposed = true }
  }, [run.id, run.finishedAt, retry])
  if (!run.finishedAt) return null
  if (error) return <p role="alert">변화 요약을 불러오지 못했어요. <button className="text-button" onClick={() => setRetry(n => n + 1)}>다시 시도</button></p>
  if (!summary) return <p className="diff-caption">변화 요약을 불러오고 있어요…</p>
  return <section className="collection-changes" aria-label="수집 후 변화 요약"><div><strong>이번 수집에서 달라진 점</strong><small>수집 당시 관심 표시 기준 · 숫자를 누르면 해당 공고만 보여요.</small></div><div className="change-links">{kinds.map(({ key, label }) => <button className={key.startsWith('interest') ? 'interest-change' : ''} disabled={!summary[key].length || summary.legacy && key.startsWith('interest')} key={key} onClick={() => onBrowse(summary[key], label)}>{label}<b>{summary.legacy && key.startsWith('interest') ? '—' : summary[key].length}</b></button>)}</div>{summary.legacy && <small>이전 수집 기록에는 당시 관심 여부와 마감 전환 정보가 없어 해당 수치를 확인할 수 없어요.</small>}</section>
}
