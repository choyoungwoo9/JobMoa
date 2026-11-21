import { useMemo, useState } from 'react'
import './PostingMockGallery.css'

type MockPosting = {
  id: string
  company: string
  group: string
  title: string
  location: string
  firstSeen: string
  categories: string[]
  read: boolean
  closed: boolean
  status: '신규' | '변경' | ''
  versions: number
}

const roleNames = ['웹 백엔드', '웹 프론트엔드', '데이터 엔지니어', 'DevOps/SRE', 'ML 엔지니어', 'Android']
const seeds = [
  ['네이버', '네이버', 'Backend Engineer', '분당 · 하이브리드', ['웹 백엔드', 'DevOps/SRE']],
  ['토스', '토스', 'Server Developer (Payments)', '서울 강남', ['웹 백엔드']],
  ['카카오', '카카오', '플랫폼 백엔드 개발자', '제주 · 판교', ['웹 백엔드', '데이터 엔지니어']],
  ['쿠팡', '쿠팡', 'Senior Software Engineer, Search', '서울 잠실', ['웹 백엔드', 'ML 엔지니어']],
  ['당근', '당근', 'Server Engineer — Local Business', '서울 서초', ['웹 백엔드']],
  ['LINE', 'LINE', 'Backend Engineer, Messaging', '서울 · 도쿄', ['웹 백엔드', 'DevOps/SRE']],
  ['우아한형제들', '우아한형제들', '주문 플랫폼 서버 개발자', '서울 송파', ['웹 백엔드']],
  ['오늘의집', '오늘의집', 'Data Platform Engineer', '서울 서초', ['데이터 엔지니어', 'DevOps/SRE']],
  ['토스뱅크', '토스', 'SRE — Core Banking', '서울 강남', ['DevOps/SRE', '웹 백엔드']],
  ['카카오페이', '카카오', 'Android Engineer', '경기 성남', ['Android']],
  ['쿠팡이츠', '쿠팡', 'Data Engineer, Eats', '서울 잠실', ['데이터 엔지니어']],
  ['당근페이', '당근', 'ML Engineer — Trust', '서울 서초', ['ML 엔지니어', '데이터 엔지니어']],
  ['LINE Plus', 'LINE', 'Frontend Engineer', '서울 · 도쿄', ['웹 프론트엔드']],
  ['배민스토어', '우아한형제들', '서비스 플랫폼 개발자', '서울 송파', ['웹 백엔드', '웹 프론트엔드']],
  ['네이버클라우드', '네이버', 'Cloud Native Engineer', '분당', ['DevOps/SRE', '웹 백엔드']],
  ['카카오모빌리티', '카카오', '데이터 엔지니어', '경기 성남', ['데이터 엔지니어']],
]

const postings: MockPosting[] = Array.from({ length: 30 }, (_, index) => {
  const seed = seeds[index % seeds.length]
  const closed = [7, 13, 20, 26, 29].includes(index)
  return {
    id: `sample-${index + 1}`,
    company: seed[0] as string,
    group: seed[1] as string,
    title: `${seed[2] as string}${index >= seeds.length ? ` · ${['Payments', 'Core', 'Platform'][index % 3]}` : ''}`,
    location: seed[3] as string,
    firstSeen: `2026.11.${String(21 - (index % 18)).padStart(2, '0')}`,
    categories: seed[4] as string[],
    read: index % 4 === 2 || index % 7 === 5,
    closed,
    status: closed ? '' : index % 9 === 1 ? '신규' : index % 8 === 3 ? '변경' : '',
    versions: index % 5 === 0 ? 3 : index % 3 === 0 ? 2 : 1,
  }
})

const variants = [
  { id: 'cards', key: 'A', title: '태그 카드형', description: '공고마다 직군을 붙여 한눈에 훑고, 선택한 공고는 아래에서 확인해요.' },
  { id: 'table', key: 'B', title: '직군 열이 있는 표형', description: '회사·직무·마감 상태를 열로 나눠 많은 공고를 빠르게 비교해요.' },
  { id: 'split', key: 'C', title: '목록 + 상세 고정형', description: '목록을 유지하면서 오른쪽에서 본문과 캡처를 연속해서 확인해요.' },
] as const

type VariantId = (typeof variants)[number]['id']

