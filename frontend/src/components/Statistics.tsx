import { useEffect, useState } from 'react'
import type { Category, Company, SourceId } from '../types'
import { SOURCES } from '../data'
import { Icon } from './Icon'
import './Statistics.css'

interface Summary {
  total: number; open: number; closed: number; unclassified: number; staleOpen: number
  discovered: number; closures: number; reopened: number; averageOpenAgeDays: number | null
  averageClosedDurationDays: number | null; medianClosedDurationDays: number | null
  durationSamples: number; averageClosureGapDays: number | null
}
interface Point { date: string; discovered: number; closures: number; reopened: number; observations: number }
interface Group { id: string; name: string; total: number; open: number; closed: number; discovered: number; closures: number; averageClosedDurationDays: number | null; durationSamples: number }
interface Report {
  asOf: string; from: string; to: string; timezone: string; lastObservedAt: string | null
  historyStartedAt: string | null; observedDays: number; summary: Summary; trend: Point[]; groups: Group[]
  ages: { label: string; count: number }[]
}
export interface StatisticsScope { sourceId: SourceId | 'all'; companyId: string | null; categoryId: string }
const today = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul' }).format(new Date())
function daysBefore(date: string, days: number) {
  const value = new Date(`${date}T00:00:00Z`); value.setUTCDate(value.getUTCDate() - days)
  return value.toISOString().slice(0, 10)
}
const count = (n: number) => n.toLocaleString('ko-KR')
const duration = (n: number | null) => n === null ? '—' : n > 0 && n < .1 ? '0.1일 미만' : `${n.toLocaleString('ko-KR', { maximumFractionDigits: 1 })}일`
const timestamp = (s: string | null) => s ? new Date(s).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul', month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '관측 없음'

function Trend({ points }: { points: Point[] }) {
  const [table, setTable] = useState(false)
  const [series, setSeries] = useState({ discovered: true, closures: true, reopened: true })
  const definitions = [{ key: 'discovered', name: '신규 발견', color: '#638a51' }, { key: 'closures', name: '마감 관측', color: '#c89447' }, { key: 'reopened', name: '재오픈 관측', color: '#8c7fb0' }] as const
  const enabled = definitions.filter(s => series[s.key])
  const max = Math.max(1, ...points.flatMap(p => enabled.map(s => p[s.key])))
  const plotWidth = 880, plotHeight = 205, left = 45, top = 15
  const step = plotWidth / Math.max(1, points.length)
  return <section className="stats-panel stats-trend">
    <div className="stats-section-heading"><div><h2>발견과 마감의 흐름</h2><p>선택 기간의 일별 기록 · 한국 시간</p></div><button className="text-button" aria-expanded={table} onClick={() => setTable(!table)}>{table ? '그래프로 보기' : '표로 보기'}</button></div>
    <div className="stats-legend">{definitions.map(s => <label key={s.key}><input type="checkbox" checked={series[s.key]} onChange={e => setSeries({ ...series, [s.key]: e.target.checked })} style={{ accentColor: s.color }} /><i style={{ background: s.color }} />{s.name}</label>)}</div>
    {table ? <div className="stats-table-scroll"><table className="stats-table"><thead><tr><th>날짜</th><th>신규 발견</th><th>마감 관측</th><th>재오픈</th><th>성공 관측</th></tr></thead><tbody>{points.map(p => <tr key={p.date}><th scope="row">{p.date}</th><td>{p.discovered}</td><td>{p.closures}</td><td>{p.reopened}</td><td>{p.observations || '미관측'}</td></tr>)}</tbody></table></div> : <div className="stats-chart-wrap"><svg viewBox="0 0 950 265" role="img" aria-label="일별 신규 발견·마감·재오픈 관측 건수. 표로 보기에서 정확한 값을 확인할 수 있어요.">
      {[...new Set([0, Math.ceil(max / 2), max])].map(tick => <g key={tick}><line x1={left} x2={left + plotWidth} y1={top + plotHeight * (1 - tick / max)} y2={top + plotHeight * (1 - tick / max)} stroke="#e8edde" /><text x={left - 10} y={top + plotHeight * (1 - tick / max) + 4} textAnchor="end">{tick}</text></g>)}
      {points.map((p, i) => <g key={p.date}>
        <title>{p.date}: 신규 {p.discovered}, 마감 {p.closures}, 재오픈 {p.reopened}, 성공 관측 {p.observations}회</title>
        {enabled.map((s, j) => <rect key={s.key} x={left + i * step + step * .12 + j * step * .76 / Math.max(1, enabled.length)} y={top + plotHeight - p[s.key] / max * plotHeight} width={Math.max(.5, step * .7 / Math.max(1, enabled.length))} height={p[s.key] / max * plotHeight} rx="1" fill={s.color} />)}
        {!p.observations && <circle cx={left + (i + .5) * step} cy={top + plotHeight + 7} r="1.5" fill="#c6ccbd" />}
        {(i === 0 || i === points.length - 1 || i % Math.max(1, Math.ceil(points.length / 6)) === 0) && <text x={left + (i + .5) * step} y="251" textAnchor="middle">{p.date.slice(5).replace('-', '.')}</text>}
      </g>)}
    </svg></div>}
    <p className="stats-note">회색 점은 성공한 공고 관측이 없는 날이에요. 수집하지 않은 날의 0건은 시장에 공고가 없었다는 뜻이 아니에요. 신규는 최초 발견일, 마감·재오픈은 상태 변화를 확인한 날로 집계해요.</p>
  </section>
}

