import { MyPage } from './components/MyPage'
import { CollectionChanges } from './components/CollectionChanges'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { SOURCES, dateLabel, sourceById, summarize } from './data'
import type { FailureStage, SourceId } from './types'
import { useCollection } from './useCollection'
import { BrandMark } from './components/BrandMark'
import { Icon } from './components/Icon'
import { CollectDialog } from './components/CollectDialog'
import { JobDrawer } from './components/JobDrawer'
import { RunCard } from './components/History'
import { Statistics, type StatisticsScope } from './components/Statistics'
import { InterestBoard } from './components/InterestBoard'
import { InterestStar } from './components/InterestControls'
import { interestOf } from './interest'
import { AiChat } from './components/AiChat'
import { PostingMockGallery } from './components/PostingMockGallery'
import { CategoryPicker } from './components/CategoryPicker'
import { CareerMaterials } from './components/CareerMaterials'
import { emptyNavigationSnapshot, readNavigationSnapshot, writeNavigationSnapshot, type AppNavigationSnapshot, type WorkspaceView } from './navigation'
import './App.css'

const PAGE_SIZES = [8, 20, 50]
function App() {
  if (new URLSearchParams(window.location.search).get('mock') === 'postings') {
    return <PostingMockGallery />
  }
  return <LiveApp />
}
function LiveApp() {
  const collection = useCollection()
  return <Workspace key={collection.data.workspaceRevision} collection={collection} />
}
function Workspace({ collection }: { collection: ReturnType<typeof useCollection> }) {
  const { data, notice, start, retryFailures, toggleRead, markRead, running, demo, connected, error, availableSources, categories, companies, loadJob, setStarred, saveInterest } = collection
  const [restoredNavigation] = useState(() => readNavigationSnapshot())
  const initialNavigation = restoredNavigation ?? emptyNavigationSnapshot()
  const [view, setView] = useState<WorkspaceView>(initialNavigation.view)
  const [careerTab, setCareerTab] = useState(initialNavigation.careerTab ?? 'materials')
  const [changeScope, setChangeScope] = useState<{ ids: string[]; label: string } | null>(initialNavigation.changeScope)
  const [categoryId, setCategoryId] = useState(initialNavigation.categoryId)
  const [source, setSource] = useState<SourceId | 'all'>(initialNavigation.source)
  const [companyFilter, setCompanyFilter] = useState<string | null>(initialNavigation.companyFilter)
  const [tab, setTab] = useState<'all' | 'unread' | 'new'>(initialNavigation.tab)
  const [query, setQuery] = useState(initialNavigation.query)
  const [sort, setSort] = useState(initialNavigation.sort)
  const [page, setPage] = useState(initialNavigation.page)
  const [pageSize, setPageSize] = useState(initialNavigation.pageSize)
  const [includeClosed, setIncludeClosed] = useState(initialNavigation.includeClosed)
  const [collectOpen, setCollectOpen] = useState(initialNavigation.collectOpen)
  const [selectedJob, setSelectedJob] = useState<string | null>(initialNavigation.selectedJob)
  const [browseQueue, setBrowseQueue] = useState<string[]>(initialNavigation.browseQueue)
  const [expandedRuns, setExpandedRuns] = useState<string[]>(restoredNavigation?.expandedRuns ?? [data.runs[0]?.id].filter((id): id is string => !!id))
  const pendingScrollRestore = useRef<{ y: number; tableTop: number; tableLeft: number } | null>(restoredNavigation ? { y: restoredNavigation.scrollY, tableTop: restoredNavigation.tableScrollTop, tableLeft: restoredNavigation.tableScrollLeft } : null)
  const latestRun = data.runs[0]
  const latest = latestRun ? summarize(latestRun) : null
  const captureNavigation = useCallback((): AppNavigationSnapshot => {
    const savedNavigation = readNavigationSnapshot()
    const savedInterest = savedNavigation?.interest
    const table = document.querySelector('.table-scroll')
    return {
      version: 1,
      view,
      careerTab,
      changeScope,
      categoryId,
      source,
      companyFilter,
      tab,
      query,
      sort,
      page,
      pageSize,
      includeClosed,
      collectOpen,
      modal: savedNavigation?.modal ?? null,
      selectedJob,
      browseQueue,
      expandedRuns,
      scrollY: window.scrollY,
      tableScrollTop: table?.scrollTop ?? 0,
      tableScrollLeft: table?.scrollLeft ?? 0,
      ...(savedInterest ? { interest: savedInterest } : {}),
    }
  }, [view, careerTab, changeScope, categoryId, source, companyFilter, tab, query, sort, page, pageSize, includeClosed, collectOpen, selectedJob, browseQueue, expandedRuns])
  const applyNavigation = useCallback((navigation: AppNavigationSnapshot) => {
    setView(navigation.view)
    setCareerTab(navigation.careerTab ?? 'materials')
    setChangeScope(navigation.changeScope)
    setCategoryId(navigation.categoryId)
    setSource(navigation.source)
    setCompanyFilter(navigation.companyFilter)
    setTab(navigation.tab)
    setQuery(navigation.query)
    setSort(navigation.sort)
    setPage(navigation.page)
    setPageSize(navigation.pageSize)
    setIncludeClosed(navigation.includeClosed)
    setCollectOpen(navigation.collectOpen)
    setSelectedJob(navigation.selectedJob)
    setBrowseQueue(navigation.browseQueue)
    setExpandedRuns(navigation.expandedRuns)
    pendingScrollRestore.current = { y: navigation.scrollY, tableTop: navigation.tableScrollTop, tableLeft: navigation.tableScrollLeft }
  }, [])
  const navigateApp = useCallback((patch: Partial<AppNavigationSnapshot>, mode: 'push' | 'replace' = 'push') => {
    const next = { ...captureNavigation(), ...patch }
    writeNavigationSnapshot(next, mode)
    applyNavigation(next)
  }, [captureNavigation, applyNavigation])
  const closeCollect = useCallback(() => {
    if (readNavigationSnapshot()?.collectOpen) window.history.back()
    else setCollectOpen(false)
  }, [])
  const closeJob = useCallback(() => {
    if (readNavigationSnapshot()?.selectedJob) window.history.back()
    else setSelectedJob(null)
  }, [])
  useEffect(() => {
    const onPopState = (event: PopStateEvent) => {
      const navigation = readNavigationSnapshot(event.state)
      if (navigation) applyNavigation(navigation)
    }
    window.addEventListener('popstate', onPopState)
    return () => window.removeEventListener('popstate', onPopState)
  }, [applyNavigation])
  useEffect(() => {
    const saved = readNavigationSnapshot()
    const navigation = captureNavigation()
    if (saved?.interest) navigation.interest = saved.interest
    writeNavigationSnapshot(navigation)
  }, [captureNavigation])
  useEffect(() => {
    const pending = pendingScrollRestore.current
    if (!pending) return
    pendingScrollRestore.current = null
    requestAnimationFrame(() => {
      window.scrollTo({ top: pending.y })
      const table = document.querySelector('.table-scroll')
      if (table) {
        table.scrollTop = pending.tableTop
        table.scrollLeft = pending.tableLeft
      }
    })
  }, [view, careerTab, changeScope, categoryId, source, companyFilter, tab, query, sort, page, pageSize, includeClosed, collectOpen, selectedJob, browseQueue, expandedRuns])
  useEffect(() => {
    const previousRestoration = window.history.scrollRestoration
    window.history.scrollRestoration = 'manual'
    let frame = 0
    const saveScroll = () => {
      if (frame) return
      frame = requestAnimationFrame(() => {
        frame = 0
        const current = readNavigationSnapshot()
        if (current) writeNavigationSnapshot({ ...captureNavigation(), interest: current.interest })
      })
    }
    window.addEventListener('scroll', saveScroll, { passive: true })
    const table = document.querySelector('.table-scroll')
    table?.addEventListener('scroll', saveScroll, { passive: true })
    return () => {
      if (frame) cancelAnimationFrame(frame)
      window.removeEventListener('scroll', saveScroll)
      table?.removeEventListener('scroll', saveScroll)
      window.history.scrollRestoration = previousRestoration
    }
  }, [captureNavigation])
  const collectionCategoryId = categoryId === 'unclassified' || (!demo && !companies.some(company => company.supportedCategoryIds.includes(categoryId))) ? 'all' : categoryId
  const categoryName = (id = 'all') => id === 'unclassified' ? '미분류' : categories.find(category => category.id === id)?.name ?? '기타 분야'
  const categoryJobs = data.jobs.filter(job => (!changeScope || changeScope.ids.includes(job.id)) && (categoryId === 'all' || (categoryId === 'unclassified' ? !job.categories?.length : job.categories?.includes(categoryId))) && (!companyFilter || job.companyId === companyFilter))
  const available = categoryJobs.filter((job) => includeClosed || !job.closed)
  const unread = available.filter((job) => !job.read).length
  const newCount = available.filter((job) => job.latestChange === 'new').length
  const closedCount = categoryJobs.filter((job) => job.closed).length
  const categoryCounts = useMemo(() => {
    const scoped = data.jobs.filter(job =>
      (!changeScope || changeScope.ids.includes(job.id)) &&
      (includeClosed || !job.closed) &&
      (source === 'all' || job.sourceId === source) &&
      (!companyFilter || job.companyId === companyFilter),
    )
    const counts: Record<string, number> = { all: scoped.length }
    for (const job of scoped) {
      for (const id of job.categories ?? []) counts[id] = (counts[id] ?? 0) + 1
    }
    return counts
  }, [data.jobs, changeScope, includeClosed, source, companyFilter])

  const filtered = useMemo(
    () =>
      data.jobs
        .filter(
          (job) =>
            (!changeScope || changeScope.ids.includes(job.id)) &&
            (categoryId === 'all' || (categoryId === 'unclassified' ? !job.categories?.length : !!job.categories?.includes(categoryId))) &&
            (!companyFilter || job.companyId === companyFilter) &&
            (includeClosed || !job.closed) &&
            (source === 'all' || job.sourceId === source) &&
            (tab !== 'unread' || !job.read) &&
            (tab !== 'new' || job.latestChange === 'new') &&
            `${job.title} ${job.company} ${job.location}`
              .toLocaleLowerCase()
              .includes(query.trim().toLocaleLowerCase()),
        )
        .sort((a, b) =>
          sort === 'company'
            ? a.company.localeCompare(b.company, 'ko')
            : sort === 'oldest'
              ? a.firstSeen.localeCompare(b.firstSeen)
              : b.firstSeen.localeCompare(a.firstSeen),
        ),
    [data.jobs, changeScope, categoryId, companyFilter, includeClosed, source, tab, query, sort],
  )
  const listJobs = filtered
  const totalPages = Math.max(1, Math.ceil(listJobs.length / pageSize))
  const safePage = Math.min(page, totalPages)
  const visibleJobs = listJobs.slice(
    (safePage - 1) * pageSize,
    safePage * pageSize,
  )
  const activeJob = data.jobs.find((job) => job.id === selectedJob)
  const activeVersionId = activeJob?.versions[0]?.id
  useEffect(() => {
    if (selectedJob) void loadJob(selectedJob)
  }, [selectedJob, activeVersionId, loadJob])
  const changeSource = (id: SourceId | 'all') => {
    navigateApp({ changeScope: null, source: id, companyFilter: null, view: 'jobs', page: 1, selectedJob: null, scrollY: 0, tableScrollTop: 0, tableScrollLeft: 0 })
  }
  const browseChanges = (ids: string[], label: string) => {
    navigateApp({ view: 'jobs', changeScope: { ids, label }, categoryId: 'all', source: 'all', companyFilter: null, tab: 'all', query: '', includeClosed: true, selectedJob: null, page: 1, scrollY: 0, tableScrollTop: 0, tableScrollLeft: 0 })
  }
  const openJob = (id: string) => {
    const queue = selectedJob ? browseQueue : filtered.map((job) => job.id)
    markRead(id)
    navigateApp({ selectedJob: id, browseQueue: queue })
  }
  const navigateJob = (offset: number) => {
    const position = browseQueue.indexOf(selectedJob ?? '') + offset
    const id = browseQueue[position]
    if (id) {
      markRead(id)
      navigateApp({ selectedJob: id, page: Math.floor(position / pageSize) + 1 })
    }
  }
  const openRun = (id: string) => {
    const expanded = expandedRuns.includes(id) ? expandedRuns : [id, ...expandedRuns]
    if (view === 'history' && expandedRuns.includes(id)) return
    navigateApp({ view: 'history', expandedRuns: expanded, selectedJob: null, scrollY: 0, tableScrollTop: 0, tableScrollLeft: 0 })
  }
  const toggleRun = (id: string) => {
    const expanded = expandedRuns.includes(id) ? expandedRuns.filter(runId => runId !== id) : [id, ...expandedRuns]
    navigateApp({ expandedRuns: expanded })
  }
  const begin = async (ids: SourceId[], failure: FailureStage | null, selectedCategory: string, companyIds: string[], categoryIds?: string[]) => {
    const id = await start(ids, failure, selectedCategory, companyIds, undefined, categoryIds)
    if (id) {
      closeCollect()
      setExpandedRuns((items) => [id, ...items])
    }
  }
  const retry = async (runId: string, ids?: string[]) => {
    const id = await retryFailures(runId, ids)
    if (id) openRun(id)
  }
  const resetFilters = () => {
    setChangeScope(null)
    setCompanyFilter(null)
    setSource('all')
    setTab('all')
    setQuery('')
    setPage(1)
    setSelectedJob(null)
  }

  const browseStatistics = (scope: StatisticsScope) => {
    navigateApp({ changeScope: null, source: scope.sourceId, companyFilter: scope.companyId, categoryId: scope.categoryId, view: 'jobs', includeClosed: true, tab: 'all', query: '', page: 1, selectedJob: null, scrollY: 0, tableScrollTop: 0, tableScrollLeft: 0 })
  }

  const openAiPosting = (postingId: number) => {
    const id = String(postingId)
    markRead(id)
    navigateApp({ view: 'jobs', changeScope: null, categoryId: 'all', source: 'all', companyFilter: null, tab: 'all', query: '', includeClosed: true, page: 1, browseQueue: [id], selectedJob: id, scrollY: 0, tableScrollTop: 0, tableScrollLeft: 0 })
  }

  return (
    <div
      className={`app-shell ${view === 'jobs' && activeJob ? 'reading-mode' : ''}`}
    >
      <aside className="sidebar">
        <button
          className="wordmark"
          onClick={() => {
            navigateApp({ view: 'jobs', changeScope: null, source: 'all', companyFilter: null, tab: 'all', query: '', page: 1, selectedJob: null, scrollY: 0, tableScrollTop: 0, tableScrollLeft: 0 })
          }}
          aria-label="JobMoa 홈"
        >
          <span className="logo-symbol">
            <Icon name="layers" size={23} />
          </span>
          jobmoa<span className="wordmark-dot">.</span>
        </button>
        <div className="workspace-label">MY WORKSPACE</div>
        <nav className="main-nav" aria-label="주 메뉴">
          <button
            className={view === 'jobs' ? 'active' : ''}
            onClick={() => changeSource('all')}
          >
            <Icon name="grid" size={18} />
            <span>공고 모아보기</span>
            <span className="nav-count">{available.length}</span>
          </button>
          <button className={view === 'interests' ? 'active' : ''} onClick={() => navigateApp({ view: 'interests', selectedJob: null, scrollY: 0, tableScrollTop: 0, tableScrollLeft: 0 })}><Icon name="star" size={18} /><span>관심 공고</span><span className="nav-count">{data.jobs.filter(job => interestOf(job).starred).length}</span></button>
          <button
            className={view === 'history' ? 'active' : ''}
            onClick={() => navigateApp({ view: 'history', selectedJob: null, scrollY: 0, tableScrollTop: 0, tableScrollLeft: 0 })}
          >
            <Icon name="history" size={18} />
            <span>수집 기록</span>
            {running && <span className="live-dot" />}
          </button>
          <button className={view === 'statistics' ? 'active' : ''} onClick={() => navigateApp({ view: 'statistics', selectedJob: null, categoryId: 'all', source: 'all', companyFilter: null, scrollY: 0, tableScrollTop: 0, tableScrollLeft: 0 })}><Icon name="chart" size={18} /><span>채용 통계</span></button>
          <button className={view === 'ai' ? 'active' : ''} onClick={() => navigateApp({ view: 'ai', selectedJob: null, scrollY: 0, tableScrollTop: 0, tableScrollLeft: 0 })}><Icon name="spark" size={18} /><span>AI에게 질문</span></button>
          <button className={view === 'career' ? 'active' : ''} onClick={() => navigateApp({ view: 'career', selectedJob: null, scrollY: 0, tableScrollTop: 0, tableScrollLeft: 0 })}><Icon name="file" size={18} /><span>경력 자료</span></button>
          <button className={view === 'my' ? 'active' : ''} onClick={() => navigateApp({ view: 'my', selectedJob: null, scrollY: 0, tableScrollTop: 0, tableScrollLeft: 0 })}><Icon name="layers" size={18} /><span>마이페이지</span></button>
        </nav>
        <div className="source-section-heading">
          <span>관심 기업군</span>
          <span>{SOURCES.length}</span>
        </div>
        <nav className="source-nav" aria-label="기업군 선택">
          <button
            className={source === 'all' && view === 'jobs' ? 'selected' : ''}
            onClick={() => changeSource('all')}
          >
            <span className="all-source-icon">
              <Icon name="layers" size={16} />
            </span>
            <span>전체 기업</span>
            <span className="source-count">{available.length}</span>
          </button>
          {SOURCES.map((item) => (
            <button
              key={item.id}
              className={
                source === item.id && view === 'jobs' ? 'selected' : ''
              }
              onClick={() => changeSource(item.id)}
            >
              <BrandMark sourceId={item.id} small />
              <span>{item.name}</span>
              <span className="source-count">
                {available.filter((job) => job.sourceId === item.id).length}
              </span>
            </button>
          ))}
        </nav>
        <div className="affiliate-note">
          <span className="tiny-dot" />각 기업군의 계열사 포함
        </div>
        <div className="sidebar-bottom">
          <div className="local-box">
            <span className="local-icon">
              <Icon name="monitor" size={18} />
            </span>
            <div>
              <strong>나만의 로컬 공간</strong>
              <span>필요할 때 켜고, 한 번에 수집</span>
            </div>
            <span className="local-dot" />
          </div>
          <div className="sidebar-footer">
            <span>JobMoa</span>
            <span>{demo ? 'Frontend preview' : 'Local workspace'} · v0.1</span>
          </div>
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <div className="breadcrumb">
            <Icon name="layers" size={16} />
            <span>내 워크스페이스</span>
            <Icon name="chevron" size={12} />
            <strong>{view === 'jobs' ? '공고 모아보기' : view === 'my' ? '마이페이지' : view === 'interests' ? '관심 공고' : view === 'statistics' ? '채용 통계' : view === 'ai' ? 'AI에게 질문' : view === 'career' ? '경력 자료' : '수집 기록'}</strong>
          </div>
          <div className="topbar-right">
            <span className="preview-badge">
              <span />
              {demo ? '화면 검토용 · 샘플 데이터' : connected ? '로컬 DB 연결' : '백엔드 연결 중'}
            </span>
            <a className="text-button" href={demo ? '/' : '/?demo=1'}>{demo ? '실제 공고' : '샘플 보기'}</a>
            <span className="avatar" aria-label="개인 워크스페이스">
              J
            </span>
          </div>
        </header>
        <main>
          <div className={`page-heading ${view === 'jobs' ? 'jobs-page-heading' : ''}`}>
            <div>
              {view !== 'jobs' && <div className="eyebrow">
                A LITTLE CLOSER TO YOUR NEXT CHAPTER
              </div>}
              <h1>
                {view === 'jobs'
                  ? source === 'all'
                    ? categoryId === 'all' ? '공고 모아보기' : `${categoryName(categoryId)} 공고`
                    : `${sourceById(source).name} 공고`
                  : view === 'my' ? '마이페이지' : view === 'interests' ? '관심 공고' : view === 'statistics' ? '채용 통계' : view === 'ai' ? 'AI에게 질문' : view === 'career' ? '경력 자료' : '수집 기록'}
                <span className="heading-dot">.</span>
              </h1>
              {view !== 'jobs' && <p>{view === 'my' ? '내 데이터를 백업하고 필요한 범위만 초기화해요.' : view === 'interests' ? '지원하고 싶은 공고와 다음 할 일을 차곡차곡 관리해요.' : view === 'statistics' ? '쌓아온 공고에서 채용의 흐름을 살펴봐요.' : view === 'ai' ? '전체 공고를 바탕으로 질문하고 근거를 확인해요.' : view === 'career' ? '이력서와 포트폴리오를 올리고 경력 근거를 정리해요.' : '언제, 어디에서, 얼마나 모았는지 확인해요.'}</p>}
            </div>
            {view === 'jobs' && <button
              className="button primary collect-button"
              disabled={running || !connected}
              onClick={() => navigateApp({ collectOpen: true, modal: 'collect-title' })}
            >
              <Icon
                name="refresh"
                size={18}
                className={running ? 'spin' : ''}
              />
              {running ? '공고 수집 중' : collectionCategoryId === 'all' ? '공고 수집' : `${categoryName(categoryId)} 수집`}
              {!running && <span className="button-plus">+</span>}
            </button>}
          </div>
          {error && <div className="backend-error" role="alert">{error} 연결을 자동으로 다시 확인하고 있어요.</div>}
          {view === 'jobs' ? (
            <>
              {changeScope && <div className="stats-job-scope">{changeScope.label} · {changeScope.ids.filter(id => data.jobs.some(job => job.id === id)).length}개 공고<button className="text-button" onClick={() => { setChangeScope(null); setSelectedJob(null); setPage(1) }}>변화 요약 필터 해제</button></div>}
              {companyFilter && <div className="stats-job-scope">{companies.find(c => c.id === companyFilter)?.name} 공고 <button className="text-button" onClick={() => setCompanyFilter(null)}>회사 필터 해제</button></div>}
              <div className="classification-filters">
                <div className="category-tabs" role="tablist" aria-label="공고 분류">
                  {[{ id: 'all', name: '전체 공고' }, { id: 'unclassified', name: '미분류' }].map(category => <button key={category.id} role="tab" aria-selected={categoryId === category.id} className={categoryId === category.id ? 'active' : ''} onClick={() => { setCategoryId(category.id); setPage(1); setSelectedJob(null) }}>{category.name}<span>{data.jobs.filter(job => (includeClosed || !job.closed) && (source === 'all' || job.sourceId === source) && (!companyFilter || job.companyId === companyFilter) && (category.id === 'all' || !job.categories?.length)).length}</span></button>)}
                </div>
                <CategoryPicker
                  categories={categories}
                  counts={categoryCounts}
                  value={categoryId}
                  onSelect={id => { setCategoryId(id); setPage(1); setSelectedJob(null) }}
                />
              </div>
              {!demo && latestRun?.finishedAt && <CollectionChanges key={latestRun.id} run={latestRun} onBrowse={browseChanges} compact onOpenRun={() => openRun(latestRun.id)} />}
              {latestRun &&
                latest &&
                (running ? (
                  <section
                    className="live-progress"
                    aria-label="기업별 수집 진행 상황"
                  >
                    <div>
                      <Icon name="refresh" size={17} className="spin" />
                      <strong>{(latestRun.categoryIds?.length ? latestRun.categoryIds.map(id => categoryName(id)).join(' · ') : categoryName(latestRun.categoryId))}를 모으고 있어요</strong>
                      <span>
                        {latestRun.progress ? `${latestRun.progress.completed + latestRun.progress.failed} / ${latestRun.progress.total}개 공고 · ${latestRun.progress.discoveryFinished ? `${latestRun.progress.percent}%` : '목록 탐색 중'}` : `${latest.done} / ${latestRun.results.length}개 기업군`}
                      </span>
                      <button
                        className="text-button"
                        onClick={() => openRun(latestRun.id)}
                      >
                        실행 기록 보기
                        <Icon name="chevron" size={12} />
                      </button>
                    </div>
                    {latestRun.applicationProgress && <div className="live-application-progress">
                      <Icon name="file" size={13} />
                      <span>지원 페이지 확인</span>
                      <strong>{latestRun.applicationProgress.fetched + latestRun.applicationProgress.failed} / {latestRun.applicationProgress.total}</strong>
                      <small>페이지 저장 {latestRun.applicationProgress.fetched} · 실패 {latestRun.applicationProgress.failed} · 대기 {latestRun.applicationProgress.pending} · 진행 중 {latestRun.applicationProgress.processing}</small>
                    </div>}
                    <div className="live-source-list">
                      {latestRun.results.map((r) => (
                        <span
                          className={`live-source status-${r.status}`}
                          key={r.sourceId}
                        >
                          <BrandMark sourceId={r.sourceId} small />
                          {sourceById(r.sourceId).name}
                          {r.status === 'running' ? (
                            <Icon name="refresh" size={11} className="spin" />
                          ) : r.status === 'success' ? (
                            <Icon name="check" size={11} />
                          ) : r.status === 'failed' ||
                            r.status === 'partial' ? (
                            <Icon name="alert" size={11} />
                          ) : (
                            <span className="tiny-dot" />
                          )}
                        </span>
                      ))}
                    </div>
                    <div className="progress-track">
                      <div
                        style={{
                          width: `${Math.max(3, latestRun.progress?.percent ?? (latest.done / latestRun.results.length) * 100)}%`,
                        }}
                      />
                    </div>
                  </section>
                ) : demo ? (
                  <button
                    className="last-collection"
                    onClick={() => openRun(latestRun.id)}
                  >
                    <span>
                      <span
                        className={`tiny-dot ${latest.failed ? 'warning' : ''}`}
                      />
                      {(latestRun.categoryIds?.length ? latestRun.categoryIds.map(id => categoryName(id)).join(' · ') : categoryName(latestRun.categoryId))} · 마지막 수집 <b>{dateLabel(latestRun.startedAt, true)}</b>
                      <i />
                      신규 <strong>{latest.added}</strong> · 변경{' '}
                      <strong>{latest.changed}</strong>
                      {latest.failed > 0 && (
                        <span className="error-label">
                          실패 {latest.failed}
                        </span>
                      )}
                    </span>
                    <span>
                      수집 기록 보기
                      <Icon name="chevron" size={12} />
                    </span>
                  </button>
                ) : null)}
              <div className="browse-layout">
                <section
                  className="jobs-section"
                  aria-label="수집된 공고 목록"
                  tabIndex={-1}
                >
                  <div
                    className="list-tabs"
                    role="tablist"
                    aria-label="공고 보기"
                  >
                    <button
                      role="tab"
                      aria-selected={tab === 'all'}
                      className={tab === 'all' ? 'active' : ''}
                      onClick={() => {
                        setTab('all')
                        setPage(1)
                        setSelectedJob(null)
                      }}
                    >
                      전체 공고<span>{available.length}</span>
                    </button>
                    <button
                      role="tab"
                      aria-selected={tab === 'unread'}
                      className={tab === 'unread' ? 'active' : ''}
                      onClick={() => {
                        setTab('unread')
                        setPage(1)
                        setSelectedJob(null)
                      }}
                    >
                      안 읽은 공고<span>{unread}</span>
                    </button>
                    <button
                      role="tab"
                      aria-selected={tab === 'new'}
                      className={tab === 'new' ? 'active' : ''}
                      onClick={() => {
                        setTab('new')
                        setPage(1)
                        setSelectedJob(null)
                      }}
                    >
                      이번 수집 신규<span>{newCount}</span>
                    </button>
                    <label className="closed-toggle">
                      <input
                        type="checkbox"
                        checked={includeClosed}
                        onChange={(e) => {
                          setIncludeClosed(e.target.checked)
                          setPage(1)
                          setSelectedJob(null)
                        }}
                      />
                      마감 포함 <span>{closedCount}</span>
                    </label>
                  </div>
                  <div className="list-toolbar">
                    <label className="search-field">
                      <Icon name="search" size={18} />
                      <input
                        aria-label="회사, 공고명, 근무지 검색"
                        placeholder="회사, 공고명, 근무지 검색"
                        value={query}
                        onChange={(e) => {
                          setQuery(e.target.value)
                          setPage(1)
                          setSelectedJob(null)
                        }}
                      />
                      {query && (
                        <button
                          className="icon-button"
                          onClick={() => {
                            setQuery('')
                            setPage(1)
                            setSelectedJob(null)
                          }}
                          aria-label="검색어 지우기"
                        >
                          <Icon name="close" size={15} />
                        </button>
                      )}
                    </label>
                    <div className="list-options">
                      {source !== 'all' && (
                        <button
                          className="filter-chip"
                          onClick={() => changeSource('all')}
                        >
                          {sourceById(source).name}
                          <Icon name="close" size={12} />
                        </button>
                      )}
                      <span className="result-count">
                        총 <strong>{listJobs.length}</strong>개
                      </span>
                      <label className="sort-select">
                        <select
                          aria-label="공고 정렬"
                          value={sort}
                          onChange={(e) => {
                            setSort(e.target.value)
                            setPage(1)
                            setSelectedJob(null)
                          }}
                        >
                          <option value="newest">최초 발견일 최신순</option>
                          <option value="oldest">최초 발견일 오래된순</option>
                          <option value="company">회사 이름순</option>
                        </select>
                        <Icon name="down" size={13} />
                      </label>
                      <label className="page-size-select">
                        <span>페이지당</span>
                        <select aria-label="페이지 크기" value={pageSize} onChange={(event) => { setPageSize(Number(event.target.value)); setPage(1) }}>
                          {PAGE_SIZES.map(size => <option key={size} value={size}>{size}개</option>)}
                        </select>
                      </label>
                    </div>
                  </div>
                  <div className="table-scroll">
                    <table className="jobs-table">
                      <thead>
                        <tr>
                          <th><span className="sr-only">읽음</span><Icon name="check" size={16} /></th>
                          <th>회사</th>
                          <th>공고명</th>
                          <th>직군</th>
                          <th>근무지</th>
                          <th>최초 발견일</th>
                          <th>원문</th>
                        </tr>
                      </thead>
                      <tbody>
                        {visibleJobs.map((job) => (
                          <tr key={job.id} className={`${job.read ? 'job-read' : ''} ${job.closed ? 'job-closed' : ''}`}>
                            <td>
                              <label className="read-checkbox" title={job.read ? '안 읽음으로 변경' : '읽음으로 표시'}>
                                <input type="checkbox" checked={job.read} aria-label={`${job.title} 읽음 표시`} onChange={() => toggleRead(job.id)} />
                                <span><Icon name="check" size={12} /></span>
                              </label>
                            </td>
                            <td>
                              <div className="company-cell">
                                <BrandMark sourceId={job.sourceId} />
                                <span>{job.company}<small>{sourceById(job.sourceId).name} 기업군</small></span>
                              </div>
                            </td>
                            <td className="title-cell">
                              <div className="posting-title-line">
                                <InterestStar job={job} onChange={setStarred} />
                                <button className="job-title-open" data-job-id={job.id} aria-label={`${job.title} 상세 보기`} onClick={() => openJob(job.id)}>
                                  <span className="job-title-text">{job.title}</span>
                                  {job.latestChange === 'new' && <span className="new-badge">NEW</span>}
                                  {job.latestChange === 'changed' && <span className="changed-badge">변경</span>}
                                  {job.closed && <span className="closed-badge">마감 · 보관</span>}
                                  <span className="job-open-hint">상세 보기 <Icon name="chevron" size={12} /></span>
                                </button>
                              </div>
                              <span className="saved-indicator">
                                <Icon name="image" size={12} />
                                <span>캡처 {job.versions[0].captureAvailable ? demo ? '예시' : '저장됨' : '미저장'}</span>
                                <span className="saved-dot">·</span>
                                <Icon name="file" size={12} />
                                <span>본문</span>
                                {job.versions.length > 1 && <><span className="saved-dot">·</span><span>버전 {job.versions.length}</span></>}
                              </span>
                            </td>
                            <td className="job-category-cell">
                              {job.categories?.length ? job.categories.map(id => <span className="job-category" key={id}>{categoryName(id)}</span>) : <span className="job-category unmapped">미분류</span>}
                            </td>
                            <td className="location-cell">{job.location}</td>
                            <td className="date-cell">{dateLabel(job.firstSeen)}</td>
                            <td>
                              <a className="original-link" href={job.url} target="_blank" rel="noreferrer" aria-label={`${job.company} 공식 채용 사이트 열기`} title={demo ? '샘플: 공식 채용 홈으로 이동' : '공고 원문 열기'}>
                                <Icon name="external" size={16} />
                              </a>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  {!listJobs.length && (
                    <div className="empty-state">
                      <span>
                        <Icon name="search" size={28} />
                      </span>
                      <h3>조건에 맞는 공고가 없어요</h3>
                      <p>{categoryId === 'unclassified' ? '현재 조건에서 직군이 매핑되지 않은 공고가 없어요.' : !categoryJobs.length ? '공고를 수집하면 저장된 본문을 기준으로 직군을 자동 분류해요.' : '검색어나 선택한 기업군을 바꿔보세요.'}</p>
                      <button
                        className="button secondary small"
                        onClick={resetFilters}
                      >
                        검색 조건 초기화
                      </button>
                    </div>
                  )}
                  <div className="table-footer">
                    <span>
                      {listJobs.length
                        ? `${(safePage - 1) * pageSize + 1}–${Math.min(safePage * pageSize, listJobs.length)}`
                        : '0'}
                      <span> / {listJobs.length}개 공고</span>
                    </span>
                    <nav className="pagination" aria-label="공고 페이지">
                      <button
                        aria-label="이전 페이지"
                        disabled={safePage === 1}
                        onClick={() => setPage(safePage - 1)}
                      >
                        <Icon name="chevron" className="rotate-180" size={14} />
                      </button>
                      {Array.from({ length: totalPages }, (_, i) => i + 1)
                        .filter(
                          (p) =>
                            p === 1 ||
                            p === totalPages ||
                            Math.abs(p - safePage) <= 1,
                        )
                        .map((p, index, array) => (
                          <span key={p}>
                            {index > 0 && p - array[index - 1] > 1 && (
                              <span className="page-ellipsis">…</span>
                            )}
                            <button
                              aria-label={`${p}페이지`}
                              aria-current={safePage === p ? 'page' : undefined}
                              className={safePage === p ? 'active' : ''}
                              onClick={() => setPage(p)}
                            >
                              {p}
                            </button>
                          </span>
                        ))}
                      <button
                        aria-label="다음 페이지"
                        disabled={safePage === totalPages}
                        onClick={() => setPage(safePage + 1)}
                      >
                        <Icon name="chevron" size={14} />
                      </button>
                    </nav>
                  </div>
                </section>
                {activeJob && (
                  <JobDrawer
                    job={activeJob}
                    categories={categories}
                    onClose={closeJob}
                    onToggleRead={() => toggleRead(activeJob.id)}
                    onStar={setStarred}
                    onSaveInterest={saveInterest}
                    position={browseQueue.indexOf(activeJob.id)}
                    total={browseQueue.length}
                    onPrevious={() => navigateJob(-1)}
                    onNext={() => navigateJob(1)}
                    demo={demo}
                  />
                )}
              </div>
              <div className="page-footnote">
                <Icon name="info" size={14} />
                <span>
                  {demo ? '화면 검토용 샘플이며 실제 채용 여부와 무관해요.' : '저장된 본문과 캡처를 보관해요. 최신 내용은 원문에서도 확인할 수 있어요.'} 마감 공고는 보관되며 기본 목록에서 숨겨져요.
                </span>
              </div>
            </>
          ) : view === 'career' ? <CareerMaterials demo={demo} categories={categories} companies={companies} tab={careerTab} onTabChange={(next) => navigateApp({ careerTab: next })} onOpenPosting={openAiPosting} /> : view === 'my' ? <MyPage key={data.workspaceRevision} demo={demo} running={running} /> : view === 'interests' ? <InterestBoard key={data.workspaceRevision} jobs={data.jobs} categories={categories} onStar={setStarred} onSave={saveInterest} markRead={markRead} toggleRead={toggleRead} loadJob={loadJob} onBrowse={() => changeSource('all')} /> : view === 'statistics' ? <Statistics categories={categories} companies={companies} demo={demo} onBrowse={browseStatistics} /> : view === 'ai' ? <AiChat demo={demo} onOpenPosting={openAiPosting} /> : (
            <section className="history-view">
              <div className="section-heading">
                <h2>
                  실행별 수집 내역 <span>{data.runs.length}</span>
                </h2>
                <p>
                  버튼 한 번에 기록 하나. 성공한 결과는 실패와 관계없이
                  저장해요.
                </p>
              </div>
              {data.runs.map((run) => (
                <RunCard
                  companies={companies}
                  categories={categories}
                  key={run.id}
                  run={run}
                  expanded={expandedRuns.includes(run.id)}
                  onToggle={() => toggleRun(run.id)}
                  onRetry={(ids) => retry(run.id, ids)}
                  onOpenOriginal={(id) => {
                    openRun(id)
                    requestAnimationFrame(() =>
                      document
                        .getElementById(`run-${id}`)
                        ?.scrollIntoView({ block: 'start' }),
                    )
                  }}
                  onBrowseChanges={demo ? undefined : browseChanges}
                  busy={running}
                />
              ))}
              <div className="page-footnote">
                <Icon name="info" size={14} />
                <span>
                  {demo ? '현재 기록은 샘플 시뮬레이션이며 이 브라우저에 저장돼요.' : '공고·읽음 표시·수집 이력은 로컬 DB에 저장돼요. 성공한 결과는 일부 실패와 관계없이 보관해요.'}
                </span>
              </div>
            </section>
          )}
          <footer className="main-footer">
            <span>하나씩 모으다 보면, 다음이 보여요.</span>
            <span>
              Made for your next move <Icon name="spark" size={12} />
            </span>
          </footer>
        </main>
      </div>
      {collectOpen && <CollectDialog onClose={closeCollect} onStart={begin} demo={demo} availableSources={availableSources} categories={categories} companies={companies} initialCategoryId={collectionCategoryId} />}
      {notice && (
        <div className="toast" role="status">
          <Icon name="check" size={17} />
          {notice}
        </div>
      )}
    </div>
  )
}
export default App
