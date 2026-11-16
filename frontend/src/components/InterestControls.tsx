import { useState } from 'react'
import type { InterestManagement, Job } from '../types'
import { interestOf, koreaToday, PRIORITIES } from '../interest'

export function InterestStar({ job, onChange }: { job: Job; onChange: (id: string, starred: boolean) => Promise<boolean> }) {
  const [busy, setBusy] = useState(false)
  const starred = interestOf(job).starred
  return <button type="button" className={`interest-star ${starred ? 'starred' : ''}`} aria-label={`${job.title} 관심 ${starred ? '해제' : '등록'}`} aria-pressed={starred} disabled={busy} title={starred ? '관심 해제 · 메모와 지원 기록은 유지' : '관심 공고에 추가'} onClick={async () => {
    setBusy(true)
    try { await onChange(job.id, !starred) } finally { setBusy(false) }
  }}><span aria-hidden="true">{starred ? '★' : '☆'}</span></button>
}

export function InterestEditor({ job, onSave }: { job: Job; onSave: (id: string, management: InterestManagement) => Promise<boolean> }) {
  const interest = interestOf(job)
  const [draft, setDraft] = useState<InterestManagement | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const value = draft ?? interest
  function edit(update: Partial<InterestManagement>) {
    setDraft({ note: value.note, priority: value.priority, applicationStatus: value.applicationStatus, appliedOn: value.appliedOn, ...update })
    setError('')
  }
  return <details className="interest-editor" open={interest.starred || !!interest.note || interest.applicationStatus === 'APPLIED' || undefined}>
    <summary>메모 · 우선순위 · 지원 관리{draft && <span>저장하지 않은 변경</span>}</summary>
    <form onSubmit={async event => {
      event.preventDefault()
      setSaving(true); setError('')
      try {
        const saved = await onSave(job.id, { note: value.note, priority: value.priority, applicationStatus: value.applicationStatus, appliedOn: value.appliedOn })
        if (saved) setDraft(null)
        else setError('저장하지 못했어요. 입력 내용은 유지되며 다시 저장할 수 있어요.')
      } finally { setSaving(false) }
    }}>
      <p>{interest.starred ? '관심을 해제해도 저장한 메모와 지원 기록은 남아요.' : '관심 등록 여부와 별도로 관리 정보를 저장할 수 있어요.'}</p>
      <fieldset disabled={saving}>
        <div className="interest-editor-fields">
          <label>우선순위<select aria-label="관심 공고 우선순위" value={value.priority} onChange={e => edit({ priority: e.target.value as InterestManagement['priority'] })}>{PRIORITIES.map(priority => <option key={priority.id} value={priority.id}>{priority.name}</option>)}</select></label>
          <label>지원 상태<select aria-label="관심 공고 지원 상태" value={value.applicationStatus} onChange={e => {
            const status = e.target.value as InterestManagement['applicationStatus']
            edit({ applicationStatus: status, appliedOn: status === 'APPLIED' ? value.appliedOn ?? koreaToday() : null })
          }}><option value="BEFORE_APPLY">지원 전</option><option value="APPLIED">지원 완료</option></select></label>
          {value.applicationStatus === 'APPLIED' && <label>지원일<input aria-label="지원일" type="date" max={koreaToday()} required value={value.appliedOn ?? ''} onChange={e => edit({ appliedOn: e.target.value || null })} /></label>}
        </div>
        <label className="interest-note-label">메모<textarea aria-label="관심 공고 메모" maxLength={5000} rows={4} value={value.note} placeholder="지원 전에 확인할 내용이나 관심 있는 이유를 적어보세요." onChange={e => edit({ note: e.target.value })} /></label>
      </fieldset>
      <div className="interest-editor-footer"><span>{value.note.length.toLocaleString()} / 5,000자 · 저장 버튼을 눌러 반영해요.</span><button className="button primary small" type="submit" disabled={!draft || saving}>{saving ? '저장 중…' : '관리 정보 저장'}</button></div>
      {error && <p role="alert" className="interest-save-error">{error}</p>}
    </form>
  </details>
}
