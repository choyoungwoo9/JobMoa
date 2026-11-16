import { InterestComparison } from './InterestComparison'
import { useEffect, useState } from 'react'
import { dateLabel, SOURCES } from '../data'
import { interestOf, priorityOf } from '../interest'
import type { Category, InterestManagement, Job } from '../types'
import { BrandMark } from './BrandMark'
import { Icon } from './Icon'
import { JobDrawer } from './JobDrawer'
import { InterestStar } from './InterestControls'

type StatusFilter = 'all' | 'BEFORE_APPLY' | 'APPLIED'
const PAGE_SIZE = 12
export function InterestBoard({ jobs, categories, onStar, onSave, markRead, toggleRead, loadJob, onBrowse }: {
  jobs: Job[]
  categories: Category[]
  onStar: (id: string, starred: boolean) => Promise<boolean>
  onSave: (id: string, management: InterestManagement) => Promise<boolean>
  markRead: (id: string) => void
  toggleRead: (id: string) => void
  loadJob: (id: string) => void
  onBrowse: () => void
}) {
  const [compareIds, setCompareIds] = useState<string[]>([])
  const [comparing, setComparing] = useState(false)
  const compareJobs = compareIds.flatMap(id => jobs.find(job => job.id === id && interestOf(job).starred) ?? [])
  useEffect(() => { if (comparing) jobs.filter(job => compareIds.includes(job.id) && job.versions.some(version => version.bodyLoaded === false)).forEach(job => { void loadJob(job.id) }) }, [comparing, jobs, compareIds, loadJob])
  const [tab, setTab] = useState<StatusFilter>('BEFORE_APPLY')
  const [query, setQuery] = useState('')
  const [source, setSource] = useState('all')
  const [category, setCategory] = useState('all')
  const [includeClosed, setIncludeClosed] = useState(true)
  const [sort, setSort] = useState('priority')
  const [page, setPage] = useState(1)
  const [selected, setSelected] = useState<string | null>(null)
  const [queue, setQueue] = useState<string[]>([])
  const interests = jobs.filter(job => interestOf(job).starred)
  const filtered = interests.filter(job => (tab === 'all' || interestOf(job).applicationStatus === tab) &&
    (source === 'all' || job.sourceId === source) && (category === 'all' || (category === 'unclassified' ? !job.categories?.length : job.categories?.includes(category))) &&
    (includeClosed || !job.closed) && `${job.company} ${job.title} ${job.location} ${interestOf(job).note}`.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase()))
    .sort((a, b) => {
      const priority = priorityOf(interestOf(a).priority).rank - priorityOf(interestOf(b).priority).rank
      const latest = b.firstSeen.localeCompare(a.firstSeen) || b.id.localeCompare(a.id, undefined, { numeric: true })
      return sort === 'priority' ? priority || latest : sort === 'applied' ? (interestOf(b).appliedOn ?? '').localeCompare(interestOf(a).appliedOn ?? '') || latest : latest
    })
  const activeJob = jobs.find(job => job.id === selected)
  const activeVersionId = activeJob?.versions[0]?.id
  useEffect(() => { if (selected) void loadJob(selected) }, [selected, activeVersionId, loadJob])
  const displayed = selected ? queue.flatMap(id => jobs.find(job => job.id === id) ?? []) : filtered
  const pages = Math.max(1, Math.ceil(displayed.length / PAGE_SIZE))
  const safePage = Math.min(page, pages)
  const visible = displayed.slice((safePage - 1) * PAGE_SIZE, safePage * PAGE_SIZE)
  function filterChange() { setSelected(null); setPage(1) }
  function open(id: string) {
    if (!selected) setQueue(filtered.map(job => job.id))
    markRead(id); setSelected(id)
  }
  function navigate(offset: number) {
    const index = queue.indexOf(selected ?? '') + offset
    if (queue[index]) { markRead(queue[index]); setSelected(queue[index]); setPage(Math.floor(index / PAGE_SIZE) + 1) }
  }
  return <section className="interest-board" aria-label="관심 공고 목록">
    <div className="interest-toolbar">
      <div className="interest-tabs" role="tablist" aria-label="관심 공고 지원 상태">
        {([{ id: 'all', label: '전체' }, { id: 'BEFORE_APPLY', label: '지원 전' }, { id: 'APPLIED', label: '지원 완료' }] as const).map(item => <button key={item.id} role="tab" aria-selected={tab === item.id} className={tab === item.id ? 'active' : ''} onClick={() => { setTab(item.id); filterChange() }}>{item.label}<span>{interests.filter(job => item.id === 'all' || interestOf(job).applicationStatus === item.id).length}</span></button>)}
      </div>
      <span>높음 → 보통 → 낮음 → 미지정 · 같은 순위는 최근 발견순</span>
    </div>
    <div className="interest-filters">
      <label className="search-field"><Icon name="search" size={17} /><input aria-label="관심 공고 검색" placeholder="회사, 공고, 메모 검색" value={query} onChange={e => { setQuery(e.target.value); filterChange() }} /></label>
      <select aria-label="관심 공고 기업군" value={source} onChange={e => { setSource(e.target.value); filterChange() }}><option value="all">전체 기업군</option>{SOURCES.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select>
      <select aria-label="관심 공고 직군" value={category} onChange={e => { setCategory(e.target.value); filterChange() }}><option value="all">전체 직군</option>{categories.filter(item => item.id !== 'all').map(item => <option key={item.id} value={item.id}>{item.name}</option>)}<option value="unclassified">미분류</option></select>
      <select aria-label="관심 공고 정렬" value={sort} onChange={e => { setSort(e.target.value); filterChange() }}><option value="priority">우선순위 높은 순</option><option value="newest">최초 발견일 최신순</option><option value="applied">지원일 최신순</option></select>
      <label className="interest-closed"><input type="checkbox" checked={includeClosed} onChange={e => { setIncludeClosed(e.target.checked); filterChange() }} />마감 포함</label>
    </div>
    <div className="compare-toolbar"><span>비교할 관심 공고 2~3개를 선택하세요. <b>{compareJobs.length}/3</b></span><button className="button secondary small" disabled={compareJobs.length < 2} onClick={() => { compareJobs.forEach(job => markRead(job.id)); setComparing(true) }}>선택한 공고 비교</button>{compareIds.length > 0 && <button className="text-button" onClick={() => setCompareIds([])}>선택 해제</button>}</div>
    {comparing && <InterestComparison jobs={compareJobs} categories={categories} onClose={() => setComparing(false)} />}
    <div className={`interest-workspace ${activeJob ? 'has-detail' : ''}`}>
      <div className="interest-list">
        <div className="interest-list-heading"><span>{displayed.length}개 공고</span>{selected && <span>열어둔 목록 · 닫으면 필터 갱신</span>}</div>
        {visible.map(job => {
          const interest = interestOf(job)
          return <article className={`interest-card ${selected === job.id ? 'selected' : ''}`} key={job.id}>
            <label className="compare-select"><input type="checkbox" aria-label={`${job.title} 비교 선택`} checked={compareIds.includes(job.id)} disabled={!interest.starred || !compareIds.includes(job.id) && compareJobs.length >= 3} onChange={e => setCompareIds(ids => e.target.checked ? [...ids.filter(id => jobs.some(j => j.id === id && interestOf(j).starred)), job.id] : ids.filter(id => id !== job.id))} /><span>비교</span></label>
            <InterestStar job={job} onChange={onStar} />
            <button className="interest-card-main" onClick={() => open(job.id)} aria-current={selected === job.id ? 'true' : undefined}>
              <span className="interest-card-company"><BrandMark sourceId={job.sourceId} small />{job.company}{job.closed && <span className="closed-badge">마감</span>}{!interest.starred && <span className="closed-badge">관심 해제됨</span>}</span>
              <strong>{job.title}</strong>
              <span className="interest-card-meta"><span className={`priority-badge priority-${interest.priority.toLowerCase()}`}>{priorityOf(interest.priority).name}</span><span>{interest.applicationStatus === 'APPLIED' ? `지원 완료 · ${interest.appliedOn}` : '지원 전'}</span><span>{job.read ? '읽음' : '안 읽음'}</span><span>발견 {dateLabel(job.firstSeen)}</span></span>
              {interest.note && <span className="interest-card-note">{interest.note}</span>}
            </button>
            <a className="original-link" href={job.url} target="_blank" rel="noreferrer" aria-label={`${job.company} ${job.title} 원문 열기`}><Icon name="external" size={15} /></a>
          </article>
        })}
        {!displayed.length && <div className="empty-state"><span aria-hidden="true">☆</span><h3>{interests.length ? '조건에 맞는 관심 공고가 없어요' : '관심 있는 공고를 모아보세요'}</h3><p>{interests.length ? '지원 상태나 검색 조건을 바꿔보세요.' : '공고의 별표를 누르면 지원 후보를 이곳에서 관리할 수 있어요.'}</p><button className="button secondary small" onClick={interests.length ? () => { setTab('all'); setQuery(''); setSource('all'); setCategory('all'); setIncludeClosed(true); filterChange() } : onBrowse}>{interests.length ? '조회 조건 초기화' : '공고 둘러보기'}</button></div>}
        <div className="table-footer"><span>{safePage} / {pages}페이지</span><nav className="pagination" aria-label="관심 공고 페이지"><button aria-label="관심 공고 이전 페이지" disabled={safePage === 1} onClick={() => setPage(safePage - 1)}><Icon name="chevron" className="rotate-180" size={14} /></button><button aria-label="관심 공고 다음 페이지" disabled={safePage === pages} onClick={() => setPage(safePage + 1)}><Icon name="chevron" size={14} /></button></nav></div>
      </div>
      {activeJob && <JobDrawer key={activeJob.id} job={activeJob} categories={categories} onClose={() => setSelected(null)} onToggleRead={() => toggleRead(activeJob.id)} onStar={onStar} onSaveInterest={onSave} position={queue.indexOf(activeJob.id)} total={queue.length} onPrevious={() => navigate(-1)} onNext={() => navigate(1)} />}
    </div>
    <p className="interest-footnote">마감 공고도 관리할 수 있어요. 관심을 해제해도 저장한 메모·우선순위·지원 기록은 유지돼요.</p>
  </section>
}
