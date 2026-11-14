import { useEffect, useRef, useState } from 'react'
import {
  emptyResult,
  loadDemo,
  makeSampleJob,
  sampleBody,
  saveDemo,
  sourceById,
} from './data'
import type { CollectionRun, Failure, FailureStage, SourceId } from './types'

// Frontend-only simulator. The later API implementation can replace this hook.
export function useCollection() {
  const [data, setData] = useState(loadDemo)
  const [notice, setNotice] = useState('')
  const locked = useRef(false)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  useEffect(() => {
    saveDemo(data)
  }, [data])
  useEffect(
    () => () => {
      if (timer.current) clearTimeout(timer.current)
    },
    [],
  )
  useEffect(() => {
    if (!notice) return
    const t = setTimeout(() => setNotice(''), 4500)
    return () => clearTimeout(t)
  }, [notice])

  function start(
    ids: SourceId[],
    failureStage: FailureStage | null = null,
    retry?: { runId: string; failures: Failure[] },
  ) {
    if (locked.current || !ids.length) {
      setNotice('현재 수집이 끝난 뒤 다시 시도해주세요.')
      return null
    }
    locked.current = true
    const runId = String(Date.now())
    const run: CollectionRun = {
      id: runId,
      startedAt: new Date().toISOString(),
      results: ids.map(emptyResult),
      ...(retry
        ? {
            retryOf: retry.runId,
            retriedFailures: retry.failures.map((f) => f.id),
          }
        : {}),
    }
    setData((current) => ({
      ...current,
      jobs: current.jobs.map((job) => ({ ...job, latestChange: null })),
      runs: [run, ...current.runs],
    }))
    function process(index: number) {
      const sourceId = ids[index]
      setData((current) => ({
        ...current,
        runs: current.runs.map((item) =>
          item.id === runId
            ? {
                ...item,
                results: item.results.map((r) =>
                  r.sourceId === sourceId ? { ...r, status: 'running' } : r,
                ),
              }
            : item,
        ),
      }))
      timer.current = setTimeout(() => {
        const at = new Date().toISOString()
        setData((current) => {
          let jobs = current.jobs
          const recovered =
            retry?.failures.filter((f) => f.sourceId === sourceId) ?? []
          const result = {
            ...emptyResult(sourceId),
            status: 'success' as const,
            added: 0,
            changed: 0,
            identical: 0,
            failed: 0,
            recovered: recovered.length,
          }
          let finalResult: (typeof run.results)[number] = result
          if (retry) {
            for (const failure of recovered) {
              if (failure.stage === 'list' || failure.stage === 'save') {
                // A save retry reuses the already rendered/extracted assets.
                const job =
                  failure.pendingJob ??
                  makeSampleJob(
                    sourceId,
                    `${runId}-${failure.id}`,
                    failure.stage === 'save' ? failure.at : at,
                  )
                if (!failure.pendingJob && failure.title) {
                  job.title = failure.title
                  job.versions[0].body = sampleBody(job.company, job.title)
                }
                jobs = [job, ...jobs]
                result.added += 1
              } else {
                jobs = jobs.map((job) =>
                  job.id !== failure.jobId
                    ? job
                    : {
                        ...job,
                        versions: job.versions.map((version) =>
                          version.id !== failure.versionId
                            ? version
                            : {
                                ...version,
                                captureAvailable: true,
                                ...(failure.stage === 'body'
                                  ? { body: sampleBody(job.company, job.title) }
                                  : {}),
                              },
                        ),
                      },
                )
              }
            }
          } else {
            // One source can fail without discarding the other successful sources.
            const stage =
              failureStage && index === 0
                ? failureStage
                : failureStage && ids.length > 1 && index === ids.length - 1
                  ? 'list'
                  : null
            if (stage === 'list') {
              const failure: Failure = {
                id: `${runId}-${sourceId}-list`,
                sourceId,
                company: sourceById(sourceId).name,
                url: sourceById(sourceId).url,
                stage,
                at,
                attempts: 1,
                resolved: false,
                error:
                  'HTTP 503 Service Unavailable\nStage: list\nSample diagnostic — no request was sent.',
              }
              finalResult = {
                ...emptyResult(sourceId),
                status: 'failed',
                failures: [failure],
              }
            } else {
              const newJob = makeSampleJob(
                sourceId,
                runId,
                at,
                stage !== 'body' && stage !== 'capture',
              )
              if (stage === 'body') newJob.versions[0].body = ''
              const existing = jobs.filter(
                (j) => j.sourceId === sourceId && !j.closed,
              )
              const changedJob = existing[0]
              jobs = jobs.map((job) =>
                job.id !== changedJob?.id
                  ? job
                  : {
                      ...job,
                      latestChange: 'changed',
                      versions: [
                        {
                          id: `${runId}-${job.id}-v${job.versions.length + 1}`,
                          savedAt: at,
                          captureAvailable: true,
                          body: sampleBody(
                            job.company,
                            job.title,
                            job.versions.length + 1,
                          ),
                        },
                        ...job.versions,
                      ],
                    },
              )
              if (stage !== 'save') jobs = [newJob, ...jobs]
              finalResult = {
                ...result,
                status: stage ? 'partial' : 'success',
                added: stage === 'save' ? 0 : 1,
                changed: changedJob ? 1 : 0,
                identical: Math.max(0, existing.length - (changedJob ? 1 : 0)),
                failed: stage ? 1 : 0,
                failures: stage
                  ? [
                      {
                        id: `${runId}-${sourceId}-${stage}`,
                        sourceId,
                        company: newJob.company,
                        title: newJob.title,
                        url: newJob.url,
                        stage,
                        at,
                        attempts: 1,
                        resolved: false,
                        ...(stage === 'save'
                          ? { pendingJob: newJob }
                          : {
                              jobId: newJob.id,
                              versionId: newJob.versions[0].id,
                            }),
                        error: `${stage === 'capture' ? 'TimeoutError: page.screenshot: Timeout 30000ms exceeded.' : stage === 'body' ? 'ExtractionError: Main content selector returned no text.' : 'StorageError: Asset write failed.'}\nStage: ${stage}\nSample diagnostic — no request was sent.`,
                      },
                    ]
                  : [],
              }
            }
          }
          const resolvedIds = new Set(recovered.map((f) => f.id))
          return {
            ...current,
            jobs,
            runs: current.runs.map((item) =>
              item.id === runId
                ? {
                    ...item,
                    ...(index === ids.length - 1 ? { finishedAt: at } : {}),
                    results: item.results.map((r) =>
                      r.sourceId === sourceId ? finalResult : r,
                    ),
                  }
                : retry && item.id === retry.runId
                  ? {
                      ...item,
                      results: item.results.map((r) => ({
                        ...r,
                        failures: r.failures.map((f) =>
                          resolvedIds.has(f.id)
                            ? { ...f, resolved: true, attempts: f.attempts + 1 }
                            : f,
                        ),
                      })),
                    }
                  : item,
            ),
          }
        })
        if (index + 1 < ids.length) process(index + 1)
        else {
          locked.current = false
          timer.current = null
          setNotice(
            retry
              ? '실패한 단계부터 재시도했어요. 수집 기록에서 결과를 확인하세요.'
              : '수집 미리보기를 마쳤어요. 샘플 공고와 버전이 추가됐어요.',
          )
        }
      }, 750)
    }
    process(0)
    return runId
  }
  function retryFailures(runId: string, ids?: string[]) {
    const failures =
      data.runs
        .find((run) => run.id === runId)
        ?.results.flatMap((r) => r.failures)
        .filter((f) => !f.resolved && (!ids || ids.includes(f.id))) ?? []
    if (!failures.length) return null
    return start([...new Set(failures.map((f) => f.sourceId))], null, {
      runId,
      failures,
    })
  }
  const toggleRead = (id: string) =>
    setData((current) => ({
      ...current,
      jobs: current.jobs.map((job) =>
        job.id === id ? { ...job, read: !job.read } : job,
      ),
    }))
  const markRead = (id: string) =>
    setData((current) => ({
      ...current,
      jobs: current.jobs.map((job) =>
        job.id === id ? { ...job, read: true } : job,
      ),
    }))
  return {
    data,
    notice,
    start,
    retryFailures,
    toggleRead,
    markRead,
    running: Boolean(data.runs[0] && !data.runs[0].finishedAt),
  }
}
