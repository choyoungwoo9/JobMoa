import type { SourceId } from './types'

export type WorkspaceView = 'jobs' | 'history' | 'statistics' | 'interests' | 'ai' | 'career' | 'my'
export type CareerTab = 'materials' | 'assistant'
export type JobListTab = 'all' | 'unread' | 'new'
export type InterestStatusTab = 'all' | 'BEFORE_APPLY' | 'APPLIED'

export interface InterestNavigationSnapshot {
  tab: InterestStatusTab
  query: string
  source: string
  category: string
  includeClosed: boolean
  sort: string
  page: number
  selected: string | null
  queue: string[]
  comparing: boolean
}

export interface AppNavigationSnapshot {
  version: 1
  view: WorkspaceView
  careerTab: CareerTab
  changeScope: { ids: string[]; label: string } | null
  categoryId: string
  source: SourceId | 'all'
  companyFilter: string | null
  tab: JobListTab
  query: string
  sort: string
  page: number
  pageSize: number
  includeClosed: boolean
  collectOpen: boolean
  modal: string | null
  selectedJob: string | null
  browseQueue: string[]
  expandedRuns: string[]
  scrollY: number
  tableScrollTop: number
  tableScrollLeft: number
  interest?: InterestNavigationSnapshot
}

const HISTORY_KEY = 'jobmoaNavigation'

export function emptyNavigationSnapshot(): AppNavigationSnapshot {
  return {
    version: 1,
    view: 'jobs',
    careerTab: 'materials',
    changeScope: null,
    categoryId: 'all',
    source: 'all',
    companyFilter: null,
    tab: 'all',
    query: '',
    sort: 'newest',
    page: 1,
    pageSize: 20,
    includeClosed: false,
    collectOpen: false,
    modal: null,
    selectedJob: null,
    browseQueue: [],
    expandedRuns: [],
    scrollY: 0,
    tableScrollTop: 0,
    tableScrollLeft: 0,
  }
}

export function readNavigationSnapshot(state: unknown = window.history.state): AppNavigationSnapshot | null {
  if (!state || typeof state !== 'object') return null
  const snapshot = (state as Record<string, unknown>)[HISTORY_KEY]
  if (!snapshot || typeof snapshot !== 'object') return null
  const candidate = snapshot as Partial<AppNavigationSnapshot>
  if (candidate.version !== 1 || !candidate.view) return null
  return candidate as AppNavigationSnapshot
}

export function writeNavigationSnapshot(snapshot: AppNavigationSnapshot, mode: 'push' | 'replace' = 'replace') {
  const existing = window.history.state && typeof window.history.state === 'object'
    ? window.history.state as Record<string, unknown>
    : {}
  const state = { ...existing, [HISTORY_KEY]: snapshot }
  if (mode === 'push') window.history.pushState(state, '', window.location.href)
  else window.history.replaceState(state, '', window.location.href)
}
