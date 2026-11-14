export type SourceId =
  | 'naver'
  | 'toss'
  | 'kakao'
  | 'coupang'
  | 'daangn'
  | 'line'
  | 'woowa'
export interface Source {
  id: SourceId
  name: string
  mark: string
  url: string
}
export interface JobVersion {
  id: string
  savedAt: string
  body: string
  captureAvailable: boolean
}
export interface Job {
  id: string
  sourceId: SourceId
  company: string
  title: string
  location: string
  url: string
  firstSeen: string
  read: boolean
  closed: boolean
  latestChange: 'new' | 'changed' | null
  versions: JobVersion[]
}
export type FailureStage = 'list' | 'body' | 'capture' | 'save'
export interface Failure {
  id: string
  sourceId: SourceId
  company: string
  title?: string
  url: string
  stage: FailureStage
  at: string
  attempts: number
  error: string
  jobId?: string
  versionId?: string
  resolved: boolean
}
export interface SourceResult {
  sourceId: SourceId
  status: 'waiting' | 'running' | 'success' | 'partial' | 'failed'
  added: number | null
  changed: number | null
  identical: number | null
  failed: number | null
  recovered: number
  failures: Failure[]
}
export interface CollectionRun {
  id: string
  startedAt: string
  finishedAt?: string
  results: SourceResult[]
  retryOf?: string
  retriedFailures?: string[]
}
export interface DemoState {
  version: 1
  jobs: Job[]
  runs: CollectionRun[]
}
