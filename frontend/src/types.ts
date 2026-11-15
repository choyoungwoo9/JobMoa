export type SourceId =
  | 'naver'
  | 'toss'
  | 'kakao'
  | 'coupang'
  | 'daangn'
  | 'line'
  | 'woowa'
  | 'bucketplace'
export interface Category {
  id: string
  name: string
  description?: string
}
export interface Company {
  id: string
  sourceId: SourceId
  name: string
  supportedCategoryIds: string[]
}
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
  captureUrl?: string
  bodyLoaded?: boolean
}
export interface Job {
  companyId?: string
  classification?: {
    rulesVersion: string
    parserMode: string
    evidence: Record<string, string[]>
    classifiedAt: string
    unclassifiedReason?: string | null
  } | null
  categories?: string[]
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
  pendingJob?: Job
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
  succeeded?: number
  total?: number
  excluded?: number
}
export interface CollectionRun {
  categoryId?: string
  companyIds?: string[]
  id: string
  startedAt: string
  finishedAt?: string
  results: SourceResult[]
  retryOf?: string
  retriedFailures?: string[]
  progress?: { discoveryFinished: boolean; total: number; pending: number; processing: number; completed: number; failed: number; percent: number | null }
}
export interface DemoState {
  version: 1
  jobs: Job[]
  runs: CollectionRun[]
}
