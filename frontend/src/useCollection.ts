import { useCallback, useEffect, useRef, useState } from 'react'
import type { Category, Company, DemoState, FailureStage, InterestManagement, Job, PostingInterest, SourceId } from './types'
import { useDemoCollection } from './useDemoCollection'
import { CATEGORIES } from './data'

interface BackendState extends DemoState {
  workspaceRevision?: string
  categories?: Category[]
  companies?: Company[]
  sources: { id: SourceId; available: boolean }[]
}
const initial: BackendState = { version: 1, jobs: [], runs: [], sources: [] }
function merge(next: BackendState, previous: BackendState, preserveInterest: (id: string) => boolean): BackendState {
  if (next.workspaceRevision !== previous.workspaceRevision) return next
  const loaded = new Map(previous.jobs.flatMap(job => job.versions.filter(v => v.bodyLoaded).map(v => [v.id, v] as const)))
  const previousJobs = new Map(previous.jobs.map(job => [job.id, job]))
  return { ...next, jobs: next.jobs.map(job => ({ ...job, interest: preserveInterest(job.id) ? previousJobs.get(job.id)?.interest : job.interest, versions: job.versions.map(v => loaded.has(v.id) ? { ...v, body: loaded.get(v.id)!.body, bodyLoaded: true } : v) })) }
}
function newestInterest(next?: PostingInterest, previous?: PostingInterest) {
  return Date.parse(previous?.updatedAt ?? '') > Date.parse(next?.updatedAt ?? '1970-01-01') ? previous : next
}
async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, { ...options, headers: { 'Content-Type': 'application/json', ...options?.headers } })
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.message ?? `요청 실패 (${response.status})`)
  }
  return response.status === 204 ? undefined as T : response.json()
}

function useBackendCollection() {
  const [data, setData] = useState<BackendState>(initial)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState(() => sessionStorage.getItem('jobmoa.workspace.notice') ?? '')
  const [connected, setConnected] = useState(false)
  const [busy, setBusy] = useState(false)
  useEffect(() => { sessionStorage.removeItem('jobmoa.workspace.notice') }, [])
  const locked = useRef(false)
  const loading = useRef(new Set<string>())
  const workspaceRevision = useRef<string | undefined>(undefined)
  useEffect(() => { workspaceRevision.current = data.workspaceRevision }, [data.workspaceRevision])
  // 저장 전에 시작한 조회만 보류한다. 이후 조회는 서버의 빈 관리 정보도 그대로 반영한다.
  const interestRevision = useRef(0)
  const interestWrites = useRef(new Map<string, number>())
  const loadJob = useCallback(async (id: string) => {
    if (loading.current.has(id)) return
    loading.current.add(id)
    const epoch = workspaceRevision.current
    try {
      const requestRevision = interestRevision.current
      const job = await request<Job>(`/api/postings/${id}`)
      setData(current => current.workspaceRevision !== epoch ? current : ({ ...current, jobs: current.jobs.map(existing => existing.id === id ? { ...job, interest: (interestWrites.current.get(id) ?? 0) > requestRevision ? existing.interest : job.interest, read: existing.read, latestChange: existing.latestChange } : existing) }))
    } catch (e) { setNotice(e instanceof Error ? e.message : '본문을 불러오지 못했어요.') }
    finally { loading.current.delete(id) }
  }, [])
  useEffect(() => {
    let disposed = false
    let timer: ReturnType<typeof setTimeout>
    const controller = new AbortController()
    async function poll() {
      try {
        const requestRevision = interestRevision.current
        const state = await request<BackendState>('/api/state', { signal: controller.signal })
        if (!disposed) { setData(current => merge(state, current, id => (interestWrites.current.get(id) ?? 0) > requestRevision)); setError(''); setConnected(true) }
      } catch (e) {
        if (!disposed) { setConnected(false); setError(`백엔드에 연결하지 못했어요. ${e instanceof Error ? e.message : ''}`) }
      } finally { if (!disposed) timer = setTimeout(poll, 1000) }
    }
    void poll()
    return () => { disposed = true; controller.abort(); clearTimeout(timer) }
  }, [])
  useEffect(() => {
    if (!notice) return
    const timer = setTimeout(() => setNotice(''), 4500)
    return () => clearTimeout(timer)
  }, [notice])
  async function launch(url: string, body: object) {
    if (locked.current) return null
    locked.current = true; setBusy(true)
    try {
      const result = await request<{ id: string }>(url, { method: 'POST', body: JSON.stringify(body) })
      const requestRevision = interestRevision.current
      const state = await request<BackendState>('/api/state')
      setData(current => merge(state, current, id => (interestWrites.current.get(id) ?? 0) > requestRevision))
      return result.id
    } catch (e) { setNotice(e instanceof Error ? e.message : '실행에 실패했어요.'); return null }
    finally { locked.current = false; setBusy(false) }
  }
  async function read(id: string, value: boolean) {
    try {
      await request(`/api/postings/${id}/read`, { method: 'PATCH', body: JSON.stringify({ read: value }) })
      const requestRevision = interestRevision.current
      const state = await request<BackendState>('/api/state')
      setData(current => merge(state, current, id => (interestWrites.current.get(id) ?? 0) > requestRevision))
    } catch (e) { setNotice(e instanceof Error ? e.message : '읽음 저장에 실패했어요.') }
  }
  async function updateInterest(id: string, method: 'PATCH' | 'PUT', body: object) {
    try {
      const interest = await request<PostingInterest>(`/api/postings/${id}/interest${method === 'PUT' ? '/management' : ''}`, { method, body: JSON.stringify(body) })
      interestWrites.current.set(id, ++interestRevision.current)
      setData(current => ({ ...current, jobs: current.jobs.map(job => job.id === id ? { ...job, interest: newestInterest(interest, job.interest) } : job) }))
      if (method === 'PUT') setNotice('관심 공고 관리 정보를 저장했어요.')
      return true
    } catch (e) { setNotice(e instanceof Error ? e.message : '관심 공고 정보를 저장하지 못했어요.'); return false }
  }
  return {
    setStarred: (id: string, starred: boolean) => updateInterest(id, 'PATCH', { starred }),
    saveInterest: (id: string, management: InterestManagement) => updateInterest(id, 'PUT', management),
    data, notice, connected, error, demo: false, loadJob,
    availableSources: data.sources.filter(s => s.available).map(s => s.id),
    categories: data.categories ?? CATEGORIES,
    companies: data.companies ?? [],
    start: (ids: SourceId[], _failure: FailureStage | null = null, categoryId = 'all', companyIds?: string[], _retry?: unknown, categoryIds?: string[]) => launch('/api/collections', { sourceIds: ids, categoryId, companyIds, categoryIds }),
    retryFailures: (id: string, ids?: string[]) => launch(`/api/collections/${id}/retry`, { failureIds: ids }),
    toggleRead: (id: string) => { void read(id, !data.jobs.find(j => j.id === id)?.read) },
    markRead: (id: string) => { if (!data.jobs.find(j => j.id === id)?.read) void read(id, true) },
    running: busy || data.runs.some(r => !r.finishedAt),
  }
}

// 명시적으로 선택한 샘플 화면만 기존 시뮬레이터를 사용한다.
export const useCollection = new URLSearchParams(window.location.search).get('demo') === '1'
  ? useDemoCollection : useBackendCollection
