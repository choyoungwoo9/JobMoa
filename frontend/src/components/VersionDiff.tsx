import { useMemo, useState } from 'react'
import type { JobVersion } from '../types'
import { dateLabel } from '../data'
import { sentenceDiff } from '../diff'

export function VersionDiff({ versions, current }: { versions: JobVersion[]; current: JobVersion }) {
  const index = versions.findIndex(v => v.id === current.id)
  const [baseline, setBaseline] = useState('')
  const [full, setFull] = useState(false)
  const before = versions.find(v => v.id === baseline && v.id !== current.id) ?? versions[index + 1]
  const diff = useMemo(() => before && Math.max(before.body.length, current.body.length) <= 200_000 ? sentenceDiff(before.body, current.body) : null, [before, current])
  if (versions.length < 2) return <div className="diff-empty"><h3>아직 비교할 이전 버전이 없어요</h3><p>본문이 변경되어 새 버전이 저장되면 여기서 차이를 볼 수 있어요.</p></div>
  if (before && Math.max(before.body.length, current.body.length) > 200_000) return <p className="diff-empty">본문이 매우 길어 문장 비교를 생략했어요. 저장된 본문 탭에서 각 버전을 확인하세요. (비교 제한: 버전당 20만 자)</p>
  const changed = diff?.lines.flatMap((line, i) => line.kind !== 'same' ? [i] : []) ?? []
  const visible = diff?.lines.map((line, index) => ({ line, index })).filter(({ line, index }) => full || line.kind !== 'same' || changed.some(position => Math.abs(index - position) <= 3)) ?? []
  return <section className="version-diff" aria-label="공고 변경점 비교">
    <label className="diff-base">비교 기준<select aria-label="변경 비교 기준 버전" value={before?.id ?? ''} onChange={e => setBaseline(e.target.value)}>
      {!before && <option value="">비교할 버전 선택</option>}{versions.map((v, i) => v.id !== current.id && <option key={v.id} value={v.id}>v{versions.length - i} · {dateLabel(v.savedAt, true)}</option>)}
    </select></label>
    {!before ? <p>비교할 버전을 선택하세요.</p> : before.bodyLoaded === false || current.bodyLoaded === false ? <p>비교할 본문을 불러오는 중이에요…</p> : <>
      <div className="diff-summary"><span className="diff-plus">+ {diff!.lines.filter(l => l.kind === 'add' && l.text).length}문장 추가</span><span className="diff-minus">− {diff!.lines.filter(l => l.kind === 'remove' && l.text).length}문장 삭제</span><label><input type="checkbox" checked={full} onChange={e => setFull(e.target.checked)} />전체 본문</label></div>
      <p className="diff-caption">− 기준 버전 → + 선택한 버전 · 문장 번호 기준 · 캡처 이미지의 차이는 포함하지 않아요.</p>
      {diff!.coarse && <p>변경 범위가 커서 문장 묶음으로 표시해요.</p>}
      {!changed.length ? <p className="diff-empty">본문 문장의 변경이 없어요. 회사명·공고명·근무지 변경으로 저장된 버전일 수 있어요.</p> : <div className="diff-code" role="region" aria-label="추가 삭제 문장">
        {visible.map(({ line, index: i }, position) => {
          const gap = (position ? visible[position - 1].index : -1) < i - 1
          return <div key={i}>{gap && <div className="diff-gap">⋯ 동일한 문장 생략</div>}<div className={`diff-line diff-${line.kind}`}><span>{line.oldNumber ?? ''}</span><span>{line.newNumber ?? ''}</span><b aria-label={line.kind === 'add' ? '추가' : line.kind === 'remove' ? '삭제' : '동일'}>{line.kind === 'add' ? '+' : line.kind === 'remove' ? '−' : ' '}</b><code>{line.text || ' '}</code></div></div>
        })}
      </div>}
    </>}
  </section>
}