export function Statistics({ categories, companies, demo, onBrowse }: { categories: Category[]; companies: Company[]; demo: boolean; onBrowse: (scope: StatisticsScope) => void }) {
  const [from, setFrom] = useState(() => daysBefore(today(), 29))
  const [to, setTo] = useState(today)
  const [sourceId, setSource] = useState<SourceId | 'all'>('all')
  const [companyId, setCompany] = useState('all')
  const [categoryId, setCategory] = useState('all')
  const [groupBy, setGroupBy] = useState('source')
  const [sort, setSort] = useState('open')
  const [refresh, setRefresh] = useState(0)
  const [report, setReport] = useState<Report | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [loadedKey, setLoadedKey] = useState('')
  const key = new URLSearchParams({ from, to, sourceId, companyId, categoryId, groupBy }).toString()
  const invalid = !from || !to || from > to || to > today() || (Date.parse(to) - Date.parse(from)) / 86_400_000 >= 366
  useEffect(() => {
    if (demo || invalid) return
    let disposed = false
    let active: AbortController | undefined
    async function load() {
      active?.abort(); active = new AbortController()
      setLoading(true); setError('')
      try {
        const response = await fetch(`/api/statistics?${key}`, { signal: active.signal })
        if (!response.ok) { const body = await response.json().catch(() => null); throw new Error(body?.message || `통계 조회 실패 (${response.status})`) }
        const next: Report = await response.json()
        if (!disposed) { setReport(next); setLoadedKey(key) }
      } catch (e) { if (!disposed && !(e instanceof DOMException && e.name === 'AbortError')) setError(e instanceof Error ? e.message : '통계를 불러오지 못했어요.') }
      finally { if (!disposed) setLoading(false) }
    }
    void load()
    const timer = setInterval(() => { void load() }, 30_000)
    return () => { disposed = true; active?.abort(); clearInterval(timer) }
  }, [key, refresh, demo, invalid])
  if (demo) return <section className="stats-panel stats-empty"><Icon name="chart" size={32} /><h2>통계는 실제 수집 기록으로 만들어요</h2><p>샘플 공고로 평균 게시 기간이나 채용 동향을 계산하지 않아요.</p><a className="button primary" href="/">실제 워크스페이스 열기</a></section>
  const s = report?.summary
  const ready = report && loadedKey === key && !invalid && !error
  const rows = report?.groups.slice().sort((a, b) => sort === 'duration' ? (b.averageClosedDurationDays ?? -1) - (a.averageClosedDurationDays ?? -1) : sort === 'discovered' ? b.discovered - a.discovered : b.open - a.open) ?? []
  function browse(group?: Group) {
    onBrowse({ sourceId: group && groupBy === 'source' ? group.id as SourceId : sourceId,
      companyId: group && groupBy === 'company' ? group.id : companyId === 'all' ? null : companyId,
      categoryId: group && groupBy === 'category' ? group.id : categoryId })
  }
  return <div className="statistics-view">
    <section className="stats-filters" aria-label="통계 필터">
      <div className="stats-filter-row">
        <label>사이트<select aria-label="통계 사이트" value={sourceId} onChange={e => { setSource(e.target.value as SourceId | 'all'); setCompany('all') }}><option value="all">전체 사이트</option>{SOURCES.map(s => <option value={s.id} key={s.id}>{s.name}</option>)}</select></label>
        <label>회사<select aria-label="통계 회사" value={companyId} onChange={e => setCompany(e.target.value)}><option value="all">전체 회사</option>{companies.filter(c => sourceId === 'all' || c.sourceId === sourceId).map(c => <option value={c.id} key={c.id}>{c.name}</option>)}</select></label>
        <label>직무<select aria-label="통계 직무" value={categoryId} onChange={e => setCategory(e.target.value)}>{categories.map(c => <option value={c.id} key={c.id}>{c.name}</option>)}<option value="unclassified">분야 미분류</option></select></label>
        <button className="button" disabled={loading || invalid} onClick={() => setRefresh(v => v + 1)}><Icon name="refresh" size={15} />{loading ? '계산 중' : '새로고침'}</button>
      </div>
      <div className="stats-period-row"><span>동향 기간</span><div className="stats-presets">{[7, 30, 90].map(n => <button key={n} className={from === daysBefore(today(), n - 1) && to === today() ? 'active' : ''} onClick={() => { setFrom(daysBefore(today(), n - 1)); setTo(today()) }}>최근 {n}일</button>)}</div><div className="stats-dates"><input type="date" aria-label="통계 시작일" value={from} max={to || today()} onChange={e => setFrom(e.target.value)} /><span>~</span><input type="date" aria-label="통계 종료일" value={to} min={from} max={today()} onChange={e => setTo(e.target.value)} /></div></div>
      <p className="stats-note">열린 공고·보관 마감·발견 후 경과는 현재 저장 상태예요. 기간 선택은 신규·마감·재오픈·완료 기간 평균에 적용돼요.</p>
    </section>
    {invalid && <p className="backend-error" role="alert">시작일과 종료일을 오늘까지 최대 366일로 선택해주세요.</p>}
    {error && <div className="backend-error" role="alert">{error} <button className="text-button" onClick={() => setRefresh(v => v + 1)}>다시 시도</button></div>}
    {!ready ? !invalid && !error && <div className="stats-panel stats-empty" role="status">통계를 계산하고 있어요.</div> : <>
      <div className="stats-freshness"><span className="tiny-dot" /><span>마지막 성공 관측 {timestamp(report.lastObservedAt)} · 기간 중 관측 {report.observedDays}일</span><span>집계 {timestamp(report.asOf)}{loading ? ' · 갱신 중' : ''}</span></div>
      <section className="stats-cards" aria-label="통계 요약">
        <article className="stats-card primary"><span>현재 열린 공고</span><strong>{count(s!.open)}<small>개</small></strong><p>저장된 상태 기준 · 보관 마감 {count(s!.closed)}개</p></article>
        <article className="stats-card"><span>기간 내 신규 발견</span><strong>{count(s!.discovered)}<small>개</small></strong><p>같은 공고는 한 번만 집계해요</p></article>
        <article className="stats-card"><span>기간 내 마감 관측</span><strong>{count(s!.closures)}<small>회</small></strong><p>재오픈 관측 {count(s!.reopened)}회</p></article>
        <article className="stats-card"><span>평균 열림 관측 기간</span><strong className="stats-duration">{duration(s!.averageClosedDurationDays)}</strong><p>{s!.durationSamples ? `마감까지 관측한 ${s!.durationSamples}개 기간 · 중앙값 ${duration(s!.medianClosedDurationDays)}` : '열림 → 마감 관측이 쌓이면 표시해요'}</p></article>
      </section>
      <div className="stats-context"><Icon name="info" size={17} /><div><strong>채용 시장 전체가 아닌, 내가 수집한 공고의 통계예요.</strong><p>전체 수집 공고에는 분야가 자동으로 붙지 않아요. 분야별 수집으로 연결한 공고만 직무 통계에 포함돼요. 현재 분야 미분류 {count(s!.unclassified)}개.{s!.staleOpen > 0 && ` 열린 공고 중 ${count(s!.staleOpen)}개는 7일 이상 확인하지 않았어요.`}</p></div></div>
      <Trend points={report.trend} />
      <section className="stats-panel">
        <div className="stats-section-heading"><div><h2>어디에서, 어떤 공고가 열려 있을까?</h2><p>현재 상태와 기간 내 변화를 함께 비교해요</p></div><div className="stats-table-controls"><select aria-label="통계 비교 기준" value={groupBy} onChange={e => setGroupBy(e.target.value)}><option value="source">사이트별</option><option value="company">회사별</option><option value="category">직무별</option></select><select aria-label="통계 정렬" value={sort} onChange={e => setSort(e.target.value)}><option value="open">열린 공고 많은 순</option><option value="discovered">신규 발견 많은 순</option><option value="duration">평균 기간 긴 순</option></select></div></div>
        <div className="stats-table-scroll"><table className="stats-table"><thead><tr><th>{groupBy === 'source' ? '사이트' : groupBy === 'company' ? '회사' : '직무'}</th><th>열림</th><th>보관 마감</th><th>기간 신규</th><th>기간 마감</th><th>평균 관측 기간</th><th><span className="sr-only">공고 이동</span></th></tr></thead><tbody>{rows.map(g => <tr key={g.id}><th scope="row">{g.name}<span className="stats-row-bar"><i style={{ width: `${g.open / Math.max(1, ...rows.map(r => r.open)) * 100}%` }} /></span></th><td><b>{count(g.open)}</b></td><td>{count(g.closed)}</td><td>{count(g.discovered)}</td><td>{count(g.closures)}</td><td>{duration(g.averageClosedDurationDays)}<small>{g.durationSamples ? `${g.durationSamples}개 기간` : '표본 없음'}</small></td><td><button className="text-button" onClick={() => browse(g)} disabled={groupBy === 'company' && !companies.some(c => c.id === g.id)} aria-label={`${g.name} 공고 보기`}>공고 보기<Icon name="chevron" size={12} /></button></td></tr>)}</tbody></table>{!rows.length && <div className="stats-empty">선택한 조건의 공고가 없어요. 분야별 수집 또는 다른 필터를 선택해보세요.</div>}</div>
        <p className="stats-note">{groupBy === 'category' ? '같은 공고가 여러 분야에 연결되면 각 행에 포함돼요. 행 합계는 중복 없는 전체 공고 수와 다를 수 있어요.' : '전체 공고 수는 URL 기준 중복을 제거해요. 열린 공고 수는 수집 시점 이후 원문과 달라졌을 수 있어요.'}</p>
      </section>
      <div className="stats-bottom-grid">
        <section className="stats-panel"><div className="stats-section-heading"><div><h2>열린 공고의 발견 후 경과</h2><p>최초 발견일부터 지금까지 · 평균 {duration(s!.averageOpenAgeDays)}</p></div></div><div className="stats-age-bars">{report.ages.map(b => <div key={b.label}><span>{b.label}</span><span className="stats-age-track"><i style={{ width: `${b.count / Math.max(1, s!.open) * 100}%` }} /></span><b>{count(b.count)}</b></div>)}</div><p className="stats-note">실제 게시 기간과 달라요. 오래 열려 있는 공고도 마감된 기간 평균에는 넣지 않아요.</p></section>
        <section className="stats-panel stats-method"><Icon name="history" size={20} /><h2>기간은 이렇게 계산해요</h2><p>열림을 처음 관측한 시각부터 마감을 처음 관측한 시각까지 계산해요. 실제 게시일·정확한 마감 시각은 알 수 없어 ‘평균 게시 기간’으로 부르지 않아요.</p><p>처음부터 마감된 공고는 제외하고, 재오픈하면 새 기간을 시작해요. 기간 필터는 마감을 관측한 날짜에 적용해요.</p><p>마지막 열림 확인과 마감 확인 사이 간격 평균: <b>{duration(s!.averageClosureGapDays)}</b>. 수집 간격이 길수록 마감 시각의 오차도 커져요.</p><p>상태 이력 시작: {timestamp(report.historyStartedAt)}. 기능 추가 전에는 마지막 저장 상태만 기준점으로 가져오며, 과거 마감·재오픈 이력을 만들지 않아요.</p></section>
      </div>
    </>}
  </div>
}