export function PostingMockGallery() {
  const [variant, setVariant] = useState<VariantId>('cards')
  const [role, setRole] = useState('전체 직군')
  const [query, setQuery] = useState('')
  const [includeClosed, setIncludeClosed] = useState(false)
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [pageSize, setPageSize] = useState(8)
  const [page, setPage] = useState(1)
  const [selectedId, setSelectedId] = useState('sample-1')
  const [detailOpen, setDetailOpen] = useState(true)
  const [readState, setReadState] = useState<Record<string, boolean>>({})

  const filtered = useMemo(() => postings.filter((job) =>
    (includeClosed || !job.closed) &&
    (role === '전체 직군' || job.categories.includes(role)) &&
    (!unreadOnly || !(readState[job.id] ?? job.read)) &&
    `${job.title} ${job.company} ${job.location}`.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase()),
  ), [includeClosed, role, query, unreadOnly, readState])
  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize))
  const currentPage = Math.min(page, totalPages)
  const visible = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize)
  const selected = visible.find((job) => job.id === selectedId) ?? visible[0]
  const countOpen = postings.filter((job) => !job.closed && (role === '전체 직군' || job.categories.includes(role))).length
  const countClosed = postings.filter((job) => job.closed && (role === '전체 직군' || job.categories.includes(role))).length

  const chooseRole = (value: string) => { setRole(value); setPage(1) }
  const selectJob = (id: string) => {
    setSelectedId(id)
    setReadState((state) => ({ ...state, [id]: true }))
  }
  const navigateJob = (offset: number) => {
    const currentIndex = filtered.findIndex((job) => job.id === selected?.id)
    const next = filtered[currentIndex + offset]
    if (!next) return
    const nextIndex = filtered.findIndex((job) => job.id === next.id)
    setPage(Math.floor(nextIndex / pageSize) + 1)
    selectJob(next.id)
  }
  const toggleRead = (job: MockPosting) => setReadState((state) => ({ ...state, [job.id]: !(state[job.id] ?? job.read) }))
  const changePageSize = (value: number) => { setPageSize(value); setPage(1) }

  return (
    <div className="mock-gallery">
      <header className="mock-topbar">
        <a className="mock-wordmark" href="/" aria-label="JobMoa 실제 화면으로 돌아가기"><span>J</span> jobmoa<span className="mock-dot">.</span></a>
        <span className="mock-topbar-label">공고 목록 화면 시안</span>
        <a className="mock-exit" href="/">실제 화면으로 돌아가기 ↗</a>
      </header>
      <main className="mock-main">
        <section className="mock-intro">
          <span className="mock-eyebrow">LAYOUT PREVIEW · SAMPLE ONLY</span>
          <h1>공고를 훑는 화면, 어떤 방식이 좋을까요?</h1>
          <p>30개 샘플 공고로 직군 태그, 마감 상태, 페이지 크기, 상세 확인 흐름을 비교해보세요. 여기서 바꾼 읽음·필터 상태는 실제 데이터에 저장되지 않습니다.</p>
          <div className="mock-run-note"><span className="mock-live-dot" /> 마지막 수집 11.21 10:32 <i>·</i> 신규 8 <i>·</i> 변경 3 <i>·</i> 새로 마감 2 <span>샘플</span></div>
        </section>

        <nav className="mock-variant-tabs" aria-label="공고 목록 화면 시안">
          {variants.map((option) => (
            <button key={option.id} className={variant === option.id ? 'active' : ''} onClick={() => { setVariant(option.id); if (option.id === 'table') setDetailOpen(true) }} aria-pressed={variant === option.id}>
              <span className="mock-variant-key">{option.key}</span><span><strong>{option.title}</strong><small>{option.description}</small></span>
            </button>
          ))}
        </nav>

        <section className="mock-workspace" aria-label={`${variants.find((item) => item.id === variant)?.title} 시안`}>
          <div className="mock-workspace-heading">
            <div><span className="mock-eyebrow">공고 모아보기</span><h2>{variants.find((item) => item.id === variant)?.title}</h2></div>
            <span className="mock-data-badge">가상 공고 30개</span>
          </div>
          <div className="mock-toolbar">
            <label className="mock-search"><span>⌕</span><input aria-label="공고 검색" value={query} placeholder="회사·공고명·근무지 검색" onChange={(event) => { setQuery(event.target.value); setPage(1) }} /></label>
            <label className="mock-role-select"><span>직군</span><select aria-label="직군 선택" value={role} onChange={(event) => chooseRole(event.target.value)}>{['전체 직군', ...roleNames].map((name) => <option key={name}>{name}</option>)}</select></label>
            <button className={`mock-filter-pill ${unreadOnly ? 'selected' : ''}`} aria-pressed={unreadOnly} onClick={() => { setUnreadOnly(!unreadOnly); setPage(1) }}>○ 안 읽은 공고</button>
            <label className="mock-closed-toggle"><input type="checkbox" checked={includeClosed} onChange={(event) => { setIncludeClosed(event.target.checked); setPage(1) }} /><span>마감 포함 <b>{countClosed}</b></span></label>
            <label className="mock-page-size"><span>페이지당</span><select aria-label="페이지 크기" value={pageSize} onChange={(event) => changePageSize(Number(event.target.value))}><option value={8}>8개</option><option value={20}>20개</option><option value={50}>50개</option></select></label>
          </div>
          <div className="mock-results-summary"><strong>{filtered.length}개 공고</strong><span>열림 {countOpen} <i>·</i> 보관 마감 {countClosed}</span><span>{filtered.length ? `${(currentPage - 1) * pageSize + 1}–${Math.min(currentPage * pageSize, filtered.length)}개 표시` : '검색 결과 없음'}</span></div>

          {variant === 'cards' && <CardVariant jobs={visible} selected={selected} readState={readState} onSelect={selectJob} onToggleRead={toggleRead} onNavigate={navigateJob} />}
          {variant === 'table' && <TableVariant jobs={visible} selected={selected} readState={readState} onSelect={selectJob} onToggleRead={toggleRead} onNavigate={navigateJob} detailOpen={detailOpen} onOpen={() => setDetailOpen(true)} onClose={() => setDetailOpen(false)} />}
          {variant === 'split' && <SplitVariant jobs={visible} selected={selected} readState={readState} onSelect={selectJob} onToggleRead={toggleRead} onNavigate={navigateJob} />}

          <div className="mock-pagination"><span>페이지 {currentPage} / {totalPages}</span><div><button aria-label="이전 페이지" disabled={currentPage <= 1} onClick={() => setPage(currentPage - 1)}>‹</button><button aria-label="다음 페이지" disabled={currentPage >= totalPages} onClick={() => setPage(currentPage + 1)}>›</button></div></div>
        </section>
        <footer className="mock-footer">어느 시안이 가장 편한지 알려주세요. 예: “C에서 태그는 A처럼 보여줘”처럼 섞어서 선택해도 돼요.</footer>
      </main>
    </div>
  )
}

