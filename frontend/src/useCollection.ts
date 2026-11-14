import { useCallback, useEffect, useRef, useState } from 'react'
import type { DemoState, FailureStage, Job, SourceId } from './types'
import { useDemoCollection } from './useDemoCollection'

interface BackendState extends DemoState {
  sources: { id: SourceId; available: boolean }[]
}
const initial: BackendState = { version: 1, jobs: [], runs: [], sources: [] }
function merge(next: BackendState, previous: BackendState): BackendState {
  const loaded = new Map(previous.jobs.flatMap(job => job.versions.filter(v => v.bodyLoaded).map(v => [v.id, v] as const)))
  return { ...next, jobs: next.jobs.map(job => ({ ...job, versions: job.versions.map(v => loaded.has(v.id) ? { ...v, body: loaded.get(v.id)!.body, bodyLoaded: true } : v) })) }
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
  const [notice, setNotice] = useState('')
  const [connected, setConnected] = useState(false)
  const [busy, setBusy] = useState(false)
  const locked = useRef(false)
  const loading = useRef(new Set<string>())
  const loadJob = useCallback(async (id: string) => {
    if (loading.current.has(id)) return
    loading.current.add(id)
    try {
      const job = await request<Job>(`/api/postings/${id}`)
      setData(current => ({ ...current, jobs: current.jobs.map(existing => existing.id === id ? { ...job, read: existing.read, latestChange: existing.latestChange } : existing) }))
    } catch (e) { setNotice(e instanceof Error ? e.message : '본문을 불러오지 못했어요.') }
    finally { loading.current.delete(id) }
  }, [])
  useEffect(() => {
    let disposed = false
    let timer: ReturnType<typeof setTimeout>
    const controller = new AbortController()
    async function poll() {
      try {
        const state = await request<BackendState>('/api/state', { signal: controller.signal })
        if (!disposed) { setData(current => merge(state, current)); setError(''); setConnected(true) }
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
      const state = await request<BackendState>('/api/state')
      setData(current => merge(state, current))
      return result.id
    } catch (e) { setNotice(e instanceof Error ? e.message : '실행에 실패했어요.'); return null }
    finally { locked.current = false; setBusy(false) }
  }
  async function read(id: string, value: boolean) {
    try {
      await request(`/api/postings/${id}/read`, { method: 'PATCH', body: JSON.stringify({ read: value }) })
      const state = await request<BackendState>('/api/state')
      setData(current => merge(state, current))
    } catch (e) { setNotice(e instanceof Error ? e.message : '읽음 저장에 실패했어요.') }
  }
  return {
    data, notice, connected, error, demo: false, loadJob,
    availableSources: data.sources.filter(s => s.available).map(s => s.id),
    start: (ids: SourceId[], _failure: FailureStage | null = null) => launch('/api/collections', { sourceIds: ids }),
    retryFailures: (id: string, ids?: string[]) => launch(`/api/collections/${id}/retry`, { failureIds: ids }),
    toggleRead: (id: string) => { void read(id, !data.jobs.find(j => j.id === id)?.read) },
    markRead: (id: string) => { if (!data.jobs.find(j => j.id === id)?.read) void read(id, true) },
    running: busy || data.runs.some(r => !r.finishedAt),
  }
}

// 명시적으로 선택한 샘플 화면만 기존 시뮬레이터를 사용한다.
export const useCollection = new URLSearchParams(window.location.search).get('demo') === '1'
  ? useDemoCollection : useBackendCollection