type VariantProps = { jobs: MockPosting[]; selected?: MockPosting; readState: Record<string, boolean>; onSelect: (id: string) => void; onToggleRead: (job: MockPosting) => void; onNavigate: (offset: number) => void }

function RoleChips({ job, compact = false }: { job: MockPosting; compact?: boolean }) {
  return <span className={`mock-role-chips ${compact ? 'compact' : ''}`}>{job.categories.map((category) => <span key={category}>{category}</span>)}</span>
}

function JobStatus({ job }: { job: MockPosting }) {
  return <span className="mock-statuses">{job.status && <b className={job.status === '신규' ? 'new' : 'changed'}>{job.status}</b>}{job.closed && <b className="closed">마감</b>}</span>
}

function CardVariant({ jobs, selected, readState, onSelect, onToggleRead, onNavigate }: VariantProps) {
  return <div className="mock-cards-layout">
    <div className="mock-card-grid">{jobs.map((job) => <article key={job.id} className={`mock-job-card ${selected?.id === job.id ? 'selected' : ''} ${job.closed ? 'is-closed' : ''}`}>
      <div className="mock-card-top"><label className="mock-read-check" title="샘플 읽음 상태"><input type="checkbox" checked={readState[job.id] ?? job.read} onChange={() => onToggleRead(job)} /><span /></label><span className="mock-company">{job.group}<small>{job.company}</small></span><JobStatus job={job} /></div>
      <button className="mock-card-title" onClick={() => onSelect(job.id)}>{job.title}</button>
      <RoleChips job={job} />
      <div className="mock-card-meta"><span>{job.location}</span><span>발견 {job.firstSeen}</span></div>
      {job.closed && <div className="mock-closed-note">보관 중 · 본문과 캡처 유지</div>}
    </article>)}</div>
    {selected && <DetailPreview job={selected} mode="wide" onNavigate={onNavigate} />}
  </div>
}

function TableVariant({ jobs, selected, readState, onSelect, onToggleRead, onNavigate, detailOpen, onOpen, onClose }: VariantProps & { detailOpen: boolean; onOpen: () => void; onClose: () => void }) {
  return <>
    <div className="mock-table-layout">
    <div className="mock-table-wrap"><table><thead><tr><th>읽음</th><th>회사</th><th>공고명</th><th>직군</th><th>근무지</th><th>발견</th><th>상태</th></tr></thead>
      <tbody>{jobs.map((job) => <tr key={job.id} className={`${selected?.id === job.id ? 'selected' : ''} ${job.closed ? 'is-closed' : ''}`} onClick={() => { onSelect(job.id); onOpen() }}>
        <td onClick={(event) => event.stopPropagation()}><label className="mock-read-check"><input type="checkbox" checked={readState[job.id] ?? job.read} onChange={() => onToggleRead(job)} /><span /></label></td>
        <td><b>{job.group}</b><small className="mock-table-company">{job.company}</small></td>
        <td><span className="mock-table-title">{job.title}</span><span className="mock-table-version">본문 · 캡처 저장 · 버전 {job.versions}</span></td>
        <td><RoleChips job={job} compact /></td><td>{job.location}</td><td>{job.firstSeen}</td>
        <td><JobStatus job={job} />{job.closed && <small className="mock-table-closed">보관 중</small>}</td>
      </tr>)}</tbody>
    </table></div>
    </div>
    {selected && detailOpen && <div className="mock-drawer-overlay" onClick={(event) => { if (event.target === event.currentTarget) onClose() }}><div className="mock-drawer-panel"><button className="mock-drawer-close" onClick={onClose} aria-label="공고 상세 닫기">상세 닫기 ×</button><DetailPreview job={selected} mode="side" onNavigate={onNavigate} /></div></div>}
  </>
}

function SplitVariant({ jobs, selected, readState, onSelect, onToggleRead, onNavigate }: VariantProps) {
  return <div className="mock-split-layout">
    <div className="mock-split-list" aria-label="공고 목록">
      {jobs.map((job) => <article key={job.id} className={`mock-split-row ${selected?.id === job.id ? 'selected' : ''} ${job.closed ? 'is-closed' : ''}`}>
        <label className="mock-read-check"><input type="checkbox" checked={readState[job.id] ?? job.read} onChange={() => onToggleRead(job)} /><span /></label>
        <button onClick={() => onSelect(job.id)}><span className="mock-company">{job.company}<JobStatus job={job} /></span><strong>{job.title}</strong><RoleChips job={job} compact /><small>{job.location} <i>·</i> {job.firstSeen}</small></button>
      </article>)}
    </div>
    {selected && <DetailPreview job={selected} mode="side" onNavigate={onNavigate} />}
  </div>
}

function DetailPreview({ job, mode, onNavigate }: { job: MockPosting; mode: 'wide' | 'side'; onNavigate: (offset: number) => void }) {
  const [tab, setTab] = useState<'capture' | 'body'>('capture')
  return <aside className={`mock-detail ${mode === 'side' ? 'side' : 'wide'}`}>
    <div className="mock-detail-heading"><div><span className="mock-eyebrow">저장된 공고</span><h3>{job.title}</h3></div><JobStatus job={job} /></div>
    <div className="mock-detail-company">{job.company} <i>·</i> {job.location} <i>·</i> 최초 발견 {job.firstSeen}</div>
    <RoleChips job={job} />
    {job.closed && <div className="mock-closure-evidence"><strong>마감 · 보관 중</strong><span>마감 확인 2026.11.20 · 사이트의 접수 종료 표시</span><small>기존 본문·캡처와 읽음/관심 정보는 유지돼요.</small></div>}
    <div className="mock-detail-toolbar"><div className="mock-detail-tabs"><button className={tab === 'capture' ? 'active' : ''} onClick={() => setTab('capture')}>캡처 보기</button><button className={tab === 'body' ? 'active' : ''} onClick={() => setTab('body')}>저장된 본문</button></div><span>버전 {job.versions}개</span></div>
    {tab === 'capture' ? <div className="mock-capture"><div className="mock-capture-bar"><i /><i /><i /><span>채용 페이지 캡처 · 저장본</span></div><div className="mock-capture-content"><span className="mock-capture-label">{job.company} CAREERS</span><h4>{job.title}</h4><p>{job.location} <i>·</i> 정규직</p><hr /><b>이런 일을 해요</b><p>서비스의 안정적인 운영과 확장을 위한 백엔드 시스템을 설계하고 개선합니다.</p><p>데이터 흐름과 사용자 경험을 함께 살피며 동료들과 문제를 해결합니다.</p></div></div> : <div className="mock-body"><h4>{job.title}</h4><b>담당 업무</b><p>대규모 트래픽을 처리하는 API와 데이터 파이프라인을 설계·운영합니다.</p><b>자격 요건</b><p>Kotlin, Java 또는 유사 언어를 활용한 서비스 개발 경험이 필요합니다.</p><b>근무지</b><p>{job.location}</p></div>}
    <div className="mock-detail-bottom"><button onClick={() => onNavigate(-1)}>← 이전 공고</button><span>공고 연속 탐색</span><button onClick={() => onNavigate(1)}>다음 공고 →</button></div>
  </aside>
}
